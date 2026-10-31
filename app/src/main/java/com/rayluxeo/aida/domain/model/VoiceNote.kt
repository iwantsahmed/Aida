package com.rayluxeo.aida.domain.model

import com.rayluxeo.aida.core.common.SpokenLanguage
import java.time.Duration
import java.time.Instant

/**
 * Something Abu said and kept.
 *
 * The recording itself is a file record; this is what it means. [audioFileId] is nullable because
 * the audio can be cleared to reclaim space without taking the transcript with it, and [noteId]
 * is set only once the voice note has been written up as a note.
 *
 * [recordedAt] is when Abu spoke, which is not the same as when the row was created — an import
 * of yesterday's recording is created today and must still sort under yesterday.
 */
data class VoiceNote(
    val id: Long = 0L,
    val audioFileId: Long? = null,
    val noteId: Long? = null,
    val transcript: String? = null,
    val duration: Duration,
    val language: SpokenLanguage = SpokenLanguage.ENGLISH,
    val recordedAt: Instant,
    val deletedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /** A recording with no words yet is the one thing a transcription pass is looking for. */
    val hasTranscript: Boolean get() = !transcript.isNullOrBlank()
}
