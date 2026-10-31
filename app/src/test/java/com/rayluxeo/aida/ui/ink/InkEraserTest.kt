package com.rayluxeo.aida.ui.ink

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.domain.ink.InkDrawing
import com.rayluxeo.aida.domain.ink.InkPoint
import com.rayluxeo.aida.domain.ink.InkStroke
import org.junit.Test

/**
 * The eraser's reach, which is the part of it Abu feels.
 *
 * The case that matters is the flick: between two samples the eraser can travel much further than
 * the nib is wide, and an implementation that only asks "what is under the eraser now" passes
 * straight through a line and leaves it standing. That reads as an eraser that does not work.
 */
class InkEraserTest {

    private fun page(vararg strokes: InkStroke) = InkDrawing(1_000, 2_000, strokes.toList())

    private fun horizontal(y: Float, widthPx: Float = 4f) = InkStroke(
        widthPx = widthPx,
        points = listOf(InkPoint(100f, y), InkPoint(400f, y)),
    )

    @Test
    fun `a mark under the eraser is taken`() {
        val drawing = page(horizontal(y = 200f))

        val hit = InkEraser.strokesTouching(
            drawing = drawing,
            fromX = 250f,
            fromY = 200f,
            toX = 250f,
            toY = 200f,
            radiusPx = 18f,
        )

        assertThat(hit).containsExactly(0)
    }

    @Test
    fun `a mark the eraser never came near is left alone`() {
        val drawing = page(horizontal(y = 200f))

        val hit = InkEraser.strokesTouching(
            drawing = drawing,
            fromX = 800f,
            fromY = 800f,
            toX = 820f,
            toY = 820f,
            radiusPx = 18f,
        )

        assertThat(hit).isEmpty()
    }

    @Test
    fun `a flick that jumps clean over a line still takes it`() {
        val drawing = page(horizontal(y = 200f))

        val hit = InkEraser.strokesTouching(
            drawing = drawing,
            fromX = 250f,
            fromY = 40f,
            // Four hundred pixels in one sample: far more than the eraser is wide, which is what
            // a point test would miss.
            toX = 250f,
            toY = 440f,
            radiusPx = 18f,
        )

        assertThat(hit).containsExactly(0)
    }

    @Test
    fun `a sweep takes every mark it crossed, lowest first`() {
        val drawing = page(horizontal(100f), horizontal(200f), horizontal(300f), horizontal(900f))

        val hit = InkEraser.strokesTouching(
            drawing = drawing,
            fromX = 250f,
            fromY = 50f,
            toX = 250f,
            toY = 350f,
            radiusPx = 18f,
        )

        assertThat(hit).containsExactly(0, 1, 2).inOrder()
    }

    @Test
    fun `a thick mark is hit the moment the eraser reaches its edge`() {
        val thin = page(horizontal(y = 200f, widthPx = 2f))
        val thick = page(horizontal(y = 200f, widthPx = 60f))
        // Twenty-five pixels clear of the centre line: outside a hairline's reach, inside a
        // broad nib's, because half the mark's own width counts.
        val away = 25f

        assertThat(
            InkEraser.strokesTouching(thin, 250f, 200f - away, 250f, 200f - away, radiusPx = 18f),
        ).isEmpty()
        assertThat(
            InkEraser.strokesTouching(thick, 250f, 200f - away, 250f, 200f - away, radiusPx = 18f),
        ).containsExactly(0)
    }

    @Test
    fun `a single dot is erasable`() {
        val drawing = page(InkStroke(widthPx = 4f, points = listOf(InkPoint(300f, 300f))))

        val hit = InkEraser.strokesTouching(
            drawing = drawing,
            fromX = 290f,
            fromY = 300f,
            toX = 295f,
            toY = 300f,
            radiusPx = 18f,
        )

        assertThat(hit).containsExactly(0)
    }
}
