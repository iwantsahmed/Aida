package com.rayluxeo.aida.ui.ink

import android.content.Context
import android.os.Build
import android.view.MotionEvent
import android.view.MotionPredictor
import androidx.annotation.RequiresApi

/** Fallback when the display will not say, chosen low so the guess stays short. */
private const val FALLBACK_REFRESH_HZ = 60f

private const val NANOS_PER_SECOND = 1_000_000_000f

/**
 * Drawing one frame ahead of the hand.
 *
 * There are a few milliseconds between the digitiser feeling the nib and the glass showing the
 * ink, and on a page of handwriting that gap is visible as the line trailing the pen. The
 * platform's own [MotionPredictor] closes it by extrapolating where the stroke is going, which
 * costs nothing, needs no dependency, and is exactly the kind of thing that should not be
 * hand-rolled.
 *
 * **Nothing this produces is ever stored.** A predicted point is the platform's guess, not
 * something Abu wrote; committing one would put a mark in his note that his hand never made, and
 * it would be there on every replay and every export. That is why [InkCanvasEngine] keeps the
 * prediction in a field of its own rather than appending it to the live stroke.
 *
 * The interface is here so the canvas can hold "prediction, or none" without carrying an API
 * level around with it — the platform class is reached for only inside [create].
 */
internal interface InkPrediction {

    fun record(event: MotionEvent)

    /** Where the stroke is likely to be one frame from now, or nothing at all. */
    fun predict(event: MotionEvent): List<InkSample>

    companion object {

        /** Null on a phone with no platform predictor, which draws to the last real sample. */
        fun create(context: Context): InkPrediction? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                PlatformInkPrediction(context)
            } else {
                null
            }
    }
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private class PlatformInkPrediction(context: Context) : InkPrediction {

    private val predictor = MotionPredictor(context)

    /**
     * One frame of the display Abu is actually writing on, rather than a guessed 16ms.
     *
     * A context that is not attached to a display refuses the question outright, so the refusal
     * is caught and the conservative 60Hz taken instead — a short guess is always safe, where a
     * long one would draw ink ahead of a pen that has already stopped.
     */
    private val horizonNanos: Long = run {
        val refreshHz = runCatching { context.display?.refreshRate }
            .getOrNull()
            ?.takeIf { it > 0f }
            ?: FALLBACK_REFRESH_HZ
        (NANOS_PER_SECOND / refreshHz).toLong()
    }

    override fun record(event: MotionEvent) {
        if (!isAvailableFor(event)) return
        predictor.record(event)
    }

    /**
     * The event handed back belongs to the predictor and is reused between calls, so it is read
     * and left alone — recycling it here would take an event the platform still owns.
     */
    override fun predict(event: MotionEvent): List<InkSample> {
        if (!isAvailableFor(event)) return emptyList()
        val predicted = predictor.predict(event.eventTimeNanos + horizonNanos) ?: return emptyList()
        return predicted.inkSamples(pointerIndex = 0)
    }

    private fun isAvailableFor(event: MotionEvent): Boolean =
        predictor.isPredictionAvailable(event.deviceId, event.source)
}
