package com.rayluxeo.aida.ui.salah

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.salah.Prayer
import com.rayluxeo.aida.domain.salah.PrayerStatus
import com.rayluxeo.aida.domain.salah.TahajjudStatus

/**
 * Everything the Salah main screen draws from.
 *
 * The screen is language-free data plus already-worded strings: the live strings (the countdown,
 * the prayer times, the dates) are rendered in the view model because they depend on the injected
 * clock and formatter, while the static chrome and the status words are rendered in the screen from
 * the same [SalahWording]. [isConfigured] false means no location is set yet — the screen then shows
 * a calm setup prompt rather than a wrong time (§12, no fake data).
 */
data class SalahUiState(
    val isLoading: Boolean = true,
    val isConfigured: Boolean = true,
    val language: SpokenLanguage = SpokenLanguage.ENGLISH,
    val gregorianDate: String = "",
    val hijriDate: String = "",
    val locationLabel: String = "",
    val hero: SalahHeroUi? = null,
    val prayers: List<PrayerRowUi> = emptyList(),
    val tahajjud: TahajjudCardUi? = null,
    val hadith: WaqtHadithUi? = null,
    val notice: String? = null,
)

/**
 * An authentic Hadith for the active prayer waqt.
 */
data class WaqtHadithUi(
    val prayer: Prayer,
    val prayerName: String,
    val arabic: String,
    val bangla: String,
    val reference: String,
)

/**
 * The hero: which prayer's window is open now, which one is next, when it falls and how long is
 * left. [countdown] is re-rendered every tick from the clock — never a stored, decrementing number,
 * so it stays correct across a process death exactly as the timer countdowns do.
 */
data class SalahHeroUi(
    val currentPrayer: Prayer,
    val nextPrayer: Prayer,
    val nextPrayerName: String,
    val nextPrayerTime: String,
    val untilText: String,
    val countdown: String,
)

/**
 * One obligatory prayer's row. [storedStatus] is what the log holds (so the status sheet can show
 * which choice is current); [displayStatus] is what the circle shows, which fills in the gentle
 * in-between states — [PrayerStatus.UPCOMING] before its time, [PrayerStatus.NOT_YET] after — that
 * the log leaves as [PrayerStatus.NOT_RECORDED].
 */
data class PrayerRowUi(
    val prayer: Prayer,
    val name: String,
    val time: String,
    val storedStatus: PrayerStatus,
    val displayStatus: PrayerStatus,
    val statusLabel: String,
) {
    val isCompleted: Boolean get() = displayStatus.isCompleted
}

/**
 * The Tahajjud card, kept apart from the five on purpose: it is voluntary and never folded into the
 * obligatory score. [windowLabel] is the recommended last-third window ("2:14 am — 4:38 am"),
 * presented as a window and never as a required minute (Abu's instruction).
 */
data class TahajjudCardUi(
    val status: TahajjudStatus,
    val statusLabel: String,
    val windowLabel: String,
)
