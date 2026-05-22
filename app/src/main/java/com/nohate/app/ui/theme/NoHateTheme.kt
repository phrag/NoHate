package com.nohate.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Static fallback — deep teal primary, coral secondary
private val LightColors = lightColorScheme(
    primary = Color(0xFF00696C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9CF0F2),
    onPrimaryContainer = Color(0xFF002021),
    secondary = Color(0xFFB52A2A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDAD6),
    onSecondaryContainer = Color(0xFF3B0909),
    tertiary = Color(0xFF4A5C92),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDDE1FF),
    onTertiaryContainer = Color(0xFF01174B),
    background = Color(0xFFFAFDFD),
    onBackground = Color(0xFF191C1C),
    surface = Color(0xFFFAFDFD),
    onSurface = Color(0xFF191C1C),
    surfaceVariant = Color(0xFFDAE4E4),
    onSurfaceVariant = Color(0xFF3F4949),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF80D4D6),
    onPrimary = Color(0xFF003738),
    primaryContainer = Color(0xFF004F51),
    onPrimaryContainer = Color(0xFF9CF0F2),
    secondary = Color(0xFFFFB3AC),
    onSecondary = Color(0xFF680F0F),
    secondaryContainer = Color(0xFF911B1B),
    onSecondaryContainer = Color(0xFFFFDAD6),
    tertiary = Color(0xFFB9C3FF),
    onTertiary = Color(0xFF132961),
    tertiaryContainer = Color(0xFF2F4478),
    onTertiaryContainer = Color(0xFFDDE1FF),
    background = Color(0xFF191C1C),
    onBackground = Color(0xFFE0E3E3),
    surface = Color(0xFF191C1C),
    onSurface = Color(0xFFE0E3E3),
    surfaceVariant = Color(0xFF3F4949),
    onSurfaceVariant = Color(0xFFBEC8C8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun NoHateTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colors,
        typography = NoHateTypography,
        shapes = NoHateShapes,
        content = content,
    )
}
