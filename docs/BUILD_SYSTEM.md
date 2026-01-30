# Android 构建系统指南 (Build System Guide)

OrcTerm Android 项目采用基于 Gradle 的现代化构建系统，支持多渠道打包、自动化签名、资源混淆和质量检查。

## 1. 核心功能 (Core Features)

*   **多渠道支持 (Flavors)**:
    *   `googlePlay`: 针对 Google Play 商店的构建，包含完整功能。
    *   `foss`: 针对 F-Droid 或开源发布的构建，移除专有依赖 (如有)。
*   **构建变体 (Build Types)**:
    *   `debug`: 调试模式，无混淆，使用调试签名。
    *   `release`: 发布模式，开启混淆 (R8)，开启资源压缩，使用发布签名。
    *   `staging`: 预发布模式，开启混淆但保留调试功能，用于内部测试。
*   **自动化签名**: 通过 `gradle.properties` 配置签名信息，支持本地或 CI/CD 环境注入。
*   **代码混淆与优化**: 集成 ProGuard/R8 规则，优化 APK 体积。

## 2. 配置指南 (Configuration)

### 2.1 签名配置 (Signing)
在 `gradle.properties` 中配置签名信息 (开发环境已预设默认值):

```properties
RELEASE_STORE_FILE=../keystores/release.jks
RELEASE_STORE_PASSWORD=android
RELEASE_KEY_ALIAS=orcterm
RELEASE_KEY_PASSWORD=android
```

**注意**: 在生产环境 (CI/CD) 中，不应将密码直接提交到代码仓库。建议使用环境变量或加密的 `local.properties`。

### 2.2 生成密钥库
如果尚未生成密钥库，可运行脚本:
*   Windows (PowerShell): `.\scripts\generate_keystore.ps1` (需自行创建或使用 keytool)
*   Linux/Mac: `./scripts/generate_keystore.sh`

## 3. 构建命令 (Build Commands)

### 3.1 常用命令
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

### 3.2 使用构建脚本
为了简化操作，我们在 `scripts/` 目录下提供了自动化脚本:

*   **Windows**: `.\scripts\build_release.ps1`
*   **Linux/Mac**: `./scripts\build_release.sh`

运行该脚本将自动清理项目并生成 Google Play 和 FOSS 两个渠道的 APK 和 AAB 文件。

## 4. 输出产物 (Outputs)

构建完成后，产物位于:
*   **APK**: `app/build/outputs/apk/{flavor}/{buildType}/`
*   **AAB**: `app/build/outputs/bundle/{flavor}{BuildType}/`

## 5. CI/CD 集成建议

在 GitHub Actions 或 Jenkins 中集成时:
1.  设置环境变量覆盖 `gradle.properties` 中的签名配置。
2.  运行 `./gradlew test` 进行单元测试。
3.  运行 `./gradlew lint` 进行代码质量检查。
4.  运行 `./gradlew bundleRelease` 生成发布包。
5.  使用 Fastlane 或类似工具自动上传到 Google Play。

## 6. 依赖管理

项目使用标准 Gradle 依赖管理。所有第三方库在 `app/build.gradle` 中定义。
建议定期检查依赖更新:
```bash
./gradlew dependencyUpdates
```
