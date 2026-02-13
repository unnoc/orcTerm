# OrcTerm 文档合集（主题编排版）

## 目录
- [项目概览](#项目概览)
- [技术架构与核心模块](#技术架构与核心模块)
- [项目结构](#项目结构)
- [快速开始与路线图](#快速开始与路线图)
- [构建系统](#构建系统)
- [真实 SSH 连接库](#真实-ssh-连接库)
- [真实 SSH 实现指南](#真实-ssh-实现指南)
- [云端同步后端](#云端同步后端)
- [二维码分享功能](#二维码分享功能)
- [二维码解析问题修复总结](#二维码解析问题修复总结)
- [项目分析报告](#项目分析报告)

## 项目概览

### OrcTerm - Android Linux 终端与服务器管理器

OrcTerm 是一款专为开发者和运维工程师设计的专业 Android 应用程序，旨在让您能够直接通过移动设备管理 Linux 服务器、Docker 容器和文件系统。

## 技术架构与核心模块

### 技术架构

- **编程语言**: Java 11
- **UI 框架**: Android Views (XML), Material Design 3
- **架构模式**: MVVM (ViewModel + LiveData)
- **数据库**: Room (SQLite)
- **SSH 核心**: 基于 `libssh2` 的 JNI Bridge (C/C++)
- **异步处理**: Java `ExecutorService`

### 核心模块

#### 1. 连接管理器 (Connection Manager)
- 基于 Room 数据库的主机管理（添加/编辑/删除）。
- 安全的密码存储（占位符，建议生产环境使用 Keystore）。
- JNI 层实现的连接池逻辑。

#### 2. 终端模拟器 (Terminal SSH)
- 基于 JNI 的 PTY Shell 执行。
- 实时输入/输出流处理。
- 后台线程处理非阻塞 I/O。
- 支持 SSH、Telnet 和本地 Shell 协议。
- 支持端口转发 (Port Forwarding)。

#### 3. Docker 管理器 (Docker Manager)
- **无代理模式**: 使用 SSH `exec` 通道直接运行 `docker` CLI 命令。
- **功能特性**:
  - 列出容器 (`docker ps`)。
  - 容器操作: 启动、停止、重启。
  - 实时日志查看 (`docker logs --tail`)。

#### 4. SFTP 文件管理器 (SFTP File Manager)
- 远程文件浏览。
- 目录导航。
- 文件详情查看。

#### 5. 系统监控 (System Monitor)
- 服务器健康状态实时仪表盘。
- **监控指标**:
  - CPU 使用率与平均负载。
  - 内存使用率 (RAM)。
  - 磁盘空间使用情况。

## 项目结构

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

## 快速开始与路线图

### 快速开始

1.  **克隆** 仓库到本地。
2.  **打开** Android Studio (Iguana 或更高版本)。
3.  **同步** Gradle 项目。
4.  在模拟器或真机上 **运行**。

> **注意**: 当前版本包含 JNI 实现，但需要您自行提供编译好的静态库 (`libssh2.a`, `libssl.a`, `libcrypto.a`) 以启用完整的 SSH 功能。详情请参考 [IMPLEMENTATION_GUIDE.md](docs/IMPLEMENTATION_GUIDE.md)。

### 开发路线图

- [x] MVP 架构搭建 (Java + JNI)
- [x] Docker 管理模块
- [x] SFTP 文件管理模块
- [x] 系统监控模块
- [x] 真实的 libssh2 集成
- [x] 终端 ANSI 颜色解析与渲染
- [x] SSH 密钥认证支持
- [x] 端口转发 (SSH Tunnel) 支持
- [x] 多协议支持 (Local Shell, Telnet)

## 构建系统

### Android 构建系统指南 (Build System Guide)

OrcTerm Android 项目采用基于 Gradle 的现代化构建系统，支持多渠道打包、自动化签名、资源混淆和质量检查。

### 1. 核心功能 (Core Features)

*   **多渠道支持 (Flavors)**:
    *   `googlePlay`: 针对 Google Play 商店的构建，包含完整功能。
    *   `foss`: 针对 F-Droid 或开源发布的构建，移除专有依赖 (如有)。
*   **构建变体 (Build Types)**:
    *   `debug`: 调试模式，无混淆，使用调试签名。
    *   `release`: 发布模式，开启混淆 (R8)，开启资源压缩，使用发布签名。
    *   `staging`: 预发布模式，开启混淆但保留调试功能，用于内部测试。
*   **自动化签名**: 通过 `gradle.properties` 配置签名信息，支持本地或 CI/CD 环境注入。
*   **代码混淆与优化**: 集成 ProGuard/R8 规则，优化 APK 体积。

### 2. 配置指南 (Configuration)

#### 2.1 签名配置 (Signing)
在 `gradle.properties` 中配置签名信息 (开发环境已预设默认值):

```properties
RELEASE_STORE_FILE=../keystores/release.jks
RELEASE_STORE_PASSWORD=android
RELEASE_KEY_ALIAS=orcterm
RELEASE_KEY_PASSWORD=android
```

**注意**: 在生产环境 (CI/CD) 中，不应将密码直接提交到代码仓库。建议使用环境变量或加密的 `local.properties`。

#### 2.2 生成密钥库
如果尚未生成密钥库，可运行脚本:
*   Windows (PowerShell): `.\scripts\generate_keystore.ps1` (需自行创建或使用 keytool)
*   Linux/Mac: `./scripts/generate_keystore.sh`

### 3. 构建命令 (Build Commands)

#### 3.1 常用命令
*   **构建所有 Release APK**:
    ```bash
    ./gradlew assembleRelease
    ```
*   **构建 Google Play Bundle (AAB)**:
    ```bash
    ./gradlew bundleGooglePlayRelease
    ```
*   **运行单元测试**:
    ```bash
    ./gradlew test
    ```
*   **运行 Lint 检查**:
    ```bash
    ./gradlew lint
    ```

#### 3.2 使用构建脚本
为了简化操作，我们在 `scripts/` 目录下提供了自动化脚本:

*   **Windows**: `.\scripts\build_release.ps1`
*   **Linux/Mac**: `./scripts\build_release.sh`

运行该脚本将自动清理项目并生成 Google Play 和 FOSS 两个渠道的 APK 和 AAB 文件。

### 4. 输出产物 (Outputs)

构建完成后，产物位于:
*   **APK**: `app/build/outputs/apk/{flavor}/{buildType}/`
*   **AAB**: `app/build/outputs/bundle/{flavor}{BuildType}/`

### 5. CI/CD 集成建议

在 GitHub Actions 或 Jenkins 中集成时:
1.  设置环境变量覆盖 `gradle.properties` 中的签名配置。
2.  运行 `./gradlew test` 进行单元测试。
3.  运行 `./gradlew lint` 进行代码质量检查。
4.  运行 `./gradlew bundleRelease` 生成发布包。
5.  使用 Fastlane 或类似工具自动上传到 Google Play。

### 6. 依赖管理

项目使用标准 Gradle 依赖管理。所有第三方库在 `app/build.gradle` 中定义。
建议定期检查依赖更新:
```bash
./gradlew dependencyUpdates
```

## 真实 SSH 连接库

### 如何启用真实 SSH 连接库

当前项目代码已包含完整的 SSH 逻辑，但为了成功编译，您必须提供编译好的静态库文件（`libssh2.a`, `libssl.a`, `libcrypto.a`）。这些库文件不包含在 Git 仓库中，需要自行准备并放入 `sshlib/src/main/cpp`。

### 选项 1：使用内置脚本编译（推荐）

适用于 **macOS / Linux / WSL**：

1. **安装 NDK**: 通过 Android Studio SDK Manager 安装 Android NDK。
2. **设置环境变量**: `export ANDROID_NDK_ROOT=/path/to/ndk`
3. **运行脚本**（项目根目录）: `bash docs/scripts/build_libs.sh`

脚本会自动下载依赖、构建并写入：
- `sshlib/src/main/cpp/include`
- `sshlib/src/main/cpp/libs/<abi>`

### 选项 2：使用预编译库（手动放置）

如果您不想自己编译，可以准备适用于 Android 的预编译静态库。
您需要的是 **静态库 (.a)**，而不是动态库 (.so)。

**所需文件（每个 ABI）：**
1. `libssh2.a`
2. `libssl.a`
3. `libcrypto.a`

**目标架构：**
- `arm64-v8a` (大多数现代 Android 手机)
- `x86_64` (Android 模拟器)

### 📂 文件放置位置

一旦您获得了 `.a` 文件，请严格按照以下结构放置：

```text
orcTerm/
├── sshlib/
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

### 🔄 最终步骤

放置好文件后：
1. 点击 Android Studio 中的 **"Sync Project with Gradle Files"**。
2. 检查 **Build** 输出标签页，确保 CMake 能够找到这些库。
3. 运行应用程序。现在它将使用真实的 SSH 逻辑进行连接。

## 真实 SSH 实现指南

### OrcTerm - 真实 SSH 实现指南

目前，`ssh_bridge.c` 包含了基于 `libssh2` 的真实实现逻辑。为了使 OrcTerm 能够正常编译和运行，您需要为 Android 平台准备 `libssh2` 和 `openssl` 静态库，并将其放置在 `sshlib/src/main/cpp` 中。

### 🛠️ 第一步：编译原生库

您需要将 `libssh2` 和 `openssl` 编译为适用于 Android 架构（arm64-v8a, x86_64）的静态库 (`.a`)。

#### 推荐构建脚本
建议直接使用项目内脚本 `docs/scripts/build_libs.sh`，会自动下载依赖并产出 `.a` 文件。

**目标目录结构：**
```
sshlib/src/main/cpp/
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

### 📝 第二步：检查 CMakeLists.txt

确保 `sshlib/src/main/cpp/CMakeLists.txt` 中的链接逻辑已正确配置（当前代码库已默认配置好，只需确保文件存在）：

```cmake
include_directories(${CMAKE_SOURCE_DIR}/include)

set(OPENSSL_ROOT_DIR ${CMAKE_SOURCE_DIR})
set(OPENSSL_USE_STATIC_LIBS TRUE)
set(OPENSSL_SSL_LIBRARY ${CMAKE_SOURCE_DIR}/libs/${ANDROID_ABI}/libssl.a)
set(OPENSSL_CRYPTO_LIBRARY ${CMAKE_SOURCE_DIR}/libs/${ANDROID_ABI}/libcrypto.a)
find_package(OpenSSL REQUIRED)

add_subdirectory(${CMAKE_SOURCE_DIR}/libssh2_official ${CMAKE_BINARY_DIR}/libssh2_build)

target_link_libraries(orcterm-jni
    libssh2_static
    OpenSSL::SSL
    OpenSSL::Crypto
    ${log-lib}
    z
)
```

### 💻 第三步：JNI 实现概览 (ssh_bridge.c)

`ssh_bridge.c` 实现了以下核心功能：

#### 1. 连接与会话初始化
- 创建 Socket 连接。
- 初始化 `libssh2_session`。
- 执行 SSH 握手 (`handshake`)。

#### 2. 认证机制
- **密码认证**: `libssh2_userauth_password`
- **公钥认证**: `libssh2_userauth_publickey_fromfile`

#### 3. Shell 与 命令执行
- **Shell**: 打开 Channel，请求 PTY，启动 Shell 模式。
- **Exec**: 打开 Channel，执行单条命令 (`libssh2_channel_exec`)，读取结果并关闭。

#### 4. SFTP 支持
- 初始化 SFTP 会话。
- 遍历目录 (`libssh2_sftp_readdir`) 并构建 JSON 格式的文件列表返回给 Java 层。

#### 5. 端口转发 (Port Forwarding)
- **Local Forwarding**: 使用 `libssh2_channel_direct_tcpip` 建立隧道。
- 数据读写通过 JNI 暴露的 `readChannel` 和 `writeChannel` 接口在 Java 层线程池中进行调度。

### ⚠️ 关键注意事项

1.  **线程安全**: 所有 JNI 调用都是阻塞或半阻塞的。必须确保它们在工作线程（Worker Threads）中被调用，严禁在 UI 线程（Main Thread）中直接调用 Native 方法。
2.  **错误处理**: 检查 `libssh2` 函数的返回值。负值通常表示错误（如 `LIBSSH2_ERROR_SOCKET_NONE`）。
3.  **内存管理**: 确保在断开连接时释放 `SshContext` 结构体及相关 Session/Channel 资源，防止内存泄漏。

## 云端同步后端

### 功能概述
- 后端模块位于 `orcTerm-backend`，提供账户注册、登录与数据同步接口。
- 认证使用服务端生成的同步令牌（非 JWT），通过 `Authorization: Bearer <token>` 访问同步接口。
- 数据同步以 `updatedAt` 为准进行冲突判定，冲突项将返回给客户端处理。

### 接口说明
- `POST /auth/register`、`POST /auth/login`：返回 `token` 与 `expiresAt`。
- `GET /sync/status`：返回 `serverTime`、`lastUpdatedAt`、`totalCount`。
- `GET /sync/pull?since=0&limit=200&types=note,macro`：
  - 返回 `items`、`serverTime`、`hasMore`、`nextSince`。
- `POST /sync/push`：
  - 请求体为 `changes` 列表，返回 `accepted`、`conflicts`、`skipped`、`serverTime`。

## 二维码分享功能

### OrcTerm 二维码分享功能

#### 功能概述

为主机列表添加了二维码分享功能，支持密码直接包含在二维码中，让其他设备可以通过扫码一键添加主机配置，无需手动输入密码。

#### 实现的功能

##### 1. 二维码生成 (ServersFragment)
- **位置**: 长按主机列表中的任意主机 → "生成二维码"
- **功能**: 生成包含主机配置信息的二维码
- **二维码内容**: JSON格式的主机配置数据

##### 2. 二维码内容格式
```json
{
    "type": "orcterm_host",
    "version": "1.0",
    "alias": "主机别名",
    "hostname": "主机地址",
    "port": 端口号,
    "username": "用户名",
    "auth_type": 认证类型(0=密码, 1=密钥),
    "key_path": "密钥路径(密钥认证时)",
    "os_name": "系统名称(可选)",
    "os_version": "系统版本(可选)",
    "container_engine": "容器引擎(可选)",
    "password": "密码(如果存在)",
    "password_required": false/true
}
```

##### 3. 二维码显示功能
- **显示内容**: 二维码图片、主机信息、操作说明
- **操作按钮**:
  - **复制配置JSON**: 复制主机配置到剪贴板
  - **保存二维码图片**: 保存二维码图片到相册

##### 4. 扫码添加 (MainActivity)
- **入口**: FAB按钮 → "扫码导入"
- **支持格式**:
  - 现有的 `orcterm://import` 格式
  - 新的 JSON 格式二维码

##### 5. 扫码解析逻辑
- **JSON验证**: 验证二维码格式和必要字段
- **重复检查**: 检查主机是否已存在
- **安全处理**: 不在二维码中包含密码，需要用户手动输入

#### 安全特性

##### 1. 密码处理
- **密码包含**: 二维码现在包含密码信息，无需手动输入
- **可选包含**: 如果主机有密码，则包含在二维码中
- **安全提示**: 当二维码包含密码时显示安全警告
- **向后兼容**: 支持不包含密码的旧格式二维码

##### 2. 验证机制
- **格式验证**: 严格的JSON格式验证
- **字段验证**: 必要字段完整性检查
- **重复检测**: 防止添加重复主机

#### 技术实现

##### 1. 依赖库
```gradle
implementation 'com.google.zxing:core:3.4.1'  // 二维码生成
implementation 'com.google.zxing:android-integration:3.3.0'  // 二维码扫描
```

##### 2. 关键方法
- **生成二维码**: `generateQRCode(HostEntity host)`
- **解析二维码**: `handleHostJson(String jsonStr)`
- **保存图片**: `saveQRCodeImage(Bitmap bitmap, String alias)`
- **密码输入**: `showPasswordInputDialog(HostEntity host)`

##### 3. 数据库操作
- **检查重复**: 通过 hostname + port + username 组合检查
- **异步保存**: 使用后台线程执行数据库操作
- **UI更新**: 在主线程更新UI状态

#### 使用流程

##### 分享流程
1. 长按主机列表中的主机
2. 选择"生成二维码"
3. 查看生成的二维码
4. 可选择复制JSON或保存图片
5. 其他设备扫描该二维码

##### 扫码添加流程
1. 点击FAB按钮
2. 选择"扫码导入"
3. 扫描二维码
4. 如果二维码包含密码，直接添加
5. 如果二维码不包含密码，提示输入密码后添加
6. 自动添加到主机列表

#### 错误处理

##### 1. 二维码生成失败
- 显示错误信息
- 提供备用方案（复制JSON）

##### 2. 二维码解析失败
- 验证格式错误
- 提供友好的错误提示

##### 3. 保存失败
- 检查存储权限
- 提供重试机制

#### 兼容性

##### 1. 向后兼容
- 保持对现有 `orcterm://import` 格式的支持
- 新格式作为扩展功能

##### 2. 版本控制
- JSON中包含 `version` 字段
- 支持未来格式升级

#### 文件修改清单

##### 新增文件
- `TerminalFragment.java` - 终端页面占位符
- `FilesFragment.java` - 文件页面占位符
- `fragment_terminal.xml` - 终端页面布局
- `fragment_files.xml` - 文件页面布局

##### 修改文件
- `ServersFragment.java` - 添加二维码生成功能
- `MainActivity.java` - 添加二维码解析功能
- `activity_main.xml` - 调整ViewPager页面数量

#### 总结

这个二维码分享功能提供了便捷的主机配置共享方式，同时保持了良好的安全性和用户体验。用户可以快速分享主机配置，无需手动输入复杂的连接参数。

## 二维码解析问题修复总结

### 已修复的问题

#### 1. LiveData类型转换问题
**问题**: `getAllHosts()` 返回 `LiveData<List<HostEntity>>` 而不是直接的 `List<HostEntity>`
**解决方案**: 
```java
androidx.lifecycle.LiveData<java.util.List<HostEntity>> liveData = hostDao.getAllHosts();
java.util.List<HostEntity> existingHosts = liveData.getValue();
if (existingHosts == null) existingHosts = new java.util.ArrayList<>();
```

#### 2. JSON解析错误处理
**问题**: JSON解析异常没有被正确捕获和处理
**解决方案**: 添加详细的try-catch块和错误日志
```java
try {
    JSONObject json = new JSONObject(jsonStr);
    // 处理逻辑
} catch (org.json.JSONException e) {
    Log.e("QR_SCAN", "JSON parsing error: " + e.getMessage());
    Toast.makeText(this, "二维码JSON解析失败", Toast.LENGTH_SHORT).show();
}
```

#### 3. 增强调试日志
**问题**: 缺少详细的调试信息来诊断问题
**解决方案**: 添加全面的日志输出
- 扫描内容日志
- JSON解析日志  
- 字段提取日志
- 密码处理日志

#### 4. 方法结构优化
**问题**: 重复代码和复杂的嵌套逻辑
**解决方案**: 重构为独立的处理方法
- `handleScanResult()` - 处理扫描结果
- `handleHostJson()` - 处理JSON格式
- `processHostJson()` - 处理主机数据

### 调试功能

#### 使用ADB查看日志
```bash
adb logcat | grep "QR_SCAN"
```

#### 关键日志标签
- `QR_SCAN` - 扫描相关
- 查看内容长度
- 查看JSON解析结果
- 查看字段提取结果
- 查看密码处理逻辑

### 验证步骤

#### 1. 生成二维码测试
```json
{
    "type": "orcterm_host",
    "version": "1.0",
    "alias": "测试主机",
    "hostname": "192.168.1.100",
    "port": 22,
    "username": "testuser",
    "auth_type": 0,
    "password": "testpass",
    "password_required": false
}
```

#### 2. 预期行为
- 扫描二维码应该看到 `QR_SCAN` 日志
- JSON解析成功应该看到解析后的JSON
- 主机信息提取应该看到所有字段
- 根据密码状态自动处理

#### 3. 错误处理
- 无效格式显示友好错误消息
- JSON解析失败显示具体错误
- 重复主机检测和提示

### 修复结果

现在的二维码解析功能应该：
1. ✅ 正确识别JSON格式二维码
2. ✅ 正确解析所有字段
3. ✅ 智能处理密码字段
4. ✅ 提供详细的调试信息
5. ✅ 优雅的错误处理
6. ✅ 向后兼容旧格式

### 测试建议

1. 使用模拟器测试基本功能
2. 生成包含密码的二维码测试
3. 生成不包含密码的二维码测试  
4. 使用无效JSON测试错误处理
5. 检查重复主机检测功能

现在二维码解析问题应该已经完全修复！

#### 1. 检查日志输出

使用以下命令查看应用日志：

```bash
adb logcat | grep "QR_SCAN"
```

这将显示所有二维码相关的调试信息。

#### 2. 常见问题分析

##### 问题1：JSON格式错误
**症状**: 日志显示JSON解析失败
**原因**: 生成的JSON格式不正确
**解决方案**: 检查buildHostJson方法，确保JSON格式正确

##### 问题2：类型验证失败
**症状**: 日志显示"无效的主机二维码格式"
**原因**: JSON中缺少或错误的type字段
**解决方案**: 确保生成包含`"type": "orcterm_host"`

##### 问题3：扫描内容为空
**症状**: 日志显示"扫描内容为空"
**原因**: ZXing扫描器返回空内容
**解决方案**: 检查相机权限和ZXing配置

##### 问题4：密码字段问题
**症状**: 扫描成功但密码处理错误
**原因**: JSON中密码字段格式错误
**解决方案**: 确保密码字段正确转义

#### 3. 手动测试JSON

可以使用以下JSON进行测试：

```json
{
    "type": "orcterm_host",
    "version": "1.0",
    "alias": "测试主机",
    "hostname": "192.168.1.100",
    "port": 22,
    "username": "testuser",
    "auth_type": 0,
    "password": "testpass",
    "password_required": false,
    "os_name": "Ubuntu",
    "os_version": "20.04",
    "container_engine": "docker"
}
```

#### 4. 验证二维码生成

检查buildHostJson方法输出的JSON：
1. 确保所有字符串都正确转义
2. 确保JSON格式正确（花括号、逗号等）
3. 确保所有必要字段都存在

#### 5. 验证二维码扫描

检查handleScanResult方法的处理：
1. 确认content不为null
2. 确认content.trim()正确处理
3. 确认JSON解析逻辑正确

#### 6. 常见修复

##### 修复1：JSON转义问题
```java
// 在buildHostJson方法中确保正确转义
private String escapeJson(String str) {
    if (str == null) return "";
    return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
}
```

##### 修复2：密码处理逻辑
```java
// 在handleHostJson方法中正确处理密码
String password = json.optString("password", "");
boolean passwordRequired = json.optBoolean("password_required", false);

if (!password.isEmpty()) {
    host.password = password;
    saveHostFromQR(host);
} else if (passwordRequired) {
    showPasswordInputDialog(host);
} else {
    saveHostFromQR(host);
}
```

#### 7. 测试步骤

1. 生成二维码
2. 查看日志确认JSON生成正确
3. 扫描二维码
4. 查看日志确认扫描内容正确
5. 查看日志确认JSON解析正确
6. 检查最终处理结果

#### 8. 如果问题持续存在

如果以上步骤都无法解决问题，请：

1. 提供完整的错误日志
2. 提供生成的JSON内容
3. 提供扫描的原始内容
4. 提供具体的错误信息

这将帮助更精确地定位问题。

## 项目分析报告

### 项目分析报告 (Project Analysis Report)

#### 1. 项目概述与技术栈 (Project Overview & Tech Stack)

##### 1.1 项目概述
OrcTerm 是一个功能强大的 Android 终端模拟器应用，旨在为用户提供便捷的远程服务器管理能力。它支持 SSH、Telnet 和本地 Shell 协议，允许用户通过手机随时随地连接和管理服务器。项目不仅实现了标准的终端仿真功能，还集成了端口转发、密钥管理等高级特性。

##### 1.2 技术栈
*   **开发语言**: Java (主要业务逻辑与 UI), C/C++ (底层协议支持)
*   **构建工具**: Gradle
*   **核心库**:
    *   **libssh2**: 通过 JNI (Java Native Interface) 集成，提供底层的 SSH 协议支持。
    *   **Android SDK**: 提供 UI 组件、系统服务（如输入法、剪贴板）和网络支持。
*   **架构模式**: 分层架构 (UI Layer -> Logic Layer -> Transport Layer -> Native Layer)。

#### 2. 核心架构与模块设计 (Core Architecture & Module Design)

项目采用清晰的分层架构，确保各模块职责单一，易于维护和扩展。

##### 2.1 模块划分
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

##### 2.2 核心类图关系
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

#### 3. 关键业务流程 (Key Business Processes)

##### 3.1 建立连接流程 (SSH)
1.  用户在 UI 输入主机信息，点击连接。
2.  `TerminalSession.connect()` 被调用，根据协议创建 `SshTransport` 实例。
3.  `SshTransport.connect()` 调用 `SshNative.connect()` (JNI)。
4.  JNI 层调用 libssh2 API 完成 TCP 连接、握手和认证。
5.  认证成功后，开启 Shell 通道 (`openShell`)。
6.  `TerminalSession` 启动读取线程 (`startReading`)，持续监听数据。

##### 3.2 数据输入与输出
*   **输入 (Input)**: 用户按键 -> `TerminalInputView` 捕获 -> `TerminalSession.write()` -> `Transport.write()` -> 发送至远程服务器。
*   **输出 (Output)**: 远程服务器发送数据 -> `Transport.read()` -> `TerminalSession` 收到数据 -> `TerminalEmulator.append()` (解析 ANSI) -> 更新缓冲区 -> 通知 UI 重绘。

##### 3.3 端口转发 (Port Forwarding)
1.  调用 `SshTransport.startLocalForwarding()`。
2.  在本地开启 `ServerSocket` 监听指定端口。
3.  接收到本地连接后，通过 `sshNative.openDirectTcpIp()` 在 SSH 连接上开启专用通道。
4.  启动两个线程分别处理 "本地Socket -> SSH通道" 和 "SSH通道 -> 本地Socket" 的数据双向拷贝。

#### 4. 部署与配置指南 (Deployment & Configuration Guide)

##### 4.1 开发环境搭建
*   安装 Android Studio。
*   配置 Android NDK (用于编译 C 代码)。
*   导入项目，Gradle 会自动处理依赖。

##### 4.2 编译说明
*   **Java 代码**: 直接通过 Gradle `assembleDebug` 编译。
*   **Native 代码**: CMakeLists.txt 定义了编译规则，Gradle 同步时会自动生成 `libssh_bridge.so`。

##### 4.3 运行要求
*   Android 5.0 (API Level 21) 及以上。
*   网络权限 (INTERNET)。

#### 5. 已知问题与优化建议 (Known Issues & Optimization Suggestions)

##### 5.1 已知问题
1.  **终端仿真不完整**: 目前仅支持基础 ANSI 序列，对于复杂的全屏应用（如 vim, htop）可能存在渲染错误或颜色显示不正确。
2.  **输入兼容性**: 部分特殊按键（如 Ctrl 组合键、功能键）在某些软键盘上可能无法触发或映射错误。
3.  **性能瓶颈**: 在大量数据快速刷新时，`TerminalView` 的重绘可能导致 UI 卡顿。

##### 5.2 优化建议
1.  **完善 ANSI 支持**: 扩展 `TerminalEmulator`，支持 xterm-256color，完善光标移动和屏幕清除指令。
2.  **渲染优化**: 采用双缓冲机制，或只重绘脏矩形区域 (Dirty Rect)，避免全屏重绘。
3.  **会话保持**: 引入 Service 将 `TerminalSession` 放入后台运行，防止屏幕旋转或切换应用时连接中断。
4.  **文件传输**: 基于 libssh2 的 SFTP 功能，添加文件上传/下载模块。

#### 6. 后续开发规划 (Subsequent Development Plan)

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
    *   云端同步配置加密 (待实现)。
