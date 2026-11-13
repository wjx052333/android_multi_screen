package com.example.multiscreen.state

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
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

@OptIn(UnstableApi::class)
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
