package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SanaDarkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFF97F0FF),

    secondary = VioletSecondary,
    onSecondary = Color(0xFF280056),
    secondaryContainer = Color(0xFF3F1976),
    onSecondaryContainer = Color(0xFFE9DDFF),

    tertiary = PurpleAccent,
    onTertiary = Color(0xFF4C0068),
    tertiaryContainer = Color(0xFF671485),
    onTertiaryContainer = Color(0xFFF6D9FF),

    background = ObsidianBg,
    onBackground = TextPrimary,

    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = TextSecondary,

    error = CrimsonError,
    onError = Color.White,
    outline = ObsidianBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Use our signature SANA dark luxury theme consistently
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SanaDarkColorScheme,
        typography = Typography,
        content = content
    )
}
