package com.rayluxeo.aida.ui.meetings

import com.rayluxeo.aida.core.common.SpokenLanguage

/**
 * Every word the Meetings screen puts in front of Abu, in one language: the title, the control
 * that opens the sheet, the empty list's line, the labels on a meeting card and on the add-meeting
 * sheet, and the notice the view model raises once a meeting is saved.
 *
 * The screen wrote these as English literals, so with বাংলা selected a Bengali shell opened onto an
 * English form. A label is chosen where the language is known — here — rather than typed at the
 * call site, the same rule the shell and the Tasks screen already follow.
 *
 * Lines that carry runtime data — the start moment, the location, a saved meeting's title — are
 * functions, so the data is dropped into a sentence worded per language rather than glued to a
 * fixed English prefix.
 */
internal interface MeetingsWording {

    val screenTitle: String

    /** The control that opens the add-meeting sheet, and the sheet's own title. */
    val newMeeting: String

    /** The empty list's line, which reads as an invitation rather than as a fault. */
    val emptyMessage: String

    /** The start moment on a meeting card. [moment] is already formatted; only the label is worded. */
    fun startsAt(moment: String): String

    /** The location chip on a meeting card. */
    fun location(place: String): String

    val meetingTitleLabel: String

    val meetingTitlePlaceholder: String

    val locationLabel: String

    val meetingWhen: String
    val pickTime: String
    val setLabel: String
    val cancelLabel: String

    val saveMeeting: String

    /** What Mira says once a meeting is on the calendar, naming the one she just scheduled. */
    fun meetingScheduled(title: String): String

    /** Shown when the save itself fails, so the sheet does not close in silence on a lost meeting. */
    fun saveFailed(detail: String?): String
}

/** The Meetings screen in English. */
internal object EnglishMeetingsWording : MeetingsWording {

    override val screenTitle: String = "Meetings"

    override val newMeeting: String = "New Meeting"

    override val emptyMessage: String = "No meetings yet. Add one to keep it on your calendar."

    override fun startsAt(moment: String): String = "Starts: $moment"

    override fun location(place: String): String = "Location: $place"

    override val meetingTitleLabel: String = "Meeting Title"

    override val meetingTitlePlaceholder: String = "e.g., Client Review Sync"

    override val locationLabel: String = "Location / Link (Optional)"
    override val meetingWhen: String = "Date & time"
    override val pickTime: String = "Pick a time"
    override val setLabel: String = "Set"
    override val cancelLabel: String = "Cancel"

    override val saveMeeting: String = "Save Meeting"

    override fun meetingScheduled(title: String): String = "Meeting '$title' scheduled"
    override fun saveFailed(detail: String?): String =
        if (detail.isNullOrBlank()) "The meeting could not be saved." else "The meeting could not be saved ($detail)."
}

/**
 * The Meetings screen in Bengali. Bengali script throughout, so a Bengali shell is not an English
 * form under a Bengali title. "মিটিং" is written in Bengali script rather than translated, because
 * that is the word Abu uses. No digit crosses into this file — the only number the screen prints is
 * the start moment, which arrives already formatted.
 */
internal object BengaliMeetingsWording : MeetingsWording {

    override val screenTitle: String = "মিটিং"

    override val newMeeting: String = "নতুন মিটিং"

    override val emptyMessage: String = "এখনও কোনো মিটিং নেই। একটি যোগ করুন, ক্যালেন্ডারে থাকবে।"

    override fun startsAt(moment: String): String = "শুরু: $moment"

    override fun location(place: String): String = "স্থান: $place"

    override val meetingTitleLabel: String = "মিটিংয়ের শিরোনাম"
    override val meetingWhen: String = "তারিখ ও সময়"
    override val pickTime: String = "সময় বাছুন"
    override val setLabel: String = "সেট করুন"
    override val cancelLabel: String = "বাতিল"

    override val meetingTitlePlaceholder: String = "যেমন, ক্লায়েন্ট রিভিউ মিটিং"

    override val locationLabel: String = "স্থান / লিঙ্ক (ঐচ্ছিক)"

    override val saveMeeting: String = "মিটিং সেভ করুন"

    override fun meetingScheduled(title: String): String = "'$title' মিটিং নির্ধারিত হয়েছে"
    override fun saveFailed(detail: String?): String =
        if (detail.isNullOrBlank()) "মিটিংটি সংরক্ষণ করা গেল না।" else "মিটিংটি সংরক্ষণ করা গেল না ($detail)।"
}

internal fun meetingsWording(language: SpokenLanguage): MeetingsWording = when (language) {
    SpokenLanguage.BENGALI -> BengaliMeetingsWording
    SpokenLanguage.ENGLISH -> EnglishMeetingsWording
}
