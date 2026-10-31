package com.rayluxeo.aida.ui.salah

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.salah.HijriDate
import com.rayluxeo.aida.domain.salah.Prayer
import com.rayluxeo.aida.domain.salah.PrayerStatus
import com.rayluxeo.aida.domain.salah.TahajjudStatus
import com.rayluxeo.aida.ui.response.bengaliNumerals
import java.time.DayOfWeek
import java.time.Duration
import java.time.YearMonth

/**
 * Every word the Salah screens put in front of Abu, in one language.
 *
 * Wall-clock times (a prayer's time, the Tahajjud window) are worded by the shared
 * [com.rayluxeo.aida.ui.tasks.TaskTimeFormatter] so a 7 pm on a prayer row and a 7 pm on a task
 * read the same; what lives here is everything Salah-specific — the prayer names, the status
 * words, the Hijri month names, the live countdown and the month-summary labels. The tone is the
 * calm, non-judgemental one Abu asked for: a prayer that has not been logged is "not yet", never a
 * failure (§1, and [PrayerStatus]).
 */
internal interface SalahWording {

    val screenTitle: String
    val monthTitle: String

    val setUpTitle: String
    val setUpBody: String
    val setUpAction: String

    val monthView: String
    val tahajjudTitle: String
    val lastThird: String
    val setReminder: String

    val chooseStatus: String
    val cancel: String
    val trackingFailed: String

    /** The obligatory/night prayer's name. */
    fun prayerName(prayer: Prayer): String

    /** How an obligatory prayer stands, worded gently. */
    fun statusLabel(status: PrayerStatus): String

    /** Tahajjud's own, smaller set of states. */
    fun tahajjudStatusLabel(status: TahajjudStatus): String

    /** "Until Fajr" — the line that captions the live countdown in the hero. */
    fun until(prayerName: String): String

    /** The hero countdown as HH:MM:SS, in the language's own digits. */
    fun countdown(remaining: Duration): String

    /** The computed Hijri date as "10 Rabi al-Thani 1447", month named per language. */
    fun hijriLabel(hijri: HijriDate): String

    // --- Month view ---------------------------------------------------------------------------

    val summaryTitle: String
    val byPrayerTitle: String
    val completedTitle: String
    val daysAllFiveTitle: String
    val currentStreakTitle: String
    val bestStreakTitle: String
    val missedTitle: String
    val tahajjudNightsTitle: String
    val tahajjudStreakTitle: String
    val previousMonth: String
    val nextMonth: String

    /** "October 2026", the Gregorian month the grid shows. */
    fun monthYearLabel(month: YearMonth): String

    /** The one-letter-ish weekday headings above the grid. */
    fun weekdayNarrow(day: DayOfWeek): String

    /** A plain count in the language's own digits. */
    fun number(value: Int): String

    /** "18 of 30" — a tally against its possible total. */
    fun completedOf(done: Int, total: Int): String

    /** "12 days" / "12 nights" — a streak or a night count with its unit. */
    fun days(value: Int): String
    fun nights(value: Int): String

    fun percentage(value: Int): String
}

internal object EnglishSalahWording : SalahWording {

    override val screenTitle = "Salah"
    override val monthTitle = "Monthly Salah"

    override val setUpTitle = "Set up Salah"
    override val setUpBody =
        "Add your location so Mira can show prayer times and track your Salah — all on your device."
    override val setUpAction = "Set up"

    override val monthView = "Monthly view"
    override val tahajjudTitle = "Tahajjud"
    override val lastThird = "Last third"
    override val setReminder = "Set reminder"

    override val chooseStatus = "Mark prayer"
    override val cancel = "Cancel"
    override val trackingFailed = "Couldn't save that just now."

    override fun prayerName(prayer: Prayer) = when (prayer) {
        Prayer.FAJR -> "Fajr"
        Prayer.DHUHR -> "Dhuhr"
        Prayer.ASR -> "Asr"
        Prayer.MAGHRIB -> "Maghrib"
        Prayer.ISHA -> "Isha"
        Prayer.TAHAJJUD -> "Tahajjud"
    }

    override fun statusLabel(status: PrayerStatus) = when (status) {
        PrayerStatus.NOT_RECORDED -> "Not recorded"
        PrayerStatus.UPCOMING -> "Upcoming"
        PrayerStatus.NOT_YET -> "Not yet"
        PrayerStatus.PRAYED -> "Prayed"
        PrayerStatus.PRAYED_LATE -> "Prayed late"
        PrayerStatus.MISSED -> "Missed"
    }

    override fun tahajjudStatusLabel(status: TahajjudStatus) = when (status) {
        TahajjudStatus.NOT_RECORDED -> "Not recorded"
        TahajjudStatus.PRAYED -> "Prayed"
        TahajjudStatus.NOT_PRAYED -> "Not prayed"
    }

    override fun until(prayerName: String) = "Until $prayerName"

    override fun countdown(remaining: Duration) = formatCountdown(remaining, bengali = false)

    override fun hijriLabel(hijri: HijriDate) =
        "${hijri.day} ${HIJRI_MONTHS_EN[hijri.month - 1]} ${hijri.year} AH"

    override val summaryTitle = "This month"
    override val byPrayerTitle = "By prayer"
    override val completedTitle = "Completed"
    override val daysAllFiveTitle = "Days all five"
    override val currentStreakTitle = "Current streak"
    override val bestStreakTitle = "Best streak"
    override val missedTitle = "Missed"
    override val tahajjudNightsTitle = "Nights this month"
    override val tahajjudStreakTitle = "Current streak"
    override val previousMonth = "Previous month"
    override val nextMonth = "Next month"

    override fun monthYearLabel(month: YearMonth) =
        "${MONTHS_EN[month.monthValue - 1]} ${month.year}"

    override fun weekdayNarrow(day: DayOfWeek) = WEEKDAYS_EN[day.value - 1]

    override fun number(value: Int) = value.toString()

    override fun completedOf(done: Int, total: Int) = "$done of $total"

    override fun days(value: Int) = if (value == 1) "1 day" else "$value days"

    override fun nights(value: Int) = if (value == 1) "1 night" else "$value nights"

    override fun percentage(value: Int) = "$value%"
}

internal object BengaliSalahWording : SalahWording {

    override val screenTitle = "নামাজ"
    override val monthTitle = "মাসিক নামাজ"

    override val setUpTitle = "নামাজ চালু করুন"
    override val setUpBody =
        "আপনার অবস্থান যোগ করুন, যাতে আইদা নামাজের সময় দেখাতে ও আপনার নামাজ ট্র্যাক করতে পারে — সবকিছু আপনার ডিভাইসেই।"
    override val setUpAction = "চালু করুন"

    override val monthView = "মাসিক হিসাব"
    override val tahajjudTitle = "তাহাজ্জুদ"
    override val lastThird = "রাতের শেষ তৃতীয়াংশ"
    override val setReminder = "রিমাইন্ডার সেট করুন"

    override val chooseStatus = "নামাজ চিহ্নিত করুন"
    override val cancel = "বাতিল"
    override val trackingFailed = "এখন এটি সংরক্ষণ করা গেল না।"

    override fun prayerName(prayer: Prayer) = when (prayer) {
        Prayer.FAJR -> "ফজর"
        Prayer.DHUHR -> "যোহর"
        Prayer.ASR -> "আসর"
        Prayer.MAGHRIB -> "মাগরিব"
        Prayer.ISHA -> "এশা"
        Prayer.TAHAJJUD -> "তাহাজ্জুদ"
    }

    override fun statusLabel(status: PrayerStatus) = when (status) {
        PrayerStatus.NOT_RECORDED -> "রেকর্ড করা হয়নি"
        PrayerStatus.UPCOMING -> "আসছে"
        PrayerStatus.NOT_YET -> "এখনও নয়"
        PrayerStatus.PRAYED -> "আদায় হয়েছে"
        PrayerStatus.PRAYED_LATE -> "দেরিতে আদায়"
        PrayerStatus.MISSED -> "ছুটে গেছে"
    }

    override fun tahajjudStatusLabel(status: TahajjudStatus) = when (status) {
        TahajjudStatus.NOT_RECORDED -> "রেকর্ড করা হয়নি"
        TahajjudStatus.PRAYED -> "আদায় হয়েছে"
        TahajjudStatus.NOT_PRAYED -> "আদায় হয়নি"
    }

    override fun until(prayerName: String) = "$prayerName পর্যন্ত"

    override fun countdown(remaining: Duration) = formatCountdown(remaining, bengali = true)

    override fun hijriLabel(hijri: HijriDate) =
        "${bengaliNumerals(hijri.day.toString())} ${HIJRI_MONTHS_BN[hijri.month - 1]} ${bengaliNumerals(hijri.year.toString())} হিজরি"

    override val summaryTitle = "এই মাস"
    override val byPrayerTitle = "নামাজ অনুযায়ী"
    override val completedTitle = "আদায় হয়েছে"
    override val daysAllFiveTitle = "পাঁচ ওয়াক্ত পূর্ণ"
    override val currentStreakTitle = "চলমান ধারা"
    override val bestStreakTitle = "সেরা ধারা"
    override val missedTitle = "ছুটে গেছে"
    override val tahajjudNightsTitle = "এই মাসে রাত"
    override val tahajjudStreakTitle = "চলমান ধারা"
    override val previousMonth = "আগের মাস"
    override val nextMonth = "পরের মাস"

    override fun monthYearLabel(month: YearMonth) =
        "${MONTHS_BN[month.monthValue - 1]} ${bengaliNumerals(month.year.toString())}"

    override fun weekdayNarrow(day: DayOfWeek) = WEEKDAYS_BN[day.value - 1]

    override fun number(value: Int) = bengaliNumerals(value.toString())

    override fun completedOf(done: Int, total: Int) =
        "${bengaliNumerals(total.toString())}-এর মধ্যে ${bengaliNumerals(done.toString())}"

    override fun days(value: Int) = "${bengaliNumerals(value.toString())} দিন"

    override fun nights(value: Int) = "${bengaliNumerals(value.toString())} রাত"

    override fun percentage(value: Int) = "${bengaliNumerals(value.toString())}%"
}

/**
 * HH:MM:SS, padded by hand and never negative — [Duration.toHours] on a run-out span counts
 * backwards, so a prayer whose time has just arrived reads as zero rather than as a figure racing
 * away. String.format is avoided deliberately: it follows the machine's default locale, which is
 * not Abu's.
 */
private fun formatCountdown(remaining: Duration, bengali: Boolean): String {
    val seconds = remaining.seconds.coerceAtLeast(0)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    val latin = "${pad(hours)}:${pad(minutes)}:${pad(secs)}"
    return if (bengali) bengaliNumerals(latin) else latin
}

private fun pad(value: Long): String = if (value < 10) "0$value" else "$value"

private val MONTHS_EN = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

private val MONTHS_BN = listOf(
    "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
    "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর",
)

/** Monday-first, matching [DayOfWeek.value] (1 = Monday … 7 = Sunday). */
private val WEEKDAYS_EN = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private val WEEKDAYS_BN = listOf("সোম", "মঙ্গল", "বুধ", "বৃহ", "শুক্র", "শনি", "রবি")

private val HIJRI_MONTHS_EN = listOf(
    "Muharram", "Safar", "Rabi al-Awwal", "Rabi al-Thani", "Jumada al-Awwal", "Jumada al-Thani",
    "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qadah", "Dhu al-Hijjah",
)

private val HIJRI_MONTHS_BN = listOf(
    "মহররম", "সফর", "রবিউল আউয়াল", "রবিউস সানি", "জমাদিউল আউয়াল", "জমাদিউস সানি",
    "রজব", "শাবান", "রমজান", "শাওয়াল", "জিলক্বদ", "জিলহজ",
)

internal fun salahWording(language: SpokenLanguage): SalahWording = when (language) {
    SpokenLanguage.BENGALI -> BengaliSalahWording
    SpokenLanguage.ENGLISH -> EnglishSalahWording
}
