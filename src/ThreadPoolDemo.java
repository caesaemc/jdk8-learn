import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 阶段四：线程池 — ThreadPoolExecutor、FutureTask
 *
 * ★ execute() 三步策略：core → queue → max → reject
 *
 * 断点建议：
 *   [1] executor.execute() → Step Into → ThreadPoolExecutor.execute()
 *       → 观察 ctl 位运算、workerCount < corePoolSize 分支
 *   [2] ThreadPoolExecutor.Worker.run() → runWorker() → getTask()
 *       → 看 Worker 如何阻塞从任务队列取任务
 *   [3] FutureTask.run() → set() → 看 CAS 设置结果 → finishCompletion 唤醒等待者
 */
public class ThreadPoolDemo {

    // ────────────────────────────────────────────
    // ★ 一、手动配置 ThreadPoolExecutor — 7 个核心参数
    //   【掌握要求】
    //   1. ★ 熟记 execute() 三步策略：
    //      workerCount < corePoolSize → 新建核心线程
    //      workerCount ≥ corePoolSize ∧ 队列未满 → 入队
    //      workerCount ≥ corePoolSize ∧ 队列已满 ∧ workerCount < maxPoolSize → 新建临时线程
    //      workerCount == maxPoolSize ∧ 队列已满 → 执行拒绝策略
    //   2. 理解 ctl 字段的位运算设计：高 3 位 = 线程池运行状态（RUNNING/SHUTDOWN/STOP/TIDYING/TERMINATED）
    //      低 29 位 = 工作线程数（workerCount），一个 AtomicInteger 打包两个信息
    //   3. 理解 Worker 内部类：本身是 AQS 子类（实现不可重入的互斥锁），同时包装 Thread 和任务
    //   4. 理解 keepAliveTime + 空闲线程回收：仅对超出 corePoolSize 的线程生效
    //   5. ★ 生产环境永远用有界队列（ArrayBlockingQueue / LinkedBlockingQueue(capacity)），防止 OOM
    //   6. 理解 shutdown() vs shutdownNow()：
    //      shutdown() → 不再接受新任务，等已有任务执行完
    //      shutdownNow() → 不再接受新任务，中断正在执行的任务，返回未执行的任务列表
    //   7. 知道 ThreadFactory 的作用：自定义线程名、设置守护线程、设置优先级
    // ────────────────────────────────────────────

    static void demoThreadPoolConfig() throws Exception {
        // ★ 核心参数说明：
        //   corePoolSize=2   → 常驻线程数（即使空闲也不回收，除非 allowCoreThreadTimeOut）
        //   maxPoolSize=4    → 最大线程数
        //   keepAlive=2s     → 超出 core 的空闲线程存活时间
        //   ArrayBlockingQueue(2) → 有限队列（生产环境强烈建议有界队列）
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2, 4,
                2, TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(2),
                new ThreadFactory() {  // ★ 自定义线程名，方便调试
                    private final AtomicInteger count = new AtomicInteger(1);
                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "pool-worker-" + count.getAndIncrement());
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy() // ★ 拒绝策略：由调用线程执行
        );

        // 提交 7 个任务（core=2 + queue=2 + 临时线程=2 + 溢出=1）
        for (int i = 0; i < 7; i++) {
            final int taskId = i;
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    System.out.println("    [任务" + taskId + "] 由 " + Thread.currentThread().getName() + " 执行");
                    try { TimeUnit.SECONDS.sleep(1); }
                    catch (InterruptedException ignored) {}
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }

    // ────────────────────────────────────────────
    // ★ 二、Future / FutureTask — 异步结果载体
    //   【掌握要求】
    //   1. ★ 理解 Future 的设计模式：提交任务不阻塞 → 返回"未来结果"的凭证 → 需要时 get()
    //   2. 理解 FutureTask 的状态机：NEW → COMPLETING → NORMAL / EXCEPTIONAL / CANCELLED / INTERRUPTING / INTERRUPTED
    //   3. 理解 awaitDone() 的自旋 + park 机制：先自旋一定次数避免上下文切换开销，然后 park 等待
    //   4. 理解 finishCompletion()：遍历 waiters 链表（Treiber stack），逐个 unpark 唤醒
    //   5. 理解 RunnableFuture 接口：同时实现 Runnable + Future，所以 FutureTask 可以直接给 Thread 或 Executor
    //   6. 知道 submit(Callable) 内部实际包装成了 FutureTask
    // ────────────────────────────────────────────

    static void demoFutureTask() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // ★ Callable 返回计算结果
        Callable<Integer> task = new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                System.out.println("    [task] 正在计算...");
                TimeUnit.SECONDS.sleep(1);
                return 42;
            }
        };

        // ★★ Future 代表"未来某个时刻可用的结果"
        Future<Integer> future = executor.submit(task);

        System.out.println("    [main] 提交后继续干别的事...");
        System.out.println("    isDone=" + future.isDone()); // false

        // ★★ future.get() 阻塞直到结果就绪
        // Step Into → FutureTask.get() → awaitDone() → park 自旋等待
        Integer result = future.get();
        System.out.println("    [main] 结果=" + result + ", isDone=" + future.isDone());

        executor.shutdown();
    }

    // ────────────────────────────────────────────
    // ★ 三、FutureTask 手动使用（不用线程池）
    //   【掌握要求】
    //   1. 理解 FutureTask 的灵活之处：既是 Runnable 又是 Future，不依赖线程池
    //   2. 理解 FutureTask.run() → Callable.call() → set(result) 的完整链路
    //   3. 理解 CAS 在设置结果中的作用：多个线程可能同时调用 get()，需要 CAS 安全地改变状态
    //   4. 理解 cancel(mayInterruptIfRunning) 的行为差异
    // ────────────────────────────────────────────

    static void demoFutureTaskDirect() throws Exception {
        // FutureTask 本身就是 Runnable + Future
        FutureTask<String> ft = new FutureTask<String>(new Callable<String>() {
            @Override
            public String call() {
                return "直接通过 Thread 运行";
            }
        });

        // ★★ Step Into ft.run() → 看 Callable 执行 → set(result) → CAS 设置 outcome
        new Thread(ft, "manual-future").start();

        // ★★ Step Into ft.get() → 看 awaitDone(false, 0L) 自旋 + park
        System.out.println("    FutureTask 结果: " + ft.get());
    }

    // ────────────────────────────────────────────
    // ★ 四、4 种拒绝策略对比
    //   【掌握要求】
    //   1. 熟记 4 种拒绝策略：
    //      AbortPolicy（默认）→ 抛 RejectedExecutionException
    //      CallerRunsPolicy → 提交任务的线程自己执行
    //      DiscardPolicy → 静默丢弃，不抛异常
    //      DiscardOldestPolicy → 丢弃队头最老任务，再尝试提交
    //   2. 理解 CallerRunsPolicy 的双面性：减缓提交速度（反压），但可能阻塞主线程
    //   3. ★ 通常选 CallerRunsPolicy + 记录日志/告警（监控拒绝次数），让系统有自保能力
    //   4. 知道可以自定义 RejectedExecutionHandler
    // ────────────────────────────────────────────

    static void demoRejectionPolicies() {
        ExecutorService pool = new ThreadPoolExecutor(
                1, 1, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(1) // 容量=1，容易触发拒绝
        );

        // 先提交两个任务填满 core 和 queue
        pool.execute(new Runnable() {
            @Override
            public void run() {
                try { TimeUnit.SECONDS.sleep(5); }
                catch (InterruptedException ignored) {}
            }
        });
        pool.execute(new Runnable() {
            @Override
            public void run() {
                try { TimeUnit.SECONDS.sleep(5); }
                catch (InterruptedException ignored) {}
            }
        });

        // ★ 第3个任务触发拒绝策略
        try {
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    System.out.println("   不会执行到这里");
                }
            });
        } catch (RejectedExecutionException e) {
            System.out.println("    ★ 默认 AbortPolicy 抛出 RejectedExecutionException");
        }

        pool.shutdownNow();
    }

    // ────────────────────────────────────────────
    // ★ 五、ScheduledThreadPoolExecutor — 定时调度
    //   【掌握要求】
    //   1. 理解 DelayedWorkQueue：基于堆的延迟队列（类似 DelayQueue + PriorityQueue）
    //   2. 理解 scheduleAtFixedRate vs scheduleWithFixedDelay：
    //      FixedRate → 以上次任务开始时间为基准，固定周期
    //      FixedDelay → 以上次任务结束时间为基准，固定间隔
    //   3. 理解 ScheduledFutureTask 如何包装 Runnable + 时间信息
    //   4. 知道 Timer 的缺陷（单线程、异常终止）→ 为什么用 ScheduledThreadPoolExecutor 替代
    // ────────────────────────────────────────────

    static void demoScheduled() throws Exception {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

        System.out.println("    当前时间: " + System.currentTimeMillis() % 100000);

        // ★ schedule：延迟执行一次
        scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                System.out.println("    [延迟1s] 执行时间: " + System.currentTimeMillis() % 100000);
            }
        }, 1, TimeUnit.SECONDS);

        // ★ scheduleAtFixedRate：固定频率执行
        ScheduledFuture<?> periodic = scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                System.out.println("    [周期任务] " + System.currentTimeMillis() % 100000);
            }
        }, 0, 500, TimeUnit.MILLISECONDS);

        TimeUnit.SECONDS.sleep(2);
        periodic.cancel(false);
        System.out.println("    已取消周期任务");
        scheduler.shutdown();
    }

    public static void main(String[] args) throws Exception {
        System.out.println("========== 1. 手动配置 ThreadPoolExecutor ==========");
        demoThreadPoolConfig();

        System.out.println("\n========== 2. Future + FutureTask ==========");
        demoFutureTask();

        System.out.println("\n========== 3. FutureTask 手动使用 ==========");
        demoFutureTaskDirect();

        System.out.println("\n========== 4. 拒绝策略 ==========");
        demoRejectionPolicies();

        System.out.println("\n========== 5. 定时调度 ==========");
        demoScheduled();
    }
}
