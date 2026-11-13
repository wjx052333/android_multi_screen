package com.example.multiscreen.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer

@OptIn(UnstableApi::class)
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
