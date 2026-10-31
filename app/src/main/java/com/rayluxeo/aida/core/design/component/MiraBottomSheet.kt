package com.rayluxeo.aida.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import com.rayluxeo.aida.core.design.MiraTheme

/**
 * A modal sheet in Mira's own surfaces: carbon, square-shouldered at the bottom, with a handle
 * that is a hairline bar rather than Material's grey pill.
 *
 * Window insets are switched off on the sheet itself and applied to the content instead, so the
 * sheet's background always runs to the bottom of the screen while the content clears the
 * navigation bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiraBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = MiraTheme.spacing
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge.copy(
            bottomStart = CornerSize(0),
            bottomEnd = CornerSize(0),
        ),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = MiraTheme.colors.scrim,
        dragHandle = { MiraDragHandle() },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(
                    start = spacing.screenHorizontal,
                    end = spacing.screenHorizontal,
                    bottom = spacing.lg,
                ),
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(spacing.md))
            }
            content()
        }
    }
}

/** The grab affordance: one hairline bar, centred, with room above and below it. */
@Composable
private fun MiraDragHandle() {
    val spacing = MiraTheme.spacing
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = spacing.xl, height = spacing.xxs)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MiraTheme.colors.hairlineStrong),
        )
    }
}

@Preview
@Composable
private fun MiraDragHandlePreview() {
    MiraPreviewSurface {
        MiraDragHandle()
    }
}
