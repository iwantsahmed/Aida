package com.rayluxeo.aida.core.design.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.tooling.preview.Preview
import com.rayluxeo.aida.core.design.MiraTheme

/**
 * Mira waits quietly. One thin champagne arc on a hairline track — no pulsing, no full-width
 * bars, nothing that suggests the app is struggling.
 */
@Composable
fun MiraLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val spacing = MiraTheme.spacing
    CircularProgressIndicator(
        modifier = modifier.size(spacing.lg),
        color = color,
        strokeWidth = spacing.xxs / 2,
        trackColor = MiraTheme.colors.hairline,
        strokeCap = StrokeCap.Round,
    )
}

@Preview
@Composable
private fun MiraLoadingIndicatorPreview() {
    MiraPreviewSurface {
        MiraLoadingIndicator()
    }
}
