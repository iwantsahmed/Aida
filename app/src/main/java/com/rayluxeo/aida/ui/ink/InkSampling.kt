package com.rayluxeo.aida.ui.ink

import android.view.MotionEvent

/**
 * Everything one event carries for one pointer, oldest first.
 *
 * The historical samples are the point of this function. Android batches digitiser samples
 * between frames and delivers the older ones as history; at the rate the S24 Ultra samples an
 * S Pen, three or four of them sit behind every frame's current point. Reading only `getX()` and
 * `getY()` therefore throws most of the stroke away, and the result is handwriting that looks
 * faceted rather than round — the single most common way a canvas like this is got wrong.
 *
 * Tilt is asked of the device rather than assumed: a digitiser with no tilt axis returns 0 for
 * it, and 0 radians is a real claim — the pen held upright — not the absence of one.
 */
internal fun MotionEvent.inkSamples(pointerIndex: Int): List<InkSample> {
    val tilted = device?.getMotionRange(MotionEvent.AXIS_TILT, source) != null
    val samples = ArrayList<InkSample>(historySize + 1)
    for (position in 0 until historySize) {
        samples += InkSample(
            x = getHistoricalX(pointerIndex, position),
            y = getHistoricalY(pointerIndex, position),
            pressure = getHistoricalPressure(pointerIndex, position),
            tiltRadians = if (tilted) {
                getHistoricalAxisValue(MotionEvent.AXIS_TILT, pointerIndex, position)
            } else {
                null
            },
            timestampMillis = getHistoricalEventTime(position),
        )
    }
    samples += InkSample(
        x = getX(pointerIndex),
        y = getY(pointerIndex),
        pressure = getPressure(pointerIndex),
        tiltRadians = if (tilted) getAxisValue(MotionEvent.AXIS_TILT, pointerIndex) else null,
        timestampMillis = eventTime,
    )
    return samples
}

/**
 * What the platform says is touching the glass.
 *
 * `TOOL_TYPE_ERASER` is the reason this mapping exists at all: the S Pen reports it when it is
 * reversed or when its button is held, and honouring that is how Abu expects to rub something
 * out. Everything Mira does not recognise is folded into [InkToolType.OTHER], which is treated
 * with a finger's caution rather than a pen's licence.
 */
internal fun MotionEvent.inkToolType(pointerIndex: Int): InkToolType =
    when (getToolType(pointerIndex)) {
        MotionEvent.TOOL_TYPE_STYLUS -> InkToolType.STYLUS
        MotionEvent.TOOL_TYPE_ERASER -> InkToolType.ERASER
        MotionEvent.TOOL_TYPE_FINGER -> InkToolType.FINGER
        else -> InkToolType.OTHER
    }
