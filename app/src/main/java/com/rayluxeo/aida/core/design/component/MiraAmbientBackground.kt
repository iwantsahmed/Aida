package com.rayluxeo.aida.core.design.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rayluxeo.aida.core.design.MiraMotion
import com.rayluxeo.aida.core.design.MiraTheme
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * The aurora behind Mira's presence screens — her answer to the Gemini app's living backdrop.
 *
 * Abu asked for this on 16 October 2026, which reverses the old §10 "no purple AI gradients, no
 * neon" line for exactly this one surface (and no other — every control, state and text stays
 * ice-blue). It is DRAWN, never a video or an image: a photo or a Lottie would weigh megabytes,
 * could not re-tint with the palette, and §3 forbids a fetched asset outright. So two slow violet
 * glows breathe low behind the content and a halftone field of fine dots gathers where their light
 * pools — all a few hundred bytes of maths, identical on a plane.
 *
 * It answers the same microphone the rest of the voice layer does: [amplitude] in 0f..1f lifts the
 * glow and brightens the dots, and [active] (listening or speaking) holds a floor of life even when
 * there is no live level to sample — so Home, which reads the brief aloud with no microphone, still
 * swells while she talks. At rest it simply breathes on [MiraMotion.breath].
 *
 * The hot core sits low (behind the composer / the greeting's feet), so the greeting and the mark
 * above it keep their contrast and the colour never fights the words — the same discipline
 * [MiraMountainHero] uses. The clock advances in one [withFrameNanos] loop and republishes under a
 * never-equal policy, so only the draw is invalidated, never the composition.
 */
@Composable
fun MiraAmbientBackground(
    modifier: Modifier = Modifier,
    amplitude: Float = 0f,
    active: Boolean = false,
) {
    val obsidian = MaterialTheme.colorScheme.background
    val magenta = MiraTheme.colors.auroraMagenta
    val violet = MiraTheme.colors.auroraViolet
    val indigo = MiraTheme.colors.auroraIndigo

    // Wall clock and the one smoothed amplitude, advanced per frame. Held in single-element arrays
    // so the hot draw path reads them without a recomposition and allocates nothing.
    val time = remember { floatArrayOf(0f) }
    val level = remember { floatArrayOf(0f) }
    val clock = remember { mutableStateOf(0f, neverEqualPolicy()) }
    val liveAmplitude = rememberUpdatedState(amplitude.coerceIn(0f, 1f))
    val activeState = rememberUpdatedState(active)

    LaunchedEffect(Unit) {
        var previousNanos = 0L
        while (true) {
            withFrameNanos { nowNanos ->
                val delta = if (previousNanos == 0L) {
                    0f
                } else {
                    ((nowNanos - previousNanos) / NANOS_PER_SECOND).coerceIn(0f, MAX_FRAME_SECONDS)
                }
                previousNanos = nowNanos

                // The same fast-attack / slow-release envelope the orb and the waveform use, so the
                // aurora answers Abu's voice as one instrument with them. Active keeps a floor of life.
                val target = if (activeState.value) {
                    max(liveAmplitude.value, ACTIVE_FLOOR)
                } else {
                    liveAmplitude.value
                }
                val tau = if (target > level[0]) {
                    MiraMotion.AMPLITUDE_ATTACK_SECONDS
                } else {
                    MiraMotion.AMPLITUDE_RELEASE_SECONDS
                }
                level[0] += (target - level[0]) * (1f - exp(-delta / tau))

                time[0] += delta
                clock.value = time[0]
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        clock.value // subscribe: redraw every frame, no recomposition
        val t = time[0]
        val amp = level[0]
        val w = size.width
        val h = size.height

        // The base is opaque, so the layer is complete on its own and the additive glows below have
        // near-black to add their light onto.
        drawRect(color = obsidian)

        // One breath drives the whole field — a quick rise, a held top, a slow release — so the
        // glow and the dots swell together rather than each on its own metronome.
        val breath = MiraMotion.breath(t / BREATH_PERIOD_SECONDS)
        val intensity = GLOW_BASE + GLOW_BREATH * breath + GLOW_VOICE * amp
        val swell = 1f + GLOW_SWELL * breath + GLOW_VOICE_SWELL * amp

        // Two glows drifting low on slow, detuned oscillators — one magenta, one violet — so their
        // overlap is where the light is hottest. Additive (Plus), so overlapping light brightens the
        // way real light does instead of muddying.
        val c1 = Offset(
            w * (GLOW1_X + DRIFT_X * sin(t * DRIFT1_X_SPEED)),
            h * (GLOW1_Y + DRIFT_Y * sin(t * DRIFT1_Y_SPEED + DRIFT1_PHASE)),
        )
        val c2 = Offset(
            w * (GLOW2_X + DRIFT_X * sin(t * DRIFT2_X_SPEED + DRIFT2_PHASE)),
            h * (GLOW2_Y + DRIFT_Y * sin(t * DRIFT2_Y_SPEED)),
        )
        val r1 = size.minDimension * GLOW1_RADIUS * swell
        val r2 = size.minDimension * GLOW2_RADIUS * swell
        drawAurora(c1, r1, magenta, GLOW1_ALPHA * intensity)
        drawAurora(c2, r2, violet, GLOW2_ALPHA * intensity)

        // A deep indigo wash pooled along the base, so the colour has a floor to sit on and does not
        // read as two circles floating in black.
        drawRect(
            brush = Brush.verticalGradient(
                INDIGO_WASH_TOP to Color.Transparent,
                1f to indigo.copy(alpha = INDIGO_ALPHA * intensity),
            ),
        )

        // The halftone field. A dot's light is the sum of the two glows' quadratic falloff at its
        // cell (squared distance only — no sqrt, no exp in the loop), shimmered by one slow travelling
        // wave and faded out toward the top so the greeting above reads on near-black. The dot is
        // hottest-pink where the light is strongest and cools to indigo at the fringe.
        val step = DOT_STEP.toPx()
        if (step > 0f) {
            val rSq1 = r1 * r1
            val rSq2 = r2 * r2
            val baseR = DOT_RADIUS.toPx()
            val topFade = h * DOT_TOP_FADE
            val bottomFade = h * DOT_BOTTOM_FADE
            var y = 0f
            while (y <= h) {
                // The field thins to nothing at both ends — toward the top so the greeting reads on
                // near-black, and toward the bottom so the screen settles to black at its edge. The
                // nav bar itself is translucent, so the (fading) aurora still shows through it
                // rather than being blocked by a solid pill (Abu, 16 Oct 2026).
                val vFade = (y / topFade).coerceIn(0f, 1f) *
                    ((h - y) / bottomFade).coerceIn(0f, 1f)
                if (vFade > 0f) {
                    var x = 0f
                    while (x <= w) {
                        val dx1 = x - c1.x
                        val dy1 = y - c1.y
                        val q1 = (dx1 * dx1 + dy1 * dy1) / rSq1
                        val l1 = if (q1 >= 1f) 0f else (1f - q1) * (1f - q1)

                        val dx2 = x - c2.x
                        val dy2 = y - c2.y
                        val q2 = (dx2 * dx2 + dy2 * dy2) / rSq2
                        val l2 = if (q2 >= 1f) 0f else (1f - q2) * (1f - q2)

                        var lum = (l1 + l2) * vFade * intensity
                        if (lum > DOT_MIN) {
                            // One travelling wave over the grid makes the whole field shimmer slowly.
                            val shimmer = SHIMMER_FLOOR + (1f - SHIMMER_FLOOR) *
                                (0.5f + 0.5f * sin(x * SHIMMER_FX + y * SHIMMER_FY - t * SHIMMER_SPEED))
                            lum *= shimmer
                            if (lum > DOT_MIN) {
                                val hot = (l1 / (l1 + l2 + EPS)).coerceIn(0f, 1f)
                                val dotColor = lerp(indigo, lerp(violet, magenta, hot), lum.coerceIn(0f, 1f))
                                drawCircle(
                                    color = dotColor,
                                    radius = baseR * (DOT_MIN_SCALE + DOT_SCALE * lum.coerceIn(0f, 1f)),
                                    center = Offset(x, y),
                                    alpha = (lum * DOT_ALPHA).coerceIn(0f, DOT_ALPHA),
                                )
                            }
                        }
                        x += step
                    }
                }
                y += step
            }
        }

        // Settle the field to obsidian along the very bottom edge, so the screen ends on clean
        // black below the translucent nav bar.
        drawRect(
            brush = Brush.verticalGradient(
                BOTTOM_WASH_TOP to Color.Transparent,
                1f to obsidian,
            ),
        )
    }
}

/** One soft glow: a radial gradient from a bright core to nothing, added onto what is beneath it. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAurora(
    center: Offset,
    radius: Float,
    color: Color,
    coreAlpha: Float,
) {
    if (radius <= 0f) return
    drawCircle(
        brush = Brush.radialGradient(
            0f to color.copy(alpha = coreAlpha.coerceIn(0f, 1f)),
            GLOW_MID_STOP to color.copy(alpha = (coreAlpha * GLOW_MID_FRACTION).coerceIn(0f, 1f)),
            1f to Color.Transparent,
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
        blendMode = BlendMode.Plus,
    )
}

// ----------------------------------------------------------------------------- the clock

private const val NANOS_PER_SECOND = 1_000_000_000f

/** A dropped frame must not become a jump; clamp the step at three frames' worth. */
private const val MAX_FRAME_SECONDS = 0.05f

/** One full breath of the aurora, in seconds. Slower than the orb's, so it is felt, not watched. */
private const val BREATH_PERIOD_SECONDS = 7.5f

/** The life the field holds while she is active even with no live amplitude behind it. */
private const val ACTIVE_FLOOR = 0.55f

private const val EPS = 0.0001f

// ----------------------------------------------------------------------------- the glows

// Overall brightness: a resting floor, how much the breath lifts it, and how much a live voice adds.
private const val GLOW_BASE = 0.55f
private const val GLOW_BREATH = 0.18f
private const val GLOW_VOICE = 0.42f

// How much the glow radius itself swells with the breath and with a voice.
private const val GLOW_SWELL = 0.06f
private const val GLOW_VOICE_SWELL = 0.14f

// Where the two glows rest (fractions of the box) — both low, flanking the centre, so the hot core
// pools behind the composer and the top of the screen stays dark for the greeting.
private const val GLOW1_X = 0.38f
private const val GLOW1_Y = 0.82f
private const val GLOW2_X = 0.64f
private const val GLOW2_Y = 0.74f

private const val GLOW1_RADIUS = 0.92f
private const val GLOW2_RADIUS = 0.80f

private const val GLOW1_ALPHA = 0.34f
private const val GLOW2_ALPHA = 0.30f

// The radial falloff: where the core gives way, and how bright it still is there.
private const val GLOW_MID_STOP = 0.38f
private const val GLOW_MID_FRACTION = 0.45f

// The slow drift of each glow's centre. Different speeds and phases per axis and per glow, so the
// two never line up into an obvious orbit — the field never looks like it is on a loop.
private const val DRIFT_X = 0.06f
private const val DRIFT_Y = 0.045f
private const val DRIFT1_X_SPEED = 0.11f
private const val DRIFT1_Y_SPEED = 0.08f
private const val DRIFT1_PHASE = 1.3f
private const val DRIFT2_X_SPEED = 0.09f
private const val DRIFT2_Y_SPEED = 0.13f
private const val DRIFT2_PHASE = 2.1f

// The indigo floor wash.
private const val INDIGO_WASH_TOP = 0.45f
private const val INDIGO_ALPHA = 0.5f

// ----------------------------------------------------------------------------- the dot field

/** The grid pitch and dot size. Coarse enough that a software rasteriser keeps up, fine enough to
 *  read as a halftone rather than as spots. */
private val DOT_STEP = 15.dp
private val DOT_RADIUS = 1.5.dp

/** Below this light a cell draws nothing, which is how the field thins to black away from the glow. */
private const val DOT_MIN = 0.02f

/** The top fraction of the screen over which the dots fade in from nothing. */
private const val DOT_TOP_FADE = 0.42f

/** The bottom fraction over which the dots fade back out, so the screen settles to black at its edge. */
private const val DOT_BOTTOM_FADE = 0.14f

/** Where the closing obsidian wash begins (fraction of height); it reaches full obsidian at the very
 *  bottom. Kept low so the aurora still reads through the translucent nav bar and only the very edge
 *  below it goes black. */
private const val BOTTOM_WASH_TOP = 0.88f

/** A dot is never a full circle of colour: its radius and alpha both ride the light at its cell. */
private const val DOT_MIN_SCALE = 0.35f
private const val DOT_SCALE = 0.9f
private const val DOT_ALPHA = 0.85f

// The shimmer wave over the grid: how much it dips (floor), its spatial frequency per axis (per px),
// and how fast it travels. Slow and shallow — a breath moving through the field, not a strobe.
private const val SHIMMER_FLOOR = 0.55f
private const val SHIMMER_FX = 0.010f
private const val SHIMMER_FY = 0.006f
private const val SHIMMER_SPEED = 0.9f

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun MiraAmbientBackgroundPreview() {
    MiraTheme {
        MiraAmbientBackground(amplitude = 0.4f, active = true)
    }
}
