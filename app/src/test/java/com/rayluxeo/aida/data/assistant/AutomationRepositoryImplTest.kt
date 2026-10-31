package com.rayluxeo.aida.data.assistant

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.data.TestDispatcherProvider
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.repository.AutomationRepositoryImpl
import com.rayluxeo.aida.domain.model.Automation
import com.rayluxeo.aida.domain.model.AutomationAction
import com.rayluxeo.aida.domain.model.AutomationOutcome
import com.rayluxeo.aida.domain.model.AutomationTriggerConfig
import com.rayluxeo.aida.domain.model.GlobalDeviceAction
import java.time.DayOfWeek
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
 * The trigger and action settings survive the database as JSON, so most of what is proved here is
 * that a value put in comes back as the same value and that a row which cannot be read is reported
 * rather than thrown or silently dropped from a caller that asked for it by id.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AutomationRepositoryImplTest {

    private lateinit var database: MiraDatabase
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
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a manual automation round trips`() = runTest {
        val stored = create(automation(trigger = AutomationTriggerConfig.Manual))

        assertThat(stored.trigger).isEqualTo(AutomationTriggerConfig.Manual)
        assertThat(stored.action).isEqualTo(AutomationAction.RunUtterance("aajker brief dao"))
        assertThat(stored.isEnabled).isFalse()
    }

    @Test
    fun `a time of day trigger round trips with its days`() = runTest {
        val trigger = AutomationTriggerConfig.TimeOfDay(
            minuteOfDay = 7 * 60 + 30,
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
        )

        assertThat(create(automation(trigger = trigger)).trigger).isEqualTo(trigger)
    }

    @Test
    fun `an empty day set stays empty, because it means every day`() = runTest {
        val trigger = AutomationTriggerConfig.TimeOfDay(minuteOfDay = 0)

        val stored = create(automation(trigger = trigger)).trigger

        assertThat(stored).isEqualTo(trigger)
        assertThat((stored as AutomationTriggerConfig.TimeOfDay).daysOfWeek).isEmpty()
    }

    @Test
    fun `an interval trigger round trips`() = runTest {
        val trigger = AutomationTriggerConfig.Interval(everyMinutes = 90)

        assertThat(create(automation(trigger = trigger)).trigger).isEqualTo(trigger)
    }

    @Test
    fun `a place trigger round trips its coordinates exactly`() = runTest {
        val trigger = AutomationTriggerConfig.Place(
            label = "Studio",
            latitude = 23.780573,
            longitude = 90.279239,
            radiusMetres = 120,
        )

        assertThat(create(automation(trigger = trigger)).trigger).isEqualTo(trigger)
    }

    @Test
    fun `a calendar trigger round trips with and without a title filter`() = runTest {
        val narrowed = AutomationTriggerConfig.CalendarEvent(minutesBefore = 15, titleContains = "client")
        val everything = AutomationTriggerConfig.CalendarEvent(minutesBefore = 0)

        assertThat(create(automation(trigger = narrowed)).trigger).isEqualTo(narrowed)
        assertThat(create(automation(name = "Second", trigger = everything)).trigger)
            .isEqualTo(everything)
    }

    @Test
    fun `a notify action round trips with and without a body`() = runTest {
        val withBody = AutomationAction.Notify(title = "Stand up", body = "Twenty minutes at the desk")
        val bare = AutomationAction.Notify(title = "Stand up")

        assertThat(create(automation(action = withBody)).action).isEqualTo(withBody)
        assertThat(create(automation(name = "Second", action = bare)).action).isEqualTo(bare)
    }

    @Test
    fun `the power triggers round trip`() = runTest {
        assertThat(create(automation(trigger = AutomationTriggerConfig.PowerConnected)).trigger)
            .isEqualTo(AutomationTriggerConfig.PowerConnected)
        assertThat(create(automation(name = "Unplug", trigger = AutomationTriggerConfig.PowerDisconnected)).trigger)
            .isEqualTo(AutomationTriggerConfig.PowerDisconnected)
    }

    @Test
    fun `an open-link action round trips its url`() = runTest {
        // Guards the one serialization trap: decodeAction is a string `when` with else -> null, so a
        // new action silently vanishes on read if its decode branch is forgotten.
        val action = AutomationAction.OpenLink("https://dash.rayluxeo.com")

        assertThat(create(automation(action = action)).action).isEqualTo(action)
    }

    @Test
    fun `a global-action and a launch-app action round trip`() = runTest {
        // Same decodeAction else -> null trap as the open-link guard above: the cross-app actions
        // encode fine and would silently vanish on read without their decode branches.
        val gesture = AutomationAction.PerformGlobalAction(GlobalDeviceAction.RECENTS)
        val launch = AutomationAction.LaunchApp(packageName = "com.rayluxeo.other", label = "Other")

        assertThat(create(automation(action = gesture)).action).isEqualTo(gesture)
        assertThat(create(automation(name = "Second", action = launch)).action).isEqualTo(launch)
    }

    @Test
    fun `a notification trigger round trips with and without filters`() = runTest {
        val narrowed = AutomationTriggerConfig.NotificationPosted(packageName = "com.bank", titleContains = "received")
        val anyNotification = AutomationTriggerConfig.NotificationPosted()

        assertThat(create(automation(trigger = narrowed)).trigger).isEqualTo(narrowed)
        assertThat(create(automation(name = "Second", trigger = anyNotification)).trigger)
            .isEqualTo(anyNotification)
    }

    @Test
    fun `an unreadable configuration is reported by id and left out of the list`() = runTest {
        val repository = repository()
        val broken = create(automation(name = "Broken")).id
        create(automation(name = "Intact"))
        corrupt(broken)

        val byId = repository.byId(broken)

        assertThat(byId).isInstanceOf(AppResult.Failure::class.java)
        assertThat((byId as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(repository.observeAll().first().map { it.name }).containsExactly("Intact")
    }

    @Test
    fun `a configuration that parses but cannot be acted on is also a validation failure`() =
        runTest {
            val repository = repository()
            val id = create(
                automation(trigger = AutomationTriggerConfig.Interval(everyMinutes = 30)),
            ).id
            database.openHelper.writableDatabase.execSQL(
                "UPDATE automations SET triggerConfigJson = '{\"everyMinutes\":0}' WHERE id = $id",
            )

            val byId = repository.byId(id)

            assertThat((byId as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.VALIDATION)
        }

    @Test
    fun `enabling an automation moves it into the enabled flow`() = runTest {
        val repository = repository()
        val id = create(automation()).id

        val enabled = repository.setEnabled(id, enabled = true)

        assertThat((enabled as AppResult.Success).value.isEnabled).isTrue()
        assertThat(repository.observeEnabled().first().map { it.id }).containsExactly(id)
    }

    @Test
    fun `enabling an automation that does not exist reports not found`() = runTest {
        val enabled = repository().setEnabled(404L, enabled = true)

        assertThat((enabled as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    @Test
    fun `update replaces the stored trigger`() = runTest {
        val repository = repository()
        val stored = create(automation(trigger = AutomationTriggerConfig.Manual))
        clock.moveTo(LocalDateTime.of(2026, 3, 5, 15, 0))

        val changed = repository.update(
            stored.copy(trigger = AutomationTriggerConfig.Interval(everyMinutes = 45)),
        )

        val value = (changed as AppResult.Success).value
        assertThat(value.trigger).isEqualTo(AutomationTriggerConfig.Interval(everyMinutes = 45))
        assertThat(value.updatedAt).isEqualTo(clock.now())
    }

    @Test
    fun `delete is soft and hides the automation from every read`() = runTest {
        val repository = repository()
        val id = create(automation()).id

        assertThat(repository.delete(id)).isInstanceOf(AppResult.Success::class.java)

        assertThat((repository.byId(id) as AppResult.Failure).error.code)
            .isEqualTo(MiraErrorCode.NOT_FOUND)
        assertThat(repository.observeAll().first()).isEmpty()
        assertThat(repository.delete(id)).isInstanceOf(AppResult.Failure::class.java)
    }

    @Test
    fun `starting a run opens it in flight and stamps the automation`() = runTest {
        val repository = repository()
        val id = create(automation()).id

        val run = (repository.startRun(id) as AppResult.Success).value

        assertThat(run.isInFlight).isTrue()
        assertThat(run.outcome).isEqualTo(AutomationOutcome.FAILED)
        assertThat(run.startedAt).isEqualTo(clock.now())
        assertThat((repository.byId(id) as AppResult.Success).value.lastRunAt).isEqualTo(clock.now())
        assertThat(repository.observeRuns(id).first().map { it.id }).containsExactly(run.id)
    }

    @Test
    fun `a run cannot be started against a binned automation`() = runTest {
        val repository = repository()
        val id = create(automation()).id
        repository.delete(id)

        val started = repository.startRun(id)

        assertThat((started as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    @Test
    fun `a run cannot be started against an automation nobody can read`() = runTest {
        val repository = repository()
        val id = create(automation()).id
        corrupt(id)

        val started = repository.startRun(id)

        assertThat((started as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `an unfinished run is what is left after the process dies`() = runTest {
        val repository = repository()
        val id = create(automation()).id
        val abandoned = (repository.startRun(id) as AppResult.Success).value

        val inFlight = (repository.inFlightRuns() as AppResult.Success).value

        assertThat(inFlight.map { it.id }).containsExactly(abandoned.id)
        assertThat(inFlight.single().isInFlight).isTrue()
    }

    @Test
    fun `finishing a run closes it and takes it out of flight`() = runTest {
        val repository = repository()
        val id = create(automation()).id
        val run = (repository.startRun(id) as AppResult.Success).value
        clock.moveTo(LocalDateTime.of(2026, 3, 5, 14, 5))

        val finished = repository.finishRun(run.id, AutomationOutcome.SUCCESS, "brief spoken")

        val value = (finished as AppResult.Success).value
        assertThat(value.outcome).isEqualTo(AutomationOutcome.SUCCESS)
        assertThat(value.finishedAt).isEqualTo(clock.now())
        assertThat(value.isInFlight).isFalse()
        assertThat((repository.inFlightRuns() as AppResult.Success).value).isEmpty()
    }

    @Test
    fun `finishing a run twice is refused rather than rewriting how it ended`() = runTest {
        val repository = repository()
        val id = create(automation()).id
        val run = (repository.startRun(id) as AppResult.Success).value
        repository.finishRun(run.id, AutomationOutcome.SUCCESS)

        val second = repository.finishRun(run.id, AutomationOutcome.FAILED)

        assertThat((second as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(repository.observeRuns(id).first().single().outcome)
            .isEqualTo(AutomationOutcome.SUCCESS)
    }

    @Test
    fun `finishing a run that was never started reports not found`() = runTest {
        val finished = repository().finishRun(404L, AutomationOutcome.SUCCESS)

        assertThat((finished as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    /** Reads back through the database, so a "round trips" test proves storage and not the copy. */
    private suspend fun create(automation: Automation): Automation {
        val repository = repository()
        val id = (repository.create(automation) as AppResult.Success).value.id
        return (repository.byId(id) as AppResult.Success).value
    }

    private fun corrupt(id: Long) {
        database.openHelper.writableDatabase.execSQL(
            "UPDATE automations SET triggerConfigJson = 'not json at all' WHERE id = $id",
        )
    }

    private fun repository(): AutomationRepositoryImpl = AutomationRepositoryImpl(
        dao = database.automationDao(),
        clock = clock,
        dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher()),
    )

    private fun automation(
        name: String = "Morning brief",
        trigger: AutomationTriggerConfig = AutomationTriggerConfig.Manual,
        action: AutomationAction = AutomationAction.RunUtterance("aajker brief dao"),
    ): Automation = Automation(
        name = name,
        description = null,
        trigger = trigger,
        action = action,
        createdAt = clock.now(),
        updatedAt = clock.now(),
    )
}
