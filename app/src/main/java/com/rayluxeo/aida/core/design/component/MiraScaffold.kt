package com.rayluxeo.aida.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rayluxeo.aida.core.design.MiraTheme

/**
 * The height of the shell's floating nav bar, handed down so every screen's content clears it.
 *
 * The nav bar floats over the content (so the content scrolls edge to edge BEHIND the translucent
 * pill rather than being cut off above a reserved bar), which means the screen, not the nav bar,
 * has to leave room for it at the foot of its scroll. The shell measures the bar and provides this;
 * [MiraScaffold] folds it into the content's bottom padding. It is `0.dp` where there is no bar —
 * onboarding, and any screen composed outside the shell.
 */
val LocalMiraBottomInset = compositionLocalOf { 0.dp }

/**
 * The frame every Mira screen sits in: obsidian edge to edge, and one set of content padding
 * that already folds in the window insets and the screen gutter, so no screen works out its own.
 *
 * [topBar] and [bottomBar] are nullable rather than empty-lambda defaults because an absent bar
 * must emit nothing at all — that is what lets the scaffold hand the insets to the content
 * instead of to a zero-height bar.
 *
 * [background] is an optional full-bleed layer drawn edge to edge BEHIND the content, under the
 * status and navigation bars — where [MiraAmbientBackground] lives on onboarding, which stands
 * outside the shell. When it is given, the scaffold's own container goes transparent so the layer
 * shows through; obsidian is laid down first regardless, so a partly transparent layer never
 * flashes white.
 *
 * [transparent] makes the scaffold's container see-through WITHOUT drawing anything of its own — for
 * a screen that sits over a layer its parent already drew, such as Home and the assistant screen
 * over the shell's aurora (so the glow shows behind the translucent nav bar, which a per-screen
 * [background] confined above the bar could not do). The caller is then responsible for what shows
 * through; a normal screen leaves both off and gets an opaque obsidian container as before.
 */
@Composable
fun MiraScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable (() -> Unit)? = null,
    bottomBar: @Composable (() -> Unit)? = null,
    horizontalPadding: Dp = MiraTheme.spacing.screenHorizontal,
    background: (@Composable () -> Unit)? = null,
    transparent: Boolean = false,
    content: @Composable (PaddingValues) -> Unit,
) {
    val seeThrough = transparent || background != null
    Box(modifier = modifier) {
        if (background != null) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                background()
            }
        }
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = { topBar?.invoke() },
            bottomBar = { bottomBar?.invoke() },
            containerColor = if (seeThrough) Color.Transparent else MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            contentWindowInsets = WindowInsets.safeDrawing,
        ) { insets ->
            val direction = LocalLayoutDirection.current
            // The content clears whichever is taller: the system's own bottom inset (gesture bar) or
            // the shell's floating nav bar, whose height already folds the gesture bar in. One or the
            // other, never both stacked.
            val bottom = maxOf(insets.calculateBottomPadding(), LocalMiraBottomInset.current)
            content(
                PaddingValues(
                    start = insets.calculateStartPadding(direction) + horizontalPadding,
                    top = insets.calculateTopPadding(),
                    end = insets.calculateEndPadding(direction) + horizontalPadding,
                    bottom = bottom,
                ),
            )
        }
    }
}
