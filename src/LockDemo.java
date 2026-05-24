import java.util.concurrent.*;
import java.util.concurrent.locks.*;

/**
 * 阶段三：锁框架 — AQS、ReentrantLock、ReadWriteLock
 *
 * ★ 整个 JUC 最核心的部分。CountDownLatch、Semaphore、ReentrantLock、
 *   ReentrantReadWriteLock、ThreadPoolExecutor.Worker 全部基于 AQS。
 *
 * 断点建议：
 *   [1] ReentrantLock.lock() → Step Into → FairSync/NonfairSync → AQS.acquire()
 *   [2] AQS.acquireQueued() → 看线程如何入队 + park 自己
 *   [3] unlock() → AQS.release() → unparkSuccessor() → 看唤醒后继节点
 */
public class LockDemo {

    // ────────────────────────────────────────────
    // ★ 一、ReentrantLock + Condition（替代 synchronized + wait/notify）
    //   【掌握要求】
    //   1. 理解 ReentrantLock.lock() 的内部流程：
    //      tryAcquire(1) 尝试快速拿锁 → 失败则 acquireQueued(addWaiter(...), 1) 入队阻塞
    //   2. 理解 ConditionObject.await() 的完整流程：
    //      加入条件队列（单向链表）→ 完全释放锁（state=0）→ LockSupport.park 阻塞自己
    //   3. 理解 signal() 的流程：
    //      将条件队列头部节点 → 转移到 AQS 同步队列尾部 → 设置前驱为 SIGNAL
    //   4. ★ 对比 synchronized 的内置条件队列（每个对象一个）vs ReentrantLock 可创建多个 Condition
    //   5. 掌握 try-finally 中 unlock() 的规范写法（和 JDK 7+ 的 try-with-resources 的区别）
    //   6. Step Into 理解 CLH 变种队列：双向链表 + 前驱节点的 waitStatus 标记（SIGNAL/CANCELLED）
    // ────────────────────────────────────────────

    static void demoReentrantLock() throws Exception {
        ReentrantLock lock = new ReentrantLock(); // ★ 默认非公平锁
        Condition notEmpty = lock.newCondition();  // ★ AQS.ConditionObject

        final String[] buffer = new String[1];

        Thread consumer = new Thread(new Runnable() {
            @Override
            public void run() {
                lock.lock();  // ★★ Step Into → acquire(1) → tryAcquire → acquireQueued
                try {
                    while (buffer[0] == null) {
                        System.out.println("    [consumer] 缓冲区空，await...");
                        // ★★ Step Into → ConditionObject.await() → 释放锁 + 进入条件队列 + park
                        notEmpty.await();
                    }
                    System.out.println("    [consumer] 消费: " + buffer[0]);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    lock.unlock(); // ★★ Step Into → release(1) → tryRelease → unparkSuccessor
                }
            }
        }, "consumer");

        consumer.start();
        TimeUnit.SECONDS.sleep(1);

        Thread producer = new Thread(new Runnable() {
            @Override
            public void run() {
                lock.lock();
                try {
                    buffer[0] = "data-42";
                    System.out.println("    [producer] 生产: " + buffer[0]);
                    // ★★ Step Into → signal() → ConditionObject.doSignal → transferForSignal
                    notEmpty.signal();
                } finally {
                    lock.unlock();
                }
            }
        }, "producer");

        producer.start();
        consumer.join();
    }

    // ────────────────────────────────────────────
    // ★ 二、可重入性验证
    //   【掌握要求】
    //   1. ★ 理解可重入锁的含义：同一个线程可以多次获取同一把锁不会死锁
    //   2. 理解 AQS 如何实现可重入：state 字段记录重入次数，每次 lock 加 1，每次 unlock 减 1
    //   3. 理解 getHoldCount() 返回当前线程持有该锁的次数
    //   4. 对比 synchronized 的可重入性（JVM 层面自动支持，通过对象头中的 monitor 记录）
    //   5. 知道不可重入锁的场景：某些需要严格防止递归调用的场合
    // ────────────────────────────────────────────

    static void demoReentrancy() {
        ReentrantLock lock = new ReentrantLock();
        System.out.println("  holdCount 初始: " + lock.getHoldCount()); // 0

        lock.lock();
        System.out.println("  第1次 lock:  holdCount=" + lock.getHoldCount()); // 1

        lock.lock();
        System.out.println("  第2次 lock:  holdCount=" + lock.getHoldCount()); // 2  ★ 证明可重入

        lock.unlock();
        System.out.println("  第1次 unlock: holdCount=" + lock.getHoldCount());

        lock.unlock();
        System.out.println("  第2次 unlock: holdCount=" + lock.getHoldCount());
    }

    // ────────────────────────────────────────────
    // ★ 三、公平锁 vs 非公平锁
    //   【掌握要求】
    //   1. 理解公平锁（hasQueuedPredecessors 检查队列）vs 非公平锁（先 CAS 抢一次）的实现差异
    //   2. 非公平锁的"插队"优势：减少线程切换（刚释放锁的线程更可能还在 CPU 缓存中）
    //   3. ★ 生产环境默认选非公平锁（吞吐量高），只有强制需要 FIFO 顺序才用公平锁
    //   4. 理解"饥饿"问题：非公平锁下某些线程可能长时间获取不到锁
    //   5. Step Into FairSync.tryAcquire() → 看 hasQueuedPredecessors() 如何检测队列前面是否有等待者
    // ────────────────────────────────────────────

    static void demoFairVsNonfair() throws Exception {
        System.out.println("  --- 非公平锁 (默认) ---");
        runFairnessTest(new ReentrantLock(false));

        System.out.println("  --- 公平锁 ---");
        runFairnessTest(new ReentrantLock(true));
        // ★ 公平模式下，线程严格按 FIFO 顺序获得锁，但吞吐量更低
    }

    private static void runFairnessTest(ReentrantLock lock) throws Exception {
        for (int i = 0; i < 3; i++) {
            final int id = i;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    lock.lock();
                    try {
                        System.out.println("    T" + id + " 获得锁, 队列长度=" + lock.getQueueLength());
                    } finally {
                        lock.unlock();
                    }
                }
            }).start();
        }
        TimeUnit.SECONDS.sleep(1);
    }

    // ────────────────────────────────────────────
    // ★ 四、ReadWriteLock — 读共享、写互斥
    //   【掌握要求】
    //   1. ★ 理解读写锁的核心思想：读多写少的场景，让读操作并发，写操作排他
    //   2. 理解 state 字段的高低位拆分：高 16 位 = 读锁计数（共享），低 16 位 = 写锁计数（排他）
    //   3. 理解锁降级（写锁 → 读锁）的流程及其意义（保证数据可见性），不能锁升级
    //   4. 对比 StampedLock（JDK8 新增）的乐观读模式：先读（不加锁）→ 校验 stamp → 不一致则升级为悲观读
    //   5. 理解为什么读锁不能无条件升级为写锁（会死锁：两个读线程同时等写锁）
    //   6. 面试常考：读写锁 vs synchronized 的性能差异及选型
    // ────────────────────────────────────────────

    static void demoReadWriteLock() throws Exception {
        ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
        Lock readLock = rwLock.readLock();
        Lock writeLock = rwLock.writeLock();

        // 两个读线程可以同时持有读锁
        new Thread(new Runnable() {
            @Override
            public void run() {
                readLock.lock();
                try {
                    System.out.println("    [reader-1] 持有读锁");
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException ignored) {
                } finally {
                    readLock.unlock();
                    System.out.println("    [reader-1] 释放读锁");
                }
            }
        }).start();

        TimeUnit.MILLISECONDS.sleep(100);

        new Thread(new Runnable() {
            @Override
            public void run() {
                readLock.lock(); // ★ 读锁可共享，不会阻塞（除非有写线程在等待或持有写锁）
                try {
                    System.out.println("    [reader-2] 同时持有读锁 ★ 证明读锁可共享");
                } finally {
                    readLock.unlock();
                }
            }
        }).start();

        TimeUnit.SECONDS.sleep(2);

        // ★ 写锁是排他的
        writeLock.lock();
        try {
            System.out.println("    [writer] 写锁期间，任何读锁/写锁都无法获取");
        } finally {
            writeLock.unlock();
        }
    }

    // ────────────────────────────────────────────
    // ★ 五、tryLock 超时 — 避免死锁
    //   【掌握要求】
    //   1. 理解 tryLock() vs lock()：tryLock 不会无条件阻塞，返回 false 表示获取失败
    //   2. 理解 tryLock(timeout) 的内部机制：AQS.doAcquireNanos() → parkNanos() 定时阻塞
    //   3. ★ 掌握 tryLock 用于打破死锁循环的经典场景
    //   4. 理解 LockSupport.parkNanos() 和 Thread.sleep() 的区别（parkNanos 可被 unpark 提前唤醒）
    //   5. 能说出 lockInterruptibly() 的语义：在等待锁的过程中响应中断
    // ────────────────────────────────────────────

    static void demoTryLock() throws Exception {
        ReentrantLock lock = new ReentrantLock();

        // 主线程持有锁
        lock.lock();
        System.out.println("  [main] 持锁 2 秒");

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // ★★ tryLock(超时) → AQS.tryAcquireNanos → doAcquireNanos
                    //   等待超时后返回 false，不会永久阻塞
                    boolean got = lock.tryLock(500, TimeUnit.MILLISECONDS);
                    System.out.println("  [t] tryLock 结果: " + got + " ★ 超时退出，避免死等");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        t.start();

        TimeUnit.SECONDS.sleep(2);
        lock.unlock();
        System.out.println("  [main] 释放锁");
        t.join();
    }

    public static void main(String[] args) throws Exception {
        System.out.println("========== 1. ReentrantLock + Condition ==========");
        demoReentrantLock();

        System.out.println("\n========== 2. 可重入性 ==========");
        demoReentrancy();

        System.out.println("\n========== 3. 公平锁 vs 非公平锁 ==========");
        demoFairVsNonfair();

        System.out.println("\n========== 4. ReadWriteLock 读写锁 ==========");
        demoReadWriteLock();

        System.out.println("\n========== 5. tryLock 超时 ==========");
        demoTryLock();
    }
}
