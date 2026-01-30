# 如何启用真实 SSH 连接库

当前项目代码已包含完整的 SSH 逻辑，但为了成功编译，您必须提供编译好的静态库文件（`libssh2.a`, `libssl.a`, `libcrypto.a`）。由于版权和文件大小限制，这些库文件通常不包含在 Git 仓库中。

## 选项 1：下载预编译库（推荐快速上手）

如果您不想自己编译，可以尝试寻找适用于 Android 的预编译二进制文件。
您需要的是 **静态库 (.a)**，而不是动态库 (.so)。

**所需文件：**
1. `libssh2.a`
2. `libssl.a`
3. `libcrypto.a`

**目标架构：**
- `arm64-v8a` (大多数现代 Android 手机)
- `x86_64` (Android 模拟器)

## 选项 2：自行编译（推荐安全性）

您需要 **Linux**、**WSL (Windows Subsystem for Linux)** 或 **macOS** 来正确运行构建脚本。通常不支持 Windows PowerShell。

1. **安装 NDK**: 确保通过 Android Studio SDK Manager 安装了 Android NDK。
2. **设置环境变量**: `export ANDROID_NDK_ROOT=/path/to/your/ndk`
3. **使用构建脚本**:
   可以参考或克隆此仓库：`https://github.com/egorovandreyrm/libssh_android_build_scripts`
   运行 `./build_all_abi.sh`

## 📂 文件放置位置

一旦您获得了 `.a` 文件，请严格按照以下结构放置：

```text
orcTerm/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── cpp/
│   │   │   │   ├── include/          <-- 放置头文件 (.h)
│   │   │   │   │   ├── libssh2.h
│   │   │   │   │   ├── libssh2_sftp.h
│   │   │   │   │   └── openssl/      <-- OpenSSL 头文件目录
│   │   │   │   ├── libs/
│   │   │   │   │   ├── arm64-v8a/    <-- 放置 ARM64 .a 文件
│   │   │   │   │   │   ├── libssh2.a
│   │   │   │   │   │   ├── libssl.a
│   │   │   │   │   │   └── libcrypto.a
│   │   │   │   │   ├── x86_64/       <-- 放置 x86_64 .a 文件
│   │   │   │   │   │   ├── libssh2.a
│   │   │   │   │   │   ├── libssl.a
│   │   │   │   │   │   └── libcrypto.a
```

## 🔄 最终步骤

放置好文件后：
1. 点击 Android Studio 中的 **"Sync Project with Gradle Files"**。
2. 检查 **Build** 输出标签页，确保 CMake 能够找到这些库。
3. 运行应用程序。现在它将使用真实的 SSH 逻辑进行连接。
