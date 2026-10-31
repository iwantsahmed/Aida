package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.data.db.dao.VoiceNoteDao
import com.rayluxeo.aida.data.db.entity.NoteEntity
import com.rayluxeo.aida.domain.model.Note
import com.rayluxeo.aida.domain.model.VoiceNote
import com.rayluxeo.aida.domain.repository.VoiceNoteRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Recordings Abu kept, over the voice_notes table.
 *
 * Writing one up as a note is the only operation here that touches two tables, and it is the DAO
 * transaction that makes it safe. This class owns what has to be true before that transaction is
 * worth starting: there is a recording, it is not in the bin, and it has words in it.
 */
@Singleton
class VoiceNoteRepositoryImpl @Inject constructor(
    private val dao: VoiceNoteDao,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : VoiceNoteRepository {

    override fun observe(): Flow<List<VoiceNote>> =
        dao.observeAll()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override fun observeAwaitingTranscript(): Flow<List<VoiceNote>> =
        dao.observeAwaitingTranscript()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override suspend fun byId(id: Long): AppResult<VoiceNote> {
        val found = storageCall(dispatchers, "load voice note $id") { dao.findById(id) }
        return when (found) {
            is AppResult.Failure -> found
            is AppResult.Success -> found.value?.toDomain()?.let { AppResult.Success(it) }
                ?: failure(MiraErrorCode.NOT_FOUND, "No voice note with id $id")
        }
    }

    override suspend fun record(voiceNote: VoiceNote): AppResult<VoiceNote> =
        storageCall(dispatchers, "record voice note") {
            val clean = voiceNote.copy(id = 0L, deletedAt = null)
            clean.copy(id = dao.insert(clean.toEntity()))
        }

    override suspend fun setTranscript(id: Long, transcript: String): AppResult<VoiceNote> {
        if (transcript.isBlank()) {
            return failure(MiraErrorCode.VALIDATION, "A transcript cannot be blank")
        }
        val changed = storageCall(dispatchers, "transcribe voice note $id") {
            dao.setTranscript(id = id, transcript = transcript, atEpochMillis = clock.now().toEpochMilli())
        }
        return when (changed) {
            is AppResult.Failure -> changed
            is AppResult.Success ->
                if (changed.value > 0) byId(id)
                else failure(MiraErrorCode.NOT_FOUND, "No live voice note with id $id")
        }
    }

    override suspend fun saveAsNote(id: Long, title: String?): AppResult<Note> {
        val recording = when (val loaded = byId(id)) {
            is AppResult.Failure -> return loaded
            is AppResult.Success -> loaded.value
        }
        if (recording.deletedAt != null) {
            return failure(MiraErrorCode.VALIDATION, "Voice note $id is in the bin")
        }
        val transcript = recording.transcript?.takeIf { it.isNotBlank() }
            ?: return failure(MiraErrorCode.VALIDATION, "Voice note $id has no transcript to write up")

        val written = storageCall(dispatchers, "write voice note $id up as a note") {
            val noteId = dao.saveAsNote(
                voiceNoteId = id,
                note = noteFrom(recording, title, transcript, clock.now()),
            )
            noteId?.let { dao.noteById(it) }
        }
        return when (written) {
            is AppResult.Failure -> written
            is AppResult.Success -> written.value?.toDomain()?.let { AppResult.Success(it) }
                ?: failure(MiraErrorCode.NOT_FOUND, "No voice note with id $id")
        }
    }

    override suspend fun delete(id: Long): AppResult<Unit> =
        storageCall(dispatchers, "delete voice note $id") {
            dao.softDelete(id, clock.now().toEpochMilli())
        }.orNotFound("No live voice note with id $id")

    override suspend fun restore(id: Long): AppResult<Unit> =
        storageCall(dispatchers, "restore voice note $id") {
            dao.restore(id, clock.now().toEpochMilli())
        }.orNotFound("No binned voice note with id $id")

    /**
     * The note keeps the recording's language, not Abu's current setting: he dictates in Bengali
     * and English on the same day, and the written note is the same words he spoke.
     */
    private fun noteFrom(
        recording: VoiceNote,
        title: String?,
        transcript: String,
        now: Instant,
    ) = NoteEntity(
        title = title?.takeIf { it.isNotBlank() },
        body = transcript,
        isPinned = false,
        clientId = null,
        projectId = null,
        // A transcript is not about a task and is not filed away: it is a note Abu has just
        // spoken into existence, so it starts live and unlinked like any other.
        taskId = null,
        languageTag = recording.language.tag,
        archivedAtEpochMillis = null,
        deletedAtEpochMillis = null,
        createdAtEpochMillis = now.toEpochMilli(),
        updatedAtEpochMillis = now.toEpochMilli(),
    )
}
