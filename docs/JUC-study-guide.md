# JUC（java.util.concurrent）源码学习教程

基于 OpenJDK 8u432 源码，6 个阶段系统掌握 Java 并发编程。

---
## 目录

- [阶段一：线程基础](#ch1)
- [阶段二：原子类与 CAS](#ch2)
- [阶段三：AQS 与锁框架](#ch3)
- [阶段四：线程池](#ch4)
- [阶段五：并发集合](#ch5)
- [阶段六：同步工具与 CompletableFuture](#ch6)
- [全局自检清单](#checklist)

---
<a id="ch1"></a>
## 阶段一：线程基础

> **Demo 文件**：[ThreadDemo.java](../src/ThreadDemo.java)  
> **源码位置**：`java/lang/Thread.java`、`java/lang/Object.java`

<a id="s11"></a>
### 1.1 线程生命周期

| 状态 | 触发条件 | 退出条件 |
|------|---------|---------|
| NEW | `new Thread()` | `start()` |
| RUNNABLE | `start()` 或获得CPU时间片 | 失去CPU / 等待锁 / 主动休眠 |
| BLOCKED | 等待 `synchronized` 锁 | 获得锁 |
| WAITING | `wait()` / `park()` / `join()` | `notify()` / `unpark()` / 目标线程终止 |
| TIMED_WAITING | `sleep()` / `wait(timeout)` / `parkNanos()` | 超时或被唤醒 |
| TERMINATED | `run()` 结束或异常退出 | —— |

**掌握自检**

- [ ] 能画出 6 种状态的完整转换图
- [ ] 能说出 BLOCKED 和 WAITING 的本质区别（是否等待锁）
- [ ] 理解 `start()` vs `run()`：start() 创建新线程，run() 只是普通方法调用
- [ ] 理解 `join()` 语义：当前线程等待目标线程终止
- [ ] Step Into `Thread.start()` → `native start0()`，看过 OS 线程创建过程

<a id="s12"></a>
### 1.2 volatile 可见性

**两大语义：**

1. **保证可见性**：写 volatile 变量后立即刷新到主内存，读 volatile 变量前强制从主内存读取
2. **禁止指令重排**：通过内存屏障（Memory Barrier）阻止 JIT 和 CPU 的乱序优化

**知识边界：**

| volatile 能做到 | volatile 做不到 |
|----------------|----------------|
| 保证单次读/写的可见性 | 保证 `i++` 的原子性 |
| 禁止指令重排（happens-before） | 替代 `synchronized` 的互斥 |
| 状态标志在线程间安全传递 | 保证多个变量的操作原子性 |

**掌握自检**

- [ ] 理解 JMM 主内存/工作内存模型
- [ ] 能解释 volatile 的两大语义及底层（MESI 协议 + 内存屏障）
- [ ] 能说出 volatile 不保证原子性的反例（`i++` 三步操作）
- [ ] 能写出 DCL（双重检查锁定）单例的正确写法
- [ ] 理解 happens-before：volatile 写 happens-before 后续 volatile 读
- [ ] 【动手】去掉 volatile，用 `-server` 模式跑多遍，观察 reader 不退出

<a id="s13"></a>
### 1.3 wait / notify 线程协作

**三个关键行为：**

1. 调用 `wait()` → **释放锁** → 线程进入 WAITING
2. 被 `notify()` → 线程从 WAITING 转为 BLOCKED（等待重新获取锁）
3. 重新获得锁 → 从 `wait()` 返回，继续执行

**核心规则：**

- ✦ 必须在 `synchronized` 块内调用（需要持有对象监视器）
- ✦ **用 `while(!condition)` 而非 `if`** — 防止虚假唤醒（spurious wakeup）
- ✦ `wait()` 释放锁，`sleep()` 不释放锁
- ✦ `notify()` 随机唤醒一个等待线程，`notifyAll()` 唤醒所有

**掌握自检**

- [ ] 理解 wait() 的三个关键行为：释放锁 → 等待 → 重新竞争锁
- [ ] 理解为什么 wait() 必须在 synchronized 块内调用
- [ ] **能解释为什么用 while 而不是 if 检查条件**（虚假唤醒）
- [ ] 能说出 wait() vs sleep() 的三个区别
- [ ] 能说出 notify() vs notifyAll() 的区别及适用场景
- [ ] 能手写一个 wait/notify 生产者-消费者模型

---
<a id="ch2"></a>
## 阶段二：原子类与 CAS

> **Demo 文件**：[AtomicDemo.java](../src/AtomicDemo.java)  
> **源码位置**：`java/util/concurrent/atomic/`、`sun/misc/Unsafe.java`

<a id="s21"></a>
### 2.1 CAS（Compare-And-Swap）

**三要素**：内存地址 V / 期望值 A / 新值 B

```
CAS(V, A, B) → 如果 V == A，则 V = B 并返回 true；否则返回 false
```

**核心思想**：乐观锁 — 先操作，失败再重试（区别于 synchronized 的悲观锁）

**CAS 的三大问题：**

| 问题 | 描述 | 解决方案 |
|------|------|---------|
| ABA 问题 | 值 A→B→A，CAS 无法检测到变化 | AtomicStampedReference 加版本号 |
| 循环开销 | 高竞争下 CAS 频繁失败，CPU 空转 | 低竞争场景使用，高竞争用锁 |
| 单一变量限制 | 只能保证一个共享变量的原子性 | AtomicReference 包装多个变量 |

**掌握自检**

- [ ] 能用一句话描述 CAS 的操作语义
- [ ] **理解 CAS 的"乐观锁" vs synchronized 的"悲观锁"思想**
- [ ] 理解自旋（spin）：CAS 失败 → 循环重试 → 直到成功
- [ ] 能举出 CAS 的三大问题及解决方案
- [ ] Step Into `Unsafe.compareAndSwapInt()` 见过 native 声明
- [ ] 理解 AtomicInteger 的 `value` 为什么用 volatile 修饰（保证 CAS 间的可见性）
- [ ] 能解释 CAS 为什么比 synchronized 快（无上下文切换），以及何时反而更差（高竞争）

<a id="s22"></a>
### 2.2 i++ 的并发问题（对比实验）

**`i++` 不是原子操作**：读内存 → 寄存器加 1 → 写回内存（三步可被打断）

| 方案 | 线程安全 | 性能 | 原理 |
|------|---------|------|------|
| `int i++` | 否 | 最快 | 无同步开销但数据丢失 |
| `synchronized` | 是 | 较慢 | 悲观锁，有上下文切换 |
| `AtomicInteger` | 是 | 中等 | CAS 自旋，无上下文切换 |
| `LongAdder` | 是 | 高并发最优 | 分段累加，分散竞争 |

<a id="s23"></a>
### 2.3 AtomicReference — 对象级 CAS

- [ ] 理解 `compareAndSet` 用 `==` 比较，不是 `equals`
- [ ] 掌握 CAS 经典编程范式：`do { old=get(); newVal=compute(old); } while (!cas(old, newVal))`
- [ ] 能对比 AtomicReference 和 AtomicStampedReference 的 API 差异

<a id="s24"></a>
### 2.4 ABA 问题与 AtomicStampedReference

- [ ] **能用一句话描述 ABA**：线程1以为值没变(还是A)，但实际上 A→B→A 已经被其他线程改过
- [ ] 知道 ABA 何时有害：链表节点删除重建（无锁栈/队列），计数器无害
- [ ] 掌握 `AtomicStampedReference` 的版本号解决方案：值 + stamp 双重比对
- [ ] 了解 `AtomicMarkableReference` 作为简化版（布尔标记）
- [ ] 知道 JDK 中哪些地方会遭遇 ABA（AQS 的无锁队列、ConcurrentLinkedQueue）
- [ ] 能举出至少一个 ABA 导致 bug 的实际场景

<a id="s25"></a>
### 2.5 LongAdder — 分段累加器

- [ ] **理解核心思想**：空间换时间 → Cell[] 数组分散热点
- [ ] 理解 Striped64 架构：每个 Cell = `@Contended` 的 volatile long（防伪共享）
- [ ] **LongAdder vs AtomicLong 选型**：高并发写用 LongAdder，需精确瞬时值用 AtomicLong
- [ ] Step Into `Striped64.longAccumulate()` → 看 Cell 初始化、扩容(×2)、CAS 切换 Cell
- [ ] 理解 `sum()` 是弱一致性（近似值）的原因：遍历过程中可能有新写入
- [ ] 了解变体：`LongAccumulator` / `DoubleAdder` / `DoubleAccumulator`

---
<a id="ch3"></a>
## 阶段三：AQS 与锁框架

> **Demo 文件**：[LockDemo.java](../src/LockDemo.java)  
> **源码位置**：`java/util/concurrent/locks/AbstractQueuedSynchronizer.java`

<a id="s31"></a>
### 3.1 AQS（AbstractQueuedSynchronizer）概述

> ✦ **AQS 是整个 JUC 的"引擎"**，CountDownLatch、Semaphore、ReentrantLock、ReentrantReadWriteLock、ThreadPoolExecutor.Worker 全部基于它

**核心字段：**

| 字段 | 含义 |
|------|------|
| `state` (volatile int) | 同步状态（锁次数/许可数/倒计数） |
| `head` / `tail` | CLH 变种双端队列的头尾指针 |
| `Node.waitStatus` | SIGNAL(-1) / CANCELLED(1) / CONDITION(-2) / PROPAGATE(-3) |

**两套 API 模板：**

| 模式 | 代表类 | tryAcquire 返回值含义 |
|------|-------|---------------------|
| 排他模式 | ReentrantLock | true=获得锁 |
| 共享模式 | CountDownLatch, Semaphore | ≥0=通过, <0=阻塞 |

<a id="s32"></a>
### 3.2 ReentrantLock + Condition

**lock() 全链路：**
```
lock() → acquire(1) → tryAcquire(1) → [失败] addWaiter(Node.EXCLUSIVE) → acquireQueued()
  入队：CAS 设置 tail → 找到前驱 → 前驱为 head 则再 tryAcquire → 否则 park
  唤醒：unlock() → release(1) → tryRelease → unparkSuccessor(head.next)
```

**ConditionObject.await() 全链路：**
```
await() → addConditionWaiter() → fullyRelease(state) → park()
signal() → doSignal() → transferForSignal() → enq() → 设置 SIGNAL
```

**ReentrantLock vs synchronized：**

| 特性 | ReentrantLock | synchronized |
|------|-------------|-------------|
| 公平性 | 可选公平/非公平 | 非公平 |
| 条件队列 | 多个 Condition | 一个 wait set |
| 中断响应 | lockInterruptibly() | wait() 可响应 |
| 超时获取 | tryLock(timeout) | 不支持 |
| 语法 | 需 try-finally | 关键字自动释放 |

**掌握自检**

- [ ] **理解 lock() → acquire() → addWaiter() → acquireQueued() → park() 的完整链路**
- [ ] **理解 await() → addConditionWaiter() → fullyRelease() → park() 的完整链路**
- [ ] **理解 signal() → doSignal() → transferForSignal() → enq() 的转移过程**
- [ ] 能画图解释同步队列（CLH 变种）和条件队列的关系
- [ ] 掌握 try-finally 中 unlock() 的规范写法
- [ ] 理解 waitStatus 的几种值：SIGNAL / CANCELLED / CONDITION / PROPAGATE

<a id="s33"></a>
### 3.3 可重入性

- [ ] **理解可重入**：同一线程可多次获取同一把锁 → state 递增，unlock 时递减
- [ ] 理解 `getHoldCount()` 的含义
- [ ] 知道 synchronized 的 native 可重入实现（对象头 monitor 记录）
- [ ] 知道不可重入锁的应用场景（如 Worker 的锁防止重复执行）

<a id="s34"></a>
### 3.4 公平锁 vs 非公平锁

- [ ] 理解实现差异：公平锁先检查 `hasQueuedPredecessors()`，非公平锁直接 CAS 抢
- [ ] 为啥非公平锁吞吐量高：释放锁的线程可能还在 CPU 缓存中，减少线程切换
- [ ] **生产环境选型**：默认非公平，需要严格 FIFO 才用公平锁
- [ ] 理解"饥饿"问题：非公平锁下可能某些线程长期获取不到锁

<a id="s35"></a>
### 3.5 ReadWriteLock — 读写锁

- [ ] **理解核心思想**：读共享、写互斥，读多写少的场景
- [ ] 理解 state 的高低位拆分：高 16 位=读锁计数，低 16 位=写锁计数
- [ ] 理解**锁降级**（写锁→读锁）的流程，以及为什么不能锁升级（会死锁）
- [ ] 了解 StampedLock（JDK8）的乐观读模式：不加锁读 → 校验 stamp → 不一致则悲观读
- [ ] 面试常考：读写锁 vs synchronized 的性能差异及选型

<a id="s36"></a>
### 3.6 tryLock 超时与死锁

- [ ] 理解 `tryLock()` vs `lock()`：非阻塞 vs 阻塞
- [ ] 理解 `tryLock(timeout)` → `AQS.doAcquireNanos()` → `parkNanos()` 内部机制
- [ ] **掌握 tryLock 打破死锁的经典场景**
- [ ] 理解 `parkNanos()` 和 `sleep()` 的区别（parkNanos 可被 unpark 提前唤醒）
- [ ] 能说出 `lockInterruptibly()` 的语义

---
<a id="ch4"></a>
## 阶段四：线程池

> **Demo 文件**：[ThreadPoolDemo.java](../src/ThreadPoolDemo.java)  
> **源码位置**：`java/util/concurrent/ThreadPoolExecutor.java`

<a id="s41"></a>
### 4.1 execute() 三步策略

```
workerCount < corePoolSize  →  新建核心线程
    ↓ (否)
队列未满                    →  任务入队
    ↓ (否)
workerCount < maxPoolSize   →  新建临时线程
    ↓ (否)
                             →  执行拒绝策略
```

<a id="s42"></a>
### 4.2 7 个核心参数

| 参数 | 含义 | 建议 |
|------|------|------|
| corePoolSize | 核心线程数 | CPU 密集型 = N+1；IO 密集型 = 2N+1 |
| maxPoolSize | 最大线程数 | 有界，防 OOM |
| keepAliveTime | 空闲线程存活时间 | 仅对超出 core 的线程生效 |
| TimeUnit | 时间单位 | — |
| workQueue | 任务队列 | **必须用有界队列**（防 OOM） |
| threadFactory | 线程工厂 | 自定义线程名，方便排查 |
| handler | 拒绝策略 | 见 4.4 |

<a id="s43"></a>
### 4.3 ctl 位运算

```
ctl (AtomicInteger): 高 3 位 = 线程池状态, 低 29 位 = workerCount

RUNNING(-1) → SHUTDOWN(0) → STOP(1) → TIDYING(2) → TERMINATED(3)
```

- `RUNNING`: 接受新任务，处理队列任务
- `SHUTDOWN`: 不接受新任务，处理队列任务（`shutdown()`）
- `STOP`: 不接受新任务，不处理队列任务，中断进行中任务（`shutdownNow()`）
- `TIDYING`: 任务全部终止，workerCount=0
- `TERMINATED`: `terminated()` 已执行

**掌握自检**（覆盖 4.1-4.3）

- [ ] **能画出 execute() 的三步策略流程图**
- [ ] 理解 ctl 的位运算设计：一个 AtomicInteger 打包两个信息
- [ ] 理解 Worker 内部类：AQS 子类（不可重入锁）+ 包装 Thread + 任务
- [ ] 理解 keepAliveTime + 空闲线程回收机制
- [ ] **生产环境为什么必须用有界队列**（防止内存溢出）
- [ ] 理解 `shutdown()` vs `shutdownNow()` 的行为差异
- [ ] 知道 ThreadFactory 的作用：线程名、守护线程、优先级

<a id="s44"></a>
### 4.4 四种拒绝策略

| 策略 | 行为 | 适用场景 |
|------|------|---------|
| AbortPolicy（默认） | 抛 RejectedExecutionException | 必须感知拒绝 |
| CallerRunsPolicy | 提交任务的线程自己执行 | **推荐**：自带反压效果 |
| DiscardPolicy | 静默丢弃 | 不重要的任务 |
| DiscardOldestPolicy | 丢队头最老任务，再尝试提交 | 优先新数据 |

- [ ] 熟记 4 种拒绝策略的行为差异
- [ ] 理解 CallerRunsPolicy 的双面性：减缓提交（反压）vs 可能阻塞主线程
- [ ] 知道可以自定义 RejectedExecutionHandler

<a id="s45"></a>
### 4.5 Future / FutureTask

**FutureTask 状态机**：
```
NEW → COMPLETING → NORMAL / EXCEPTIONAL / CANCELLED / INTERRUPTING / INTERRUPTED
```

- [ ] **理解 Future 的设计模式**：提交不阻塞 → 返回凭证 → 需要时 get()
- [ ] 理解 FutureTask 7 种状态转换
- [ ] 理解 `awaitDone()` 的自旋 + park 策略：先自旋避免上下文切换，再 park
- [ ] 理解 `finishCompletion()`：遍历 Treiber stack（waiters 链表），逐个 unpark
- [ ] 理解 Why FutureTask 可直接给 Thread（实现 RunnableFuture 接口）
- [ ] 知道 `submit(Callable)` 内部实际包装成 FutureTask
- [ ] 理解 `cancel(mayInterruptIfRunning)` 的行为差异

<a id="s46"></a>
### 4.6 定时调度

- [ ] 理解 `scheduleAtFixedRate` vs `scheduleWithFixedDelay`：
  - FixedRate：以上次任务**开始**时间为基准
  - FixedDelay：以上次任务**结束**时间为基准
- [ ] 知道 Timer 的缺陷（单线程、异常终止）→ 为什么用 ScheduledThreadPoolExecutor 替代
- [ ] 了解 DelayedWorkQueue 基于堆的延迟队列实现

---
<a id="ch5"></a>
## 阶段五：并发集合

> **Demo 文件**：[ConcurrentCollectionDemo.java](../src/ConcurrentCollectionDemo.java)  
> **源码位置**：`java/util/concurrent/ConcurrentHashMap.java`

<a id="s51"></a>
### 5.1 ConcurrentHashMap（JDK 8）

**JDK7 → JDK8 最大变化**：抛弃 Segment 分段锁（默认 16 段）→ CAS + synchronized 锁单 bin 头节点

**putVal() 两条路径：**

| 路径 | 条件 | 机制 |
|------|------|------|
| 无锁插入 | bin 为空 | CAS `tabAt(i)` 直接设置 |
| 有锁插入 | bin 非空 | `synchronized(f)` 锁头节点 → 遍历链表/红黑树 |

**树化逻辑：**
- 链表长度 ≥ 8 (`TREEIFY_THRESHOLD`) 且数组长度 ≥ 64 (`MIN_TREEIFY_CAPACITY`)
- 退树化：红黑树节点 ≤ 6 (`UNTREEIFY_THRESHOLD`)

**多线程扩容（helpTransfer）**：发现正在扩容时，其他线程帮忙复制 bin 数据

**为什么 capacity 是 2 的幂**：`hash & (n-1)` 替代 `hash % n`，位运算更快

**sizeCtl 的三重语义：**

| 值 | 含义 |
|----|------|
| -1 | 正在初始化 |
| -(1 + n) | n 个线程在协助扩容 |
| >0 | 扩容阈值（= 0.75 × 当前容量） |

| 方案演进 | 锁粒度 |
|---------|-------|
| Hashtable | 全表锁 |
| Collections.synchronizedMap | 全表锁 |
| JDK7 CHM | Segment 锁（16 段） |
| JDK8 CHM | 单个 bin 头节点锁 |

**掌握自检**

- [ ] **能画出 ConcurrentHashMap 从 JDK7 到 JDK8 的演进路线**
- [ ] 理解 putVal() 的无锁路径和有锁路径
- [ ] **理解树化条件**：链表长度 ≥ 8 且数组长度 ≥ 64
- [ ] 理解为什么 capacity 是 2 的幂
- [ ] 理解 sizeCtl 的三重语义
- [ ] 理解多线程协助扩容（helpTransfer）
- [ ] 理解 CHM 为什么是弱一致性：size()/isEmpty() 是近似值
- [ ] 理解 HashMap 并发下三大问题：数据丢失 / JDK7 环形链表 / size 不准

<a id="s52"></a>
### 5.2 CopyOnWriteArrayList

- [ ] **理解写时复制**：写操作 ReentrantLock + `Arrays.copyOf()`，读写不互斥
- [ ] 理解 COWIterator 快照：遍历期间修改不可见，不抛 ConcurrentModificationException
- [ ] **适用场景**：读远多于写（配置信息、监听器列表），写多会 OOM

<a id="s53"></a>
### 5.3 阻塞队列对比

| 队列 | 锁设计 | 数据结构 | 适用场景 |
|------|--------|---------|---------|
| ArrayBlockingQueue | 一把 ReentrantLock + 2 Condition | 环形数组 | 固定容量，内存连续 |
| LinkedBlockingQueue | `putLock` + `takeLock` 分离 | 链表 | 高并发生产消费 |
| ConcurrentLinkedQueue | CAS 无锁 | 链表 | 无需阻塞的场景 |

**掌握自检**

- [ ] 理解 ArrayBlockingQueue 的一把锁 + 两个 Condition 设计
- [ ] **理解 LinkedBlockingQueue 双锁分离**：putLock/takeLock 让生产消费并行
- [ ] 理解级联通知（cascade notify）：count 从 0→1 或 capacity→capacity-1 时互相通知
- [ ] 理解 ConcurrentLinkedQueue 的 Michael-Scott 无锁算法
- [ ] 理解松弛设计：tail 允许滞后 1-2 节点，减少 CAS 竞争
- [ ] 理解 offer()/poll() vs put()/take() 的区别：非阻塞/超时 vs 阻塞

---
<a id="ch6"></a>
## 阶段六：同步工具与 CompletableFuture

> **Demo 文件**：[SyncToolsDemo.java](../src/SyncToolsDemo.java)

<a id="s61"></a>
### 6.1 CountDownLatch

- [ ] **理解底层**：基于 AQS 共享模式，state = 计数值
- [ ] `tryAcquireShared`：state==0 返回 1（通过），否则返回 -1（阻塞）
- [ ] `countDown()` → `releaseShared(1)` → CAS 减 state → state==0 时 `doReleaseShared` 唤醒所有
- [ ] 经典"发令枪"用法：count=1，所有线程 await()，主线程 countDown() 一把放行
- [ ] **不可重用**：state 到 0 后无法重置
- [ ] 理解 `await(timeout)` 超时机制

<a id="s62"></a>
### 6.2 Semaphore

- [ ] **理解底层**：AQS 共享模式，state = 许可数
- [ ] acquire：state>0 → CAS 减 1 → 通过；state==0 → 入队 park
- [ ] release 可超过初始值（不需要"持有许可"才能释放）
- [ ] **经典场景**：限流（连接池、API 限流）、停车场模型
- [ ] 理解 availablePermits() 是近似值
- [ ] 公平 vs 非公平信号量的实现差异

<a id="s63"></a>
### 6.3 CyclicBarrier

- [ ] **底层是 ReentrantLock + Condition**（不用 AQS！与 CountDownLatch/Semaphore 不同）
- [ ] 屏障回调：最后一个线程到达后执行 barrierAction
- [ ] 理解"代"（generation）：每轮是一个 generation，broken=true 表示屏障损坏

**CyclicBarrier vs CountDownLatch：**

| 特性 | CyclicBarrier | CountDownLatch |
|------|-------------|----------------|
| 可重用性 | ✅ 自动重置 | ❌ 一次性 |
| 计数方式 | 各线程都调 await() | countDown() 和 await() 可分开调 |
| 回调能力 | 有 barrierAction | 无 |
| 底层实现 | ReentrantLock + Condition | AQS 共享模式 |

<a id="s64"></a>
### 6.4 CompletableFuture

**核心方法速查：**

| 方法 | 类比 | 说明 |
|------|------|------|
| `thenApply(Function)` | map | 同步转换结果 T→U |
| `thenCompose(Function)` | flatMap | 连接两个异步操作 T→Future\<U\> |
| `thenAccept(Consumer)` | forEach | 消费结果，无返回值 |
| `thenCombine(other, BiFunction)` | zip | 合并两个 Future 结果 |
| `exceptionally(Function)` | recover | 异常恢复 |
| `whenComplete(BiConsumer)` | finally | 无论成败都执行 |
| `allOf(...)` | Promise.all | 等待全部完成 |
| `anyOf(...)` | Promise.race | 任一完成即返回 |

**线程池规则：**
- 默认线程池：`ForkJoinPool.commonPool()`（CPU 核数 - 1 个线程）
- **不要在其中提交阻塞任务**（会耗尽线程）

**掌握自检**

- [ ] **理解 CompletableFuture = Future + CompletionStage（结果容器 + 组合入口）**
- [ ] 能区分 thenApply / thenCompose / thenAccept 的入参出参差异
- [ ] 理解 Async 后缀：thenApply 用同一线程继续，thenApplyAsync 可能切换线程
- [ ] 理解默认线程池的局限：不应提交阻塞任务
- [ ] 掌握 exceptionally / handle / whenComplete 的差异
- [ ] 能写出 "查用户 → 查订单 → 查推荐 → 汇总" 的异步编排
- [ ] 知道 allOf 不直接返回结果，需单独 f1.get() / f2.get()
- [ ] 掌握 thenCombine(A, B, (a,b) -> merge(a,b)) 合并两个 Future

<a id="s65"></a>
### 6.5 Phaser

- [ ] 理解 Phaser = CountDownLatch 的计数 + CyclicBarrier 的可重用 + 动态增减参与方
- [ ] `arriveAndAwaitAdvance()`：到达并等待（等同 CyclicBarrier.await()）
- [ ] `register()` / `arriveAndDeregister()`：动态增减参与方
- [ ] 理解 onAdvance(phase, registeredParties) → return true 终止 phaser
- [ ] 使用场景：分阶段并行计算

---
<a id="checklist"></a>
## 全局自检清单

<a id="cl-core"></a>
### 核心概念

- [ ] 能画出 Java 线程 6 种状态转换图
- [ ] 能解释 volatile 的两大语义及其硬件基础
- [ ] **能说清楚 CAS 的原理、优势和三大问题**
- [ ] 能从头到尾画出 AQS 排他模式 acquire → park → release → unpark 的流程
- [ ] **能说清楚 AQS 独占模式和共享模式的 tryAcquire 差异**
- [ ] 能画出 ThreadPoolExecutor.execute() 的完整决策树
- [ ] 能列出 ConcurrentHashMap 从 JDK7 到 JDK8 的演进及每代的锁粒度
- [ ] 能区分数种阻塞队列的锁设计和适用场景

<a id="cl-source"></a>
### 源码阅读（Step Into 打卡）

- [ ] 至少一次 Step Into `Unsafe.compareAndSwapInt()` 见到 native
- [ ] 至少一次 Step Into `AQS.acquireQueued()` 跟踪入队和 park
- [ ] 至少一次 Step Into `ConditionObject.await()` 跟踪条件队列
- [ ] 至少一次 Step Into `ThreadPoolExecutor.Worker.run()` 跟踪 runWorker + getTask
- [ ] 至少一次 Step Into `FutureTask.get()` 跟踪 awaitDone 自旋 + park
- [ ] 至少一次 Step Into `ConcurrentHashMap.putVal()` 跟踪 CAS + synchronized 分支

<a id="cl-compare"></a>
### 对比类面试题

- [ ] `synchronized` vs `ReentrantLock`
- [ ] `wait/notify` vs `Condition.await/signal`
- [ ] `AtomicInteger` vs `LongAdder`
- [ ] `CountDownLatch` vs `CyclicBarrier`
- [ ] `ArrayBlockingQueue` vs `LinkedBlockingQueue` vs `ConcurrentLinkedQueue`
- [ ] `HashMap` vs `Hashtable` vs `Collections.synchronizedMap` vs JDK7 CHM vs JDK8 CHM
- [ ] `Timer` vs `ScheduledThreadPoolExecutor`
- [ ] `Runnable` vs `Callable` / `Future` vs `FutureTask`
- [ ] `thenApply` vs `thenCompose` vs `thenAccept`

---
## 学习建议

1. **按阶段顺序来**，不要跳阶段（AQS 是理解 CountDownLatch/Semaphore/ThreadPoolExecutor 的前提）
2. **每个 Demo 先跑一遍看输出**，再打断点 Step Into 追踪
3. **对照自检清单**，能打勾才算真正掌握
4. **阶段三 AQS 建议花 40% 以上时间深挖**，是整个 JUC 的灵魂
5. 遇到看不懂的源码，**回到 Demo 里的 Step Into 路径注释**，按指定方法步步深入
