package com.rayluxeo.aida.data.platform

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.data.TestDispatcherProvider
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.repository.ActivityLogRepositoryImpl
import com.rayluxeo.aida.domain.model.ActivityCategory
import com.rayluxeo.aida.domain.model.ActivityEntry
import com.rayluxeo.aida.domain.model.ActivitySubject
import com.rayluxeo.aida.domain.repository.ActivityLogRepository
import java.time.Duration
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ActivityLogRepositoryImplTest {

    private lateinit var database: MiraDatabase
    private val clock = FakeMiraClock(LocalDateTime.of(2026, 3, 5, 14, 0))
    private val start: Instant = clock.now()

    @Before
    fun openDatabase() {
        database = openTestDatabase()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a recorded entry comes back with its new id and every field intact`() = runTest {
        val repository = repository()

        val recorded = (
            repository.record(
                entry(
                    category = ActivityCategory.MONEY,
                    action = "invoice.sent",
                    summary = "Invoice 0042 sent to Ahmed",
                    subject = ActivitySubject("invoices", 42L),
                ),
            ) as AppResult.Success
            ).value

        assertThat(recorded.id).isGreaterThan(0L)
        val stored = repository.observeRecent().first().single()
        assertThat(stored.id).isEqualTo(recorded.id)
        assertThat(stored.category).isEqualTo(ActivityCategory.MONEY)
        assertThat(stored.action).isEqualTo("invoice.sent")
        assertThat(stored.summary).isEqualTo("Invoice 0042 sent to Ahmed")
        assertThat(stored.subject).isEqualTo(ActivitySubject("invoices", 42L))
        assertThat(stored.occurredAt).isEqualTo(start)
    }

    @Test
    fun `the log reads newest first`() = runTest {
        val repository = repository()
        repository.record(entry(action = "first", at = start))
        repository.record(entry(action = "second", at = start.plus(Duration.ofMinutes(5))))
        repository.record(entry(action = "third", at = start.plus(Duration.ofMinutes(10))))

        assertThat(repository.observeRecent().first().map { it.action })
            .containsExactly("third", "second", "first")
            .inOrder()
    }

    @Test
    fun `an entry outlives the row it describes`() = runTest {
        val repository = repository()
        database.insertClient(id = 7L)
        repository.record(
            entry(
                category = ActivityCategory.CLIENT,
                action = "client.added",
                subject = ActivitySubject("clients", 7L),
            ),
        )

        database.openHelper.writableDatabase.execSQL("DELETE FROM clients WHERE id = 7")

        assertThat(database.countRows("clients")).isEqualTo(0)
        val history = repository.observeForSubject(ActivitySubject("clients", 7L)).first()
        assertThat(history.map { it.action }).containsExactly("client.added")
    }

    @Test
    fun `an entry with no subject stores neither half of the reference`() = runTest {
        val repository = repository()

        repository.record(entry(action = "voice.listened", subject = null))

        val stored = database.activityLogDao().observeRecent(limit = 10).first().single()
        assertThat(stored.subjectTable).isNull()
        assertThat(stored.subjectId).isNull()
        assertThat(repository.observeRecent().first().single().subject).isNull()
    }

    @Test
    fun `the subject history of one row ignores another row with the same id`() = runTest {
        val repository = repository()
        repository.record(entry(action = "invoice.sent", subject = ActivitySubject("invoices", 7L)))
        repository.record(entry(action = "client.added", subject = ActivitySubject("clients", 7L)))

        val history = repository.observeForSubject(ActivitySubject("invoices", 7L)).first()

        assertThat(history.map { it.action }).containsExactly("invoice.sent")
    }

    @Test
    fun `a category can be read on its own`() = runTest {
        val repository = repository()
        repository.record(entry(category = ActivityCategory.MONEY, action = "payment.received"))
        repository.record(entry(category = ActivityCategory.FILE, action = "file.scanned"))

        assertThat(repository.observeByCategory(ActivityCategory.FILE).first().map { it.action })
            .containsExactly("file.scanned")
    }

    @Test
    fun `a window includes its start and excludes its end`() = runTest {
        val repository = repository()
        repository.record(entry(action = "before", at = start.minusMillis(1)))
        repository.record(entry(action = "at the start", at = start))
        repository.record(entry(action = "inside", at = start.plus(Duration.ofHours(1))))
        repository.record(entry(action = "at the end", at = start.plus(Duration.ofHours(2))))

        val window = (
            repository.between(start, start.plus(Duration.ofHours(2))) as AppResult.Success
            ).value

        assertThat(window.map { it.action }).containsExactly("at the start", "inside").inOrder()
    }

    private fun repository(): ActivityLogRepository = ActivityLogRepositoryImpl(
        dao = database.activityLogDao(),
        dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher()),
    )

    private fun entry(
        category: ActivityCategory = ActivityCategory.OTHER,
        action: String = "something.happened",
        summary: String = "Aida did something",
        subject: ActivitySubject? = null,
        at: Instant = start,
    ) = ActivityEntry(
        category = category,
        action = action,
        summary = summary,
        subject = subject,
        occurredAt = at,
    )
}
