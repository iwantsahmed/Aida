package com.rayluxeo.aida.ui.response

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.brief.BriefNotificationText
import com.rayluxeo.aida.domain.salah.Prayer
import com.rayluxeo.aida.domain.salah.SalahNotificationWording
import javax.inject.Inject

/**
 * The Salah notification's words, taken from the one place Mira keeps them.
 *
 * This exists so the code that posts a Salah notification, which lives in `data/scheduler`, can
 * reach Mira's wording without `data` importing `ui` (§4). It holds no sentences of its own: every
 * line comes from [SalahNotificationPhrases], exactly as [ReminderNotificationWordingImpl] takes
 * its lines from [ReminderWording].
 */
class SalahNotificationWordingImpl @Inject constructor() : SalahNotificationWording {

    override fun prayerTimeChannel(language: SpokenLanguage): BriefNotificationText {
        val words = salahNotificationPhrases(language)
        return BriefNotificationText(words.prayerTimeChannelName, words.prayerTimeChannelDescription)
    }

    override fun checkInChannel(language: SpokenLanguage): BriefNotificationText {
        val words = salahNotificationPhrases(language)
        return BriefNotificationText(words.checkInChannelName, words.checkInChannelDescription)
    }

    override fun tahajjudChannel(language: SpokenLanguage): BriefNotificationText {
        val words = salahNotificationPhrases(language)
        return BriefNotificationText(words.tahajjudChannelName, words.tahajjudChannelDescription)
    }

    override fun prayerTimeTitle(prayer: Prayer, language: SpokenLanguage): String =
        salahNotificationPhrases(language).prayerName(prayer)

    override fun prayerTimeBody(prayer: Prayer, language: SpokenLanguage): String =
        salahNotificationPhrases(language).prayerTimeBody(prayer)

    override fun checkInQuestion(prayer: Prayer, language: SpokenLanguage): String =
        salahNotificationPhrases(language).checkInQuestion(prayer)

    override fun yes(language: SpokenLanguage): String = salahNotificationPhrases(language).yes

    override fun notYet(language: SpokenLanguage): String = salahNotificationPhrases(language).notYet

    override fun remindMe(language: SpokenLanguage): String = salahNotificationPhrases(language).remindMe

    override fun tahajjudTitle(language: SpokenLanguage): String =
        salahNotificationPhrases(language).tahajjudTitle

    override fun tahajjudBody(windowLabel: String, language: SpokenLanguage): String =
        salahNotificationPhrases(language).tahajjudBody(windowLabel)
}
