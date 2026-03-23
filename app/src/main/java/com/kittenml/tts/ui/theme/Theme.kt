package com.kittenml.tts.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryAccent,
    secondary = SecondaryAccent,
    background = AppBackground,
    surface = CardBg,
    onPrimary = AppBackground,
    onSecondary = Surface,
    onBackground = Surface,
    onSurface = Surface
)

@Composable
fun KittenTTSTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
