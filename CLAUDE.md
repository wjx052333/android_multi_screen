# CLAUDE.md - 四宫格多路视频播放器 (Multi-Screen Video Player)

## 项目概述
本项目是一个 Android 本地四宫格（2x2）多路视频播放器 App，支持在同一屏幕内同时流畅播放 4 个独立的本地视频文件。

### 核心特性
- **四宫格独立交互**：每个格子具备独立的播放/暂停、快进 10 秒、快退 10 秒、进度滑块拖拽、静音切换、更换视频以及关闭释放等操作。
- **全屏沉浸与自动隐藏控制浮层**：
  - 未选择视频时显示占位卡片，点击任意区域呼出系统文件选取器（SAF）。
  - 选定视频后自动开始全屏纯净播放，所有控制按钮默认隐藏。
  - 点击画面空白处浮现半透明操作蒙层，3 秒无任何触摸交互后自动渐隐；再次点击可立即手动折叠。
- **多路音频并发混音**：
  - 统一配置多路音频属性（`AUDIO_CONTENT_TYPE_MOVIE` + `USAGE_MEDIA`），关闭各 ExoPlayer 内部抢占（`handleAudioFocus = false`），实现系统底层 `AudioFlinger` 多路原生混音。
  - 每个窗口提供独立静音/出声开关。
- **防 OOM 与硬件解码耗尽容错**：
  - **防 OOM**：深度定制 `DefaultLoadControl`（限制缓冲区为 2.5s~5s，单实例目标缓冲内存仅 3MB，4 路总缓冲 < 16MB）。
  - **高效渲染**：采用 `SurfaceView`（零拷贝渲染架构），减轻 GPU 显存与合成器压力。
  - **解码器异常保护**：自动捕获 `MediaCodecRenderer.DecoderInitializationException`（硬件解码器实例达到 SoC 上限或不支持格式），优雅降级展示警告提示和重试/关闭按钮，并立即释放底层编解码器资源，防止 App 崩溃或卡死其他正常播放的窗口。

---

## 技术栈与依赖
- **语言**：Kotlin 1.9.24 / JVM 17
- **UI 框架**：Jetpack Compose (Material3) + Compose BOM `2024.06.00`
- **播放引擎**：AndroidX Media3 ExoPlayer `1.4.1`
- **文件与权限**：Storage Access Framework (`ActivityResultContracts.OpenDocument`，无需危险存储权限)
- **构建工具**：Gradle 8.6 + Android Gradle Plugin 8.4.2
- **镜像加速**：内置阿里云 Maven 与 Google 镜像代理

---

## 目录结构
```text
android_multi_screen/
├── app/
│   ├── build.gradle.kts           # 模块构建配置与依赖项
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml # 清单配置（沉浸式全屏、横竖屏自适应）
│       │   └── java/com/example/multiscreen/
│       │       ├── MainActivity.kt               # 主入口，沉浸式全屏与系统UI隐藏
│       │       ├── model/
│       │       │   └── VideoCellModel.kt         # 单元格状态模型与时间格式化
│       │       ├── player/
│       │       │   └── PlayerFactory.kt          # ExoPlayer 工厂（轻量缓存、混音、错误提示）
│       │       ├── state/
│       │       │   └── VideoCellController.kt    # 单格控制器（状态机、3s渐隐定时器、生命周期）
│       │       └── ui/
│       │           ├── MultiScreenApp.kt         # 2x2 网格容器与 Activity 生命周期协同
│       │           ├── VideoCell.kt              # 单格容器（SurfaceView PlayerView 与手势）
│       │           ├── components/
│       │           │   ├── CellControllerOverlay.kt # 控制浮层（播放/暂停/进度条/静音/快进退）
│       │           │   ├── EmptyCellPlaceholder.kt  # 空状态占位与文件选择引导
│       │           │   └── ErrorCellOverlay.kt      # 解码失败/错误重试提示
│       │           └── theme/
│       │               └── Theme.kt              # 深色全屏观影主题
│       └── test/java/com/example/multiscreen/   # 单元测试 (Model, PlayerFactory)
├── gradle/
│   ├── libs.versions.toml         # 版本目录
│   └── wrapper/                   # Gradle 8.6 Wrapper
├── build.gradle.kts               # 根构建脚本
├── settings.gradle.kts            # 插件与依赖仓库源（含阿里云镜像配置）
└── local.properties               # 本地 Android SDK 路径
```

---

## 编译与测试指南

### 1. 前置环境要求
- **JDK**：Java 17 或以上（支持 Android Studio 自带的 JBR 17/21）
- **Android SDK**：
  - Compile SDK: 34
  - Build-Tools: 34.0.0
  - Min SDK: 24 (Android 7.0+)
  - Target SDK: 34 (Android 14)

### 2. 配置 local.properties
确保根目录下的 `local.properties` 指向本机 Android SDK 路径，例如：
```properties
sdk.dir=C\:\\Users\\<用户名>\\AppData\\Local\\Android\\Sdk
```
*(在 Linux/macOS 上为 `sdk.dir=/Users/<用户名>/Library/Android/sdk`)*

### 3. 执行单元测试
在终端（或 Git Bash）中执行：
```bash
# Windows (cmd / powershell)
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
.\gradlew.bat testDebugUnitTest

# Bash / Linux / macOS
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew testDebugUnitTest
```

### 4. 编译并打包 Debug APK
在终端执行：
```bash
# Windows (cmd / powershell)
.\gradlew.bat assembleDebug

# Bash / Linux / macOS
./gradlew assembleDebug
```
编译成功后，生成的 APK 文件位于：
```text
app/build/outputs/apk/debug/app-debug.apk
```

### 5. 安装至真机或模拟器
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
打开应用后，点击 4 个格子中的任意格子，即可独立通过系统文件选择器选取本地视频并体验并发混音播放。
