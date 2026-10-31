package com.rayluxeo.aida.ui.settings

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.ConversationStyle
import com.rayluxeo.aida.domain.repository.CloudQuotaStatus
import com.rayluxeo.aida.domain.repository.VoicePreferences
import com.rayluxeo.aida.ui.response.bengaliNumerals
import java.time.DayOfWeek

/**
 * Every word the Settings screen says to Abu, in the language he chose.
 *
 * Settings was the last screen to hold its own English, which is the worst place for it: the
 * control that picks Abu's language sat at the top of a screen that then went on answering him in
 * the other one. Same phrasebook shape as [AssistantWording], [PlacesWording] and
 * [BriefSettingsWording]: one interface, one object per language, one selector.
 *
 * The places and daily-brief sections are deliberately absent — they already have phrasebooks of
 * their own, and a second copy here would be the two-lists problem AGENTS.md §7.12 forbids.
 *
 * Bengali is the register Abu speaks. Android's and Google's own names — Gemini, API key, haptic —
 * are kept and written in Bengali script rather than replaced with coinages he would have to
 * decode. Numbers go through [bengaliNumerals], because a Latin digit in a Bengali line reads as a
 * different voice.
 */
internal interface SettingsWording {

    // The frame, and the one dismissible notice.
    val title: String
    val back: String
    val dismiss: String

    // Language.
    val language: String
    val languageFootnote: String

    // Voice.
    val voice: String
    val spokenResponses: String
    val spokenResponsesDetail: String
    val activationSound: String
    val activationSoundDetail: String
    val hapticFeedback: String
    val hapticFeedbackDetail: String
    val howMiraSounds: String

    /** What has actually been chosen. An engine package name would be noise, so it is left out. */
    fun voiceSummary(preferences: VoicePreferences): String

    // Assistant.
    val assistant: String

    val privacyAndData: String
    val privacyDashboard: String
    val privacyDashboardDetail: String
    val backupAndRestore: String
    val backupAndRestoreDetail: String
    val setMiraAsAssistant: String
    val setMiraAsAssistantDetail: String

    // The exact-alarm warning, shown only while Android is withholding the permission.
    val reminders: String
    val remindersMayBeLate: String
    val remindersMayBeLateDetail: String
    val allowExactAlarms: String

    // Gemini: the one optional external service, off until Abu adds a key.
    val gemini: String
    val geminiOn: String
    val geminiOff: String
    val geminiDetail: String
    val addKey: String
    val replaceKey: String
    val removeKey: String
    val geminiKeyTitle: String
    val geminiKeyDetail: String
    val keyLabel: String
    val saveKey: String
    val savingKey: String

    // Conversation, and the one destructive thing on this screen.
    val conversation: String
    val clearConversation: String
    val clearConversationDetail: String
    val clearConversationTitle: String
    val clearConversationMessage: String
    val clearIt: String
    val keepIt: String

    // About.
    val about: String
    fun versionLine(versionName: String): String
    fun buildLine(versionCode: Int): String
    val checkForUpdates: String
    val checkForUpdatesDetail: String
    val checkingForUpdates: String
    val appUpToDate: String
    val updateAvailable: String
    val downloadAndInstall: String
    val downloadingUpdate: String
    val installNow: String

    // --- The eight section headers ---
    val sectionProfile: String
    val userNameTitle: String
    val userNameNotSet: String
    val editNameTitle: String
    val namePlaceholder: String
    val save: String
    val cancel: String
    val sectionMira: String
    val sectionGeneral: String
    val sectionNotifications: String
    val sectionAi: String
    val sectionPrivacy: String
    val sectionData: String
    val sectionIntegrations: String

    // MIRA
    val assistantRole: String
    val assistantRoleDetail: String
    val salah: String
    val salahDetail: String
    val conversationStyle: String
    val conversationStyleDetail: String
    fun conversationStyleName(style: ConversationStyle): String

    // GENERAL
    val theme: String
    val themeValue: String
    val themeDetail: String
    val dateFormat: String
    val dateFormatDetail: String
    val currency: String
    val currencyDetail: String
    val firstDayOfWeek: String
    val firstDayOfWeekDetail: String
    fun dayName(day: DayOfWeek): String

    // NOTIFICATIONS
    val notifyTasks: String
    val notifyTasksDetail: String
    val notifyReminders: String
    val notifyRemindersDetail: String
    val notifyInvoices: String
    val notifyInvoicesDetail: String
    val notifySubscriptions: String
    val notifySubscriptionsDetail: String
    val quietHours: String
    val quietHoursDetail: String
    val quietHoursFrom: String
    val quietHoursTo: String

    // AI
    val disableCloudAi: String
    val disableCloudAiDetail: String
    val cloudAvailability: String
    val availabilityReady: String
    val availabilityNoKey: String
    val availabilityOff: String
    val freeTierState: String
    fun quotaLabel(status: CloudQuotaStatus): String

    // PRIVACY & SECURITY
    val biometricLock: String
    val biometricLockDetail: String
    val autoLock: String
    val autoLockDetail: String
    fun timeoutLabel(seconds: Int): String
    val lockWhenBackgrounded: String
    val lockWhenBackgroundedDetail: String
    val permissions: String
    val permissionsDetail: String
    val protectHeader: String
    val protectDetail: String
    val protectMoney: String
    val protectMemory: String
    val protectInvoices: String
    val protectBackups: String
    val activityLog: String
    val activityLogDetail: String

    // CROSS-APP AUTOMATIONS (the opt-in tier)
    val sectionCrossApp: String
    val crossAppAutomations: String
    val crossAppAutomationsDetail: String

    // DATA
    val dataBackup: String
    val dataBackupDetail: String
    val eraseData: String
    val eraseDataDetail: String
    val eraseDataTitle: String
    val eraseDataMessage: String
    val eraseDataConfirm: String
    val eraseDataKeep: String
    val eraseDataFailed: String
    val erasing: String

    // INTEGRATIONS
    val integrationCalendar: String
    val integrationCalendarDetail: String
    val integrationContacts: String
    val integrationContactsDetail: String
    val defaultAssistant: String
    val defaultAssistantDetail: String
    val connected: String
    val notConnected: String

    // ABOUT
    val privacyInformation: String
    val privacyInformationDetail: String
    val licenses: String
    val licensesDetail: String

    // Picker sheet titles
    val chooseDateFormat: String
    val chooseCurrency: String
    val chooseFirstDay: String
    val chooseAutoLock: String
    val timeSet: String
    val timeCancel: String

    // When something did not work. Never a raw exception.
    val conversationNotCleared: String
    val geminiKeyRefused: String
}

internal object EnglishSettingsWording : SettingsWording {

    override val title = "Settings"
    override val back = "Back"
    override val dismiss = "Dismiss"

    override val language = "Language"
    override val languageFootnote =
        "Auto answers in whichever language you used. Bangla and English pin every reply to that one."

    override val voice = "Voice"
    override val spokenResponses = "Spoken responses"
    override val spokenResponsesDetail = "Aida reads her answers out loud."
    override val activationSound = "Activation sound"
    override val activationSoundDetail =
        "A short tone when she starts listening and when a command lands."
    override val hapticFeedback = "Haptic feedback"
    override val hapticFeedbackDetail = "A tick under the thumb on taps and confirmations."
    override val howMiraSounds = "How Aida sounds"

    override fun voiceSummary(preferences: VoicePreferences): String {
        val chosen = chosenVoices(preferences)
        return when {
            chosen.isEmpty() -> "No voice chosen yet — pick how Aida sounds"
            preferences.setupComplete -> "Voices: $chosen"
            else -> "Voices: $chosen — setup unfinished"
        }
    }

    override val assistant = "Assistant"
    override val privacyAndData = "Privacy & data"
    override val privacyDashboard = "Privacy"
    override val privacyDashboardDetail = "What's stored, what's encrypted, what leaves the device"
    override val backupAndRestore = "Backup & restore"
    override val backupAndRestoreDetail = "Export an encrypted copy, or restore one"
    override val setMiraAsAssistant = "Set Aida as my assistant"
    override val setMiraAsAssistantDetail =
        "The side-button gesture, a test, and this phone's own extra steps."

    override val reminders = "Reminders"
    override val remindersMayBeLate = "Reminders may be late"
    override val remindersMayBeLateDetail =
        "Android is not letting Aida set alarms to the exact minute, so a reminder can arrive " +
            "late. Granting the permission fixes it."
    override val allowExactAlarms = "Allow exact alarms"

    override val gemini = "Gemini"
    override val geminiOn = "Gemini is on"
    override val geminiOff = "Gemini is off"
    override val geminiDetail =
        "Aida works fully without this. With a key saved, the text of one request is sent to " +
            "Google only when the on-device engine cannot make sense of it — never your tasks, " +
            "your reminders or anything in the vault."
    override val addKey = "Add a key"
    override val replaceKey = "Replace the key"
    override val removeKey = "Remove it"
    override val geminiKeyTitle = "Gemini API key"
    override val geminiKeyDetail =
        "Paste a free-tier key from Google AI Studio. Aida seals it with a key held in this " +
            "phone's secure hardware and never shows it again."
    override val keyLabel = "Key"
    override val saveKey = "Save the key"
    override val savingKey = "Saving"

    override val conversation = "Conversation"
    override val clearConversation = "Clear the conversation"
    override val clearConversationDetail =
        "Removes every turn from this phone. There is no copy anywhere else."
    override val clearConversationTitle = "Clear the conversation?"
    override val clearConversationMessage =
        "Every turn Aida has recorded is deleted from this phone. Tasks, reminders and the vault " +
            "are untouched."
    override val clearIt = "Clear it"
    override val keepIt = "Keep it"

    override val about = "About"

    override fun versionLine(versionName: String) = "Aida $versionName"

    override fun buildLine(versionCode: Int) =
        "Build $versionCode · a private assistant for Abu, running on this phone alone."

    override val checkForUpdates = "Check for Updates"
    override val checkForUpdatesDetail = "OTA updates via GitHub Releases"
    override val checkingForUpdates = "Checking for updates on GitHub..."
    override val appUpToDate = "Aida is up to date"
    override val updateAvailable = "New update available"
    override val downloadAndInstall = "Download & Install"
    override val downloadingUpdate = "Downloading update..."
    override val installNow = "Install Now"

    override val sectionProfile = "Profile"
    override val userNameTitle = "Your Name"
    override val userNameNotSet = "Not set"
    override val editNameTitle = "Edit Your Name"
    override val namePlaceholder = "Enter your name"
    override val save = "Save"
    override val cancel = "Cancel"

    override val sectionMira = "Aida"
    override val sectionGeneral = "General"
    override val sectionNotifications = "Notifications"
    override val sectionAi = "AI"
    override val sectionPrivacy = "Privacy & security"
    override val sectionData = "Data"
    override val sectionIntegrations = "Integrations"

    override val assistantRole = "Assistant role"
    override val assistantRoleDetail = "The side-button gesture, a test, and this phone's own extra steps."
    override val salah = "Salah"
    override val salahDetail = "Location, calculation method, notifications and Tahajjud."
    override val conversationStyle = "Conversation style"
    override val conversationStyleDetail = "How much Aida says around an answer. The numbers never change."
    override fun conversationStyleName(style: ConversationStyle) = when (style) {
        ConversationStyle.CONCISE -> "Concise"
        ConversationStyle.BALANCED -> "Balanced"
        ConversationStyle.DETAILED -> "Detailed"
    }

    override val theme = "Theme"
    override val themeValue = "AMOLED dark"
    override val themeDetail = "Aida is dark by design — true black for this phone's screen, one accent, nothing to switch."
    override val dateFormat = "Date format"
    override val dateFormatDetail = "How a date is written across the app."
    override val currency = "Currency"
    override val currencyDetail = "The default for a new invoice or quote when none is named."
    override val firstDayOfWeek = "First day of week"
    override val firstDayOfWeekDetail = "Where a week starts in reports and summaries."
    override fun dayName(day: DayOfWeek) = when (day) {
        DayOfWeek.MONDAY -> "Monday"
        DayOfWeek.TUESDAY -> "Tuesday"
        DayOfWeek.WEDNESDAY -> "Wednesday"
        DayOfWeek.THURSDAY -> "Thursday"
        DayOfWeek.FRIDAY -> "Friday"
        DayOfWeek.SATURDAY -> "Saturday"
        DayOfWeek.SUNDAY -> "Sunday"
    }

    override val notifyTasks = "Tasks"
    override val notifyTasksDetail = "Reminders for tasks that are due."
    override val notifyReminders = "Reminders"
    override val notifyRemindersDetail = "The reminders you set yourself, and place and birthday nudges."
    override val notifyInvoices = "Invoices"
    override val notifyInvoicesDetail = "When an invoice is due or overdue."
    override val notifySubscriptions = "Subscriptions"
    override val notifySubscriptionsDetail = "Before a subscription renews."
    override val quietHours = "Quiet hours"
    override val quietHoursDetail = "During these hours a reminder arrives silently, without a sound."
    override val quietHoursFrom = "From"
    override val quietHoursTo = "To"

    override val disableCloudAi = "Disable cloud AI"
    override val disableCloudAiDetail = "Keep your key but refuse every cloud call. Aida stays fully on-device."
    override val cloudAvailability = "Availability"
    override val availabilityReady = "Ready when online"
    override val availabilityNoKey = "No key added"
    override val availabilityOff = "Turned off"
    override val freeTierState = "Free-tier state"
    override fun quotaLabel(status: CloudQuotaStatus) = when (status) {
        CloudQuotaStatus.AVAILABLE -> "Available"
        CloudQuotaStatus.RATE_LIMITED -> "Rate-limited — try again shortly"
        CloudQuotaStatus.QUOTA_EXCEEDED -> "Daily free quota used up"
        CloudQuotaStatus.UNKNOWN -> "Unknown"
    }

    override val biometricLock = "Biometric lock"
    override val biometricLockDetail = "Require your fingerprint or face to open Aida."
    override val autoLock = "Auto-lock"
    override val autoLockDetail = "How long after leaving before Aida locks again."
    override fun timeoutLabel(seconds: Int) = when (seconds) {
        0 -> "Immediately"
        in 1..59 -> "$seconds seconds"
        else -> {
            val minutes = seconds / 60
            if (minutes == 1) "1 minute" else "$minutes minutes"
        }
    }
    override val lockWhenBackgrounded = "Lock when I leave"
    override val lockWhenBackgroundedDetail = "Lock the moment Aida goes to the background, not only after the delay."
    override val permissions = "Permissions"
    override val permissionsDetail = "What Aida may reach on this phone."
    override val protectHeader = "Require unlock for"
    override val protectDetail = "Ask for your fingerprint before opening these areas."
    override val protectMoney = "Money"
    override val protectMemory = "Memory"
    override val protectInvoices = "Invoices"
    override val protectBackups = "Backups"
    override val activityLog = "Activity log"
    override val activityLogDetail = "Everything Aida has done, in order."

    override val sectionCrossApp = "Cross-app automations"
    override val crossAppAutomations = "Let Aida act across apps"
    override val crossAppAutomationsDetail =
        "Off by default. When on, an automation can perform a system gesture (back, home, recents) or " +
            "react to another app's notification. You turn on each system grant from the rule that " +
            "needs it, and Aida never reads your screen. She works fully without this."

    override val dataBackup = "Backup, restore & export"
    override val dataBackupDetail = "An encrypted copy you keep, or a readable export."
    override val eraseData = "Erase all data"
    override val eraseDataDetail = "Remove everything from this phone. There is no copy anywhere else."
    override val eraseDataTitle = "Erase everything?"
    override val eraseDataMessage =
        "Every task, note, client, invoice, reminder and conversation is deleted from this phone, " +
            "and the vault key is destroyed. This cannot be undone."
    override val eraseDataConfirm = "Erase everything"
    override val eraseDataKeep = "Keep it"
    override val eraseDataFailed = "The data could not be erased. Try once more."
    override val erasing = "Erasing…"

    override val integrationCalendar = "Calendar"
    override val integrationCalendarDetail = "Read your day and add the events you ask for."
    override val integrationContacts = "Contacts"
    override val integrationContactsDetail = "Find a person when you ask her to call or message them."
    override val defaultAssistant = "Default assistant"
    override val defaultAssistantDetail = "Hold Aida on the side button in place of the system assistant."
    override val connected = "Allowed"
    override val notConnected = "Not allowed"

    override val privacyInformation = "Privacy information"
    override val privacyInformationDetail = "What's stored, what's encrypted, what leaves the device."
    override val licenses = "Open-source licenses"
    override val licensesDetail = "The free components Aida is built on."

    override val chooseDateFormat = "Date format"
    override val chooseCurrency = "Currency"
    override val chooseFirstDay = "First day of week"
    override val chooseAutoLock = "Auto-lock after"
    override val timeSet = "Set"
    override val timeCancel = "Cancel"

    override val conversationNotCleared = "The conversation could not be cleared. Try once more."
    override val geminiKeyRefused =
        "That key could not be stored securely, so Aida did not keep it. She carries on on-device."
}

internal object BengaliSettingsWording : SettingsWording {

    override val title = "সেটিংস"
    override val back = "পেছনে যান"
    override val dismiss = "বন্ধ করুন"

    override val language = "ভাষা"
    override val languageFootnote =
        "Auto বেছে নিলে আপনি যে ভাষায় বলেছেন, আইদা সেই ভাষাতেই উত্তর দেবে। বাংলা বা English " +
            "বেছে নিলে প্রতিটি উত্তরই সেই ভাষায় হবে।"

    override val voice = "ভয়েস"
    override val spokenResponses = "মুখে উত্তর"
    override val spokenResponsesDetail = "আইদা তার উত্তরগুলো পড়ে শোনাবে।"
    override val activationSound = "শোনা শুরুর শব্দ"
    override val activationSoundDetail = "সে শোনা শুরু করলে আর কোনো কাজ হয়ে গেলে ছোট একটি টোন।"
    override val hapticFeedback = "হ্যাপটিক"
    override val hapticFeedbackDetail = "ট্যাপ আর নিশ্চিত করার সময় আঙুলের নিচে হালকা কম্পন।"
    override val howMiraSounds = "আইদা কেমন শোনায়"

    override fun voiceSummary(preferences: VoicePreferences): String {
        val chosen = chosenVoices(preferences)
        return when {
            chosen.isEmpty() -> "এখনও কোনো ভয়েস বাছা হয়নি — আইদা কেমন শোনাবে বেছে নিন"
            preferences.setupComplete -> "ভয়েস: $chosen"
            else -> "ভয়েস: $chosen — সেটআপ এখনও শেষ হয়নি"
        }
    }

    override val assistant = "অ্যাসিস্ট্যান্ট"
    override val privacyAndData = "গোপনীয়তা ও ডেটা"
    override val privacyDashboard = "গোপনীয়তা"
    override val privacyDashboardDetail = "কী সংরক্ষিত, কী এনক্রিপ্ট করা, কী ডিভাইস ছাড়ে"
    override val backupAndRestore = "ব্যাকআপ ও পুনরুদ্ধার"
    override val backupAndRestoreDetail = "এনক্রিপ্ট করা কপি রপ্তানি করুন, বা পুনরুদ্ধার করুন"
    override val setMiraAsAssistant = "আইদাকে আমার অ্যাসিস্ট্যান্ট করুন"
    override val setMiraAsAssistantDetail =
        "সাইড বাটনের জেসচার, একটি টেস্ট, আর এই ফোনের নিজস্ব বাড়তি ধাপগুলো।"

    override val reminders = "রিমাইন্ডার"
    override val remindersMayBeLate = "রিমাইন্ডার দেরিতে আসতে পারে"
    override val remindersMayBeLateDetail =
        "অ্যান্ড্রয়েড আইদাকে ঠিক মিনিটে অ্যালার্ম দিতে দিচ্ছে না, তাই রিমাইন্ডার দেরিতে আসতে " +
            "পারে। অনুমতিটি দিলে ঠিক হয়ে যাবে।"
    override val allowExactAlarms = "ঠিক সময়ের অ্যালার্মের অনুমতি দিন"

    override val gemini = "Gemini"
    override val geminiOn = "Gemini চালু আছে"
    override val geminiOff = "Gemini বন্ধ আছে"
    override val geminiDetail =
        "এটি ছাড়াও আইদা পুরোপুরি কাজ করে। কি সেভ করা থাকলে, ফোনের নিজের ইঞ্জিন যেটি বুঝতে " +
            "পারে না কেবল সেই একটি অনুরোধের লেখাটুকুই Google-এ যায় — আপনার কাজ, রিমাইন্ডার বা " +
            "ভল্টের কিছুই নয়।"
    override val addKey = "একটি কি যোগ করুন"
    override val replaceKey = "কি বদলান"
    override val removeKey = "মুছে ফেলুন"
    override val geminiKeyTitle = "Gemini API কি"
    override val geminiKeyDetail =
        "Google AI Studio থেকে একটি ফ্রি-টিয়ার কি পেস্ট করুন। আইদা এই ফোনের নিরাপদ হার্ডওয়্যারে " +
            "রাখা একটি চাবি দিয়ে সেটি সিল করে দেয়, আর কখনও দেখায় না।"
    override val keyLabel = "কি"
    override val saveKey = "কি সেভ করুন"
    override val savingKey = "সেভ হচ্ছে"

    override val conversation = "কথোপকথন"
    override val clearConversation = "কথোপকথন মুছে ফেলুন"
    override val clearConversationDetail =
        "এই ফোন থেকে প্রতিটি কথা মুছে যাবে। আর কোথাও এর কোনো কপি নেই।"
    override val clearConversationTitle = "কথোপকথন মুছে ফেলবেন?"
    override val clearConversationMessage =
        "আইদার রাখা প্রতিটি কথা এই ফোন থেকে মুছে যাবে। কাজ, রিমাইন্ডার আর ভল্টে হাত পড়বে না।"
    override val clearIt = "মুছে ফেলুন"
    override val keepIt = "রেখে দিন"

    override val about = "সম্পর্কে"

    override fun versionLine(versionName: String) = bengaliNumerals("আইদা $versionName")

    override fun buildLine(versionCode: Int) =
        bengaliNumerals("বিল্ড $versionCode · আবুর জন্য একটি ব্যক্তিগত অ্যাসিস্ট্যান্ট, শুধু এই ফোনেই চলে।")

    override val checkForUpdates = "আপডেট চেক করুন"
    override val checkForUpdatesDetail = "GitHub রিলিজ থেকে সরাসরি OTA আপডেট"
    override val checkingForUpdates = "গিটহাবে আপডেট চেক করা হচ্ছে..."
    override val appUpToDate = "আইদা আপ-টু-ডেট আছে"
    override val updateAvailable = "নতুন আপডেট উপলব্ধ"
    override val downloadAndInstall = "ডাউনলোড ও ইনস্টল করুন"
    override val downloadingUpdate = "আপডেট ডাউনলোড হচ্ছে..."
    override val installNow = "এখনই ইনস্টল করুন"

    override val sectionProfile = "প্রোফাইল"
    override val userNameTitle = "আপনার নাম"
    override val userNameNotSet = "নাম দেওয়া হয়নি"
    override val editNameTitle = "নাম পরিবর্তন করুন"
    override val namePlaceholder = "আপনার নাম লিখুন"
    override val save = "সংরক্ষণ"
    override val cancel = "বাতিল"

    override val sectionMira = "আইদা"
    override val sectionGeneral = "সাধারণ"
    override val sectionNotifications = "নোটিফিকেশন"
    override val sectionAi = "AI"
    override val sectionPrivacy = "গোপনীয়তা ও নিরাপত্তা"
    override val sectionData = "ডেটা"
    override val sectionIntegrations = "ইন্টিগ্রেশন"

    override val assistantRole = "অ্যাসিস্ট্যান্টের ভূমিকা"
    override val assistantRoleDetail = "সাইড বাটনের জেসচার, একটি টেস্ট, আর এই ফোনের নিজস্ব বাড়তি ধাপগুলো।"
    override val salah = "নামাজ"
    override val salahDetail = "অবস্থান, হিসাবের পদ্ধতি, নোটিফিকেশন আর তাহাজ্জুদ।"
    override val conversationStyle = "কথা বলার ধরন"
    override val conversationStyleDetail = "উত্তরের সাথে আইদা কতটা বলবে। সংখ্যাগুলো কখনও বদলায় না।"
    override fun conversationStyleName(style: ConversationStyle) = when (style) {
        ConversationStyle.CONCISE -> "সংক্ষিপ্ত"
        ConversationStyle.BALANCED -> "সুষম"
        ConversationStyle.DETAILED -> "বিস্তারিত"
    }

    override val theme = "থিম"
    override val themeValue = "AMOLED ডার্ক"
    override val themeDetail = "আইদা ডিজাইনেই ডার্ক — এই ফোনের স্ক্রিনের জন্য খাঁটি কালো, একটি অ্যাকসেন্ট, বদলানোর কিছু নেই।"
    override val dateFormat = "তারিখের ধরন"
    override val dateFormatDetail = "অ্যাপজুড়ে তারিখ কীভাবে লেখা হবে।"
    override val currency = "মুদ্রা"
    override val currencyDetail = "নতুন ইনভয়েস বা কোটে মুদ্রা না বললে এটিই ধরা হবে।"
    override val firstDayOfWeek = "সপ্তাহের প্রথম দিন"
    override val firstDayOfWeekDetail = "রিপোর্ট ও সারসংক্ষেপে সপ্তাহ যেদিন থেকে শুরু হয়।"
    override fun dayName(day: DayOfWeek) = when (day) {
        DayOfWeek.MONDAY -> "সোমবার"
        DayOfWeek.TUESDAY -> "মঙ্গলবার"
        DayOfWeek.WEDNESDAY -> "বুধবার"
        DayOfWeek.THURSDAY -> "বৃহস্পতিবার"
        DayOfWeek.FRIDAY -> "শুক্রবার"
        DayOfWeek.SATURDAY -> "শনিবার"
        DayOfWeek.SUNDAY -> "রবিবার"
    }

    override val notifyTasks = "কাজ"
    override val notifyTasksDetail = "যে কাজের সময় হয়েছে তার রিমাইন্ডার।"
    override val notifyReminders = "রিমাইন্ডার"
    override val notifyRemindersDetail = "আপনার নিজের দেওয়া রিমাইন্ডার, আর জায়গা ও জন্মদিনের মনে করিয়ে দেওয়া।"
    override val notifyInvoices = "ইনভয়েস"
    override val notifyInvoicesDetail = "ইনভয়েসের সময় হলে বা পেরিয়ে গেলে।"
    override val notifySubscriptions = "সাবস্ক্রিপশন"
    override val notifySubscriptionsDetail = "সাবস্ক্রিপশন নবায়নের আগে।"
    override val quietHours = "নীরব সময়"
    override val quietHoursDetail = "এই সময়ে রিমাইন্ডার কোনো শব্দ ছাড়া নীরবে আসে।"
    override val quietHoursFrom = "থেকে"
    override val quietHoursTo = "পর্যন্ত"

    override val disableCloudAi = "ক্লাউড AI বন্ধ করুন"
    override val disableCloudAiDetail = "কি রেখেই প্রতিটি ক্লাউড কল বন্ধ। আইদা পুরোপুরি ফোনেই চলবে।"
    override val cloudAvailability = "লভ্যতা"
    override val availabilityReady = "অনলাইন থাকলে প্রস্তুত"
    override val availabilityNoKey = "কোনো কি যোগ করা নেই"
    override val availabilityOff = "বন্ধ আছে"
    override val freeTierState = "ফ্রি-টিয়ারের অবস্থা"
    override fun quotaLabel(status: CloudQuotaStatus) = when (status) {
        CloudQuotaStatus.AVAILABLE -> "লভ্য"
        CloudQuotaStatus.RATE_LIMITED -> "সীমিত হয়েছে — একটু পরে আবার চেষ্টা করুন"
        CloudQuotaStatus.QUOTA_EXCEEDED -> "আজকের ফ্রি কোটা শেষ"
        CloudQuotaStatus.UNKNOWN -> "অজানা"
    }

    override val biometricLock = "বায়োমেট্রিক লক"
    override val biometricLockDetail = "আইদা খুলতে আপনার আঙুলের ছাপ বা মুখ লাগবে।"
    override val autoLock = "অটো-লক"
    override val autoLockDetail = "বেরিয়ে যাওয়ার কতক্ষণ পর আইদা আবার লক হবে।"
    override fun timeoutLabel(seconds: Int) = when (seconds) {
        0 -> "সঙ্গে সঙ্গে"
        in 1..59 -> bengaliNumerals("$seconds সেকেন্ড")
        else -> {
            val minutes = seconds / 60
            bengaliNumerals("$minutes মিনিট")
        }
    }
    override val lockWhenBackgrounded = "বেরোলেই লক"
    override val lockWhenBackgroundedDetail = "দেরি না করে, আইদা ব্যাকগ্রাউন্ডে গেলেই লক করুন।"
    override val permissions = "অনুমতি"
    override val permissionsDetail = "এই ফোনে আইদা কী কী ব্যবহার করতে পারবে।"
    override val protectHeader = "খুলতে লক লাগবে"
    override val protectDetail = "এই বিভাগগুলো খোলার আগে আঙুলের ছাপ চাইবে।"
    override val protectMoney = "টাকা"
    override val protectMemory = "মেমরি"
    override val protectInvoices = "ইনভয়েস"
    override val protectBackups = "ব্যাকআপ"
    override val activityLog = "কার্যকলাপের লগ"
    override val activityLogDetail = "আইদা যা যা করেছে, ক্রম অনুযায়ী।"

    override val sectionCrossApp = "ক্রস-অ্যাপ অটোমেশন"
    override val crossAppAutomations = "আইদাকে অ্যাপ জুড়ে কাজ করতে দিন"
    override val crossAppAutomationsDetail =
        "ডিফল্টে বন্ধ। চালু থাকলে একটি অটোমেশন সিস্টেম জেসচার (ব্যাক, হোম, রিসেন্টস) করতে পারে বা অন্য " +
            "অ্যাপের নোটিফিকেশনে সাড়া দিতে পারে। যে নিয়মের দরকার সেখান থেকেই প্রতিটি সিস্টেম অনুমতি চালু " +
            "করবেন, আর আইদা কখনও আপনার স্ক্রিন পড়ে না। এটি ছাড়াও সে পুরোপুরি কাজ করে।"

    override val dataBackup = "ব্যাকআপ, পুনরুদ্ধার ও রপ্তানি"
    override val dataBackupDetail = "আপনার রাখা এনক্রিপ্ট করা কপি, বা পড়ার মতো রপ্তানি।"
    override val eraseData = "সব ডেটা মুছে ফেলুন"
    override val eraseDataDetail = "এই ফোন থেকে সবকিছু সরিয়ে দিন। আর কোথাও কোনো কপি নেই।"
    override val eraseDataTitle = "সবকিছু মুছে ফেলবেন?"
    override val eraseDataMessage =
        "প্রতিটি কাজ, নোট, ক্লায়েন্ট, ইনভয়েস, রিমাইন্ডার আর কথোপকথন এই ফোন থেকে মুছে যাবে, " +
            "আর ভল্টের চাবি ধ্বংস হবে। এটি ফেরানো যাবে না।"
    override val eraseDataConfirm = "সব মুছে ফেলুন"
    override val eraseDataKeep = "রেখে দিন"
    override val eraseDataFailed = "ডেটা মোছা গেল না। আরেকবার চেষ্টা করুন।"
    override val erasing = "মুছে ফেলা হচ্ছে…"

    override val integrationCalendar = "ক্যালেন্ডার"
    override val integrationCalendarDetail = "আপনার দিন দেখতে আর আপনি যে ইভেন্ট বলেন তা যোগ করতে।"
    override val integrationContacts = "পরিচিতি"
    override val integrationContactsDetail = "কল বা মেসেজ করতে বললে মানুষটিকে খুঁজে পেতে।"
    override val defaultAssistant = "ডিফল্ট অ্যাসিস্ট্যান্ট"
    override val defaultAssistantDetail = "সিস্টেম অ্যাসিস্ট্যান্টের বদলে সাইড বাটনে আইদাকে রাখুন।"
    override val connected = "অনুমতি আছে"
    override val notConnected = "অনুমতি নেই"

    override val privacyInformation = "গোপনীয়তার তথ্য"
    override val privacyInformationDetail = "কী সংরক্ষিত, কী এনক্রিপ্ট করা, কী ডিভাইস ছাড়ে।"
    override val licenses = "ওপেন-সোর্স লাইসেন্স"
    override val licensesDetail = "আইদা যেসব ফ্রি উপাদানের উপর তৈরি।"

    override val chooseDateFormat = "তারিখের ধরন"
    override val chooseCurrency = "মুদ্রা"
    override val chooseFirstDay = "সপ্তাহের প্রথম দিন"
    override val chooseAutoLock = "যতক্ষণ পর অটো-লক"
    override val timeSet = "সেট করুন"
    override val timeCancel = "বাতিল"

    override val conversationNotCleared = "কথোপকথনটি মোছা গেল না। আরেকবার চেষ্টা করুন।"
    override val geminiKeyRefused =
        "কি-টি নিরাপদে রাখা গেল না, তাই আইদা সেটি রাখেনি। সে ফোনেই কাজ চালিয়ে যাচ্ছে।"
}

internal fun settingsWording(language: SpokenLanguage): SettingsWording = when (language) {
    SpokenLanguage.ENGLISH -> EnglishSettingsWording
    SpokenLanguage.BENGALI -> BengaliSettingsWording
}

/**
 * Which languages actually have a voice behind them, in one copy for both phrasebooks.
 *
 * Each name is written in its own language whichever phrasebook asks, for the same reason the
 * picker's labels are (`ui/common/LanguageLabels`): the name of a language belongs to it.
 */
private fun chosenVoices(preferences: VoicePreferences): String = buildList {
    if (!preferences.englishVoiceId.isNullOrBlank()) add("English")
    if (!preferences.bengaliVoiceId.isNullOrBlank()) add("বাংলা")
}.joinToString(" · ")
