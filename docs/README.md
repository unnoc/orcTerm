# OrcTerm - Android Linux 终端与服务器管理器

OrcTerm 是一款专为开发者和运维工程师设计的专业 Android 应用程序，旨在让您能够直接通过移动设备管理 Linux 服务器、Docker 容器和文件系统。

## 🏗️ 技术架构

- **编程语言**: Java 11
- **UI 框架**: Android Views (XML), Material Design 3
- **架构模式**: MVVM (ViewModel + LiveData)
- **数据库**: Room (SQLite)
- **SSH 核心**: 基于 `libssh2` 的 JNI Bridge (C/C++)
- **异步处理**: Java `ExecutorService`

## 🧩 核心模块

### 1. 连接管理器 (Connection Manager)
- 基于 Room 数据库的主机管理（添加/编辑/删除）。
- 安全的密码存储（占位符，建议生产环境使用 Keystore）。
- JNI 层实现的连接池逻辑。

### 2. 终端模拟器 (Terminal SSH)
- 基于 JNI 的 PTY Shell 执行。
- 实时输入/输出流处理。
- 后台线程处理非阻塞 I/O。
- 支持 SSH、Telnet 和本地 Shell 协议。
- 支持端口转发 (Port Forwarding)。

### 3. Docker 管理器 (Docker Manager)
- **无代理模式**: 使用 SSH `exec` 通道直接运行 `docker` CLI 命令。
- **功能特性**:
  - 列出容器 (`docker ps`)。
  - 容器操作: 启动、停止、重启。
  - 实时日志查看 (`docker logs --tail`)。

### 4. SFTP 文件管理器 (SFTP File Manager)
- 远程文件浏览。
- 目录导航。
- 文件详情查看。

### 5. 系统监控 (System Monitor)
- 服务器健康状态实时仪表盘。
- **监控指标**:
  - CPU 使用率与平均负载。
  - 内存使用率 (RAM)。
  - 磁盘空间使用情况。

## 📂 项目结构

```
OrcTerm/
├── app/
│   ├── src/main/java/com/orcterm/
│   │   ├── core/           # 业务逻辑与数据模型
│   │   │   ├── ssh/        # JNI 接口定义
│   │   │   ├── transport/  # 传输层抽象 (SSH, Telnet, Local)
│   │   │   ├── terminal/   # 终端模拟器逻辑
│   │   │   ├── docker/     # Docker 相关模型
│   │   │   └── sftp/       # SFTP 相关模型
│   │   ├── data/           # Room 数据库与 Repositories
│   │   └── ui/             # Activities, Adapters, ViewModels
│   ├── src/main/cpp/       # Native 代码
│   │   ├── ssh_bridge.c    # JNI 实现 (libssh2 调用)
│   │   └── CMakeLists.txt  # 构建配置
│   └── src/main/res/       # 布局、资源、绘图文件
```

## 🚀 快速开始

1.  **克隆** 仓库到本地。
2.  **打开** Android Studio (Iguana 或更高版本)。
3.  **同步** Gradle 项目。
4.  在模拟器或真机上 **运行**。

> **注意**: 当前版本包含 JNI 实现，但需要您自行提供编译好的静态库 (`libssh2.a`, `libssl.a`, `libcrypto.a`) 以启用完整的 SSH 功能。详情请参考 [IMPLEMENTATION_GUIDE.md](IMPLEMENTATION_GUIDE.md)。

## 📅 开发路线图

- [x] MVP 架构搭建 (Java + JNI)
- [x] Docker 管理模块
- [x] SFTP 文件管理模块
- [x] 系统监控模块
- [x] 真实的 libssh2 集成
- [x] 终端 ANSI 颜色解析与渲染
- [x] SSH 密钥认证支持
- [x] 端口转发 (SSH Tunnel) 支持
- [x] 多协议支持 (Local Shell, Telnet)
