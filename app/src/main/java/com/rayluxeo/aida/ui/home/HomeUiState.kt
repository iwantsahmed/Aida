package com.rayluxeo.aida.ui.home

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.attention.AttentionAction
import com.rayluxeo.aida.domain.brief.AttentionSeverity
import com.rayluxeo.aida.domain.brief.AttentionTarget
import com.rayluxeo.aida.domain.salah.Prayer
import com.rayluxeo.aida.voice.VoiceSessionState

/**
 * Everything Home shows, already worded.
 *
 * It is lifted out of the view model because it is read by three files now — the screen, the
 * attention card and the capture field — and a state that lives inside the class that builds it
 * makes every one of them import the view model to name a row.
 *
 * Every string here is in Abu's language by the time it lands: the view model words the parts
 * that depend on data (amounts, dates, counts) through [HomeWording] and the formatter, and the
 * screen words its own fixed chrome. A section with nothing in it is EMPTY rather than zeroed,
 * because Home collapses what it has nothing to say about instead of showing him a nought every
 * morning.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val language: SpokenLanguage = SpokenLanguage.ENGLISH,
    /** "Good morning, Abu." Empty only while the brief has not been read yet. */
    val greeting: String = "",
    val dateLine: String = "",
    val attention: List<HomeAttentionRow> = emptyList(),
    val nextEvent: HomeEvent? = null,
    /**
     * True when Mira could not read the calendar at all, which is a different fact from there
     * being nothing in the diary and is said differently.
     */
    val calendarUnreadable: Boolean = false,
    val section: HomeSection = HomeSection.TODAY,
    val tasks: List<HomeTask> = emptyList(),
    /** Open tasks beyond the few Home shows, so the way through to Tasks can say how many. */
    val moreTaskCount: Int = 0,
    val summary: HomeSummary? = null,
    val reminders: List<HomeReminder> = emptyList(),
    val money: HomeMoney? = null,
    /**
     * The next prayer and today's five, already worded. Null when Abu has not set a prayer
     * location — Home then shows no Salah card at all, rather than a dead or fabricated one.
     */
    val salah: HomeSalah? = null,
    /**
     * The authentic Hadith for the active prayer waqt, worded and ready for display.
     */
    val hadith: HomeHadith? = null,
    /**
     * Daily Quran recitation target and progress for Home.
     */
    val quran: HomeQuran? = null,
    val deadlines: List<HomeDeadline> = emptyList(),
    val followUps: List<HomeFollowUp> = emptyList(),
    /** Mira is reading the brief out, and tapping her face becomes a way to stop her. */
    val isSpeaking: Boolean = false,
    /**
     * The shared, process-wide spoken turn, mirrored so her face can answer it. The avatar reads
     * [VoiceSessionState.amplitude] and [VoiceSessionState.state] straight off it.
     */
    val voice: VoiceSessionState = VoiceSessionState(),
    val notice: HomeNotice? = null,
) {
    val isEmpty: Boolean get() = !isLoading && tasks.isEmpty()
}

/** Which group of open work Home is currently showing, so the section can name itself honestly. */
enum class HomeSection { TODAY, OPEN }

/** The four numbers that describe Abu's day. Live from the repository, not from the brief snapshot. */
data class HomeSummary(
    val open: Int,
    val dueToday: Int,
    val overdue: Int,
    val completedToday: Int,
) {
    /** Whether there is a single number worth showing. Four noughts is not a summary. */
    val hasAnything: Boolean get() = open > 0 || dueToday > 0 || overdue > 0 || completedToday > 0
}

data class HomeTask(
    val id: Long,
    val title: String,
    /** Already worded in Abu's language, overdue prefix included; null when there is no due moment. */
    val supporting: String?,
    val isOverdue: Boolean,
    val isHighPriority: Boolean,
)

data class HomeReminder(
    val id: Long,
    val title: String,
    val whenLabel: String,
)

data class HomeEvent(
    val title: String,
    /** The time, and the place when the entry carries one. */
    val detail: String,
)

/**
 * One line of the Needs Attention card.
 *
 * [text] carries the severity in words, so the card reads correctly in a screenshot with the
 * colour stripped out; [severity] only decides how loudly it is tinted. [target] is what the row
 * opens — null when the item pointed at no record, in which case the tap hands it to Mira.
 */
data class HomeAttentionRow(
    val key: String,
    val text: String,
    val detail: String?,
    val severity: AttentionSeverity,
    val target: AttentionTarget?,
    val whatHappened: String = text,
    val whyItMatters: String = detail.orEmpty(),
    val relatedRecordId: Long? = null,
    val relatedRecordTitle: String? = null,
    val availableActions: List<AttentionAction> = emptyList(),
)


data class HomeInvoice(
    val id: Long,
    val title: String,
    val detail: String,
    val isOverdue: Boolean,
)

data class HomePayment(
    val id: Long,
    val title: String,
    val detail: String,
)

/**
 * [id] is the project, which is deliberately NOT unique here: one project contributes a row for
 * its own due date and another for its nearest unmet milestone. [key] is what the list is keyed
 * on, because two rows sharing a key is not a cosmetic problem — `LazyColumn` throws.
 */
data class HomeDeadline(
    val id: Long,
    val key: String,
    val title: String,
    val detail: String,
)

data class HomeFollowUp(
    val id: Long,
    val title: String,
    val detail: String,
)

/**
 * What Abu is owed, as of the last time Mira looked.
 *
 * Every figure arrives here as a worded [String] built from `Money` — the amount is never turned
 * into a number the screen has to format, because the only formatting a screen could do is the
 * `Double` one AGENTS.md §8 forbids.
 */
data class HomeMoney(
    val outstanding: String?,
    val outstandingDetail: String?,
    val overdue: String?,
    val overdueDetail: String?,
    val dueSoon: List<HomeInvoice>,
    val recentPayments: List<HomePayment>,
) {
    val isEmpty: Boolean
        get() = outstanding == null && overdue == null && dueSoon.isEmpty() && recentPayments.isEmpty()
}

/**
 * The Salah card as Home shows it, every string already in Abu's language.
 *
 * The countdown is live: the view model recomputes [remainingLabel] and [progress] from the wall
 * clock on each tick, never from a stored number that decrements (the pattern the timers screen
 * uses, and the one thing that keeps a countdown honest after the process is rebuilt). The card is
 * present only when a prayer location is set, so there is no "not configured" state to render here.
 */
data class HomeSalah(
    /** The next prayer's name, e.g. "Dhuhr" / "যোহর". */
    val nextName: String,
    /** When it falls, with the day when that is not today, e.g. "Tomorrow, 4:38 am". */
    val nextTimeLabel: String,
    /** The live countdown, e.g. "1h 24m remaining" / "১ ঘণ্টা ২৪ মিনিট বাকি". */
    val remainingLabel: String,
    /** How far through the current window Abu is, 0..1, for the thin indicator under the line. */
    val progress: Float,
    /** The five obligatory prayers in order, each with its status for the day. */
    val dots: List<HomeSalahDot>,
    /** The Hadith for the active prayer waqt. */
    val hadith: HomeHadith? = null,
)

/**
 * One authentic Hadith for the active prayer waqt, worded for Home.
 */
data class HomeHadith(
    val prayer: Prayer,
    val prayerName: String,
    val arabic: String,
    val bangla: String,
    val reference: String,
)

/**
 * Daily Quran recitation progress for Home, tracking the minimum 2-page target.
 */
data class HomeQuran(
    val currentPage: Int = 1,
    val continueFromPage: Int = currentPage,
    val lastReadPageToday: Int? = null,
    val pagesReadToday: Int = 0,
    val dailyTarget: Int = 2,
    val targetMet: Boolean = false,
    val remainingPages: Int = 2,
) {
    val progressRatio: Float
        get() = (pagesReadToday.toFloat() / dailyTarget.coerceAtLeast(1)).coerceIn(0f, 1f)
}

/** One of the five dots: the prayer's short name and where it stands today. */
data class HomeSalahDot(val label: String, val state: HomeSalahDotState)

/**
 * The three states a dot can show. Deliberately not a 1:1 of [com.rayluxeo.aida.domain.salah.PrayerStatus]:
 * Home never tells Abu a prayer was "missed" (his instruction), so anything past its time and not
 * logged is simply [NOT_YET], and anything still ahead is [UPCOMING].
 */
enum class HomeSalahDotState { PRAYED, NOT_YET, UPCOMING }

/**
 * One thing Home has to say that is not part of the day itself — a failure, a setting standing in
 * the way of something Abu just asked for, or a one-line confirmation that something he asked for
 * from a card has landed.
 */
data class HomeNotice(
    val text: String,
    val isProblem: Boolean,
    val action: HomeNoticeAction? = null,
)

/** The one way forward a notice can offer. Anything else would be a control with nowhere to go. */
enum class HomeNoticeAction { OPEN_SETTINGS }
