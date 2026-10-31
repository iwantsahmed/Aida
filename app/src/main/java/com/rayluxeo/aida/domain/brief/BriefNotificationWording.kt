package com.rayluxeo.aida.domain.brief

import com.rayluxeo.aida.core.common.SpokenLanguage

/** A title and a line, already in Abu's language. */
data class BriefNotificationText(val title: String, val body: String)

/**
 * Puts a report into the two lines a notification has room for.
 *
 * A port in `domain` with its implementation in `ui/response`, because the alarm that needs these
 * words lives in `data/scheduler` and nothing in `data` may reach into `ui` (§4). Without the port
 * the receiver would either import the presentation layer or grow its own second copy of Mira's
 * wording, and the second copy is how the notification and the screen start saying different
 * things about the same morning.
 */
interface BriefNotificationWording {

    fun morning(report: DailyBriefReport, language: SpokenLanguage): BriefNotificationText

    fun evening(report: EveningReviewReport, language: SpokenLanguage): BriefNotificationText

    /**
     * What Mira says when she could not build the report at all.
     *
     * She says something rather than nothing: going quiet on a morning she could not read the
     * database is indistinguishable from a quiet day, and those are different facts.
     */
    fun unavailable(kind: BriefKind, language: SpokenLanguage): BriefNotificationText

    /** The notification channel's own name and description, which Android shows in Settings. */
    fun channel(language: SpokenLanguage): BriefNotificationText

    /**
     * The reminders channel, which sits beside the briefs channel on the same system screen.
     *
     * It lives on this port rather than staying a constant in the notification helper because
     * Android draws both names in Settings, and one of the two being English while the other
     * follows Abu's language is a seam he can see.
     */
    fun reminderChannel(language: SpokenLanguage): BriefNotificationText
}
