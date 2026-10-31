package com.rayluxeo.aida.ui.response

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.salah.Prayer

/**
 * Every word a Salah notification carries, one object per language, one selector — the same shape
 * as [ReminderWording] beside it, and for the same reason: a button label is a whole short phrase,
 * not a translated word, and "আসর নামাজ পড়েছেন?" is not "Asr" plus a question mark.
 *
 * The tone is calm and never judgemental (Abu's instruction): a prayer that is not yet logged is
 * asked after, not accused. The Tahajjud window and check-in are offered, never demanded.
 *
 * Bengali digits come through [bengaliNumerals] so a Bengali notification never shows a Latin
 * figure — the one place a clock time reaches these words is the Tahajjud window label.
 */
internal interface SalahNotificationPhrases {

    val prayerTimeChannelName: String
    val prayerTimeChannelDescription: String
    val checkInChannelName: String
    val checkInChannelDescription: String
    val tahajjudChannelName: String
    val tahajjudChannelDescription: String

    /** The prayer's name as it is spoken — "Asr", "আসর". Tahajjud included, for its own reminder. */
    fun prayerName(prayer: Prayer): String

    /** "It's time for the Asr prayer." The fact, never an instruction. */
    fun prayerTimeBody(prayer: Prayer): String

    /** "Did you pray Asr?" — the question, polite, never an accusation. */
    fun checkInQuestion(prayer: Prayer): String

    val yes: String
    val notYet: String
    val remindMe: String

    val tahajjudTitle: String

    /** [windowLabel] is Fajr in `HH:mm`, already converted to the right digits; blank for none. */
    fun tahajjudBody(windowLabel: String): String
}

internal object EnglishSalahNotificationPhrases : SalahNotificationPhrases {

    override val prayerTimeChannelName = "Prayer times"
    override val prayerTimeChannelDescription = "When each prayer's time arrives."
    override val checkInChannelName = "Prayer check-ins"
    override val checkInChannelDescription = "A gentle ask whether you've prayed yet."
    override val tahajjudChannelName = "Tahajjud"
    override val tahajjudChannelDescription = "The last third of the night, if you've asked to be reminded."

    override fun prayerName(prayer: Prayer): String = when (prayer) {
        Prayer.FAJR -> "Fajr"
        Prayer.DHUHR -> "Dhuhr"
        Prayer.ASR -> "Asr"
        Prayer.MAGHRIB -> "Maghrib"
        Prayer.ISHA -> "Isha"
        Prayer.TAHAJJUD -> "Tahajjud"
    }

    override fun prayerTimeBody(prayer: Prayer): String = "It's time for the ${prayerName(prayer)} prayer."

    override fun checkInQuestion(prayer: Prayer): String = "Did you pray ${prayerName(prayer)}?"

    override val yes = "Yes"
    override val notYet = "Not yet"
    override val remindMe = "Remind me"

    override val tahajjudTitle = "Tahajjud"

    override fun tahajjudBody(windowLabel: String): String =
        if (windowLabel.isBlank()) {
            "The last third of the night has begun."
        } else {
            "The last third of the night — until Fajr at $windowLabel."
        }
}

internal object BengaliSalahNotificationPhrases : SalahNotificationPhrases {

    override val prayerTimeChannelName = "নামাজের সময়"
    override val prayerTimeChannelDescription = "প্রতি ওয়াক্তের সময় হলে।"
    override val checkInChannelName = "নামাজের খোঁজ"
    override val checkInChannelDescription = "আপনি নামাজ পড়েছেন কিনা, মৃদু জিজ্ঞাসা।"
    override val tahajjudChannelName = "তাহাজ্জুদ"
    override val tahajjudChannelDescription = "রাতের শেষ তৃতীয়াংশে, আপনি চাইলে।"

    override fun prayerName(prayer: Prayer): String = when (prayer) {
        Prayer.FAJR -> "ফজর"
        Prayer.DHUHR -> "যোহর"
        Prayer.ASR -> "আসর"
        Prayer.MAGHRIB -> "মাগরিব"
        Prayer.ISHA -> "এশা"
        Prayer.TAHAJJUD -> "তাহাজ্জুদ"
    }

    override fun prayerTimeBody(prayer: Prayer): String = "এখন ${prayerName(prayer)} নামাজের সময়।"

    override fun checkInQuestion(prayer: Prayer): String = "${prayerName(prayer)} নামাজ পড়েছেন?"

    override val yes = "হ্যাঁ"
    override val notYet = "এখনো না"
    override val remindMe = "পরে মনে করান"

    override val tahajjudTitle = "তাহাজ্জুদ"

    override fun tahajjudBody(windowLabel: String): String =
        if (windowLabel.isBlank()) {
            "রাতের শেষ তৃতীয়াংশ শুরু হয়েছে।"
        } else {
            "রাতের শেষ তৃতীয়াংশ — ফজর ${bengaliNumerals(windowLabel)} পর্যন্ত।"
        }
}

internal fun salahNotificationPhrases(language: SpokenLanguage): SalahNotificationPhrases = when (language) {
    SpokenLanguage.ENGLISH -> EnglishSalahNotificationPhrases
    SpokenLanguage.BENGALI -> BengaliSalahNotificationPhrases
}
