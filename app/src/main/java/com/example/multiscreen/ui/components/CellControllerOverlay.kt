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
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
                        imageVector = if (uiState.isMuted) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
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

            // Center: Rewind, Play/Pause, Forward
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
