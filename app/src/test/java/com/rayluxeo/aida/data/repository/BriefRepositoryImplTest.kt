package com.rayluxeo.aida.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.data.TestDispatcherProvider
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.db.entity.ReminderEntity
import com.rayluxeo.aida.data.db.entity.TaskEntity
import com.rayluxeo.aida.domain.model.DailyBrief
import com.rayluxeo.aida.domain.model.DayPart
import com.rayluxeo.aida.domain.model.RecordOrigin
import com.rayluxeo.aida.domain.model.ReminderState
import com.rayluxeo.aida.domain.model.TaskPriority
import java.time.LocalDate
import java.time.LocalDateTime
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
class BriefRepositoryImplTest {

    private lateinit var database: MiraDatabase
    private val clock = FakeMiraClock(LocalDateTime.of(2026, 3, 5, 14, 0))
    private val today: LocalDate = LocalDate.of(2026, 3, 5)

    @Before
    fun openDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            MiraDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `day part follows the local hour across every boundary`() = runTest {
        val expected = mapOf(
            4 to DayPart.NIGHT,
            5 to DayPart.MORNING,
            11 to DayPart.MORNING,
            12 to DayPart.AFTERNOON,
            16 to DayPart.AFTERNOON,
            17 to DayPart.EVENING,
            20 to DayPart.EVENING,
            21 to DayPart.NIGHT,
            0 to DayPart.NIGHT,
        )

        for ((hour, dayPart) in expected) {
            clock.moveTo(today.atTime(hour, 30))
            assertThat(brief().dayPart).isEqualTo(dayPart)
        }
    }

    @Test
    fun `the brief carries today's date in the device zone`() = runTest {
        clock.moveTo(today.atTime(0, 30))

        assertThat(brief().date).isEqualTo(today)
    }

    @Test
    fun `counts separate open due today overdue and completed today`() = runTest {
        insertTask(title = "Due today", dueAt = at(today, hour = 10))
        insertTask(title = "Due later today", dueAt = at(today, hour = 23))
        insertTask(title = "Due yesterday", dueAt = at(today.minusDays(1), hour = 9))
        insertTask(title = "No due date")
        insertTask(title = "Done today", isDone = true, completedAt = at(today, hour = 8))
        insertTask(title = "Done yesterday", isDone = true, completedAt = at(today.minusDays(1), hour = 20))

        val brief = brief()

        assertThat(brief.openTaskCount).isEqualTo(4)
        assertThat(brief.dueTodayCount).isEqualTo(2)
        assertThat(brief.overdueCount).isEqualTo(1)
        assertThat(brief.completedTodayCount).isEqualTo(1)
    }

    @Test
    fun `the next reminder is the earliest scheduled one still ahead`() = runTest {
        insertReminder("Already fired", at(today, hour = 9), ReminderState.FIRED)
        insertReminder("Cancelled later", at(today, hour = 16), ReminderState.CANCELLED)
        insertReminder("Evening call", at(today, hour = 19), ReminderState.SCHEDULED)
        insertReminder("Tomorrow", at(today.plusDays(1), hour = 8), ReminderState.SCHEDULED)

        val brief = brief()

        assertThat(brief.nextReminderTitle).isEqualTo("Evening call")
        assertThat(brief.nextReminderAt?.toEpochMilli()).isEqualTo(at(today, hour = 19))
    }

    @Test
    fun `an empty database still produces a brief`() = runTest {
        val brief = brief()

        assertThat(brief.openTaskCount).isEqualTo(0)
        assertThat(brief.nextReminderTitle).isNull()
        assertThat(brief.nextReminderAt).isNull()
    }

    private suspend fun brief(): DailyBrief {
        val repository = BriefRepositoryImpl(
            tasks = database.taskDao(),
            reminders = database.reminderDao(),
            clock = clock,
            dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher()),
        )
        val result = repository.todayBrief()
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        return (result as AppResult.Success).value
    }

    private suspend fun insertTask(
        title: String,
        dueAt: Long? = null,
        isDone: Boolean = false,
        completedAt: Long? = null,
    ) {
        val created = at(today.minusDays(7), hour = 12)
        database.taskDao().insert(
            TaskEntity(
                title = title,
                notes = null,
                dueAtEpochMillis = dueAt,
                priority = TaskPriority.NORMAL,
                isDone = isDone,
                completedAtEpochMillis = completedAt,
                origin = RecordOrigin.MANUAL,
                createdAtEpochMillis = created,
                updatedAtEpochMillis = created,
            )
        )
    }

    private suspend fun insertReminder(title: String, triggerAt: Long, state: ReminderState) {
        database.reminderDao().insert(
            ReminderEntity(
                title = title,
                triggerAtEpochMillis = triggerAt,
                state = state,
                linkedTaskId = null,
                origin = RecordOrigin.MANUAL,
                createdAtEpochMillis = at(today.minusDays(1), hour = 12),
            )
        )
    }

    private fun at(date: LocalDate, hour: Int): Long =
        date.atTime(hour, 0).atZone(FakeMiraClock.DHAKA).toInstant().toEpochMilli()
}
