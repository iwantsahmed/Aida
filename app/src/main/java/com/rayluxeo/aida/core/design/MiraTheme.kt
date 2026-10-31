package com.rayluxeo.aida.core.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalMiraSpacing = staticCompositionLocalOf { MiraSpacing() }
private val LocalMiraExtendedColors = staticCompositionLocalOf { MiraExtendedDark }

/**
 * Mira's design system. Material 3 supplies the mechanics; every visible token is ours.
 * The theme ignores the system light/dark setting by design — Mira is always obsidian.
 *
 * Colour, spacing, typography and shape arrive through Material's own composition locals and
 * through [MiraTheme.spacing] / [MiraTheme.colors]. Motion is the exception: [MiraMotion] is a
 * plain object of constants and easings, because an animation spec never varies by where it is
 * read from and a composition local would only make it harder to use outside composition.
 */
@Composable
fun MiraTheme(content: @Composable () -> Unit) {
    // Built from a Context-backed typeface (see MiraFont), so the type scale is assembled here
    // rather than held as a top-level constant.
    val fontFamily = rememberMiraFontFamily()
    val typography = remember(fontFamily) { miraTypography(fontFamily) }
    CompositionLocalProvider(
        LocalMiraSpacing provides MiraSpacing(),
        LocalMiraExtendedColors provides MiraExtendedDark,
    ) {
        MaterialTheme(
            colorScheme = MiraColorScheme,
            typography = typography,
            shapes = MiraShapes,
            content = content,
        )
    }
}

object MiraTheme {
    val spacing: MiraSpacing
        @Composable @ReadOnlyComposable get() = LocalMiraSpacing.current

    val colors: MiraExtendedColors
        @Composable @ReadOnlyComposable get() = LocalMiraExtendedColors.current
}
