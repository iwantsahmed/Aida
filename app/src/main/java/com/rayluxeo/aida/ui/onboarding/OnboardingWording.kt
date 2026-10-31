package com.rayluxeo.aida.ui.onboarding

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.ui.response.bengaliNumerals

/**
 * Every word first-run says to Abu, in the language he chose.
 *
 * Onboarding was written in English only, which is the worst screen in Mira to leave that way: it
 * is the first thing he ever sees, and the second of its twelve panes is the one where he picks
 * the language. Choosing বাংলা there and then reading eleven English panes is Mira not listening
 * on the one occasion she has asked.
 *
 * Same phrasebook shape as `ui/settings/AssistantWording` and `ui/settings/PlacesWording`: one
 * interface, one object per language, one selector — so either language can be reworded without
 * reading the other. The pane order itself lives in [OnboardingStep] and is never repeated here.
 *
 * Bengali is the register Abu speaks. Android's own words — microphone, notification, calendar,
 * assistant, gesture, widget — are kept and written in Bengali script rather than replaced with
 * coinages he would have to decode. Numbers go through [bengaliNumerals], because a Latin digit in
 * a Bengali line reads as a different voice.
 */
internal interface OnboardingWording {

    /** The frame: the back affordance, and how far through he is. */
    val back: String
    fun stepPosition(step: Int, total: Int): String

    // The panes, in the order they are walked.
    val welcomeTitle: String
    val welcomeBody: String
    val welcomeDetail: String

    val nameTitle: String
    val nameBody: String
    val nameDetail: String
    val namePlaceholder: String

    val languageTitle: String
    val languageBody: String
    val languageFootnote: String

    val voiceTitle: String
    val voiceBody: String
    val voiceDetail: String

    val microphoneTitle: String
    val microphoneBody: String
    val microphoneDetail: String
    val microphoneGranted: String

    val notificationsTitle: String
    val notificationsBody: String
    val notificationsDetail: String
    val notificationsGranted: String

    val calendarTitle: String
    val calendarBody: String
    val calendarDetail: String
    val calendarGranted: String

    val contactsTitle: String
    val contactsBody: String
    val contactsDetail: String
    val contactsGranted: String

    val locationTitle: String
    val locationBody: String
    val locationDetail: String

    val biometricsTitle: String
    val biometricsBody: String
    val biometricsDetail: String

    val assistantTitle: String
    val assistantBody: String
    val assistantDetail: String
    val assistantSettingsMissing: String

    val privacyTitle: String
    val privacyBody: String
    val privacyPoints: List<String>
    val privacyFootnote: String

    val finishTitle: String
    val finishBody: String
    val finishDetail: String

    // What a permission is reported as, whatever asked for it.
    val notGrantedYet: String
    val notGranted: String
    val onlySettingsCanGrant: String

    // What this phone can do about the vault.
    val biometricsChecking: String
    val biometricsReady: String
    val biometricsNoneEnrolled: String
    val biometricsUnavailable: String

    // The anchored actions.
    val begin: String
    val continueOn: String
    val skipForNow: String
    val askAgain: String
    val chooseAVoiceNow: String
    val allowTheMicrophone: String
    val allowNotifications: String
    val allowTheCalendar: String
    val allowContacts: String
    val openAppSettings: String
    val openNotificationSettings: String
    val setUpAScreenLock: String
    val openAssistantSettings: String
    val startUsingMira: String
}

internal object EnglishOnboardingWording : OnboardingWording {

    override val back = "Back"

    override fun stepPosition(step: Int, total: Int) = "Step $step of $total"

    override val welcomeTitle = "Welcome to Aida"
    override val welcomeBody =
        "Aida is a private assistant that runs on this phone, for Abu. She works with the radio " +
            "off, and what she is told stays on the device."
    override val welcomeDetail =
        "Setting her up takes a few short steps. Everything optional can be skipped."

    override val nameTitle = "What should Aida call you?"
    override val nameBody =
        "Aida personalizes your daily briefs, prayers, and all notifications with your name."
    override val nameDetail =
        "You can change this at any time in Settings."
    override val namePlaceholder = "Enter your name"

    override val languageTitle = "Language"
    override val languageBody =
        "Aida understands English, Bengali, and the two mixed together inside one sentence."
    override val languageFootnote =
        "Auto answers in whichever language you used. বাংলা and English pin every reply to that one."

    override val voiceTitle = "Aida's voice"
    override val voiceBody =
        "Aida speaks with the voices this phone already has. Which one she uses is Abu's choice, " +
            "made by listening to them rather than by reading their names."
    override val voiceDetail =
        "Until one is chosen she uses the best voice installed for each language. Settings holds " +
            "the picker whenever you want it."

    override val microphoneTitle = "Microphone"
    override val microphoneBody =
        "Speech is recognised on the device, so what you say is not sent away to be transcribed."
    override val microphoneDetail = "Without the microphone Aida still does everything by typing."
    override val microphoneGranted = "Microphone granted"

    override val notificationsTitle = "Notifications"
    override val notificationsBody =
        "A reminder reaches you as a notification. That is the whole of what this is for."
    override val notificationsDetail =
        "Without it reminders still fire and still appear in Aida. They simply arrive silently."
    override val notificationsGranted = "Notifications are on"

    override val calendarTitle = "Calendar"
    override val calendarBody =
        "Aida reads this phone's own calendars to say what is scheduled, and writes an event " +
            "straight into them when you dictate one."
    override val calendarDetail =
        "The calendar stays the source of truth — she keeps no copy of it, and nothing she reads " +
            "there leaves the device. Without this she still takes the booking as a task."
    override val calendarGranted = "Calendar granted"

    override val contactsTitle = "Contacts"
    override val contactsBody =
        "Contacts let Aida turn a name you say into a person — so \"Ahmed ke call koro\" finds " +
            "the right number and hands you the dialler."
    override val contactsDetail =
        "Read only. She never writes a contact, never copies the address book into her own " +
            "database, and looks only when you have asked her about a name."
    override val contactsGranted = "Contacts granted"

    override val locationTitle = "Location"
    override val locationBody =
        "Location is entirely optional. One thing uses it: a reminder that waits at a place, like " +
            "buying ink when you reach the office."
    override val locationDetail =
        "Aida is not asking now. You save a place from Settings when you want one, and she asks " +
            "there. There is no map and no address lookup — you stand somewhere and name it, and " +
            "the spot never leaves this phone."

    override val biometricsTitle = "Biometric protection"
    override val biometricsBody =
        "The vault stays sealed until you authenticate with a fingerprint or the device PIN."
    override val biometricsDetail =
        "Its key is held in this phone's own keystore: the raw key never enters Aida's memory, " +
            "never reaches a backup, and never leaves the device."

    override val assistantTitle = "Assistant gesture"
    override val assistantBody =
        "Android gives one app the assistant gesture — the long press on the side button, or the " +
            "swipe in from a bottom corner. Aida can be that app."
    override val assistantDetail =
        "Android changes it in its own settings rather than from in here. Settings › Assistant " +
            "holds the whole of it: what answers the gesture today, a test that opens a real " +
            "session, and the extra steps this phone needs."
    override val assistantSettingsMissing =
        "This phone has no assistant settings screen to open"

    override val privacyTitle = "Privacy"
    override val privacyBody = "Three things are worth being plain about."
    override val privacyPoints = listOf(
        "Most of your data stays on this device — tasks, reminders, notes, the vault and every " +
            "conversation.",
        "Cloud AI is optional. It is off, it has no account, and it needs a key you would have to " +
            "add yourself.",
        "Aida works without Gemini. Every command she can carry out today is understood on the " +
            "phone.",
    )
    override val privacyFootnote =
        "If you ever turn Gemini on, one thing leaves the phone: the text of the single request " +
            "she could not interpret herself. Never your tasks, your reminders or your vault."

    override val finishTitle = "Ready"
    override val finishBody =
        "That is everything. Anything skipped is waiting in Settings, and Aida will ask again the " +
            "first time she genuinely needs it."
    override val finishDetail =
        "Say something, or type it. Everything she can do today works without a network."

    override val notGrantedYet = "Not granted yet"
    override val notGranted = "Not granted"
    override val onlySettingsCanGrant = "Only the system settings screen can grant this now"

    override val biometricsChecking = "Checking this phone"
    override val biometricsReady = "Ready on this phone"
    override val biometricsNoneEnrolled = "No fingerprint or screen lock is set up yet"
    override val biometricsUnavailable =
        "This phone cannot verify you, so the vault stays shut"

    override val begin = "Begin"
    override val continueOn = "Continue"
    override val skipForNow = "Skip for now"
    override val askAgain = "Ask again"
    override val chooseAVoiceNow = "Choose a voice now"
    override val allowTheMicrophone = "Allow the microphone"
    override val allowNotifications = "Allow notifications"
    override val allowTheCalendar = "Allow the calendar"
    override val allowContacts = "Allow contacts"
    override val openAppSettings = "Open app settings"
    override val openNotificationSettings = "Open notification settings"
    override val setUpAScreenLock = "Set up a screen lock"
    override val openAssistantSettings = "Open assistant settings"
    override val startUsingMira = "Start using Aida"
}

internal object BengaliOnboardingWording : OnboardingWording {

    override val back = "পেছনে যান"

    override fun stepPosition(step: Int, total: Int) = bengaliNumerals("ধাপ $step / $total")

    override val welcomeTitle = "আইদাতে স্বাগতম"
    override val welcomeBody =
        "আইদা আবুর নিজের একটি ব্যক্তিগত অ্যাসিস্ট্যান্ট, যে এই ফোনেই চলে। নেট বন্ধ থাকলেও সে " +
            "কাজ করে, আর তাকে যা বলা হয় তা এই ফোনেই থাকে।"
    override val welcomeDetail =
        "সেটআপে কয়েকটি ছোট ধাপ। যেগুলো বাধ্যতামূলক নয়, সবই এড়িয়ে যাওয়া যায়।"

    override val nameTitle = "আপনার নাম কী?"
    override val nameBody =
        "আইদা আপনার দৈনিক ব্রিফিং, নামাজের ওয়াক্ত এবং সকল বিজ্ঞপ্তিতে আপনাকে আপনার নামে সম্বোধন করবে।"
    override val nameDetail =
        "সেটিংস থেকে যেকোনো সময় এটি পরিবর্তন করা যাবে।"
    override val namePlaceholder = "আপনার নাম লিখুন"

    override val languageTitle = "ভাষা"
    override val languageBody =
        "আইদা English বোঝে, বাংলা বোঝে, আর এক বাক্যের মধ্যে দুটো মিশিয়ে বললেও বোঝে।"
    override val languageFootnote =
        "Auto বেছে নিলে আপনি যে ভাষায় বলেছেন, সে সেই ভাষাতেই উত্তর দেবে। বাংলা বা English " +
            "বেছে নিলে প্রতিটি উত্তরই সেই ভাষায় হবে।"

    override val voiceTitle = "আইদার কণ্ঠ"
    override val voiceBody =
        "এই ফোনে যে ভয়েসগুলো আছে, আইদা সেগুলো দিয়েই কথা বলে। কোনটি ব্যবহার করবে তা আবুর পছন্দ — " +
            "নাম পড়ে নয়, শুনে বেছে নেওয়া।"
    override val voiceDetail =
        "বেছে না নেওয়া পর্যন্ত প্রতিটি ভাষার জন্য ইনস্টল করা সবচেয়ে ভালো ভয়েসটিই সে ব্যবহার " +
            "করে। যখনই চান, সেটিংসে গিয়ে বেছে নিতে পারবেন।"

    override val microphoneTitle = "মাইক্রোফোন"
    override val microphoneBody =
        "কথা এই ফোনেই চেনা হয়, তাই আপনি যা বলেন তা লেখায় বদলানোর জন্য কোথাও পাঠানো হয় না।"
    override val microphoneDetail = "মাইক্রোফোন ছাড়াও টাইপ করে আইদার সব কাজ করানো যায়।"
    override val microphoneGranted = "মাইক্রোফোনের অনুমতি আছে"

    override val notificationsTitle = "নোটিফিকেশন"
    override val notificationsBody =
        "রিমাইন্ডার আপনার কাছে নোটিফিকেশন হয়েই পৌঁছায়। এটির কাজ কেবল এটুকুই।"
    override val notificationsDetail =
        "এটি ছাড়াও রিমাইন্ডার ঠিক সময়েই বাজে আর আইদার ভেতরে দেখা যায়। শুধু চুপচাপ আসে।"
    override val notificationsGranted = "নোটিফিকেশন চালু আছে"

    override val calendarTitle = "ক্যালেন্ডার"
    override val calendarBody =
        "কী কী নির্ধারিত আছে তা বলার জন্য আইদা এই ফোনের নিজের ক্যালেন্ডারগুলোই পড়ে, আর আপনি " +
            "বলে দিলে সরাসরি সেখানেই ইভেন্ট লেখে।"
    override val calendarDetail =
        "সত্যিটা ক্যালেন্ডারেরই থাকে — সে এর কোনো কপি রাখে না, আর সেখান থেকে পড়া কিছুই ফোনের " +
            "বাইরে যায় না। অনুমতি না দিলে সে বিষয়টি একটি কাজ হিসেবেই টুকে রাখবে।"
    override val calendarGranted = "ক্যালেন্ডারের অনুমতি আছে"

    override val contactsTitle = "কন্টাক্ট"
    override val contactsBody =
        "কন্টাক্ট থাকলে আপনার বলা নামটিকে আইদা একজন মানুষ হিসেবে চিনতে পারে — তাই \"আহমেদকে " +
            "কল করো\" বললে সে ঠিক নম্বরটি বের করে ডায়ালার খুলে দেয়।"
    override val contactsDetail =
        "শুধু পড়ার জন্য। সে কখনও কোনো কন্টাক্ট লেখে না, ঠিকানার খাতা নিজের ডেটাবেসে কপি করে না, " +
            "আর কেবল আপনি কোনো নাম বললেই দেখে।"
    override val contactsGranted = "কন্টাক্টের অনুমতি আছে"

    override val locationTitle = "অবস্থান"
    override val locationBody =
        "অবস্থান পুরোপুরি ঐচ্ছিক। একটিই কাজে লাগে: কোনো জায়গায় অপেক্ষা করা রিমাইন্ডার — যেমন " +
            "অফিসে পৌঁছালে কালি কেনার কথা মনে করিয়ে দেওয়া।"
    override val locationDetail =
        "আইদা এখন অনুমতি চাইছে না। যখন কোনো জায়গা সেভ করতে চাইবেন, সেটিংস থেকে করবেন আর সে " +
            "সেখানেই জিজ্ঞেস করবে। কোনো ম্যাপ নেই, ঠিকানা খোঁজাও নেই — আপনি কোথাও দাঁড়িয়ে তার " +
            "নাম দেন, আর জায়গাটি কখনও এই ফোনের বাইরে যায় না।"

    override val biometricsTitle = "বায়োমেট্রিক সুরক্ষা"
    override val biometricsBody =
        "আঙুলের ছাপ বা ফোনের পিন দিয়ে পরিচয় না দেওয়া পর্যন্ত ভল্ট বন্ধই থাকে।"
    override val biometricsDetail =
        "এর চাবিটি এই ফোনের নিজের কিস্টোরে থাকে: আসল চাবি কখনও আইদার মেমোরিতে আসে না, কোনো " +
            "ব্যাকআপে পৌঁছায় না, আর ফোনের বাইরে যায় না।"

    override val assistantTitle = "অ্যাসিস্ট্যান্ট জেসচার"
    override val assistantBody =
        "অ্যান্ড্রয়েড একটিমাত্র অ্যাপকে অ্যাসিস্ট্যান্ট জেসচার দেয় — সাইড বাটন চেপে ধরা, বা " +
            "নিচের কোণ থেকে ভেতরের দিকে টান। আইদা সেই অ্যাপটি হতে পারে।"
    override val assistantDetail =
        "অ্যান্ড্রয়েড এটি এখান থেকে নয়, নিজের সেটিংসেই বদলায়। সেটিংস › অ্যাসিস্ট্যান্টে পুরোটাই " +
            "আছে: আজ জেসচারে কে সাড়া দিচ্ছে, সত্যিকারের একটি সেশন খোলার টেস্ট, আর এই ফোনের " +
            "বাড়তি ধাপগুলো।"
    override val assistantSettingsMissing =
        "এই ফোনে খোলার মতো কোনো অ্যাসিস্ট্যান্ট সেটিংস স্ক্রিন নেই"

    override val privacyTitle = "গোপনীয়তা"
    override val privacyBody = "তিনটি কথা খোলাখুলি বলে রাখা ভালো।"
    override val privacyPoints = listOf(
        "আপনার বেশির ভাগ তথ্য এই ফোনেই থাকে — কাজ, রিমাইন্ডার, নোট, ভল্ট আর প্রতিটি কথোপকথন।",
        "ক্লাউড AI ঐচ্ছিক। এটি বন্ধ, এর কোনো অ্যাকাউন্ট নেই, আর এর জন্য এমন একটি কি লাগে যা " +
            "আপনাকে নিজে যোগ করতে হবে।",
        "Gemini ছাড়াও আইদা কাজ করে। আজ সে যা যা করতে পারে, তার প্রতিটি নির্দেশ ফোনেই বোঝা হয়।",
    )
    override val privacyFootnote =
        "কখনও Gemini চালু করলে একটিই জিনিস ফোনের বাইরে যায়: যে একটি অনুরোধ সে নিজে বুঝতে " +
            "পারেনি, তার লেখাটুকু। আপনার কাজ, রিমাইন্ডার বা ভল্ট কখনও নয়।"

    override val finishTitle = "প্রস্তুত"
    override val finishBody =
        "এটুকুই। যা কিছু এড়িয়ে গেছেন সব সেটিংসে অপেক্ষা করছে, আর সত্যিই দরকার হলে আইদা " +
            "প্রথমবারেই আবার জিজ্ঞেস করবে।"
    override val finishDetail =
        "কিছু বলুন, বা লিখুন। আজ সে যা যা পারে, তার সবই নেট ছাড়া চলে।"

    override val notGrantedYet = "এখনও অনুমতি দেওয়া হয়নি"
    override val notGranted = "অনুমতি দেওয়া হয়নি"
    override val onlySettingsCanGrant = "এখন কেবল সিস্টেম সেটিংস স্ক্রিন থেকেই এটি দেওয়া যাবে"

    override val biometricsChecking = "ফোনটি দেখা হচ্ছে"
    override val biometricsReady = "এই ফোনে প্রস্তুত"
    override val biometricsNoneEnrolled = "এখনও কোনো আঙুলের ছাপ বা স্ক্রিন লক সেট করা নেই"
    override val biometricsUnavailable = "এই ফোন আপনার পরিচয় যাচাই করতে পারে না, তাই ভল্ট বন্ধই থাকবে"

    override val begin = "শুরু করুন"
    override val continueOn = "এগিয়ে যান"
    override val skipForNow = "এখন থাক"
    override val askAgain = "আবার জিজ্ঞেস করুন"
    override val chooseAVoiceNow = "এখনই একটি ভয়েস বেছে নিন"
    override val allowTheMicrophone = "মাইক্রোফোনের অনুমতি দিন"
    override val allowNotifications = "নোটিফিকেশনের অনুমতি দিন"
    override val allowTheCalendar = "ক্যালেন্ডারের অনুমতি দিন"
    override val allowContacts = "কন্টাক্টের অনুমতি দিন"
    override val openAppSettings = "অ্যাপ সেটিংস খুলুন"
    override val openNotificationSettings = "নোটিফিকেশন সেটিংস খুলুন"
    override val setUpAScreenLock = "একটি স্ক্রিন লক সেট করুন"
    override val openAssistantSettings = "অ্যাসিস্ট্যান্ট সেটিংস খুলুন"
    override val startUsingMira = "আইদা ব্যবহার শুরু করুন"
}

internal fun onboardingWording(language: SpokenLanguage): OnboardingWording = when (language) {
    SpokenLanguage.ENGLISH -> EnglishOnboardingWording
    SpokenLanguage.BENGALI -> BengaliOnboardingWording
}
