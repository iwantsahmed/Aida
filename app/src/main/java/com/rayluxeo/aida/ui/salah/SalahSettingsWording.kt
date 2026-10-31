package com.rayluxeo.aida.ui.salah

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.salah.CalculationMethod
import com.rayluxeo.aida.domain.salah.HighLatitudeRule
import com.rayluxeo.aida.domain.salah.Madhab
import com.rayluxeo.aida.domain.salah.Prayer
import com.rayluxeo.aida.domain.salah.TahajjudReminderMode
import com.rayluxeo.aida.ui.response.bengaliNumerals
import java.util.Locale
import kotlin.math.abs

/**
 * Every word the Salah settings screen says to Abu, in the language he chose. Same phrasebook shape
 * as [com.rayluxeo.aida.ui.settings.SettingsWording]: one interface, one object per language, one
 * selector. Human wording lives here and never in the domain (§4.6).
 *
 * The register is Abu's own: the prayer names and "Tahajjud" stay as he says them, and the tone
 * around Tahajjud is always an invitation and never an instruction (his rule — the last third of
 * the night is a recommended window, never a requirement). Latin numerals in a Bengali line read as
 * a different voice, so numbers go through [bengaliNumerals].
 */
internal interface SalahSettingsWording {

    // Frame and the one dismissible notice.
    val title: String
    val back: String
    val dismiss: String
    val save: String
    val cancel: String
    fun noticeText(notice: SalahSettingsNotice): String
    val openSystemSettings: String

    // Stepper affordances, reused by every ±stepper on the screen.
    val decrease: String
    val increase: String

    // LOCATION
    val sectionLocation: String
    val locationSetupTitle: String
    val locationSetupBody: String
    val cityLabelField: String
    val cityLabelPlaceholder: String
    val useCurrentLocation: String
    val locating: String
    val savedLocation: String
    val editName: String
    val unnamedPlace: String
    fun coordinates(latitude: Double, longitude: Double): String
    val mosqueNote: String
    val selectCity: String
    val changeCity: String
    val searchCityPlaceholder: String
    val popularLocations: String
    val allDistricts: String
    val internationalCities: String
    val noGpsRequiredBadge: String

    // PRAYER CALCULATION
    val sectionCalculation: String
    val calculationMethod: String
    val karachiSuggestion: String
    fun methodName(method: CalculationMethod): String

    /** The concise label for the row, where the full [methodName] would crush the title. */
    fun methodShortName(method: CalculationMethod): String
    val asrMethod: String
    val asrMethodDetail: String
    fun madhabName(madhab: Madhab): String
    val customAngles: String
    val fajrAngle: String
    val ishaAngle: String
    fun angleLabel(degrees: Double): String
    val highLatitudeRule: String
    val highLatitudeRuleDetail: String
    fun highLatitudeRuleName(rule: HighLatitudeRule): String

    /** The concise label for the row; the sheet still lists the full [highLatitudeRuleName]. */
    fun highLatitudeRuleShortName(rule: HighLatitudeRule): String

    // MANUAL ADJUSTMENTS
    val sectionAdjustments: String
    val adjustmentsDetail: String
    fun prayerName(prayer: Prayer): String
    fun minutesLabel(minutes: Int): String

    // NOTIFICATIONS
    val sectionNotifications: String
    val prayerNotifications: String
    val prayerNotificationsDetail: String

    // PRAYER CHECK-INS
    val sectionCheckIns: String
    val askAfterEach: String
    val askAfterEachDetail: String
    val followUpDelay: String
    val followUpDelayDetail: String
    fun delayLabel(minutes: Int): String

    // TAHAJJUD
    val sectionTahajjud: String
    val tahajjudReminder: String
    val tahajjudReminderDetail: String
    val tahajjudTime: String
    fun tahajjudModeName(mode: TahajjudReminderMode): String

    /** The concise label for the row; the sheet still lists the full [tahajjudModeName]. */
    fun tahajjudModeShortName(mode: TahajjudReminderMode): String
    val tahajjudCustomMinutes: String
    val tahajjudNote: String

    // TRACKING & STATISTICS
    val sectionTracking: String
    val trackingDetail: String

    // PRIVACY
    val sectionPrivacy: String
    val privacyDetail: String

    // RESET
    val sectionReset: String
    val resetTracking: String
    val resetTrackingDetail: String
    val resetting: String
    val resetTitle: String
    val resetMessage: String
    val resetConfirm: String
    val resetKeep: String

    // Picker sheet titles
    val chooseMethod: String
    val chooseHighLatitude: String
    val chooseDelay: String
    val chooseTahajjudTime: String
}

internal object EnglishSalahSettingsWording : SalahSettingsWording {

    override val title = "Salah"
    override val back = "Back"
    override val dismiss = "Dismiss"
    override val save = "Save"
    override val cancel = "Cancel"

    override fun noticeText(notice: SalahSettingsNotice) = when (notice) {
        SalahSettingsNotice.LOCATION_UNAVAILABLE ->
            "Mira could not get your location. Turn location on in system settings, then try again."
        SalahSettingsNotice.SAVE_FAILED -> "That change could not be saved. Try once more."
        SalahSettingsNotice.RESET_FAILED -> "The tracking could not be reset. Try once more."
    }
    override val openSystemSettings = "Open settings"

    override val decrease = "Decrease"
    override val increase = "Increase"

    override val sectionLocation = "Location"
    override val locationSetupTitle = "Set your location"
    override val locationSetupBody =
        "Mira needs a place to work out your prayer times, and she does it on this phone — nothing " +
            "leaves the device. Name where you are and take one location fix; it is saved so the " +
            "times keep working offline."
    override val cityLabelField = "Place name"
    override val cityLabelPlaceholder = "e.g. Dhaka"
    override val useCurrentLocation = "Use current location"
    override val locating = "Locating…"
    override val savedLocation = "Your location"
    override val editName = "Edit name"
    override val unnamedPlace = "Unnamed place"

    override fun coordinates(latitude: Double, longitude: Double) =
        formatCoordinates(latitude, longitude)

    override val mosqueNote =
        "Your local mosque's timetable may differ by a minute or two. Nudge each prayer under " +
            "Manual adjustments to match it exactly."
    override val selectCity = "Select city or district"
    override val changeCity = "Change city"
    override val searchCityPlaceholder = "Search district or city…"
    override val popularLocations = "Popular locations"
    override val allDistricts = "Bangladesh districts (64)"
    override val internationalCities = "World cities"
    override val noGpsRequiredBadge = "No GPS or location tracking required"

    override val sectionCalculation = "Prayer calculation"
    override val calculationMethod = "Calculation method"
    override val karachiSuggestion = "Karachi suits most timetables in Bangladesh."
    override fun methodName(method: CalculationMethod) = when (method) {
        CalculationMethod.KARACHI -> "University of Islamic Sciences, Karachi"
        CalculationMethod.MUSLIM_WORLD_LEAGUE -> "Muslim World League"
        CalculationMethod.EGYPTIAN -> "Egyptian General Authority"
        CalculationMethod.UMM_AL_QURA -> "Umm al-Qura, Makkah"
        CalculationMethod.NORTH_AMERICA_ISNA -> "North America (ISNA)"
        CalculationMethod.CUSTOM -> "Custom"
    }
    override fun methodShortName(method: CalculationMethod) = when (method) {
        CalculationMethod.KARACHI -> "Karachi"
        CalculationMethod.MUSLIM_WORLD_LEAGUE -> "MWL"
        CalculationMethod.EGYPTIAN -> "Egyptian"
        CalculationMethod.UMM_AL_QURA -> "Umm al-Qura"
        CalculationMethod.NORTH_AMERICA_ISNA -> "ISNA"
        CalculationMethod.CUSTOM -> "Custom"
    }
    override val asrMethod = "Asr method"
    override val asrMethodDetail = "When the afternoon prayer begins. The one prayer that is a matter of school."
    override fun madhabName(madhab: Madhab) = when (madhab) {
        Madhab.SHAFI -> "Standard (Shafi)"
        Madhab.HANAFI -> "Hanafi"
    }
    override val customAngles = "Custom angles"
    override val fajrAngle = "Fajr angle"
    override val ishaAngle = "Isha angle"
    override fun angleLabel(degrees: Double) = formatAngle(degrees)
    override val highLatitudeRule = "High-latitude rule"
    override val highLatitudeRuleDetail =
        "Used only far north or south, where twilight never fully ends. It never changes your times here."
    override fun highLatitudeRuleName(rule: HighLatitudeRule) = when (rule) {
        HighLatitudeRule.MIDDLE_OF_NIGHT -> "Middle of the night"
        HighLatitudeRule.SEVENTH_OF_NIGHT -> "One seventh of the night"
        HighLatitudeRule.TWILIGHT_ANGLE -> "Angle-based"
        HighLatitudeRule.NONE -> "None"
    }
    override fun highLatitudeRuleShortName(rule: HighLatitudeRule) = when (rule) {
        HighLatitudeRule.MIDDLE_OF_NIGHT -> "Middle of night"
        HighLatitudeRule.SEVENTH_OF_NIGHT -> "One-seventh"
        HighLatitudeRule.TWILIGHT_ANGLE -> "Angle-based"
        HighLatitudeRule.NONE -> "None"
    }

    override val sectionAdjustments = "Manual adjustments"
    override val adjustmentsDetail = "Nudge any prayer to line Mira up with your mosque's timetable."
    override fun prayerName(prayer: Prayer) = when (prayer) {
        Prayer.FAJR -> "Fajr"
        Prayer.DHUHR -> "Dhuhr"
        Prayer.ASR -> "Asr"
        Prayer.MAGHRIB -> "Maghrib"
        Prayer.ISHA -> "Isha"
        Prayer.TAHAJJUD -> "Tahajjud"
    }
    override fun minutesLabel(minutes: Int) = formatMinutes(minutes)

    override val sectionNotifications = "Notifications"
    override val prayerNotifications = "Prayer notifications"
    override val prayerNotificationsDetail = "A quiet notification when each prayer's time comes in."

    override val sectionCheckIns = "Prayer check-ins"
    override val askAfterEach = "Ask after each prayer"
    override val askAfterEachDetail = "A little after each time, Mira asks whether you prayed."
    override val followUpDelay = "Follow-up delay"
    override val followUpDelayDetail = "How long after the time she asks."
    override fun delayLabel(minutes: Int) = formatDelay(minutes)

    override val sectionTahajjud = "Tahajjud"
    override val tahajjudReminder = "Tahajjud reminder"
    override val tahajjudReminderDetail =
        "A gentle nudge for the night prayer, if you would like one."
    override val tahajjudTime = "Reminder time"
    override fun tahajjudModeName(mode: TahajjudReminderMode) = when (mode) {
        TahajjudReminderMode.LAST_THIRD -> "At the last third of the night"
        TahajjudReminderMode.FIFTEEN_AFTER_LAST_THIRD -> "15 minutes after"
        TahajjudReminderMode.THIRTY_AFTER_LAST_THIRD -> "30 minutes after"
        TahajjudReminderMode.CUSTOM -> "Custom"
        TahajjudReminderMode.DISABLED -> "Off"
    }
    override fun tahajjudModeShortName(mode: TahajjudReminderMode) = when (mode) {
        TahajjudReminderMode.LAST_THIRD -> "Last third"
        TahajjudReminderMode.FIFTEEN_AFTER_LAST_THIRD -> "+15 min"
        TahajjudReminderMode.THIRTY_AFTER_LAST_THIRD -> "+30 min"
        TahajjudReminderMode.CUSTOM -> "Custom"
        TahajjudReminderMode.DISABLED -> "Off"
    }
    override val tahajjudCustomMinutes = "Minutes into the last third"
    override val tahajjudNote =
        "The last third of the night is a recommended window, never a requirement."

    override val sectionTracking = "Tracking & statistics"
    override val trackingDetail =
        "Your prayer log and your monthly statistics live on the Salah screen and its month view."

    override val sectionPrivacy = "Privacy"
    override val privacyDetail =
        "Prayer times are computed on this phone from your saved location. Nothing about your " +
            "prayers or your place ever leaves the device."

    override val sectionReset = "Reset"
    override val resetTracking = "Reset Salah tracking"
    override val resetTrackingDetail = "Erase every prayer you have logged. Your settings stay."
    override val resetting = "Resetting…"
    override val resetTitle = "Reset Salah tracking?"
    override val resetMessage =
        "Every prayer and Tahajjud night you have logged is erased from this phone. Your location " +
            "and calculation settings are kept. This cannot be undone."
    override val resetConfirm = "Reset tracking"
    override val resetKeep = "Keep it"

    override val chooseMethod = "Calculation method"
    override val chooseHighLatitude = "High-latitude rule"
    override val chooseDelay = "Follow-up delay"
    override val chooseTahajjudTime = "Reminder time"
}

internal object BengaliSalahSettingsWording : SalahSettingsWording {

    override val title = "সালাত"
    override val back = "পেছনে যান"
    override val dismiss = "বন্ধ করুন"
    override val save = "সেভ করুন"
    override val cancel = "বাতিল"

    override fun noticeText(notice: SalahSettingsNotice) = when (notice) {
        SalahSettingsNotice.LOCATION_UNAVAILABLE ->
            "আইদা আপনার অবস্থান পায়নি। সিস্টেম সেটিংসে লোকেশন চালু করে আবার চেষ্টা করুন।"
        SalahSettingsNotice.SAVE_FAILED -> "পরিবর্তনটি সেভ করা গেল না। আরেকবার চেষ্টা করুন।"
        SalahSettingsNotice.RESET_FAILED -> "ট্র্যাকিং রিসেট করা গেল না। আরেকবার চেষ্টা করুন।"
    }
    override val openSystemSettings = "সেটিংস খুলুন"

    override val decrease = "কমান"
    override val increase = "বাড়ান"

    override val sectionLocation = "অবস্থান"
    override val locationSetupTitle = "আপনার অবস্থান ঠিক করুন"
    override val locationSetupBody =
        "নামাজের সময় বের করতে আইদার একটি জায়গা দরকার, আর সে কাজটি এই ফোনেই করে — কিছুই ডিভাইস " +
            "ছাড়ে না। আপনি কোথায় আছেন তার একটি নাম দিন আর একবার অবস্থান নিন; এটি সেভ থাকে, তাই " +
            "ইন্টারনেট ছাড়াও সময় ঠিকঠাক চলে।"
    override val cityLabelField = "জায়গার নাম"
    override val cityLabelPlaceholder = "যেমন ঢাকা"
    override val useCurrentLocation = "এখনকার অবস্থান নিন"
    override val locating = "অবস্থান নেওয়া হচ্ছে…"
    override val savedLocation = "আপনার অবস্থান"
    override val editName = "নাম বদলান"
    override val unnamedPlace = "নামহীন জায়গা"

    override fun coordinates(latitude: Double, longitude: Double) =
        bengaliNumerals(formatCoordinates(latitude, longitude))

    override val mosqueNote =
        "আপনার স্থানীয় মসজিদের সময়সূচি এক-দুই মিনিট আলাদা হতে পারে। হুবহু মেলাতে নিচের ম্যানুয়াল " +
            "সমন্বয়ে প্রতিটি নামাজ একটু সরিয়ে নিন।"
    override val selectCity = "জেলা বা শহর নির্বাচন করুন"
    override val changeCity = "শহর পরিবর্তন করুন"
    override val searchCityPlaceholder = "জেলা বা শহর খুঁজুন…"
    override val popularLocations = "জনপ্রিয় স্থানসমূহ"
    override val allDistricts = "বাংলাদেশের জেলাসমূহ (৬৪টি)"
    override val internationalCities = "বিশ্বের প্রধান শহরসমূহ"
    override val noGpsRequiredBadge = "জিপিএস বা লোকেশন ট্র্যাকিং প্রয়োজন নেই"

    override val sectionCalculation = "নামাজের হিসাব"
    override val calculationMethod = "হিসাবের পদ্ধতি"
    override val karachiSuggestion = "বাংলাদেশের বেশিরভাগ সময়সূচির সাথে করাচি মানানসই।"
    override fun methodName(method: CalculationMethod) = when (method) {
        CalculationMethod.KARACHI -> "ইউনিভার্সিটি অফ ইসলামিক সায়েন্সেস, করাচি"
        CalculationMethod.MUSLIM_WORLD_LEAGUE -> "মুসলিম ওয়ার্ল্ড লিগ"
        CalculationMethod.EGYPTIAN -> "মিশরীয় কর্তৃপক্ষ"
        CalculationMethod.UMM_AL_QURA -> "উম্মুল কুরা, মক্কা"
        CalculationMethod.NORTH_AMERICA_ISNA -> "উত্তর আমেরিকা (ISNA)"
        CalculationMethod.CUSTOM -> "নিজস্ব"
    }
    override fun methodShortName(method: CalculationMethod) = when (method) {
        CalculationMethod.KARACHI -> "করাচি"
        CalculationMethod.MUSLIM_WORLD_LEAGUE -> "MWL"
        CalculationMethod.EGYPTIAN -> "মিশরীয়"
        CalculationMethod.UMM_AL_QURA -> "উম্মুল কুরা"
        CalculationMethod.NORTH_AMERICA_ISNA -> "ISNA"
        CalculationMethod.CUSTOM -> "নিজস্ব"
    }
    override val asrMethod = "আসরের পদ্ধতি"
    override val asrMethodDetail = "আসর কখন শুরু হয়। একমাত্র এই নামাজটি মাজহাবের ব্যাপার।"
    override fun madhabName(madhab: Madhab) = when (madhab) {
        Madhab.SHAFI -> "সাধারণ (শাফি)"
        Madhab.HANAFI -> "হানাফি"
    }
    override val customAngles = "নিজস্ব কোণ"
    override val fajrAngle = "ফজরের কোণ"
    override val ishaAngle = "ইশার কোণ"
    override fun angleLabel(degrees: Double) = bengaliNumerals(formatAngle(degrees))
    override val highLatitudeRule = "উঁচু অক্ষাংশের নিয়ম"
    override val highLatitudeRuleDetail =
        "শুধু অনেক উত্তরে বা দক্ষিণে কাজে লাগে, যেখানে গোধূলি পুরোপুরি শেষ হয় না। এখানে আপনার সময় বদলায় না।"
    override fun highLatitudeRuleName(rule: HighLatitudeRule) = when (rule) {
        HighLatitudeRule.MIDDLE_OF_NIGHT -> "রাতের মাঝামাঝি"
        HighLatitudeRule.SEVENTH_OF_NIGHT -> "রাতের এক-সপ্তমাংশ"
        HighLatitudeRule.TWILIGHT_ANGLE -> "কোণ-ভিত্তিক"
        HighLatitudeRule.NONE -> "কিছু না"
    }
    override fun highLatitudeRuleShortName(rule: HighLatitudeRule) = when (rule) {
        HighLatitudeRule.MIDDLE_OF_NIGHT -> "মাঝরাত"
        HighLatitudeRule.SEVENTH_OF_NIGHT -> "এক-সপ্তমাংশ"
        HighLatitudeRule.TWILIGHT_ANGLE -> "কোণ-ভিত্তিক"
        HighLatitudeRule.NONE -> "কিছু না"
    }

    override val sectionAdjustments = "ম্যানুয়াল সমন্বয়"
    override val adjustmentsDetail = "আপনার মসজিদের সময়সূচির সাথে মেলাতে যেকোনো নামাজ একটু সরান।"
    override fun prayerName(prayer: Prayer) = when (prayer) {
        Prayer.FAJR -> "ফজর"
        Prayer.DHUHR -> "যোহর"
        Prayer.ASR -> "আসর"
        Prayer.MAGHRIB -> "মাগরিব"
        Prayer.ISHA -> "ইশা"
        Prayer.TAHAJJUD -> "তাহাজ্জুদ"
    }
    override fun minutesLabel(minutes: Int) = bengaliNumerals(formatMinutesBangla(minutes))

    override val sectionNotifications = "নোটিফিকেশন"
    override val prayerNotifications = "নামাজের নোটিফিকেশন"
    override val prayerNotificationsDetail = "প্রতিটি নামাজের সময় হলে একটি শান্ত নোটিফিকেশন।"

    override val sectionCheckIns = "নামাজের খোঁজ"
    override val askAfterEach = "প্রতিটি নামাজের পরে জিজ্ঞেস করুক"
    override val askAfterEachDetail = "প্রতিটি সময়ের একটু পরে আইদা জিজ্ঞেস করবে আপনি পড়েছেন কিনা।"
    override val followUpDelay = "কতক্ষণ পরে জিজ্ঞেস করবে"
    override val followUpDelayDetail = "সময়ের কতক্ষণ পরে সে জিজ্ঞেস করবে।"
    override fun delayLabel(minutes: Int) = bengaliNumerals(formatDelayBangla(minutes))

    override val sectionTahajjud = "তাহাজ্জুদ"
    override val tahajjudReminder = "তাহাজ্জুদের রিমাইন্ডার"
    override val tahajjudReminderDetail = "চাইলে রাতের নামাজের জন্য একটি মৃদু মনে করিয়ে দেওয়া।"
    override val tahajjudTime = "রিমাইন্ডারের সময়"
    override fun tahajjudModeName(mode: TahajjudReminderMode) = when (mode) {
        TahajjudReminderMode.LAST_THIRD -> "রাতের শেষ তৃতীয়াংশে"
        TahajjudReminderMode.FIFTEEN_AFTER_LAST_THIRD -> "১৫ মিনিট পরে"
        TahajjudReminderMode.THIRTY_AFTER_LAST_THIRD -> "৩০ মিনিট পরে"
        TahajjudReminderMode.CUSTOM -> "নিজের মতো"
        TahajjudReminderMode.DISABLED -> "বন্ধ"
    }
    override fun tahajjudModeShortName(mode: TahajjudReminderMode) = when (mode) {
        TahajjudReminderMode.LAST_THIRD -> "শেষ তৃতীয়াংশ"
        TahajjudReminderMode.FIFTEEN_AFTER_LAST_THIRD -> "+১৫ মিনিট"
        TahajjudReminderMode.THIRTY_AFTER_LAST_THIRD -> "+৩০ মিনিট"
        TahajjudReminderMode.CUSTOM -> "নিজের মতো"
        TahajjudReminderMode.DISABLED -> "বন্ধ"
    }
    override val tahajjudCustomMinutes = "শেষ তৃতীয়াংশের ভেতর কত মিনিট"
    override val tahajjudNote = "রাতের শেষ তৃতীয়াংশ একটি প্রস্তাবিত সময়, কখনও বাধ্যতামূলক নয়।"

    override val sectionTracking = "ট্র্যাকিং ও পরিসংখ্যান"
    override val trackingDetail =
        "আপনার নামাজের রেকর্ড আর মাসিক পরিসংখ্যান সালাত স্ক্রিনে আর তার মাস-ভিউতে থাকে।"

    override val sectionPrivacy = "গোপনীয়তা"
    override val privacyDetail =
        "নামাজের সময় এই ফোনেই আপনার সেভ করা অবস্থান থেকে বের করা হয়। আপনার নামাজ বা অবস্থানের " +
            "কিছুই কখনও ডিভাইস ছাড়ে না।"

    override val sectionReset = "রিসেট"
    override val resetTracking = "সালাত ট্র্যাকিং রিসেট করুন"
    override val resetTrackingDetail = "আপনার রাখা প্রতিটি নামাজের রেকর্ড মুছে যাবে। সেটিংস থেকে যাবে।"
    override val resetting = "রিসেট হচ্ছে…"
    override val resetTitle = "সালাত ট্র্যাকিং রিসেট করবেন?"
    override val resetMessage =
        "আপনার রাখা প্রতিটি নামাজ আর তাহাজ্জুদের রাত এই ফোন থেকে মুছে যাবে। আপনার অবস্থান আর " +
            "হিসাবের সেটিংস থেকে যাবে। এটি ফেরানো যাবে না।"
    override val resetConfirm = "ট্র্যাকিং রিসেট করুন"
    override val resetKeep = "রেখে দিন"

    override val chooseMethod = "হিসাবের পদ্ধতি"
    override val chooseHighLatitude = "উঁচু অক্ষাংশের নিয়ম"
    override val chooseDelay = "কতক্ষণ পরে জিজ্ঞেস করবে"
    override val chooseTahajjudTime = "রিমাইন্ডারের সময়"
}

internal fun salahSettingsWording(language: SpokenLanguage): SalahSettingsWording = when (language) {
    SpokenLanguage.ENGLISH -> EnglishSalahSettingsWording
    SpokenLanguage.BENGALI -> BengaliSalahSettingsWording
}

// --- Shared number formatting, written once in Latin and folded to Bengali numerals where needed ---

private fun formatCoordinates(latitude: Double, longitude: Double): String {
    val ns = if (latitude >= 0) "N" else "S"
    val ew = if (longitude >= 0) "E" else "W"
    return String.format(Locale.US, "%.4f° %s, %.4f° %s", abs(latitude), ns, abs(longitude), ew)
}

private fun formatAngle(degrees: Double): String = String.format(Locale.US, "%.1f°", degrees)

/** "+2 min", "0 min", "−3 min" — a true minus sign, not a hyphen. */
private fun formatMinutes(minutes: Int): String = when {
    minutes > 0 -> "+$minutes min"
    minutes < 0 -> "−${abs(minutes)} min"
    else -> "0 min"
}

private fun formatMinutesBangla(minutes: Int): String = when {
    minutes > 0 -> "+$minutes মিনিট"
    minutes < 0 -> "−${abs(minutes)} মিনিট"
    else -> "0 মিনিট"
}

private fun formatDelay(minutes: Int): String = if (minutes == 1) "1 minute" else "$minutes minutes"

private fun formatDelayBangla(minutes: Int): String = "$minutes মিনিট"
