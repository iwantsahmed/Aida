package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.Note
import com.rayluxeo.aida.domain.model.VoiceNote
import kotlinx.coroutines.flow.Flow

/** Recordings Abu kept, and the written notes some of them became. */
interface VoiceNoteRepository {

    fun observe(): Flow<List<VoiceNote>>

    /** Recordings with no words against them yet — what a transcription pass works through. */
    fun observeAwaitingTranscript(): Flow<List<VoiceNote>>

    /**
     * Reads a voice note **whatever its state, including one in the bin**.
     *
     * That is deliberate: restoring a binned record has to be able to read it first. It does mean
     * a caller showing the result must check `deletedAt` — every listing, search and recall path
     * already filters, so this is the one accessor where a binned row can come back.
     */
    suspend fun byId(id: Long): AppResult<VoiceNote>

    suspend fun record(voiceNote: VoiceNote): AppResult<VoiceNote>

    /** A blank transcript is refused: it would hide the recording from the transcription pass. */
    suspend fun setTranscript(id: Long, transcript: String): AppResult<VoiceNote>

    /**
     * Writes the transcript up as a note and links the two in one transaction, so a note can
     * never exist without the recording pointing at it. Saving a second time returns the note
     * that already exists rather than writing another copy.
     */
    suspend fun saveAsNote(id: Long, title: String?): AppResult<Note>

    suspend fun delete(id: Long): AppResult<Unit>

    suspend fun restore(id: Long): AppResult<Unit>
}
