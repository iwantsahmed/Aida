package com.rayluxeo.aida.ui.notes.detail

import com.rayluxeo.aida.ui.response.bengaliNumerals

/**
 * The note editor in Bengali.
 *
 * Every number goes through [bengaliNumerals]: "3 tasks created" written with Latin digits inside
 * a Bengali sentence is the one place a bilingual screen always slips.
 */
internal object BengaliNoteDetailWording : NoteDetailWording {

    override val newNoteTitle: String = "নতুন নোট"

    override val noteTitle: String = "নোট"

    override val back: String = "ফিরে যান"

    override val moreActions: String = "আরও"

    override val save: String = "সংরক্ষণ করুন"

    override val cancel: String = "বাতিল"

    override val set: String = "ঠিক আছে"

    override val done: String = "ঠিক আছে"

    override val dismiss: String = "বন্ধ করুন"

    override val none: String = "কিছু না"

    override val titlePlaceholder: String = "শিরোনাম"

    override val bodyPlaceholder: String = "লিখে রাখুন"

    override val sectionLabels: String = "ট্যাগ"

    override val sectionBelongs: String = "কার সাথে"

    override val sectionHandwriting: String = "হাতের লেখা"

    override val sectionAttachments: String = "সংযুক্তি"

    override val sectionVoice: String = "রেকর্ডিং"

    override val sectionActions: String = "এটি দিয়ে কিছু করুন"

    override val sectionHistory: String = "ইতিহাস"

    override val clientLabel: String = "ক্লায়েন্ট"

    override val projectLabel: String = "প্রজেক্ট"

    override val taskLabel: String = "কাজ"

    override val clientSheetTitle: String = "ক্লায়েন্ট"

    override val projectSheetTitle: String = "প্রজেক্ট"

    override val taskSheetTitle: String = "কাজ"

    override val noClients: String = "এখনো কোনো ক্লায়েন্ট নেই।"

    override val noProjects: String = "এখনো কোনো প্রজেক্ট নেই।"

    override val noTasks: String = "যুক্ত করার মতো খোলা কাজ নেই।"

    override val tagsSheetTitle: String = "ট্যাগ"

    override val tagPlaceholder: String = "নতুন ট্যাগ"

    override val addTag: String = "ট্যাগ যোগ করুন"

    override fun removeTag(name: String): String = "$name সরান"

    override val noTags: String = "কোনো ট্যাগ নেই"

    override fun fileSize(bytes: Long): String = when {
        bytes < BENGALI_KILOBYTE -> "${digits(bytes)} বাইট"
        bytes < BENGALI_MEGABYTE -> "${digits(bytes / BENGALI_KILOBYTE)} কেবি"
        else -> "${digits(bytes / BENGALI_MEGABYTE)} এমবি"
    }

    override fun inkPageLabel(strokeCount: Int, moment: String): String =
        "${digits(strokeCount)}টি টান · $moment"

    override val addHandwriting: String = "হাতে একটি পাতা লিখুন"

    override val inkLost: String = "এই পাতার লেখা আর নেই।"

    override val inkReadWhileWriting: String = "ক্যানভাসে লেখার সময়েই হাতের লেখা পড়া হয়।"

    override fun voiceNoteLabel(minutes: Long, seconds: Long, moment: String): String =
        "${digits(minutes)}:${digits(seconds.toString().padStart(SECONDS_DIGITS, '0'))} · $moment"

    override fun created(moment: String): String = "$moment এ লেখা"

    override fun updated(moment: String): String = "$moment এ সম্পাদিত"

    override fun actionLabel(action: NoteAction): String = when (action) {
        NoteAction.ORGANIZE -> "গুছিয়ে দিন"
        NoteAction.TURN_INTO_TASKS -> "কাজে পরিণত করুন"
        NoteAction.CREATE_REMINDER -> "রিমাইন্ডার দিন"
        NoteAction.LINK_CLIENT -> "ক্লায়েন্ট যুক্ত করুন"
        NoteAction.CREATE_INVOICE_DRAFT -> "ইনভয়েসের খসড়া"
        NoteAction.ASK_MIRA -> "আইদাকে জিজ্ঞাসা করুন"
    }

    override val saveFirst: String = "আগে নোটটি সংরক্ষণ করুন"

    override val restoreFirst: String = "নোটটি ঝুড়িতে আছে"

    override val linkClientFirst: String = "আগে একজন ক্লায়েন্ট যুক্ত করুন"

    override val needsWords: String = "কাজ বানানোর মতো কোনো লাইন নেই"

    override fun unavailable(label: String, reason: String): String = "$label — $reason।"

    override val organizeTitle: String = "আইদা এগুলো বদলাতে চায়"

    override val organizeApply: String = "প্রয়োগ করুন"

    override val organizeNothing: String = "বদলানোর কিছু নেই — নোটটি গোছানোই আছে।"

    override val organizeTitleLabel: String = "শিরোনাম"

    override val organizeTagsLabel: String = "ট্যাগ"

    override val organizeApplied: String = "নোট গুছিয়ে দেওয়া হয়েছে।"

    override val tasksTitle: String = "কাজে পরিণত করুন"

    override val tasksCreate: String = "কাজ তৈরি করুন"

    override val tasksEmpty: String = "কাজ বানানোর মতো কোনো লাইন নেই।"

    override fun tasksCreated(count: Int): String = "${digits(count)}টি কাজ তৈরি হয়েছে।"

    override val reminderTimeTitle: String = "কখন মনে করাব"

    override fun reminderCreated(moment: String): String = "$moment এ মনে করিয়ে দেব।"

    override fun invoiceDraftCreated(number: String, client: String): String =
        "$client এর জন্য $number খসড়া তৈরি হয়েছে।"

    override val pin: String = "পিন করুন"

    override val unpin: String = "পিন সরান"

    override val archive: String = "সংরক্ষণাগারে রাখুন"

    override val unarchive: String = "তালিকায় ফেরান"

    override val delete: String = "মুছে ফেলুন"

    override val restore: String = "ফিরিয়ে আনুন"

    override val inBin: String = "এই নোটটি ঝুড়িতে আছে।"

    override val noteSaved: String = "নোট সংরক্ষিত হয়েছে।"

    override val noteArchived: String = "নোট সংরক্ষণাগারে রাখা হয়েছে।"

    override val noteUnarchived: String = "নোট তালিকায় ফিরেছে।"

    override val noteDeleted: String = "নোট মুছে ফেলা হয়েছে।"

    override val noteRestored: String = "নোট ফিরে এসেছে।"

    override val deleteTitle: String = "নোটটি মুছে ফেলবেন?"

    override val deleteMessage: String = "এটি ঝুড়িতে যাবে, সেখান থেকে ফিরিয়ে আনা যাবে।"

    override val discardTitle: String = "সংরক্ষণ না করে চলে যাবেন?"

    override val discardMessage: String = "এখানে যা লিখেছেন তা হারিয়ে যাবে।"

    override val discardConfirm: String = "চলে যান"

    override val keepEditing: String = "লেখা চালিয়ে যান"

    private fun digits(value: String): String = bengaliNumerals(value)

    private fun digits(value: Int): String = bengaliNumerals(value.toString())

    private fun digits(value: Long): String = bengaliNumerals(value.toString())
}

/** Seconds are always two digits beside the minutes, as any clock face writes them. */
private const val SECONDS_DIGITS = 2

private const val BENGALI_KILOBYTE = 1_024L

private const val BENGALI_MEGABYTE = 1_024L * 1_024L
