import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 阶段六：同步工具 + CompletableFuture
 *
 * 断点建议：
 *   [1] CountDownLatch.await() → Step Into → AQS.acquireSharedInterruptibly → 共享模式
 *   [2] Semaphore.acquire()  → Step Into → AQS 共享模式的 tryAcquireShared
 *   [3] CyclicBarrier.await() → Step Into → ReentrantLock + trip.await()
 *   [4] CompletableFuture.supplyAsync() → apply() → thenAccept() → 组合链执行
 */
public class SyncToolsDemo {

    // ────────────────────────────────────────────
    // ★ 一、CountDownLatch — 倒计数门闩（AQS 共享模式）
    //   【掌握要求】
    //   1. ★ 理解 CountDownLatch 基于 AQS 共享模式：
    //      state = 计数值，tryAcquireShared 在 state==0 时返回 1（通过），否则返回 -1（阻塞）
    //   2. 理解 countDown() → releaseShared(1) → 自旋 CAS 减 state → state==0 时 doReleaseShared 唤醒所有等待线程
    //   3. 经典用法：「发令枪」：count=1，所有线程 await()，主线程 countDown() 一次性放行
    //   4. ★ 注意：CountDownLatch 不可重用（一次性），state 到 0 后无法重置
    //   5. 理解 await(timeout) 的超时机制
    // ────────────────────────────────────────────

    static void demoCountDownLatch() throws Exception {
        int taskCount = 5;
        CountDownLatch startGate = new CountDownLatch(1);   // ★ 发令枪
        CountDownLatch doneGate = new CountDownLatch(taskCount); // ★ 等待所有完成

        for (int i = 0; i < taskCount; i++) {
            final int id = i;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        // ★★ Step Into await() → AQS.acquireSharedInterruptibly
                        //   CountDownLatch.Sync.tryAcquireShared 在 state==0 时返回 1（通过）
                        startGate.await();
                        System.out.println("    [T" + id + "] 起跑!");
                        TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(500));
                        doneGate.countDown(); // ★ state--，state==0 时唤醒所有等待线程
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }).start();
        }

        System.out.println("    准备... 发令!");
        startGate.countDown();  // ★ 发令枪响，所有线程同时起跑
        doneGate.await();       // ★ 等待所有线程完成
        System.out.println("    所有任务完成!");
    }

    // ────────────────────────────────────────────
    // ★ 二、Semaphore — 信号量（AQS 共享模式）
    //   【掌握要求】
    //   1. ★ 理解 Semaphore 就是 AQS 共享模式的直接应用：state = 许可数
    //   2. 理解 acquire() 的流程：state>0 → CAS 减 1 → 通过；state==0 → 入队阻塞
    //   3. 理解释放许可不需要持有许可的线程（release 可以增加 state 超过初始值）
    //   4. ★ 经典场景：限流（数据库连接池、API 限流）、停车场模型
    //   5. 理解公平 vs 非公平信号量：公平模式检查 FIFO 队列，非公平模式直接 CAS 抢夺
    //   6. 理解 availablePermits() 只是近似值（返回瞬间可能已被其他线程获取）
    // ────────────────────────────────────────────

    static void demoSemaphore() throws Exception {
        // ★ permits=2：最多 2 个线程同时访问临界区
        Semaphore semaphore = new Semaphore(2);
        int threads = 5;

        for (int i = 0; i < threads; i++) {
            final int id = i;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        // ★★ Step Into → AQS.acquireSharedInterruptibly
                        //   当 availablePermits > 0 → CAS 减 1 → 通过
                        //   当 availablePermits == 0 → 入队 park
                        semaphore.acquire();
                        System.out.println("    [T" + id + "] 获得许可, 剩余=" + semaphore.availablePermits());
                        TimeUnit.SECONDS.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        semaphore.release(); // ★ state++，唤醒下一个等待者
                        System.out.println("    [T" + id + "] 释放许可, 剩余=" + semaphore.availablePermits());
                    }
                }
            }).start();
            TimeUnit.MILLISECONDS.sleep(100);
        }
        TimeUnit.SECONDS.sleep(6);
    }

    // ────────────────────────────────────────────
    // ★ 三、CyclicBarrier — 可重用栅栏
    //   【掌握要求】
    //   1. ★ CyclicBarrier 底层是 ReentrantLock + Condition（不用 AQS！这与 CountDownLatch/Semaphore 不同）
    //   2. 理解屏障回调：最后一个线程到达屏障后执行 barrierAction（在所有线程被释放之前）
    //   3. 理解"代"（generation）概念：每轮等待是一个 generation，broken = true 表示屏障损坏
    //   4. ★ CyclicBarrier vs CountDownLatch：
    //      ① CyclicBarrier 可重用（自动重置计数），CountDownLatch 不可重用
    //      ② CountDownLatch 的 countDown() 和 await() 可以由不同线程调用，CyclicBarrier 各线程都调 await()
    //      ③ CyclicBarrier 有屏障回调，CountDownLatch 没有
    //   5. 理解 reset() 机制：打断当前所有等待线程，重新开始新的一代
    // ────────────────────────────────────────────

    static void demoCyclicBarrier() throws Exception {
        int parties = 3;
        // ★★ CyclicBarrier 底层是 ReentrantLock + Condition（不用 AQS）
        CyclicBarrier barrier = new CyclicBarrier(parties, new Runnable() {
            @Override
            public void run() {
                // ★ 所有线程到达屏障后，最后一个到达的线程执行此回调
                System.out.println("    ★★★ 屏障动作：所有线程到齐，一起通过! ★★★");
            }
        });

        for (int round = 1; round <= 2; round++) {
            System.out.println("  --- 第 " + round + " 轮 ---");
            CountDownLatch roundDone = new CountDownLatch(parties);

            for (int i = 0; i < parties; i++) {
                final int id = i;
                final int r = round;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            System.out.println("    [T" + id + "] 第" + r + "轮：执行任务");
                            TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(500));

                            // ★★ Step Into → dowait() →
                            //   1. lock.lock()
                            //   2. --count，若 count==0 执行 barrierAction + trip.signalAll()
                            //   3. 否则 trip.await() → ConditionObject.await()
                            barrier.await();
                            System.out.println("    [T" + id + "] 第" + r + "轮：通过屏障");
                            roundDone.countDown();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
            }
            roundDone.await();
            // ★ CyclicBarrier 可重用（自动重置），CountDownLatch 不可重用
        }
    }

    // ────────────────────────────────────────────
    // ★ 四、CompletableFuture — 组合式异步编程（JDK8 重点新特性）
    //   【掌握要求】
    //   1. 理解 CompletableFuture 同时实现了 Future + CompletionStage（既是结果容器，又是组合入口）
    //   2. 掌握 thenApply / thenCompose / thenAccept 的区别：
    //      thenApply：对结果做同步转换（map），入参 T 出参 U
    //      thenCompose：连接两个异步操作（flatMap），入参 T 出参 CompletionStage<U>
    //      thenAccept：纯消费结果（无返回值），入参 T
    //   3. 理解 Async 后缀的含义：thenApply 用执行任务的线程继续执行；thenApplyAsync 可能切换线程
    //   4. 理解默认线程池是 ForkJoinPool.commonPool()（CPU 核数-1个线程），不应提交阻塞任务
    //   5. 掌握 exceptionally / handle / whenComplete 的异常处理区别
    //   6. ★ 能写出 "查用户 → 查订单 → 查推荐 → 汇总" 的异步编排
    // ────────────────────────────────────────────

    static void demoCompletableFuture() throws Exception {
        // ★ supplyAsync：异步执行有返回值的任务
        CompletableFuture<String> future = CompletableFuture.supplyAsync(new Supplier<String>() {
            @Override
            public String get() {
                System.out.println("    [supplyAsync] 正在计算... (线程: " + Thread.currentThread().getName() + ")");
                sleep(500);
                return "Hello";
            }
        });

        // ★ thenApply：对结果进行同步转换（同一线程）
        // ★ thenApplyAsync：异步转换（可能切换线程）
        CompletableFuture<String> result = future
                .thenApply(new java.util.function.Function<String, String>() {
                    @Override
                    public String apply(String s) {
                        System.out.println("    [thenApply] 转换: " + s + " → " + s + " World");
                        return s + " World";
                    }
                })
                .thenCompose(new java.util.function.Function<String, CompletionStage<String>>() {
                    @Override
                    public CompletionStage<String> apply(String s) {
                        // ★ thenCompose：连接两个异步操作（flatMap）
                        return CompletableFuture.supplyAsync(new Supplier<String>() {
                            @Override
                            public String get() {
                                System.out.println("    [thenCompose] 再异步追加: " + s + " !");
                                return s + " !";
                            }
                        });
                    }
                });

        // ★ whenComplete：无论成功/失败都执行的副作用
        result.whenComplete(new java.util.function.BiConsumer<String, Throwable>() {
            @Override
            public void accept(String val, Throwable err) {
                if (err == null) {
                    System.out.println("    [whenComplete] 最终结果: " + val);
                } else {
                    System.out.println("    [whenComplete] 异常: " + err.getMessage());
                }
            }
        });

        // ★ join() 阻塞等待最终结果（类似 get() 但不抛受检异常）
        System.out.println("    主线程拿到: " + result.join());
    }

    // ────────────────────────────────────────────
    // ★ 五、CompletableFuture 聚合：allOf + anyOf
    //   【掌握要求】
    //   1. 理解 allOf：等待所有 CompletableFuture 都完成，返回 Void（无结果）
    //   2. 理解 anyOf：任意一个完成即返回，结果为 Object（需转型）
    //   3. 掌握 thenCombine(A, B, (a,b) -> merge(a,b))：两个 Future 结果合并
    //   4. 理解 allOf 返回值不能直接拿结果，需要用 f1.get() / f2.get() 单独获取
    //   5. 生产环境常见模式：allOf + thenApply(v -> 组合各结果)
    // ────────────────────────────────────────────

    static void demoCompletableFutureCombine() throws Exception {
        // 模拟三个异步服务调用
        CompletableFuture<String> f1 = CompletableFuture.supplyAsync(new Supplier<String>() {
            @Override
            public String get() {
                sleep(300);
                return "用户信息";
            }
        });

        CompletableFuture<String> f2 = CompletableFuture.supplyAsync(new Supplier<String>() {
            @Override
            public String get() {
                sleep(500);
                return "订单列表";
            }
        });

        CompletableFuture<String> f3 = CompletableFuture.supplyAsync(new Supplier<String>() {
            @Override
            public String get() {
                sleep(200);
                return "推荐商品";
            }
        });

        // ★ allOf：等待所有完成
        CompletableFuture<Void> all = CompletableFuture.allOf(f1, f2, f3);
        all.join();
        System.out.println("    ★ allOf 完成: " + f1.get() + " | " + f2.get() + " | " + f3.get());

        // ★ anyOf：任意一个完成即可
        // 重新创建演示 anyOf
        CompletableFuture<String> slow = CompletableFuture.supplyAsync(() -> {
            sleep(1000);
            return "慢";
        });
        CompletableFuture<String> fast = CompletableFuture.supplyAsync(() -> {
            sleep(100);
            return "快";
        });
        Object first = CompletableFuture.anyOf(slow, fast).get();
        System.out.println("    ★ anyOf 返回最快的: " + first);
    }

    // ────────────────────────────────────────────
    // ★ 六、Phaser — 动态多阶段协调器
    //   【掌握要求】
    //   1. 理解 Phaser = CountDownLatch 的计数 + CyclicBarrier 的可重用 + 动态增减参与方
    //   2. 理解 arriveAndAwaitAdvance()：到达并等待 → 等价于 CyclicBarrier.await()
    //   3. 理解 arriveAndDeregister()：到达并注销 → 动态减少参与方
    //   4. 理解 register()：动态增加参与方
    //   5. 理解 onAdvance(phase, registeredParties) 回调：每阶段结束时调用，return true 终止 phaser
    //   6. 理解 "phase"（阶段号）递增：每次所有参与方到达后 phase+1
    //   7. ★ 使用场景：分阶段并行计算（如遗传算法每代需等所有个体计算完）
    // ────────────────────────────────────────────

    static void demoPhaser() {
        // Phaser 可以动态增减参与方，比 CyclicBarrier 更灵活
        Phaser phaser = new Phaser(1) { // ★ 注册主线程为 1 方
            @Override
            protected boolean onAdvance(int phase, int registeredParties) {
                System.out.println("  ★★★ Phase " + phase + " 完成，剩余方数=" + registeredParties + " ★★★");
                return registeredParties == 0; // 返回 true 表示 phaser 终止
            }
        };

        for (int i = 0; i < 3; i++) {
            final int id = i;
            phaser.register(); // ★ 动态注册参与方
            new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int phase = 0; phase < 2; phase++) {
                        System.out.println("    [T" + id + "] Phase " + phase);
                        // ★ 到达并等待其他方
                        phaser.arriveAndAwaitAdvance();
                    }
                    phaser.arriveAndDeregister(); // ★ 注销自己
                }
            }).start();
        }

        phaser.arriveAndDeregister(); // 主线程也注销
    }

    private static void sleep(long ms) {
        try { TimeUnit.MILLISECONDS.sleep(ms); }
        catch (InterruptedException ignored) {}
    }

    public static void main(String[] args) throws Exception {
        System.out.println("========== 1. CountDownLatch (AQS 共享模式) ==========");
        demoCountDownLatch();

        System.out.println("\n========== 2. Semaphore (信号量) ==========");
        demoSemaphore();

        System.out.println("\n========== 3. CyclicBarrier (可重用栅栏) ==========");
        demoCyclicBarrier();

        System.out.println("\n========== 4. CompletableFuture 链式组合 ==========");
        demoCompletableFuture();

        System.out.println("\n========== 5. CompletableFuture allOf / anyOf ==========");
        demoCompletableFutureCombine();

        System.out.println("\n========== 6. Phaser (动态多阶段) ==========");
        demoPhaser();
    }
}
