package com.rayluxeo.aida.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rayluxeo.aida.core.design.MiraTheme

/**
 * Previews render on a white canvas by default, which makes an AMOLED-dark component library
 * impossible to judge. Every preview in this package sits on obsidian instead.
 */
@Composable
internal fun MiraPreviewSurface(content: @Composable ColumnScope.() -> Unit) {
    MiraTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(MiraTheme.spacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.md),
            content = content,
        )
    }
}
