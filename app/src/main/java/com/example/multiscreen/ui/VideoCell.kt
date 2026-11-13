package com.example.multiscreen.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
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

@OptIn(UnstableApi::class)
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
