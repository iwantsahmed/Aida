package com.rayluxeo.aida.ui.response

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.brief.BriefKind
import com.rayluxeo.aida.domain.brief.BriefNotificationText
import com.rayluxeo.aida.domain.brief.BriefNotificationWording
import com.rayluxeo.aida.domain.brief.DailyBriefReport
import com.rayluxeo.aida.domain.brief.EveningReviewReport
import javax.inject.Inject

/**
 * The notification's words, taken from the same narrator the screen uses.
 *
 * This exists so that the alarm in `data/scheduler` can reach Mira's wording without `data`
 * importing `ui` (§4). It deliberately holds no sentences of its own: every line comes from
 * [BriefNarrator], because a second copy of the wording is how the notification and the screen
 * start describing the same morning differently.
 */
class BriefNotificationWordingImpl @Inject constructor(
    private val narrator: BriefNarrator,
) : BriefNotificationWording {

    override fun morning(report: DailyBriefReport, language: SpokenLanguage): BriefNotificationText =
        narrator.morningNotification(report, language).asText()

    override fun evening(report: EveningReviewReport, language: SpokenLanguage): BriefNotificationText =
        narrator.eveningNotification(report, language).asText()

    override fun unavailable(kind: BriefKind, language: SpokenLanguage): BriefNotificationText {
        val wording = briefWording(language)
        return BriefNotificationText(
            title = when (kind) {
                BriefKind.MORNING -> wording.morningTitle
                BriefKind.EVENING -> wording.eveningTitle
            },
            body = wording.briefUnavailable,
        )
    }

    override fun channel(language: SpokenLanguage): BriefNotificationText {
        val wording = briefWording(language)
        return BriefNotificationText(wording.briefChannelName, wording.briefChannelDescription)
    }

    override fun reminderChannel(language: SpokenLanguage): BriefNotificationText {
        val wording = briefWording(language)
        return BriefNotificationText(wording.reminderChannelName, wording.reminderChannelDescription)
    }

    private fun BriefNotification.asText() = BriefNotificationText(title = title, body = line)
}
