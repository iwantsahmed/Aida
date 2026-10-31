package com.rayluxeo.aida.core.design.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rayluxeo.aida.core.design.MiraMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * The seven things Mira can be, one expression each.
 *
 * She is a face now — Abu asked for a character on 9 October 2026, reversing the old "a presence,
 * not a face" rule (see AGENTS.md §10/§11) — so each state is a look rather than a lighting trick.
 * [PROCESSING] and [EXECUTING] stay distinct because working out what he meant and writing the
 * record are different waits; [SUCCESS] and [ERROR] are momentary, so the end of a turn is a
 * change of face rather than a silent drop back to [IDLE].
 */
enum class MiraAvatarState {
    /** At rest: eyes open and calm, a slow breath, the occasional blink. */
    IDLE,

    /** The microphone is open and `amplitude` is live: eyes widen and she leans in, pulsing with his voice. */
    LISTENING,

    /** Abu has stopped; she is working it out. Her eyes narrow and glance away, thinking. */
    PROCESSING,

    /** The command is being carried out: a steady, focused look — something is being written. */
    EXECUTING,

    /** She is answering, bobbing gently with her cadence. A press here stops her. */
    SPEAKING,

    /** Momentary. The turn finished and something landed: a bright, happy squint. */
    SUCCESS,

    /** Momentary. The turn did not finish: small, lowered eyes and a dropped sprout. */
    ERROR,
}

/**
 * Mira herself: a rounded character in round glasses, drawn entirely in code so she needs no asset
 * and renders identically offline. Her body takes a different [MASCOT_PALETTE] colour each time she
 * appears — Abu asked for that — while her glasses and eyes stay ivory for contrast on the black.
 *
 * One [withFrameNanos] loop drives everything and republishes a never-equal clock, so the per-frame
 * work is a draw and not a recomposition. The expression smooths toward the current [state] rather
 * than snapping, so a state change reads as a change of face; [amplitude] in 0f..1f makes her pulse
 * while [MiraAvatarState.LISTENING].
 */
@Composable
fun MiraAvatar(
    state: MiraAvatarState,
    amplitude: Float,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    contentDescription: String? = null,
    size: Dp = DEFAULT_SIZE,
) {
    // A new body colour each time she enters composition — a different greeting every appearance.
    val bodyColor = remember { MASCOT_PALETTE.random() }
    val ivory = MaterialTheme.colorScheme.onBackground

    val paths = remember { MascotPaths() }
    val face = remember { Face() }
    val time = remember { floatArrayOf(0f) }
    val level = remember { floatArrayOf(0f) }
    val clock = remember { mutableStateOf(0f, neverEqualPolicy()) }
    val liveAmplitude = rememberUpdatedState(amplitude.coerceIn(0f, 1f))
    val currentState = rememberUpdatedState(state)

    LaunchedEffect(Unit) {
        var previousNanos = 0L
        while (true) {
            withFrameNanos { nowNanos ->
                val delta = if (previousNanos == 0L) 0f
                else ((nowNanos - previousNanos) / NANOS_PER_SECOND).coerceIn(0f, MAX_FRAME_SECONDS)
                previousNanos = nowNanos
                time[0] += delta

                val listening = currentState.value == MiraAvatarState.LISTENING
                val amp = if (listening) liveAmplitude.value else 0f
                val tau = if (amp > level[0]) MiraMotion.AMPLITUDE_ATTACK_SECONDS else MiraMotion.AMPLITUDE_RELEASE_SECONDS
                level[0] += (amp - level[0]) * (1f - exp(-delta / tau))

                face.approach(target(currentState.value, time[0]), delta)
                clock.value = time[0]
            }
        }
    }

    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(bounded = false, color = ivory),
            enabled = enabled,
            onClick = onClick,
        )
    } else {
        Modifier
    }

    Canvas(
        modifier = modifier
            .size(size)
            .then(clickModifier)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
    ) {
        clock.value // subscribe: redraw every frame
        drawMascot(paths, face, time[0], bodyColor, ivory, level[0], currentState.value)
    }
}

/** The body colours she cycles through — friendly, saturated enough to read on obsidian, never neon. */
val MASCOT_PALETTE = listOf(
    Color(0xFFA6C8E4), // ice
    Color(0xFFB7A7E0), // lavender
    Color(0xFF8FD3C3), // mint
    Color(0xFFE6B3A3), // clay
    Color(0xFFE8CE8F), // sand
    Color(0xFF9FB8E8), // periwinkle
    Color(0xFFC9A7D6), // orchid
)

/** The target look for a state, in the 200x200 design space. Smoothed toward, never snapped to. */
private class ExprTarget(
    val eyeH: Float,
    val eyeW: Float,
    val lookX: Float,
    val lookY: Float,
    val happy: Float,
    val bob: Float,
    val sprout: Float,
)

private fun target(state: MiraAvatarState, t: Float): ExprTarget = when (state) {
    MiraAvatarState.IDLE -> ExprTarget(30f, 14f, 0f, 0f, 0f, 0f, 1f)
    MiraAvatarState.LISTENING -> ExprTarget(37f, 16f, 0f, -2f, 0f, -2f, 1f)
    // Eyes glance side to side while she thinks.
    MiraAvatarState.PROCESSING -> ExprTarget(22f, 12f, sin(t * 3f) * 5f, -4f, 0f, 0f, 1f)
    MiraAvatarState.EXECUTING -> ExprTarget(25f, 13f, 0f, 0f, 0f, 0f, 1f)
    MiraAvatarState.SPEAKING -> ExprTarget(29f, 14f, 0f, 0f, 0f, 0f, 1f)
    MiraAvatarState.SUCCESS -> ExprTarget(30f, 18f, 0f, 0f, 1f, -3f, 1f)
    MiraAvatarState.ERROR -> ExprTarget(16f, 13f, 0f, 4f, 0f, 3f, 0.55f)
}

/** The live, smoothed face — the values actually drawn, chasing the current [ExprTarget]. */
private class Face {
    var eyeH = 30f
    var eyeW = 14f
    var lookX = 0f
    var lookY = 0f
    var happy = 0f
    var bob = 0f
    var sprout = 1f

    fun approach(to: ExprTarget, dt: Float) {
        val k = 1f - exp(-dt / SMOOTH_TAU)
        eyeH += (to.eyeH - eyeH) * k
        eyeW += (to.eyeW - eyeW) * k
        lookX += (to.lookX - lookX) * k
        lookY += (to.lookY - lookY) * k
        happy += (to.happy - happy) * k
        bob += (to.bob - bob) * k
        sprout += (to.sprout - sprout) * k
    }
}

/** The static silhouette, built once. The glasses and eyes are drawn directly, not as paths. */
private class MascotPaths {
    val body = Path().apply {
        moveTo(100f, 38f)
        cubicTo(150f, 38f, 168f, 74f, 168f, 108f)
        cubicTo(168f, 152f, 140f, 176f, 100f, 176f)
        cubicTo(58f, 176f, 34f, 150f, 34f, 110f)
        cubicTo(34f, 74f, 52f, 38f, 100f, 38f)
        close()
    }
    val sprout = Path().apply {
        moveTo(78f, 44f)
        cubicTo(70f, 30f, 72f, 16f, 84f, 14f)
        cubicTo(96f, 12f, 100f, 24f, 93f, 34f)
        cubicTo(90f, 39f, 85f, 42f, 78f, 44f)
        close()
    }
    val nub = Path().apply {
        moveTo(164f, 99f)
        cubicTo(180f, 99f, 182f, 117f, 166f, 117f)
        cubicTo(161f, 112f, 161f, 104f, 164f, 99f)
        close()
    }
    val bridge = Path().apply {
        moveTo(112f, 102f)
        cubicTo(107.5f, 92f, 107.5f, 94f, 107f, 104f)
    }
    val temple = Path().apply {
        moveTo(52f, 105f)
        cubicTo(38f, 104f, 34f, 106f, 30f, 109f)
    }
    val smileL = Path()
    val smileR = Path()
}

private const val LENS_LX = 82f
private const val LENS_LY = 104f
private const val LENS_LR = 30f
private const val LENS_RX = 133f
private const val LENS_RY = 106f
private const val LENS_RR = 28f
private const val GLASSES_STROKE = 7f

private fun DrawScope.drawMascot(
    paths: MascotPaths,
    face: Face,
    t: Float,
    body: Color,
    ivory: Color,
    amp: Float,
    state: MiraAvatarState,
) {
    val s = size.minDimension / 200f
    val breath = MiraMotion.breath(t / MiraMotion.BREATH_PERIOD_SECONDS)
    // Gentle idle bob, plus the per-expression offset; a touch quicker and deeper while speaking.
    val speaking = if (state == MiraAvatarState.SPEAKING) sin(t * 9f) * 1.4f else 0f
    val bobUnits = face.bob + sin(t / 0.65f) * 1.4f + speaking
    val swell = 1f + 0.012f * breath + amp * 0.05f
    val glassStroke = GLASSES_STROKE * s

    withTransform({
        scale(s, s, pivot = Offset.Zero)
        translate(0f, bobUnits)
        scale(swell, 2f - swell, pivot = Offset(100f, 110f))
    }) {
        drawPath(paths.nub, color = body)
        drawPath(paths.body, color = body)
        drawPath(paths.sprout, color = body, alpha = face.sprout.coerceIn(0f, 1f))

        // Glasses frame: temple, bridge, two rings.
        val frame = Stroke(width = GLASSES_STROKE, cap = StrokeCap.Round)
        drawPath(paths.temple, color = ivory, style = frame)

        // Eyes / smile crossfade by `happy`.
        val pillAlpha = (1f - face.happy).coerceIn(0f, 1f)
        val smileAlpha = face.happy.coerceIn(0f, 1f)
        val blink = blinkFactor(t, state)
        if (pillAlpha > 0.01f) {
            drawEye(LENS_LX, LENS_LY, face, blink, ivory, pillAlpha)
            drawEye(LENS_RX, LENS_RY, face, blink, ivory, pillAlpha)
        }
        if (smileAlpha > 0.01f) {
            drawSmile(paths.smileL, LENS_LX, LENS_LY, ivory, smileAlpha)
            drawSmile(paths.smileR, LENS_RX, LENS_RY, ivory, smileAlpha)
        }

        drawCircle(ivory, radius = LENS_LR, center = Offset(LENS_LX, LENS_LY), style = frame)
        drawCircle(ivory, radius = LENS_RR, center = Offset(LENS_RX, LENS_RY), style = frame)
        drawPath(paths.bridge, color = ivory, style = frame)
    }
}

private fun DrawScope.drawEye(cx: Float, cy: Float, face: Face, blink: Float, ivory: Color, alpha: Float) {
    val w = face.eyeW
    val h = (face.eyeH * blink).coerceAtLeast(2.5f)
    drawRoundRect(
        color = ivory,
        topLeft = Offset(cx - w / 2f + face.lookX, cy - h / 2f + face.lookY),
        size = Size(w, h),
        cornerRadius = CornerRadius(w / 2f, w / 2f),
        alpha = alpha,
    )
}

private fun DrawScope.drawSmile(path: Path, cx: Float, cy: Float, ivory: Color, alpha: Float) {
    path.reset()
    path.moveTo(cx - 9f, cy + 2f)
    path.quadraticTo(cx, cy - 8f, cx + 9f, cy + 2f)
    drawPath(path, color = ivory, alpha = alpha, style = Stroke(width = 6f, cap = StrokeCap.Round))
}

/** A quick blink roughly every 3.4s — but not during a happy squint, where the eyes are arcs. */
private fun blinkFactor(t: Float, state: MiraAvatarState): Float {
    if (state == MiraAvatarState.SUCCESS) return 1f
    val p = t % 3.4f
    if (p < 0.14f) return abs(cos(p / 0.14f * PI.toFloat()))
    return 1f
}

private const val NANOS_PER_SECOND = 1_000_000_000f
private const val MAX_FRAME_SECONDS = 0.05f
private const val SMOOTH_TAU = 0.11f
private val DEFAULT_SIZE = 96.dp

@Preview
@Composable
private fun MiraAvatarPreview() {
    MiraPreviewSurface {
        MiraAvatar(state = MiraAvatarState.IDLE, amplitude = 0f)
        MiraAvatar(state = MiraAvatarState.LISTENING, amplitude = 0.7f)
        MiraAvatar(state = MiraAvatarState.SUCCESS, amplitude = 0f)
    }
}
