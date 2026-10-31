package com.rayluxeo.aida.domain.ink

/**
 * A drawing plus everything Abu can still take back.
 *
 * Immutable, and pure Kotlin on purpose: undo is the part of a handwriting canvas that is easiest
 * to get subtly wrong and hardest to notice — a stroke that comes back in the wrong order, a redo
 * stack that survives a new stroke, an erase that undoes one mark instead of the four it took. All
 * of that is decided here, where it can be tested without a stylus, a device or a frame.
 *
 * The canvas owns none of this. It reports what the pen did and draws what it is given.
 */
data class InkHistory(
    val drawing: InkDrawing,
    private val done: List<InkEdit> = emptyList(),
    private val undone: List<InkEdit> = emptyList(),
) {

    val canUndo: Boolean get() = done.isNotEmpty()

    val canRedo: Boolean get() = undone.isNotEmpty()

    val strokeCount: Int get() = drawing.strokes.size

    /**
     * Records a new edit and **discards the redo stack**.
     *
     * That discard is the rule every undo implementation needs and some forget: once Abu has drawn
     * something new, the strokes he had undone are no longer a future he can get back to. Keeping
     * them would let a redo splice an old mark into the middle of new writing.
     */
    fun apply(edit: InkEdit): InkHistory = InkHistory(
        drawing = edit.applyTo(drawing),
        done = done + edit,
        undone = emptyList(),
    )

    fun undo(): InkHistory {
        val last = done.lastOrNull() ?: return this
        return InkHistory(
            drawing = last.revertIn(drawing),
            done = done.dropLast(1),
            undone = undone + last,
        )
    }

    fun redo(): InkHistory {
        val next = undone.lastOrNull() ?: return this
        return InkHistory(
            drawing = next.applyTo(drawing),
            done = done + next,
            undone = undone.dropLast(1),
        )
    }

    /**
     * Empties the page in ONE undoable step.
     *
     * Clear is the most destructive thing on the canvas and the easiest to hit by accident, so it
     * has to be recoverable. Modelled as an erase of everything rather than a new kind of edit,
     * which means undo already knows how to put it back in the right order.
     */
    fun clear(): InkHistory {
        if (drawing.isEmpty) return this
        val removed = drawing.strokes.mapIndexed { index, stroke -> InkEdit.IndexedStroke(index, stroke) }
        return apply(InkEdit.Erase(removed))
    }

    companion object {
        fun of(drawing: InkDrawing): InkHistory = InkHistory(drawing)
    }
}

private fun InkEdit.applyTo(drawing: InkDrawing): InkDrawing = when (this) {
    is InkEdit.Add -> drawing.copy(strokes = drawing.strokes + stroke)
    is InkEdit.Erase -> {
        val gone = removed.map { it.stroke }
        // Compared by identity of position, not by value: two strokes CAN be equal — a dotted i
        // twice over — and removing "the equal one" would take the wrong mark off the page.
        val keep = drawing.strokes.filterIndexed { index, _ -> removed.none { it.index == index } }
        drawing.copy(strokes = if (gone.isEmpty()) drawing.strokes else keep)
    }
}

/**
 * Puts an edit back.
 *
 * An erase is reverted by re-inserting each stroke at the index it held, lowest first, so the
 * drawing order is restored exactly. Appending instead would put a mark that was underneath on
 * top, which on overlapping handwriting is visible.
 */
private fun InkEdit.revertIn(drawing: InkDrawing): InkDrawing = when (this) {
    is InkEdit.Add -> drawing.copy(strokes = drawing.strokes.dropLast(1))
    is InkEdit.Erase -> {
        val restored = drawing.strokes.toMutableList()
        removed.sortedBy { it.index }.forEach { (index, stroke) ->
            restored.add(index.coerceIn(0, restored.size), stroke)
        }
        drawing.copy(strokes = restored)
    }
}
