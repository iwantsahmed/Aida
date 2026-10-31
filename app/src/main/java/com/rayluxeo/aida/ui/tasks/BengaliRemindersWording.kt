package com.rayluxeo.aida.ui.tasks

import com.rayluxeo.aida.domain.model.ReminderKind
import com.rayluxeo.aida.domain.scheduler.PlaceWatchRefusal
import com.rayluxeo.aida.ui.response.bengaliNumerals

/** The Reminders tab in Bengali. Counts go through [bengaliNumerals], never Latin digits. */
internal object BengaliRemindersWording : RemindersWording {

    override fun kindLabel(kind: ReminderKind): String = when (kind) {
        ReminderKind.ONE_TIME -> "রিমাইন্ডার"
        ReminderKind.RECURRING -> "পুনরাবৃত্ত"
        ReminderKind.TASK -> "কাজ"
        ReminderKind.INVOICE -> "ইনভয়েস"
        ReminderKind.CLIENT_FOLLOW_UP -> "যোগাযোগ"
        ReminderKind.BIRTHDAY -> "জন্মদিন"
        ReminderKind.PROJECT_DEADLINE -> "সময়সীমা"
        ReminderKind.SUBSCRIPTION -> "সাবস্ক্রিপশন"
        ReminderKind.PLACE -> "জায়গা"
    }

    /** Bengali already carries the locative in the moment itself — "সকাল ১০টা" needs no preposition. */
    override fun at(moment: String): String = moment

    override fun snoozedUntil(moment: String): String = "$moment পর্যন্ত স্থগিত"

    override fun waitingAt(place: String): String = "$place-এ অপেক্ষা করছে"

    override val notBeingWatched: String = "খেয়াল রাখা হচ্ছে না"

    override fun placesNotWatched(count: Int): String =
        "${bengaliNumerals(count.toString())}টি জায়গার রিমাইন্ডারের পেছনে কিছু নেই।"

    override val watchAgain: String = "আবার খেয়াল রাখুন"

    override val repeats: String = "পুনরাবৃত্ত"

    override val unsnooze: String = "স্থগিত বাতিল"

    override val snoozeCalledOff: String = "নিজের সময়েই ফিরে গেছে।"

    override val cancelTitle: String = "এই রিমাইন্ডার বাতিল করবেন?"

    override fun cancelMessage(title: String): String =
        "$title আর মনে করিয়ে দেবে না। বাতিল করলে আর ফেরানো যাবে না।"

    override val cancelConfirm: String = "বাতিল করুন"

    override val keepReminder: String = "থাক"

    override fun placeRefusal(refusal: PlaceWatchRefusal): String = when (refusal) {
        PlaceWatchRefusal.PERMISSION_MISSING ->
            "ফোনের অবস্থান দেখার অনুমতি না দিলে আইদা কোনো জায়গার জন্য অপেক্ষা করতে পারবে না।"
        PlaceWatchRefusal.BACKGROUND_PERMISSION_MISSING ->
            "অ্যান্ড্রয়েড শুধু অ্যাপ খোলা থাকলেই আইদাকে জায়গা খেয়াল রাখতে দিচ্ছে। সবসময় " +
                "অবস্থানের অনুমতি দিলে পৌঁছানোর সময় সে জানাতে পারবে।"
        PlaceWatchRefusal.LOCATION_DISABLED ->
            "এই ফোনে অবস্থান বন্ধ আছে, তাই আপনি পৌঁছেছেন কিনা আইদা জানতে পারবে না।"
        PlaceWatchRefusal.NO_PROVIDER ->
            "এই ফোনে আইদার ব্যবহারযোগ্য কোনো অবস্থান ব্যবস্থা নেই, তাই জায়গার রিমাইন্ডার এখানে কাজ করবে না।"
        PlaceWatchRefusal.NO_FIX_YET ->
            "আপনি এখন কোথায় আছেন আইদা বুঝতে পারেনি। জানালার কাছে বা বাইরে গেলে সাধারণত কাজ করে।"
        PlaceWatchRefusal.LIMIT_REACHED ->
            "অ্যান্ড্রয়েড যতগুলো জায়গা রাখতে দেয়, আইদা ততগুলোই রেখেছে। নতুনটি যোগ করতে একটি সরান।"
        PlaceWatchRefusal.BATTERY_RESTRICTED ->
            "ব্যাটারি বাঁচাতে অ্যান্ড্রয়েড আইদাকে পেছনে চলতে দিচ্ছে না, তাই পৌঁছানোর খবর সে " +
                "নাও পেতে পারে। বিনা বাধায় চলার অনুমতি দিলে ঠিক হয়ে যাবে।"
    }

    override fun placeRefusalAction(refusal: PlaceWatchRefusal): String? = when (refusal) {
        PlaceWatchRefusal.PERMISSION_MISSING -> "অবস্থানের অনুমতি দিন"
        PlaceWatchRefusal.BACKGROUND_PERMISSION_MISSING -> "সবসময় অনুমতি দিন"
        PlaceWatchRefusal.LOCATION_DISABLED -> "অবস্থান সেটিংস খুলুন"
        PlaceWatchRefusal.BATTERY_RESTRICTED -> "ব্যাটারি সেটিংস খুলুন"
        // A real retry, not a settings trip — nothing on the phone is wrong.
        PlaceWatchRefusal.NO_FIX_YET -> "আবার চেষ্টা করুন"
        PlaceWatchRefusal.NO_PROVIDER, PlaceWatchRefusal.LIMIT_REACHED -> null
    }

    override val watchingAgain: String = "জায়গাগুলো আবার খেয়াল রাখা হচ্ছে।"
}
