package com.rayluxeo.aida.ui.ink

import com.rayluxeo.aida.domain.ink.InkDrawing
import com.rayluxeo.aida.domain.ink.InkEdit
import com.rayluxeo.aida.domain.ink.InkHistory
import com.rayluxeo.aida.domain.ink.InkPoint
import com.rayluxeo.aida.domain.ink.InkStroke
import com.rayluxeo.aida.domain.ink.InkTool
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The nib and the eraser before a screen has said how dense its display is.
 *
 * The canvas is handed density-derived values from the spacing scale the moment it composes; these
 * are what the engine works with until then, and what a test that has no display uses throughout.
 */
private const val DEFAULT_NIB_WIDTH_PX = 5f

private const val DEFAULT_ERASER_RADIUS_PX = 18f

/**
 * Everything the canvas knows, with nothing Android in it.
 *
 * The view below this class reports what the pen did and paints what it is handed; every decision
 * — whether a contact counts, which implement it is, when a stroke is committed, what an eraser
 * took, what undo puts back — is made here, where a local test can make it without a device
 * (AGENTS.md §7.4).
 *
 * Undo is not reimplemented: [InkHistory] owns it, and this class only hands it edits. That is
 * the point of the domain type — a second stack here is how a redo eventually splices an old mark
 * into new writing.
 *
 * It lives in the view model rather than in the view. A `MotionEvent` has to reach the glass in
 * the frame it arrived in, so the view mutates this object directly instead of routing tens of
 * thousands of points through a `StateFlow` and a recomposition; keeping it above the view is
 * also what stops a rotation, which destroys the view, taking Abu's page with it.
 */
internal class InkCanvasEngine {

    /** Repaint. Set by the canvas view; fires for anything visible, including the prediction. */
    var onInvalidate: (() -> Unit)? = null

    /** Something was committed or taken back. Set by the view model, which drives the controls. */
    var onHistoryChanged: (() -> Unit)? = null

    var nibWidthPx: Float = DEFAULT_NIB_WIDTH_PX

    var eraserRadiusPx: Float = DEFAULT_ERASER_RADIUS_PX

    /** What the on-screen toggle says. A reversed S Pen overrides it — see [InkInputPolicy]. */
    var selectedTool: InkTool = InkTool.PEN

    /**
     * Off by default, and that is a judgement about this phone rather than a safe-looking default:
     * the S24 Ultra ships with a pen, so on a full-screen canvas the finger touching the glass is
     * usually the hand holding the phone. Abu turns it on for the times he has put the pen down.
     */
    var fingerDrawingEnabled: Boolean = false

    var history: InkHistory = InkHistory.of(InkDrawing.blank(0, 0))
        private set

    /** The mark under the nib right now, painted live and only committed on pen-up. */
    var liveStroke: InkStroke? = null
        private set

    /**
     * Where the pen is about to be.
     *
     * Drawn ahead of the hand to hide the few milliseconds between the digitiser and the glass,
     * and **never committed**. A predicted point is the platform's guess, not something Abu wrote;
     * storing one would put ink in his note that his hand never made, and it would come back on
     * every replay and every export. The distinction is the whole reason this is a separate field
     * rather than points appended to [liveStroke].
     */
    var predictedTail: List<InkSample> = emptyList()
        private set

    /**
     * Strokes this eraser gesture has taken, hidden from the page but not yet committed.
     *
     * Held back until pen-up so that one sweep across four letters is one undo and not four —
     * an eraser that has to be undone once per mark it happened to cross is worse than no undo.
     */
    var pendingErase: Set<Int> = emptySet()
        private set

    /**
     * Which implement the gesture in flight is using, or null between gestures.
     *
     * Read by the view so the eraser can show its own size under the hand. A reversed S Pen means
     * this can say [InkTool.ERASER] while [selectedTool] still says pen, which is exactly the
     * state the ring has to be drawn for.
     */
    var activeTool: InkTool? = null
        private set

    private var stylusDown: Boolean = false

    /**
     * Whether the gesture in flight was begun by the pen.
     *
     * The second line of defence behind the pointer the view tracks: a move or a lift that is not
     * the implement this gesture started with is not part of it, however it reaches here. Without
     * it the engine trusts [activeTool] alone, and the heel of Abu's hand — a contact it had
     * already refused — can be appended to the word the nib is halfway through.
     */
    private var activeIsPen: Boolean = false

    private var livePoints: MutableList<InkPoint> = mutableListOf()
    private var lastEraserX: Float = 0f
    private var lastEraserY: Float = 0f

    /**
     * The view size the page was last fitted to.
     *
     * Kept apart from the drawing's own size because they are two different measurements: a page
     * fitted into a shorter view keeps the proportions it was written at, so the drawing records
     * the box its writing occupies while this records the glass it was fitted into. Comparing
     * against the drawing instead made every re-measure at an unchanged size look like a resize.
     */
    private var fittedWidthPx: Int = 0
    private var fittedHeightPx: Int = 0

    /** Event time of this session's first sample, so points are stored relative to the page. */
    private var originMillis: Long? = null

    /** Where a reopened page's clock had got to, so new strokes carry on rather than restart. */
    private var baseElapsedMillis: Long = 0L

    private var revision: Int = 0
    private var savedRevision: Int = 0

    val drawing: InkDrawing get() = history.drawing

    val canUndo: Boolean get() = history.canUndo

    val canRedo: Boolean get() = history.canRedo

    val strokeCount: Int get() = history.strokeCount

    /**
     * Whether there is anything on the page at all, counting the mark being made right now.
     *
     * Separate from [strokeCount] because the line inviting Abu to start writing has to go the
     * moment he does, not when he lifts the nib — a hint sitting under his first word reads as a
     * screen that has not noticed him.
     */
    val hasMarks: Boolean get() = strokeCount > 0 || liveStroke != null

    /** True when there is something on the page that is not on disk. Drives the save control. */
    val hasUnsavedWork: Boolean get() = revision != savedRevision

    /** Strokes the page is actually showing: everything except what this sweep has rubbed out. */
    fun visibleStrokes(): List<InkStroke> =
        if (pendingErase.isEmpty()) {
            drawing.strokes
        } else {
            drawing.strokes.filterIndexed { index, _ -> index !in pendingErase }
        }

    /**
     * Fits the page to the view.
     *
     * A page already written on is scaled uniformly rather than re-laid out, because handwriting
     * stretched on one axis stops being Abu's handwriting. Undo does not survive a fit that
     * actually moves something: the edits behind it hold coordinates from the old size, and a
     * redo that put a mark back at those would be worse than a rotation costing the undo stack.
     *
     * A fit that moves nothing costs nothing — neither the coordinates nor the history are
     * touched — because this is called on every measure pass, not only when Abu rotates the
     * phone.
     */
    fun resize(widthPx: Int, heightPx: Int) {
        if (widthPx <= 0 || heightPx <= 0) return
        if (widthPx == fittedWidthPx && heightPx == fittedHeightPx) return
        fittedWidthPx = widthPx
        fittedHeightPx = heightPx
        val current = drawing
        val fitted = fit(current, widthPx, heightPx)
        if (fitted == current) return
        history = InkHistory.of(fitted)
        onHistoryChanged?.invoke()
        onInvalidate?.invoke()
    }

    /**
     * Puts a page read back from disk on the canvas.
     *
     * It arrives clean: nothing has been written since it was stored, so the save control has
     * nothing to offer yet.
     */
    fun restore(stored: InkDrawing) {
        val fitted = fitToCurrentPage(stored)
        history = InkHistory.of(fitted)
        baseElapsedMillis = fitted.strokes.maxOfOrNull { stroke ->
            stroke.points.maxOf { it.elapsedMillis }
        } ?: 0L
        originMillis = null
        livePoints = mutableListOf()
        liveStroke = null
        pendingErase = emptySet()
        activeTool = null
        activeIsPen = false
        predictedTail = emptyList()
        revision = 0
        savedRevision = 0
        onHistoryChanged?.invoke()
        onInvalidate?.invoke()
    }

    /**
     * A marker for "the page exactly as it is now", to be handed back to [markSaved].
     *
     * Saving is not instant, and Abu can write another word while it is happening. Marking the
     * page clean by whatever it looks like when the write finishes would quietly swallow that
     * word — the save control would go dark over writing that is not on disk. So the caller takes
     * this together with the drawing it is about to store, and gives it back afterwards.
     */
    fun revisionMark(): Int = revision

    /** Called once [revisionMark]'s page has been written to disk. */
    fun markSaved(mark: Int) {
        if (mark < savedRevision) return
        savedRevision = mark
        onHistoryChanged?.invoke()
    }

    fun undo() {
        if (!canUndo) return
        history = history.undo()
        afterEdit()
    }

    fun redo() {
        if (!canRedo) return
        history = history.redo()
        afterEdit()
    }

    fun clear() {
        if (drawing.isEmpty) return
        history = history.clear()
        afterEdit()
    }

    /**
     * Hands the canvas the platform's guess at the next few points.
     *
     * Accepted only while Abu is actually drawing, and dropped the instant the gesture ends: a
     * prediction outliving the stroke it belonged to would leave a phantom tail on the page.
     */
    fun predict(samples: List<InkSample>) {
        if (activeTool != InkTool.PEN) {
            if (predictedTail.isNotEmpty()) {
                predictedTail = emptyList()
                onInvalidate?.invoke()
            }
            return
        }
        predictedTail = samples
        onInvalidate?.invoke()
    }

    /**
     * @return whether this contact is part of the gesture the engine is recording. The view
     *   claims the pointer it tracks on that answer and on nothing else: a palm landing beside an
     *   in-flight pen stroke is refused here, and a caller that read [activeTool] instead would
     *   see the *pen's* gesture still in flight, take the palm for the accepted contact, and feed
     *   the heel of Abu's hand into the word he is writing.
     */
    fun onMotion(motion: InkMotion): Boolean = when (motion.phase) {
        InkPhase.DOWN -> begin(motion)
        InkPhase.MOVE -> extend(motion)
        InkPhase.UP -> finish(motion)
        InkPhase.CANCEL -> abandon()
    }

    private fun begin(motion: InkMotion): Boolean {
        predictedTail = emptyList()
        val verdict = InkInputPolicy.verdict(
            toolType = motion.toolType,
            selectedTool = selectedTool,
            fingerDrawingEnabled = fingerDrawingEnabled,
            // Read before the flag is raised, so the pen's own pen-down is never rejected by it.
            stylusDown = stylusDown,
        )
        if (motion.toolType.isPen) stylusDown = true
        val tool = (verdict as? InkInputVerdict.Mark)?.tool
        if (tool == null) {
            // A contact Mira is not writing with — a resting palm, a finger with the toggle off.
            // Whatever is already in flight is left exactly as it is: a palm landing beside the
            // nib must not end the stroke the nib is halfway through.
            return false
        }
        // The pen arriving during a finger stroke takes over, and what the finger had started is
        // thrown away rather than committed. That IS palm rejection in the case it matters most:
        // the heel of the hand lands a moment before the nib does, and keeping its mark would
        // leave a smear across the word Abu was about to write.
        if (activeTool != null) discardGesture()
        activeTool = tool
        activeIsPen = motion.toolType.isPen
        when (tool) {
            InkTool.PEN -> {
                livePoints = mutableListOf()
                appendPoints(motion.samples)
            }

            InkTool.ERASER -> {
                pendingErase = emptySet()
                val first = motion.samples.firstOrNull()
                if (first != null) {
                    lastEraserX = first.x
                    lastEraserY = first.y
                }
                eraseAlong(motion.samples)
            }
        }
        // The controls are told a gesture has started, not only that one has finished: what the
        // page is showing has already changed by the first point.
        onHistoryChanged?.invoke()
        onInvalidate?.invoke()
        return true
    }

    private fun extend(motion: InkMotion): Boolean {
        if (!continues(motion)) return false
        when (activeTool) {
            InkTool.PEN -> appendPoints(motion.samples)
            InkTool.ERASER -> eraseAlong(motion.samples)
            null -> return false
        }
        onInvalidate?.invoke()
        return true
    }

    private fun finish(motion: InkMotion): Boolean {
        if (!continues(motion)) return false
        when (activeTool) {
            InkTool.PEN -> {
                appendPoints(motion.samples)
                commitStroke()
            }

            InkTool.ERASER -> {
                eraseAlong(motion.samples)
                commitErase()
            }

            null -> return false
        }
        endGesture()
        return true
    }

    /**
     * A cancel voids the whole stream, whatever it claims is touching the glass.
     *
     * The tool type on a cancel is read from whichever pointer index survives, which need not be
     * the one that began the gesture — so clearing [stylusDown] only for a pen-shaped cancel is
     * how that flag used to stick on, and a stuck flag means finger drawing stays dead for the
     * life of the view model however many times Abu toggles it.
     */
    private fun abandon(): Boolean {
        discardGesture()
        stylusDown = false
        predictedTail = emptyList()
        onInvalidate?.invoke()
        return true
    }

    /** Whether this event belongs to the gesture in flight rather than to a second contact. */
    private fun continues(motion: InkMotion): Boolean =
        activeTool != null && motion.toolType.isPen == activeIsPen

    /** Throws away the gesture in flight without committing anything or touching the history. */
    private fun discardGesture() {
        livePoints = mutableListOf()
        liveStroke = null
        pendingErase = emptySet()
        activeTool = null
        activeIsPen = false
    }

    private fun endGesture() {
        // Read from the gesture rather than from the event that ended it, so a lift that arrives
        // naming another implement cannot leave the pen recorded as still on the glass.
        if (activeIsPen) stylusDown = false
        activeTool = null
        activeIsPen = false
        predictedTail = emptyList()
        onInvalidate?.invoke()
    }

    private fun appendPoints(samples: List<InkSample>) {
        if (samples.isEmpty()) return
        samples.forEach { sample ->
            val origin = originMillis ?: sample.timestampMillis.also { originMillis = it }
            livePoints += InkPoint(
                x = sample.x,
                y = sample.y,
                pressure = sample.pressure.coerceIn(0f, InkPoint.FULL_PRESSURE),
                tiltRadians = sample.tiltRadians,
                elapsedMillis = (sample.timestampMillis - origin) + baseElapsedMillis,
            )
        }
        liveStroke = InkStroke(widthPx = nibWidthPx, points = livePoints.toList())
    }

    private fun commitStroke() {
        val points = livePoints.toList()
        livePoints = mutableListOf()
        liveStroke = null
        if (points.isEmpty()) return
        history = history.apply(InkEdit.Add(InkStroke(widthPx = nibWidthPx, points = points)))
        afterEdit()
    }

    private fun eraseAlong(samples: List<InkSample>) {
        if (samples.isEmpty()) return
        val strokes = drawing.strokes
        if (strokes.isEmpty()) {
            val last = samples.last()
            lastEraserX = last.x
            lastEraserY = last.y
            return
        }
        var taken = pendingErase
        samples.forEach { sample ->
            val hits = InkEraser.strokesTouching(
                drawing = drawing,
                fromX = lastEraserX,
                fromY = lastEraserY,
                toX = sample.x,
                toY = sample.y,
                radiusPx = eraserRadiusPx,
            )
            if (hits.isNotEmpty()) taken = taken + hits
            lastEraserX = sample.x
            lastEraserY = sample.y
        }
        pendingErase = taken
    }

    private fun commitErase() {
        val taken = pendingErase
        pendingErase = emptySet()
        if (taken.isEmpty()) return
        val strokes = drawing.strokes
        val removed = taken.sorted()
            .filter { it in strokes.indices }
            .map { InkEdit.IndexedStroke(index = it, stroke = strokes[it]) }
        if (removed.isEmpty()) return
        history = history.apply(InkEdit.Erase(removed))
        afterEdit()
    }

    private fun afterEdit() {
        revision++
        onHistoryChanged?.invoke()
        onInvalidate?.invoke()
    }

    /**
     * A page read back at a different size is scaled to fit; one read back before the view has
     * been measured is left alone, and [resize] catches it the moment the size is known.
     */
    private fun fitToCurrentPage(loaded: InkDrawing): InkDrawing {
        if (fittedWidthPx <= 0 || fittedHeightPx <= 0) return loaded
        return fit(loaded, fittedWidthPx, fittedHeightPx)
    }

    private fun fit(drawing: InkDrawing, widthPx: Int, heightPx: Int): InkDrawing = when {
        drawing.widthPx == widthPx && drawing.heightPx == heightPx -> drawing
        drawing.isEmpty || drawing.widthPx <= 0 || drawing.heightPx <= 0 ->
            // Nothing written yet, so the page simply becomes the glass it is being shown on.
            InkDrawing(widthPx, heightPx, drawing.strokes)
        else -> drawing.scaledTo(widthPx, heightPx)
    }
}

/**
 * The same drawing fitted into a different view, at the size its writing actually occupies.
 *
 * One factor for both axes, taken from whichever fits: writing squashed on one axis is no longer
 * the hand that wrote it. The nib is scaled with the page for the same reason — a stroke that
 * kept its old width would look heavier or lighter than it was drawn.
 *
 * **The size recorded is the writing's box, not the view's**, and that is what makes fitting
 * reversible. Recording the view meant the way back computed a different factor from the way out:
 * a 1000×2000 page fitted into a view 100px shorter came back at 0.95, and fitting it back into
 * the full view found a factor of 1.0 and left it there. Every banner that appeared and went cost
 * Abu 5% of his handwriting permanently, and a rotation there and back left it at a quarter size
 * in the corner.
 */
private fun InkDrawing.scaledTo(widthPx: Int, heightPx: Int): InkDrawing {
    val factor = min(widthPx.toFloat() / this.widthPx, heightPx.toFloat() / this.heightPx)
    return InkDrawing(
        widthPx = (this.widthPx * factor).roundToInt().coerceAtLeast(1),
        heightPx = (this.heightPx * factor).roundToInt().coerceAtLeast(1),
        strokes = strokes.map { stroke ->
            InkStroke(
                widthPx = stroke.widthPx * factor,
                points = stroke.points.map { it.copy(x = it.x * factor, y = it.y * factor) },
            )
        },
    )
}
