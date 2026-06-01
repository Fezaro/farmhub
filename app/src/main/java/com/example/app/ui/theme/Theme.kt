package com.example.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = GreenColor,
    onPrimary = GreenOnColor,
    primaryContainer = GreenLight,
    onPrimaryContainer = Color(0xFF062B06),
    secondary = GreenDark,
    onSecondary = Color.White,
    tertiary = OrangeColor,
    onTertiary = OrangeOnColor,
    tertiaryContainer = OrangeLight,
    onTertiaryContainer = OrangeOnColor,
    background = Color(0xFFFFFBFE),
    surface = Color.White,
    surfaceVariant = Color(0xFFF1F8EF),
    onBackground = Color.Black,
    onSurface = Color.Black,
)

private val DarkColors = darkColorScheme(
    primary = GreenColor,
    onPrimary = GreenOnColor,
    primaryContainer = GreenDark,
    onPrimaryContainer = Color(0xFFE9F9E9),
    secondary = GreenLight,
    onSecondary = Color.Black,
    tertiary = OrangeDark,
    onTertiary = Color(0xFFFFF8EB),
    tertiaryContainer = OrangeDark,
    onTertiaryContainer = Color(0xFFFFF8EB),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF1F2A1E),
    onBackground = Color.White,
    onSurface = Color.White,
)

@Composable
fun AppTheme(
    useDarkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = if (useDarkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}
