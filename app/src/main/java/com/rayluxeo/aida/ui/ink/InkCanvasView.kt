package com.rayluxeo.aida.ui.ink

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.view.MotionEvent
import android.view.View
import com.rayluxeo.aida.domain.ink.InkHistory
import com.rayluxeo.aida.domain.ink.InkStroke
import com.rayluxeo.aida.domain.ink.InkTool
import com.rayluxeo.aida.domain.ink.strokeWidthAt

private const val NO_POINTER = -1

/**
 * The page itself: a plain [View] that Abu writes on.
 *
 * **Why this is not Compose `pointerInput`.** Every one of these is load-bearing and none of them
 * is available through the pointer layer:
 *
 * - **Historical samples.** `MotionEvent.getHistorySize` and `getHistoricalX/Y/Pressure` carry the
 *   digitiser samples Android batched between frames. At the rate an S Pen is sampled that is
 *   most of the stroke, and Compose's pointer events do not expose them.
 * - **`requestUnbufferedDispatch`.** It asks the platform to stop batching this gesture at all,
 *   which is the difference between ink that keeps up with the nib and ink that lags a frame
 *   behind it. It is a method on a `View`.
 * - **[MotionPredictor][android.view.MotionPredictor].** It takes and returns `MotionEvent`s.
 * - **`getToolType`.** A reversed S Pen is `TOOL_TYPE_ERASER`, and honouring the hardware is how
 *   Abu expects to rub something out.
 *
 * The view decides nothing. It translates events into [InkMotion], hands them to
 * [InkCanvasEngine], and paints what the engine holds — which is what lets every rule about
 * palms, erasers and undo be tested without a device.
 *
 * It invents no colour either: the two it paints with are handed in, because the only ivory and
 * the only champagne in Mira are the palette's.
 */
@SuppressLint("ViewConstructor")
internal class InkCanvasView(
    context: Context,
    private val engine: InkCanvasEngine,
    inkColor: Int,
    eraserRingColor: Int,
    hairlinePx: Float,
) : View(context) {

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val nibPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val eraserRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    /**
     * Prediction, or null on a phone without it. A canvas that cannot predict simply draws to
     * the last real sample, which is what it would have done anyway.
     */
    private val prediction: InkPrediction? = InkPrediction.create(context)

    /**
     * Everything already committed, painted once and then blitted each frame.
     *
     * A full page is tens of thousands of segments. Redrawing all of them every frame while Abu
     * is mid-word is how a canvas starts dropping frames, so the committed strokes are cached and
     * only the live stroke is painted per frame.
     */
    private var committed: Bitmap? = null
    private var committedCanvas: Canvas? = null
    private var renderedHistory: InkHistory? = null
    private var renderedErase: Set<Int> = emptySet()
    private var layerDirty: Boolean = true

    /** Held as a field so it can be compared by identity when the view is detached. */
    private val invalidateCallback: () -> Unit = { invalidate() }

    private var activePointerId: Int = NO_POINTER
    private var eraserX: Float = 0f
    private var eraserY: Float = 0f

    /** True once this handwriting session has been asked for, so a second pen-down does not. */
    private var handwritingRequested: Boolean = false

    /** Ivory on obsidian, handed down from the theme — the view invents no colour of its own. */
    var inkColor: Int = inkColor
        set(value) {
            if (field == value) return
            field = value
            strokePaint.color = value
            nibPaint.color = value
            layerDirty = true
            invalidate()
        }

    /** The champagne accent, for the ring that shows how much the eraser is about to take. */
    var eraserRingColor: Int = eraserRingColor
        set(value) {
            if (field == value) return
            field = value
            eraserRingPaint.color = value
            invalidate()
        }

    /**
     * While this is on the keyboard takes the pen, not the canvas.
     *
     * The platform's stylus handwriting recognises strokes *as they are written*, so the two
     * cannot both have them: anything drawn here during a handwriting session would be ink the
     * recogniser never saw, sitting on a page Abu thought he was dictating into.
     */
    var handwritingMode: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            // Each entry into the mode is one session. Without this reset a second pen-down asks
            // the keyboard for another one before the first has reported, and the two race for
            // the same strokes.
            handwritingRequested = false
        }

    /** Fires on pen-down in handwriting mode, with the view the session attaches to. */
    var onHandwritingRequested: ((View) -> Unit)? = null

    /** The hairline the eraser ring is drawn at, in pixels, from the design system's own token. */
    var hairlinePx: Float = hairlinePx
        set(value) {
            if (field == value) return
            field = value
            eraserRingPaint.strokeWidth = value
            invalidate()
        }

    init {
        isFocusable = true
        strokePaint.color = this.inkColor
        nibPaint.color = this.inkColor
        eraserRingPaint.color = this.eraserRingColor
        eraserRingPaint.strokeWidth = this.hairlinePx
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        engine.onInvalidate = invalidateCallback
        invalidate()
    }

    override fun onDetachedFromWindow() {
        // Only if it is still ours: a rotation builds the replacement view before this one goes,
        // and clearing unconditionally would leave the new canvas unable to repaint itself.
        if (engine.onInvalidate === invalidateCallback) engine.onInvalidate = null
        releaseLayer()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        releaseLayer()
        engine.resize(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val layerCanvas = ensureLayer()
        val layer = committed
        if (layer != null && layerCanvas != null) {
            if (layerDirty ||
                renderedHistory !== engine.history ||
                renderedErase != engine.pendingErase
            ) {
                repaintCommitted(layerCanvas)
            }
            canvas.drawBitmap(layer, 0f, 0f, null)
        }
        engine.liveStroke?.let { drawStroke(canvas, it) }
        drawPrediction(canvas)
        drawEraserRing(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (handwritingMode) {
            // The session is asked for once per entry into the mode and the keyboard takes it
            // from there. The event is still consumed so nothing underneath reacts to a pen that
            // is mid-word.
            if (!handwritingRequested &&
                event.actionMasked == MotionEvent.ACTION_DOWN &&
                event.inkToolType(event.actionIndex).isPen
            ) {
                handwritingRequested = true
                onHandwritingRequested?.invoke(this)
            }
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val pointerIndex = event.actionIndex
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    // Asks the platform to stop batching this gesture. Without it the samples
                    // arrive a frame late in a clump, which is felt as the ink trailing the nib.
                    requestUnbufferedDispatch(event)
                }
                // Claimed only when the engine took THIS contact. Asking whether a gesture is in
                // flight instead answers yes while the pen's stroke is still going, so the palm
                // landing beside the nib became the pointer every later move was read from — and
                // the heel of Abu's hand was drawn into the middle of his word.
                if (feed(event, pointerIndex, InkPhase.DOWN)) {
                    activePointerId = event.getPointerId(pointerIndex)
                    rememberEraserPosition(event, pointerIndex)
                    prediction?.record(event)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex >= 0) {
                    feed(event, pointerIndex, InkPhase.MOVE)
                    rememberEraserPosition(event, pointerIndex)
                    predictAhead(event)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                if (event.getPointerId(pointerIndex) == activePointerId) {
                    feed(event, pointerIndex, InkPhase.UP)
                    activePointerId = NO_POINTER
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                if (activePointerId != NO_POINTER) {
                    val pointerIndex = event.findPointerIndex(activePointerId).coerceAtLeast(0)
                    feed(event, pointerIndex, InkPhase.CANCEL)
                    activePointerId = NO_POINTER
                }
            }
        }
        return true
    }

    private fun feed(event: MotionEvent, pointerIndex: Int, phase: InkPhase): Boolean =
        engine.onMotion(
            InkMotion(
                phase = phase,
                toolType = event.inkToolType(pointerIndex),
                samples = event.inkSamples(pointerIndex),
            ),
        )

    /**
     * The cached layer, allocated on first use rather than only when the size changes.
     *
     * Freeing it on detach and allocating it only in `onSizeChanged` is not symmetric: a view
     * re-attached at the size it already had never gets another `onSizeChanged`, so the layer
     * stays null, the committed strokes are never painted, and the page comes back blank with
     * every one of them still in the engine.
     */
    private fun ensureLayer(): Canvas? {
        committedCanvas?.let { return it }
        if (width <= 0 || height <= 0) return null
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        committed = bitmap
        layerDirty = true
        return Canvas(bitmap).also { committedCanvas = it }
    }

    private fun releaseLayer() {
        committedCanvas = null
        committed?.recycle()
        committed = null
        renderedHistory = null
        layerDirty = true
    }

    private fun predictAhead(event: MotionEvent) {
        val predictor = prediction ?: return
        predictor.record(event)
        engine.predict(predictor.predict(event))
    }

    private fun rememberEraserPosition(event: MotionEvent, pointerIndex: Int) {
        eraserX = event.getX(pointerIndex)
        eraserY = event.getY(pointerIndex)
    }

    private fun repaintCommitted(layerCanvas: Canvas) {
        layerCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        engine.visibleStrokes().forEach { drawStroke(layerCanvas, it) }
        renderedHistory = engine.history
        renderedErase = engine.pendingErase
        layerDirty = false
    }

    /**
     * One line per sample pair, each at its own width.
     *
     * The width comes from the pressure either end of the segment through
     * [strokeWidthAt][com.rayluxeo.aida.domain.ink.strokeWidthAt] — the same rule the exported
     * PNG is drawn with, which is the only way the image Abu shares is the page he wrote. A phone
     * with no pressure sensor reports full pressure throughout and gets an even line, which is
     * correct rather than a fallback.
     */
    private fun drawStroke(canvas: Canvas, stroke: InkStroke) {
        val points = stroke.points
        if (points.size == 1) {
            val only = points.first()
            val radius = strokeWidthAt(stroke.widthPx, only.pressure) / 2f
            canvas.drawCircle(only.x, only.y, radius, nibPaint)
            return
        }
        for (index in 0 until points.lastIndex) {
            val from = points[index]
            val to = points[index + 1]
            strokePaint.strokeWidth =
                strokeWidthAt(stroke.widthPx, (from.pressure + to.pressure) / 2f)
            canvas.drawLine(from.x, from.y, to.x, to.y, strokePaint)
        }
    }

    /**
     * The predicted tail, in the same ink as the rest.
     *
     * Deliberately not tinted or faded: it exists to hide latency, and a differently coloured tip
     * would draw the eye to exactly the thing it is there to make invisible. It is painted on the
     * frame and never on the committed layer, so it disappears the moment a real sample replaces
     * it and can never reach the page.
     */
    private fun drawPrediction(canvas: Canvas) {
        val tail = engine.predictedTail
        if (tail.isEmpty()) return
        val last = engine.liveStroke?.points?.lastOrNull() ?: return
        var fromX = last.x
        var fromY = last.y
        var fromPressure = last.pressure
        tail.forEach { sample ->
            strokePaint.strokeWidth =
                strokeWidthAt(engine.nibWidthPx, (fromPressure + sample.pressure) / 2f)
            canvas.drawLine(fromX, fromY, sample.x, sample.y, strokePaint)
            fromX = sample.x
            fromY = sample.y
            fromPressure = sample.pressure
        }
    }

    /** Shows how much the eraser will take, because otherwise its size is a guess. */
    private fun drawEraserRing(canvas: Canvas) {
        if (engine.activeTool != InkTool.ERASER) return
        canvas.drawCircle(eraserX, eraserY, engine.eraserRadiusPx, eraserRingPaint)
    }
}
