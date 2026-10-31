package com.rayluxeo.aida.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.brief.AttentionItem
import com.rayluxeo.aida.domain.brief.BriefDeadline
import com.rayluxeo.aida.domain.brief.BriefEvent
import com.rayluxeo.aida.domain.brief.BriefFollowUp
import com.rayluxeo.aida.domain.brief.BriefInvoice
import com.rayluxeo.aida.domain.brief.BriefMoney
import com.rayluxeo.aida.domain.brief.BriefPayment
import com.rayluxeo.aida.domain.brief.BriefReminder
import com.rayluxeo.aida.domain.brief.DailyBriefEngine
import com.rayluxeo.aida.domain.brief.DailyBriefReport
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.model.DayPart
import com.rayluxeo.aida.domain.model.Task
import com.rayluxeo.aida.domain.model.TaskFilter
import com.rayluxeo.aida.domain.model.TaskPriority
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.repository.TaskCounts
import com.rayluxeo.aida.domain.repository.TaskRepository
import com.rayluxeo.aida.domain.repository.VoicePreferenceRepository
import com.rayluxeo.aida.domain.salah.NextPrayerInfo
import com.rayluxeo.aida.domain.salah.Prayer
import com.rayluxeo.aida.domain.salah.PrayerStatus
import com.rayluxeo.aida.domain.salah.PrayerTimes
import com.rayluxeo.aida.domain.salah.SalahClock
import com.rayluxeo.aida.domain.salah.SalahDay
import com.rayluxeo.aida.domain.salah.SalahDayTracking
import com.rayluxeo.aida.domain.salah.SalahSettings
import com.rayluxeo.aida.domain.salah.SalahSettingsRepository
import com.rayluxeo.aida.domain.salah.SalahTrackingRepository
import com.rayluxeo.aida.domain.salah.WaqtHadithProvider
import com.rayluxeo.aida.domain.usecase.GetQuranProgressUseCase
import com.rayluxeo.aida.domain.usecase.GetSalahDayUseCase
import com.rayluxeo.aida.domain.usecase.SetTaskDoneUseCase
import com.rayluxeo.aida.ui.mira.speechFailureWording
import com.rayluxeo.aida.ui.response.ResponseRenderer
import com.rayluxeo.aida.ui.response.salahPrayerName
import com.rayluxeo.aida.ui.response.salahRemainingLabel
import com.rayluxeo.aida.ui.tasks.TaskTimeFormatter
import com.rayluxeo.aida.ui.tasks.displayLanguage
import com.rayluxeo.aida.voice.AudioFocusPurpose
import com.rayluxeo.aida.voice.MiraAudioFocusManager
import com.rayluxeo.aida.voice.MiraTextToSpeech
import com.rayluxeo.aida.voice.SpeakingState
import com.rayluxeo.aida.voice.SpeechFailure
import com.rayluxeo.aida.voice.VoiceConversationController
import com.rayluxeo.aida.voice.VoiceSessionState
import com.rayluxeo.aida.core.common.getOrNull
import com.rayluxeo.aida.domain.attention.AttentionAction
import com.rayluxeo.aida.domain.attention.AttentionDismissalRepository
import com.rayluxeo.aida.domain.attention.AttentionRecordType
import com.rayluxeo.aida.domain.attention.ProactiveAttentionEngine
import com.rayluxeo.aida.domain.attention.ProactiveAttentionItem
import com.rayluxeo.aida.domain.brief.AttentionTarget
import com.rayluxeo.aida.domain.model.RecordOrigin
import com.rayluxeo.aida.domain.model.ReminderKind
import com.rayluxeo.aida.domain.usecase.CreateReminderUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Home is a live view, not a snapshot.
 *
 * Two clocks run behind it, deliberately. The task list and the four counts come straight from
 * the repository, so ticking something off anywhere in the app moves them at once. Everything the
 * brief engine knows — the money, the diary, the deadlines — is a snapshot, because those are
 * queries across a dozen tables and re-running the lot on every keystroke is the waste the engine's
 * own contract warns about. The cheap half of that engine, [DailyBriefEngine.attention], IS
 * re-asked whenever the task counts move, so the card at the top of the screen never disagrees
 * with the list under it.
 *
 * The draft Abu is typing deliberately lives in the field rather than in this state: putting it
 * here would rebuild every row on Home, and re-ask the engine, once per character.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val briefEngine: DailyBriefEngine,
    private val tasks: TaskRepository,
    private val setTaskDone: SetTaskDoneUseCase,
    private val settings: SettingsRepository,
    private val voicePreferences: VoicePreferenceRepository,
    private val tts: MiraTextToSpeech,
    private val audioFocus: MiraAudioFocusManager,
    private val controller: VoiceConversationController,
    private val renderer: ResponseRenderer,
    private val times: TaskTimeFormatter,
    private val clock: MiraClock,
    private val proactiveAttention: ProactiveAttentionEngine? = null,
    private val createReminder: CreateReminderUseCase? = null,
    private val dismissalRepository: AttentionDismissalRepository? = null,
    /**
     * The Salah seams, all optional so Home builds in a plain unit test without the Salah graph and
     * so the card simply does not appear for an Abu who has not set a prayer location. The card's
     * live countdown comes from these three, not from the brief snapshot: the day's prayer times,
     * today's log, and the settings that gate whether there is anything to show.
     */
    private val salahDay: GetSalahDayUseCase? = null,
    private val salahTracking: SalahTrackingRepository? = null,
    private val salahSettingsRepo: SalahSettingsRepository? = null,
    private val quranProgress: GetQuranProgressUseCase? = null,
) : ViewModel() {


    private val brief = MutableStateFlow(BriefSnapshot())
    private val session = MutableStateFlow(CaptureSession())

    /**
     * Bumped whenever the day's prayer times should be recomputed — on load and on every return to
     * Home. The prayer times are a per-day constant the card derives the countdown from, so they
     * are fetched once per day rather than per tick; the bump is also what carries the card across
     * midnight when Abu happens to be looking at Home as the date turns.
     */
    private val salahReload = MutableStateFlow(0)

    /**
     * One second at a time, and only while Home is actually on screen — the whole chain is
     * [SharingStarted.WhileSubscribed], so nothing ticks behind a closed screen. The card reads
     * `now` off this and recomputes what is left from the wall clock, never a decrementing counter.
     */
    private val salahTicks: Flow<Instant> = flow {
        while (true) {
            emit(clock.now())
            delay(SALAH_TICK_MILLIS)
        }
    }

    /**
     * The Salah card, live and already worded, or null when Abu has not set a prayer location.
     *
     * The day's times are fetched once per [salahReload] (so, once a day and on each resume); the
     * tracking and the one-second tick drive the rest. [kotlinx.coroutines.flow.flatMapLatest]
     * means a settings change or a new day cleanly replaces the inner stream rather than stacking a
     * second ticker on top of it.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val salahCard: Flow<HomeSalah?> = combine(
        salahSettingsRepo?.settings ?: flowOf(SalahSettings()),
        settings.settings,
        salahReload,
    ) { salahPrefs, appPrefs, _ ->
        SalahContext(salahPrefs.isConfigured, appPrefs.languageMode.displayLanguage())
    }.flatMapLatest { context ->
        val useCase = salahDay
        val trackingRepo = salahTracking
        if (!context.configured || useCase == null || trackingRepo == null) {
            flowOf<HomeSalah?>(null)
        } else {
            val date = clock.today()
            val day = useCase(date).getOrNull()
            if (day == null) {
                flowOf<HomeSalah?>(null)
            } else {
                combine(trackingRepo.observeDay(date), salahTicks) { tracking, now ->
                    buildHomeSalah(day, tracking, now, context.language)
                }
            }
        }
    }
        // An immediate null so the rest of Home never waits on the prayer-time fetch: the card
        // simply appears once its data is ready, rather than holding the whole screen loading.
        .onStart { emit(null) }

    private val quranCard: Flow<HomeQuran?> = quranProgress?.observe(clock.today())
        ?.map { progress ->
            HomeQuran(
                currentPage = progress.currentPage,
                continueFromPage = progress.continueFromPage,
                lastReadPageToday = progress.lastReadPageToday,
                pagesReadToday = progress.pagesReadToday,
                dailyTarget = progress.dailyTarget,
                targetMet = progress.targetMet,
                remainingPages = progress.remainingPagesToday,
            )
        } ?: flowOf(HomeQuran())

    /** Mira reading the brief out. Held so a second press can stop her mid-sentence. */
    private var speech: Job? = null

    /** The read in flight, so the screen's first resume does not repeat the one `init` started. */
    private var loading: Job? = null

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            tasks.observe(TaskFilter.OPEN),
            tasks.observeCounts(),
            settings.settings,
            brief,
            session,
        ) { open, counts, preferences, snapshot, capture ->
            CoreInputs(open, counts, preferences.languageMode.displayLanguage(), preferences.userName, snapshot, capture)
        },
        salahCard,
        quranCard,
    ) { core, salah, quran ->
        buildState(
            open = core.open,
            counts = core.counts,
            language = core.language,
            userName = core.userName,
            snapshot = core.snapshot,
            capture = core.capture,
            salah = salah,
            quran = quran,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = HomeUiState(),
    )

    init {
        reload()
        viewModelScope.launch {
            // The first emission arrives with the brief that is already being read, so only the
            // moves after it are worth a second look.
            tasks.observeCounts().drop(1).collect { refreshAttention() }
        }
        viewModelScope.launch {
            tts.state.collect { state ->
                session.update { it.copy(speaking = state == SpeakingState.SPEAKING) }
            }
        }
        // The spoken turn is process-wide and shared with the Mira screen; Home mirrors it so the
        // wave answers a press here without Abu ever leaving the page.
        viewModelScope.launch {
            controller.state.collect { voice -> session.update { it.copy(voice = voice) } }
        }
    }

    /** Re-reads the whole brief. The screen asks for this when Abu comes back to it. */
    fun refresh() {
        reload()
    }

    /**
     * Home lists open work only, so a tap is always a completion. The task leaves the list on the
     * next emission; undoing it is the Tasks screen's job, where the done list is visible.
     */
    fun onCompleteTask(id: Long) {
        viewModelScope.launch {
            when (val result = setTaskDone(id, true)) {
                is AppResult.Success -> session.update { it.copy(error = null) }
                is AppResult.Failure -> session.update { it.copy(error = result.error.code) }
            }
        }
    }

    /**
     * Reads the brief aloud, or stops her if she is already reading it.
     *
     * It goes through the same voice layer as every other spoken reply — audio focus taken for
     * the duration, Abu's own voice and rate, and his setting respected. With speech switched
     * off Mira says so and offers the screen where it is switched back on, rather than a control
     * that quietly does nothing.
     */
    fun onReadBrief() {
        if (session.value.speaking) {
            stopSpeaking()
            return
        }
        viewModelScope.launch {
            if (!voicePreferences.current().spokenResponsesEnabled) {
                session.update { it.copy(voiceOff = true) }
                return@launch
            }
            val snapshot = brief.value
            val report = snapshot.report
            if (report == null) {
                session.update { it.copy(error = snapshot.failure ?: MiraErrorCode.UNKNOWN) }
                return@launch
            }
            val language = settings.current().languageMode.displayLanguage()
            val text = spokenBrief(report, language)
            // The brief is spoken on Home, not written into the Mira conversation: every glance at
            // Home re-read it, and each read appended an identical turn, so the Mira screen opened on
            // the same greeting two or three times over. The transcript is Abu's conversation with
            // Mira; a brief he actually asks her for still logs through the command path.
            speech = viewModelScope.launch {
                audioFocus.withFocus(AudioFocusPurpose.SPEAKING) { tts.speak(text, language) }
            }
        }
    }

    fun onDismissNotice() {
        session.update { it.copy(error = null, voiceOff = false, confirmation = null) }
        brief.update { it.copy(failure = null) }
    }

    fun onAttentionRemindMe(row: HomeAttentionRow) {
        val reminderUseCase = createReminder ?: return
        viewModelScope.launch {
            val language = settings.current().languageMode.displayLanguage()
            val wording = homeWording(language)
            val title = wording.reviewTitle(row.whatHappened)
            val triggerAt = clock.today().plusDays(1).atTime(9, 0).atZone(clock.zone()).toInstant()
            val result = reminderUseCase(
                title = title,
                triggerAt = triggerAt,
                kind = ReminderKind.ONE_TIME,
                origin = RecordOrigin.DERIVED,
            )
            // On failure Abu hears the real reason, not "Reminder set." — claiming a mutating
            // action landed when it did not is the one mistake §5 says he has no way to notice. The
            // answer rides the one notice Home shows above its content, since the capture field that
            // used to carry it is gone.
            when (result) {
                is AppResult.Success -> session.update { it.copy(confirmation = wording.reminderCreated, error = null) }
                is AppResult.Failure -> session.update { it.copy(error = result.error.code) }
            }
        }
    }

    fun onAttentionMarkDone(row: HomeAttentionRow) {
        val taskId = row.relatedRecordId ?: return
        onCompleteTask(taskId)
        onAttentionDismiss(row)
    }

    fun onAttentionDismiss(row: HomeAttentionRow) {
        viewModelScope.launch {
            proactiveAttention?.dismiss(row.key)
            dismissalRepository?.dismiss(row.key)
            refreshAttention()
        }
    }

    override fun onCleared() {
        stopSpeaking()
    }

    private fun reload() {
        // The Salah card's day is refetched here too, which is what carries it over midnight when
        // Abu returns to Home on a new date.
        salahReload.update { it + 1 }
        if (loading?.isActive == true) return
        loading = viewModelScope.launch {
            val language = settings.current().languageMode.displayLanguage()
            val proactive = proactiveAttention?.detectItems()?.getOrNull()?.map { item ->
                item.toHomeAttentionRow(language)
            }
            when (val result = briefEngine.today()) {
                is AppResult.Success -> brief.value = BriefSnapshot(
                    report = result.value,
                    attention = result.value.attention,
                    proactiveRows = proactive,
                    loaded = true,
                )
                // The last brief she managed to read is left standing: a stale picture with a
                // notice above it tells Abu more than a screen that empties itself.
                is AppResult.Failure -> brief.update {
                    it.copy(failure = result.error.code, proactiveRows = proactive, loaded = true)
                }
            }
        }
    }

    private suspend fun refreshAttention() {
        val language = settings.current().languageMode.displayLanguage()
        val proactive = proactiveAttention?.detectItems()?.getOrNull()?.map { item ->
            item.toHomeAttentionRow(language)
        }
        if (proactive != null) {
            brief.update { it.copy(proactiveRows = proactive, failure = null) }
        }
        when (val result = briefEngine.attention()) {
            is AppResult.Success -> brief.update { it.copy(attention = result.value, failure = null) }
            is AppResult.Failure -> brief.update { it.copy(failure = result.error.code) }
        }
    }

    private fun stopSpeaking() {
        speech?.cancel()
        speech = null
        tts.stop()
    }

    /**
     * The concise version. Everything Home shows is on the screen already, so reading all of it
     * out would be tiresome; what is spoken is the day, the next thing in it and the money, with
     * the deadlines and the follow-ups arriving as the attention lines they produced.
     */
    private fun spokenBrief(report: DailyBriefReport, language: SpokenLanguage): String {
        val wording = homeWording(language)
        val lines = buildList {
            add(wording.greeting(report.dayPart, report.ownerName))
            if (report.isQuiet) {
                add(wording.spokenQuiet)
            } else {
                add(wording.spokenTasks(report.dueTodayCount, report.overdueCount, report.openTaskCount))
                // Said aloud, because silence here is the harmful case: no event and no sentence
                // is indistinguishable from a clear day, and Mira may simply not have been allowed
                // to look.
                if (!report.calendarReadable) {
                    add(wording.calendarUnreadable)
                } else {
                    report.nextEvent?.let {
                        add(wording.spokenEvent(it.title, momentOf(it, language)))
                    }
                }
                report.upcomingReminders.firstOrNull()?.let {
                    add(wording.spokenReminder(it.title, times.momentLabel(it.triggerAt, language)))
                }
                report.attention.take(SPOKEN_ATTENTION_COUNT).forEach {
                    add(wording.spokenAttention(it.kind, it.subject, it.daysAway))
                }
            }
            wording.spokenMoney(report.money.outstandingTotal, report.money.overdueCount)
                .takeIf { it.isNotEmpty() }
                ?.let { add(it) }
            // One line for the next prayer, said even on a quiet day: the prayer time is a fact
            // worth a sentence regardless of what else the morning holds.
            report.salah?.let { salah ->
                add(
                    wording.spokenSalah(
                        prayerName = salahPrayerName(salah.nextPrayer, language),
                        moment = times.timeLabel(
                            LocalDateTime.ofInstant(salah.nextTime, clock.zone()).toLocalTime(),
                            language,
                        ),
                        tomorrow = salah.isTomorrow,
                    ),
                )
            }
        }
        return lines.joinToString(" ")
    }

    private fun buildState(
        open: List<Task>,
        counts: TaskCounts,
        language: SpokenLanguage,
        userName: String,
        snapshot: BriefSnapshot,
        capture: CaptureSession,
        salah: HomeSalah?,
        quran: HomeQuran? = null,
    ): HomeUiState {
        val wording = homeWording(language)
        val report = snapshot.report
        val today = open.filter { times.isOverdue(it.dueAt) || times.isDueToday(it.dueAt) }
        val section = if (today.isEmpty()) HomeSection.OPEN else HomeSection.TODAY
        val shown = (if (today.isEmpty()) open else today).take(MAX_HOME_TASKS)

        val owner = report?.ownerName ?: userName.trim().takeIf { it.isNotEmpty() }
        val greetingText = when {
            snapshot.failure != null -> ""
            else -> wording.greeting(report?.dayPart ?: dayPartFor(clock.localNow().hour), owner)
        }

        val hadith = salah?.hadith ?: run {
            val date = clock.today()
            val localTime = clock.localNow().toLocalTime()
            val fallbackPrayer = approximatePrayer(localTime)
            val rawHadith = WaqtHadithProvider.forPrayer(fallbackPrayer, date)
            HomeHadith(
                prayer = fallbackPrayer,
                prayerName = salahPrayerName(fallbackPrayer, language),
                arabic = rawHadith.arabic,
                bangla = rawHadith.bangla,
                reference = rawHadith.reference,
            )
        }

        return HomeUiState(
            isLoading = !snapshot.loaded,
            language = language,
            greeting = greetingText,
            dateLine = times.dateHeadline(report?.date ?: clock.today(), language),
            attention = snapshot.proactiveRows ?: snapshot.attention.mapIndexed { index, item ->
                item.toRow(index, wording, language)
            },
            nextEvent = report?.nextEvent?.toRow(wording, language),
            calendarUnreadable = report != null && !report.calendarReadable,
            section = section,
            tasks = shown.map { it.toHomeTask(language) },
            moreTaskCount = (counts.open - shown.size).coerceAtLeast(0),
            // A row of four noughts above an empty list says nothing Abu cannot already see, and
            // he would scroll past it every quiet morning. The card returns the moment there is a
            // single number in it.
            summary = HomeSummary(
                open = counts.open,
                dueToday = counts.dueToday,
                overdue = counts.overdue,
                completedToday = counts.completedToday,
            ).takeIf { it.hasAnything },
            reminders = report?.upcomingReminders.orEmpty().map { it.toRow(language) },
            money = report?.money?.toRow(wording, language)?.takeUnless { it.isEmpty },
            salah = salah,
            hadith = hadith,
            quran = quran,
            deadlines = report?.deadlines.orEmpty().map { it.toRow(wording, language) },
            followUps = report?.followUps.orEmpty().map { it.toRow(wording) },
            isSpeaking = capture.speaking,
            voice = capture.voice,
            notice = notice(wording, language, capture, snapshot.failure),
        )
    }

    /**
     * One line at a time, and a failure outranks a setting: Abu can only act on one of them, and
     * the one he cannot have expected goes first.
     */
    private fun notice(
        wording: HomeWording,
        language: SpokenLanguage,
        capture: CaptureSession,
        briefFailure: MiraErrorCode?,
    ): HomeNotice? {
        val problem = capture.error ?: briefFailure
        return when {
            problem != null -> HomeNotice(
                text = renderer.display(MiraResponse.Problem(problem), language),
                isProblem = true,
            )
            capture.voiceOff -> HomeNotice(
                text = wording.spokenRepliesOff,
                isProblem = false,
                action = HomeNoticeAction.OPEN_SETTINGS,
            )
            // A one-line "done" from a card action — a reminder set from the attention list — shown
            // the same quiet way a failure would be, and cleared the same way.
            capture.confirmation != null -> HomeNotice(
                text = capture.confirmation,
                isProblem = false,
            )
            else -> null
        }
    }

    private fun AttentionItem.toRow(
        index: Int,
        wording: HomeWording,
        language: SpokenLanguage,
    ): HomeAttentionRow = HomeAttentionRow(
        // The engine names no key of its own and two items may share a subject, so the position
        // in the list it just built is the only identifier that cannot collide.
        key = "$index:${kind.name}:$subject",
        text = wording.attentionLine(kind, subject, daysAway),
        detail = detailOf(wording, language),
        severity = severity,
        target = reference?.type,
    )

    private fun AttentionItem.detailOf(wording: HomeWording, language: SpokenLanguage): String? {
        val figure = amount?.let { wording.amount(it) }
        val date = at?.let {
            times.dateLabel(LocalDateTime.ofInstant(it, clock.zone()).toLocalDate(), language)
        }
        return listOfNotNull(figure, date).joinToString(" · ").ifEmpty { null }
    }

    private fun BriefEvent.toRow(wording: HomeWording, language: SpokenLanguage) = HomeEvent(
        title = title,
        detail = wording.eventDetail(momentOf(this, language), location?.cleaned()),
    )

    /** An all-day entry has no time worth saying, so it is named by its day instead. */
    private fun momentOf(event: BriefEvent, language: SpokenLanguage): String = if (event.allDay) {
        times.dateLabel(LocalDateTime.ofInstant(event.startsAt, clock.zone()).toLocalDate(), language)
    } else {
        times.momentLabel(event.startsAt, language)
    }

    private fun BriefReminder.toRow(language: SpokenLanguage) = HomeReminder(
        id = id,
        title = title,
        whenLabel = times.momentLabel(triggerAt, language),
    )

    private fun BriefMoney.toRow(wording: HomeWording, language: SpokenLanguage) = HomeMoney(
        // Owing nothing is said by leaving the line out, the same way nothing-overdue is below.
        // The engine returns a real zero for an empty ledger, so without this guard Home shows
        // "৳0 outstanding" to a man who has no unpaid invoices.
        outstanding = outstandingTotal?.takeIf { invoiceCount > 0 }?.let { wording.amount(it) },
        outstandingDetail = outstandingTotal
            ?.takeIf { invoiceCount > 0 }
            ?.let { wording.acrossInvoices(invoiceCount) },
        // Nothing overdue is said by leaving the line out, not by showing a nought beside it.
        overdue = overdueTotal?.takeIf { overdueCount > 0 }?.let { wording.amount(it) },
        overdueDetail = if (overdueCount > 0) wording.overdueInvoices(overdueCount) else null,
        dueSoon = dueSoon.map { it.toRow(wording, language) },
        recentPayments = recentPayments.map { it.toRow(wording, language) },
    )

    private fun BriefInvoice.toRow(wording: HomeWording, language: SpokenLanguage) = HomeInvoice(
        id = id,
        title = wording.invoiceTitle(number.trim(), clientName?.cleaned()),
        detail = wording.invoiceDetail(amount, dueOn?.let { times.dateLabel(it, language) }, isOverdue),
        isOverdue = isOverdue,
    )

    private fun BriefPayment.toRow(wording: HomeWording, language: SpokenLanguage) = HomePayment(
        id = id,
        title = clientName?.cleaned() ?: wording.payment,
        detail = wording.paymentDetail(amount, times.momentLabel(receivedAt, language)),
    )

    private fun BriefDeadline.toRow(wording: HomeWording, language: SpokenLanguage) = HomeDeadline(
        id = projectId,
        // The milestone is what separates a project's own deadline from its next milestone, so it
        // has to be in the key. Keying on the project alone crashes the list.
        key = "deadline-$projectId-${milestone.orEmpty()}",
        title = projectName,
        detail = wording.deadlineDetail(
            clientName = clientName?.cleaned(),
            milestone = milestone?.cleaned(),
            dueLabel = times.dateLabel(dueOn, language),
        ),
    )

    private fun BriefFollowUp.toRow(wording: HomeWording) = HomeFollowUp(
        id = clientId,
        title = clientName,
        detail = wording.followUpDetail(daysSince?.coerceAtLeast(0)),
    )

    private fun Task.toHomeTask(language: SpokenLanguage): HomeTask {
        val overdue = times.isOverdue(dueAt)
        return HomeTask(
            id = id,
            title = title,
            // Already carries the overdue word, in the same language as the moment beside it.
            supporting = dueAt?.let { times.dueLabel(it, language, overdue) },
            isOverdue = overdue,
            isHighPriority = priority == TaskPriority.HIGH,
        )
    }

    /**
     * The card's worded state for one tick: the next prayer, the live countdown and the five dots.
     * [SalahClock] does the timing — it returns tomorrow's Fajr after Isha, so the countdown stays
     * right across the night — and the wording comes from the shared Salah phrasebook so the card
     * and the brief name a prayer the same way.
     */
    private fun buildHomeSalah(
        day: SalahDay,
        tracking: SalahDayTracking,
        now: Instant,
        language: SpokenLanguage,
    ): HomeSalah {
        val info = SalahClock.nextPrayer(now, day.times, day.tomorrowFajr)
        val activePrayer = info.current
        val rawHadith = WaqtHadithProvider.forPrayer(activePrayer, day.date)
        val hadith = HomeHadith(
            prayer = activePrayer,
            prayerName = salahPrayerName(activePrayer, language),
            arabic = rawHadith.arabic,
            bangla = rawHadith.bangla,
            reference = rawHadith.reference,
        )
        return HomeSalah(
            nextName = salahPrayerName(info.next, language),
            nextTimeLabel = times.momentLabel(info.nextTime.toInstant(), language),
            remainingLabel = salahRemainingLabel(info.remaining, language),
            progress = salahProgress(now, info, day.times),
            dots = Prayer.OBLIGATORY.map { prayer ->
                HomeSalahDot(
                    label = salahPrayerName(prayer, language),
                    state = dotStateOf(prayer, tracking.statusOf(prayer), day.times, now),
                )
            },
            hadith = hadith,
        )
    }

    /**
     * Approximates the current prayer waqt by local time when an astronomical calculation is not yet available.
     */
    private fun approximatePrayer(time: LocalTime): Prayer {
        val minuteOfDay = time.hour * 60 + time.minute
        return when {
            minuteOfDay < 5 * 60 + 15 -> Prayer.ISHA
            minuteOfDay < 12 * 60 -> Prayer.FAJR
            minuteOfDay < 15 * 60 + 30 -> Prayer.DHUHR
            minuteOfDay < 18 * 60 -> Prayer.ASR
            minuteOfDay < 19 * 60 + 30 -> Prayer.MAGHRIB
            else -> Prayer.ISHA
        }
    }

    /**
     * A dot's look, derived from the log and the clock together rather than the stored status
     * alone — the default NOT_RECORDED cannot say whether a prayer is still ahead or already past
     * its time. A prayer logged as prayed is [HomeSalahDotState.PRAYED]; one still ahead is
     * [HomeSalahDotState.UPCOMING]; anything past its time and not logged is
     * [HomeSalahDotState.NOT_YET] — never "missed", which Home does not say (Abu's instruction).
     */
    private fun dotStateOf(
        prayer: Prayer,
        status: PrayerStatus,
        prayerTimes: PrayerTimes,
        now: Instant,
    ): HomeSalahDotState = when {
        status.isCompleted -> HomeSalahDotState.PRAYED
        prayerTimes.time(prayer)?.toInstant()?.isAfter(now) == true -> HomeSalahDotState.UPCOMING
        else -> HomeSalahDotState.NOT_YET
    }

    /**
     * How far Abu is through the window leading to the next prayer, 0..1. The window runs from the
     * current prayer's time to the next one; before Fajr the "current" prayer is the night's Isha,
     * whose scheduled time today is in the evening — later than the Fajr being counted to — so it
     * is pulled back a day to keep the window the right way round.
     */
    private fun salahProgress(now: Instant, info: NextPrayerInfo, prayerTimes: PrayerTimes): Float {
        val nextMillis = info.nextTime.toInstant().toEpochMilli()
        val windowStart = prayerTimes.time(info.current) ?: info.nextTime
        val start = if (!windowStart.toInstant().isBefore(info.nextTime.toInstant())) {
            windowStart.minusDays(1)
        } else {
            windowStart
        }
        val startMillis = start.toInstant().toEpochMilli()
        if (nextMillis <= startMillis) return 0f
        return ((now.toEpochMilli() - startMillis).toFloat() / (nextMillis - startMillis)).coerceIn(0f, 1f)
    }

    /** The five pieces the main state is built from, folded into one so the two combines nest. */
    private data class CoreInputs(
        val open: List<Task>,
        val counts: TaskCounts,
        val language: SpokenLanguage,
        val userName: String,
        val snapshot: BriefSnapshot,
        val capture: CaptureSession,
    )

    /** Whether the Salah card should appear, and the language to word it in. */
    private data class SalahContext(val configured: Boolean, val language: SpokenLanguage)

    /** What the brief engine last managed to tell Mira, and whether it managed at all. */
    private data class BriefSnapshot(
        val report: DailyBriefReport? = null,
        val attention: List<AttentionItem> = emptyList(),
        val proactiveRows: List<HomeAttentionRow>? = null,
        val failure: MiraErrorCode? = null,
        val loaded: Boolean = false,
    )

    /**
     * Words a detected attention item in Abu's language. The engine carries only facts
     * (AGENTS.md §4 rule 6); the two lines Abu reads are assembled here from those facts.
     */
    private fun ProactiveAttentionItem.toHomeAttentionRow(language: SpokenLanguage): HomeAttentionRow {
        val wording = homeWording(language)
        val (what, why) = proactiveAttentionText(
            item = this,
            language = language,
            amountText = amount?.let { wording.amount(it) },
            secondaryText = secondaryAmount?.let { wording.amount(it) },
            dueText = dueOn?.let { times.dateLabel(it, language) },
            number = { wording.number(it.toInt()) },
        )
        return HomeAttentionRow(
            key = id,
            text = what,
            detail = why,
            severity = when (severity) {
                com.rayluxeo.aida.domain.attention.AttentionSeverity.CRITICAL ->
                    com.rayluxeo.aida.domain.brief.AttentionSeverity.NOW
                com.rayluxeo.aida.domain.attention.AttentionSeverity.WARNING ->
                    com.rayluxeo.aida.domain.brief.AttentionSeverity.SOON
                com.rayluxeo.aida.domain.attention.AttentionSeverity.NOTICE ->
                    com.rayluxeo.aida.domain.brief.AttentionSeverity.WATCH
            },
            target = when (relatedRecord.type) {
                AttentionRecordType.INVOICE -> AttentionTarget.INVOICE
                AttentionRecordType.TASK -> AttentionTarget.TASK
                AttentionRecordType.PROJECT -> AttentionTarget.PROJECT
                AttentionRecordType.CLIENT -> AttentionTarget.CLIENT
                AttentionRecordType.SUBSCRIPTION -> AttentionTarget.SUBSCRIPTION
                AttentionRecordType.EXPENSE -> null
            },
            whatHappened = what,
            whyItMatters = why,
            relatedRecordId = relatedRecord.id,
            relatedRecordTitle = relatedRecord.title,
            availableActions = availableActions,
        )
    }


    private data class CaptureSession(
        val speaking: Boolean = false,
        val error: MiraErrorCode? = null,
        /** Abu asked for the brief out loud with Mira's voice switched off. */
        val voiceOff: Boolean = false,
        /** The shared spoken turn, mirrored so her face can read it. */
        val voice: VoiceSessionState = VoiceSessionState(),
        /** A one-line "done" from a card action, shown above the content until dismissed. */
        val confirmation: String? = null,
    )

    private companion object {
        /** Enough to be useful at a glance; past this Home turns into the Tasks screen. */
        const val MAX_HOME_TASKS = 5

        /** Two is the most that can be read out before a spoken brief stops being one. */
        const val SPOKEN_ATTENTION_COUNT = 2

        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L

        /** One tick a second for the Salah countdown; the label changes once a minute. */
        const val SALAH_TICK_MILLIS = 1_000L
    }
}

private fun dayPartFor(hour: Int): DayPart = when (hour) {
    in 5..11 -> DayPart.MORNING
    in 12..16 -> DayPart.AFTERNOON
    in 17..20 -> DayPart.EVENING
    else -> DayPart.NIGHT
}

/** Blank is the same as absent in everything Abu types, and neither earns a line of its own. */
private fun String?.cleaned(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
