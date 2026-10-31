package com.rayluxeo.aida.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.automation.AutomationAlarmScheduler
import com.rayluxeo.aida.domain.automation.AutomationPlaceScheduler
import com.rayluxeo.aida.domain.automation.AutomationPlaceWatch
import com.rayluxeo.aida.domain.model.Automation
import com.rayluxeo.aida.domain.model.AutomationAction
import com.rayluxeo.aida.domain.model.AutomationTriggerConfig
import com.rayluxeo.aida.domain.scheduler.PlaceWatchRefusal
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * [SyncAutomationSchedulesUseCase] exists so the alarm and the geofence move together and the view
 * model never arms a scheduler directly (§4). These tests pin the two things that have to hold: both
 * schedulers are always driven, and the place verdict — the one thing the caller has to tell Abu
 * about — is the value that comes back.
 *
 * Hand-written fakes, no Hilt (§7.4).
 */
class SyncAutomationSchedulesUseCaseTest {

    private val alarm = FakeAlarmScheduler()
    private val place = FakePlaceScheduler()
    private val useCase = SyncAutomationSchedulesUseCase(alarm, place)

    @Test
    fun `sync drives both schedulers for the same rule`() = runTest {
        val automation = placeAutomation()

        useCase.sync(automation)

        assertThat(alarm.synced).containsExactly(automation.id)
        assertThat(place.synced).containsExactly(automation.id)
    }

    @Test
    fun `sync returns the place scheduler's watch verdict`() = runTest {
        place.nextWatch = AppResult.Success(AutomationPlaceWatch.Watching)

        val result = useCase.sync(placeAutomation())

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat((result as AppResult.Success).value).isEqualTo(AutomationPlaceWatch.Watching)
    }

    @Test
    fun `a refused watch is carried straight back for the caller to word`() = runTest {
        place.nextWatch =
            AppResult.Success(AutomationPlaceWatch.Refused(PlaceWatchRefusal.BACKGROUND_PERMISSION_MISSING))

        val result = useCase.sync(placeAutomation())

        val watch = (result as AppResult.Success).value
        assertThat(watch).isInstanceOf(AutomationPlaceWatch.Refused::class.java)
        assertThat((watch as AutomationPlaceWatch.Refused).reason)
            .isEqualTo(PlaceWatchRefusal.BACKGROUND_PERMISSION_MISSING)
    }

    @Test
    fun `the alarm is still armed even when the place watch is refused`() = runTest {
        // A rule can be both time-based and place-based; a refusal on the place side must not stop
        // the time side from being armed, or an edit would silently drop a working alarm.
        place.nextWatch = AppResult.Success(AutomationPlaceWatch.Refused(PlaceWatchRefusal.LIMIT_REACHED))

        useCase.sync(placeAutomation())

        assertThat(alarm.synced).isNotEmpty()
    }

    @Test
    fun `cancel tears down both schedulers`() = runTest {
        useCase.cancel(automationId = 7L)

        assertThat(alarm.cancelled).containsExactly(7L)
        assertThat(place.cancelled).containsExactly(7L)
    }

    private fun placeAutomation(): Automation = Automation(
        id = 42L,
        name = "At the office",
        description = null,
        trigger = AutomationTriggerConfig.Place(
            label = "Office",
            latitude = 23.8103,
            longitude = 90.4125,
            radiusMetres = 150,
        ),
        action = AutomationAction.GenerateDailyBrief,
        isEnabled = true,
        createdAt = FIXED,
        updatedAt = FIXED,
    )

    private class FakeAlarmScheduler : AutomationAlarmScheduler {
        val synced = mutableListOf<Long>()
        val cancelled = mutableListOf<Long>()

        override suspend fun sync(automation: Automation): AppResult<Unit> {
            synced += automation.id
            return AppResult.Success(Unit)
        }

        override suspend fun cancel(automationId: Long): AppResult<Unit> {
            cancelled += automationId
            return AppResult.Success(Unit)
        }

        override suspend fun rescheduleAll(): AppResult<Unit> = AppResult.Success(Unit)
    }

    private class FakePlaceScheduler : AutomationPlaceScheduler {
        val synced = mutableListOf<Long>()
        val cancelled = mutableListOf<Long>()
        var nextWatch: AppResult<AutomationPlaceWatch> = AppResult.Success(AutomationPlaceWatch.NotWatched)

        override val capacity: Int = 20

        override suspend fun sync(automation: Automation): AppResult<AutomationPlaceWatch> {
            synced += automation.id
            return nextWatch
        }

        override suspend fun cancel(automationId: Long): AppResult<Unit> {
            cancelled += automationId
            return AppResult.Success(Unit)
        }

        override suspend fun rewatchAll(): AppResult<Unit> = AppResult.Success(Unit)
    }

    private companion object {
        val FIXED: Instant = Instant.parse("2026-10-16T09:00:00Z")
    }
}
