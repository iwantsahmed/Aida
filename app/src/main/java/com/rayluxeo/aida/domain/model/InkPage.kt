package com.rayluxeo.aida.domain.model

import java.time.Instant

/**
 * A page of handwriting belonging to a note.
 *
 * The strokes are not here — they are a file, and [strokeFileId] points at it. What this carries
 * is everything a list needs in order to draw a row without opening that file: the size it was
 * written at, how much is on it, and the exported image to show as a thumbnail.
 *
 * Both file links are nullable, and null is a real state rather than an oversight. Files are soft
 * deleted and reclaimed later, so a page can outlive its own ink; when that happens Abu is told
 * the writing is gone rather than being shown a blank page that looks like he never wrote
 * anything.
 */
data class InkPage(
    val id: Long = 0L,
    val noteId: Long,
    /** The strokes, replayable and re-exportable. Null once the file has been reclaimed. */
    val strokeFileId: Long? = null,
    /** A PNG of the same strokes, for showing without decoding them. */
    val imageFileId: Long? = null,
    val widthPx: Int,
    val heightPx: Int,
    val strokeCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
) {

    /** True when neither the strokes nor the image survived, which Abu is shown plainly. */
    val isLost: Boolean get() = strokeFileId == null && imageFileId == null
}
