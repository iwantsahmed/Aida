package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.ink.InkDrawing
import com.rayluxeo.aida.domain.model.InkPage
import kotlinx.coroutines.flow.Flow

/**
 * The handwriting pages, and the files behind them.
 *
 * Reading and writing the strokes goes through here rather than through `FileRepository` directly,
 * because a page and its two files have to be created, replaced and removed together. A caller
 * doing it in pieces is how a row ends up pointing at a file that was never written, or a file is
 * left on the disk with nothing referring to it.
 */
interface InkPageRepository {

    fun observeForNote(noteId: Long): Flow<List<InkPage>>

    suspend fun byId(id: Long): AppResult<InkPage>

    suspend fun forNote(noteId: Long): AppResult<List<InkPage>>

    /**
     * Writes [drawing] for [noteId] — strokes, exported image and row — or none of them.
     *
     * Replaces the page when [pageId] is given, so editing existing handwriting keeps its id and
     * anything pointing at it. The old files are released only once the new ones are on disk: the
     * other order loses Abu's writing if the export fails halfway.
     *
     * The note is marked as touched once the page has landed, so that writing on it moves it in
     * a list sorted by when it was last worked on. A note whose handwriting changed while its
     * row did not would sit exactly where it was, which reads as writing that was lost.
     */
    suspend fun save(
        noteId: Long,
        drawing: InkDrawing,
        pageId: Long? = null,
    ): AppResult<InkPage>

    /** The strokes, ready to reopen on the canvas. Fails when the stroke file is gone. */
    suspend fun load(pageId: Long): AppResult<InkDrawing>

    /** Takes the page away and marks its note as touched, for the reason [save] gives. */
    suspend fun delete(pageId: Long): AppResult<Unit>
}
