package com.rayluxeo.aida.core.design.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.rayluxeo.aida.core.design.MiraMotion
import com.rayluxeo.aida.core.design.MiraTheme

/** How far the [MiraStatus.ACTIVE] dot dims at the bottom of its breath. */
private const val PULSE_MIN_ALPHA = 0.35f

/** What a small piece of Mira is currently doing, reduced to the five cases a dot can carry. */
enum class MiraStatus {
    /** Present but doing nothing: "On-device", "Ready". */
    IDLE,

    /** Working right now: "Listening", "Thinking". The only state that moves. */
    ACTIVE,

    /** Finished, and it went well: "Saved", "Synced with the calendar". */
    SUCCESS,

    /** Degraded but usable: "Offline", "Gemini unavailable". */
    WARNING,

    /** Stopped: "Microphone blocked", "Vault locked out". */
    ERROR,
}

/**
 * A dot and a label, for the quiet line of state Mira keeps at the edge of a screen.
 *
 * The [label] always carries the meaning on its own. The dot is a second, faster read for
 * someone who already knows the screen — never the only one, which is why WARNING and ACTIVE
 * may share champagne: the words "Offline" and "Listening" are what distinguish them, and the
 * pulse only confirms it. Colour-blindness, a dimmed AMOLED panel and a glance from across the
 * desk all still work.
 *
 * Only ACTIVE moves. A status line that animates while nothing is happening reads as a fault.
 */
@Composable
fun MiraStatusIndicator(
    status: MiraStatus,
    label: String,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    val dotColor = when (status) {
        MiraStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
        MiraStatus.ACTIVE -> MaterialTheme.colorScheme.primary
        MiraStatus.SUCCESS -> MiraTheme.colors.positive
        MiraStatus.WARNING -> MaterialTheme.colorScheme.primary
        MiraStatus.ERROR -> MiraTheme.colors.danger
    }
    val pulse = if (status == MiraStatus.ACTIVE) rememberStatusPulse() else null

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiraStatusDot(color = dotColor, pulseAlpha = pulse, size = spacing.xs)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * [pulseAlpha] is passed as a [State] rather than a float so that a pulsing dot invalidates only
 * the draw, instead of recomposing the label sixty times a second beside it. Null means still.
 */
@Composable
private fun MiraStatusDot(
    color: Color,
    pulseAlpha: State<Float>?,
    size: Dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .then(
                if (pulseAlpha != null) {
                    Modifier.graphicsLayer { alpha = pulseAlpha.value }
                } else {
                    Modifier
                },
            )
            .clip(CircleShape)
            .background(color),
    )
}

/**
 * The breath runs on [MiraMotion.AMBIENT_MS] — the same slow clock as the voice orb's idle, so
 * two things that are both "alive and waiting" are alive at the same rate.
 */
@Composable
private fun rememberStatusPulse(): State<Float> {
    val transition = rememberInfiniteTransition(label = "MiraStatusPulse")
    return transition.animateFloat(
        initialValue = PULSE_MIN_ALPHA,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = MiraMotion.AMBIENT_MS, easing = MiraMotion.Linear),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "MiraStatusPulseAlpha",
    )
}

@Preview
@Composable
private fun MiraStatusIndicatorPreview() {
    MiraPreviewSurface {
        MiraStatusIndicator(status = MiraStatus.ACTIVE, label = "Listening")
        MiraStatusIndicator(status = MiraStatus.IDLE, label = "On-device")
        MiraStatusIndicator(status = MiraStatus.SUCCESS, label = "Saved")
        MiraStatusIndicator(status = MiraStatus.WARNING, label = "Offline")
        MiraStatusIndicator(status = MiraStatus.ERROR, label = "Microphone blocked")
    }
}
