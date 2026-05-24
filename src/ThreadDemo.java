import java.util.concurrent.TimeUnit;

/**
 * 阶段一：线程基础 — Thread 状态、volatile 可见性、wait/notify
 *
 * 断点建议：
 *   [1] demoThreadLifecycle() → t.start() 后 → Step Into Thread.run()
 *   [2] demoVolatileVisibility() → while 循环 → 观察非 volatile 下 reader 能否退出
 *   [3] demoWaitNotify() → wait() 处打断点 → Step Into Object.wait()
 */
public class ThreadDemo {

    // ────────────────────────────────────────────
    // ★ 一、线程生命周期
    //   【掌握要求】
    //   1. 熟记 6 种线程状态：NEW → RUNNABLE → BLOCKED / WAITING / TIMED_WAITING → TERMINATED
    //   2. 能画出状态转换图：synchronized 等锁 → BLOCKED；wait()/park() → WAITING；sleep() → TIMED_WAITING
    //   3. 理解 start() vs run() 的区别：start() 才会创建新线程，run() 只是普通方法调用
    //   4. 理解 join() 的语义：当前线程等待目标线程终止
    //   5. Step Into Thread.java 源码，看 start() → native start0() 的 OS 线程创建过程
    // ────────────────────────────────────────────
    static void demoThreadLifecycle() throws InterruptedException {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                printState("    run() 入口");

                // ── 1. TIMED_WAITING ──
                try { TimeUnit.SECONDS.sleep(1); } catch (InterruptedException ignored) {}
                printState("    sleep(1s) 后");

                // ── 2. BLOCKED ──
                synchronized (ThreadDemo.class) {
                    printState("    获得锁");
                }
            }
        }, "lifecycle-thread");

        printState("new 后 (NEW)");          // → NEW
        t.start();
        printState("start() 后 (RUNNABLE)"); // → RUNNABLE

        // 主线程先持锁，让 t 阻塞在 synchronized 上
        synchronized (ThreadDemo.class) {
            TimeUnit.MILLISECONDS.sleep(1500); // 等 t 从 sleep 醒来进入 BLOCKED
            printState("主线程持有锁期间，t 的状态");
            // ★ 此时 t 极大概率处于 BLOCKED
        } // 释放锁 → t 进入 RUNNABLE → 获得锁 → TERMINATED

        t.join();
        printState("t 终止后 (TERMINATED)");
    }

    // ────────────────────────────────────────────
    // ★ 二、volatile 可见性与指令重排
    //   【掌握要求】
    //   1. 理解 JMM（Java Memory Model）中的主内存与工作内存模型
    //   2. ★ volatile 的两大语义：① 保证可见性（写后立即刷新到主存）② 禁止指令重排（内存屏障）
    //   3. volatile 不保证原子性！i++ 依然是三步操作
    //   4. 知道 CPU 缓存一致性协议（MESI）是 volatile 的硬件基础
    //   5. 能写出 volatile 的经典使用场景：状态标志、DCL（双重检查锁定）单例
    //   6. 理解 happens-before 原则：volatile 写 happens-before 后续的 volatile 读
    //   7. 【动手】去掉 volatile 修饰，用 -server 模式跑多遍，观察 reader 不退出
    // ────────────────────────────────────────────

    // 【对比】去掉 volatile 后 reader 可能永不退出（JIT 将 flag 缓存在寄存器/CPU cache）
    // ★ 打断点到 reader 的 while(body) 内，观察有/无 volatile 的行为差异
    private static volatile boolean flag = true;

    static void demoVolatileVisibility() throws InterruptedException {
        Thread reader = new Thread(new Runnable() {
            @Override
            public void run() {
                // ★ Step Into 这行 → 观察 JIT 优化后 flag 的读取来源
                while (flag) {
                    // 空循环：不写任何东西，JIT 更容易把 flag 优化掉
                    // 如果 flag 不是 volatile，这个线程可能永远不会退出
                }
                System.out.println("    [reader] 读到 flag=false，退出循环");
            }
        }, "volatile-reader");
        reader.start();

        TimeUnit.SECONDS.sleep(2);
        // ★ 打断点在这行 → 然后观察 reader 是否退出
        flag = false;
        System.out.println("    [main] 已设置 flag=false");

        reader.join(3000);
        if (reader.isAlive()) {
            System.out.println("    ★ 警告：reader 线程未退出！去掉 volatile 复现此现象");
            reader.interrupt();
        }
    }

    // ────────────────────────────────────────────
    // ★ 三、wait / notify 线程协作
    //   【掌握要求】
    //   1. ★ wait() 的三个关键行为：① 释放锁 ② 线程进入 WAITING ③ 被 notify 后重新竞争锁（不是立即执行）
    //   2. 理解为什么 wait() 必须在 synchronized 块内调用（需要先持有对象监视器）
    //   3. ★ 为什么用 while(!condition) 而不是 if：防止虚假唤醒（spurious wakeup）
    //   4. 理解 wait() vs sleep() 的区别：wait 释放锁，sleep 不释放锁
    //   5. 理解 notify() vs notifyAll()：notify 随机唤醒一个，notifyAll 唤醒所有
    //   6. 能用 wait/notify 手写一个生产者-消费者模型
    //   7. Step Into Object.wait() 的 native 实现 → 看 JVM 底层如何操作监视器（monitor）
    // ────────────────────────────────────────────

    private static final Object LOCK = new Object();
    private static boolean ready = false;

    static void demoWaitNotify() throws InterruptedException {
        Thread waiter = new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (LOCK) {
                    try {
                        // ★ 打断点在这里 → Step Into Object.wait()
                        //   观察 wait() 如何释放锁、线程进入 WAITING 状态、
                        //   被 notify 后如何重新竞争锁
                        while (!ready) {
                            System.out.println("    [waiter] 条件不满足，进入 wait...");
                            LOCK.wait();  // ★★ 释放 LOCK 锁，线程进入 WAITING
                        }
                        System.out.println("    [waiter] 被唤醒，条件满足，继续执行");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }, "waiter");

        waiter.start();
        TimeUnit.SECONDS.sleep(1); // 确保 waiter 已进入 wait()

        // 通知线程
        synchronized (LOCK) {
            ready = true;
            // ★ 打断点在这里 → Step Into notify()
            LOCK.notify();  // ★★ 唤醒一个在 LOCK 上 wait 的线程
            System.out.println("    [main] 已设置 ready=true 并 notify");
        }
        waiter.join();
    }

    // ────────────────────────────────────────────

    static void printState(String label) {
        Thread t = Thread.currentThread();
        // ★ Thread.getState() 返回 6 种状态之一
        System.out.println("  " + label + " → " + t.getName() + " 状态=" + t.getState());
    }

    public static void main(String[] args) throws Exception {
        System.out.println("========== 1. 线程生命周期 ==========");
        demoThreadLifecycle();

        // System.out.println("\n========== 2. volatile 可见性 ==========");
        // demoVolatileVisibility();

        // System.out.println("\n========== 3. wait / notify 协作 ==========");
        // demoWaitNotify();
    }
}
