package com.rayluxeo.aida.data.repository

import android.content.Context
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.data.db.dao.InkPageDao
import com.rayluxeo.aida.data.db.dao.NoteDao
import com.rayluxeo.aida.data.db.entity.InkPageEntity
import com.rayluxeo.aida.domain.ink.InkCodec
import com.rayluxeo.aida.domain.ink.InkDrawing
import com.rayluxeo.aida.domain.ink.InkRasterizer
import com.rayluxeo.aida.domain.model.FileRecord
import com.rayluxeo.aida.domain.model.FileSource
import com.rayluxeo.aida.domain.model.InkPage
import com.rayluxeo.aida.domain.repository.FileRepository
import com.rayluxeo.aida.domain.repository.InkPageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Handwriting pages: the row in `ink_pages`, the strokes on disk and the exported image, kept in
 * step with one another.
 *
 * A page is three things written in three places, and the order they are written in is the whole
 * point of this class. New strokes and a new image go down first, they are registered next, the
 * row is pointed at them last, and only then are the files the page used to have released. The
 * other order — releasing first, writing after — loses Abu's handwriting the one time the export
 * fails halfway, and he would have no way of knowing it had happened.
 *
 * Nothing here can refuse to save because the writing could not be read back as text. Recognition
 * is a separate act, performed while he writes; a page of ink IS the note.
 */
@Singleton
class InkPageRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: InkPageDao,
    private val notes: NoteDao,
    private val files: FileRepository,
    private val codec: InkCodec,
    private val rasterizer: InkRasterizer,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : InkPageRepository {

    override fun observeForNote(noteId: Long): Flow<List<InkPage>> =
        dao.observeForNote(noteId)
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override suspend fun byId(id: Long): AppResult<InkPage> {
        val found = storageCall(dispatchers, "load ink page $id") { dao.byId(id) }
        return when (found) {
            is AppResult.Failure -> found
            is AppResult.Success -> found.value?.toDomain()?.let { AppResult.Success(it) }
                ?: failure(MiraErrorCode.NOT_FOUND, "No ink page with id $id")
        }
    }

    override suspend fun forNote(noteId: Long): AppResult<List<InkPage>> =
        storageCall(dispatchers, "list ink pages for note $noteId") {
            dao.forNote(noteId).map { it.toDomain() }
        }


    /**
     * An empty page is refused rather than stored.
     *
     * Two files and a row for a page with nothing on it would show in the notes list as
     * handwriting that turns out to be blank, which reads as lost writing. Erasing a page down to
     * nothing is [delete], and saying so is the caller's job.
     *
     * This is the gate for saving; `AndroidInkRasterizer` refuses the same two shapes because a
     * port may be called by anything, not because the rule is written twice. Nothing gets past
     * here, so its copy is a precondition rather than a second decision about what Abu may save.
     */
    override suspend fun save(
        noteId: Long,
        drawing: InkDrawing,
        pageId: Long?,
    ): AppResult<InkPage> {
        if (drawing.isEmpty) {
            return failure(MiraErrorCode.VALIDATION, "A page with no strokes is not handwriting")
        }
        if (drawing.widthPx <= 0 || drawing.heightPx <= 0) {
            return failure(
                MiraErrorCode.VALIDATION,
                "A page of ${drawing.widthPx}x${drawing.heightPx} has no size to replay at",
            )
        }

        val existing = when (val found = existingRow(pageId, noteId)) {
            is AppResult.Failure -> return found
            is AppResult.Success -> found.value
        }

        // One name for both files, so a page's strokes and its image are obviously a pair on disk.
        val name = UUID.randomUUID().toString()
        val strokePath = "$INK_DIRECTORY/$name$STROKE_SUFFIX"
        val imagePath = "$INK_DIRECTORY/$name$IMAGE_SUFFIX"

        val strokeBytes = when (val written = writeStrokes(strokePath, drawing)) {
            is AppResult.Failure -> return written
            is AppResult.Success -> written.value
        }

        val imageBytes = when (val rendered = rasterizer.renderPng(drawing, imagePath)) {
            is AppResult.Failure -> {
                removeFile(strokePath)
                return rendered
            }
            is AppResult.Success -> rendered.value
        }

        val strokeFile = when (
            val registered = files.register(record(strokePath, STROKE_MIME, strokeBytes))
        ) {
            is AppResult.Failure -> {
                removeFile(strokePath)
                removeFile(imagePath)
                return registered
            }
            is AppResult.Success -> registered.value
        }

        val imageFile = when (
            val registered = files.register(record(imagePath, IMAGE_MIME, imageBytes))
        ) {
            is AppResult.Failure -> {
                discard(strokeFile)
                removeFile(imagePath)
                return registered
            }
            is AppResult.Success -> registered.value
        }

        val saved = writeRow(existing, noteId, drawing, strokeFile.id, imageFile.id)
        val row = when (saved) {
            is AppResult.Failure -> {
                discard(strokeFile)
                discard(imageFile)
                return saved
            }
            is AppResult.Success -> saved.value
        }

        // Only now. Until this line the page could still have been abandoned, and the writing Abu
        // had before is what he would have been left with.
        existing?.strokeFileId?.let { release(it) }
        existing?.imageFileId?.let { release(it) }
        touchNote(noteId)

        // The row that was just written, rather than a re-read of it. A read that failed here
        // would report a page as unsaved seconds after it was saved — the inverse of §5's rule,
        // and the one wrong answer Abu would act on by writing the page again.
        return AppResult.Success(row.toDomain())
    }

    override suspend fun load(pageId: Long): AppResult<InkDrawing> {
        val page = when (val found = byId(pageId)) {
            is AppResult.Failure -> return found
            is AppResult.Success -> found.value
        }
        val strokeFileId = page.strokeFileId
            ?: return failure(
                MiraErrorCode.NOT_FOUND,
                "The strokes of ink page $pageId have been reclaimed",
            )
        val strokeRecord = when (val found = files.byId(strokeFileId)) {
            is AppResult.Failure -> return found
            is AppResult.Success -> found.value
        }
        val stored = storageCall(dispatchers, "read strokes ${strokeRecord.relativePath}") {
            File(context.filesDir, strokeRecord.relativePath).readText()
        }
        return when (stored) {
            is AppResult.Failure -> stored
            is AppResult.Success -> codec.decode(stored.value)
        }
    }

    /**
     * The row goes first, then the files are binned.
     *
     * `ink_pages` has no tombstone of its own, so the row is gone at once; the files are soft
     * deleted and reclaimed later, which is where the recoverable history of a page actually is.
     */
    override suspend fun delete(pageId: Long): AppResult<Unit> {
        val page = when (val found = byId(pageId)) {
            is AppResult.Failure -> return found
            is AppResult.Success -> found.value
        }
        val removed = storageCall(dispatchers, "delete ink page $pageId") { dao.delete(pageId) }
        if (removed is AppResult.Failure) return removed
        page.strokeFileId?.let { release(it) }
        page.imageFileId?.let { release(it) }
        touchNote(page.noteId)
        return AppResult.Success(Unit)
    }

    /** Null when this is a new page; a refusal when [pageId] names a page of another note. */
    private suspend fun existingRow(pageId: Long?, noteId: Long): AppResult<InkPageEntity?> {
        if (pageId == null) return AppResult.Success(null)
        val found = storageCall(dispatchers, "load ink page $pageId") { dao.byId(pageId) }
        return when (found) {
            is AppResult.Failure -> found
            is AppResult.Success -> {
                val row = found.value
                    ?: return failure(MiraErrorCode.NOT_FOUND, "No ink page with id $pageId")
                if (row.noteId == noteId) AppResult.Success(row)
                else failure(
                    MiraErrorCode.VALIDATION,
                    "Ink page $pageId belongs to note ${row.noteId}, not $noteId",
                )
            }
        }
    }

    /**
     * Marks the note as touched, once its handwriting has actually changed.
     *
     * Two things depend on it, and both are silent when it is missing. A note list is sorted by
     * `updatedAtEpochMillis`, so a page Abu wrote on an existing note would leave his list looking
     * exactly as it did before — which reads as writing that never landed. And the handwriting
     * filter is re-read whenever the `notes` table changes (`NoteRepositoryImpl.withInkOnly`),
     * because `ink_pages` is not a table Room observes for that query; without a write here, a
     * note that has just gained or lost its last page stays in the wrong list.
     *
     * The pin flag is re-written to the value it already has because `NoteDao` carries no
     * statement that writes the timestamp on its own, and this is the only guarded UPDATE there
     * that touches no column Abu could be editing at the same moment: writing a whole row read a
     * moment earlier would roll back words typed on the note detail screen while the canvas was
     * saving. A `touch(id, atEpochMillis)` statement on `NoteDao` is what should replace it.
     *
     * The result is dropped on purpose. By the time this runs the page is saved, and a timestamp
     * that did not move is not a reason to tell Abu his writing failed.
     */
    private suspend fun touchNote(noteId: Long) {
        storageCall(dispatchers, "touch note $noteId") {
            notes.findById(noteId)?.let { note ->
                notes.setPinned(
                    id = noteId,
                    pinned = note.isPinned,
                    atEpochMillis = clock.now().toEpochMilli(),
                )
            }
        }
    }

    private suspend fun writeStrokes(relativePath: String, drawing: InkDrawing): AppResult<Long> =
        storageCall(dispatchers, "write strokes $relativePath") {
            val target = File(context.filesDir, relativePath)
            target.parentFile?.mkdirs()
            target.writeText(codec.encode(drawing))
            target.length()
        }

    /** The row as it now stands on disk, so the caller does not have to read it back. */
    private suspend fun writeRow(
        existing: InkPageEntity?,
        noteId: Long,
        drawing: InkDrawing,
        strokeFileId: Long,
        imageFileId: Long,
    ): AppResult<InkPageEntity> {
        val now = clock.now().toEpochMilli()
        if (existing == null) {
            val row = InkPageEntity(
                noteId = noteId,
                strokeFileId = strokeFileId,
                imageFileId = imageFileId,
                widthPx = drawing.widthPx,
                heightPx = drawing.heightPx,
                strokeCount = drawing.strokes.size,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            )
            val inserted = storageCall(dispatchers, "create ink page for note $noteId") {
                dao.insert(row)
            }
            return when (inserted) {
                is AppResult.Failure -> inserted
                is AppResult.Success -> AppResult.Success(row.copy(id = inserted.value))
            }
        }
        val row = existing.copy(
            strokeFileId = strokeFileId,
            imageFileId = imageFileId,
            widthPx = drawing.widthPx,
            heightPx = drawing.heightPx,
            strokeCount = drawing.strokes.size,
            updatedAtEpochMillis = now,
        )
        val updated = storageCall(dispatchers, "update ink page ${existing.id}") {
            dao.update(row)
        }
        return when (updated) {
            is AppResult.Failure -> updated
            is AppResult.Success ->
                if (updated.value > 0) AppResult.Success(row)
                else failure(MiraErrorCode.NOT_FOUND, "No ink page with id ${existing.id}")
        }
    }

    /**
     * The registration for one of a page's files.
     *
     * [FileRecord.displayName] is the moment the page was written, not a phrase: a name in the
     * files list has to read the same to Abu in Bengali and in English, and a date in digits does
     * that without being translated. The file's own name cannot serve — it is the UUID that keeps
     * two pages apart on disk, and a list of those tells him nothing about which page is which.
     */
    private fun record(relativePath: String, mimeType: String, sizeBytes: Long): FileRecord {
        val now = clock.now()
        val written = clock.localNow()
        return FileRecord(
            displayName = "%04d-%02d-%02d-%02d%02d%02d.%s".format(
                Locale.ROOT,
                written.year,
                written.monthValue,
                written.dayOfMonth,
                written.hour,
                written.minute,
                written.second,
                relativePath.substringAfterLast('.'),
            ),
            relativePath = relativePath,
            mimeType = mimeType,
            sizeBytes = sizeBytes.coerceAtLeast(0L),
            source = FileSource.GENERATED,
            createdAt = now,
            updatedAt = now,
        )
    }

    /**
     * Lets go of a file the page no longer points at.
     *
     * Soft, like every delete of a file in Mira: the bytes stay until the reclaim pass takes
     * them, so a page saved over can still be recovered from the bin. The result is not returned
     * anywhere, because by the time this runs the new page is already saved — an old file that
     * could not be binned is a leftover for the reclaim pass to find, not a reason to tell Abu
     * that his writing failed to save.
     */
    private suspend fun release(fileId: Long) {
        files.softDelete(fileId)
    }

    /**
     * Unwinds a registration made during a save that then failed, so a half-written page leaves
     * nothing behind: the bytes go first, then the record, because a record purged while its file
     * is still on disk is a file nothing remembers.
     */
    private suspend fun discard(file: FileRecord) {
        removeFile(file.relativePath)
        if (files.softDelete(file.id) is AppResult.Success) files.purge(file.id)
    }

    private suspend fun removeFile(relativePath: String) {
        storageCall(dispatchers, "remove $relativePath") {
            File(context.filesDir, relativePath).delete()
        }
    }

    private companion object {
        /** Under Mira's own files directory, so the paths on the records stay relative to it. */
        const val INK_DIRECTORY = "ink"
        const val STROKE_SUFFIX = ".ink"
        const val IMAGE_SUFFIX = ".png"

        /**
         * The stroke file's shape belongs to [InkCodec], which is free to version it, so the
         * record does not claim a format it cannot guarantee.
         */
        const val STROKE_MIME = "application/octet-stream"
        const val IMAGE_MIME = "image/png"
    }
}
