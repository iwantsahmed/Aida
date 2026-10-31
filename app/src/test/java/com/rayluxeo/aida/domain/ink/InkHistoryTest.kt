package com.rayluxeo.aida.domain.ink

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Undo is the part of a handwriting canvas Abu would never catch getting it wrong: a stroke that
 * comes back on top of the one it was under, an erase that gives back one mark of the four it
 * took, a redo that splices an old mark into new writing. Each of those has a test here.
 */
class InkHistoryTest {

    private val page = InkDrawing.blank(widthPx = 1440, heightPx = 3120)

    @Test
    fun `a fresh history has nothing to take back`() {
        val history = InkHistory.of(page)

        assertThat(history.canUndo).isFalse()
        assertThat(history.canRedo).isFalse()
        assertThat(history.strokeCount).isEqualTo(0)
    }

    @Test
    fun `undo on an empty history is a no-op rather than a crash`() {
        val history = InkHistory.of(page)

        val undone = history.undo().undo().undo()

        assertThat(undone.drawing).isEqualTo(page)
        assertThat(undone.canUndo).isFalse()
        assertThat(undone.canRedo).isFalse()
    }

    @Test
    fun `redo with nothing undone is a no-op rather than a crash`() {
        val history = InkHistory.of(page).apply(InkEdit.Add(stroke(1f)))

        val redone = history.redo().redo()

        assertThat(redone.drawing).isEqualTo(history.drawing)
        assertThat(redone.canRedo).isFalse()
    }

    @Test
    fun `undo then redo restores the page exactly`() {
        val first = stroke(1f)
        val second = stroke(2f)
        val written = InkHistory.of(page)
            .apply(InkEdit.Add(first))
            .apply(InkEdit.Add(second))

        val undone = written.undo()
        val redone = undone.redo()

        assertThat(undone.drawing.strokes).containsExactly(first).inOrder()
        assertThat(undone.canRedo).isTrue()
        assertThat(redone.drawing).isEqualTo(written.drawing)
        assertThat(redone.canRedo).isFalse()
        assertThat(redone.canUndo).isTrue()
    }

    @Test
    fun `every edit can be walked back to the page Abu started from`() {
        val first = stroke(1f)
        val second = stroke(2f)
        val third = stroke(3f)
        val written = InkHistory.of(page)
            .apply(InkEdit.Add(first))
            .apply(InkEdit.Add(second))
            .apply(InkEdit.Add(third))
            .apply(InkEdit.Erase(listOf(InkEdit.IndexedStroke(1, second))))
            .clear()

        val start = written.undo().undo().undo().undo().undo()

        assertThat(start.drawing).isEqualTo(page)
        assertThat(start.canUndo).isFalse()
    }

    @Test
    fun `a new stroke after an undo discards what could have been redone`() {
        val first = stroke(1f)
        val second = stroke(2f)
        val third = stroke(3f)
        val undone = InkHistory.of(page)
            .apply(InkEdit.Add(first))
            .apply(InkEdit.Add(second))
            .undo()

        val written = undone.apply(InkEdit.Add(third))

        assertThat(undone.canRedo).isTrue()
        assertThat(written.canRedo).isFalse()
        assertThat(written.redo().drawing.strokes).containsExactly(first, third).inOrder()
    }

    @Test
    fun `erasing several crossing strokes at once undoes as one step`() {
        val first = stroke(1f)
        val second = stroke(2f)
        val third = stroke(3f)
        val fourth = stroke(4f)
        val written = InkHistory.of(page.copy(strokes = listOf(first, second, third, fourth)))

        val erased = written.apply(
            InkEdit.Erase(
                listOf(
                    InkEdit.IndexedStroke(1, second),
                    InkEdit.IndexedStroke(2, third),
                ),
            ),
        )
        val restored = erased.undo()

        assertThat(erased.drawing.strokes).containsExactly(first, fourth).inOrder()
        assertThat(restored.drawing.strokes).containsExactly(first, second, third, fourth).inOrder()
        assertThat(restored.canUndo).isFalse()
    }

    @Test
    fun `an erase puts strokes back at the index each one held, not on top`() {
        val first = stroke(1f)
        val second = stroke(2f)
        val third = stroke(3f)
        val written = InkHistory.of(page.copy(strokes = listOf(first, second, third)))

        val restored = written
            .apply(
                InkEdit.Erase(
                    listOf(
                        InkEdit.IndexedStroke(0, first),
                        InkEdit.IndexedStroke(2, third),
                    ),
                ),
            )
            .undo()

        assertThat(restored.drawing.strokes).containsExactly(first, second, third).inOrder()
    }

    @Test
    fun `erasing one of two identical marks takes the one at that index`() {
        val dot = stroke(1f)
        val line = stroke(2f)
        val written = InkHistory.of(page.copy(strokes = listOf(dot, line, dot)))

        val erased = written.apply(InkEdit.Erase(listOf(InkEdit.IndexedStroke(2, dot))))

        assertThat(erased.drawing.strokes).containsExactly(dot, line).inOrder()
        assertThat(erased.undo().drawing.strokes).containsExactly(dot, line, dot).inOrder()
    }

    @Test
    fun `an erase can be redone after it is undone`() {
        val first = stroke(1f)
        val second = stroke(2f)
        val written = InkHistory.of(page.copy(strokes = listOf(first, second)))

        val redone = written
            .apply(InkEdit.Erase(listOf(InkEdit.IndexedStroke(0, first))))
            .undo()
            .redo()

        assertThat(redone.drawing.strokes).containsExactly(second).inOrder()
    }

    @Test
    fun `clear empties the page in one undoable step`() {
        val first = stroke(1f)
        val second = stroke(2f)
        val third = stroke(3f)
        val written = InkHistory.of(page.copy(strokes = listOf(first, second, third)))

        val cleared = written.clear()
        val restored = cleared.undo()

        assertThat(cleared.drawing.isEmpty).isTrue()
        assertThat(cleared.canUndo).isTrue()
        assertThat(restored.drawing.strokes).containsExactly(first, second, third).inOrder()
        assertThat(restored.canUndo).isFalse()
    }

    @Test
    fun `clearing a page that is already empty records nothing to undo`() {
        val history = InkHistory.of(page).clear()

        assertThat(history.canUndo).isFalse()
        assertThat(history.drawing).isEqualTo(page)
    }

    @Test
    fun `the stroke count follows the drawing through every edit`() {
        val first = stroke(1f)
        val second = stroke(2f)
        val written = InkHistory.of(page)
            .apply(InkEdit.Add(first))
            .apply(InkEdit.Add(second))

        assertThat(written.strokeCount).isEqualTo(2)
        assertThat(written.undo().strokeCount).isEqualTo(1)
        assertThat(written.clear().strokeCount).isEqualTo(0)
    }

    /** Distinct nib widths, so a stroke restored in the wrong place is visible in an assertion. */
    private fun stroke(nibPx: Float): InkStroke = InkStroke(
        widthPx = nibPx,
        points = listOf(
            InkPoint(x = nibPx, y = nibPx),
            InkPoint(x = nibPx + 10f, y = nibPx + 10f),
        ),
    )
}
