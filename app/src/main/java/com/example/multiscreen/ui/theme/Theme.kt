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
