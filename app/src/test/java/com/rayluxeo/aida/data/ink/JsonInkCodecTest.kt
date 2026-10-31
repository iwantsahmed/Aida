package com.rayluxeo.aida.data.ink

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.domain.ink.InkDrawing
import com.rayluxeo.aida.domain.ink.InkPoint
import com.rayluxeo.aida.domain.ink.InkStroke
import org.junit.Test

/**
 * The format is the only thing standing between Abu's handwriting and a file he cannot open again,
 * so these cover both halves of that: what a page must survive being written down, and what a
 * damaged file must never be able to do to the app reading it.
 */
class JsonInkCodecTest {

    private val codec = JsonInkCodec()

    @Test
    fun `a page survives being written down and read back`() {
        val drawing = InkDrawing(
            widthPx = 1440,
            heightPx = 3120,
            strokes = listOf(
                InkStroke(
                    widthPx = 3.5f,
                    points = listOf(
                        InkPoint(x = 100.5f, y = 200.25f, pressure = 0.5f, tiltRadians = 0.25f),
                        InkPoint(
                            x = 101.75f,
                            y = 202.5f,
                            pressure = 0.75f,
                            tiltRadians = 0.5f,
                            elapsedMillis = 16L,
                        ),
                    ),
                ),
                InkStroke(
                    widthPx = 8f,
                    points = listOf(InkPoint(x = 12.25f, y = 48.5f, elapsedMillis = 900L)),
                ),
            ),
        )

        val decoded = codec.decode(codec.encode(drawing)).value()

        assertThat(decoded).isEqualTo(drawing)
    }

    @Test
    fun `pressure and tilt come back as they went in`() {
        val stroke = InkStroke(
            widthPx = 2f,
            points = listOf(
                InkPoint(x = 1f, y = 1f, pressure = 0.125f, tiltRadians = 0.875f),
                InkPoint(x = 2f, y = 2f, pressure = 1f, tiltRadians = 0.5f),
            ),
        )

        val decoded = codec.decode(codec.encode(page(stroke))).value()

        val points = decoded.strokes.single().points
        assertThat(points[0].pressure).isEqualTo(0.125f)
        assertThat(points[0].tiltRadians).isEqualTo(0.875f)
        assertThat(points[1].pressure).isEqualTo(1f)
        assertThat(points[1].tiltRadians).isEqualTo(0.5f)
    }

    @Test
    fun `a tilt the digitiser never reported stays absent and does not become upright`() {
        val stroke = InkStroke(
            widthPx = 2f,
            points = listOf(
                InkPoint(x = 1f, y = 1f, tiltRadians = null),
                InkPoint(x = 2f, y = 2f, tiltRadians = 0f),
                InkPoint(x = 3f, y = 3f, tiltRadians = null),
            ),
        )

        val decoded = codec.decode(codec.encode(page(stroke))).value()

        val points = decoded.strokes.single().points
        assertThat(points[0].tiltRadians).isNull()
        assertThat(points[1].tiltRadians).isEqualTo(0f)
        assertThat(points[2].tiltRadians).isNull()
    }

    @Test
    fun `a page with no tilt at all leaves the tilt out rather than writing a column of nulls`() {
        val stroke = InkStroke(
            widthPx = 2f,
            points = listOf(InkPoint(x = 1f, y = 1f), InkPoint(x = 2f, y = 2f)),
        )

        val encoded = codec.encode(page(stroke))

        assertThat(encoded).doesNotContain("\"t\":")
        assertThat(encoded).doesNotContain("\"p\":")
        assertThat(encoded).doesNotContain("\"dt\":")
        assertThat(codec.decode(encoded).value().strokes.single().points.first().tiltRadians).isNull()
    }

    @Test
    fun `timings come back as the moments they were, not as the gaps they are stored as`() {
        val stroke = InkStroke(
            widthPx = 2f,
            points = listOf(
                InkPoint(x = 1f, y = 1f, elapsedMillis = 4_300L),
                InkPoint(x = 2f, y = 2f, elapsedMillis = 4_316L),
                InkPoint(x = 3f, y = 3f, elapsedMillis = 5_000L),
            ),
        )

        val decoded = codec.decode(codec.encode(page(stroke))).value()

        assertThat(decoded.strokes.single().points.map { it.elapsedMillis })
            .containsExactly(4_300L, 4_316L, 5_000L)
            .inOrder()
    }

    @Test
    fun `a coordinate is kept to well inside a pixel`() {
        val stroke = InkStroke(
            widthPx = 2f,
            points = listOf(InkPoint(x = 12.3456789f, y = 987.654321f)),
        )

        val decoded = codec.decode(codec.encode(page(stroke))).value()

        val point = decoded.strokes.single().points.single()
        assertThat(point.x).isWithin(0.01f).of(12.3456789f)
        assertThat(point.y).isWithin(0.01f).of(987.654321f)
    }

    @Test
    fun `the stored form carries the version it was written by`() {
        val encoded = codec.encode(page(InkStroke(widthPx = 2f, points = listOf(InkPoint(1f, 1f)))))

        assertThat(encoded).contains("\"v\":1")
    }

    @Test
    fun `a page written by a version this build does not know is refused`() {
        val fromTheFuture = """{"v":99,"w":100,"h":100,"s":[{"w":2.0,"x":[1.0],"y":[1.0]}]}"""

        assertThat(codec.decode(fromTheFuture).errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `nothing a damaged file can hold comes back as an exception`() {
        val damaged = listOf(
            "",
            "   ",
            "not json at all",
            "{",
            "[]",
            "{}",
            """{"v":1}""",
            """{"v":1,"w":100,"h":100}""",
            """{"v":1,"w":100,"h":100,"s":"three strokes"}""",
            """{"v":1,"w":100,"h":100,"s":[{"w":2.0,"x":[],"y":[]}]}""",
            """{"v":1,"w":100,"h":100,"s":[{"w":2.0,"x":[1.0,2.0],"y":[1.0]}]}""",
            """{"v":1,"w":100,"h":100,"s":[{"w":2.0,"x":[1.0],"y":[1.0],"p":[0.5,0.5]}]}""",
            """{"v":1,"w":100,"h":100,"s":[{"w":2.0,"x":[1.0],"y":[1.0],"t":[0.1,0.2]}]}""",
            """{"v":1,"w":100,"h":100,"s":[{"w":2.0,"x":[1.0],"y":[1.0],"dt":[0,16]}]}""",
            """{"v":1,"w":100,"h":100,"s":[{"w":0.0,"x":[1.0],"y":[1.0]}]}""",
            """{"v":1,"w":100,"h":100,"s":[{"w":-2.0,"x":[1.0],"y":[1.0]}]}""",
            """{"v":1,"w":0,"h":100,"s":[]}""",
            """{"v":1,"w":100,"h":-5,"s":[]}""",
            """{"v":1,"w":100,"h":100,"s":[{"w":2.0,"x":[1e39],"y":[1.0]}]}""",
        )

        for (file in damaged) {
            assertThat(codec.decode(file).errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
        }
    }

    @Test
    fun `an unknown field written by a later build does not stop an otherwise readable page`() {
        val withExtra = """{"v":1,"w":100,"h":100,"s":[{"w":2.0,"x":[1.0],"y":[1.0],"colour":"red"}]}"""

        assertThat(codec.decode(withExtra).value().strokes).hasSize(1)
    }

    @Test
    fun `a full page of handwriting round trips and stays compact`() {
        val strokes = (0 until 200).map { strokeIndex ->
            InkStroke(
                widthPx = 3f,
                points = (0 until 250).map { pointIndex ->
                    InkPoint(
                        x = strokeIndex + pointIndex * 0.5f,
                        y = strokeIndex + pointIndex * 0.25f,
                        pressure = 0.5f,
                        elapsedMillis = (strokeIndex * 250L + pointIndex) * 8L,
                    )
                },
            )
        }
        val drawing = InkDrawing(widthPx = 1440, heightPx = 3120, strokes = strokes)

        val encoded = codec.encode(drawing)

        assertThat(codec.decode(encoded).value()).isEqualTo(drawing)
        // An object per point costs upwards of thirty characters in keys and punctuation alone;
        // parallel arrays have to stay well under that or they are not worth the shape.
        assertThat(encoded.length).isLessThan(50_000 * 24)
    }

    @Test
    fun `a nib width that cannot be drawn is written down as a usable one rather than thrown`() {
        // Encoding has no failure channel: a number strict JSON cannot hold would throw inside the
        // serializer and take the page Abu was saving with it. Nothing forbids one reaching here.
        val undrawable = listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0f, -3f)

        for (nib in undrawable) {
            val stroke = InkStroke(widthPx = nib, points = listOf(InkPoint(x = 4f, y = 8f)))

            val decoded = codec.decode(codec.encode(page(stroke))).value()

            assertThat(decoded.strokes.single().widthPx).isEqualTo(1f)
            assertThat(decoded.strokes.single().points.single().x).isEqualTo(4f)
        }
    }

    @Test
    fun `a sample the digitiser could not describe becomes a mark at the origin, not a lost page`() {
        val stroke = InkStroke(
            widthPx = 2f,
            points = listOf(
                InkPoint(
                    x = Float.NaN,
                    y = Float.POSITIVE_INFINITY,
                    pressure = Float.NaN,
                    tiltRadians = Float.NEGATIVE_INFINITY,
                ),
                InkPoint(x = 12.5f, y = 24.25f, pressure = 0.5f, tiltRadians = 0.25f),
            ),
        )

        val decoded = codec.decode(codec.encode(page(stroke))).value()

        val points = decoded.strokes.single().points
        assertThat(points[0].x).isEqualTo(0f)
        assertThat(points[0].y).isEqualTo(0f)
        assertThat(points[0].pressure).isEqualTo(0f)
        assertThat(points[0].tiltRadians).isEqualTo(0f)
        assertThat(points[1].x).isEqualTo(12.5f)
        assertThat(points[1].pressure).isEqualTo(0.5f)
    }

    private fun page(vararg strokes: InkStroke): InkDrawing =
        InkDrawing(widthPx = 1440, heightPx = 3120, strokes = strokes.toList())

    private fun AppResult<InkDrawing>.value(): InkDrawing {
        assertThat(this).isInstanceOf(AppResult.Success::class.java)
        return (this as AppResult.Success).value
    }

    private fun AppResult<InkDrawing>.errorCode(): MiraErrorCode {
        assertThat(this).isInstanceOf(AppResult.Failure::class.java)
        return (this as AppResult.Failure).error.code
    }
}
