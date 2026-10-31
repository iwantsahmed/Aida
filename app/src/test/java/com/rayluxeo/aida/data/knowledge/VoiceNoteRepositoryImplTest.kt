package com.rayluxeo.aida.data.knowledge

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.data.TestDispatcherProvider
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.repository.NoteRepositoryImpl
import com.rayluxeo.aida.data.repository.VoiceNoteRepositoryImpl
import com.rayluxeo.aida.domain.model.NoteFilter
import com.rayluxeo.aida.domain.model.VoiceNote
import com.rayluxeo.aida.domain.repository.NoteRepository
import com.rayluxeo.aida.domain.repository.VoiceNoteRepository
import java.time.Duration
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class VoiceNoteRepositoryImplTest {

    private lateinit var database: MiraDatabase
    private val clock = FakeMiraClock(LocalDateTime.of(2026, 3, 5, 14, 0))

    @Before
    fun openDatabase() {
        database = openKnowledgeDatabase()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a recording comes back with its duration intact`() = runTest {
        val repository = repository()

        val stored = repository.record(voiceNote(duration = Duration.ofSeconds(93))).expectSuccess()

        assertThat(stored.id).isGreaterThan(0L)
        val loaded = repository.byId(stored.id).expectSuccess()
        assertThat(loaded.duration).isEqualTo(Duration.ofSeconds(93))
        assertThat(loaded.recordedAt).isEqualTo(clock.now())
        assertThat(loaded.hasTranscript).isFalse()
    }

    @Test
    fun `a recording with no words yet sits in the backlog until it is transcribed`() = runTest {
        val repository = repository()
        val id = repository.record(voiceNote()).expectSuccess().id

        assertThat(repository.observeAwaitingTranscript().first().map { it.id }).containsExactly(id)

        val transcribed = repository.setTranscript(id, "call Rahman about the gold logo").expectSuccess()

        assertThat(transcribed.transcript).isEqualTo("call Rahman about the gold logo")
        assertThat(transcribed.hasTranscript).isTrue()
        assertThat(repository.observeAwaitingTranscript().first()).isEmpty()
    }

    @Test
    fun `a blank transcript is refused before it reaches the database`() = runTest {
        val repository = repository()
        val id = repository.record(voiceNote()).expectSuccess().id

        assertThat(repository.setTranscript(id, "   ").expectFailure()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(repository.byId(id).expectSuccess().transcript).isNull()
    }

    @Test
    fun `transcribing a recording that does not exist reports NOT_FOUND`() = runTest {
        val repository = repository()

        assertThat(repository.setTranscript(404L, "anything").expectFailure())
            .isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    @Test
    fun `writing a recording up as a note links the two and keeps the spoken language`() = runTest {
        val repository = repository()
        val notes = noteRepository()
        val id = repository.record(
            voiceNote(language = SpokenLanguage.BENGALI),
        ).expectSuccess().id
        repository.setTranscript(id, "Rahman ke phone korte hobe")

        val note = repository.saveAsNote(id, title = "Rahman").expectSuccess()

        assertThat(note.id).isGreaterThan(0L)
        assertThat(note.title).isEqualTo("Rahman")
        assertThat(note.body).isEqualTo("Rahman ke phone korte hobe")
        assertThat(note.language).isEqualTo(SpokenLanguage.BENGALI)
        assertThat(repository.byId(id).expectSuccess().noteId).isEqualTo(note.id)
        assertThat(notes.observe(NoteFilter.All).first().map { it.id }).containsExactly(note.id)
    }

    @Test
    fun `writing the same recording up twice does not leave two copies of it`() = runTest {
        val repository = repository()
        val notes = noteRepository()
        val id = repository.record(voiceNote()).expectSuccess().id
        repository.setTranscript(id, "call Rahman")

        val first = repository.saveAsNote(id, title = null).expectSuccess()
        val second = repository.saveAsNote(id, title = "a different title").expectSuccess()

        assertThat(second.id).isEqualTo(first.id)
        assertThat(second.title).isNull()
        assertThat(notes.observe(NoteFilter.All).first()).hasSize(1)
    }

    @Test
    fun `a recording with nothing transcribed cannot be written up`() = runTest {
        val repository = repository()
        val notes = noteRepository()
        val id = repository.record(voiceNote()).expectSuccess().id

        assertThat(repository.saveAsNote(id, title = "Rahman").expectFailure())
            .isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(notes.observe(NoteFilter.All).first()).isEmpty()
        assertThat(database.countNotes()).isEqualTo(0)
    }

    @Test
    fun `a recording in the bin cannot be written up`() = runTest {
        val repository = repository()
        val id = repository.record(voiceNote()).expectSuccess().id
        repository.setTranscript(id, "call Rahman")
        repository.delete(id)

        assertThat(repository.saveAsNote(id, title = null).expectFailure())
            .isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(database.countNotes()).isEqualTo(0)
    }

    @Test
    fun `writing up a recording that does not exist reports NOT_FOUND and writes nothing`() = runTest {
        val repository = repository()

        assertThat(repository.saveAsNote(404L, title = null).expectFailure())
            .isEqualTo(MiraErrorCode.NOT_FOUND)
        assertThat(database.countNotes()).isEqualTo(0)
    }

    @Test
    fun `deleting hides the recording and restore brings it back`() = runTest {
        val repository = repository()
        val id = repository.record(voiceNote()).expectSuccess().id

        assertThat(repository.delete(id)).isInstanceOf(AppResult.Success::class.java)
        assertThat(repository.observe().first()).isEmpty()
        assertThat(repository.observeAwaitingTranscript().first()).isEmpty()
        assertThat(repository.byId(id).expectSuccess().deletedAt).isEqualTo(clock.now())

        assertThat(repository.restore(id)).isInstanceOf(AppResult.Success::class.java)
        assertThat(repository.observe().first().map { it.id }).containsExactly(id)
        assertThat(repository.delete(id)).isInstanceOf(AppResult.Success::class.java)
        assertThat(repository.delete(id).expectFailure()).isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    @Test
    fun `the audio can be cleared without taking the transcript with it`() = runTest {
        val repository = repository()
        val fileId = database.seedFile("recording.m4a")
        val id = repository.record(voiceNote(audioFileId = fileId)).expectSuccess().id
        repository.setTranscript(id, "worth keeping")

        database.hardDeleteFile(fileId)

        val stored = repository.byId(id).expectSuccess()
        assertThat(stored.audioFileId).isNull()
        assertThat(stored.transcript).isEqualTo("worth keeping")
    }

    private fun repository(): VoiceNoteRepository = VoiceNoteRepositoryImpl(
        dao = database.voiceNoteDao(),
        clock = clock,
        dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher()),
    )

    private fun noteRepository(): NoteRepository = NoteRepositoryImpl(
        dao = database.noteDao(),
        inkPages = database.inkPageDao(),
        clock = clock,
        dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher()),
    )

    private fun voiceNote(
        audioFileId: Long? = null,
        language: SpokenLanguage = SpokenLanguage.ENGLISH,
        duration: Duration = Duration.ofSeconds(12),
    ) = VoiceNote(
        audioFileId = audioFileId,
        duration = duration,
        language = language,
        recordedAt = clock.now(),
        createdAt = clock.now(),
        updatedAt = clock.now(),
    )
}
