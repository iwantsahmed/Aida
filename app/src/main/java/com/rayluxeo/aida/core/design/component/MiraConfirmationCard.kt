package com.rayluxeo.aida.core.design.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import com.rayluxeo.aida.core.design.MiraMotion
import com.rayluxeo.aida.core.design.MiraTheme

/**
 * Mira understood the command and wants a yes before she acts on it.
 *
 * Inline rather than modal, because a confirmation is her half of a conversation that is already
 * on screen — a dialog would take the transcript away and make an ordinary "shall I?" feel like
 * a warning. Nothing here is red for the same reason: this is the pipeline's confirmation gate
 * working correctly, not a failure.
 *
 * It rises into place rather than appearing, so Abu's eye finds the new card without the list
 * underneath it seeming to jump.
 */
@Composable
fun MiraConfirmationCard(
    question: String,
    confirmText: String,
    onConfirm: () -> Unit,
    cancelText: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    val spacing = MiraTheme.spacing
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val entry = animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = MiraMotion.enter(),
        label = "MiraConfirmationCardEntry",
    )

    MiraCard(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = entry.value
                translationY = spacing.sm.toPx() * (1f - entry.value)
            },
    ) {
        Text(
            text = question,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (detail != null) {
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(spacing.lg))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            MiraButton(
                text = cancelText,
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                style = MiraButtonStyle.SECONDARY,
            )
            MiraButton(
                text = confirmText,
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                style = MiraButtonStyle.PRIMARY,
            )
        }
    }
}

@Preview
@Composable
private fun MiraConfirmationCardPreview() {
    MiraPreviewSurface {
        MiraConfirmationCard(
            question = "Set a reminder for tomorrow at 10:00?",
            confirmText = "Yes",
            onConfirm = {},
            cancelText = "No",
            onCancel = {},
            detail = "Call the landlord about the lease",
        )
        MiraConfirmationCard(
            question = "Record 4,500 BDT as income?",
            confirmText = "Record it",
            onConfirm = {},
            cancelText = "Cancel",
            onCancel = {},
        )
    }
}
