package com.rayluxeo.aida.ui.activity

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.ActivityCategory
import com.rayluxeo.aida.ui.response.bengaliNumerals
import java.time.Duration
import java.time.Instant

/**
 * Every word the Activity screen puts in front of Abu, in one language: the title, the back label,
 * the "All" filter, a name for each [ActivityCategory], the empty line, and how a "when" is worded
 * while an entry is recent.
 *
 * Only the *relative* wording lives here — "just now", "5 minutes ago". Once an entry is older than
 * a day the screen falls back to the shared day-and-time label worded by `TaskTimeFormatter`, which
 * is the one place Mira's Bengali month and time-of-day wording lives; reproducing it here is how
 * the two would drift apart (§13). An entry's [ActivityEntry.summary] is never worded here either —
 * it is a record, stored in the language it was written in, and shown verbatim.
 */
internal interface ActivityLogWording {

    val screenTitle: String
    val back: String
    val allFilter: String
    val empty: String

    /** A category's name, as the filter chip shows it and as it tags a row. */
    fun categoryLabel(category: ActivityCategory): String

    /** How long ago [occurredAt] was, in the language's own digits: "just now", "N minutes ago". */
    fun relativeTime(occurredAt: Instant, now: Instant): String
}

internal object EnglishActivityLogWording : ActivityLogWording {

    override val screenTitle = "Activity"
    override val back = "Back"
    override val allFilter = "All"
    override val empty = "Nothing to show here yet."

    override fun categoryLabel(category: ActivityCategory) = when (category) {
        ActivityCategory.TASK -> "Tasks"
        ActivityCategory.REMINDER -> "Reminders"
        ActivityCategory.NOTE -> "Notes"
        ActivityCategory.MONEY -> "Money"
        ActivityCategory.CLIENT -> "Clients"
        ActivityCategory.PROJECT -> "Projects"
        ActivityCategory.FILE -> "Files"
        ActivityCategory.VOICE -> "Voice"
        ActivityCategory.SETTING -> "Settings"
        ActivityCategory.AUTOMATION -> "Automations"
        ActivityCategory.BACKUP -> "Backups"
        ActivityCategory.OTHER -> "Other"
    }

    override fun relativeTime(occurredAt: Instant, now: Instant): String {
        val seconds = secondsBetween(occurredAt, now)
        return when {
            seconds < SECONDS_PER_MINUTE -> "Just now"
            seconds < SECONDS_PER_HOUR -> {
                val minutes = seconds / SECONDS_PER_MINUTE
                if (minutes == 1L) "1 minute ago" else "$minutes minutes ago"
            }
            else -> {
                val hours = seconds / SECONDS_PER_HOUR
                if (hours == 1L) "1 hour ago" else "$hours hours ago"
            }
        }
    }
}

internal object BengaliActivityLogWording : ActivityLogWording {

    override val screenTitle = "কার্যকলাপ"
    override val back = "পিছনে"
    override val allFilter = "সব"
    override val empty = "এখানে দেখানোর মতো এখনও কিছু নেই।"

    override fun categoryLabel(category: ActivityCategory) = when (category) {
        ActivityCategory.TASK -> "কাজ"
        ActivityCategory.REMINDER -> "রিমাইন্ডার"
        ActivityCategory.NOTE -> "নোট"
        ActivityCategory.MONEY -> "টাকা"
        ActivityCategory.CLIENT -> "ক্লায়েন্ট"
        ActivityCategory.PROJECT -> "প্রকল্প"
        ActivityCategory.FILE -> "ফাইল"
        ActivityCategory.VOICE -> "ভয়েস"
        ActivityCategory.SETTING -> "সেটিংস"
        ActivityCategory.AUTOMATION -> "অটোমেশন"
        ActivityCategory.BACKUP -> "ব্যাকআপ"
        ActivityCategory.OTHER -> "অন্যান্য"
    }

    override fun relativeTime(occurredAt: Instant, now: Instant): String {
        val seconds = secondsBetween(occurredAt, now)
        return when {
            seconds < SECONDS_PER_MINUTE -> "এইমাত্র"
            seconds < SECONDS_PER_HOUR ->
                "${bengaliNumerals((seconds / SECONDS_PER_MINUTE).toString())} মিনিট আগে"
            else ->
                "${bengaliNumerals((seconds / SECONDS_PER_HOUR).toString())} ঘণ্টা আগে"
        }
    }
}

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 60L * 60L

/** Clamped at zero so a clock that reads an entry a moment in the future still says "just now". */
private fun secondsBetween(occurredAt: Instant, now: Instant): Long =
    Duration.between(occurredAt, now).seconds.coerceAtLeast(0L)

internal fun activityLogWording(language: SpokenLanguage): ActivityLogWording = when (language) {
    SpokenLanguage.BENGALI -> BengaliActivityLogWording
    SpokenLanguage.ENGLISH -> EnglishActivityLogWording
}
