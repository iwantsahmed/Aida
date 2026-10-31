package com.rayluxeo.aida.core.design.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rayluxeo.aida.core.design.MiraMotion
import com.rayluxeo.aida.core.design.MiraTheme
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/** Strands in the ribbon. Dense enough to read as woven silk; the strokes are solid (not gradient)
 *  so even this many stay cheap. */
private const val DEFAULT_STRAND_COUNT = 40
private const val MINIMUM_STRAND_COUNT = 12

/** Samples along each strand. A strand is a smooth curve, so it needs points, not bars — but the
 *  envelope is gentle, so it does not need many. */
private const val SAMPLES = 60

/** One broad swell centred in the frame — the band is fullest in the middle and tapers to the edges. */
private const val CENTRE_WIDTH = 0.3f

/** Fractions of the band's height: how wide the strands fan, and how tall each undulates. */
private const val SPREAD_FRACTION = 0.62f
private const val WAVE_FRACTION = 0.09f

/** The ribbon uses all but a hair of the box height, so its tallest crest never touches the edge. */
private const val RIB_HEIGHT_FRACTION = 0.95f

/** The vertical excursion at rest, how much the idle breath swells it, and what a live voice adds. */
private const val IDLE_SCALE = 0.42f
private const val BREATH_DEPTH = 0.24f
private const val LIVE_SCALE = 0.62f

/** The wave's centreline sits at the middle of its box; the band is symmetric around it. */
private const val CENTRELINE = 0.5f

/** Undulation across the width; each strand detunes its own frequency slightly to break the weave. */
private const val CROSS_FREQUENCY = 2.4f
private const val STRAND_DETUNE = 0.12f

/** A fine second ripple that only rides the strands while a voice is actually driving her. */
private const val RIPPLE_FRACTION = 0.06f
private const val RIPPLE_CROSS_FREQUENCY = 3.4f

/** Flow speed: a slow drift at rest, quicker when a voice drives it. */
private const val IDLE_FLOW = 0.16f
private const val ACTIVE_FLOW = 1.9f

/**
 * How lively the ribbon is whenever she is active — listening or speaking — even with no live
 * amplitude behind it. Home reads the brief aloud with no microphone to sample, so without a floor
 * the band would sit dead-still while Mira talks; on the Mira screen the real amplitude rides on
 * top of this. It is the one number that lets one instrument answer both a voice and a silence.
 */
private const val ACTIVE_FLOOR = 0.6f

/** A strand is a filament, not a bar. */
private val STRAND_WIDTH = 1.0.dp

private const val NANOS_PER_SECOND = 1_000_000_000f

/** A dropped frame must not become a jump; clamp the step at three frames' worth. */
private const val MAX_FRAME_SECONDS = 0.05f

private const val STRAND_ALPHA = 0.17f
private const val EDGE_FADE = 0.14f
private const val MIN_VISIBLE_ALPHA = 0.012f
private const val TWO_PI = 6.2831855f

/**
 * Mira's signature element: a flowing ribbon of fine ice-light, fullest at the centre and tapering
 * to nothing at either edge. It is fed by a live [amplitude] in 0f..1f while she is [active], and
 * breathes slowly when she is not.
 *
 * The ribbon is many thin strands, not bars. Each strand is a smooth curve whose height follows one
 * broad centred envelope; the strands fan wider where the swell is tallest, so the crest is where
 * the most strands overlap and the light gathers there on its own rather than being painted
 * brighter. Two details keep it from looking mechanical: every strand carries a fixed *hashed* phase
 * and a slight frequency detune, so the strands never march into interference banding; and the whole
 * band breathes on [MiraMotion.breath] — a quick rise, a held top, a slow release — rather than
 * merely scrolling.
 *
 * The colour is a single constant — the ice accent, always — because presence comes from motion and
 * scale, not from a hue change; a voice makes the band grow and quicken, it does not recolour it.
 * The [amplitude] follows the same fast-attack / slow-release law the whole voice layer uses, so the
 * ribbon answers one microphone as one instrument.
 *
 * The envelope is size-invariant, so its two `exp()` per sample are computed ONCE (not per strand),
 * and the strand [Path]s are kept and rewound each frame. The clock advances in one
 * [withFrameNanos] loop and republishes under a never-equal policy, so recomposition stays out of
 * the per-frame path — only the draw is invalidated.
 *
 * The caller sets the height through [modifier]; the ribbon fills the width it is given.
 */
@Composable
fun MiraWaveform(
    amplitude: Float,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    strandCount: Int = DEFAULT_STRAND_COUNT,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val strands = strandCount.coerceAtLeast(MINIMUM_STRAND_COUNT)
    val paths = remember(strands) { List(strands) { Path() } }

    // Per-strand constants: a hashed phase and a small frequency detune scatter the strands so the
    // weave never bands, plus the fixed layer and its alpha. Computed once, so the draw allocates
    // nothing and does no transcendental work per strand beyond the one sin() the curve needs.
    val strandData = remember(strands) {
        val phase = FloatArray(strands)
        val detune = FloatArray(strands)
        val layer = FloatArray(strands)
        val layerAlpha = FloatArray(strands)
        for (s in 0 until strands) {
            phase[s] = frac(sin(s * 12.9898f) * 43758.5453f) * TWO_PI
            detune[s] = 1f + (frac(sin(s * 78.233f) * 24634.6345f) - 0.5f) * STRAND_DETUNE
            val l = s.toFloat() / (strands - 1) - 0.5f
            layer[s] = l
            layerAlpha[s] = STRAND_ALPHA * (0.35f + 0.65f * (1f - abs(l * 2f)))
        }
        StrandData(phase, detune, layer, layerAlpha)
    }

    // The envelope depends only on the sample index, so both gaussians are evaluated once here, and
    // both cross-arguments — the main weave and the finer ripple — are precomputed alongside it.
    val env = remember { FloatArray(SAMPLES + 1) { i -> envelope(i.toFloat() / SAMPLES) } }
    val arg = remember {
        FloatArray(SAMPLES + 1) { i -> (i.toFloat() / SAMPLES) * CROSS_FREQUENCY * TWO_PI }
    }
    val argRipple = remember {
        FloatArray(SAMPLES + 1) { i -> (i.toFloat() / SAMPLES) * RIPPLE_CROSS_FREQUENCY * TWO_PI }
    }

    val time = remember { floatArrayOf(0f) }
    val flow = remember { floatArrayOf(0f) }
    val level = remember { floatArrayOf(0f) }
    val clock = remember { mutableStateOf(0f, neverEqualPolicy()) }
    val liveAmplitude = rememberUpdatedState(amplitude.coerceIn(0f, 1f))
    val listening = rememberUpdatedState(active)

    // The obsidian the ribbon sits on, used to fade its ends without a per-strand gradient.
    val background = MaterialTheme.colorScheme.background

    LaunchedEffect(strands) {
        var previousNanos = 0L
        while (true) {
            withFrameNanos { nowNanos ->
                val delta = if (previousNanos == 0L) {
                    0f
                } else {
                    ((nowNanos - previousNanos) / NANOS_PER_SECOND).coerceIn(0f, MAX_FRAME_SECONDS)
                }
                previousNanos = nowNanos

                // Active means at least ACTIVE_FLOOR of life, with any real amplitude riding on top.
                val target = if (listening.value) max(liveAmplitude.value, ACTIVE_FLOOR) else 0f
                val tau = if (target > level[0]) MiraMotion.AMPLITUDE_ATTACK_SECONDS else MiraMotion.AMPLITUDE_RELEASE_SECONDS
                level[0] += (target - level[0]) * (1f - exp(-delta / tau))

                time[0] += delta
                flow[0] += delta * (IDLE_FLOW + level[0] * ACTIVE_FLOW)
                clock.value = flow[0]
            }
        }
    }

    Canvas(modifier = modifier.fillMaxWidth()) {
        clock.value // subscribe: redraw every frame, no recomposition
        val drift = flow[0]
        val amp = level[0]
        val listeningNow = listening.value
        val width = size.width
        val height = size.height
        val centreY = height * CENTRELINE
        val ribHeight = height * RIB_HEIGHT_FRACTION
        val breath = MiraMotion.breath(time[0] / MiraMotion.BREATH_PERIOD_SECONDS)
        val scale = IDLE_SCALE * (1f + BREATH_DEPTH * breath) + amp * LIVE_SCALE
        val strokePx = STRAND_WIDTH.toPx()
        val ripple = RIPPLE_FRACTION * amp

        // Each strand is a SOLID stroke, not a gradient one — gradient-shaded stroking is what makes
        // dozens of paths crawl on a software rasteriser. The ends are faded afterwards by two small
        // gradient washes in the background colour, far cheaper than shading every strand.
        for (s in 0 until strands) {
            val alpha = strandData.layerAlpha[s]
            if (alpha < MIN_VISIBLE_ALPHA) continue
            val layer = strandData.layer[s]
            val phase = strandData.phase[s]
            val detune = strandData.detune[s]
            val path = paths[s]
            path.reset()
            for (i in 0..SAMPLES) {
                val e = env[i]
                var wave = WAVE_FRACTION * e * sin(arg[i] * detune + phase + drift)
                if (listeningNow && ripple > 0f) {
                    wave += ripple * e * sin(argRipple[i] + phase * 2f + drift * 1.6f)
                }
                val spread = layer * SPREAD_FRACTION * (0.25f + 0.75f * e)
                val y = centreY - (wave + spread) * ribHeight * scale
                val x = (i.toFloat() / SAMPLES) * width
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = color,
                alpha = alpha.coerceIn(0f, 1f),
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }

        val fadeW = width * EDGE_FADE
        drawRect(
            brush = Brush.horizontalGradient(0f to background, 1f to background.copy(alpha = 0f), startX = 0f, endX = fadeW),
            size = Size(fadeW, height),
        )
        drawRect(
            brush = Brush.horizontalGradient(0f to background.copy(alpha = 0f), 1f to background, startX = width - fadeW, endX = width),
            topLeft = Offset(width - fadeW, 0f),
            size = Size(fadeW, height),
        )
    }
}

/** Per-strand constants, precomputed so the hot per-frame path stays allocation- and exp-free. */
private class StrandData(
    val phase: FloatArray,
    val detune: FloatArray,
    val layer: FloatArray,
    val layerAlpha: FloatArray,
)

/** The centred swell at [fx] in 0f..1f: near 1 at the middle, ~0 at either edge. */
private fun envelope(fx: Float): Float = gaussian(fx, 0.5f, CENTRE_WIDTH).coerceIn(0f, 1f)

private fun gaussian(x: Float, centre: Float, width: Float): Float {
    val d = (x - centre) / width
    return exp(-d * d)
}

/** The fractional part — the cheap deterministic hash used to scatter strand phase and frequency. */
private fun frac(x: Float): Float = x - floor(x)

@Preview
@Composable
private fun MiraWaveformPreview() {
    MiraPreviewSurface {
        MiraWaveform(
            amplitude = 0.7f,
            modifier = Modifier.fillMaxWidth().height(MiraTheme.spacing.xxl * 3),
        )
        MiraWaveform(
            amplitude = 0f,
            active = false,
            modifier = Modifier.fillMaxWidth().height(MiraTheme.spacing.xxl * 3),
        )
    }
}
