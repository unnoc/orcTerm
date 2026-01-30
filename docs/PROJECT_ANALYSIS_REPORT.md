# 项目分析报告 (Project Analysis Report)

## 1. 项目概述与技术栈 (Project Overview & Tech Stack)

### 1.1 项目概述
OrcTerm 是一个功能强大的 Android 终端模拟器应用，旨在为用户提供便捷的远程服务器管理能力。它支持 SSH、Telnet 和本地 Shell 协议，允许用户通过手机随时随地连接和管理服务器。项目不仅实现了标准的终端仿真功能，还集成了端口转发、密钥管理等高级特性。

### 1.2 技术栈
*   **开发语言**: Java (主要业务逻辑与 UI), C/C++ (底层协议支持)
*   **构建工具**: Gradle
*   **核心库**:
    *   **libssh2**: 通过 JNI (Java Native Interface) 集成，提供底层的 SSH 协议支持。
    *   **Android SDK**: 提供 UI 组件、系统服务（如输入法、剪贴板）和网络支持。
*   **架构模式**: 分层架构 (UI Layer -> Logic Layer -> Transport Layer -> Native Layer)。

## 2. 核心架构与模块设计 (Core Architecture & Module Design)

项目采用清晰的分层架构，确保各模块职责单一，易于维护和扩展。

### 2.1 模块划分
1.  **UI 层 (User Interface)**
    *   `TerminalActivity`: 主活动，负责界面展示、生命周期管理和用户交互。
    *   `TerminalView`: 自定义 View，负责终端屏幕的渲染（将字符网格绘制到 Canvas）。
    *   `TerminalInputView`: 处理软键盘输入，适配 Android 输入法连接 (InputConnection)。

2.  **逻辑层 (Logic Core)**
    *   `TerminalSession`: 核心会话管理类，协调网络传输与终端逻辑，维护连接状态。
    *   `TerminalEmulator`: 终端仿真器，负责解析 ANSI 转义序列，维护屏幕字符缓冲区 (Char Buffer) 和样式缓冲区 (Style Buffer)。

3.  **传输层 (Transport Layer)**
    *   `Transport` (Interface): 定义连接、断开、读写等标准接口。
    *   `SshTransport`: 实现 SSH 协议，支持密码/密钥认证及端口转发。
    *   `TelnetTransport`: 实现 Telnet 协议 (基于 Socket)。
    *   `LocalTransport`: 实现本地 Shell (基于 ProcessBuilder/Runtime)。

4.  **原生层 (Native Layer)**
    *   `SshNative` (Java): JNI 包装类，声明 native 方法。
    *   `ssh_bridge.c` (C): JNI 实现，调用 libssh2 库完成实际的加密通信。

### 2.2 核心类图关系
```
TerminalActivity
  |--> TerminalSession
         |--> TerminalEmulator (屏幕状态维护)
         |--> Transport (接口)
                ^
                | (实现)
         +------+------+------+
         |             |      |
    SshTransport  TelnetTransport  LocalTransport
         |
    SshNative (JNI)
         |
      libssh2
```

## 3. 关键业务流程 (Key Business Processes)

### 3.1 建立连接流程 (SSH)
1.  用户在 UI 输入主机信息，点击连接。
2.  `TerminalSession.connect()` 被调用，根据协议创建 `SshTransport` 实例。
3.  `SshTransport.connect()` 调用 `SshNative.connect()` (JNI)。
4.  JNI 层调用 libssh2 API 完成 TCP 连接、握手和认证。
5.  认证成功后，开启 Shell 通道 (`openShell`)。
6.  `TerminalSession` 启动读取线程 (`startReading`)，持续监听数据。

### 3.2 数据输入与输出
*   **输入 (Input)**: 用户按键 -> `TerminalInputView` 捕获 -> `TerminalSession.write()` -> `Transport.write()` -> 发送至远程服务器。
*   **输出 (Output)**: 远程服务器发送数据 -> `Transport.read()` -> `TerminalSession` 收到数据 -> `TerminalEmulator.append()` (解析 ANSI) -> 更新缓冲区 -> 通知 UI 重绘。

### 3.3 端口转发 (Port Forwarding)
1.  调用 `SshTransport.startLocalForwarding()`。
2.  在本地开启 `ServerSocket` 监听指定端口。
3.  接收到本地连接后，通过 `sshNative.openDirectTcpIp()` 在 SSH 连接上开启专用通道。
4.  启动两个线程分别处理 "本地Socket -> SSH通道" 和 "SSH通道 -> 本地Socket" 的数据双向拷贝。

## 4. 部署与配置指南 (Deployment & Configuration Guide)

### 4.1 开发环境搭建
*   安装 Android Studio。
*   配置 Android NDK (用于编译 C 代码)。
*   导入项目，Gradle 会自动处理依赖。

### 4.2 编译说明
*   **Java 代码**: 直接通过 Gradle `assembleDebug` 编译。
*   **Native 代码**: CMakeLists.txt 定义了编译规则，Gradle 同步时会自动生成 `libssh_bridge.so`。

### 4.3 运行要求
*   Android 5.0 (API Level 21) 及以上。
*   网络权限 (INTERNET)。

## 5. 已知问题与优化建议 (Known Issues & Optimization Suggestions)

### 5.1 已知问题
1.  **终端仿真不完整**: 目前仅支持基础 ANSI 序列，对于复杂的全屏应用（如 vim, htop）可能存在渲染错误或颜色显示不正确。
2.  **输入兼容性**: 部分特殊按键（如 Ctrl 组合键、功能键）在某些软键盘上可能无法触发或映射错误。
3.  **性能瓶颈**: 在大量数据快速刷新时，`TerminalView` 的重绘可能导致 UI 卡顿。

### 5.2 优化建议
1.  **完善 ANSI 支持**: 扩展 `TerminalEmulator`，支持 xterm-256color，完善光标移动和屏幕清除指令。
2.  **渲染优化**: 采用双缓冲机制，或只重绘脏矩形区域 (Dirty Rect)，避免全屏重绘。
3.  **会话保持**: 引入 Service 将 `TerminalSession` 放入后台运行，防止屏幕旋转或切换应用时连接中断。
4.  **文件传输**: 基于 libssh2 的 SFTP 功能，添加文件上传/下载模块。

## 6. 后续开发规划 (Subsequent Development Plan)

1.  **阶段一：基础体验优化**
    *   完善中文注释 (已进行中)。
    *   修复已知的输入法遮挡和按键映射问题。
    *   优化底部安全区域适配。

2.  **阶段二：功能增强**
    *   实现多 Tab 会话管理。
    *   添加常用命令快捷栏 (Snippets)。
    *   支持自定义字体和配色方案。

3.  **阶段三：高级特性**
    *   集成 SFTP 文件管理器。
    *   支持 SSH 代理跳转 (Jump Host)。
    *   云端同步主机配置 (加密存储)。
