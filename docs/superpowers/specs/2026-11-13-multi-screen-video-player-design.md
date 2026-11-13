# 四宫格多路视频播放器 (Multi-Screen Video Player) 设计规范

## 1. 概述与需求
开发一个 Android 应用程序，在屏幕上以 2x2 四宫格形式同时播放 4 个独立的本地视频文件。
核心需求：
- **独立性**：每个播放窗口均可独立选择文件、独立播放/暂停、独立快进/快退、独立调节进度、独立静音及独立关闭释放。
- **全屏纯净与自动隐藏**：初始未加载时点击屏幕弹出文件选择器；选择文件后自动开始播放；播放时所有控制按钮默认隐藏；点击画面浮现控制条（播放/暂停、进度滑块、快进/退、音量、关闭、换视频），3 秒无操作自动渐隐。再次点击可立即收起。
- **音频并发混音**：支持 4 路视频音频并发混音播放，统一接管 AudioFocus，关闭播放器内部抢占，每个格子支持独立静音与音量调节。
- **硬件解码与防 OOM 保障**：
  - 深度定制 ExoPlayer `DefaultLoadControl`（限制缓冲区为 2.5s~5s，单实例缓冲内存仅 2~4MB，总体内存 < 16MB）。
  - 使用 `SurfaceView` 实现零拷贝渲染。
  - 智能捕获 `MediaCodecRenderer.DecoderInitializationException` 等硬件解码耗尽/不支持异常，友好提示用户并支持一键释放重置，杜绝 App 崩溃与全屏瘫痪。

---

## 2. 技术栈
- **语言**：Kotlin
- **UI 框架**：Jetpack Compose (Material3)
- **播放引擎**：AndroidX Media3 (ExoPlayer `1.4.1`+)
- **存储与权限**：Storage Access Framework (`ActivityResultContracts.OpenDocument` + `takePersistableUriPermission`)
- **系统要求**：Min SDK 24 (Android 7.0), Target SDK 34 (Android 14)

---

## 3. 架构设计

### 3.1 目录结构
```text
app/src/main/java/com/example/multiscreen/
├── MainActivity.kt                # 沉浸式入口，生命周期统筹
├── model/
│   └── VideoCellModel.kt          # 单元格状态模型定义
├── player/
│   └── PlayerFactory.kt           # ExoPlayer 定制工厂（轻量缓存配置、混音配置）
├── state/
│   └── VideoCellState.kt          # 单个格子的状态持有与控制器
└── ui/
    ├── MultiScreenApp.kt          # 2x2 宫格网格容器
    ├── VideoCell.kt               # 独立播放单元 UI
    ├── components/
    │   ├── EmptyCellPlaceholder.kt # 空状态占位与引导
    │   ├── CellControllerOverlay.kt# 浮层控制器（自动隐藏、进度拖拽）
    │   └── ErrorCellOverlay.kt    # 解码失败/错误友好提示浮层
    └── theme/
        ├── Color.kt
        ├── Theme.kt
        └── Type.kt
```

### 3.2 核心模块职责与接口

#### 3.2.1 `PlayerFactory` (播放器构造工厂)
- **防 OOM 缓冲池定制**：
  ```kotlin
  val loadControl = DefaultLoadControl.Builder()
      .setBufferDurationsMs(
          /* minBufferMs = */ 2500,
          /* maxBufferMs = */ 5000,
          /* bufferForPlaybackMs = */ 1000,
          /* bufferForPlaybackAfterRebufferMs = */ 2000
      )
      .setTargetBufferBytes(3 * 1024 * 1024)
      .setPrioritizeTimeOverSizeThresholds(true)
      .build()
  ```
- **并发混音配置**：
  - 配置 `AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).setUsage(C.USAGE_MEDIA).build()`
  - 设置 `setHandleAudioBecomingNoisy(false)`
  - 设置 `handleAudioFocus = false`（禁止各个实例互相争抢导致暂停）

#### 3.2.2 `VideoCellState` (单个宫格独立控制器)
- 管理当前单元的生命周期：`init()`, `playUri(uri)`, `togglePlayPause()`, `seekTo(ms)`, `seekForward()`, `seekRewind()`, `toggleMute()`, `reset()`。
- 状态属性（Compose State）：
  - `uri: Uri?`
  - `playbackState: CellPlaybackState` (`EMPTY`, `LOADING`, `PLAYING`, `PAUSED`, `ERROR`)
  - `currentPositionMs: Long`, `durationMs: Long`
  - `isMuted: Boolean`
  - `isControlsVisible: Boolean`
  - `errorMessage: String?`
- 定时隐藏调度：使用 Coroutine Scope 维护 `autoHideJob`，3000ms 后自动将 `isControlsVisible` 设为 `false`；用户手势交互时重置倒计时。

#### 3.2.3 异常捕获与硬件解码保护
- 注册 `Player.Listener#onPlayerError`：
  - 判断是否为 `ExoPlaybackException.TYPE_RENDERER` 且 `cause is MediaCodecRenderer.DecoderInitializationException`。
  - 发生解码不足时，捕获异常并将状态置为 `ERROR`，错误信息标明：“设备硬件解码器不足或格式不受支持”。
  - 立即执行 `player.stop()` 释放编解码器句柄，防止占用资源殃及其他单元。

---

## 4. UI与交互规范

### 4.1 四宫格排布
- 使用 `Column` 嵌套两个 `Row`，每格赋予 `Modifier.weight(1f).fillMaxHeight()`。
- 单元格之间添加 1dp 细分隔线（`Color(0x33FFFFFF)`），增强视界区分度。

### 4.2 交互逻辑
1. **空状态**：
   - 居中展示视频图标与提示“点击选择视频”。
   - 点击单元任意区域，通过 Compose `rememberLauncherForActivityResult` 启动 `ActivityResultContracts.OpenDocument()`，筛选 `video/*`。
2. **播放状态**：
   - 选定视频后获取持久化 URI，自动 `prepare()` 并 `play()`。
   - 默认隐藏所有控制控件。
   - 点击画面：
     - 若控制条不可见：展开控制条，并启动 3 秒无操作自动隐藏计时器。
     - 若控制条已可见：立即折叠控制条。
3. **控制层组件**：
   - **顶部操作条**：静音切换（VolumeUp / VolumeOff）、更换视频（Folder）、关闭释放（Close）。
   - **中心操作条**：播放/暂停（Play/Pause 大图标按钮）。
   - **底部进度条**：
     - 进度滑块（`Slider`）：滑动时暂停自动隐藏，松手触发 `seekTo(position)`。
     - 时间戳展示（格式 `mm:ss / mm:ss`）。
     - 快退 10 秒 / 快进 10 秒微调按钮。
4. **错误状态**：
   - 展示警告图标、错误说明文字。
   - 提供“重试”与“关闭释放”操作按钮。

---

## 5. 项目构建与配置
- Gradle 8.x + Android Gradle Plugin 8.5+
- Kotlin 2.0+
- Compose Compiler & Material3
- 依赖项：
  - `androidx.media3:media3-exoplayer:1.4.1`
  - `androidx.media3:media3-ui:1.4.1`
  - `androidx.activity:activity-compose:1.9.2`
  - `androidx.compose.material3:material3`
  - `androidx.compose.material:material-icons-extended`
