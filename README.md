# JUC 源码学习项目 · JUC Source Learning Project

基于 OpenJDK 8u432 源码，6 阶段系统掌握 Java 并发编程。每个阶段配有可运行 Demo，支持打断点 **Step Into** 进入 JDK 内部实现。

---

A 6-phase systematic deep-dive into Java concurrent programming, built on OpenJDK 8u432 sources. Each phase has runnable demos — set breakpoints and **Step Into** the JDK internals.

---

## 快速开始 · Quick Start

```bash
# 1. 用 JDK 8 · Use JDK 8
export JAVA_HOME="$HOME/dev/jdk8"
export PATH="$JAVA_HOME/bin:$PATH"
java -version   # should show 1.8.x

# 2. 编译所有 Demo · Compile all demos
cd src
javac -encoding UTF-8 *.java

# 3. 运行任意 Demo · Run any demo
java -cp . ThreadDemo
```

---

## 学习路线 · Learning Roadmap

| 阶段 · Phase | Demo 文件 · File | 掌握内容 · What You'll Learn |
|---|---|---|
| [一 · I](docs/JUC-study-guide.md#s11) | [ThreadDemo.java](src/ThreadDemo.java) | 线程生命周期、volatile可见性、wait/notify |
| [二 · II](docs/JUC-study-guide.md#s21) | [AtomicDemo.java](src/AtomicDemo.java) | CAS原子操作、ABA问题、LongAdder分段累加 |
| [三 · III](docs/JUC-study-guide.md#s31) | [LockDemo.java](src/LockDemo.java) | AQS框架、ReentrantLock、ReadWriteLock、Condition |
| [四 · IV](docs/JUC-study-guide.md#s41) | [ThreadPoolDemo.java](src/ThreadPoolDemo.java) | ThreadPoolExecutor、FutureTask、拒绝策略 |
| [五 · V](docs/JUC-study-guide.md#s51) | [ConcurrentCollectionDemo.java](src/ConcurrentCollectionDemo.java) | ConcurrentHashMap、CopyOnWriteList、阻塞队列对比 |
| [六 · VI](docs/JUC-study-guide.md#s61) | [SyncToolsDemo.java](src/SyncToolsDemo.java) | CountDownLatch、Semaphore、CyclicBarrier、CompletableFuture |

> 📖 **完整教程 · Full Guide**：[docs/JUC-study-guide.md](docs/JUC-study-guide.md) — 含跳转、要点速查表、自检清单 · with anchor links, cheat sheets, and self-check lists

---

## 文件结构 · File Structure

```
jdk8u-demo/
├── src/
│   ├── ThreadDemo.java               # 阶段一 · Phase I
│   ├── AtomicDemo.java               # 阶段二 · Phase II
│   ├── LockDemo.java                 # 阶段三 · Phase III
│   ├── ThreadPoolDemo.java           # 阶段四 · Phase IV
│   ├── ConcurrentCollectionDemo.java # 阶段五 · Phase V
│   ├── SyncToolsDemo.java            # 阶段六 · Phase VI
│   └── HashMapProbe.java             # HashMap Step Into 验证
├── docs/
│   └── JUC-study-guide.md            # 完整教程 · Full Guide
└── README.md                         # 本文件 · This file
```

---

## 使用方式 · How to Use

### 三步走 · Three Steps

1. **先运行看效果** · Run the demo to see the output
2. **在 ★★ 标注处打断点** · Set breakpoints on lines marked with ★★
3. **F11 Step Into JDK 源码** · Step Into the JDK source and trace the internals

### 示例 · Example

```java
// ThreadPoolDemo.java
executor.execute(task); // ★★ Step Into → ThreadPoolExecutor.execute()
                        //   Observe ctl bit operations and the 3-step strategy
```

### 注释标记说明 · Annotation Legend

| 标记 · Marker | 含义 · Meaning |
|---|---|
| `★` | 重要知识点 · Key concept |
| `★★` | 打断点并 Step Into · Set breakpoint here |
| `【掌握要求】` | 该知识点的掌握清单 · Mastery checklist |
| `→` | 方法调用链 · Method call chain |

---

## 配套环境 · Companion Environment

本项目依赖同级目录下的 JDK 8 源码和运行时：

| 路径 · Path | 说明 · Description |
|---|---|
| `../jdk8u/` | OpenJDK 8u432 完整源码 |
| `../env-jdk8.sh` | 环境变量配置脚本 |

详见父目录 [README.md](../README.md) · See parent directory [README](../README.md)

---

## 许可证 · License

本项目 Demo 代码遵循 MIT License · Demo code under MIT License.

源码位于 `../jdk8u/`，遵循 OpenJDK GPLv2+CE · Source code under OpenJDK GPLv2+CE.
