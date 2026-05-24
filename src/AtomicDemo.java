import java.util.concurrent.atomic.*;
import java.util.concurrent.*;

/**
 * 阶段二：原子类与 CAS — 无锁并发的基石
 *
 * 核心要义：所有原子操作最终调用 Unsafe.compareAndSwap* 系列 native 方法，
 * 利用 CPU 的 CAS 指令（cmpxchg）实现一条指令的"比较并交换"。
 *
 * 断点建议：
 *   [1] atomicInt.incrementAndGet() 处 → Step Into → 看自旋循环中的 compareAndSwapInt
 *   [2] atomicRef.compareAndSet() 处 → Step Into → 看 Unsafe.compareAndSwapObject
 *   [3] Striped64.longAccumulate() → 高并发分段累加的核心
 */
public class AtomicDemo {

    // ────────────────────────────────────────────
    // ★ 一、AtomicInteger — CAS 自旋 + Unsafe
    //   【掌握要求】
    //   1. 理解 CAS（Compare-And-Swap）的三要素：内存地址V / 期望值A / 新值B
    //   2. ★ 掌握 CAS 的"乐观锁"思想：先操作，再检查（区别于 synchronized 的悲观锁）
    //   3. ★ 理解自旋（spin）的含义：CAS 失败 → 在循环中不断重试 → 直到成功
    //   4. 理解 CAS 的三大问题：ABA 问题 / 循环开销大 / 只能保证一个共享变量原子性
    //   5. Step Into Unsafe.compareAndSwapInt() → 看到 native 声明 → 理解这是 CPU 级别的 cmpxchg 指令
    //   6. 理解 AtomicInteger 的 value 为什么用 volatile 修饰（保证 CAS 操作间的可见性）
    //   7. 能解释 CAS 为什么比 synchronized 快（无上下文切换、无锁竞争开销，但高竞争时反而更差）
    // ────────────────────────────────────────────

    static void demoAtomicInteger() throws Exception {
        AtomicInteger atomicInt = new AtomicInteger(0);
        int threads = 10;
        int perThread = 1000;
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int j = 0; j < perThread; j++) {
                        // ★ 打断点在这里 → Step Into
                        //   → AtomicInteger.incrementAndGet()
                        //   → Unsafe.getAndAddInt() 中的 do-while + compareAndSwapInt
                        atomicInt.incrementAndGet();
                    }
                    latch.countDown();
                }
            }).start();
        }

        latch.await();
        int expected = threads * perThread;
        System.out.println("  AtomicInteger 最终值=" + atomicInt.get()
                + " (期望=" + expected + ", 正确=" + (atomicInt.get() == expected) + ")");
    }

    // ────────────────────────────────────────────
    // ★ 二、普通 int 的并发问题（作为对比）
    //   【掌握要求】
    //   1. ★ 深刻理解 i++ 不是原子操作：实际上是 读内存 → 寄存器加1 → 写回内存 三步
    //   2. 理解竞态条件（race condition）：多个线程同时读写共享变量导致的不确定性结果
    //   3. 知道 synchronized 也能解决此问题，但开销比 AtomicInteger 大得多
    //   4. 能解释为什么这里的结果每次都不同（线程调度的随机性）
    // ────────────────────────────────────────────

    private static int plainInt = 0;

    static void demoPlainInt() throws Exception {
        plainInt = 0;
        int threads = 10;
        int perThread = 1000;
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int j = 0; j < perThread; j++) {
                        // ++ 不是原子操作：读 → 加 1 → 写，三步之间可被其他线程打断
                        plainInt++;
                    }
                    latch.countDown();
                }
            }).start();
        }

        latch.await();
        int expected = threads * perThread;
        System.out.println("  普通 int 最终值=" + plainInt
                + " (期望=" + expected + ", 丢失=" + (expected - plainInt) + ")");
    }

    // ────────────────────────────────────────────
    // ★ 三、AtomicReference — 对象级 CAS
    //   【掌握要求】
    //   1. 理解 AtomicReference 用 equals 还是 == 做比较（答案是 ==，即比较内存地址）
    //   2. 掌握 CAS 的经典编程范式：do { old = get(); newVal = compute(old); } while (!cas(old, newVal));
    //   3. 能对比 AtomicReference 和 AtomicStampedReference 的 API 差异
    //   4. 理解 AtomicReference 适合什么场景：简单对象引用的无锁更新
    // ────────────────────────────────────────────

    static void demoAtomicReference() {
        AtomicReference<String> ref = new AtomicReference<>("A");

        // CAS 的经典写法：compare-and-swap 循环
        // ★ 打断点到 compareAndSet → Step Into → 看 native compareAndSwapObject
        boolean ok = ref.compareAndSet("A", "B");
        System.out.println("  CAS A→B: " + ok + ", 当前值=" + ref.get());

        ok = ref.compareAndSet("A", "C"); // 预期是 A，实际是 B → 失败
        System.out.println("  CAS A→C (预期错误): " + ok + ", 当前值=" + ref.get());
    }

    // ────────────────────────────────────────────
    // ★ 四、ABA 问题与 AtomicStampedReference
    //   【掌握要求】
    //   1. ★ 能用一句话描述 ABA：线程1以为值没变(还是A)，但实际上 A→B→A 已经被其他线程改过
    //   2. 理解 ABA 在哪些场景下有危害：链表操作（节点被删除又新建），普通计数器则无所谓
    //   3. 掌握 AtomicStampedReference 的解决方案：比较值 + 比较版本号（双重 CAS）
    //   4. 理解 AtomicMarkableReference（简化版，只标记 true/false 是否被修改过）
    //   5. 知道 JDK 中哪些地方会遭遇 ABA：AQS的无锁队列、ConcurrentLinkedQueue 等
    //   6. 能举出至少一个 ABA 导致 bug 的实际例子
    // ────────────────────────────────────────────

    static void demoABA() throws Exception {
        // 普通 AtomicReference 无法检测 ABA
        AtomicReference<String> plain = new AtomicReference<>("X");

        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                // t1 读到 X，准备 CAS X→Z, 但被 t2 抢先
                String old = plain.get();
                try { TimeUnit.MILLISECONDS.sleep(500); } catch (InterruptedException ignored) {}
                boolean ok = plain.compareAndSet(old, "Z");
                System.out.println("  [t1] CAS " + old + "→Z: " + ok + " (可能踩到 ABA)");
            }
        });

        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                plain.compareAndSet("X", "Y");   // X → Y
                plain.compareAndSet("Y", "X");   // Y → X  （ABA：最终值又是 X）
                System.out.println("  [t2] 完成 X→Y→X (制造 ABA)");
            }
        });

        t2.start(); t2.join(); t1.start(); t1.join();

        // ★ AtomicStampedReference 通过版本号（stamp）检测 ABA
        AtomicStampedReference<String> stamped = new AtomicStampedReference<>("X", 0);
        int[] stampHolder = new int[1];

        // t3: 先读值和版本号，再等待，最后尝试 CAS
        //    此时版本号已经被 t4 修改过，所以 CAS 应该失败
        Thread t3 = new Thread(new Runnable() {
            @Override
            public void run() {
                String val = stamped.get(stampHolder);
                int stamp = stampHolder[0];
                System.out.println("  [t3] 读到值=" + val + ", 版本=" + stamp);
                try {
                    // 等待足够长时间让 t4 完成 X→Y→X 的 ABA 操作
                    TimeUnit.MILLISECONDS.sleep(500);
                } catch (InterruptedException ignored) {}
                boolean ok = stamped.compareAndSet(val, "Z", stamp, stamp + 1);
                System.out.println("  [t3] Stamped CAS (期望版本=" + stamp + ") X→Z: " + ok
                        + " ★ 期望失败，因为版本号已变");
            }
        });

        Thread t4 = new Thread(new Runnable() {
            @Override
            public void run() {
                String val = stamped.get(stampHolder);
                int s = stampHolder[0];
                stamped.compareAndSet(val, "Y", s, s + 1);
                val = stamped.get(stampHolder);
                s = stampHolder[0];
                stamped.compareAndSet(val, "X", s, s + 1);
                System.out.println("  [t4] 完成 X→Y→X (制造 ABA, 最终版本=2)");
            }
        });

        t3.start();
        TimeUnit.MILLISECONDS.sleep(50); // 让 t3 先读到 stamp=0 再启动 t4
        t4.start();
        t3.join();
        t4.join();
    }

    // ────────────────────────────────────────────
    // ★ 五、LongAdder — 高并发分段累加器
    //   【掌握要求】
    //   1. ★ 理解 LongAdder 的核心思想：空间换时间 + 热点分散
    //   2. 理解 Striped64 的 Cell[] 数组结构：每个 Cell 是一个独立的计数器，由不同线程操作
    //   3. 理解 LongAdder vs AtomicLong 的适用场景：
    //      - 高并发写 → LongAdder 远优于 AtomicLong（Cell 分散竞争）
    //      - 需要精确瞬时值 → AtomicLong（get() 是精确值，sum() 是近似值）
    //   4. Step Into Striped64.longAccumulate() → 看 Cell 初始化、扩容（x2）、CAS 切换 Cell
    //   5. 理解 LongAdder.sum() 为什么是"弱一致性"的（遍历 Cell[] 的过程中可能有新写入）
    //   6. 了解 LongAccumulator / DoubleAdder / DoubleAccumulator 作为变体
    // ────────────────────────────────────────────

    static void demoLongAdder() throws Exception {
        // LongAdder 内部维护一个 Cell[] 数组（Striped64 子类）
        // 高并发时各线程操作不同 Cell，最后 sum() 汇总
        // ★ 打断点到 add(1) → Step Into → Striped64.longAccumulate()
        LongAdder adder = new LongAdder();
        int threads = 8;
        CountDownLatch latch = new CountDownLatch(threads);

        long start = System.nanoTime();
        for (int i = 0; i < threads; i++) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int j = 0; j < 100_000; j++) {
                        adder.add(1);
                    }
                    latch.countDown();
                }
            }).start();
        }
        latch.await();
        long elapsed = System.nanoTime() - start;

        System.out.println("  LongAdder sum=" + adder.sum() + " (期望=" + (threads * 100_000L) + ")"
                + ", 耗时=" + TimeUnit.NANOSECONDS.toMillis(elapsed) + "ms");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("========== 1. AtomicInteger 并发累加 ==========");
        demoAtomicInteger();

        System.out.println("\n========== 2. 普通 int 并发累加（对比） ==========");
        demoPlainInt();

        System.out.println("\n========== 3. AtomicReference CAS ==========");
        demoAtomicReference();

        System.out.println("\n========== 4. ABA 问题与 AtomicStampedReference ==========");
        demoABA();

        System.out.println("\n========== 5. LongAdder 高并发累加 ==========");
        demoLongAdder();
    }
}
