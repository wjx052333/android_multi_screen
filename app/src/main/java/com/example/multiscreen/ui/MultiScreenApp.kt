package com.example.multiscreen.ui

import androidx.annotation.OptIn
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.util.UnstableApi
import com.example.multiscreen.state.VideoCellController

@OptIn(UnstableApi::class)
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
