<p align="center">
  <img src="https://img.shields.io/badge/JDK-8-blue?logo=openjdk" alt="JDK 8">
  <img src="https://img.shields.io/badge/source-OpenJDK%208u432-orange" alt="OpenJDK 8u432">
</p>

# JUC Source Learning Project

> 基于 OpenJDK 8u432 源码，6 阶段系统掌握 Java 并发编程 · Stop reading blogs, start reading source.

每个阶段配有可运行 Demo，支持打断点 **F11 Step Into** 进入 JDK 内部实现。

A 6-phase deep-dive into `java.util.concurrent` internals — with runnable demos, annotated source, and a full study guide.

---

## 📚 Table of Contents

- [Quick Start](#-quick-start)
- [Learning Roadmap](#-learning-roadmap)
- [File Structure](#-file-structure)
- [How to Use](#-how-to-use)
- [Annotation Legend](#-annotation-legend)
- [Full Study Guide](#-full-study-guide)
- [Companion Environment](#-companion-environment)

---

## ⚡ Quick Start

```bash
# 1. Switch to JDK 8 (required for Step Into)
export JAVA_HOME="$HOME/dev/jdk8"
export PATH="$JAVA_HOME/bin:$PATH"
java -version   # 1.8.x

# 2. Compile
cd src && javac -encoding UTF-8 *.java

# 3. Run any demo
java -cp . ThreadDemo
```

---

## 🗺️ Learning Roadmap

| Phase | Demo | Topics | Guide |
|:---:|:---|:---|:---:|
| **I** | [`ThreadDemo.java`](src/ThreadDemo.java) | 线程生命周期、`volatile` 可见性、`wait`/`notify` | [→](docs/JUC-study-guide.md#s11) |
| **II** | [`AtomicDemo.java`](src/AtomicDemo.java) | CAS 自旋、ABA 问题、`LongAdder` 分段累加 | [→](docs/JUC-study-guide.md#s21) |
| **III** | [`LockDemo.java`](src/LockDemo.java) | **AQS 框架**、`ReentrantLock`、`ReadWriteLock` | [→](docs/JUC-study-guide.md#s31) |
| **IV** | [`ThreadPoolDemo.java`](src/ThreadPoolDemo.java) | `ThreadPoolExecutor`、`FutureTask`、拒绝策略 | [→](docs/JUC-study-guide.md#s41) |
| **V** | [`ConcurrentCollectionDemo.java`](src/ConcurrentCollectionDemo.java) | `ConcurrentHashMap` (JDK8)、阻塞队列对比 | [→](docs/JUC-study-guide.md#s51) |
| **VI** | [`SyncToolsDemo.java`](src/SyncToolsDemo.java) | `CountDownLatch`、`Semaphore`、`CyclicBarrier`、`CompletableFuture` | [→](docs/JUC-study-guide.md#s61) |

---

## 📁 File Structure

```
jdk8u-demo/
├── src/
│   ├── ThreadDemo.java               # Phase I
│   ├── AtomicDemo.java               # Phase II
│   ├── LockDemo.java                 # Phase III ★ spend 40% time here
│   ├── ThreadPoolDemo.java           # Phase IV
│   ├── ConcurrentCollectionDemo.java # Phase V
│   ├── SyncToolsDemo.java            # Phase VI
│   └── HashMapProbe.java             # Step Into verification demo
├── docs/
│   └── JUC-study-guide.md            # Full tutorial with self-check lists
└── README.md
```

---

## 🧭 How to Use

Each demo is self-contained and can be run independently.

| Step | Action |
|:---:|:---|
| 1 | **Run** the demo to observe the output |
| 2 | **Set a breakpoint** on lines marked with `★★` |
| 3 | **F11 Step Into** the JDK source code |
| 4 | **Read** the `【掌握要求】` block above each method to know what to master |

---

## 🏷️ Annotation Legend

| Marker | Meaning |
|:---|:---|
| `★` | Key concept — understand this |
| `★★` | **Breakpoint + Step Into** — the most important call points |
| `【掌握要求】` | Mastery checklist for this topic |
| `→` | Method call chain for source tracing |

---

## 📖 Full Study Guide

See **[docs/JUC-study-guide.md](docs/JUC-study-guide.md)** for:

- Complete walkthrough with **anchor-links** navigate between chapters
- **Cheat sheets** (state machines, comparison tables, lock call chains)
- **Self-check lists**（自检清单）for every topic
- **Interview comparison questions**（对比类面试题）

---

## 🔗 Companion Environment

| Path | Description |
|:---|:---|
| `../jdk8u/` | OpenJDK 8u432 full source |
| `../env-jdk8.sh` | Environment setup script |

---

<p align="center">
  <sub>Demo code under MIT License. JDK source under OpenJDK GPLv2+CE.</sub>
</p>
