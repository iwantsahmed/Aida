package com.rayluxeo.aida.data.ink

import android.app.Application
import android.content.Context
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.design.MiraPalette
import com.rayluxeo.aida.data.TestDispatcherProvider
import com.rayluxeo.aida.domain.ink.InkDrawing
import com.rayluxeo.aida.domain.ink.InkPoint
import com.rayluxeo.aida.domain.ink.InkStroke
import java.io.File
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What can honestly be checked without a screen: which pages are refused, where the file lands,
 * what the export is allowed to do to files that were already there, and that the reported size is
 * the file's own.
 *
 * **The pixels are deliberately not asserted here.** A local JVM test draws through Robolectric's
 * shadows, so an assertion about what the exported image looks like would be an assertion about
 * the shadow. Anti-aliasing and the drawn geometry are verifiable only on a device or an emulator.
 * The pressure-to-width rule is not among them: it is `strokeWidthAt` in `domain/ink`, tested on
 * its own numbers there, and this file draws with that one function rather than a copy of it —
 * which is the whole reason the export and the canvas cannot disagree again.
 *
 * Two failure branches are reachable in production and not from here: an encoder that returns
 * false, and a bitmap allocation that runs out of memory. Neither can be provoked through
 * Robolectric without a shadow of its own, so they belong in an instrumented test rather than in a
 * local one that pretends to cover them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AndroidInkRasterizerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val rasterizer = AndroidInkRasterizer(
        context,
        TestDispatcherProvider(UnconfinedTestDispatcher()),
    )

    @Test
    fun `a page with nothing on it is refused rather than exported as a blank image`() = runTest {
        val blank = InkDrawing.blank(widthPx = 800, heightPx = 600)

        val result = rasterizer.renderPng(blank, "ink/empty.png")

        assertThat(result.errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(File(context.filesDir, "ink/empty.png").exists()).isFalse()
    }

    @Test
    fun `a page with no size is refused`() = runTest {
        val sizeless = InkDrawing(widthPx = 0, heightPx = 600, strokes = listOf(stroke()))

        assertThat(rasterizer.renderPng(sizeless, "ink/sizeless.png").errorCode())
            .isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `a path that climbs out of Mira's own files is refused`() = runTest {
        val escaped = File(context.filesDir.parentFile, "escaped.png")

        val result = rasterizer.renderPng(page(), "../escaped.png")

        assertThat(result.errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(escaped.exists()).isFalse()
    }

    @Test
    fun `a path with nothing in it is refused`() = runTest {
        assertThat(rasterizer.renderPng(page(), "   ").errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `an exported page lands inside Mira's files and reports the bytes it actually holds`() =
        runTest {
            val result = rasterizer.renderPng(page(), "ink/1/page.png")

            val written = File(context.filesDir, "ink/1/page.png")
            assertThat(written.exists()).isTrue()
            assertThat(result.value()).isGreaterThan(0L)
            assertThat(result.value()).isEqualTo(written.readBytes().size.toLong())
        }

    @Test
    fun `a path that already holds a file is refused, and that file is left exactly as it was`() =
        runTest {
            val taken = File(context.filesDir, "ink/taken.png")
            taken.parentFile?.mkdirs()
            taken.writeBytes(ALREADY_THERE)

            val result = rasterizer.renderPng(page(), "ink/taken.png")

            assertThat(result.errorCode()).isEqualTo(MiraErrorCode.STORAGE)
            assertThat(taken.readBytes()).isEqualTo(ALREADY_THERE)
        }

    @Test
    fun `an export that cannot be written does not take away a file it never created`() = runTest {
        // A regular file where the export wants a directory: nothing can be written beneath it.
        val blocking = File(context.filesDir, "blocked")
        blocking.writeBytes(ALREADY_THERE)

        val result = rasterizer.renderPng(page(), "blocked/page.png")

        assertThat(result.errorCode()).isEqualTo(MiraErrorCode.STORAGE)
        assertThat(blocking.readBytes()).isEqualTo(ALREADY_THERE)
    }

    @Test
    fun `the page and the ink are the same obsidian and platinum the rest of Mira is`() {
        // The data layer may not read `MiraPalette` (AGENTS.md section 4), so it holds its own two
        // ARGB values. This is what stops the pair drifting into a second, slightly different Mira.
        assertThat(INK_PAGE_ARGB).isEqualTo(MiraPalette.Obsidian.toArgb())
        assertThat(INK_ARGB).isEqualTo(MiraPalette.Platinum.toArgb())
    }

    private fun page(): InkDrawing =
        InkDrawing(widthPx = 800, heightPx = 600, strokes = listOf(stroke()))

    private fun stroke(): InkStroke = InkStroke(
        widthPx = 4f,
        points = listOf(
            InkPoint(x = 10f, y = 10f, pressure = 0.2f),
            InkPoint(x = 120f, y = 80f, pressure = 0.9f),
            InkPoint(x = 300f, y = 240f, pressure = 0.5f),
        ),
    )

    private fun AppResult<Long>.value(): Long {
        assertThat(this).isInstanceOf(AppResult.Success::class.java)
        return (this as AppResult.Success).value
    }

    private fun AppResult<Long>.errorCode(): MiraErrorCode {
        assertThat(this).isInstanceOf(AppResult.Failure::class.java)
        return (this as AppResult.Failure).error.code
    }

    private companion object {
        /** Recognisable bytes, so a test failure says the file changed rather than just its size. */
        val ALREADY_THERE = byteArrayOf(7, 7, 7, 7)
    }
}
