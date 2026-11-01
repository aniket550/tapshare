package com.aniket.tapshare.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LocalColors = staticCompositionLocalOf { LightColors }
private val LocalType = staticCompositionLocalOf { DefaultType }

object TapShareTheme {
    val colors: TapShareColors
        @Composable @ReadOnlyComposable get() = LocalColors.current

    val type: TapShareType
        @Composable @ReadOnlyComposable get() = LocalType.current
}

/** Follows the system light/dark setting, like iOS. */
@Composable
fun TapShareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    // Material3 is still used for a few primitives (text selection, ripples off), so give it matching colours.
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = Color.White,
            background = colors.groupedBackground,
            onBackground = colors.label,
            surface = colors.cell,
            onSurface = colors.label,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = Color.White,
            background = colors.groupedBackground,
            onBackground = colors.label,
            surface = colors.cell,
            onSurface = colors.label,
        )
    }
    CompositionLocalProvider(LocalColors provides colors, LocalType provides DefaultType) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
