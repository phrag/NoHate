package com.nohate.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF0B0E12),
    primaryContainer = Color(0xFF1E2A44),
    onPrimaryContainer = Color(0xFFDCE8FF),
    secondary = Color(0xFFBB86FC),
    onSecondary = Color(0xFF120B17),
    background = Color(0xFF0B0E12),
    onBackground = Color(0xFFE6E8ED),
    surface = Color(0xFF12161C),
    onSurface = Color(0xFFE6E8ED),
    surfaceVariant = Color(0xFF1A212B),
    onSurfaceVariant = Color(0xFFBEC6D0),
    error = Color(0xFFFFB4A9),
    onError = Color(0xFF370000)
)

@Composable
fun NoHateTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else DarkColors
    MaterialTheme(
        colorScheme = colors,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
