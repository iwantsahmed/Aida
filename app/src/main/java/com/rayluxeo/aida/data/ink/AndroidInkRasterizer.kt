package com.rayluxeo.aida.data.ink

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.ink.InkDrawing
import com.rayluxeo.aida.domain.ink.InkRasterizer
import com.rayluxeo.aida.domain.ink.InkStroke
import com.rayluxeo.aida.domain.ink.strokeWidthAt
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * Turns a page of strokes into a PNG inside Mira's own files directory.
 *
 * **The export is opaque, on Mira's obsidian, in ivory ink.** The alternative — ink on
 * transparency — looks right everywhere Mira draws it and disappears the moment Abu shares it
 * into anything pale, because ivory on white is invisible; picking a mid-tone that survives both
 * backgrounds would mean handwriting that is washed out on every one of them. Carrying its own
 * background costs a few kilobytes of flat colour that PNG compresses to almost nothing, and buys
 * a file that reads the same in a message, a document and a printout.
 *
 * **Stroke width follows per-point pressure, through [strokeWidthAt].** That is the same function
 * the canvas draws the live stroke with, and sharing it is what makes the claim above true: what
 * Abu sees while he writes is what comes out of here. A uniform line is not handwriting, it is a
 * trace of where the pen went — and an export drawn by a second, separately tuned copy of the
 * rule is not the page he wrote.
 *
 * Size is bounded by [InkExportGeometry] before a single byte is allocated, and the allocation
 * itself is still guarded: a page big enough to fail is a page Abu is told about, not a crash.
 */
@Singleton
class AndroidInkRasterizer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : InkRasterizer {

    override suspend fun renderPng(drawing: InkDrawing, relativePath: String): AppResult<Long> {
        if (drawing.widthPx <= 0 || drawing.heightPx <= 0) {
            return failure(
                MiraErrorCode.VALIDATION,
                "A page of ${drawing.widthPx} by ${drawing.heightPx} pixels cannot be exported",
            )
        }
        if (drawing.isEmpty) {
            // An image of nothing would still register as a file and show as a thumbnail of a
            // blank page, which reads as writing that was lost rather than writing never made.
            return failure(MiraErrorCode.VALIDATION, "There is nothing on the page to export")
        }
        return withContext(dispatchers.io) {
            when (val target = resolve(relativePath)) {
                is AppResult.Failure -> target
                is AppResult.Success -> render(drawing, target.value)
            }
        }
    }

    /**
     * Refuses any path that would land outside Mira's files directory.
     *
     * The path is built by the repository rather than by Abu, so this is a guard and not a
     * validation step he will ever see — but it is the guard that keeps a stored page from ever
     * being able to name somewhere else on the phone.
     */
    private fun resolve(relativePath: String): AppResult<File> = try {
        val root = context.filesDir.canonicalFile
        val target = File(root, relativePath).canonicalFile
        if (relativePath.isBlank() || !target.path.startsWith(root.path + File.separator)) {
            failure(MiraErrorCode.VALIDATION, "$relativePath is not a path inside Mira's own files")
        } else {
            AppResult.Success(target)
        }
    } catch (unresolvable: IOException) {
        failure(MiraErrorCode.STORAGE, "Could not work out where $relativePath is", unresolvable)
    }

    /**
     * Writes the page, and on any failure takes back only what this call put on disk.
     *
     * The cleanup on each failure branch is why the existing file is refused rather than replaced:
     * opening the stream truncates, so a render that fell over halfway through an existing file
     * would have destroyed it and then deleted the remains. Nothing calls this with a path
     * already taken — the repository mints a fresh name per save — so it costs nothing today, and
     * it means the rule holds for whoever calls this next.
     */
    private fun render(drawing: InkDrawing, target: File): AppResult<Long> {
        if (target.exists()) {
            return failure(
                MiraErrorCode.STORAGE,
                "${target.name} already exists and exporting would write over it",
            )
        }
        return try {
            val size = InkExportGeometry.fit(drawing.widthPx, drawing.heightPx)
            val bitmap = Bitmap.createBitmap(size.widthPx, size.heightPx, Bitmap.Config.ARGB_8888)
            // The page carries its own opaque background, so the alpha channel is dead weight the
            // PNG encoder can drop entirely.
            bitmap.setHasAlpha(false)
            val encoded = try {
                val canvas = Canvas(bitmap)
                canvas.drawColor(INK_PAGE_ARGB)
                canvas.scale(size.scale, size.scale)
                val paint = inkPaint()
                drawing.strokes.forEach { canvas.drawStroke(it, paint) }
                target.parentFile?.mkdirs()
                FileOutputStream(target).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
                }
            } finally {
                bitmap.recycle()
            }
            val bytes = target.length()
            when {
                !encoded -> {
                    target.delete()
                    failure(MiraErrorCode.STORAGE, "The page could not be encoded as a PNG")
                }
                bytes <= 0L -> {
                    target.delete()
                    failure(MiraErrorCode.STORAGE, "The exported page was written empty")
                }
                else -> AppResult.Success(bytes)
            }
        } catch (unwritable: IOException) {
            target.delete()
            failure(MiraErrorCode.STORAGE, "Could not write ${target.name}", unwritable)
        } catch (tooLarge: OutOfMemoryError) {
            // Caught deliberately, and only here: the one allocation that can be big enough to fail
            // is the bitmap, it is released before this line runs, and losing an export must not
            // take the strokes — which are already safe as a file of their own — down with it.
            target.delete()
            failure(MiraErrorCode.STORAGE, "This page is too large to export as an image", tooLarge)
        }
    }

    private fun Canvas.drawStroke(stroke: InkStroke, paint: Paint) {
        val points = stroke.points
        if (points.size == 1) {
            // A tap is a dot, and a dot with no second point has no line to draw: the round cap is
            // what gives it its shape.
            val only = points.first()
            paint.strokeWidth = strokeWidthAt(stroke.widthPx, only.pressure)
            drawPoint(only.x, only.y, paint)
            return
        }
        for (index in 1 until points.size) {
            val from = points[index - 1]
            val to = points[index]
            paint.strokeWidth = strokeWidthAt(stroke.widthPx, (from.pressure + to.pressure) / 2f)
            drawLine(from.x, from.y, to.x, to.y, paint)
        }
    }

    /**
     * No `strokeJoin`: the stroke is drawn as independent segments and a join applies to the
     * corners of a path, so setting one here would have described the rounding rather than caused
     * it. The round cap is what actually rounds every end, and therefore every corner.
     */
    private fun inkPaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = INK_ARGB
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
}

/**
 * Mira's obsidian, the page Abu's handwriting sits on, written out as plain ARGB rather than
 * read from `MiraPalette`.
 *
 * `core/design` is the presentation side of AGENTS.md §4's diagram and `data` may not depend on
 * it — reading the palette here dragged Compose's UI graphics onto the PNG encoder's classpath,
 * which was the only such import in `data` or `domain`. These two values are the same two tokens
 * and must move if the palette ever does; `AndroidInkRasterizerTest` asserts they still match, so
 * a change to one that forgets the other fails a test rather than shipping a two-tone Mira.
 */
internal val INK_PAGE_ARGB: Int = 0xFF080808.toInt()

/** Mira's platinum: the ink itself, for the same reason as [INK_PAGE_ARGB]. */
internal val INK_ARGB: Int = 0xFFF1F3F7.toInt()

/** PNG is lossless and ignores this, but the parameter is not optional. */
private const val PNG_QUALITY = 100
