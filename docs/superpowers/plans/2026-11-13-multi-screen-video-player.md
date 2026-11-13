# Multi-Screen Video Player Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a 2x2 multi-screen Android video player app that plays up to 4 local videos independently with auto-hiding controls, multi-track audio mixing, and hardware codec exhaustion / OOM protection.

**Architecture:** Independent Component State Machine (方案 1). Each video cell is managed by its own `VideoCellController` holding an ExoPlayer instance with custom low-memory `DefaultLoadControl` and `audioFocus = false`. UI is built with Jetpack Compose + Media3 PlayerView, featuring 3-second auto-hiding overlays for play/pause, seek, volume/mute, replace video, and close.

**Tech Stack:** Kotlin 2.0, Android Gradle Plugin 8.5+, Jetpack Compose (Material3), AndroidX Media3 ExoPlayer 1.4.1+, Storage Access Framework (SAF).

---

## File Structure & Responsibilities

- `settings.gradle.kts` - Gradle settings & dependency repositories
- `build.gradle.kts` - Root build script
- `gradle/wrapper/gradle-wrapper.properties` - Gradle 8.7 wrapper configuration
- `app/build.gradle.kts` - App module build script, Media3 & Compose dependencies
- `app/src/main/AndroidManifest.xml` - App manifest, fullscreen orientation & theme
- `app/src/main/res/values/strings.xml` & `themes.xml` - Resources
- `app/src/main/java/com/example/multiscreen/model/VideoCellModel.kt` - Models for playback state (`CellPlaybackState`), formatting helpers
- `app/src/main/java/com/example/multiscreen/player/PlayerFactory.kt` - ExoPlayer factory with low-memory LoadControl, multi-track audio mixing attributes, and codec error handling
- `app/src/main/java/com/example/multiscreen/state/VideoCellController.kt` - Controller for single cell: player lifecycle, auto-hide timer, seek/play/pause/mute logic
- `app/src/main/java/com/example/multiscreen/ui/components/EmptyCellPlaceholder.kt` - Empty state UI with file selection trigger
- `app/src/main/java/com/example/multiscreen/ui/components/CellControllerOverlay.kt` - Floating control overlay with play/pause, seek slider, forward/rewind, mute, close, replace
- `app/src/main/java/com/example/multiscreen/ui/components/ErrorCellOverlay.kt` - Error overlay for codec / decoding failure with retry & close buttons
- `app/src/main/java/com/example/multiscreen/ui/VideoCell.kt` - Composite cell: SurfaceView PlayerView + gestures + overlays
- `app/src/main/java/com/example/multiscreen/ui/MultiScreenApp.kt` - 2x2 grid container & lifecycle forwarding
- `app/src/main/java/com/example/multiscreen/ui/theme/Theme.kt` - Dark theme for immersive video viewing
- `app/src/main/java/com/example/multiscreen/MainActivity.kt` - Activity entry point, immersive full-screen flags
- `app/src/test/java/com/example/multiscreen/model/VideoCellModelTest.kt` - Unit tests for time formatting and state transitions

---

### Task 1: Android Project Scaffolding & Gradle Build Setup

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`

- [ ] **Step 1: Create Gradle root configuration files**

Write `settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "android_multi_screen"
include(":app")
```

Write `build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
}
```

Write `gradle/libs.versions.toml`:
```toml
[versions]
agp = "8.5.2"
kotlin = "2.0.0"
coreKtx = "1.13.1"
lifecycleRuntimeKtx = "2.8.6"
activityCompose = "1.9.2"
composeBom = "2024.09.02"
media3 = "1.4.1"
junit = "4.13.2"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
androidx-media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
androidx-media3-ui = { group = "androidx.media3", name = "media3-ui", version.ref = "media3" }
junit = { group = "junit", name = "junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

Write `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
```

Write `gradle/wrapper/gradle-wrapper.properties`:
```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.7-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 2: Create app module build file and Android resources**

Write `app/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.example.multiscreen"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.multiscreen"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    testImplementation(libs.junit)
}
```

Write `app/src/main/res/values/strings.xml`:
```xml
<resources>
    <string name="app_name">四宫格播放器</string>
    <string name="select_video">点击选择视频</string>
    <string name="replace_video">更换视频</string>
    <string name="close_video">关闭视频</string>
    <string name="mute">静音</string>
    <string name="unmute">开启声音</string>
    <string name="forward_10s">快进10秒</string>
    <string name="rewind_10s">快退10秒</string>
    <string name="codec_error_msg">设备硬件解码器不足或视频格式不受支持</string>
    <string name="retry">重试</string>
</resources>
```

Write `app/src/main/res/values/themes.xml`:
```xml
<resources>
    <style name="Theme.MultiScreen" parent="android:Theme.Material.NoActionBar">
        <item name="android:statusBarColor">#000000</item>
        <item name="android:navigationBarColor">#000000</item>
    </style>
</resources>
```

Write `app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="true"
        android:icon="@android:drawable/sym_def_app_icon"
        android:label="@string/app_name"
        android:roundIcon="@android:drawable/sym_def_app_icon"
        android:supportsRtl="true"
        android:theme="@style/Theme.MultiScreen">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden"
            android:theme="@style/Theme.MultiScreen">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

- [ ] **Step 3: Setup gradle wrapper scripts (gradlew & gradlew.bat)**

Download or generate standard `gradle-wrapper.jar` and wrapper scripts so Gradle can run directly via embedded Java.

- [ ] **Step 4: Commit build structure**

```bash
git add .
git commit -m "chore: setup project structure, gradle build and dependencies"
```

---

### Task 2: Models & Unit Tests (TDD for State & Formatting)

**Files:**
- Create: `app/src/main/java/com/example/multiscreen/model/VideoCellModel.kt`
- Test: `app/src/test/java/com/example/multiscreen/model/VideoCellModelTest.kt`

- [ ] **Step 1: Write failing unit tests for time formatting and cell states**

Write `app/src/test/java/com/example/multiscreen/model/VideoCellModelTest.kt`:
```kotlin
package com.example.multiscreen.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoCellModelTest {

    @Test
    fun testFormatTimeMs_zero() {
        assertEquals("00:00", formatTimeMs(0L))
    }

    @Test
    fun testFormatTimeMs_secondsAndMinutes() {
        assertEquals("01:05", formatTimeMs(65_000L))
        assertEquals("12:34", formatTimeMs((12 * 60 + 34) * 1000L))
    }

    @Test
    fun testFormatTimeMs_hours() {
        assertEquals("01:02:03", formatTimeMs(((1 * 3600) + (2 * 60) + 3) * 1000L))
    }

    @Test
    fun testFormatTimeMs_negative() {
        assertEquals("00:00", formatTimeMs(-5000L))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest`
Expected: FAIL with compilation error (formatTimeMs unresolved).

- [ ] **Step 3: Implement `VideoCellModel.kt`**

Write `app/src/main/java/com/example/multiscreen/model/VideoCellModel.kt`:
```kotlin
package com.example.multiscreen.model

import android.net.Uri
import java.util.Locale

enum class CellPlaybackState {
    EMPTY,
    LOADING,
    PLAYING,
    PAUSED,
    ERROR
}

data class VideoCellUiState(
    val id: Int,
    val uri: Uri? = null,
    val playbackState: CellPlaybackState = CellPlaybackState.EMPTY,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isMuted: Boolean = false,
    val isControlsVisible: Boolean = false,
    val errorMessage: String? = null
)

fun formatTimeMs(timeMs: Long): String {
    if (timeMs <= 0) return "00:00"
    val totalSeconds = timeMs / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600

    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
```

- [ ] **Step 4: Run unit tests to verify they pass**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/multiscreen/model/VideoCellModel.kt app/src/test/java/com/example/multiscreen/model/VideoCellModelTest.kt
git commit -m "feat: add VideoCellModel, playback states, and time formatting with unit tests"
```

---

### Task 3: ExoPlayer Factory (Low Memory LoadControl, Audio Mixing & Error Mapping)

**Files:**
- Create: `app/src/main/java/com/example/multiscreen/player/PlayerFactory.kt`
- Test: `app/src/test/java/com/example/multiscreen/player/PlayerFactoryTest.kt`

- [ ] **Step 1: Write test for PlayerFactory error message resolution**

Write `app/src/test/java/com/example/multiscreen/player/PlayerFactoryTest.kt`:
```kotlin
package com.example.multiscreen.player

import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerFactoryTest {

    @Test
    fun testDecoderErrorMessageRecognition() {
        val message = PlayerFactory.getFriendlyErrorMessage(
            isDecoderInitException = true,
            fallbackMessage = "Unknown error"
        )
        assertTrue(message.contains("硬件解码器") || message.contains("解码"))
    }

    @Test
    fun testGenericErrorMessageFallback() {
        val fallback = "File not found"
        val message = PlayerFactory.getFriendlyErrorMessage(
            isDecoderInitException = false,
            fallbackMessage = fallback
        )
        assertTrue(message.contains(fallback))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest`
Expected: FAIL (unresolved PlayerFactory).

- [ ] **Step 3: Implement `PlayerFactory.kt`**

Write `app/src/main/java/com/example/multiscreen/player/PlayerFactory.kt`:
```kotlin
package com.example.multiscreen.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer

@UnstableApi
object PlayerFactory {

    const val CODEC_EXHAUSTED_ERROR = "设备硬件解码器不足或视频格式不受支持"

    fun getFriendlyErrorMessage(isDecoderInitException: Boolean, fallbackMessage: String?): String {
        return if (isDecoderInitException) {
            CODEC_EXHAUSTED_ERROR
        } else {
            fallbackMessage ?: "播放出错，请重试"
        }
    }

    /**
     * Creates an ExoPlayer instance optimized for multi-video playback:
     * 1. Low-memory buffer control (2.5s~5s, <= 3MB buffer target per player) to prevent OOM
     * 2. Multitrack audio mixing: AudioAttributes set to MOVIE/MEDIA, handleAudioFocus = false
     */
    fun createPlayer(context: Context): ExoPlayer {
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

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(C.USAGE_MEDIA)
            .build()

        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ false)
            .setHandleAudioBecomingNoisy(false)
            .build()
    }
}
```

- [ ] **Step 4: Run unit test to verify it passes**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/multiscreen/player/PlayerFactory.kt app/src/test/java/com/example/multiscreen/player/PlayerFactoryTest.kt
git commit -m "feat: implement PlayerFactory with low-memory LoadControl and multi-track audio mixing"
```

---

### Task 4: VideoCellController (State Machine, Timer & Player Lifecycle)

**Files:**
- Create: `app/src/main/java/com/example/multiscreen/state/VideoCellController.kt`

- [ ] **Step 1: Implement `VideoCellController`**

Write `app/src/main/java/com/example/multiscreen/state/VideoCellController.kt`:
```kotlin
package com.example.multiscreen.state

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer
import com.example.multiscreen.model.CellPlaybackState
import com.example.multiscreen.model.VideoCellUiState
import com.example.multiscreen.player.PlayerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@UnstableApi
class VideoCellController(
    val cellId: Int,
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    var uiState by mutableStateOf(VideoCellUiState(id = cellId))
        private set

    var player: ExoPlayer? = null
        private set

    private var autoHideJob: Job? = null
    private var progressTrackingJob: Job? = null

    init {
        initPlayer()
    }

    private fun initPlayer() {
        if (player != null) return

        val exo = PlayerFactory.createPlayer(context)
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        uiState = uiState.copy(playbackState = CellPlaybackState.LOADING)
                    }
                    Player.STATE_READY -> {
                        val duration = exo.duration.coerceAtLeast(0L)
                        val isPlaying = exo.playWhenReady
                        uiState = uiState.copy(
                            playbackState = if (isPlaying) CellPlaybackState.PLAYING else CellPlaybackState.PAUSED,
                            durationMs = duration
                        )
                    }
                    Player.STATE_ENDED -> {
                        uiState = uiState.copy(
                            playbackState = CellPlaybackState.PAUSED,
                            currentPositionMs = exo.duration.coerceAtLeast(0L)
                        )
                    }
                    Player.STATE_IDLE -> {
                        if (uiState.playbackState != CellPlaybackState.ERROR && uiState.uri == null) {
                            uiState = uiState.copy(playbackState = CellPlaybackState.EMPTY)
                        }
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (uiState.playbackState != CellPlaybackState.ERROR && uiState.uri != null) {
                    uiState = uiState.copy(
                        playbackState = if (isPlaying) CellPlaybackState.PLAYING else CellPlaybackState.PAUSED
                    )
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val isDecoderIssue = error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                        error.cause is MediaCodecRenderer.DecoderInitializationException

                val msg = PlayerFactory.getFriendlyErrorMessage(isDecoderIssue, error.localizedMessage)
                // Stop to release decoder resources immediately
                exo.stop()
                uiState = uiState.copy(
                    playbackState = CellPlaybackState.ERROR,
                    errorMessage = msg,
                    isControlsVisible = false
                )
            }
        })

        player = exo
        startProgressTracking()
    }

    fun playUri(uri: Uri) {
        val p = player ?: return
        uiState = uiState.copy(
            uri = uri,
            playbackState = CellPlaybackState.LOADING,
            errorMessage = null,
            isControlsVisible = false
        )
        val mediaItem = MediaItem.fromUri(uri)
        p.setMediaItem(mediaItem)
        p.prepare()
        p.playWhenReady = true
    }

    fun togglePlayPause() {
        val p = player ?: return
        if (p.isPlaying) {
            p.pause()
        } else {
            p.play()
        }
        resetAutoHideTimer()
    }

    fun seekTo(positionMs: Long) {
        val p = player ?: return
        val target = positionMs.coerceIn(0L, uiState.durationMs.coerceAtLeast(0L))
        p.seekTo(target)
        uiState = uiState.copy(currentPositionMs = target)
        resetAutoHideTimer()
    }

    fun seekForward(deltaMs: Long = 10_000L) {
        seekTo(uiState.currentPositionMs + deltaMs)
    }

    fun seekRewind(deltaMs: Long = 10_000L) {
        seekTo(uiState.currentPositionMs - deltaMs)
    }

    fun toggleMute() {
        val p = player ?: return
        val newMuted = !uiState.isMuted
        p.volume = if (newMuted) 0f else 1f
        uiState = uiState.copy(isMuted = newMuted)
        resetAutoHideTimer()
    }

    fun onCellTapped() {
        if (uiState.playbackState == CellPlaybackState.EMPTY || uiState.playbackState == CellPlaybackState.ERROR) {
            return
        }
        val willShow = !uiState.isControlsVisible
        uiState = uiState.copy(isControlsVisible = willShow)
        if (willShow) {
            resetAutoHideTimer()
        } else {
            autoHideJob?.cancel()
        }
    }

    fun hideControlsImmediately() {
        autoHideJob?.cancel()
        uiState = uiState.copy(isControlsVisible = false)
    }

    fun resetAutoHideTimer() {
        autoHideJob?.cancel()
        autoHideJob = coroutineScope.launch {
            delay(3000L)
            if (isActive) {
                uiState = uiState.copy(isControlsVisible = false)
            }
        }
    }

    fun pauseAutoHideTimer() {
        autoHideJob?.cancel()
    }

    fun retry() {
        val uri = uiState.uri
        if (uri != null) {
            playUri(uri)
        }
    }

    fun close() {
        autoHideJob?.cancel()
        player?.stop()
        player?.clearMediaItems()
        uiState = VideoCellUiState(id = cellId)
    }

    fun release() {
        autoHideJob?.cancel()
        progressTrackingJob?.cancel()
        player?.release()
        player = null
    }

    private fun startProgressTracking() {
        progressTrackingJob?.cancel()
        progressTrackingJob = coroutineScope.launch {
            while (isActive) {
                val p = player
                if (p != null && p.isPlaying) {
                    uiState = uiState.copy(
                        currentPositionMs = p.currentPosition.coerceAtLeast(0L),
                        durationMs = p.duration.coerceAtLeast(0L)
                    )
                }
                delay(500L)
            }
        }
    }
}
```

- [ ] **Step 2: Commit VideoCellController**

```bash
git add app/src/main/java/com/example/multiscreen/state/VideoCellController.kt
git commit -m "feat: implement VideoCellController with state machine and 3s auto-hide timer"
```

---

### Task 5: UI Components (Empty Placeholder, Control Overlay & Error Overlay)

**Files:**
- Create: `app/src/main/java/com/example/multiscreen/ui/components/EmptyCellPlaceholder.kt`
- Create: `app/src/main/java/com/example/multiscreen/ui/components/CellControllerOverlay.kt`
- Create: `app/src/main/java/com/example/multiscreen/ui/components/ErrorCellOverlay.kt`

- [ ] **Step 1: Implement `EmptyCellPlaceholder.kt`**

Write `app/src/main/java/com/example/multiscreen/ui/components/EmptyCellPlaceholder.kt`:
```kotlin
package com.example.multiscreen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.multiscreen.R

@Composable
fun EmptyCellPlaceholder(
    onSelectVideoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF141414))
            .clickable(onClick = onSelectVideoClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF262626)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.select_video),
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.select_video),
                color = Color.LightGray,
                fontSize = 13.sp
            )
        }
    }
}
```

- [ ] **Step 2: Implement `CellControllerOverlay.kt`**

Write `app/src/main/java/com/example/multiscreen/ui/components/CellControllerOverlay.kt`:
```kotlin
package com.example.multiscreen.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.multiscreen.R
import com.example.multiscreen.model.CellPlaybackState
import com.example.multiscreen.model.VideoCellUiState
import com.example.multiscreen.model.formatTimeMs

@Composable
fun CellControllerOverlay(
    uiState: VideoCellUiState,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekForward: () -> Unit,
    onSeekRewind: () -> Unit,
    onToggleMute: () -> Unit,
    onReplaceVideo: () -> Unit,
    onClose: () -> Unit,
    onSliderDragStart: () -> Unit,
    onSliderDragEnd: (Long) -> Unit,
    onBackgroundTapped: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Show spinner if loading
    if (uiState.playbackState == CellPlaybackState.LOADING) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
        }
    }

    AnimatedVisibility(
        visible = uiState.isControlsVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        var isDragging by remember { mutableStateOf(false) }
        var dragProgress by remember { mutableFloatStateOf(0f) }

        val currentPos = if (isDragging) {
            (dragProgress * uiState.durationMs).toLong()
        } else {
            uiState.currentPositionMs
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x73000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBackgroundTapped
                )
        ) {
            // Top Bar: Replace, Mute, Close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onReplaceVideo) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = stringResource(R.string.replace_video),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onToggleMute) {
                    Icon(
                        imageVector = if (uiState.isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                        contentDescription = if (uiState.isMuted) stringResource(R.string.unmute) else stringResource(R.string.mute),
                        tint = if (uiState.isMuted) Color.Red else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close_video),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Center: Play / Pause
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onSeekRewind) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = stringResource(R.string.rewind_10s),
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.playbackState == CellPlaybackState.PLAYING) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }

                IconButton(onClick = onSeekForward) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = stringResource(R.string.forward_10s),
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Bottom Bar: Progress Slider & Time
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTimeMs(currentPos),
                        color = Color.White,
                        fontSize = 11.sp
                    )
                    Text(
                        text = formatTimeMs(uiState.durationMs),
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                }

                val sliderValue = if (uiState.durationMs > 0) {
                    if (isDragging) dragProgress else (uiState.currentPositionMs.toFloat() / uiState.durationMs).coerceIn(0f, 1f)
                } else 0f

                Slider(
                    value = sliderValue,
                    onValueChange = {
                        if (!isDragging) {
                            isDragging = true
                            onSliderDragStart()
                        }
                        dragProgress = it
                    },
                    onValueChangeFinished = {
                        isDragging = false
                        val targetMs = (dragProgress * uiState.durationMs).toLong()
                        onSliderDragEnd(targetMs)
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.Red,
                        inactiveTrackColor = Color(0x66FFFFFF)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
```

- [ ] **Step 3: Implement `ErrorCellOverlay.kt`**

Write `app/src/main/java/com/example/multiscreen/ui/components/ErrorCellOverlay.kt`:
```kotlin
package com.example.multiscreen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.multiscreen.R

@Composable
fun ErrorCellOverlay(
    errorMessage: String?,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xE61A1A1A))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Error",
                tint = Color(0xFFFFB74D),
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage ?: stringResource(R.string.codec_error_msg),
                color = Color.White,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
                ) {
                    Text(text = stringResource(R.string.retry), fontSize = 12.sp, color = Color.White)
                }
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedButton(
                    onClick = onClose
                ) {
                    Text(text = stringResource(R.string.close_video), fontSize = 12.sp, color = Color.LightGray)
                }
            }
        }
    }
}
```

- [ ] **Step 4: Commit UI components**

```bash
git add app/src/main/java/com/example/multiscreen/ui/components/
git commit -m "feat: add EmptyCellPlaceholder, CellControllerOverlay, and ErrorCellOverlay"
```

---

### Task 6: VideoCell Composable (PlayerView Integration & Gestures)

**Files:**
- Create: `app/src/main/java/com/example/multiscreen/ui/VideoCell.kt`

- [ ] **Step 1: Implement `VideoCell.kt`**

Write `app/src/main/java/com/example/multiscreen/ui/VideoCell.kt`:
```kotlin
package com.example.multiscreen.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.example.multiscreen.model.CellPlaybackState
import com.example.multiscreen.state.VideoCellController
import com.example.multiscreen.ui.components.CellControllerOverlay
import com.example.multiscreen.ui.components.EmptyCellPlaceholder
import com.example.multiscreen.ui.components.ErrorCellOverlay

@UnstableApi
@Composable
fun VideoCell(
    controller: VideoCellController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState = controller.uiState

    // File picker launcher using SAF (Storage Access Framework)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Non-fatal if persistable permission cannot be granted for this uri
            }
            controller.playUri(it)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            uiState.playbackState == CellPlaybackState.EMPTY -> {
                EmptyCellPlaceholder(
                    onSelectVideoClick = { filePickerLauncher.launch(arrayOf("video/*")) }
                )
            }
            uiState.playbackState == CellPlaybackState.ERROR -> {
                ErrorCellOverlay(
                    errorMessage = uiState.errorMessage,
                    onRetry = { controller.retry() },
                    onClose = { controller.close() }
                )
            }
            else -> {
                // Video Surface & Touch Gesture
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { controller.onCellTapped() }
                        )
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false // Hide default ExoPlayer controller
                                player = controller.player
                            }
                        },
                        update = { view ->
                            view.player = controller.player
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Floating Overlay
                    CellControllerOverlay(
                        uiState = uiState,
                        onTogglePlayPause = { controller.togglePlayPause() },
                        onSeekTo = { controller.seekTo(it) },
                        onSeekForward = { controller.seekForward() },
                        onSeekRewind = { controller.seekRewind() },
                        onToggleMute = { controller.toggleMute() },
                        onReplaceVideo = { filePickerLauncher.launch(arrayOf("video/*")) },
                        onClose = { controller.close() },
                        onSliderDragStart = { controller.pauseAutoHideTimer() },
                        onSliderDragEnd = { targetMs ->
                            controller.seekTo(targetMs)
                            controller.resetAutoHideTimer()
                        },
                        onBackgroundTapped = { controller.hideControlsImmediately() }
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Commit VideoCell**

```bash
git add app/src/main/java/com/example/multiscreen/ui/VideoCell.kt
git commit -m "feat: implement VideoCell with Surface PlayerView, SAF picker and overlay interactions"
```

---

### Task 7: 2x2 Grid Layout & Theme Integration (MultiScreenApp)

**Files:**
- Create: `app/src/main/java/com/example/multiscreen/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/example/multiscreen/ui/MultiScreenApp.kt`

- [ ] **Step 1: Implement Theme**

Write `app/src/main/java/com/example/multiscreen/ui/theme/Theme.kt`:
```kotlin
package com.example.multiscreen.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFE50914),
    secondary = Color(0xFFB81D24),
    background = Color(0xFF000000),
    surface = Color(0xFF141414)
)

@Composable
fun MultiScreenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
```

- [ ] **Step 2: Implement `MultiScreenApp.kt` with 2x2 Grid Layout & LifeCycle Management**

Write `app/src/main/java/com/example/multiscreen/ui/MultiScreenApp.kt`:
```kotlin
package com.example.multiscreen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import com.example.multiscreen.state.VideoCellController

@UnstableApi
@Composable
fun MultiScreenApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Initialize 4 independent cell controllers
    val controllers = remember {
        List(4) { id ->
            VideoCellController(cellId = id, context = context, coroutineScope = scope)
        }
    }

    // Forward Android lifecycle events to players
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    controllers.forEach { it.player?.pause() }
                }
                Lifecycle.Event.ON_STOP -> {
                    controllers.forEach { it.player?.pause() }
                }
                Lifecycle.Event.ON_DESTROY -> {
                    controllers.forEach { it.release() }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controllers.forEach { it.release() }
        }
    }

    val dividerColor = Color(0x33FFFFFF)

    // 2x2 Grid Layout
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Top Row (Cell 0 & Cell 1)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            VideoCell(
                controller = controllers[0],
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(dividerColor)
            )
            VideoCell(
                controller = controllers[1],
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }

        // Horizontal Divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(dividerColor)
        )

        // Bottom Row (Cell 2 & Cell 3)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            VideoCell(
                controller = controllers[2],
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(dividerColor)
            )
            VideoCell(
                controller = controllers[3],
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}
```

- [ ] **Step 3: Commit 2x2 layout and Theme**

```bash
git add app/src/main/java/com/example/multiscreen/ui/theme/Theme.kt app/src/main/java/com/example/multiscreen/ui/MultiScreenApp.kt
git commit -m "feat: implement 2x2 grid layout and lifecycle management in MultiScreenApp"
```

---

### Task 8: MainActivity & Immersive Full-screen Setup

**Files:**
- Create: `app/src/main/java/com/example/multiscreen/MainActivity.kt`

- [ ] **Step 1: Implement `MainActivity.kt` with Immersive Edge-to-Edge**

Write `app/src/main/java/com/example/multiscreen/MainActivity.kt`:
```kotlin
package com.example.multiscreen

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.media3.common.util.UnstableApi
import com.example.multiscreen.ui.MultiScreenApp
import com.example.multiscreen.ui.theme.MultiScreenTheme

@UnstableApi
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge immersive mode
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemUI()

        setContent {
            MultiScreenTheme {
                MultiScreenApp()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                    )
        }
    }
}
```

- [ ] **Step 2: Commit MainActivity**

```bash
git add app/src/main/java/com/example/multiscreen/MainActivity.kt
git commit -m "feat: implement MainActivity with edge-to-edge immersive full-screen"
```

---

### Task 9: Build & Verify (APK Assembly & Test Suite)

**Files:**
- None (Build & verification commands)

- [ ] **Step 1: Run Unit Tests**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest`
Expected: ALL TESTS PASS

- [ ] **Step 2: Assemble Debug APK**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL, `app/build/outputs/apk/debug/app-debug.apk` generated.

- [ ] **Step 3: Verification Check**

Verify that `app-debug.apk` exists and its size is reasonable (~15MB-30MB).
