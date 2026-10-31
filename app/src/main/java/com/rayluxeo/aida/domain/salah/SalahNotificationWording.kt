package com.rayluxeo.aida.domain.salah

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.brief.BriefNotificationText

/**
 * Every word a Salah notification puts on the shade: the three channels Android draws in its own
 * settings screen, the line that announces a prayer's time, the gentle check-in question, the
 * buttons on that question, and the Tahajjud reminder.
 *
 * A port in `domain` with its implementation in `ui/response`, for the same reason
 * [com.rayluxeo.aida.domain.scheduler.ReminderNotificationWording] is one: the code that posts
 * these notifications lives in `data/scheduler`, and nothing in `data` may reach into `ui` (§4).
 * Without the port the notification would either import the presentation layer or grow a second
 * copy of Mira's wording, and the second copy is how a button says one thing in the shade and
 * another on screen.
 *
 * Everything here arrives already in Abu's language, the action labels included — a button in
 * English on a Bengali phone is a shipped defect (§12).
 *
 * **Nothing here is judgemental** (Abu's instruction): a prayer that has not been logged is asked
 * after, never accused. "Did you pray Asr?" — never "you failed to pray."
 */
interface SalahNotificationWording {

    /** The prayer-time channel's name and description, which Android draws in its settings screen. */
    fun prayerTimeChannel(language: SpokenLanguage): BriefNotificationText

    fun checkInChannel(language: SpokenLanguage): BriefNotificationText

    fun tahajjudChannel(language: SpokenLanguage): BriefNotificationText

    /** The title of a prayer-time notification — the prayer's name in Abu's language. */
    fun prayerTimeTitle(prayer: Prayer, language: SpokenLanguage): String

    /** The line under it — that this prayer's time has come. Never an instruction, only the fact. */
    fun prayerTimeBody(prayer: Prayer, language: SpokenLanguage): String

    /** The check-in's question, which is also its title — "Did you pray Asr?" / "আসর নামাজ পড়েছেন?". */
    fun checkInQuestion(prayer: Prayer, language: SpokenLanguage): String

    /** The check-in's "yes" button. */
    fun yes(language: SpokenLanguage): String

    /** The check-in's "not yet" button — the gentle in-between, never a mark of failure. */
    fun notYet(language: SpokenLanguage): String

    /** The check-in's "remind me later" button. */
    fun remindMe(language: SpokenLanguage): String

    fun tahajjudTitle(language: SpokenLanguage): String

    /**
     * The Tahajjud line. [windowLabel] is the night's Fajr clock time in `HH:mm`, computed when the
     * alarm was armed and carried to the notification, so Mira can say how long the window lasts. It
     * may be blank, in which case the line is the plain statement that the window has begun.
     */
    fun tahajjudBody(windowLabel: String, language: SpokenLanguage): String
}
