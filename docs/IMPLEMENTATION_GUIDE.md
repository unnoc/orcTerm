# OrcTerm - 真实 SSH 实现指南

目前，`ssh_bridge.c` 包含了基于 `libssh2` 的真实实现逻辑。为了使 OrcTerm 能够正常编译和运行，您需要为 Android 平台编译 `libssh2` 和 `openssl` 静态库，并将其放置在正确的目录中。

## 🛠️ 第一步：编译原生库

您需要将 `libssh2` 和 `openssl` 编译为适用于 Android 架构（arm64-v8a, x86_64）的静态库 (`.a`)。

### 推荐构建脚本
建议使用类似 [android-libs](https://github.com/n8fr8/android-libs) 的脚本，或者使用 NDK 工具链手动编译。

**目标目录结构：**
```
app/src/main/cpp/
├── include/
│   ├── libssh2.h
│   ├── libssh2_sftp.h
│   └── openssl/          # OpenSSL 头文件
├── libs/
│   ├── arm64-v8a/
│   │   ├── libssh2.a
│   │   ├── libssl.a
│   │   └── libcrypto.a
│   ├── x86_64/
│   │   ├── libssh2.a
│   │   ├── libssl.a
│   │   └── libcrypto.a
```

## 📝 第二步：检查 CMakeLists.txt

确保 `app/src/main/cpp/CMakeLists.txt` 中的链接逻辑已正确配置（当前代码库已默认配置好，只需确保文件存在）：

```cmake
include_directories(${CMAKE_SOURCE_DIR}/include)

add_library(ssl STATIC IMPORTED)
set_target_properties(ssl PROPERTIES IMPORTED_LOCATION "${CMAKE_SOURCE_DIR}/libs/${ANDROID_ABI}/libssl.a")

add_library(crypto STATIC IMPORTED)
set_target_properties(crypto PROPERTIES IMPORTED_LOCATION "${CMAKE_SOURCE_DIR}/libs/${ANDROID_ABI}/libcrypto.a")

# ... (libssh2 编译配置) ...

target_link_libraries(orcterm-jni
    ssh2_static
    ssl
    crypto
    ${log-lib}
    z
)
```

## 💻 第三步：JNI 实现概览 (ssh_bridge.c)

`ssh_bridge.c` 实现了以下核心功能：

### 1. 连接与会话初始化
- 创建 Socket 连接。
- 初始化 `libssh2_session`。
- 执行 SSH 握手 (`handshake`)。

### 2. 认证机制
- **密码认证**: `libssh2_userauth_password`
- **公钥认证**: `libssh2_userauth_publickey_fromfile`

### 3. Shell 与 命令执行
- **Shell**: 打开 Channel，请求 PTY，启动 Shell 模式。
- **Exec**: 打开 Channel，执行单条命令 (`libssh2_channel_exec`)，读取结果并关闭。

### 4. SFTP 支持
- 初始化 SFTP 会话。
- 遍历目录 (`libssh2_sftp_readdir`) 并构建 JSON 格式的文件列表返回给 Java 层。

### 5. 端口转发 (Port Forwarding)
- **Local Forwarding**: 使用 `libssh2_channel_direct_tcpip` 建立隧道。
- 数据读写通过 JNI 暴露的 `readChannel` 和 `writeChannel` 接口在 Java 层线程池中进行调度。

## ⚠️ 关键注意事项

1.  **线程安全**: 所有 JNI 调用都是阻塞或半阻塞的。必须确保它们在工作线程（Worker Threads）中被调用，严禁在 UI 线程（Main Thread）中直接调用 Native 方法。
2.  **错误处理**: 检查 `libssh2` 函数的返回值。负值通常表示错误（如 `LIBSSH2_ERROR_SOCKET_NONE`）。
3.  **内存管理**: 确保在断开连接时释放 `SshContext` 结构体及相关 Session/Channel 资源，防止内存泄漏。
