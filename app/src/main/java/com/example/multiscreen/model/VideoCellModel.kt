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
