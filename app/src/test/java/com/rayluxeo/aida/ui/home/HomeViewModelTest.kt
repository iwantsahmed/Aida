package com.rayluxeo.aida.ui.home

import com.rayluxeo.aida.domain.usecase.ReopenTaskUseCase
import com.rayluxeo.aida.domain.usecase.CompleteTaskByIdUseCase
import com.rayluxeo.aida.domain.task.DefaultRecurrenceCalculator
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.ai.CommandRouter
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.core.common.getOrNull
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.domain.brief.AttentionItem
import com.rayluxeo.aida.domain.brief.AttentionKind
import com.rayluxeo.aida.domain.brief.AttentionReference
import com.rayluxeo.aida.domain.brief.AttentionSeverity
import com.rayluxeo.aida.domain.brief.AttentionTarget
import com.rayluxeo.aida.domain.brief.BriefDeadline
import com.rayluxeo.aida.domain.brief.BriefEvent
import com.rayluxeo.aida.domain.brief.BriefFollowUp
import com.rayluxeo.aida.domain.brief.BriefInvoice
import com.rayluxeo.aida.domain.brief.BriefMoney
import com.rayluxeo.aida.domain.brief.BriefPayment
import com.rayluxeo.aida.domain.brief.BriefReminder
import com.rayluxeo.aida.domain.brief.BriefTask
import com.rayluxeo.aida.domain.brief.DailyBriefEngine
import com.rayluxeo.aida.domain.brief.DailyBriefReport
import com.rayluxeo.aida.domain.command.AppCommand
import com.rayluxeo.aida.domain.command.EngineId
import com.rayluxeo.aida.domain.command.EngineResult
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.model.DayPart
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.TurnRole
import com.rayluxeo.aida.domain.salah.ObligatoryTracking
import com.rayluxeo.aida.domain.salah.Prayer
import com.rayluxeo.aida.domain.salah.PrayerLocation
import com.rayluxeo.aida.domain.salah.PrayerStatus
import com.rayluxeo.aida.domain.salah.PrayerTimeCalculator
import com.rayluxeo.aida.domain.salah.SalahDayTracking
import com.rayluxeo.aida.domain.salah.SalahSettings
import com.rayluxeo.aida.domain.salah.SalahSettingsRepository
import com.rayluxeo.aida.domain.salah.SalahTrackingRepository
import com.rayluxeo.aida.domain.salah.TahajjudStatus
import com.rayluxeo.aida.domain.salah.TahajjudTracking
import com.rayluxeo.aida.domain.salah.TrackingSource
import com.rayluxeo.aida.domain.usecase.ExecuteCommandUseCase
import com.rayluxeo.aida.domain.usecase.GetSalahDayUseCase
import com.rayluxeo.aida.domain.usecase.SetTaskDoneUseCase
import com.rayluxeo.aida.ui.mira.FakeAudioFocusManager
import com.rayluxeo.aida.ui.mira.FakeCommandEngine
import com.rayluxeo.aida.ui.mira.FakeConversationRepository
import com.rayluxeo.aida.ui.mira.FakeMiraTextToSpeech
import com.rayluxeo.aida.ui.mira.FakeTextNormalizer
import com.rayluxeo.aida.ui.mira.FakeVoiceConversationController
import com.rayluxeo.aida.ui.mira.FakeVoicePreferenceRepository
import com.rayluxeo.aida.ui.mira.PassThroughValidator
import com.rayluxeo.aida.ui.mira.RecordingDispatcher
import com.rayluxeo.aida.ui.response.MiraResponseRenderer
import com.rayluxeo.aida.ui.tasks.FakeSettingsRepository
import com.rayluxeo.aida.ui.tasks.FakeTaskRepository
import com.rayluxeo.aida.ui.tasks.TaskTimeFormatter
import com.rayluxeo.aida.ui.tasks.testTask
import com.rayluxeo.aida.voice.AudioFocusPurpose
import com.rayluxeo.aida.voice.MiraVoiceState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
 * Home is built entirely out of hand-written fakes: Hilt's test transform does not run under
 * AGP 9, so the view model is constructed here the same way Dagger would construct it.
 *
 * The command pipeline behind Quick Capture is the real [ExecuteCommandUseCase] with fake seams,
 * because the point of that field is that it is the same pipeline as the Mira screen's — a fake
 * use case would prove nothing about it.
 */
class HomeViewModelTest {

    private val clock = FakeMiraClock(LocalDateTime.of(2026, 3, 5, 9, 0))
    private val tasks = FakeTaskRepository(clock)
    private val settings = FakeSettingsRepository()
    private val voicePreferences = FakeVoicePreferenceRepository()
    private val briefs = FakeDailyBriefEngine(report())
    private val conversation = FakeConversationRepository()
    private val normalizer = FakeTextNormalizer()
    private val local = FakeCommandEngine(EngineId.LOCAL)
    private val gemini = FakeCommandEngine(EngineId.GEMINI).apply { available = false }
    private val dispatcher = RecordingDispatcher()
    private val tts = FakeMiraTextToSpeech()
    private val audioFocus = FakeAudioFocusManager()
    private val controller = FakeVoiceConversationController()
    private val renderer = MiraResponseRenderer(clock)
    private val times = TaskTimeFormatter(clock)
    private val salahSettings = FakeSalahSettingsRepository()
    private val salahTracking = FakeSalahTrackingRepository()
    private val getSalahDay = GetSalahDayUseCase(salahSettings, PrayerTimeCalculator(), clock)

    private val yesterdayMorning = LocalDateTime.of(2026, 3, 4, 9, 0).toInstant()
    private val todayNoon = LocalDateTime.of(2026, 3, 5, 12, 0).toInstant()
    private val todayEvening = LocalDateTime.of(2026, 3, 5, 17, 0).toInstant()
    private val lastMonth = LocalDateTime.of(2026, 2, 28, 9, 0).toInstant()
    private val nextWeek = LocalDateTime.of(2026, 3, 12, 9, 0).toInstant()

    @Before
    fun installMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun removeMainDispatcher() {
        Dispatchers.resetMain()
    }

    // ------------------------------------------------------------------ the day

    @Test
    fun `Home greets Abu by name and describes the day from his own records`() = runTest {
        tasks.seed(
            testTask(title = "Renew the lease", dueAt = yesterdayMorning, createdAt = yesterdayMorning),
            testTask(title = "Call Ahmed", dueAt = todayNoon, createdAt = yesterdayMorning),
            testTask(title = "Book the flight", dueAt = nextWeek, createdAt = yesterdayMorning),
            testTask(title = "Tidy the study", createdAt = yesterdayMorning),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.greeting).isEqualTo("Good morning, Abu.")
        assertThat(state.dateLine).isEqualTo("Thursday, 5 March")
        assertThat(state.section).isEqualTo(HomeSection.TODAY)
        assertThat(state.tasks.map { it.title }).containsExactly("Renew the lease", "Call Ahmed").inOrder()
        assertThat(state.moreTaskCount).isEqualTo(2)
        assertThat(state.summary).isEqualTo(
            HomeSummary(open = 4, dueToday = 1, overdue = 1, completedToday = 0),
        )
    }

    @Test
    fun `an overdue row is flagged and a future one is not`() = runTest {
        tasks.seed(
            testTask(title = "Renew the lease", dueAt = yesterdayMorning, createdAt = yesterdayMorning),
            testTask(title = "Call Ahmed", dueAt = todayNoon, createdAt = yesterdayMorning),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        val rows = viewModel.uiState.value.tasks.associateBy { it.title }
        assertThat(rows.getValue("Renew the lease").isOverdue).isTrue()
        assertThat(rows.getValue("Call Ahmed").isOverdue).isFalse()
        assertThat(rows.getValue("Call Ahmed").supporting).isEqualTo("Today, 12 pm")
    }

    @Test
    fun `nothing due today falls back to the open list rather than an empty screen`() = runTest {
        tasks.seed(
            testTask(title = "Book the flight", dueAt = nextWeek, createdAt = yesterdayMorning),
            testTask(title = "Tidy the study", createdAt = yesterdayMorning),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        val state = viewModel.uiState.value
        assertThat(state.section).isEqualTo(HomeSection.OPEN)
        assertThat(state.tasks.map { it.title }).containsExactly("Book the flight", "Tidy the study").inOrder()
        assertThat(state.isEmpty).isFalse()
    }

    @Test
    fun `a day with no open work reports itself empty`() = runTest {
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        val state = viewModel.uiState.value
        assertThat(state.isEmpty).isTrue()
        assertThat(state.moreTaskCount).isEqualTo(0)
    }

    @Test
    fun `completing a task from Home takes it off the list and moves the counts`() = runTest {
        tasks.seed(testTask(id = 7L, title = "Call Ahmed", dueAt = todayNoon, createdAt = yesterdayMorning))
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onCompleteTask(7L)
        runCurrent()

        val state = viewModel.uiState.value
        assertThat(state.tasks).isEmpty()
        assertThat(state.summary).isEqualTo(
            HomeSummary(open = 0, dueToday = 0, overdue = 0, completedToday = 1),
        )
        assertThat(state.notice).isNull()
    }

    @Test
    fun `a failed completion is worded for Abu instead of being swallowed`() = runTest {
        tasks.seed(testTask(id = 7L, title = "Call Ahmed", createdAt = yesterdayMorning))
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        tasks.failWith = MiraErrorCode.STORAGE
        viewModel.onCompleteTask(7L)
        runCurrent()

        assertThat(viewModel.uiState.value.notice?.text).isEqualTo(
            renderer.display(MiraResponse.Problem(MiraErrorCode.STORAGE), SpokenLanguage.ENGLISH),
        )
        assertThat(viewModel.uiState.value.notice?.isProblem).isTrue()

        viewModel.onDismissNotice()
        runCurrent()
        assertThat(viewModel.uiState.value.notice).isNull()
    }

    @Test
    fun `a brief that cannot be read is said out loud in the notice, not hidden behind a blank screen`() = runTest {
        briefs.failWith = MiraErrorCode.STORAGE
        tasks.seed(testTask(title = "Call Ahmed", dueAt = todayNoon, createdAt = yesterdayMorning))
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        val state = viewModel.uiState.value
        assertThat(state.notice?.isProblem).isTrue()
        assertThat(state.greeting).isEmpty()
        // The half of Home that does not come from the engine still stands.
        assertThat(state.tasks.map { it.title }).containsExactly("Call Ahmed")
        assertThat(state.dateLine).isEqualTo("Thursday, 5 March")
    }

    // ------------------------------------------------------------------ needs attention

    @Test
    fun `needs attention says the thing in Abu's own words and keeps the record it points at`() = runTest {
        briefs.report = report(
            attention = listOf(
                AttentionItem(
                    kind = AttentionKind.INVOICE_OVERDUE,
                    subject = "Ahmed",
                    severity = AttentionSeverity.NOW,
                    amount = Money.of("42000"),
                    at = lastMonth,
                    daysAway = -5,
                    reference = AttentionReference(AttentionTarget.INVOICE, 12L),
                ),
                AttentionItem(
                    kind = AttentionKind.PROJECT_DEADLINE,
                    subject = "Rayluxeo website",
                    severity = AttentionSeverity.SOON,
                    daysAway = 1,
                    reference = AttentionReference(AttentionTarget.PROJECT, 3L),
                ),
                AttentionItem(
                    kind = AttentionKind.SUBSCRIPTION_RENEWING,
                    subject = "Adobe",
                    severity = AttentionSeverity.WATCH,
                    daysAway = 3,
                ),
            ),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        val rows = viewModel.uiState.value.attention
        assertThat(rows.map { it.text }).containsExactly(
            "Ahmed's invoice is 5 days overdue",
            "Rayluxeo website is due tomorrow",
            "Adobe renews in 3 days",
        ).inOrder()
        assertThat(rows.first().detail).isEqualTo("BDT 42,000 · 28 February")
        assertThat(rows.first().severity).isEqualTo(AttentionSeverity.NOW)
        assertThat(rows.first().target).isEqualTo(AttentionTarget.INVOICE)
        // Nothing to open: the screen hands this one to Mira instead of leaving the tap dead.
        assertThat(rows.last().target).isNull()
        assertThat(rows.map { it.key }.toSet()).hasSize(3)
    }

    @Test
    fun `a day with nothing to answer for leaves the card out altogether`() = runTest {
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        assertThat(viewModel.uiState.value.attention).isEmpty()
    }

    @Test
    fun `ticking a task off re-reads the attention card without rebuilding the whole brief`() = runTest {
        tasks.seed(testTask(id = 7L, title = "Call Ahmed", dueAt = yesterdayMorning, createdAt = yesterdayMorning))
        val viewModel = createViewModel()
        observe(viewModel.uiState)
        assertThat(briefs.todayCalls).isEqualTo(1)

        briefs.report = report(
            attention = listOf(
                AttentionItem(
                    kind = AttentionKind.TASK_OVERDUE,
                    subject = "Renew the lease",
                    severity = AttentionSeverity.NOW,
                    daysAway = -2,
                    reference = AttentionReference(AttentionTarget.TASK, 9L),
                ),
            ),
        )
        viewModel.onCompleteTask(7L)
        runCurrent()

        assertThat(briefs.attentionCalls).isAtLeast(1)
        assertThat(briefs.todayCalls).isEqualTo(1)
        assertThat(viewModel.uiState.value.attention.single().text)
            .isEqualTo("Renew the lease is 2 days overdue")
    }

    // ------------------------------------------------------------------ the money and the rest

    @Test
    fun `the money is worded from the amounts themselves, and nothing overdue shows no overdue line`() = runTest {
        briefs.report = report(
            money = BriefMoney(
                outstandingTotal = Money.of("142000"),
                overdueTotal = Money.zero(),
                invoiceCount = 3,
                overdueCount = 0,
                dueSoon = listOf(
                    BriefInvoice(
                        id = 4L,
                        number = "INV-2043",
                        clientName = "Ahmed",
                        amount = Money.of("42000"),
                        dueOn = LocalDate.of(2026, 3, 9),
                        isOverdue = false,
                    ),
                ),
                recentPayments = listOf(
                    BriefPayment(
                        id = 8L,
                        clientName = "Rayluxeo",
                        amount = Money.of("18000"),
                        receivedAt = yesterdayMorning,
                    ),
                ),
            ),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        val money = requireNotNull(viewModel.uiState.value.money)
        assertThat(money.outstanding).isEqualTo("BDT 1,42,000")
        assertThat(money.outstandingDetail).isEqualTo("Across 3 invoices")
        assertThat(money.overdue).isNull()
        assertThat(money.overdueDetail).isNull()
        assertThat(money.dueSoon.single().title).isEqualTo("INV-2043 · Ahmed")
        assertThat(money.dueSoon.single().detail).isEqualTo("BDT 42,000 · Due 9 March")
        assertThat(money.recentPayments.single().title).isEqualTo("Rayluxeo")
        assertThat(money.recentPayments.single().detail).isEqualTo("BDT 18,000 · Yesterday, 9 am")
    }

    @Test
    fun `a brief with no money in it leaves the whole section out`() = runTest {
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        assertThat(viewModel.uiState.value.money).isNull()
    }

    @Test
    fun `the diary, the reminders, the deadlines and the follow-ups are all worded for Abu`() = runTest {
        briefs.report = report(
            nextEvent = BriefEvent(
                title = "Client call",
                startsAt = todayNoon,
                endsAt = null,
                location = "Zoom",
            ),
            upcomingReminders = listOf(
                BriefReminder(id = 5L, title = "Leave for the airport", triggerAt = todayEvening),
            ),
            deadlines = listOf(
                BriefDeadline(
                    projectId = 3L,
                    projectName = "Rayluxeo website",
                    clientName = "Rayluxeo",
                    dueOn = LocalDate.of(2026, 3, 6),
                    milestone = "Handover",
                ),
            ),
            followUps = listOf(
                BriefFollowUp(
                    clientId = 2L,
                    clientName = "Ahmed",
                    lastContactAt = lastMonth,
                    daysSince = 21,
                ),
            ),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        val state = viewModel.uiState.value
        assertThat(state.nextEvent?.title).isEqualTo("Client call")
        assertThat(state.nextEvent?.detail).isEqualTo("Today, 12 pm · Zoom")
        assertThat(state.reminders.single().whenLabel).isEqualTo("Today, 5 pm")
        assertThat(state.deadlines.single().detail).isEqualTo("Handover · Rayluxeo · Due 6 March")
        assertThat(state.followUps.single().detail).isEqualTo("Last contact 21 days ago")
    }

    @Test
    fun `a calendar Mira cannot read is a different fact from an empty diary`() = runTest {
        briefs.report = report(calendarReadable = false)
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        val state = viewModel.uiState.value
        assertThat(state.nextEvent).isNull()
        assertThat(state.calendarUnreadable).isTrue()
    }

    // ------------------------------------------------------------------ read my brief

    @Test
    fun `read my brief speaks a concise brief, under audio focus, in Abu's language`() = runTest {
        briefs.report = report(
            openTaskCount = 4,
            dueTodayCount = 2,
            overdueCount = 1,
            nextEvent = BriefEvent("Client call", todayNoon, endsAt = null, location = "Zoom"),
            upcomingReminders = listOf(BriefReminder(5L, "Leave for the airport", todayEvening)),
            money = BriefMoney(
                outstandingTotal = Money.of("142000"),
                overdueTotal = Money.of("42000"),
                invoiceCount = 3,
                overdueCount = 1,
                dueSoon = emptyList(),
                recentPayments = emptyList(),
            ),
            attention = listOf(
                AttentionItem(
                    kind = AttentionKind.INVOICE_OVERDUE,
                    subject = "Ahmed",
                    severity = AttentionSeverity.NOW,
                    amount = Money.of("42000"),
                    daysAway = -5,
                ),
            ),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onReadBrief()
        runCurrent()

        val (spoken, language) = tts.spoken.single()
        assertThat(language).isEqualTo(SpokenLanguage.ENGLISH)
        assertThat(spoken).contains("Good morning, Abu.")
        assertThat(spoken).contains("1 overdue and 2 due today.")
        assertThat(spoken).contains("Next: Client call, Today, 12 pm.")
        assertThat(spoken).contains("Reminder: Leave for the airport, Today, 5 pm.")
        assertThat(spoken).contains("Ahmed's invoice is 5 days overdue.")
        assertThat(spoken).contains("BDT 1,42,000 outstanding, 1 invoice overdue.")
        assertThat(audioFocus.requested).contains(AudioFocusPurpose.SPEAKING)
        // The brief is spoken, not written into the Mira conversation: every glance at Home
        // re-read it, and each read appended an identical turn, so the Mira screen opened on the
        // same greeting two or three times over. A brief Abu asks Mira for still logs via the
        // command path; this Home control only speaks.
        assertThat(conversation.recorded).isEmpty()
    }

    @Test
    fun `a quiet day is reported as quiet rather than padded out`() = runTest {
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onReadBrief()
        runCurrent()

        assertThat(tts.spoken.single().first).isEqualTo("Good morning, Abu. Nothing needs attention.")
    }

    @Test
    fun `pressing read my brief while she is reading stops her`() = runTest {
        tts.holdSpeech = true
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onReadBrief()
        runCurrent()
        assertThat(viewModel.uiState.value.isSpeaking).isTrue()

        viewModel.onReadBrief()
        runCurrent()

        assertThat(tts.stops).isEqualTo(1)
        assertThat(viewModel.uiState.value.isSpeaking).isFalse()
        assertThat(tts.spoken).hasSize(1)
    }

    @Test
    fun `with her voice switched off Mira says so and offers the screen that switches it back on`() = runTest {
        voicePreferences.setSpokenResponsesEnabled(false)
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onReadBrief()
        runCurrent()

        val notice = requireNotNull(viewModel.uiState.value.notice)
        assertThat(notice.text).isEqualTo("Aida's voice is switched off.")
        assertThat(notice.isProblem).isFalse()
        assertThat(notice.action).isEqualTo(HomeNoticeAction.OPEN_SETTINGS)
        assertThat(tts.spoken).isEmpty()
        assertThat(audioFocus.requested).isEmpty()
    }

    @Test
    fun `a brief she could not read is not read out`() = runTest {
        briefs.failWith = MiraErrorCode.STORAGE
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onReadBrief()
        runCurrent()

        assertThat(tts.spoken).isEmpty()
        assertThat(viewModel.uiState.value.notice?.isProblem).isTrue()
    }

    // ------------------------------------------------------------------ Bengali

    @Test
    fun `Bangla settings put the greeting, the date, the due times and the amounts into Bengali`() = runTest {
        settings.setLanguageMode(LanguageMode.BANGLA)
        tasks.seed(testTask(title = "Call Ahmed", dueAt = todayNoon, createdAt = yesterdayMorning))
        briefs.report = report(
            money = BriefMoney(
                outstandingTotal = Money.of("142000"),
                overdueTotal = Money.zero(),
                invoiceCount = 3,
                overdueCount = 0,
                dueSoon = emptyList(),
                recentPayments = emptyList(),
            ),
            attention = listOf(
                AttentionItem(
                    kind = AttentionKind.INVOICE_OVERDUE,
                    subject = "Ahmed",
                    severity = AttentionSeverity.NOW,
                    amount = Money.of("42000"),
                    daysAway = -5,
                ),
            ),
        )
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        val state = viewModel.uiState.value
        assertThat(state.language).isEqualTo(SpokenLanguage.BENGALI)
        assertThat(state.greeting).isEqualTo("শুভ সকাল, Abu।")
        assertThat(state.dateLine).contains("মার্চ")
        assertThat(state.tasks.single().supporting).isEqualTo("আজ, দুপুর ১২টা")
        assertThat(state.attention.single().text).isEqualTo("Ahmed-এর ইনভয়েস ৫ দিন ধরে বকেয়া")
        assertThat(state.attention.single().detail).isEqualTo("৪২,০০০ টাকা")
        assertThat(state.money?.outstanding).isEqualTo("১,৪২,০০০ টাকা")
        // The defect this guards: an English figure or an English label on a Bengali line.
        assertThat(state.money?.outstandingDetail).doesNotContain("Across")
    }

    @Test
    fun `an overdue row words the overdue prefix in the language of the moment beside it`() = runTest {
        settings.setLanguageMode(LanguageMode.BANGLA)
        tasks.seed(testTask(title = "Renew the lease", dueAt = yesterdayMorning, createdAt = yesterdayMorning))
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        val row = viewModel.uiState.value.tasks.single()
        assertThat(row.isOverdue).isTrue()
        // The defect this guards: "Overdue — গতকাল, ..." with an English word on a Bengali line.
        assertThat(row.supporting).startsWith("সময় পেরিয়েছে")
        assertThat(row.supporting).doesNotContain("Overdue")
    }

    @Test
    fun `the brief is read out in Bengali when that is the language Abu set`() = runTest {
        settings.setLanguageMode(LanguageMode.BANGLA)
        briefs.report = report(openTaskCount = 2, dueTodayCount = 2)
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        viewModel.onReadBrief()
        runCurrent()

        val (spoken, language) = tts.spoken.single()
        assertThat(language).isEqualTo(SpokenLanguage.BENGALI)
        assertThat(spoken).startsWith("শুভ সকাল, Abu।")
        assertThat(spoken).contains("আজ ২টি কাজ আছে।")
    }

    // ------------------------------------------------------------------ salah

    @Test
    fun `with no prayer location set, Home shows no Salah card`() = runTest {
        // The default fake settings carry no location, so Salah is not configured.
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        assertThat(viewModel.uiState.value.salah).isNull()
        // But an authentic Hadith is still provided on Home for the time of day
        val hadith = requireNotNull(viewModel.uiState.value.hadith)
        assertThat(hadith.arabic).isNotEmpty()
        assertThat(hadith.bangla).isNotEmpty()
        assertThat(hadith.reference).isNotEmpty()
    }

    @Test
    fun `the Salah card includes the active waqt Hadith in Arabic and Bangla`() = runTest {
        val date = LocalDate.of(2026, 3, 5)
        salahSettings.set(SalahSettings(location = DHAKA))
        val day = requireNotNull(getSalahDay(date).getOrNull())
        val dhuhr = LocalDateTime.ofInstant(day.times.dhuhr.toInstant(), FakeMiraClock.DHAKA)
        salahTracking.setDay(
            SalahDayTracking(
                date = date,
                obligatory = listOf(ObligatoryTracking(date, Prayer.FAJR, PrayerStatus.PRAYED)),
                tahajjud = null,
            ),
        )
        clock.moveTo(dhuhr.minusMinutes(90))

        val viewModel = createViewModel()
        observe(viewModel.uiState)

        val salah = requireNotNull(viewModel.uiState.value.salah)
        val hadith = requireNotNull(salah.hadith)
        assertThat(hadith.prayer).isEqualTo(Prayer.FAJR) // At 90 mins before Dhuhr, active window is Fajr
        assertThat(hadith.arabic).isNotEmpty()
        assertThat(hadith.bangla).isNotEmpty()
        assertThat(hadith.reference).isNotEmpty()
        assertThat(viewModel.uiState.value.hadith).isNotNull()
    }

    @Test
    fun `the Salah card names the next prayer and counts down live as the clock advances`() =
        runTest {
            val date = LocalDate.of(2026, 3, 5)
            salahSettings.set(SalahSettings(location = DHAKA))
            // Pin the clock a known distance before today's Dhuhr, whatever its wall-clock time
            // works out to, so the countdown is exact without hard-coding the computed minute.
            val day = requireNotNull(getSalahDay(date).getOrNull())
            val dhuhr = LocalDateTime.ofInstant(day.times.dhuhr.toInstant(), FakeMiraClock.DHAKA)
            salahTracking.setDay(
                SalahDayTracking(
                    date = date,
                    obligatory = listOf(ObligatoryTracking(date, Prayer.FAJR, PrayerStatus.PRAYED)),
                    tahajjud = null,
                ),
            )
            clock.moveTo(dhuhr.minusMinutes(90))

            val viewModel = createViewModel()
            observe(viewModel.uiState)

            val salah = requireNotNull(viewModel.uiState.value.salah)
            assertThat(salah.nextName).isEqualTo("Dhuhr")
            assertThat(salah.remainingLabel).isEqualTo("1h 30m remaining")
            assertThat(salah.dots.map { it.label })
                .containsExactly("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha").inOrder()
            // Fajr was logged; the ones still ahead read as upcoming, never as "missed".
            assertThat(salah.dots.first().state).isEqualTo(HomeSalahDotState.PRAYED)
            assertThat(salah.dots[1].state).isEqualTo(HomeSalahDotState.UPCOMING)

            // The countdown is live off the wall clock: move it forward, tick, and it shrinks.
            clock.moveTo(dhuhr.minusMinutes(30))
            advanceTimeBy(1_100)
            runCurrent()

            assertThat(viewModel.uiState.value.salah?.remainingLabel).isEqualTo("30m remaining")
        }

    // ------------------------------------------------------------------ plumbing

    // ------------------------------------------------------------------ the voice, in place

    @Test
    fun `the wave reads the shared voice session, so a press here is answered here`() = runTest {
        val viewModel = createViewModel()
        observe(viewModel.uiState)

        controller.listening(amplitude = 0.7f, partial = "remind me")
        runCurrent()

        val voice = viewModel.uiState.value.voice
        assertThat(voice.state).isEqualTo(MiraVoiceState.LISTENING)
        assertThat(voice.amplitude).isEqualTo(0.7f)
        assertThat(voice.partialTranscript).isEqualTo("remind me")
    }

    private fun createViewModel() = HomeViewModel(
        briefEngine = briefs,
        tasks = tasks,
        setTaskDone = SetTaskDoneUseCase(
            complete = CompleteTaskByIdUseCase(tasks, DefaultRecurrenceCalculator(), clock),
            reopen = ReopenTaskUseCase(tasks),
        ),
        settings = settings,
        voicePreferences = voicePreferences,
        tts = tts,
        audioFocus = audioFocus,
        controller = controller,
        renderer = renderer,
        times = times,
        clock = clock,
        salahDay = getSalahDay,
        salahTracking = salahTracking,
        salahSettingsRepo = salahSettings,
    )

    /** Keeps the `WhileSubscribed` state flow hot for the length of the test. */
    private fun TestScope.observe(state: StateFlow<HomeUiState>) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect { } }
        runCurrent()
    }

    private fun LocalDateTime.toInstant() = atZone(FakeMiraClock.DHAKA).toInstant()

    private companion object {
        /** Abu's place, so the real calculator has coordinates and a zone to resolve times from. */
        val DHAKA = PrayerLocation(
            latitude = 23.8103,
            longitude = 90.4125,
            cityLabel = "Dhaka",
            zoneId = FakeMiraClock.DHAKA,
        )

        fun report(
            date: LocalDate = LocalDate.of(2026, 3, 5),
            dayPart: DayPart = DayPart.MORNING,
            ownerName: String? = "Abu Sayed Ahmed",
            openTaskCount: Int = 0,
            dueTodayCount: Int = 0,
            overdueCount: Int = 0,
            completedTodayCount: Int = 0,
            dueToday: List<BriefTask> = emptyList(),
            overdue: List<BriefTask> = emptyList(),
            nextEvent: BriefEvent? = null,
            todaysEvents: List<BriefEvent> = emptyList(),
            calendarReadable: Boolean = true,
            upcomingReminders: List<BriefReminder> = emptyList(),
            money: BriefMoney = BriefMoney(
                outstandingTotal = null,
                overdueTotal = null,
                invoiceCount = 0,
                overdueCount = 0,
                dueSoon = emptyList(),
                recentPayments = emptyList(),
            ),
            deadlines: List<BriefDeadline> = emptyList(),
            followUps: List<BriefFollowUp> = emptyList(),
            attention: List<AttentionItem> = emptyList(),
        ) = DailyBriefReport(
            date = date,
            dayPart = dayPart,
            ownerName = ownerName,
            openTaskCount = openTaskCount,
            dueTodayCount = dueTodayCount,
            overdueCount = overdueCount,
            completedTodayCount = completedTodayCount,
            dueToday = dueToday,
            overdue = overdue,
            nextEvent = nextEvent,
            todaysEvents = todaysEvents,
            calendarReadable = calendarReadable,
            upcomingReminders = upcomingReminders,
            money = money,
            deadlines = deadlines,
            followUps = followUps,
            attention = attention,
        )
    }
}

/**
 * The brief engine as Home sees it: one report the test sets, and a count of how it was asked for.
 *
 * The counts matter. Home is meant to re-read the cheap half of the engine when the task list
 * moves and the whole thing only when Abu comes back to the screen, and the only way to hold that
 * apart is to count the two calls separately.
 */
private class FakeDailyBriefEngine(var report: DailyBriefReport) : DailyBriefEngine {

    var todayCalls = 0
        private set

    var attentionCalls = 0
        private set

    var failWith: MiraErrorCode? = null

    override suspend fun today(): AppResult<DailyBriefReport> {
        todayCalls++
        return failWith?.let { failure(it, "Injected by FakeDailyBriefEngine") }
            ?: AppResult.Success(report)
    }

    override suspend fun attention(): AppResult<List<AttentionItem>> {
        attentionCalls++
        return failWith?.let { failure(it, "Injected by FakeDailyBriefEngine") }
            ?: AppResult.Success(report.attention)
    }
}

/** One settings value the test controls; starts with no location, so Salah is not configured. */
private class FakeSalahSettingsRepository : SalahSettingsRepository {
    private val state = MutableStateFlow(SalahSettings())
    override val settings: Flow<SalahSettings> = state
    override suspend fun current(): SalahSettings = state.value
    override suspend fun save(settings: SalahSettings): AppResult<SalahSettings> {
        state.value = settings
        return AppResult.Success(settings)
    }

    fun set(settings: SalahSettings) {
        state.value = settings
    }
}

/**
 * The prayer log as Home observes it: one day the test sets, returned for whatever date is asked.
 * Only [observeDay] carries behaviour the Salah card reads; the rest are inert.
 */
private class FakeSalahTrackingRepository : SalahTrackingRepository {
    private val day = MutableStateFlow(SalahDayTracking(LocalDate.MIN, emptyList(), null))

    fun setDay(tracking: SalahDayTracking) {
        day.value = tracking
    }

    override fun observeDay(date: LocalDate): Flow<SalahDayTracking> = day

    override fun observeMonth(month: YearMonth): Flow<List<SalahDayTracking>> =
        MutableStateFlow(emptyList<SalahDayTracking>())

    override suspend fun daysBetween(from: LocalDate, to: LocalDate) =
        AppResult.Success(emptyList<SalahDayTracking>())

    override suspend fun tahajjudBetween(from: LocalDate, to: LocalDate) =
        AppResult.Success(emptyList<TahajjudTracking>())

    override suspend fun setStatus(
        date: LocalDate,
        prayer: Prayer,
        status: PrayerStatus,
        scheduledAt: Instant?,
        prayedAt: Instant?,
        source: TrackingSource,
        notes: String?,
    ) = AppResult.Success(Unit)

    override suspend fun setTahajjud(
        date: LocalDate,
        status: TahajjudStatus,
        prayedAt: Instant?,
        source: TrackingSource,
        notes: String?,
    ) = AppResult.Success(Unit)

    override suspend fun clearAll() = AppResult.Success(Unit)
}
