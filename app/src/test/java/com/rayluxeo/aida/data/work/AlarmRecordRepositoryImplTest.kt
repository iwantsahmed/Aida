package com.rayluxeo.aida.data.work

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.data.TestDispatcherProvider
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.db.entity.ReminderEntity
import com.rayluxeo.aida.data.repository.AlarmRecordRepositoryImpl
import com.rayluxeo.aida.domain.model.AlarmState
import com.rayluxeo.aida.domain.model.RecordOrigin
import com.rayluxeo.aida.domain.model.ReminderState
import com.rayluxeo.aida.domain.repository.AlarmRecordRepository
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
 * The ledger's whole value is that it survives the thing it describes, so the tests that matter
 * here are the ones about what happens after a reminder is gone or after an alarm has fired.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AlarmRecordRepositoryImplTest {

    private lateinit var database: MiraDatabase
    private val clock = FakeMiraClock(LocalDateTime.of(2026, 3, 5, 14, 0))

    @Before
    fun openDatabase() {
        database = openTestDatabase()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a scheduled alarm is found again by its request code`() = runTest {
        val repository = repository()
        val recorded = repository.record(
            reminderId = null,
            label = "Call Ahmed",
            triggerAt = clock.now().plusSeconds(600),
            isExact = true,
            requestCode = 4417,
        ).successValue()

        val found = repository.scheduledByRequestCode(4417).successValue()

        assertThat(found?.id).isEqualTo(recorded.id)
        assertThat(found?.isExact).isTrue()
        assertThat(repository.observeScheduled().first().map { it.id }).containsExactly(recorded.id)
    }

    @Test
    fun `a request code that is not scheduled resolves to nothing`() = runTest {
        val repository = repository()
        val recorded = repository.record(
            reminderId = null,
            label = "Call Ahmed",
            triggerAt = clock.now().plusSeconds(600),
            isExact = true,
            requestCode = 4417,
        ).successValue()

        repository.markFired(recorded.id, clock.now().plusSeconds(600)).successValue()

        assertThat(repository.scheduledByRequestCode(4417).successValue()).isNull()
        assertThat(repository.observeScheduled().first()).isEmpty()
    }

    @Test
    fun `firing is recorded with the moment it happened`() = runTest {
        val repository = repository()
        val recorded = repository.record(
            reminderId = null,
            label = "Call Ahmed",
            triggerAt = clock.now().plusSeconds(600),
            isExact = false,
            requestCode = 4417,
        ).successValue()
        val firedAt = clock.now().plusSeconds(640)

        val fired = repository.markFired(recorded.id, firedAt).successValue()

        assertThat(fired.state).isEqualTo(AlarmState.FIRED)
        assertThat(fired.firedAt).isEqualTo(firedAt)
        assertThat(fired.isExact).isFalse()
    }

    @Test
    fun `an alarm that has already fired cannot be cancelled after the fact`() = runTest {
        val repository = repository()
        val recorded = repository.record(
            reminderId = null,
            label = "Call Ahmed",
            triggerAt = clock.now().plusSeconds(600),
            isExact = true,
            requestCode = 4417,
        ).successValue()
        val firedAt = clock.now().plusSeconds(600)
        repository.markFired(recorded.id, firedAt).successValue()

        assertThat(repository.cancel(recorded.id).failureCode()).isEqualTo(MiraErrorCode.VALIDATION)

        val stored = repository.byId(recorded.id).successValue()
        assertThat(stored.state).isEqualTo(AlarmState.FIRED)
        assertThat(stored.firedAt).isEqualTo(firedAt)
    }

    @Test
    fun `a missed alarm is recorded as missed rather than as fired`() = runTest {
        val repository = repository()
        val recorded = repository.record(
            reminderId = null,
            label = "Call Ahmed",
            triggerAt = clock.now().minusSeconds(600),
            isExact = true,
            requestCode = 4417,
        ).successValue()

        val missed = repository.markMissed(recorded.id).successValue()

        assertThat(missed.state).isEqualTo(AlarmState.MISSED)
        assertThat(missed.firedAt).isNull()
    }

    @Test
    fun `deleting the reminder orphans its alarm records instead of deleting them`() = runTest {
        val reminderId = insertReminder("Call Ahmed")
        val repository = repository()
        val recorded = repository.record(
            reminderId = reminderId,
            label = "Call Ahmed",
            triggerAt = clock.now().plusSeconds(600),
            isExact = true,
            requestCode = 4417,
        ).successValue()
        assertThat(repository.forReminder(reminderId).successValue().map { it.id })
            .containsExactly(recorded.id)

        database.hardDelete(table = "reminders", id = reminderId)

        val orphaned = repository.byId(recorded.id).successValue()
        assertThat(orphaned.reminderId).isNull()
        assertThat(orphaned.label).isEqualTo("Call Ahmed")
        assertThat(orphaned.state).isEqualTo(AlarmState.SCHEDULED)
    }

    @Test
    fun `an unknown alarm record is reported as not found`() = runTest {
        assertThat(repository().byId(404L).failureCode()).isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    private suspend fun insertReminder(title: String): Long = database.reminderDao().insert(
        ReminderEntity(
            title = title,
            triggerAtEpochMillis = clock.now().plusSeconds(600).toEpochMilli(),
            state = ReminderState.SCHEDULED,
            linkedTaskId = null,
            origin = RecordOrigin.MANUAL,
            createdAtEpochMillis = clock.now().toEpochMilli(),
        )
    )

    private fun repository(): AlarmRecordRepository = AlarmRecordRepositoryImpl(
        dao = database.alarmRecordDao(),
        clock = clock,
        dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher()),
    )
}
