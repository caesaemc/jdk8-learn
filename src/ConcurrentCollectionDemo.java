import java.util.concurrent.*;
import java.util.*;

/**
 * 阶段五：并发集合 — ConcurrentHashMap、CopyOnWriteArrayList、阻塞队列
 *
 * 断点建议：
 *   [1] ConcurrentHashMap.put() → Step Into → putVal() → 观察 CAS tabAt + synchronized(f)
 *   [2] putVal 中树化逻辑 treeifyBin() → 链表长度 ≥ 8 时触发
 *   [3] LinkedBlockingQueue.put() → 观察 putLock 和 takeLock 分离设计
 */
public class ConcurrentCollectionDemo {

    // ────────────────────────────────────────────
    // ★ 一、ConcurrentHashMap (JDK 8 重新实现)
    //   【掌握要求】
    //   1. ★ 理解 JDK7 → JDK8 的最大变化：抛弃 Segment 分段锁（16段），改用 CAS + synchronized 锁单 bin 头节点
    //   2. 理解 putVal() 的无锁插入路径：空槽 → CAS tabAt(i) 直接设置，不用锁
    //   3. 理解 putVal() 的有锁插入路径：槽非空 → synchronized(f) 锁住头节点 → 遍历链表/红黑树
    //   4. ★ 理解树化逻辑：链表长度 ≥ 8（TREEIFY_THRESHOLD）且数组长度 ≥ 64 → 链表转红黑树
    //   5. 理解为什么默认 capacity 是 2 的幂（方便用 & (n-1) 替代 % 做 hash 取模）
    //   6. 理解 sizeCtl 的三重语义：-1=正在初始化, -(1+n)=n个线程在扩容, 正数=扩容阈值(=0.75n)
    //   7. 理解扩容时的多线程协助（helpTransfer）：其他线程发现正在扩容时帮忙复制槽数据
    //   8. 理解 ConcurrentHashMap 的弱一致性：size() / isEmpty() 是近似值（不锁全局）
    //   9. ★ 对比 Hashtable（全表锁）→ Collections.synchronizedMap → JDK7 Segment → JDK8 bin 锁 的演进
    // ────────────────────────────────────────────

    static void demoConcurrentHashMap() throws Exception {
        // ★★ JDK8 抛弃了 Segment 分段锁，改用 CAS + synchronized
        //    粒度细化到单个 bin（数组槽）的头节点
        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();

        int threads = 4;
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < 1000; i++) {
                        // ★ Step Into → putVal() →
                        //   1. CAS tabAt(i) 尝试设置空槽（无锁）
                        //   2. 槽非空 → synchronized(f) 锁住头节点
                        map.put(Thread.currentThread().getName() + "-key-" + i, i);
                    }
                    latch.countDown();
                }
            }).start();
        }

        latch.await();
        System.out.println("  ConcurrentHashMap size=" + map.size());
    }

    // ────────────────────────────────────────────
    // ★ 二、HashMap 并发问题（对比）
    //   【掌握要求】
    //   1. 理解 HashMap 在并发下三大问题：数据丢失 / 死循环（JDK7 resize 环形链表）/ size 不准
    //   2. JDK7 resize 时头插法导致环形链表的原理（面试高频）
    //   3. JDK8 改成尾插法避免了死循环，但数据丢失问题依然存在
    //   4. ★ 知道什么场景绝对不能用 HashMap：多线程读可以，但只要有任何写线程就必须用 ConcurrentHashMap
    // ────────────────────────────────────────────

    static void demoHashMapConcurrency() {
        // ★ 非线程安全的 HashMap 在多线程 put 时可能造成：
        //   1. 数据丢失（两个线程同时 put 到同一槽）
        //   2. 死循环（JDK7 resize 时的环形链表）
        //   3. size 不准确
        HashMap<String, Integer> map = new HashMap<>();
        try {
            for (int i = 0; i < 1000; i++) {
                final int val = i;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        map.put("key-" + val, val);
                    }
                }).start();
            }
            TimeUnit.MILLISECONDS.sleep(200);
            System.out.println("  ★ HashMap 并发 put: size=" + map.size() + " (期望=1000, 可能丢失)");
        } catch (Exception e) {
            System.out.println("  HashMap 并发异常: " + e.getClass().getSimpleName());
        }
    }

    // ────────────────────────────────────────────
    // ★ 三、CopyOnWriteArrayList — 写时复制
    //   【掌握要求】
    //   1. ★ 理解 CopyOnWrite（写时复制）的核心思想：写操作复制整份数据，读写不互斥
    //   2. 理解 ReentrantLock + Arrays.copyOf() 的内部实现
    //   3. 理解 COWIterator 是快照迭代器：遍历期间修改不可见，不抛 ConcurrentModificationException
    //   4. ★ 适用场景：读远多于写（如配置信息、监听器列表），写多会 OOM
    //   5. 理解 CopyOnWriteArraySet 底层实际是 CopyOnWriteArrayList（addIfAbsent）
    // ────────────────────────────────────────────

    static void demoCopyOnWrite() {
        // ★ 每次写操作都会复制整个底层数组（array = Arrays.copyOf(原数组, 新长度)）
        //   适合读多写少的场景
        CopyOnWriteArrayList<String> list = new CopyOnWriteArrayList<>();
        list.add("A");
        list.add("B");
        list.add("C");

        // ★ 迭代器是快照：遍历期间即使有其他线程修改也不抛 ConcurrentModificationException
        System.out.print("  遍历中: ");
        for (String s : list) {
            list.add("NEW-" + s); // 遍历的同时修改（实际修改的是新数组）
            System.out.print(s + " ");
        }
        System.out.println("\n  最终列表: " + list);
    }

    // ────────────────────────────────────────────
    // ★ 四、ArrayBlockingQueue — 有界阻塞队列（一把锁）
    //   【掌握要求】
    //   1. 理解 ArrayBlockingQueue 的设计：一把 ReentrantLock + 两个 Condition（notEmpty + notFull）
    //   2. 理解环形数组（循环队列）的 putIndex / takeIndex 循环移动
    //   3. 理解生产者-消费者模式与阻塞队列的对应：put()=生产、take()=消费
    //   4. 理解 offer()/poll() vs put()/take() 的区别：前者非阻塞/超时，后者阻塞
    //   5. 知道 drainTo() 方法可以批量取走元素
    // ────────────────────────────────────────────

    static void demoArrayBlockingQueue() throws Exception {
        // ★ 一把 ReentrantLock + 两个 Condition（notEmpty + notFull）
        ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(3);

        // 生产者填满队列
        Thread producer = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    for (int i = 1; i <= 5; i++) {
                        // ★★ put() 队列满时阻塞 → Condition.notFull.await()
                        queue.put("P" + i);
                        System.out.println("    [producer] put P" + i + ", 队列大小=" + queue.size());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        Thread consumer = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    TimeUnit.MILLISECONDS.sleep(500);
                    for (int i = 1; i <= 5; i++) {
                        // ★★ take() 队列空时阻塞 → Condition.notEmpty.await()
                        String item = queue.take();
                        System.out.println("    [consumer] took " + item + ", 队列大小=" + queue.size());
                        TimeUnit.MILLISECONDS.sleep(200);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();
    }

    // ────────────────────────────────────────────
    // ★ 五、LinkedBlockingQueue — 无界/有界阻塞队列（两把锁）
    //   【掌握要求】
    //   1. ★ 理解双锁设计：putLock + takeLock 分离，让生产者和消费者可以并发操作队尾和队头
    //   2. 理解锁分离为什么能提高吞吐量：put 修改 last 节点，take 修改 head 节点，互不干扰
    //   3. 理解"cascade notify"（级联通知）：count 从 0→1 时 put 方通知 notEmpty，从 capacity→capacity-1 时 take 方通知 notFull
    //   4. 理解 LinkedBlockingQueue 默认容量是 Integer.MAX_VALUE（近似无界），生产环境必须指定 capacity
    //   5. ★ 对比 ArrayBlockingQueue vs LinkedBlockingQueue：前者内存连续+预分配，后者动态节点+GC压力
    // ────────────────────────────────────────────

    static void demoLinkedBlockingQueue() throws Exception {
        // ★★ putLock + takeLock 分离：put 和 take 操作分别加锁
        //    生产者和消费者可以并发操作队头和队尾，提高吞吐量
        LinkedBlockingQueue<Integer> queue = new LinkedBlockingQueue<>();

        CountDownLatch latch = new CountDownLatch(2);
        long start = System.nanoTime();

        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 10000; i++) {
                    try { queue.put(i); }
                    catch (InterruptedException e) { break; }
                }
                latch.countDown();
            }
        }, "producer").start();

        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 10000; i++) {
                    try { queue.take(); }
                    catch (InterruptedException e) { break; }
                }
                latch.countDown();
            }
        }, "consumer").start();

        latch.await();
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        // ★ 因为 take/put 由不同锁保护，生产者和消费者可并行运行
        System.out.println("  LinkedBlockingQueue 10000 put+take 耗时: " + elapsed + "ms");
    }

    // ────────────────────────────────────────────
    // ★ 六、ConcurrentLinkedQueue — 无锁队列
    //   【掌握要求】
    //   1. ★ 理解 Michael-Scott 无锁队列算法：基于 CAS 操作 head/tail 指针
    //   2. 理解松弛（slack）设计：tail 不一定指向尾部节点（允许滞后 1-2 个节点），减少 CAS 竞争
    //   3. 理解哨兵节点模式：head 始终指向一个 dummy 节点
    //   4. 比较 ConcurrentLinkedQueue vs LinkedBlockingQueue：
    //      前者完全无锁、非阻塞、不会让线程 park；后者有锁、可阻塞
    // ────────────────────────────────────────────

    static void demoConcurrentLinkedQueue() throws Exception {
        // ★ 完全无锁，基于 CAS 的 Michael & Scott 算法
        ConcurrentLinkedQueue<Integer> queue = new ConcurrentLinkedQueue<>();

        CountDownLatch latch = new CountDownLatch(2);

        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 1000; i++) {
                    // ★ offer() 用 CAS 操作 tail 指针
                    queue.offer(i);
                }
                latch.countDown();
            }
        }).start();

        new Thread(new Runnable() {
            @Override
            public void run() {
                int count = 0;
                while (count < 1000) {
                    // ★ poll() 用 CAS 操作 head 指针
                    Integer v = queue.poll();
                    if (v != null) count++;
                }
                latch.countDown();
            }
        }).start();

        latch.await();
        System.out.println("  ConcurrentLinkedQueue 剩余元素: " + queue.size());
    }

    public static void main(String[] args) throws Exception {
        System.out.println("========== 1. ConcurrentHashMap (JDK8 CAS+synchronized) ==========");
        demoConcurrentHashMap();

        System.out.println("\n========== 2. HashMap 并发写入（对比） ==========");
        demoHashMapConcurrency();

        System.out.println("\n========== 3. CopyOnWriteArrayList ==========");
        demoCopyOnWrite();

        System.out.println("\n========== 4. ArrayBlockingQueue (一把锁) ==========");
        demoArrayBlockingQueue();

        System.out.println("\n========== 5. LinkedBlockingQueue (两把锁) ==========");
        demoLinkedBlockingQueue();

        System.out.println("\n========== 6. ConcurrentLinkedQueue (无锁) ==========");
        demoConcurrentLinkedQueue();
    }
}
