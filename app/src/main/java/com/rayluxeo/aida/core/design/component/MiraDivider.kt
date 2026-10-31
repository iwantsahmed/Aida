package com.rayluxeo.aida.core.design.component

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.rayluxeo.aida.core.design.MiraTheme

/**
 * A true hairline. Material's default divider is a whole device pixel of grey, which reads as a
 * ruled line on obsidian; Mira's is the palette's barely-there border instead.
 */
@Composable
fun MiraDivider(
    modifier: Modifier = Modifier,
    color: Color = MiraTheme.colors.hairline,
) {
    HorizontalDivider(
        modifier = modifier,
        thickness = MiraTheme.spacing.hairline,
        color = color,
    )
}
