package com.rayluxeo.aida.ui.ink

import com.rayluxeo.aida.domain.ink.InkPoint

/**
 * What the digitiser said, with Android taken out of it.
 *
 * `MotionEvent` cannot be constructed in a local unit test without a device, and it is also the
 * single most important thing on this screen to get right — a dropped historical sample turns
 * round handwriting angular, and a finger that draws while the palm is resting ruins a page. So
 * the canvas view translates every event into these values at the boundary and decides nothing
 * itself: everything that *decides* lives in [InkInputPolicy] and [InkCanvasEngine], which are
 * plain Kotlin and are tested without a stylus (AGENTS.md §7.4 rules Hilt and Robolectric out of
 * local tests, and a pen cannot be faked in one either).
 *
 * [timestampMillis] is the platform's own event time, not a wall clock. The engine subtracts the
 * first sample it ever sees to get [InkPoint.elapsedMillis], which is why nothing here reaches
 * for `MiraClock`: a stroke's timing is relative to the page, never to the date it was written.
 */
internal data class InkSample(
    val x: Float,
    val y: Float,
    val pressure: Float = InkPoint.FULL_PRESSURE,
    val tiltRadians: Float? = null,
    val timestampMillis: Long = 0L,
)

/**
 * Which implement the platform says is touching the glass.
 *
 * Deliberately not [com.rayluxeo.aida.domain.ink.InkTool]: that one is the mode Mira is in, this
 * one is the hardware's claim about Abu's hand. They differ exactly where it matters — the S Pen
 * reversed reports [ERASER] while the on-screen toggle still says pen, and the hardware wins.
 *
 * [OTHER] covers a mouse and `TOOL_TYPE_UNKNOWN`. It is treated as a finger rather than as a pen,
 * because a mark Abu did not mean to make is worse than a mark he has to make twice.
 */
internal enum class InkToolType { STYLUS, ERASER, FINGER, OTHER }

/** Where in a single gesture this event sits. */
internal enum class InkPhase { DOWN, MOVE, UP, CANCEL }

/**
 * One event, carrying **every** sample that arrived with it.
 *
 * Android batches samples between frames and hands the older ones over as history. At the 120Hz
 * the S24 Ultra digitises at, three or four of those sit behind each frame's current point, and
 * an implementation that reads only the current point throws away three quarters of the stroke.
 * That is the single biggest cause of handwriting that looks drawn by a committee, so the flat
 * list here is the contract: oldest first, current point last.
 */
internal data class InkMotion(
    val phase: InkPhase,
    val toolType: InkToolType,
    val samples: List<InkSample>,
)

/**
 * True for the two tool types the S Pen reports, whichever way round it is being held.
 *
 * Both count as "the pen is on the glass": while either is down nothing else may leave a mark,
 * and either one is what a handwriting session listens for.
 */
internal val InkToolType.isPen: Boolean
    get() = this == InkToolType.STYLUS || this == InkToolType.ERASER
