// [Relocate] [Theme.kt] - Material3 Theme
// Supports dark/light mode with custom color scheme.

package com.relocate.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Amber,
    onPrimary = Color.Black,
    primaryContainer = AmberDim,
    secondary = Blue,
    background = DarkBackground,
    surface = DarkCard,
    surfaceVariant = DarkCard2,
    onBackground = TextLight,
    onSurface = TextLight,
    onSurfaceVariant = MutedLight,
    outline = DarkBorder,
    error = Red,
    tertiary = Green
)

private val LightColorScheme = lightColorScheme(
    primary = Amber,
    onPrimary = Color.Black,
    primaryContainer = AmberDim,
    secondary = Blue,
    background = LightBackground,
    surface = LightCard,
    surfaceVariant = LightCard2,
    onBackground = TextDark,
    onSurface = TextDark,
    onSurfaceVariant = MutedDark,
    outline = LightBorder,
    error = Red,
    tertiary = Green
)

@Composable
fun RelocateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = RelocateTypography,
        content = content
    )
}
