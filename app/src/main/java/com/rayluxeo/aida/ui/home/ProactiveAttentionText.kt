package com.rayluxeo.aida.ui.home

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.attention.AttentionCategory
import com.rayluxeo.aida.domain.attention.ProactiveAttentionItem

/**
 * Words a proactive attention item's two lines — what happened and why it matters — in Abu's
 * language, from the item's facts alone.
 *
 * This is where the Needs-Attention card becomes bilingual. The detection engine is pure domain and
 * carries no prose (AGENTS.md §4 rule 6); every sentence Abu reads is assembled here, with amounts,
 * dates and numbers already formatted for the language by the caller.
 *
 * @param amountText the primary amount, already formatted (`HomeWording.amount`), or null.
 * @param secondaryText a second amount (the part paid on an invoice), already formatted, or null.
 * @param dueText the date the sentence names, already formatted for the language, or null.
 * @param number renders a count in the language's digits.
 */
internal fun proactiveAttentionText(
    item: ProactiveAttentionItem,
    language: SpokenLanguage,
    amountText: String?,
    secondaryText: String?,
    dueText: String?,
    number: (Long) -> String,
): Pair<String, String> {
    val en = language == SpokenLanguage.ENGLISH
    val title = item.relatedRecord.title
    val client = item.relatedRecord.subtitle
    val days = item.days ?: 0L
    val amount = amountText.orEmpty()

    return when (item.category) {
        AttentionCategory.OVERDUE_INVOICE -> {
            val dayW = if (en) (if (days == 1L) "1 day" else "${number(days)} days") else "${number(days)} দিন"
            val what = if (en) {
                if (client != null) "$client's invoice is $dayW overdue." else "Invoice $title is $dayW overdue."
            } else {
                if (client != null) "$client-এর ইনভয়েস $dayW ধরে বকেয়া।" else "ইনভয়েস $title $dayW ধরে বকেয়া।"
            }
            val why = if (en) "$amount outstanding past the due date." else "নির্ধারিত তারিখ পেরিয়ে $amount বকেয়া।"
            what to why
        }

        AttentionCategory.OVERDUE_TASK -> {
            val what = if (en) {
                val dayW = if (days <= 0L) "overdue" else if (days == 1L) "1 day overdue" else "${number(days)} days overdue"
                "Task '$title' is $dayW."
            } else {
                if (days <= 0L) "'$title' কাজটির সময় পেরিয়েছে।" else "'$title' কাজটি ${number(days)} দিন ধরে বকেয়া।"
            }
            val why = if (en) "Was due on ${dueText.orEmpty()}." else "${dueText.orEmpty()} তারিখে শেষ হওয়ার কথা ছিল।"
            what to why
        }

        AttentionCategory.DEADLINE_WITHIN_48_HOURS -> {
            val count = item.count ?: 0
            val dueW = if (en) when (days) { 0L -> "due today"; 1L -> "due tomorrow"; else -> "due in 2 days" }
            else when (days) { 0L -> "আজ শেষ"; 1L -> "আগামীকাল শেষ"; else -> "২ দিনে শেষ" }
            val what = if (en) {
                if (count > 0) {
                    val taskW = if (count == 1) "1 task" else "${number(count.toLong())} tasks"
                    "$title is $dueW with $taskW unfinished."
                } else "$title is $dueW."
            } else {
                if (count > 0) "$title $dueW, ${number(count.toLong())}টি কাজ বাকি।" else "$title $dueW।"
            }
            val why = if (en) "Upcoming project deadline within 48 hours." else "৪৮ ঘণ্টার মধ্যে প্রজেক্টের ডেডলাইন।"
            what to why
        }

        AttentionCategory.SCHEDULED_CLIENT_FOLLOW_UP -> {
            val what = if (en) "No contact with $title in ${number(days)} days." else "$title-এর সাথে ${number(days)} দিন কোনো যোগাযোগ নেই।"
            val why = if (en) "Scheduled follow-up window reached (over 3 weeks without contact)." else "ফলো-আপের সময় হয়েছে (৩ সপ্তাহের বেশি যোগাযোগ নেই)।"
            what to why
        }

        AttentionCategory.SUBSCRIPTION_RENEWING_SOON -> {
            val renewsW = if (en) when (days) { 0L -> "renews today"; 1L -> "renews tomorrow"; else -> "renews in ${number(days)} days" }
            else when (days) { 0L -> "আজ নবায়ন হবে"; 1L -> "আগামীকাল নবায়ন হবে"; else -> "${number(days)} দিনে নবায়ন হবে" }
            val what = if (en) "$title $renewsW." else "$title $renewsW।"
            val why = if (en) "Upcoming recurring charge of $amount." else "আসন্ন নিয়মিত চার্জ $amount।"
            what to why
        }

        AttentionCategory.PARTIALLY_PAID_INVOICE -> {
            val what = if (en) {
                if (client != null) "$client's invoice is partially paid." else "Invoice $title is partially paid."
            } else {
                if (client != null) "$client-এর ইনভয়েস আংশিক পরিশোধিত।" else "ইনভয়েস $title আংশিক পরিশোধিত।"
            }
            val why = if (en) "${secondaryText.orEmpty()} paid, $amount still outstanding." else "${secondaryText.orEmpty()} পরিশোধিত, $amount এখনো বকেয়া।"
            what to why
        }

        AttentionCategory.HIGH_PRIORITY_UNFINISHED_TASK -> {
            val what = if (en) "High-priority task '$title' is unfinished." else "উচ্চ-অগ্রাধিকারের '$title' কাজটি অসম্পূর্ণ।"
            val why = if (dueText != null) {
                if (en) "Due $dueText." else "$dueText তারিখে শেষ।"
            } else {
                if (en) "Marked high priority." else "উচ্চ অগ্রাধিকার দেওয়া।"
            }
            what to why
        }

        AttentionCategory.PROJECT_UPCOMING_DEADLINE -> {
            val what = if (en) "Project '$title' is due in ${number(days)} days." else "'$title' প্রজেক্টটি ${number(days)} দিনে শেষ।"
            val why = if (en) "Target delivery deadline on ${dueText.orEmpty()}." else "লক্ষ্য ডেলিভারি ${dueText.orEmpty()} তারিখে।"
            what to why
        }

        AttentionCategory.PROJECT_NO_RECENT_ACTIVITY -> {
            val what = if (en) "Project '$title' has had no activity for ${number(days)} days." else "'$title' প্রজেক্টে ${number(days)} দিন কোনো কার্যকলাপ নেই।"
            val why = if (en) "Open project is stalled with unfinished tasks." else "খোলা প্রজেক্ট থেমে আছে, কাজ এখনো বাকি।"
            what to why
        }

        AttentionCategory.POSSIBLE_DUPLICATE_EXPENSE -> {
            val hours = item.hours ?: 0L
            val hourW = if (en) (if (hours == 0L) "within 1 hour" else "within ${number(hours)} hours")
            else (if (hours == 0L) "১ ঘণ্টার মধ্যে" else "${number(hours)} ঘণ্টার মধ্যে")
            val what = if (en) "Possible duplicate expense of $amount for '$title'." else "'$title'-এর জন্য $amount-এর সম্ভাব্য ডুপ্লিকেট খরচ।"
            val why = if (en) "Recorded twice $hourW." else "$hourW দুইবার রেকর্ড হয়েছে।"
            what to why
        }
    }
}
