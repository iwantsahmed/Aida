package com.rayluxeo.aida.data.assistant

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.data.TestDispatcherProvider
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.db.dao.ConversationDao
import com.rayluxeo.aida.data.db.entity.ConversationEntity
import com.rayluxeo.aida.data.db.entity.ConversationMessageEntity
import com.rayluxeo.aida.data.repository.ConversationThreadRepositoryImpl
import com.rayluxeo.aida.domain.model.InputSource
import com.rayluxeo.aida.domain.model.TurnRole
import java.time.Instant
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

/**
 * The history port over the same tables the live transcript uses. What matters here is that the
 * bin is real — a binned thread has to vanish from the list, from its own transcript and from
 * search — and that archiving is not the same thing, because an archived thread is still findable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ConversationThreadRepositoryImplTest {

    private lateinit var database: MiraDatabase
    private lateinit var dao: ConversationDao
    private val clock = FakeMiraClock(LocalDateTime.of(2026, 3, 5, 14, 0))

    @Before
    fun openDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            MiraDatabase::class.java,
        )
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .allowMainThreadQueries()
            .build()
        database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON")
        dao = database.conversationDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `threads are listed most recently spoken in first`() = runTest {
        val older = thread(title = "Rent", startedAt = MONDAY)
        val newer = thread(title = "Invoices", startedAt = MONDAY + HOUR)
        say(older, "about the rent", MONDAY)
        say(newer, "about the invoice", MONDAY + HOUR)

        val threads = repository().observeThreads().first()

        assertThat(threads.map { it.title }).containsExactly("Invoices", "Rent").inOrder()
    }

    @Test
    fun `a thread counts its own messages without anything recounting them`() = runTest {
        val id = thread(title = "Rent", startedAt = MONDAY)
        say(id, "about the rent", MONDAY)
        say(id, "and the receipt", MONDAY + HOUR)

        val stored = (repository().thread(id) as AppResult.Success).value

        assertThat(stored.messageCount).isEqualTo(2)
        assertThat(stored.lastMessageAt).isEqualTo(Instant.ofEpochMilli(MONDAY + HOUR))
    }

    @Test
    fun `a thread's messages come back in reading order`() = runTest {
        val id = thread(title = "Rent", startedAt = MONDAY)
        say(id, "first", MONDAY)
        say(id, "second", MONDAY + HOUR)

        val turns = repository().observeMessages(id).first()

        assertThat(turns.map { it.text }).containsExactly("first", "second").inOrder()
    }

    @Test
    fun `renaming sets the title and a blank name clears it`() = runTest {
        val repository = repository()
        val id = thread(title = null, startedAt = MONDAY)

        val named = repository.rename(id, "  Rent and receipts  ")
        assertThat((named as AppResult.Success).value.title).isEqualTo("Rent and receipts")

        val cleared = repository.rename(id, "   ")
        assertThat((cleared as AppResult.Success).value.title).isNull()
    }

    @Test
    fun `archiving takes a thread out of the list but not out of its own transcript`() = runTest {
        val repository = repository()
        val id = thread(title = "Rent", startedAt = MONDAY)
        say(id, "about the rent", MONDAY)

        val archived = repository.setArchived(id, archived = true)

        assertThat((archived as AppResult.Success).value.isArchived).isTrue()
        assertThat(repository.observeThreads().first()).isEmpty()
        assertThat(repository.observeMessages(id).first()).hasSize(1)
        assertThat(repository.thread(id)).isInstanceOf(AppResult.Success::class.java)
    }

    @Test
    fun `an archived thread is still searchable`() = runTest {
        val repository = repository()
        val id = thread(title = "Rent", startedAt = MONDAY)
        say(id, "about the rent", MONDAY)
        repository.setArchived(id, archived = true)

        val hits = (repository.search("rent") as AppResult.Success).value

        assertThat(hits.map { it.turn.text }).containsExactly("about the rent")
    }

    @Test
    fun `a binned thread is gone from the list, its transcript and lookup by id`() = runTest {
        val repository = repository()
        val id = thread(title = "Rent", startedAt = MONDAY)
        say(id, "about the rent", MONDAY)

        assertThat(repository.delete(id)).isInstanceOf(AppResult.Success::class.java)

        assertThat(repository.observeThreads().first()).isEmpty()
        assertThat(repository.observeMessages(id).first()).isEmpty()
        assertThat((repository.thread(id) as AppResult.Failure).error.code)
            .isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    @Test
    fun `a binned thread cannot be renamed back into existence`() = runTest {
        val repository = repository()
        val id = thread(title = "Rent", startedAt = MONDAY)
        repository.delete(id)

        val renamed = repository.rename(id, "Rent again")

        assertThat((renamed as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.NOT_FOUND)
        assertThat(repository.delete(id)).isInstanceOf(AppResult.Failure::class.java)
    }

    @Test
    fun `renaming a thread that never existed reports not found`() = runTest {
        val renamed = repository().rename(404L, "Anything")

        assertThat((renamed as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    @Test
    fun `search matches a word in a message and carries the thread it was said in`() = runTest {
        val id = thread(title = "Rent", startedAt = MONDAY)
        say(id, "Ahmed ke rent er kotha bolte hobe", MONDAY)
        say(id, "buy groceries", MONDAY + HOUR)

        val hits = (repository().search("ahmed") as AppResult.Success).value

        assertThat(hits).hasSize(1)
        assertThat(hits.single().conversationId).isEqualTo(id)
        assertThat(hits.single().turn.text).isEqualTo("Ahmed ke rent er kotha bolte hobe")
    }

    @Test
    fun `binning a thread takes its messages out of search`() = runTest {
        val repository = repository()
        val kept = thread(title = "Kept", startedAt = MONDAY)
        val binned = thread(title = "Binned", startedAt = MONDAY + HOUR)
        say(kept, "about the rent", MONDAY)
        say(binned, "about the rent as well", MONDAY + HOUR)

        repository.delete(binned)

        val hits = (repository.search("rent") as AppResult.Success).value
        assertThat(hits.map { it.conversationId }).containsExactly(kept)
    }

    @Test
    fun `a query with nothing searchable in it returns no hits rather than everything`() = runTest {
        val id = thread(title = "Rent", startedAt = MONDAY)
        say(id, "about the rent", MONDAY)

        val hits = (repository().search("...!") as AppResult.Success).value

        assertThat(hits).isEmpty()
    }

    @Test
    fun `clearing the transcript takes the messages with the threads`() = runTest {
        val id = thread(title = "Rent", startedAt = MONDAY)
        say(id, "about the rent", MONDAY)
        assertThat(countRows("conversation_messages")).isEqualTo(1)

        dao.deleteAllConversations()

        assertThat(countRows("conversations")).isEqualTo(0)
        assertThat(countRows("conversation_messages")).isEqualTo(0)
    }

    private fun countRows(table: String): Int =
        database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun repository(): ConversationThreadRepositoryImpl = ConversationThreadRepositoryImpl(
        dao = dao,
        clock = clock,
        dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher()),
    )

    private suspend fun thread(title: String?, startedAt: Long): Long = dao.insertConversation(
        ConversationEntity(
            title = title,
            startedAtEpochMillis = startedAt,
            lastMessageAtEpochMillis = startedAt,
            messageCount = 0,
            isArchived = false,
            deletedAtEpochMillis = null,
            createdAtEpochMillis = startedAt,
            updatedAtEpochMillis = startedAt,
        ),
    )

    private suspend fun say(conversationId: Long, text: String, atEpochMillis: Long): Long =
        dao.appendMessage(
            ConversationMessageEntity(
                conversationId = conversationId,
                role = TurnRole.ABU,
                text = text,
                languageTag = SpokenLanguage.ENGLISH.tag,
                source = InputSource.TEXT,
                commandName = null,
                engineName = null,
                createdAtEpochMillis = atEpochMillis,
            ),
        )

    private companion object {
        const val MONDAY = 1_772_000_000_000L
        const val HOUR = 3_600_000L
    }
}
