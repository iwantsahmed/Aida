package com.rayluxeo.aida.ui.salah

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.asSuccess
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.domain.salah.BuiltInCities
import com.rayluxeo.aida.domain.salah.CalculationMethod
import com.rayluxeo.aida.domain.salah.Madhab
import com.rayluxeo.aida.domain.salah.ObligatoryTracking
import com.rayluxeo.aida.domain.salah.Prayer
import com.rayluxeo.aida.domain.salah.PrayerCity
import com.rayluxeo.aida.domain.salah.PrayerLocation
import com.rayluxeo.aida.domain.salah.PrayerParameters
import com.rayluxeo.aida.domain.salah.PrayerScheduler
import com.rayluxeo.aida.domain.salah.PrayerStatus
import com.rayluxeo.aida.domain.salah.PrayerTimeCalculator
import com.rayluxeo.aida.domain.salah.SalahClock
import com.rayluxeo.aida.domain.salah.SalahDay
import com.rayluxeo.aida.domain.salah.SalahDayTracking
import com.rayluxeo.aida.domain.salah.SalahSettings
import com.rayluxeo.aida.domain.salah.SalahSettingsRepository
import com.rayluxeo.aida.domain.salah.SalahTrackingRepository
import com.rayluxeo.aida.domain.salah.TahajjudStatus
import com.rayluxeo.aida.domain.salah.TahajjudTracking
import com.rayluxeo.aida.domain.salah.TrackingSource
import com.rayluxeo.aida.domain.usecase.GetSalahDayUseCase
import com.rayluxeo.aida.domain.usecase.SetPrayerCityUseCase
import com.rayluxeo.aida.domain.usecase.TrackPrayerUseCase
import com.rayluxeo.aida.domain.usecase.TrackTahajjudUseCase
import com.rayluxeo.aida.ui.tasks.FakeSettingsRepository
import com.rayluxeo.aida.ui.tasks.TaskTimeFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The Salah main screen, assembled by hand — Hilt's local-test transform does not run under AGP 9
 * (§7.4). The day use case is a real [GetSalahDayUseCase] built from fakes plus the deterministic
 * [PrayerTimeCalculator], so the next-prayer the screen shows is checked against the domain's own
 * [SalahClock] rather than against a hardcoded astronomical time.
 *
 * The first test is the one that matters: the countdown is read from the clock, never stored, so
 * moving the wall clock alone must change the number on screen.
 */
class SalahViewModelTest {

    private val clock = FakeMiraClock(LocalDateTime.of(2026, 3, 10, 8, 0))
    private val today: LocalDate = clock.today()

    private val salahSettings = FakeSalahSettingsRepository(configured())
    private val tracking = FakeSalahTrackingRepository()
    private val settings = FakeSettingsRepository()
    private val calculator = PrayerTimeCalculator()
    private val timeFormatter = TaskTimeFormatter(clock)

    private val getSalahDay = GetSalahDayUseCase(salahSettings, calculator, clock)
    private val trackPrayer = TrackPrayerUseCase(tracking, clock)
    private val trackTahajjud = TrackTahajjudUseCase(tracking, clock)
    private val scheduler = object : PrayerScheduler {
        override suspend fun reschedule(): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun cancelAll(): AppResult<Unit> = AppResult.Success(Unit)
    }
    private val setPrayerCity = SetPrayerCityUseCase(salahSettings, scheduler)

    @Before
    fun installMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun removeMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the hero shows the prayer the domain says is next`() = runTest {
        val day = (getSalahDay(today) as AppResult.Success).value
        val expected = SalahClock.nextPrayer(clock.now(), day.times, day.tomorrowFajr)

        val viewModel = createViewModel()
        observe(viewModel.state)

        val hero = viewModel.state.value.hero
        assertThat(hero).isNotNull()
        assertThat(hero!!.nextPrayer).isEqualTo(expected.next)
        assertThat(hero.currentPrayer).isEqualTo(expected.current)
        assertThat(hero.countdown).isEqualTo(
            EnglishSalahWording.countdown(SalahClock.remainingUntil(clock.now(), expected.nextTime)),
        )
    }

    @Test
    fun `the countdown comes from the clock rather than from a counter`() = runTest {
        val day = (getSalahDay(today) as AppResult.Success).value
        val nextTime = SalahClock.nextPrayer(clock.now(), day.times, day.tomorrowFajr).nextTime

        val viewModel = createViewModel()
        observe(viewModel.state)

        val before = SalahClock.remainingUntil(clock.now(), nextTime)
        assertThat(viewModel.state.value.hero!!.countdown)
            .isEqualTo(EnglishSalahWording.countdown(before))

        // Nothing in the app moved; only the wall clock did, exactly as it does while Mira is open.
        clock.moveTo(LocalDateTime.of(2026, 3, 10, 8, 1))
        advanceTimeBy(1_100)
        runCurrent()

        val after = SalahClock.remainingUntil(clock.now(), nextTime)
        assertThat(after.seconds).isLessThan(before.seconds)
        assertThat(viewModel.state.value.hero!!.countdown)
            .isEqualTo(EnglishSalahWording.countdown(after))
    }

    @Test
    fun `marking a prayer records it as prayed through the use case`() = runTest {
        val viewModel = createViewModel()
        observe(viewModel.state)

        viewModel.onMarkPrayed(Prayer.FAJR)
        runCurrent()

        val call = tracking.statusCalls.single()
        assertThat(call.prayer).isEqualTo(Prayer.FAJR)
        assertThat(call.status).isEqualTo(PrayerStatus.PRAYED)
        assertThat(call.date).isEqualTo(today)
        // A completed prayer is stamped with the moment it was logged, and linked to its own time.
        assertThat(call.prayedAt).isNotNull()
        assertThat(call.scheduledAt).isNotNull()

        // The row follows the log: the circle now reads prayed.
        val fajr = viewModel.state.value.prayers.single { it.prayer == Prayer.FAJR }
        assertThat(fajr.displayStatus).isEqualTo(PrayerStatus.PRAYED)
    }

    @Test
    fun `a correction from the sheet writes the chosen status`() = runTest {
        val viewModel = createViewModel()
        observe(viewModel.state)

        viewModel.onSetStatus(Prayer.ASR, PrayerStatus.MISSED)
        runCurrent()

        val call = tracking.statusCalls.single()
        assertThat(call.prayer).isEqualTo(Prayer.ASR)
        assertThat(call.status).isEqualTo(PrayerStatus.MISSED)
        // A missed prayer is not a completed one, so no prayed-at moment is stamped.
        assertThat(call.prayedAt).isNull()
    }

    @Test
    fun `a failed save surfaces a calm notice rather than pretending it landed`() = runTest {
        tracking.failWith = MiraErrorCode.STORAGE
        val viewModel = createViewModel()
        observe(viewModel.state)

        viewModel.onMarkPrayed(Prayer.ISHA)
        runCurrent()

        assertThat(viewModel.state.value.notice).isEqualTo(EnglishSalahWording.trackingFailed)
    }

    @Test
    fun `with no location set the screen asks to set Salah up`() = runTest {
        salahSettings.set(SalahSettings())
        val viewModel = createViewModel()
        observe(viewModel.state)

        assertThat(viewModel.state.value.isConfigured).isFalse()
        assertThat(viewModel.state.value.hero).isNull()
    }

    @Test
    fun `selectCity configures prayer times immediately without GPS`() = runTest {
        salahSettings.set(SalahSettings(location = null))
        val viewModel = createViewModel()
        observe(viewModel.state)
        assertThat(viewModel.state.value.isConfigured).isFalse()

        viewModel.selectCity(BuiltInCities.DEFAULT)
        runCurrent()

        assertThat(viewModel.state.value.isConfigured).isTrue()
        assertThat(viewModel.state.value.hero).isNotNull()
    }

    private fun createViewModel() = SalahViewModel(
        getSalahDay = getSalahDay,
        trackPrayer = trackPrayer,
        trackTahajjud = trackTahajjud,
        setPrayerCity = setPrayerCity,
        tracking = tracking,
        salahSettings = salahSettings,
        settings = settings,
        clock = clock,
        timeFormatter = timeFormatter,
    )

    /** `StateFlow.collect()` with no argument does not compile; the member shadows the extension. */
    private fun TestScope.observe(state: StateFlow<SalahUiState>) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect { } }
        runCurrent()
    }

    private fun configured(): SalahSettings = SalahSettings(
        location = PrayerLocation(
            latitude = 23.8103,
            longitude = 90.4125,
            cityLabel = "Dhaka",
            zoneId = ZoneId.of("Asia/Dhaka"),
        ),
        parameters = PrayerParameters(method = CalculationMethod.KARACHI, madhab = Madhab.HANAFI),
    )
}

/** A Salah configuration the test owns, so a location can be present or absent on demand. */
private class FakeSalahSettingsRepository(initial: SalahSettings) : SalahSettingsRepository {

    private val state = MutableStateFlow(initial)

    override val settings: Flow<SalahSettings> = state

    override suspend fun current(): SalahSettings = state.value

    override suspend fun save(settings: SalahSettings): AppResult<SalahSettings> {
        state.value = settings
        return settings.asSuccess()
    }

    fun set(settings: SalahSettings) {
        state.value = settings
    }
}

/**
 * The prayer log, behaving like the real repository in the ways the screen depends on: the day
 * flow is live, a set status comes straight back out of [observeDay], and a failure is a value.
 */
private class FakeSalahTrackingRepository : SalahTrackingRepository {

    private val days = MutableStateFlow<Map<LocalDate, SalahDayTracking>>(emptyMap())

    val statusCalls = mutableListOf<StatusCall>()
    val tahajjudCalls = mutableListOf<TahajjudCall>()

    /** Set to make the next mutating call fail, so the refusal path can be exercised. */
    var failWith: MiraErrorCode? = null

    override fun observeDay(date: LocalDate): Flow<SalahDayTracking> =
        days.map { it[date] ?: emptyDay(date) }

    override fun observeMonth(month: YearMonth): Flow<List<SalahDayTracking>> =
        days.map { all -> all.values.filter { YearMonth.from(it.date) == month }.sortedBy { it.date } }

    override suspend fun daysBetween(from: LocalDate, to: LocalDate): AppResult<List<SalahDayTracking>> =
        AppResult.Success(days.value.values.filter { it.date >= from && it.date <= to }.sortedBy { it.date })

    override suspend fun tahajjudBetween(from: LocalDate, to: LocalDate): AppResult<List<TahajjudTracking>> =
        AppResult.Success(
            days.value.values.mapNotNull { it.tahajjud }.filter { it.localDate >= from && it.localDate <= to },
        )

    override suspend fun setStatus(
        date: LocalDate,
        prayer: Prayer,
        status: PrayerStatus,
        scheduledAt: Instant?,
        prayedAt: Instant?,
        source: TrackingSource,
        notes: String?,
    ): AppResult<Unit> {
        failWith?.let { return failure(it, "asked to fail") }
        statusCalls += StatusCall(date, prayer, status, scheduledAt, prayedAt, source)
        val day = days.value[date] ?: emptyDay(date)
        val obligatory = day.obligatory.filterNot { it.prayer == prayer } +
            ObligatoryTracking(date, prayer, status, scheduledAt, prayedAt, source, notes)
        days.value = days.value + (date to day.copy(obligatory = obligatory))
        return AppResult.Success(Unit)
    }

    override suspend fun setTahajjud(
        date: LocalDate,
        status: TahajjudStatus,
        prayedAt: Instant?,
        source: TrackingSource,
        notes: String?,
    ): AppResult<Unit> {
        failWith?.let { return failure(it, "asked to fail") }
        tahajjudCalls += TahajjudCall(date, status, prayedAt, source)
        val day = days.value[date] ?: emptyDay(date)
        days.value = days.value + (date to day.copy(tahajjud = TahajjudTracking(date, status, prayedAt, source, notes)))
        return AppResult.Success(Unit)
    }

    override suspend fun clearAll(): AppResult<Unit> {
        days.value = emptyMap()
        return AppResult.Success(Unit)
    }

    private fun emptyDay(date: LocalDate): SalahDayTracking =
        SalahDayTracking(date = date, obligatory = emptyList(), tahajjud = null)
}

private data class StatusCall(
    val date: LocalDate,
    val prayer: Prayer,
    val status: PrayerStatus,
    val scheduledAt: Instant?,
    val prayedAt: Instant?,
    val source: TrackingSource,
)

private data class TahajjudCall(
    val date: LocalDate,
    val status: TahajjudStatus,
    val prayedAt: Instant?,
    val source: TrackingSource,
)
