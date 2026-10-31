package com.rayluxeo.aida.core.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import kotlin.math.floor

/**
 * How Mira moves.
 *
 * Motion here is punctuation, not performance: it tells Abu that something changed and then gets
 * out of the way. Nothing bounces, nothing overshoots, and nothing takes long enough to wait for.
 * Every animation in the app picks a duration and an easing from this object rather than inventing
 * its own, so the whole surface feels like one piece of software.
 */
object MiraMotion {

    /** A state flip with no travel — a checkbox, a tint, a selected chip. */
    const val INSTANT_MS = 90

    /** Small movement over a short distance: a press, a row expanding, an indicator sliding. */
    const val QUICK_MS = 140

    /** The default. Screen content fading, sheets settling, a segment moving. */
    const val STANDARD_MS = 200

    /** Something entering from off-screen, or a deliberate reveal such as the vault unlocking. */
    const val EXPRESSIVE_MS = 320

    /** Reserved for the voice orb's idle breathing, which is meant to be felt rather than watched. */
    const val AMBIENT_MS = 2_600

    /** One pass of a skeleton's shimmer sweep. Slow enough to read as "working", not as a strobe. */
    const val SHIMMER_MS = 1_250

    /**
     * Decelerating: fast to start, settling at the end. The right curve for something arriving,
     * because the eye catches the start and the ending is what reads as quality.
     */
    val Enter: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Accelerating. Things leaving should not linger. */
    val Exit: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Symmetric, for a value moving between two states while staying on screen. */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Even, for continuous motion such as a waveform or a breathing orb. */
    val Linear: Easing = CubicBezierEasing(0f, 0f, 1f, 1f)

    fun <T> instant(): FiniteAnimationSpec<T> = tween(INSTANT_MS, easing = Standard)

    fun <T> quick(): FiniteAnimationSpec<T> = tween(QUICK_MS, easing = Standard)

    fun <T> standard(): FiniteAnimationSpec<T> = tween(STANDARD_MS, easing = Standard)

    fun <T> enter(durationMs: Int = EXPRESSIVE_MS): FiniteAnimationSpec<T> =
        tween(durationMs, easing = Enter)

    fun <T> exit(durationMs: Int = QUICK_MS): FiniteAnimationSpec<T> =
        tween(durationMs, easing = Exit)

    /**
     * The one spring in the system, for a control following the thumb. Critically damped so it
     * settles without a wobble — an overshoot on a champagne accent looks like a toy.
     */
    fun <T> settle(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    // ---------------------------------------------------------------- movement vs. fading

    /**
     * **Anything that MOVES uses a spring; anything that FADES uses a curve.**
     *
     * This is the single rule that separates software which feels expensive from software which
     * merely animates, and it is worth stating plainly because it is not obvious.
     *
     * A duration is a promise about time, and a moving object interrupted mid-promise has to
     * either break it or finish going somewhere Abu has already stopped asking for. A spring has
     * no duration: interrupt it and it carries its current velocity into the new target, so a
     * sheet he catches halfway follows his thumb instead of fighting it. That is what "responsive"
     * actually means.
     *
     * Opacity is the exception, and deliberately so. A fade has no momentum to preserve — nothing
     * is travelling — and a spring on alpha just makes light flicker at the end of its settle.
     * So: [move] and [moveGently] for position, size and scale; [fade] and the tweens above for
     * colour and opacity.
     */
    fun <T> move(): FiniteAnimationSpec<T> = spring(
        dampingRatio = DAMPING_TAUT,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** For something large or far — a sheet, a screen. Slower, and just as settled. */
    fun <T> moveGently(): FiniteAnimationSpec<T> = spring(
        dampingRatio = DAMPING_TAUT,
        stiffness = Spring.StiffnessLow,
    )

    /** For a control under the thumb right now: stiff, so it reads as attached to the finger. */
    fun <T> track(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh,
    )

    /** Opacity and colour. Named so a call site says which of the two rules above it is obeying. */
    fun <T> fade(durationMs: Int = STANDARD_MS): FiniteAnimationSpec<T> =
        tween(durationMs, easing = Standard)

    /**
     * Just short of critical.
     *
     * A perfectly critically damped spring is correct and very slightly dead; a hair of compliance
     * reads as a physical object arriving. Not enough to overshoot — nothing on a champagne accent
     * may bounce (§10) — only enough to be felt.
     */
    private const val DAMPING_TAUT = 0.92f

    // ---------------------------------------------------------------- the living mark

    /**
     * A breath, from a phase in 0f..1f: a fairly quick inhale, a brief hold at the top, and a
     * slower release. It is the difference between a mark that is *alive at rest* and a screensaver.
     *
     * A symmetric reverse-tween — the obvious way to make a light pulse — is the single loudest
     * "this is a screensaver" tell, because a real chest fills faster than it empties and dwells a
     * moment at the top of the breath. So [MiraAvatar] and [MiraWaveform] shape their idle cycle
     * through this rather than a bare sine. Pure and allocation-free: safe to call once per frame
     * inside a draw. The value never exceeds 1f, so nothing it drives can overshoot (§10).
     */
    fun breath(phase: Float): Float {
        val p = phase - floor(phase)
        return when {
            p < BREATH_INHALE -> Enter.transform(p / BREATH_INHALE)
            p < BREATH_INHALE + BREATH_HOLD -> 1f
            else -> 1f - Standard.transform((p - BREATH_INHALE - BREATH_HOLD) / BREATH_EXHALE)
        }
    }

    private const val BREATH_INHALE = 0.33f
    private const val BREATH_HOLD = 0.09f
    private const val BREATH_EXHALE = 1f - BREATH_INHALE - BREATH_HOLD

    /** One full breath of the voice mark, in seconds. Slow enough to be felt, not watched. */
    const val BREATH_PERIOD_SECONDS = 5.2f

    /**
     * Fast attack, slow release — the one law both the orb's bloom and the waveform's strands
     * follow, so they answer the same microphone as a single instrument. Fast up is Mira catching
     * the syllable; slow down is her holding it a beat after Abu stops. Seconds, for a one-pole
     * `level += (target - level) * (1 - exp(-dt / tau))` filter driven off `withFrameNanos`.
     */
    const val AMPLITUDE_ATTACK_SECONDS = 0.06f
    const val AMPLITUDE_RELEASE_SECONDS = 0.22f
}
