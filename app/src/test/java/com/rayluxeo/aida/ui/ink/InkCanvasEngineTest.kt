package com.rayluxeo.aida.ui.ink

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.domain.ink.InkDrawing
import com.rayluxeo.aida.domain.ink.InkPoint
import com.rayluxeo.aida.domain.ink.InkStroke
import com.rayluxeo.aida.domain.ink.InkTool
import org.junit.Test

private const val PAGE_WIDTH = 1_000
private const val PAGE_HEIGHT = 2_000

/** A fit and its reverse are two float multiplications, so a pixel either way is not a defect. */
private const val TOLERANCE = 0.01f

/**
 * The canvas's brain, exercised without a stylus.
 *
 * Everything asserted here is something Abu would notice and could not report precisely: a stroke
 * that came out angular because samples were dropped, ink in his note that his hand never made,
 * an eraser sweep that needs four undos, a palm that scribbled over a word. Each one is a rule
 * the engine exists to keep, so each one has a test that fails loudly if it stops keeping it.
 */
class InkCanvasEngineTest {

    private fun engine(): InkCanvasEngine =
        InkCanvasEngine().apply { resize(PAGE_WIDTH, PAGE_HEIGHT) }

    private fun sample(x: Float, y: Float, pressure: Float = 1f, at: Long = 0L) =
        InkSample(x = x, y = y, pressure = pressure, timestampMillis = at)

    private fun motion(phase: InkPhase, tool: InkToolType, vararg samples: InkSample) =
        InkMotion(phase = phase, toolType = tool, samples = samples.toList())

    private fun InkCanvasEngine.writeLine(y: Float, tool: InkToolType = InkToolType.STYLUS) {
        onMotion(motion(InkPhase.DOWN, tool, sample(100f, y)))
        onMotion(motion(InkPhase.MOVE, tool, sample(250f, y)))
        onMotion(motion(InkPhase.UP, tool, sample(400f, y)))
    }

    @Test
    fun `every batched sample lands in the stroke, in the order it was written`() {
        val engine = engine()

        engine.onMotion(motion(InkPhase.DOWN, InkToolType.STYLUS, sample(1f, 1f, at = 0L)))
        engine.onMotion(
            motion(
                InkPhase.MOVE,
                InkToolType.STYLUS,
                // The three Android batched between frames, plus the current point. Reading only
                // the last of these is what makes handwriting look faceted.
                sample(2f, 2f, at = 8L),
                sample(3f, 3f, at = 16L),
                sample(4f, 4f, at = 24L),
            ),
        )
        engine.onMotion(motion(InkPhase.UP, InkToolType.STYLUS, sample(5f, 5f, at = 32L)))

        val points = engine.drawing.strokes.single().points
        assertThat(points.map { it.x }).containsExactly(1f, 2f, 3f, 4f, 5f).inOrder()
        assertThat(points.map { it.elapsedMillis }).containsExactly(0L, 8L, 16L, 24L, 32L).inOrder()
    }

    @Test
    fun `a predicted point is drawn ahead but never reaches the page`() {
        val engine = engine()

        engine.onMotion(motion(InkPhase.DOWN, InkToolType.STYLUS, sample(1f, 1f)))
        engine.predict(listOf(sample(500f, 500f), sample(600f, 600f)))
        assertThat(engine.predictedTail).hasSize(2)

        engine.onMotion(motion(InkPhase.MOVE, InkToolType.STYLUS, sample(2f, 2f)))
        engine.onMotion(motion(InkPhase.UP, InkToolType.STYLUS, sample(3f, 3f)))

        assertThat(engine.drawing.strokes.single().points.map { it.x })
            .containsExactly(1f, 2f, 3f).inOrder()
        assertThat(engine.predictedTail).isEmpty()
    }

    @Test
    fun `a prediction is refused when nothing is being drawn`() {
        val engine = engine()

        engine.predict(listOf(sample(10f, 10f)))

        assertThat(engine.predictedTail).isEmpty()
    }

    @Test
    fun `a finger leaves nothing on the page while finger drawing is off`() {
        val engine = engine()
        engine.fingerDrawingEnabled = false

        engine.writeLine(y = 100f, tool = InkToolType.FINGER)

        assertThat(engine.drawing.strokes).isEmpty()
        assertThat(engine.canUndo).isFalse()
    }

    @Test
    fun `a finger draws once Abu has turned it on`() {
        val engine = engine()
        engine.fingerDrawingEnabled = true

        engine.writeLine(y = 100f, tool = InkToolType.FINGER)

        assertThat(engine.drawing.strokes).hasSize(1)
    }

    @Test
    fun `a finger touching down beside the pen does not end the pen's stroke`() {
        val engine = engine()
        engine.fingerDrawingEnabled = true

        engine.onMotion(motion(InkPhase.DOWN, InkToolType.STYLUS, sample(10f, 10f)))
        engine.onMotion(motion(InkPhase.DOWN, InkToolType.FINGER, sample(900f, 900f)))
        engine.onMotion(motion(InkPhase.MOVE, InkToolType.STYLUS, sample(20f, 20f)))
        engine.onMotion(motion(InkPhase.UP, InkToolType.STYLUS, sample(30f, 30f)))

        val points = engine.drawing.strokes.single().points
        assertThat(points.map { it.x }).containsExactly(10f, 20f, 30f).inOrder()
    }

    @Test
    fun `a palm landing beside the pen is refused, and says so`() {
        val engine = engine()
        engine.fingerDrawingEnabled = true

        assertThat(engine.onMotion(motion(InkPhase.DOWN, InkToolType.STYLUS, sample(10f, 10f))))
            .isTrue()
        // The view claims the pointer it reads the rest of the gesture from on this answer and
        // on nothing else. Asking whether a gesture is in flight instead answers yes — the PEN's
        // is — and the palm became the tracked contact.
        assertThat(engine.onMotion(motion(InkPhase.DOWN, InkToolType.FINGER, sample(900f, 900f))))
            .isFalse()
    }

    @Test
    fun `the palm's own movement never reaches the pen's stroke`() {
        val engine = engine()
        engine.fingerDrawingEnabled = true

        engine.onMotion(motion(InkPhase.DOWN, InkToolType.STYLUS, sample(10f, 10f)))
        engine.onMotion(motion(InkPhase.DOWN, InkToolType.FINGER, sample(900f, 900f)))
        // Exactly what a view that had claimed the palm would send. The engine used to switch on
        // the active TOOL alone, so the heel of Abu's hand was appended to the word he was in
        // the middle of and the rest of the pen stroke was dropped.
        engine.onMotion(motion(InkPhase.MOVE, InkToolType.FINGER, sample(905f, 905f)))
        engine.onMotion(motion(InkPhase.UP, InkToolType.FINGER, sample(910f, 910f)))
        engine.onMotion(motion(InkPhase.MOVE, InkToolType.STYLUS, sample(20f, 20f)))
        engine.onMotion(motion(InkPhase.UP, InkToolType.STYLUS, sample(30f, 30f)))

        val points = engine.drawing.strokes.single().points
        assertThat(points.map { it.x }).containsExactly(10f, 20f, 30f).inOrder()
    }

    @Test
    fun `a palm that rested through a stroke leaves finger drawing working`() {
        val engine = engine()
        engine.fingerDrawingEnabled = true

        engine.onMotion(motion(InkPhase.DOWN, InkToolType.STYLUS, sample(10f, 10f)))
        engine.onMotion(motion(InkPhase.DOWN, InkToolType.FINGER, sample(900f, 900f)))
        engine.onMotion(motion(InkPhase.UP, InkToolType.FINGER, sample(900f, 900f)))
        engine.onMotion(motion(InkPhase.UP, InkToolType.STYLUS, sample(30f, 30f)))

        engine.writeLine(y = 500f, tool = InkToolType.FINGER)

        // The pen is off the glass, so a finger may draw. The flag saying otherwise used to be
        // cleared by whichever contact happened to lift last, and once it stuck on, finger
        // drawing was dead for the life of the view model however often Abu toggled it.
        assertThat(engine.drawing.strokes).hasSize(2)
    }

    @Test
    fun `a cancel frees the pen even when the event names another implement`() {
        val engine = engine()
        engine.fingerDrawingEnabled = true

        engine.onMotion(motion(InkPhase.DOWN, InkToolType.STYLUS, sample(10f, 10f)))
        // A cancel is read from whichever pointer index survives, which need not be the pen's.
        engine.onMotion(motion(InkPhase.CANCEL, InkToolType.FINGER, sample(10f, 10f)))

        engine.writeLine(y = 500f, tool = InkToolType.FINGER)

        assertThat(engine.drawing.strokes).hasSize(1)
    }

    @Test
    fun `the pen takes over from a finger and the finger's mark is thrown away`() {
        val engine = engine()
        engine.fingerDrawingEnabled = true

        engine.onMotion(motion(InkPhase.DOWN, InkToolType.FINGER, sample(900f, 900f)))
        engine.onMotion(motion(InkPhase.MOVE, InkToolType.FINGER, sample(910f, 910f)))
        engine.onMotion(motion(InkPhase.DOWN, InkToolType.STYLUS, sample(10f, 10f)))
        engine.onMotion(motion(InkPhase.UP, InkToolType.STYLUS, sample(20f, 20f)))

        val points = engine.drawing.strokes.single().points
        assertThat(points.map { it.x }).containsExactly(10f, 20f).inOrder()
    }

    @Test
    fun `a reversed pen erases while the pen is still the chosen tool`() {
        val engine = engine()
        engine.selectedTool = InkTool.PEN
        engine.writeLine(y = 200f)

        engine.onMotion(motion(InkPhase.DOWN, InkToolType.ERASER, sample(250f, 200f)))
        engine.onMotion(motion(InkPhase.UP, InkToolType.ERASER, sample(250f, 200f)))

        assertThat(engine.drawing.strokes).isEmpty()
        assertThat(engine.selectedTool).isEqualTo(InkTool.PEN)
    }

    @Test
    fun `one sweep across four marks is one undo, and puts them all back in order`() {
        val engine = engine()
        listOf(100f, 200f, 300f, 400f).forEach { engine.writeLine(y = it) }
        assertThat(engine.drawing.strokes).hasSize(4)

        engine.onMotion(motion(InkPhase.DOWN, InkToolType.ERASER, sample(250f, 50f)))
        engine.onMotion(motion(InkPhase.MOVE, InkToolType.ERASER, sample(250f, 450f)))
        engine.onMotion(motion(InkPhase.UP, InkToolType.ERASER, sample(250f, 450f)))

        assertThat(engine.drawing.strokes).isEmpty()

        engine.undo()

        assertThat(engine.drawing.strokes.map { it.points.first().y })
            .containsExactly(100f, 200f, 300f, 400f).inOrder()
        // One step back, not four: the sweep is on the stack once.
        assertThat(engine.canRedo).isTrue()
    }

    @Test
    fun `an eraser that touched nothing records no undo step`() {
        val engine = engine()
        engine.writeLine(y = 100f)

        engine.onMotion(motion(InkPhase.DOWN, InkToolType.ERASER, sample(900f, 900f)))
        engine.onMotion(motion(InkPhase.UP, InkToolType.ERASER, sample(950f, 950f)))

        engine.undo()

        assertThat(engine.drawing.strokes).isEmpty()
    }

    @Test
    fun `clearing the page is a single step that undo puts back`() {
        val engine = engine()
        listOf(100f, 200f, 300f).forEach { engine.writeLine(y = it) }

        engine.clear()
        assertThat(engine.drawing.strokes).isEmpty()

        engine.undo()
        assertThat(engine.drawing.strokes).hasSize(3)
    }

    @Test
    fun `new writing discards what had been undone`() {
        val engine = engine()
        engine.writeLine(y = 100f)
        engine.undo()
        assertThat(engine.canRedo).isTrue()

        engine.writeLine(y = 200f)

        assertThat(engine.canRedo).isFalse()
        assertThat(engine.drawing.strokes).hasSize(1)
    }

    @Test
    fun `a page is clean once saved and asks again as soon as it is written on`() {
        val engine = engine()
        engine.writeLine(y = 100f)
        assertThat(engine.hasUnsavedWork).isTrue()

        engine.markSaved(engine.revisionMark())
        assertThat(engine.hasUnsavedWork).isFalse()

        engine.writeLine(y = 200f)
        assertThat(engine.hasUnsavedWork).isTrue()
    }

    @Test
    fun `a word written while the page was being saved still counts as unsaved`() {
        val engine = engine()
        engine.writeLine(y = 100f)
        val mark = engine.revisionMark()

        engine.writeLine(y = 200f)
        engine.markSaved(mark)

        assertThat(engine.hasUnsavedWork).isTrue()
    }

    @Test
    fun `a page read back on a narrower screen keeps its proportions`() {
        val engine = engine()
        engine.restore(
            InkDrawing(
                widthPx = PAGE_WIDTH,
                heightPx = PAGE_HEIGHT,
                strokes = listOf(
                    InkStroke(
                        widthPx = 10f,
                        points = listOf(InkPoint(100f, 200f), InkPoint(500f, 600f)),
                    ),
                ),
            ),
        )

        engine.resize(PAGE_WIDTH / 2, PAGE_HEIGHT / 2)

        val stroke = engine.drawing.strokes.single()
        assertThat(stroke.points.map { it.x }).containsExactly(50f, 250f).inOrder()
        assertThat(stroke.points.map { it.y }).containsExactly(100f, 300f).inOrder()
        assertThat(stroke.widthPx).isEqualTo(5f)
    }

    @Test
    fun `a reopened page starts clean and replays exactly what was stored`() {
        val engine = engine()
        val stored = InkDrawing(
            widthPx = PAGE_WIDTH,
            heightPx = PAGE_HEIGHT,
            strokes = listOf(
                InkStroke(widthPx = 4f, points = listOf(InkPoint(1f, 2f), InkPoint(3f, 4f))),
                InkStroke(widthPx = 4f, points = listOf(InkPoint(5f, 6f))),
            ),
        )

        engine.restore(stored)

        assertThat(engine.drawing).isEqualTo(stored)
        assertThat(engine.hasUnsavedWork).isFalse()
        assertThat(engine.canUndo).isFalse()
    }

    @Test
    fun `writing added to a reopened page carries on its clock rather than restarting it`() {
        val engine = engine()
        engine.restore(
            InkDrawing(
                widthPx = PAGE_WIDTH,
                heightPx = PAGE_HEIGHT,
                strokes = listOf(
                    InkStroke(
                        widthPx = 4f,
                        points = listOf(InkPoint(1f, 2f, elapsedMillis = 900L)),
                    ),
                ),
            ),
        )

        engine.onMotion(motion(InkPhase.DOWN, InkToolType.STYLUS, sample(10f, 10f, at = 5_000L)))
        engine.onMotion(motion(InkPhase.UP, InkToolType.STYLUS, sample(20f, 20f, at = 5_040L)))

        val added = engine.drawing.strokes.last().points
        assertThat(added.map { it.elapsedMillis }).containsExactly(900L, 940L).inOrder()
    }

    @Test
    fun `a cancelled gesture leaves nothing behind`() {
        val engine = engine()

        engine.onMotion(motion(InkPhase.DOWN, InkToolType.STYLUS, sample(10f, 10f)))
        engine.onMotion(motion(InkPhase.MOVE, InkToolType.STYLUS, sample(20f, 20f)))
        engine.onMotion(motion(InkPhase.CANCEL, InkToolType.STYLUS, sample(20f, 20f)))

        assertThat(engine.drawing.strokes).isEmpty()
        assertThat(engine.liveStroke).isNull()
        assertThat(engine.activeTool).isNull()
        assertThat(engine.hasUnsavedWork).isFalse()
    }

    @Test
    fun `a banner appearing and going leaves the writing exactly where it was`() {
        val engine = engine()
        engine.writeLine(y = 400f)
        val before = engine.drawing.strokes.single().points.map { it.x to it.y }

        // A notice is about this tall. Fitting recorded the VIEW's size rather than the writing's,
        // so the way back found a different factor from the way out: the page came back 5%
        // smaller and never grew again, and every save showed a notice.
        engine.resize(PAGE_WIDTH, PAGE_HEIGHT - 100)
        engine.resize(PAGE_WIDTH, PAGE_HEIGHT)

        val after = engine.drawing.strokes.single().points.map { it.x to it.y }
        assertThat(after).hasSize(before.size)
        before.zip(after).forEach { (was, now) ->
            assertThat(now.first).isWithin(TOLERANCE).of(was.first)
            assertThat(now.second).isWithin(TOLERANCE).of(was.second)
        }
    }

    @Test
    fun `rotating the phone and rotating it back leaves the page as it was`() {
        val engine = engine()
        engine.writeLine(y = 400f)
        val before = engine.drawing.strokes.single()

        engine.resize(PAGE_HEIGHT, PAGE_WIDTH)
        engine.resize(PAGE_WIDTH, PAGE_HEIGHT)

        // Portrait to landscape is a half; landscape back to portrait used to be a half again,
        // which left Abu's handwriting at a quarter size in the corner.
        val after = engine.drawing.strokes.single()
        after.points.zip(before.points).forEach { (now, was) ->
            assertThat(now.x).isWithin(TOLERANCE).of(was.x)
            assertThat(now.y).isWithin(TOLERANCE).of(was.y)
        }
        assertThat(after.widthPx).isWithin(TOLERANCE).of(before.widthPx)
    }

    @Test
    fun `a measure at the size the page is already fitted to keeps undo`() {
        val engine = engine()
        engine.writeLine(y = 100f)
        engine.resize(PAGE_WIDTH, PAGE_HEIGHT - 100)
        engine.writeLine(y = 200f)

        engine.resize(PAGE_WIDTH, PAGE_HEIGHT - 100)

        // Undo cannot survive a fit that moves the writing, and this one does not move it — the
        // page was measured at exactly this size already. The fitted page now records the box
        // its writing occupies rather than the glass, so "same size" has to be asked of the view
        // it was fitted to; asking the drawing re-fits on every measure pass and Abu loses undo
        // to a layout he never asked for.
        assertThat(engine.canUndo).isTrue()
    }

    @Test
    fun `pressure is carried per point rather than per stroke`() {
        val engine = engine()

        engine.onMotion(
            motion(InkPhase.DOWN, InkToolType.STYLUS, sample(10f, 10f, pressure = 0.2f)),
        )
        engine.onMotion(
            motion(InkPhase.UP, InkToolType.STYLUS, sample(20f, 20f, pressure = 0.9f)),
        )

        assertThat(engine.drawing.strokes.single().points.map { it.pressure })
            .containsExactly(0.2f, 0.9f).inOrder()
    }
}
