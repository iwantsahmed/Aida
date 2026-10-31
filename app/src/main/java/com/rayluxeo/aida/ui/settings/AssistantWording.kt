package com.rayluxeo.aida.ui.settings

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.ui.response.bengaliNumerals

/**
 * Every word the assistant setup screen says to Abu, in the language he chose.
 *
 * The screen was written in English only, which made it the one place in Mira where he could be
 * told in a language he did not pick that something on his own phone was in the way. This is the
 * same phrasebook shape as `ui/home`, `ui/tasks` and `ui/response`: one interface, one object per
 * language, one selector — so a reader can reword one language without reading the other.
 *
 * The Samsung steps are deliberately absent. They are already built per language by
 * `AssistantRoleControllerImpl` and arrive on `OemAssistantGuidance`; a second copy here would be
 * the two-lists problem §7.12 of `AGENTS.md` forbids.
 *
 * Bengali here is the register Abu actually speaks. The English words for Android's own things are
 * kept — assistant, gesture, microphone, service, session, widget, shortcut, wake word — written in
 * Bengali script the way he says them, never replaced by a Sanskritised coinage he would have to
 * decode. Only a label he has to find on his own phone in English, such as Quick Settings, stays in
 * Latin. Numbers go through [bengaliNumerals], because a Latin digit in a Bengali line reads as a
 * different voice.
 */
internal interface AssistantWording {

    // The frame: bar, back, and the one dismissible notice.
    val title: String
    val subtitle: String
    val back: String
    val dismiss: String

    // What Android says right now, and the one control that can change it.
    val rightNow: String
    val openAndroidAssistantSetting: String
    val settingsScreenAbsent: String
    val headlineHeld: String
    val headlineNotAssistant: String
    val headlineCannotChange: String
    val headlineAsking: String
    val explanationAsking: String
    val explanationHeld: String
    val explanationUnsupported: String
    val explanationNothingHolds: String
    fun explanationHeldByOther(holder: String): String

    /** Used wherever the platform named a holder Mira cannot put a label to. */
    val anotherApp: String

    // The test, its button, and the one line that sums up its rows.
    val test: String
    val testDescription: String
    val testing: String
    val runTest: String
    val everythingInPlace: String
    val oneThingInTheWay: String
    fun thingsInTheWay(count: Int): String

    // The four things the test looks at.
    val checkRole: String
    val checkService: String
    val checkMicrophone: String
    val checkSession: String

    // Why each of them came out the way it did.
    val roleHeld: String
    fun roleHeldByOther(holder: String): String
    val roleUnheld: String
    val roleUnsupported: String
    val serviceActive: String
    val serviceNotDeclared: String
    val serviceNotBound: String
    val microphoneGranted: String
    val microphoneMissing: String
    val sessionOpened: String
    val sessionNoEntryPoint: String
    val sessionNotTried: String

    // The single action that settles a failed row. Kept short: the text button is one line.
    val openAssistantSettings: String
    val grantMicrophone: String

    // The manufacturer's own extra steps. The steps themselves come from the controller.
    val oemIntro: String
    val openThatSetting: String
    fun stepNumber(number: Int): String

    // Every way Mira can actually be started, and the plain truth about the wake word.
    val howToReachHer: String
    val hotwordAvailable: String
    val hotwordSystemOnly: String
    val hotwordNoHardware: String
    val hotwordNotTheAssistant: String
    val entryGesture: String
    val entryGestureDetail: String
    val entryVoiceButton: String
    val entryVoiceButtonDetail: String
    val entryQuickSettings: String
    val entryQuickSettingsDetail: String
    val entryWidget: String
    val entryWidgetDetail: String
    val entryShortcut: String
    val entryShortcutDetail: String
    val entryShareSheet: String
    val entryShareSheetDetail: String

    // When a screen Mira tried to open would not open. Never a raw exception.
    val assistantSettingsWouldNotOpen: String
    val oemSettingWouldNotOpen: String
}

internal object EnglishAssistantWording : AssistantWording {

    override val title = "Assistant"
    override val subtitle = "Set Aida as my assistant"
    override val back = "Back"
    override val dismiss = "Dismiss"

    override val rightNow = "Right now"
    override val openAndroidAssistantSetting = "Open Android's assistant setting"
    override val settingsScreenAbsent =
        "This phone has no screen for changing the assistant app, so the gesture cannot be moved " +
            "to Aida here. Everything below still reaches her."
    override val headlineHeld = "Aida is the assistant"
    override val headlineNotAssistant = "Aida is not the assistant"
    override val headlineCannotChange = "This phone cannot change its assistant"
    override val headlineAsking = "Asking Android"
    override val explanationAsking = "Reading what Android has to say."
    override val explanationHeld =
        "Android sends the assist gesture to Aida. Which gesture that is depends on the phone."
    override val explanationUnsupported =
        "This build of Android exposes no way to hand the assistant role to another app."
    override val explanationNothingHolds =
        "Nothing holds the assistant role on this phone. Android changes this in its own " +
            "settings, not here — choose Aida there and come back."

    override fun explanationHeldByOther(holder: String) =
        "$holder answers the gesture today. Android changes this in its own settings, not here " +
            "— choose Aida there and come back."

    override val anotherApp = "Another app"

    override val test = "Test"
    override val testDescription =
        "Asks Android what is true, and opens a real session where the role and the service " +
            "allow one."
    override val testing = "Testing"
    override val runTest = "Test Aida assistant"
    override val everythingInPlace = "Everything Aida needs is in place."
    override val oneThingInTheWay = "One thing is in the way."

    override fun thingsInTheWay(count: Int) = "$count things are in the way."

    override val checkRole = "The assistant role"
    override val checkService = "Aida's voice interaction service"
    override val checkMicrophone = "The microphone"
    override val checkSession = "Opening a session"

    override val roleHeld = "Android hands the assistant gesture to Aida."

    override fun roleHeldByOther(holder: String) = "$holder answers the gesture today."

    override val roleUnheld = "Nothing holds the assistant role on this phone."
    override val roleUnsupported = "This build of Android keeps the role for itself."
    override val serviceActive = "Android has Aida's own service bound."
    override val serviceNotDeclared =
        "This build of Aida declares no voice interaction service, so Android has nothing to bind."
    override val serviceNotBound =
        "Aida declares the service, but Android has another app's bound instead."
    override val microphoneGranted = "Granted, so a session can listen."
    override val microphoneMissing = "Not granted. A session would open and then hear nothing."
    override val sessionOpened = "A session opened just now."
    override val sessionNoEntryPoint =
        "Nothing in this build answers an assist request from here, so the gesture is the only " +
            "way to see a session."
    override val sessionNotTried =
        "Not tried: the role and the service both have to be in place first."

    override val openAssistantSettings = "Open assistant settings"
    override val grantMicrophone = "Allow the microphone"

    override val oemIntro = "This phone needs a little more than the role on its own."
    override val openThatSetting = "Open that setting"

    override fun stepNumber(number: Int) = number.toString()

    override val howToReachHer = "How to reach her"
    override val hotwordAvailable =
        "This phone offers a wake word Aida is allowed to use. These reach her as well."
    override val hotwordSystemOnly =
        "Android keeps always-on wake words for assistants that ship with the system, so Aida " +
            "has none. These reach her instead."
    override val hotwordNoHardware =
        "This phone has no keyphrase hardware to offer, so there is no wake word. These reach " +
            "her instead."
    override val hotwordNotTheAssistant =
        "A wake word would need the assistant role first, and Android keeps always-on wake words " +
            "for assistants that ship with the system in any case. These reach her now."
    override val entryGesture = "The assistant gesture"
    override val entryGestureDetail =
        "Long press the side button, or swipe in from a bottom corner."
    override val entryVoiceButton = "Aida's voice button"
    override val entryVoiceButtonDetail = "The orb on Aida's own screen."
    override val entryQuickSettings = "The Quick Settings tile"
    override val entryQuickSettingsDetail = "Add Aida to the shade and tap her there."
    override val entryWidget = "The home screen widget"
    override val entryWidgetDetail = "A Aida widget, straight from the home screen."
    override val entryShortcut = "The app shortcut"
    override val entryShortcutDetail = "Long press Aida's icon on the launcher."
    override val entryShareSheet = "The share sheet"
    override val entryShareSheetDetail =
        "Send text, a link or a file to Aida from another app."

    override val assistantSettingsWouldNotOpen =
        "This phone would not open an assistant settings screen, so the role cannot be changed " +
            "from here."
    override val oemSettingWouldNotOpen =
        "This phone would not open that setting. The steps above still describe where it lives."
}

internal object BengaliAssistantWording : AssistantWording {

    override val title = "অ্যাসিস্ট্যান্ট"
    override val subtitle = "আইদাকে আমার অ্যাসিস্ট্যান্ট বানান"
    override val back = "পেছনে যান"
    override val dismiss = "বন্ধ করুন"

    override val rightNow = "এই মুহূর্তে"
    override val openAndroidAssistantSetting = "অ্যান্ড্রয়েডের অ্যাসিস্ট্যান্ট সেটিং খুলুন"
    override val settingsScreenAbsent =
        "এই ফোনে অ্যাসিস্ট্যান্ট অ্যাপ বদলানোর কোনো স্ক্রিন নেই, তাই এখান থেকে জেসচার আইদার কাছে " +
            "আনা যাবে না। নিচের উপায়গুলো দিয়ে এখনও তার কাছে পৌঁছানো যাবে।"
    override val headlineHeld = "আইদাই অ্যাসিস্ট্যান্ট"
    override val headlineNotAssistant = "আইদা অ্যাসিস্ট্যান্ট নয়"
    override val headlineCannotChange = "এই ফোনে অ্যাসিস্ট্যান্ট বদলানো যায় না"
    override val headlineAsking = "অ্যান্ড্রয়েডকে জিজ্ঞেস করছি"
    override val explanationAsking = "অ্যান্ড্রয়েড কী বলে দেখছি।"
    override val explanationHeld =
        "অ্যান্ড্রয়েড অ্যাসিস্ট জেসচার আইদার কাছে পাঠায়। সেটি কোন জেসচার, তা ফোনভেদে আলাদা।"
    override val explanationUnsupported =
        "এই অ্যান্ড্রয়েড বিল্ডে অন্য কোনো অ্যাপকে অ্যাসিস্ট্যান্ট রোল দেওয়ার উপায় নেই।"
    override val explanationNothingHolds =
        "এই ফোনে অ্যাসিস্ট্যান্ট রোল কারও কাছে নেই। এটি বদলায় অ্যান্ড্রয়েডের নিজের সেটিংসে, " +
            "এখানে নয় — সেখানে আইদাকে বেছে নিয়ে ফিরে আসুন।"

    override fun explanationHeldByOther(holder: String) =
        "এখন জেসচারে সাড়া দেয় $holder। এটি বদলাতে হয় অ্যান্ড্রয়েডের নিজের সেটিংসে, এখানে নয় — " +
            "সেখানে আইদাকে বেছে নিয়ে ফিরে আসুন।"

    override val anotherApp = "অন্য একটি অ্যাপ"

    override val test = "পরীক্ষা"
    override val testDescription =
        "অ্যান্ড্রয়েডকে জিজ্ঞেস করে সত্যিটা দেখে নেয়, আর রোল ও সার্ভিস ঠিক থাকলে " +
            "সত্যিকারের একটি সেশন খোলে।"
    override val testing = "পরীক্ষা চলছে"
    override val runTest = "আইদাকে অ্যাসিস্ট্যান্ট হিসেবে পরীক্ষা করুন"
    override val everythingInPlace = "আইদার যা দরকার, সব ঠিক আছে।"
    override val oneThingInTheWay = "একটি জিনিস আটকাচ্ছে।"

    override fun thingsInTheWay(count: Int) =
        "${bengaliNumerals(count.toString())}টি জিনিস আটকাচ্ছে।"

    override val checkRole = "অ্যাসিস্ট্যান্ট রোল"
    override val checkService = "আইদার ভয়েস ইন্টারঅ্যাকশন সার্ভিস"
    override val checkMicrophone = "মাইক্রোফোন"
    override val checkSession = "সেশন খোলা"

    override val roleHeld = "অ্যান্ড্রয়েড অ্যাসিস্ট্যান্ট জেসচার আইদাকেই দেয়।"

    override fun roleHeldByOther(holder: String) = "এখন জেসচারে সাড়া দেয় $holder।"

    override val roleUnheld = "এই ফোনে অ্যাসিস্ট্যান্ট রোল কারও কাছে নেই।"
    override val roleUnsupported = "এই অ্যান্ড্রয়েড বিল্ড রোলটি নিজের কাছেই রাখে।"
    override val serviceActive = "অ্যান্ড্রয়েড আইদার নিজের সার্ভিসই যুক্ত করেছে।"
    override val serviceNotDeclared =
        "আইদার এই বিল্ডে কোনো ভয়েস ইন্টারঅ্যাকশন সার্ভিস নেই, তাই অ্যান্ড্রয়েডের " +
            "যুক্ত করার কিছু নেই।"
    override val serviceNotBound =
        "আইদার সার্ভিস আছে, কিন্তু অ্যান্ড্রয়েড অন্য অ্যাপের সার্ভিস যুক্ত করে রেখেছে।"
    override val microphoneGranted = "অনুমতি আছে, তাই সেশন শুনতে পারবে।"
    override val microphoneMissing = "অনুমতি নেই। সেশন খুলবে, কিন্তু কিছুই শুনবে না।"
    override val sessionOpened = "এইমাত্র একটি সেশন খুলেছে।"
    override val sessionNoEntryPoint =
        "এই বিল্ডে এখান থেকে অ্যাসিস্ট অনুরোধের উত্তর কেউ দেয় না, তাই সেশন দেখার একমাত্র উপায় " +
            "জেসচার।"
    override val sessionNotTried = "চেষ্টা করা হয়নি — আগে রোল ও সার্ভিস দুটোই ঠিক থাকতে হবে।"

    override val openAssistantSettings = "অ্যাসিস্ট্যান্ট সেটিংস খুলুন"
    override val grantMicrophone = "মাইক্রোফোনের অনুমতি দিন"

    override val oemIntro = "এই ফোনে শুধু রোল যথেষ্ট নয়, আরও একটু লাগে।"
    override val openThatSetting = "সেই সেটিং খুলুন"

    override fun stepNumber(number: Int) = bengaliNumerals(number.toString())

    override val howToReachHer = "যেভাবে তাকে ডাকবেন"
    override val hotwordAvailable =
        "এই ফোনে এমন একটি ওয়েক ওয়ার্ড আছে যা আইদা ব্যবহার করার অনুমতি পায়। এগুলো দিয়েও তাকে পাবেন।"
    override val hotwordSystemOnly =
        "সবসময় চালু থাকা ওয়েক ওয়ার্ড অ্যান্ড্রয়েড শুধু সিস্টেমের সাথে আসা অ্যাসিস্ট্যান্টের " +
            "জন্য রাখে, তাই আইদার কোনোটি নেই। এগুলো দিয়ে তাকে পাবেন।"
    override val hotwordNoHardware =
        "এই ফোনে ওয়েক ওয়ার্ডের হার্ডওয়্যার নেই, তাই কোনো ওয়েক ওয়ার্ডও নেই। " +
            "এগুলো দিয়ে তাকে পাবেন।"
    override val hotwordNotTheAssistant =
        "ওয়েক ওয়ার্ডের জন্য আগে অ্যাসিস্ট্যান্ট রোল লাগবে, আর সবসময় চালু থাকা ওয়েক ওয়ার্ড " +
            "অ্যান্ড্রয়েড এমনিতেও সিস্টেম অ্যাসিস্ট্যান্টের জন্যই রাখে। " +
            "এখন এগুলো দিয়ে তাকে পাবেন।"
    override val entryGesture = "অ্যাসিস্ট্যান্ট জেসচার"
    override val entryGestureDetail =
        "সাইড বাটন চেপে ধরুন, বা নিচের কোণ থেকে ভেতরের দিকে সোয়াইপ করুন।"
    override val entryVoiceButton = "আইদার ভয়েস বাটন"
    override val entryVoiceButtonDetail = "আইদার নিজের স্ক্রিনের গোল বাটনটি।"
    override val entryQuickSettings = "Quick Settings টাইল"
    override val entryQuickSettingsDetail = "Quick Settings প্যানেলে আইদাকে যোগ করে সেখান থেকেই ট্যাপ করুন।"
    override val entryWidget = "হোম স্ক্রিনের উইজেট"
    override val entryWidgetDetail = "হোম স্ক্রিন থেকেই সরাসরি আইদার উইজেট।"
    override val entryShortcut = "অ্যাপ শর্টকাট"
    override val entryShortcutDetail = "লঞ্চারে আইদার আইকন চেপে ধরুন।"
    override val entryShareSheet = "শেয়ার মেনু"
    override val entryShareSheetDetail = "অন্য অ্যাপ থেকে টেক্সট, লিংক বা ফাইল আইদাকে পাঠান।"

    override val assistantSettingsWouldNotOpen =
        "এই ফোন অ্যাসিস্ট্যান্ট সেটিংস স্ক্রিন খুলল না, তাই এখান থেকে রোল বদলানো যাবে না।"
    override val oemSettingWouldNotOpen =
        "এই ফোন সেই সেটিং খুলল না। সেটি কোথায় আছে, উপরের ধাপগুলোতে তা বলা আছে।"
}

internal fun assistantWording(language: SpokenLanguage): AssistantWording = when (language) {
    SpokenLanguage.ENGLISH -> EnglishAssistantWording
    SpokenLanguage.BENGALI -> BengaliAssistantWording
}
