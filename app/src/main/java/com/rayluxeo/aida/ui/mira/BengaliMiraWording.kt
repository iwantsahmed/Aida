package com.rayluxeo.aida.ui.mira

import com.rayluxeo.aida.domain.command.MiraCapability

/**
 * Mira's own screen in Bengali.
 *
 * Written as Abu would say it rather than translated word for word: the status line is a verb on
 * its own ("শুনছি") because that is how the act is reported in Bengali, where English needs a
 * participle. The permission labels name the thing being asked for, not the Android permission,
 * for the same reason the English ones do.
 */
internal object BengaliMiraWording : MiraWording {

    override val title: String = "আইদা"
    override val conversationHistory: String = "কথোপকথনের ইতিহাস"
    override val settings: String = "সেটিংস"
    override val alsoTry: String = "এগুলোও দেখতে পারেন"
    override val askAnything: String = "আইদাকে যা খুশি জিজ্ঞাসা করুন…"
    override val suggestions: List<MiraSuggestionText> = listOf(
        MiraSuggestionText("আজকের সারসংক্ষেপ", "আজকের কথা শোনাও"),
        MiraSuggestionText("কাজ যোগ করুন", "একটি কাজ যোগ করো "),
        MiraSuggestionText("রিমাইন্ডার দিন", "আমাকে মনে করিয়ে দিও "),
        MiraSuggestionText("আজ কি করার আছে", "আজ কি করার আছে"),
    )

    override val listening: String = "শুনছি"

    override val thinking: String = "ভাবছি"

    override val carryingOut: String = "করে দিচ্ছি"

    override val speaking: String = "বলছি"

    override val microphoneOff: String = "মাইক্রোফোন বন্ধ — লিখলে সবই চলে"

    override val answeredOnDevice: String = "ফোনেই উত্তর দেওয়া হয়েছে"

    override val answeredByGemini: String = "জেমিনি উত্তর দিয়েছে"

    override val listeningStopped: String = "শোনা বন্ধ হয়েছে"

    override val tapToSpeak: String = "বলতে ট্যাপ করুন"




    override val send: String = "পাঠান"

    override val dismiss: String = "বন্ধ করুন"

    override val tryAgain: String = "আবার চেষ্টা করুন"

    override val allowMicrophone: String = "অনুমতি দিন"

    override val openMicrophoneSettings: String = "সেটিংস খুলুন"

    override val confirmYes: String = "হ্যাঁ, করুন"

    override val confirmNo: String = "না"

    override val speakToMira: String = "আইদাকে বলুন"

    override val stopListening: String = "শোনা বন্ধ করুন"

    override val stopMira: String = "আইদাকে থামান"

    override val speechOff: String =
        "স্পিচ বন্ধ — সেটিংসে মাইক্রোফোনের অনুমতি দিন, বা আইদাকে লিখুন"

    override val openDialler: String = "ডায়ালার খুলুন"

    override val openMessage: String = "মেসেজ খুলুন"

    override val openWhatsApp: String = "হোয়াটসঅ্যাপ খুলুন"

    override val openAppSettings: String = "অ্যাপ সেটিংস খুলুন"

    override fun commandResult(commandName: String): String? = when (commandName) {
        "CreateReminder", "CreatePlaceReminder" -> "রিমাইন্ডার সেট"
        "CreateAlarm" -> "অ্যালার্ম সেট"
        "CreateTimer" -> "টাইমার শুরু"
        "CreateTask" -> "কাজ যোগ হলো"
        "UpdateTask" -> "কাজ আপডেট হলো"
        "CompleteTask" -> "কাজ শেষ"
        "CreateNote" -> "নোট সেভ হলো"
        "RecordPayment" -> "পেমেন্ট লেখা হলো"
        "RecordExpense" -> "খরচ লেখা হলো"
        "RecordIncome" -> "আয় লেখা হলো"
        "CreateClient" -> "ক্লায়েন্ট যোগ হলো"
        "CreateCalendarEvent" -> "ইভেন্ট যোগ হলো"
        "UpdateCalendarEvent" -> "ইভেন্ট আপডেট হলো"
        "DeleteCalendarEvent" -> "ইভেন্ট মোছা হলো"
        "CreateInvoiceDraft" -> "ইনভয়স খসড়া"
        else -> null
    }

    override fun grant(capability: MiraCapability): String = when (capability) {
        MiraCapability.CONTACTS -> "কন্টাক্টের অনুমতি দিন"
        MiraCapability.CALENDAR -> "ক্যালেন্ডারের অনুমতি দিন"
        MiraCapability.EXACT_ALARMS -> "নির্দিষ্ট সময়ের অ্যালার্মের অনুমতি দিন"
        MiraCapability.NOTIFICATIONS -> "নোটিফিকেশনের অনুমতি দিন"
        MiraCapability.MICROPHONE -> "মাইক্রোফোনের অনুমতি দিন"
    }
}
