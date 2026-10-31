package com.rayluxeo.aida.data.note

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.db.entity.InkPageEntity
import com.rayluxeo.aida.data.knowledge.SEED_MILLIS
import com.rayluxeo.aida.domain.ink.InkCodec
import com.rayluxeo.aida.domain.ink.InkDrawing
import com.rayluxeo.aida.domain.ink.InkPoint
import com.rayluxeo.aida.domain.ink.InkRasterizer
import com.rayluxeo.aida.domain.ink.InkStroke
import java.io.File

/**
 * What the notes and handwriting tests need around a note.
 *
 * The database opener, the instant they are all pinned to and the note row itself come from
 * `KnowledgeTestData` rather than being declared again here: one place decides the journal mode
 * and turns foreign keys on, one place spells out the columns of a note, and a copy of either
 * would be the one that quietly stopped matching. What is left in this file is only what is about
 * handwriting.
 */

internal const val MINUTE_MILLIS: Long = 60_000L

/**
 * Tags are written straight into their table rather than through `TagRepository`: these tests are
 * about which notes a query returns, and borrowing another area's write path would let a change
 * there fail them for a reason that has nothing to do with notes.
 */
internal fun MiraDatabase.seedTag(name: String): Long =
    openHelper.writableDatabase.insert(
        "tags",
        SQLiteDatabase.CONFLICT_ABORT,
        ContentValues().apply {
            put("name", name)
            put("createdAtEpochMillis", SEED_MILLIS)
            put("updatedAtEpochMillis", SEED_MILLIS)
        },
    )

/** A page whose files were never written: the ink filter asks which notes have pages, not files. */
internal suspend fun MiraDatabase.seedInkPage(noteId: Long): Long =
    inkPageDao().insert(
        InkPageEntity(
            noteId = noteId,
            strokeFileId = null,
            imageFileId = null,
            widthPx = 1_440,
            heightPx = 3_120,
            strokeCount = 3,
            createdAtEpochMillis = SEED_MILLIS,
            updatedAtEpochMillis = SEED_MILLIS,
        ),
    )

/** The stored `updatedAtEpochMillis` of a note, read past the repository that might have set it. */
internal fun MiraDatabase.noteUpdatedAt(noteId: Long): Long =
    openHelper.writableDatabase
        .query("SELECT updatedAtEpochMillis FROM notes WHERE id = $noteId")
        .use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }

internal fun drawingOf(vararg strokes: List<Pair<Float, Float>>): InkDrawing = InkDrawing(
    widthPx = 1_440,
    heightPx = 3_120,
    strokes = strokes.map { points ->
        InkStroke(
            widthPx = 3f,
            points = points.mapIndexed { index, (x, y) ->
                InkPoint(x = x, y = y, pressure = 0.5f, elapsedMillis = index * 8L)
            },
        )
    },
)

/**
 * A codec that writes a drawing down and reads it back, and nothing else.
 *
 * The real format is another agent's and is versioned; what these tests are about is whether the
 * repository hands the same strokes back, so the fake only has to round trip exactly.
 */
internal class RoundTripInkCodec : InkCodec {

    override fun encode(drawing: InkDrawing): String = buildString {
        append(drawing.widthPx).append('x').append(drawing.heightPx).append('\n')
        drawing.strokes.forEach { stroke ->
            append(stroke.widthPx)
            stroke.points.forEach { point ->
                append('|').append(point.x).append(',').append(point.y)
                    .append(',').append(point.pressure).append(',').append(point.elapsedMillis)
            }
            append('\n')
        }
    }

    override fun decode(stored: String): AppResult<InkDrawing> {
        val lines = stored.trim().lines()
        val (width, height) = lines.first().split('x').map { it.toInt() }
        val strokes = lines.drop(1).filter { it.isNotBlank() }.map { line ->
            val parts = line.split('|')
            InkStroke(
                widthPx = parts.first().toFloat(),
                points = parts.drop(1).map { point ->
                    val values = point.split(',')
                    InkPoint(
                        x = values[0].toFloat(),
                        y = values[1].toFloat(),
                        pressure = values[2].toFloat(),
                        elapsedMillis = values[3].toLong(),
                    )
                },
            )
        }
        return AppResult.Success(InkDrawing(widthPx = width, heightPx = height, strokes = strokes))
    }
}

/**
 * Writes a file where the real rasterizer would write a PNG.
 *
 * It has to put actual bytes on disk: the repository registers what it wrote, and a test that let
 * the image be imaginary would not notice a save that left nothing behind.
 */
internal class FakeInkRasterizer(private val filesDir: File) : InkRasterizer {

    /** Set to make the export fail, which is the case that must not cost Abu his old page. */
    var failure: AppResult.Failure? = null

    override suspend fun renderPng(drawing: InkDrawing, relativePath: String): AppResult<Long> {
        failure?.let { return it }
        val target = File(filesDir, relativePath)
        target.parentFile?.mkdirs()
        target.writeBytes(ByteArray(drawing.strokes.size * 16) { 1 })
        return AppResult.Success(target.length())
    }
}
