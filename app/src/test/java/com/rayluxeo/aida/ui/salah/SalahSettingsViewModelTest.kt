package com.rayluxeo.aida.ui.salah

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.asSuccess
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.domain.salah.BuiltInCities
import com.rayluxeo.aida.domain.salah.CalculationMethod
import com.rayluxeo.aida.domain.salah.Madhab
import com.rayluxeo.aida.domain.salah.Prayer
import com.rayluxeo.aida.domain.salah.PrayerParameters
import com.rayluxeo.aida.domain.salah.PrayerScheduler
import com.rayluxeo.aida.domain.salah.PrayerStatus
import com.rayluxeo.aida.domain.salah.SalahDayTracking
import com.rayluxeo.aida.domain.salah.SalahSettings
import com.rayluxeo.aida.domain.salah.SalahSettingsRepository
import com.rayluxeo.aida.domain.salah.SalahTrackingRepository
import com.rayluxeo.aida.domain.salah.TahajjudStatus
import com.rayluxeo.aida.domain.salah.TahajjudTracking
import com.rayluxeo.aida.domain.salah.TrackingSource
import com.rayluxeo.aida.domain.usecase.ResetSalahTrackingUseCase
import com.rayluxeo.aida.domain.usecase.SaveCurrentLocationUseCase
import com.rayluxeo.aida.domain.usecase.SetPrayerCityUseCase
import com.rayluxeo.aida.domain.usecase.UpdateSalahSettingsUseCase
import com.rayluxeo.aida.ui.settings.FakeCurrentPlaceFinder
import com.rayluxeo.aida.ui.settings.FakeSettingsRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The Salah settings screen, assembled by hand — Hilt's local-test transform does not run under
 * AGP 9 (§7.4). The use cases are the real ones over fake ports, the way `PlacesViewModelTest`
 * builds its own: a hand-stubbed use case would let the one thing worth pinning — that a change
 * actually reached storage and re-armed the alarms — be tested against a fiction.
 *
 * The mistake these guard against is the one Abu cannot see: a setting that looks changed on the
 * screen but never reached the schedule, or a reset that the screen reported but that never ran.
 */
class SalahSettingsViewModelTest {

    private val clock = FakeMiraClock(LocalDateTime.of(2026, 3, 5, 9, 0))
    private val salah = FakeSalahSettingsStore()
    private val scheduler = FakePrayerScheduler()
    private val tracking = FakeSalahTrackingStore()
    private val finder = FakeCurrentPlaceFinder()
    private val settings = FakeSettingsRepository()

    @Before
    fun installMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun removeMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `changing the madhab saves Hanafi and re-arms the alarms`() = runTest {
        salah.seed(
            SalahSettings(
                parameters = PrayerParameters(method = CalculationMethod.KARACHI, madhab = Madhab.SHAFI),
            ),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.setMadhab(Madhab.HANAFI)
        advanceUntilIdle()

        // The change reached storage through UpdateSalahSettingsUseCase, not just the screen.
        assertThat(salah.stored.parameters.madhab).isEqualTo(Madhab.HANAFI)
        // And the use case re-armed the schedule in the same step, so the alarms match the setting.
        assertThat(scheduler.reschedules).isAtLeast(1)
        assertThat(viewModel.state.value.notice).isNull()
    }

    @Test
    fun `confirming reset clears the tracking log`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.askReset()
        assertThat(viewModel.state.value.confirmingReset).isTrue()

        viewModel.confirmReset()
        advanceUntilIdle()

        assertThat(tracking.cleared).isTrue()
        assertThat(viewModel.state.value.confirmingReset).isFalse()
        assertThat(viewModel.state.value.resetting).isFalse()
        assertThat(viewModel.state.value.notice).isNull()
    }

    @Test
    fun `selecting city saves coordinates and re-arms alarms without GPS`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        val chattogram = BuiltInCities.findById("chattogram")!!
        viewModel.selectCity(chattogram)
        advanceUntilIdle()

        assertThat(salah.stored.location?.cityLabel).isEqualTo("Chattogram")
        assertThat(salah.stored.location?.latitude).isEqualTo(chattogram.latitude)
        assertThat(salah.stored.location?.longitude).isEqualTo(chattogram.longitude)
        assertThat(scheduler.reschedules).isAtLeast(1)
        assertThat(viewModel.state.value.notice).isNull()
    }

    private fun viewModel(): SalahSettingsViewModel = SalahSettingsViewModel(
        salahSettings = salah,
        settings = settings,
        updateSalahSettings = UpdateSalahSettingsUseCase(salah, scheduler),
        saveCurrentLocation = SaveCurrentLocationUseCase(finder, salah, scheduler, clock),
        resetSalahTracking = ResetSalahTrackingUseCase(tracking),
        setPrayerCity = SetPrayerCityUseCase(salah, scheduler),
    )
}

/** Abu's one Salah configuration, in memory. [stored] is what the tests assert against. */
private class FakeSalahSettingsStore(initial: SalahSettings = SalahSettings()) : SalahSettingsRepository {

    private val _settings = MutableStateFlow(initial)
    override val settings: Flow<SalahSettings> = _settings.asStateFlow()

    val stored: SalahSettings get() = _settings.value

    fun seed(settings: SalahSettings) {
        _settings.value = settings
    }

    override suspend fun current(): SalahSettings = _settings.value

    override suspend fun save(settings: SalahSettings): AppResult<SalahSettings> {
        _settings.value = settings
        return settings.asSuccess()
    }
}

/** Counts the re-arms, which is how a test tells a write-through from a bare repository write. */
private class FakePrayerScheduler : PrayerScheduler {
    var reschedules = 0
    override suspend fun reschedule(): AppResult<Unit> {
        reschedules++
        return AppResult.Success(Unit)
    }

    override suspend fun cancelAll(): AppResult<Unit> = AppResult.Success(Unit)
}

/** The prayer log. Only [clearAll] is exercised here; the rest answer emptily. */
private class FakeSalahTrackingStore : SalahTrackingRepository {
    var cleared = false

    override fun observeDay(date: LocalDate): Flow<SalahDayTracking> = emptyFlow()
    override fun observeMonth(month: YearMonth): Flow<List<SalahDayTracking>> = flowOf(emptyList())
    override suspend fun daysBetween(from: LocalDate, to: LocalDate): AppResult<List<SalahDayTracking>> =
        AppResult.Success(emptyList())

    override suspend fun tahajjudBetween(from: LocalDate, to: LocalDate): AppResult<List<TahajjudTracking>> =
        AppResult.Success(emptyList())

    override suspend fun setStatus(
        date: LocalDate,
        prayer: Prayer,
        status: PrayerStatus,
        scheduledAt: Instant?,
        prayedAt: Instant?,
        source: TrackingSource,
        notes: String?,
    ): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun setTahajjud(
        date: LocalDate,
        status: TahajjudStatus,
        prayedAt: Instant?,
        source: TrackingSource,
        notes: String?,
    ): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun clearAll(): AppResult<Unit> {
        cleared = true
        return AppResult.Success(Unit)
    }
}
