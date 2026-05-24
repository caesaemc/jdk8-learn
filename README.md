[English](#english) · [中文](#中文)

---

<p align="center">
  <img src="https://img.shields.io/badge/JDK-8-blue?logo=openjdk" alt="JDK 8">
  <img src="https://img.shields.io/badge/source-OpenJDK%208u432-orange" alt="OpenJDK 8u432">
</p>

<a id="中文"></a>
# JUC 源码学习项目

> 少看博客，多看源码。

基于 OpenJDK 8u432 源码，6 阶段系统掌握 `java.util.concurrent` 内部实现。每个阶段配有可运行 Demo，打断点 **F11 Step Into** 即可跟踪 JDK 内部调用链。

---

## 📚 目录

- [快速开始](#快速开始)
- [学习路线](#学习路线)
- [文件结构](#文件结构)
- [使用方式](#使用方式)
- [注释标记说明](#注释标记说明)
- [完整教程](#完整教程)
- [配套环境](#配套环境)

---

## ⚡ 快速开始

```bash
# 1. 切换到 JDK 8（Step Into 需要）
export JAVA_HOME="$HOME/dev/jdk8"
export PATH="$JAVA_HOME/bin:$PATH"
java -version   # 1.8.x

# 2. 编译所有 Demo
cd src && javac -encoding UTF-8 *.java

# 3. 运行任意 Demo
java -cp . ThreadDemo
```

---

## 🗺️ 学习路线

| 阶段 | Demo 文件 | 掌握内容 | 教程 |
|:---:|:---|:---|:---:|
| **一** | [`ThreadDemo.java`](src/ThreadDemo.java) | 线程生命周期、`volatile` 可见性、`wait`/`notify` | [→](docs/JUC-study-guide.md#s11) |
| **二** | [`AtomicDemo.java`](src/AtomicDemo.java) | CAS 自旋、ABA 问题、`LongAdder` 分段累加 | [→](docs/JUC-study-guide.md#s21) |
| **三** | [`LockDemo.java`](src/LockDemo.java) | **AQS 框架**、`ReentrantLock`、`ReadWriteLock` | [→](docs/JUC-study-guide.md#s31) |
| **四** | [`ThreadPoolDemo.java`](src/ThreadPoolDemo.java) | `ThreadPoolExecutor`、`FutureTask`、拒绝策略 | [→](docs/JUC-study-guide.md#s41) |
| **五** | [`ConcurrentCollectionDemo.java`](src/ConcurrentCollectionDemo.java) | `ConcurrentHashMap`(JDK8)、阻塞队列对比 | [→](docs/JUC-study-guide.md#s51) |
| **六** | [`SyncToolsDemo.java`](src/SyncToolsDemo.java) | `CountDownLatch`、`Semaphore`、`CyclicBarrier`、`CompletableFuture` | [→](docs/JUC-study-guide.md#s61) |

---

## 📁 文件结构

```
jdk8u-demo/
├── src/
│   ├── ThreadDemo.java               # 阶段一
│   ├── AtomicDemo.java               # 阶段二
│   ├── LockDemo.java                 # 阶段三 ★ 花 40% 时间深挖
│   ├── ThreadPoolDemo.java           # 阶段四
│   ├── ConcurrentCollectionDemo.java # 阶段五
│   ├── SyncToolsDemo.java            # 阶段六
│   └── HashMapProbe.java             # Step Into 验证 Demo
├── docs/
│   └── JUC-study-guide.md            # 完整教程 + 自检清单
├── jdk-src/                          # JDK 8 类库源码（可 Step Into）
│   ├── java/lang/                    # Thread、Object 等
│   ├── java/util/concurrent/         # JUC 核心
│   └── sun/misc/Unsafe.java          # CAS native 入口
└── README.md
```

---

## 🧭 使用方式

| 步骤 | 操作 |
|:---:|:---|
| 1 | **先运行** Demo 看效果 |
| 2 | 在标注 **`★★`** 的行**打断点** |
| 3 | **F11 Step Into** 进入 JDK 源码 |
| 4 | **阅读**每个方法顶部的 `【掌握要求】` 清单 |

---

## 🏷️ 注释标记说明

| 标记 | 含义 |
|:---|:---|
| `★` | 重要知识点 |
| `★★` | **打断点 + Step Into** |
| `【掌握要求】` | 该知识点的掌握清单 |
| `→` | 方法调用链追踪路径 |

---

## 📖 完整教程

查看 **[docs/JUC-study-guide.md](docs/JUC-study-guide.md)**，包含：

- 章节锚点跳转 · Cheat sheets 速查表 · **自检清单** · 对比类面试题

---

## 🔗 配套环境

| 路径 | 说明 |
|:---|:---|
| `../jdk8u/` | OpenJDK 8u432 完整源码 |
| `../env-jdk8.sh` | 环境变量配置脚本 |

---

<p align="center">
  <sub>Demo 代码 MIT License · JDK 源码 OpenJDK GPLv2+CE</sub>
</p>

---

<br>
<br>

---

<a id="english"></a>
# JUC Source Learning Project

> Stop reading blogs. Start reading source.

A 6-phase deep-dive into `java.util.concurrent` internals — with runnable demos, annotated source, and a full study guide. Set a breakpoint, hit **F11 Step Into**, and trace the JDK internals yourself.

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
| **I** | [`ThreadDemo.java`](src/ThreadDemo.java) | Thread lifecycle, `volatile`, `wait`/`notify` | [→](docs/JUC-study-guide.md#s11) |
| **II** | [`AtomicDemo.java`](src/AtomicDemo.java) | CAS spin, ABA problem, `LongAdder` | [→](docs/JUC-study-guide.md#s21) |
| **III** | [`LockDemo.java`](src/LockDemo.java) | **AQS framework**, `ReentrantLock`, `ReadWriteLock` | [→](docs/JUC-study-guide.md#s31) |
| **IV** | [`ThreadPoolDemo.java`](src/ThreadPoolDemo.java) | `ThreadPoolExecutor`, `FutureTask`, rejection policies | [→](docs/JUC-study-guide.md#s41) |
| **V** | [`ConcurrentCollectionDemo.java`](src/ConcurrentCollectionDemo.java) | `ConcurrentHashMap` (JDK8), blocking queues | [→](docs/JUC-study-guide.md#s51) |
| **VI** | [`SyncToolsDemo.java`](src/SyncToolsDemo.java) | `CountDownLatch`, `Semaphore`, `CyclicBarrier`, `CompletableFuture` | [→](docs/JUC-study-guide.md#s61) |

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
│   └── HashMapProbe.java             # Step Into verification
├── docs/
│   └── JUC-study-guide.md            # Full tutorial with self-check lists
├── jdk-src/                          # JDK 8 class library sources
│   ├── java/lang/                    # Thread, Object, etc.
│   ├── java/util/concurrent/         # JUC core
│   └── sun/misc/Unsafe.java          # CAS native entry
└── README.md
```

---

## 🧭 How to Use

| Step | Action |
|:---:|:---|
| 1 | **Run** the demo to observe the output |
| 2 | **Set a breakpoint** on lines marked with `★★` |
| 3 | **F11 Step Into** the JDK source code |
| 4 | **Read** the mastery checklist above each method |

---

## 🏷️ Annotation Legend

| Marker | Meaning |
|:---|:---|
| `★` | Key concept |
| `★★` | **Breakpoint + Step Into** |
| `【掌握要求】` | Mastery checklist |
| `→` | Method call chain |

---

## 📖 Full Study Guide

See **[docs/JUC-study-guide.md](docs/JUC-study-guide.md)** — with anchor links, comparison tables, self-check lists, and interview questions.

---

## 🔗 Companion Environment

| Path | Description |
|:---|:---|
| `../jdk8u/` | OpenJDK 8u432 full source |
| `../env-jdk8.sh` | Environment setup script |

---

<p align="center">
  <sub>Demo code under MIT License · JDK source under OpenJDK GPLv2+CE</sub>
</p>
