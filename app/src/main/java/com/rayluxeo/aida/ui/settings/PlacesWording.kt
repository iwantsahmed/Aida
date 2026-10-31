package com.rayluxeo.aida.ui.settings

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.SavedPlace
import com.rayluxeo.aida.domain.scheduler.PlaceWatchRefusal
import com.rayluxeo.aida.ui.response.bengaliNumerals
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Every word the Places screen says to Abu, in the language he chose.
 *
 * Same phrasebook shape as [AssistantWording] and [BriefSettingsWording]: one interface, one
 * object per language, one selector — so either language can be reworded without reading the
 * other.
 *
 * The refusal sentences are deliberately this screen's own rather than
 * [com.rayluxeo.aida.ui.tasks.RemindersWording]'s. They answer a different question: those say why
 * Mira cannot *watch* a place, these say why she cannot *read where Abu is standing*, and a
 * sentence about watching shown at the moment a fix failed would send him looking for the wrong
 * switch. The enum behind them is shared, which is what keeps the two lists in step.
 *
 * Bengali here is the register Abu actually speaks, with the English words for Android's own
 * things kept and written in Bengali script. Numbers go through [bengaliNumerals], because a
 * Latin digit in a Bengali line reads as a different voice.
 */
internal interface PlacesWording {

    // The frame: bar, back, and the one dismissible notice.
    val title: String
    val subtitle: String
    val back: String
    val dismiss: String

    // The row on the Settings screen that reaches this one.
    val settingsRowTitle: String
    val settingsRowSupporting: String

    // The list itself, and the state it is in before Abu has saved anything.
    val intro: String
    val section: String
    val emptyMessage: String

    // Taking a fix.
    val saveHere: String
    val takingAFix: String

    // Naming the spot he is standing on.
    val nameTitle: String
    val nameMessage: String
    val nameLabel: String
    val save: String
    val cancel: String

    // Renaming one he has already saved.
    val renameTitle: String
    val renameMessage: String
    val rename: String

    // Forgetting one. Destructive, so it is asked before it is done.
    fun forgetTitle(label: String): String
    fun forgetMessage(label: String): String
    val forget: String
    val keep: String

    /** The bare glyph on a row needs a name of its own, and the name has to say which place. */
    fun forgetContentDescription(label: String): String

    /** The quiet second line of a row: how close counts as there, and where there is. */
    fun placeDetail(place: SavedPlace): String

    /** Something Mira could not do, as a sentence rather than an error code. */
    fun problem(problem: PlacesProblem): String

    /** Why there was no fix. Every reason gets its own line; none of them is a fault. */
    fun refusal(refusal: PlaceWatchRefusal): String

    /**
     * What the way out of a refusal is called, or null when there is none.
     *
     * Null for exactly the two Abu cannot act on: a phone with no location provider is not
     * something a settings screen changes, and a full register is Mira's to empty. Offering a
     * button for either would be a control that does nothing.
     *
     * What the button then does is [PlaceWatchRefusal.remedy]'s answer, not this one's — most of
     * these open an Android screen, and "Try again" retakes the fix.
     */
    fun refusalAction(refusal: PlaceWatchRefusal): String?
}

internal object EnglishPlacesWording : PlacesWording {

    override val title = "Places"
    override val subtitle = "Where Aida can wait for you"
    override val back = "Back"
    override val dismiss = "Dismiss"

    override val settingsRowTitle = "Places Aida knows"
    override val settingsRowSupporting =
        "Save where you are standing, so a reminder can wait there."

    override val intro =
        "Aida has no map and no address lookup, and nothing about where you are leaves this " +
            "phone. A place becomes known the only honest way: you stand at it and save it."
    override val section = "Saved"
    override val emptyMessage =
        "Aida knows no places yet. Stand somewhere and save it, and she can wait for you there — " +
            "\"remind me to buy printer ink when I get to the office\" works once the office is a " +
            "place she knows."

    override val saveHere = "Save where I am now"
    override val takingAFix = "Taking a fix"

    override val nameTitle = "Name this place"
    override val nameMessage =
        "Aida keeps the spot you are standing on under this name. Say the name later and she " +
            "will know where you mean."
    override val nameLabel = "Name"
    override val save = "Save"
    override val cancel = "Cancel"

    override val renameTitle = "Rename this place"
    override val renameMessage =
        "The old name stops working the moment this one takes, so say the new one from now on."
    override val rename = "Rename"

    override fun forgetTitle(label: String) = "Forget $label?"

    override fun forgetMessage(label: String) =
        "Aida stops knowing where $label is. A reminder already waiting there keeps its own copy " +
            "of the spot and is left alone."

    override val forget = "Forget it"
    override val keep = "Keep it"

    override fun forgetContentDescription(label: String) = "Forget $label"

    override fun placeDetail(place: SavedPlace) =
        "${place.radiusMeters.roundToInt()} m · ${coordinatesOf(place)}"

    override fun problem(problem: PlacesProblem) = when (problem) {
        PlacesProblem.NAME_BLANK -> "Give the place a name and Aida will save it."
        PlacesProblem.NAME_TAKEN -> "A place already answers to that name. Choose another."
        PlacesProblem.COULD_NOT_SAVE -> "Aida could not save that place. Nothing was changed."
        PlacesProblem.COULD_NOT_RENAME ->
            "Aida could not rename that place. It keeps the name it had."
        PlacesProblem.COULD_NOT_FORGET -> "Aida could not forget that place. It is still saved."
    }

    override fun refusal(refusal: PlaceWatchRefusal) = when (refusal) {
        PlaceWatchRefusal.PERMISSION_MISSING ->
            "Aida cannot read where you are until you allow her to see this phone's location. " +
                "Nothing is sent anywhere — the coordinate stays on the phone."
        PlaceWatchRefusal.NO_FIX_YET ->
            "Aida could not get a position just now. Try again — it usually works better near a " +
                "window or outdoors."
        PlaceWatchRefusal.BACKGROUND_PERMISSION_MISSING ->
            "Android only lets Aida see where you are while she is open. That is enough to save " +
                "a place; a reminder waiting at one needs location all the time."
        PlaceWatchRefusal.LOCATION_DISABLED ->
            "Location is switched off on this phone, so there is nothing for Aida to read."
        PlaceWatchRefusal.NO_PROVIDER ->
            "This phone has no location provider Aida can use, so a place cannot be saved here."
        PlaceWatchRefusal.LIMIT_REACHED ->
            "Aida is already holding as many places as Android will take. Forget one to make room."
        PlaceWatchRefusal.BATTERY_RESTRICTED ->
            "Android has restricted Aida in the background to save battery, so it would not read " +
                "a location just now. Allowing her to run unrestricted fixes it."
    }

    override fun refusalAction(refusal: PlaceWatchRefusal): String? = when (refusal) {
        PlaceWatchRefusal.PERMISSION_MISSING -> "Allow location in settings"
        PlaceWatchRefusal.NO_FIX_YET -> "Try again"
        PlaceWatchRefusal.BACKGROUND_PERMISSION_MISSING -> "Allow all the time"
        PlaceWatchRefusal.LOCATION_DISABLED -> "Open location settings"
        PlaceWatchRefusal.BATTERY_RESTRICTED -> "Open battery settings"
        PlaceWatchRefusal.NO_PROVIDER, PlaceWatchRefusal.LIMIT_REACHED -> null
    }
}

internal object BengaliPlacesWording : PlacesWording {

    override val title = "জায়গা"
    override val subtitle = "যেখানে আইদা আপনার জন্য অপেক্ষা করতে পারে"
    override val back = "পেছনে যান"
    override val dismiss = "বন্ধ করুন"

    override val settingsRowTitle = "আইদার চেনা জায়গা"
    override val settingsRowSupporting =
        "যেখানে দাঁড়িয়ে আছেন সেটি সেভ করুন, যাতে সেখানে রিমাইন্ডার অপেক্ষা করতে পারে।"

    override val intro =
        "আইদার কোনো ম্যাপ নেই, ঠিকানা খোঁজারও ব্যবস্থা নেই, আর আপনি কোথায় আছেন তা এই ফোনের " +
            "বাইরে যায় না। জায়গা চেনার একটাই সৎ উপায় — সেখানে দাঁড়িয়ে সেভ করা।"
    override val section = "সেভ করা"
    override val emptyMessage =
        "আইদা এখনও কোনো জায়গা চেনে না। কোথাও দাঁড়িয়ে সেটি সেভ করুন, তাহলে সে সেখানে আপনার " +
            "জন্য অপেক্ষা করতে পারবে — \"অফিসে পৌঁছালে প্রিন্টারের কালি কিনতে মনে করিয়ে দিও\" " +
            "তখনই কাজ করে, যখন অফিস তার চেনা একটি জায়গা।"

    override val saveHere = "আমি এখন যেখানে আছি, সেভ করুন"
    override val takingAFix = "অবস্থান নেওয়া হচ্ছে"

    override val nameTitle = "এই জায়গার নাম দিন"
    override val nameMessage =
        "আপনি এখন যেখানে দাঁড়িয়ে আছেন, আইদা সেটি এই নামে রাখবে। পরে নামটি বললেই সে বুঝবে " +
            "আপনি কোন জায়গার কথা বলছেন।"
    override val nameLabel = "নাম"
    override val save = "সেভ করুন"
    override val cancel = "বাতিল"

    override val renameTitle = "নাম বদলান"
    override val renameMessage =
        "নতুন নামটি কার্যকর হওয়ার সাথে সাথেই পুরনো নামে আর কাজ হবে না, তাই এখন থেকে নতুন নামটিই বলবেন।"
    override val rename = "নাম বদলান"

    override fun forgetTitle(label: String) = "$label মুছে ফেলবেন?"

    override fun forgetMessage(label: String) =
        "$label কোথায়, আইদা আর জানবে না। সেখানে আগে থেকে অপেক্ষা করা কোনো রিমাইন্ডারের কাছে " +
            "জায়গাটির নিজস্ব কপি আছে, তাতে হাত পড়বে না।"

    override val forget = "মুছে ফেলুন"
    override val keep = "রেখে দিন"

    override fun forgetContentDescription(label: String) = "$label মুছে ফেলুন"

    override fun placeDetail(place: SavedPlace) =
        bengaliNumerals("${place.radiusMeters.roundToInt()} মি · ${coordinatesOf(place)}")

    override fun problem(problem: PlacesProblem) = when (problem) {
        PlacesProblem.NAME_BLANK -> "একটি নাম দিন, তাহলেই আইদা জায়গাটি সেভ করবে।"
        PlacesProblem.NAME_TAKEN -> "এই নামে একটি জায়গা আগে থেকেই আছে। অন্য নাম দিন।"
        PlacesProblem.COULD_NOT_SAVE -> "আইদা জায়গাটি সেভ করতে পারেনি। কিছুই বদলায়নি।"
        PlacesProblem.COULD_NOT_RENAME -> "আইদা নামটি বদলাতে পারেনি। আগের নামই রয়ে গেছে।"
        PlacesProblem.COULD_NOT_FORGET -> "আইদা জায়গাটি মুছতে পারেনি। এটি এখনও সেভ করা আছে।"
    }

    override fun refusal(refusal: PlaceWatchRefusal) = when (refusal) {
        PlaceWatchRefusal.PERMISSION_MISSING ->
            "ফোনের অবস্থান দেখার অনুমতি না দিলে আইদা আপনি কোথায় আছেন তা পড়তে পারবে না। " +
                "কিছুই কোথাও পাঠানো হয় না — স্থানাঙ্কটি এই ফোনেই থাকে।"
        PlaceWatchRefusal.NO_FIX_YET ->
            "আইদা এই মুহূর্তে অবস্থানটি পায়নি। আবার চেষ্টা করুন — জানালার পাশে বা বাইরে " +
                "সাধারণত ভালো কাজ করে।"
        PlaceWatchRefusal.BACKGROUND_PERMISSION_MISSING ->
            "অ্যান্ড্রয়েড শুধু অ্যাপ খোলা থাকলেই আইদাকে আপনার অবস্থান দেখতে দিচ্ছে। জায়গা সেভ " +
                "করার জন্য এটুকুই যথেষ্ট; তবে সেখানে অপেক্ষা করা রিমাইন্ডারের জন্য সবসময়ের অনুমতি লাগবে।"
        PlaceWatchRefusal.LOCATION_DISABLED ->
            "এই ফোনে অবস্থান বন্ধ আছে, তাই আইদার পড়ার মতো কিছু নেই।"
        PlaceWatchRefusal.NO_PROVIDER ->
            "এই ফোনে আইদার ব্যবহারযোগ্য কোনো অবস্থান ব্যবস্থা নেই, তাই এখানে জায়গা সেভ করা যাবে না।"
        PlaceWatchRefusal.LIMIT_REACHED ->
            "অ্যান্ড্রয়েড যতগুলো জায়গা রাখতে দেয়, আইদা ততগুলোই রেখেছে। জায়গা করতে একটি মুছে ফেলুন।"
        PlaceWatchRefusal.BATTERY_RESTRICTED ->
            "ব্যাটারি বাঁচাতে অ্যান্ড্রয়েড আইদাকে পেছনে চলতে দিচ্ছে না, তাই এখন অবস্থান পড়া " +
                "যাবে না। বিনা বাধায় চলার অনুমতি দিলে ঠিক হয়ে যাবে।"
    }

    override fun refusalAction(refusal: PlaceWatchRefusal): String? = when (refusal) {
        PlaceWatchRefusal.PERMISSION_MISSING -> "সেটিংসে অনুমতি দিন"
        PlaceWatchRefusal.NO_FIX_YET -> "আবার চেষ্টা করুন"
        PlaceWatchRefusal.BACKGROUND_PERMISSION_MISSING -> "সবসময় অনুমতি দিন"
        PlaceWatchRefusal.LOCATION_DISABLED -> "অবস্থান সেটিংস খুলুন"
        PlaceWatchRefusal.BATTERY_RESTRICTED -> "ব্যাটারি সেটিংস খুলুন"
        PlaceWatchRefusal.NO_PROVIDER, PlaceWatchRefusal.LIMIT_REACHED -> null
    }
}

internal fun placesWording(language: SpokenLanguage): PlacesWording = when (language) {
    SpokenLanguage.ENGLISH -> EnglishPlacesWording
    SpokenLanguage.BENGALI -> BengaliPlacesWording
}

/**
 * The coordinate as Abu can check it, in one copy for both languages.
 *
 * Four decimal places is about eleven metres — fine enough to tell two places apart on a street
 * and coarse enough not to read as false precision beside a radius measured in hundreds of
 * metres. [Locale.US] is forced because a decimal comma between two comma-separated numbers is
 * unreadable; the Bengali object converts the digits afterwards.
 */
private fun coordinatesOf(place: SavedPlace): String =
    String.format(Locale.US, "%.4f, %.4f", place.latitude, place.longitude)
