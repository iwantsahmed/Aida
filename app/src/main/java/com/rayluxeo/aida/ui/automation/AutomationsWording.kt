package com.rayluxeo.aida.ui.automation

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.AutomationAction
import com.rayluxeo.aida.domain.model.AutomationOutcome
import com.rayluxeo.aida.domain.model.AutomationTrigger
import com.rayluxeo.aida.domain.model.GlobalDeviceAction
import com.rayluxeo.aida.domain.scheduler.PlaceWatchRefusal
import com.rayluxeo.aida.ui.response.bengaliNumerals
import java.time.DayOfWeek

/**
 * Every word the Automations screen puts in front of Abu, in one language: the list, the add-or-edit
 * sheet, the run-history sheet and the short lines the view model raises after a run, a save or a
 * delete.
 *
 * The notices live here rather than in [AutomationsViewModel] for the same reason the rest does: the
 * view model is where Abu's language is known, so it words a result once and the screen never has to
 * choose between an English literal and a Bengali one.
 *
 * Two kinds of name stay in Latin on purpose. An action chip prints the action's own type name
 * ([AutomationAction] subtype, e.g. `RunUtterance`), which is a name to look up rather than a phrase
 * to translate; and a failure line appends a [com.rayluxeo.aida.core.common.MiraError] detail, which
 * is developer text. Everything Abu is meant to read is translated.
 */
internal interface AutomationsWording {

    // --- The list ------------------------------------------------------------------------------

    /** The top bar, and the word for the area itself. */
    val title: String

    val newRule: String

    /** The empty list is a normal state: one calm line and a way forward, never a fault. */
    val noRulesMessage: String

    val addRule: String

    // --- A rule's card -------------------------------------------------------------------------

    /** The word before a trigger on the chip. */
    val triggerWord: String

    /** The word before an action on the chip. */
    val actionWord: String

    /** The word before an outcome on a run row. */
    val outcomeWord: String

    /** What fires a rule, named in Abu's language. Covers every [AutomationTrigger]. */
    fun triggerLabel(trigger: AutomationTrigger): String

    /** The trigger chip's full text: the word and the label it carries. */
    fun triggerChip(trigger: AutomationTrigger): String = "$triggerWord: ${triggerLabel(trigger)}"

    /**
     * The action chip's full text. The action's type name stays in Latin — it is a name, not a
     * phrase — so only the word before it changes with language.
     */
    fun actionChip(action: AutomationAction): String = "$actionWord: ${action.javaClass.simpleName}"

    // --- Row controls, read by TalkBack, which cannot see the glyph ----------------------------

    val runManually: String

    val runHistory: String

    val editAutomation: String

    val deleteAutomation: String

    // --- The run-history sheet -----------------------------------------------------------------

    /** The sheet's title; the rule names itself in whatever language Abu saved it under. */
    fun runHistoryTitle(name: String): String

    val noRunsMessage: String

    /** How a run ended, named in Abu's language. Covers every [AutomationOutcome]. */
    fun outcomeLabel(outcome: AutomationOutcome): String

    /** A run row's one line: the word and the outcome it carries. */
    fun runOutcome(outcome: AutomationOutcome): String = "$outcomeWord: ${outcomeLabel(outcome)}"

    // --- The add-or-edit sheet -----------------------------------------------------------------

    val createRuleTitle: String

    val editRuleTitle: String

    val nameLabel: String

    val namePlaceholder: String

    val descriptionLabel: String

    /** The heading over the trigger picker. */
    val triggerSectionLabel: String

    /** The hint shown under the picker when the chosen trigger is Manual. */
    val manualTriggerNote: String

    /**
     * The hint shown under the picker for a record-driven trigger (an invoice, a task, a subscription
     * or a project reaching a date). These need no setup — they fire on the daily check when the
     * record condition is met — so the note explains that rather than offering an input.
     */
    fun recordTriggerNote(trigger: AutomationTrigger): String

    // --- Place-trigger inputs ------------------------------------------------------------------

    /** The heading over the saved-place picker shown when the trigger is Place. */
    val choosePlaceLabel: String

    /** Shown in place of the picker when Abu has no saved places yet; paired with [openPlacesAction]. */
    val noSavedPlacesYet: String

    /** The way forward from the empty place picker: a jump to the Places screen to add one. */
    val openPlacesAction: String

    /** The note under a chosen place, naming how close counts as arriving. */
    fun placeRadiusNote(radiusMetres: Int): String

    /**
     * Why a place rule saved but is not being watched — a withheld grant, location off, the platform
     * limit reached. Said plainly so Abu knows the rule will not fire until he acts, rather than
     * being told it is armed when it is not (§5).
     */
    fun placeWatchRefused(reason: PlaceWatchRefusal): String

    val actionSectionLabel: String

    // --- The action picker and its per-action fields -------------------------------------------

    val actionRunCommand: String

    val actionNotify: String

    val actionOpenLink: String

    val actionPerformGlobalAction: String

    val actionLaunchApp: String

    val actionDailyBrief: String

    // --- Cross-app action inputs (the opt-in tier) ---------------------------------------------

    /** The heading over the global-gesture picker. */
    val chooseGestureLabel: String

    /** A system gesture, named in Abu's language. Covers every [GlobalDeviceAction]. */
    fun globalActionLabel(action: GlobalDeviceAction): String

    /** The heading over the launch-an-app picker. */
    val chooseAppLabel: String

    /** Shown when no launchable app could be read (a rare empty catalog), in place of the picker. */
    val noAppsFound: String

    /**
     * The note shown when the whole cross-app tier is switched off (the master toggle), which is a
     * different fix from a missing OS grant: it is turned back on in Settings, not a system screen,
     * so this note carries no "enable" button that would open the wrong place (§5/§12).
     */
    val crossAppTierOffNote: String

    /** The note shown when a global-action rule needs the accessibility service and it is off. */
    val accessibilityOffNote: String

    /** The button beside [accessibilityOffNote]: a jump to the accessibility settings screen. */
    val enableAccessibilityAction: String

    // --- Notification-trigger inputs (the opt-in tier) -----------------------------------------

    /** The heading over the "from which app" picker for a notification trigger. */
    val notificationFromAppLabel: String

    /** The first chip: fire for a notification from any app (no package filter). */
    val notificationAnyApp: String

    val notificationTitleFilterLabel: String

    val notificationTitleFilterPlaceholder: String

    /** The note shown when a notification rule needs notification access and it is off. */
    val notificationAccessOffNote: String

    /** The button beside [notificationAccessOffNote]: a jump to the notification-access screen. */
    val enableNotificationAccessAction: String

    val actionFieldLabel: String

    val actionPlaceholder: String

    val notifyTitleLabel: String

    val notifyBodyLabel: String

    val linkLabel: String

    val linkPlaceholder: String

    // --- Time-based trigger inputs -------------------------------------------------------------

    val timeLabel: String

    val daysLabel: String

    /** Shown when no day is picked, because an empty set means every day. */
    val everyDayNote: String

    /** A weekday, short, for the day chips. */
    fun dayLabel(day: DayOfWeek): String

    val intervalLabel: String

    val intervalPlaceholder: String

    val timePickerTitle: String

    val setLabel: String

    val cancelLabel: String

    val enableImmediately: String

    val createRuleButton: String

    val saveChangesButton: String

    // --- The lines the view model raises -------------------------------------------------------

    val runningAutomation: String

    /** The outcome is already worded; this frames it. */
    fun runFinished(outcome: AutomationOutcome): String

    /** [detail] is a developer string appended for debugging; the sentence around it is Abu's. */
    fun runFailed(detail: String?): String

    fun automationSaved(name: String): String

    fun saveFailed(detail: String?): String

    fun validationError(detail: String?): String

    val automationDeleted: String

    fun deleteFailed(detail: String?): String
}

/** Automations in English: plain, short, never chatty. */
internal object EnglishAutomationsWording : AutomationsWording {

    override val title: String = "Automations"

    override val newRule: String = "New Automation Rule"

    override val noRulesMessage: String =
        "No automation rules created yet. Automate repetitive tasks and briefs."

    override val addRule: String = "Add Rule"

    override val triggerWord: String = "Trigger"

    override val actionWord: String = "Action"

    override val outcomeWord: String = "Outcome"

    override fun triggerLabel(trigger: AutomationTrigger): String = when (trigger) {
        AutomationTrigger.MANUAL -> "Manually"
        AutomationTrigger.TIME_OF_DAY -> "At a time of day"
        AutomationTrigger.INTERVAL -> "On a repeat"
        AutomationTrigger.PLACE -> "At a place"
        AutomationTrigger.NOTIFICATION_POSTED -> "When a notification arrives"
        AutomationTrigger.APP_START -> "When the app opens"
        AutomationTrigger.POWER_CONNECTED -> "When charging starts"
        AutomationTrigger.POWER_DISCONNECTED -> "When unplugged"
        else -> trigger.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
    }

    override val runManually: String = "Run manually"

    override val runHistory: String = "Run history"

    override val editAutomation: String = "Edit automation"

    override val deleteAutomation: String = "Delete automation"

    override fun runHistoryTitle(name: String): String = "Run History: $name"

    override val noRunsMessage: String = "No runs recorded for this automation yet."

    override fun outcomeLabel(outcome: AutomationOutcome): String = outcome.name

    override val createRuleTitle: String = "Create Automation Rule"

    override val editRuleTitle: String = "Edit Automation Rule"

    override val nameLabel: String = "Automation Name"

    override val namePlaceholder: String = "e.g., Weekly Planning Task, Morning Brief"

    override val descriptionLabel: String = "Description (Optional)"

    override val triggerSectionLabel: String = "When"

    override val manualTriggerNote: String = "Runs only when you tap Run on the rule."

    override fun recordTriggerNote(trigger: AutomationTrigger): String = when (trigger) {
        AutomationTrigger.INVOICE_DUE -> "Fires on the daily check when an invoice is within a week of its due date."
        AutomationTrigger.INVOICE_OVERDUE -> "Fires on the daily check when an invoice passes its due date unpaid."
        AutomationTrigger.TASK_DUE -> "Fires on the daily check when a task is due today."
        AutomationTrigger.TASK_OVERDUE -> "Fires on the daily check when a task is past its due date."
        AutomationTrigger.SUBSCRIPTION_RENEWAL -> "Fires on the daily check when a subscription is within a week of renewing."
        AutomationTrigger.PROJECT_DEADLINE -> "Fires on the daily check when a project is within two weeks of its deadline."
        else -> "Fires automatically on the daily check when the record condition is met."
    }

    override val choosePlaceLabel: String = "Which place"

    override val noSavedPlacesYet: String =
        "No places saved yet. Add one Aida can watch, then pick it here."

    override val openPlacesAction: String = "Add a place"

    override fun placeRadiusNote(radiusMetres: Int): String =
        "Fires when you arrive within $radiusMetres m of here."

    override fun placeWatchRefused(reason: PlaceWatchRefusal): String = when (reason) {
        PlaceWatchRefusal.PERMISSION_MISSING ->
            "Saved, but Aida needs location access to watch this place."
        PlaceWatchRefusal.BACKGROUND_PERMISSION_MISSING ->
            "Saved, but Android needs location allowed all the time to watch this place."
        PlaceWatchRefusal.LOCATION_DISABLED ->
            "Saved, but location is switched off, so Aida cannot watch this place."
        PlaceWatchRefusal.NO_PROVIDER ->
            "Saved, but this phone has no location provider, so place rules cannot run here."
        PlaceWatchRefusal.NO_FIX_YET ->
            "Saved, but Aida could not work out where you are just now."
        PlaceWatchRefusal.LIMIT_REACHED ->
            "Saved, but Aida is already watching as many places as Android will hold."
        PlaceWatchRefusal.BATTERY_RESTRICTED ->
            "Saved, but Android has restricted Aida in the background, so she may miss your arrival."
    }

    override val actionSectionLabel: String = "Do this"

    override val actionRunCommand: String = "Run a command"

    override val actionNotify: String = "Notify me"

    override val actionOpenLink: String = "Open a link"

    override val actionPerformGlobalAction: String = "Do a system gesture"

    override val actionLaunchApp: String = "Open an app"

    override val actionDailyBrief: String = "Read the daily brief"

    override val chooseGestureLabel: String = "Which gesture"

    override fun globalActionLabel(action: GlobalDeviceAction): String = when (action) {
        GlobalDeviceAction.BACK -> "Back"
        GlobalDeviceAction.HOME -> "Home"
        GlobalDeviceAction.RECENTS -> "Recent apps"
        GlobalDeviceAction.NOTIFICATIONS -> "Notification shade"
        GlobalDeviceAction.QUICK_SETTINGS -> "Quick settings"
    }

    override val chooseAppLabel: String = "Which app"

    override val noAppsFound: String = "No apps to open were found on this phone."

    override val crossAppTierOffNote: String =
        "Cross-app automations are off. Turn them on in Settings → Cross-app automations for this to fire."

    override val accessibilityOffNote: String =
        "This needs Aida's accessibility service, which is off. She can press the button but never reads your screen."

    override val enableAccessibilityAction: String = "Enable the accessibility service"

    override val notificationFromAppLabel: String = "From which app"

    override val notificationAnyApp: String = "Any app"

    override val notificationTitleFilterLabel: String = "Title contains (optional)"

    override val notificationTitleFilterPlaceholder: String = "e.g., Payment received"

    override val notificationAccessOffNote: String =
        "This needs notification access, which is off. Aida reads a notification's title only to match this rule."

    override val enableNotificationAccessAction: String = "Turn on notification access"

    override val actionFieldLabel: String = "Command"

    override val actionPlaceholder: String = "e.g., Create weekly planning task, Show my tasks"

    override val notifyTitleLabel: String = "Notification title"

    override val notifyBodyLabel: String = "Notification text (optional)"

    override val linkLabel: String = "Link"

    override val linkPlaceholder: String = "e.g., https://example.com"

    override val timeLabel: String = "Time"

    override val daysLabel: String = "On these days"

    override val everyDayNote: String = "Every day"

    override fun dayLabel(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "Mon"
        DayOfWeek.TUESDAY -> "Tue"
        DayOfWeek.WEDNESDAY -> "Wed"
        DayOfWeek.THURSDAY -> "Thu"
        DayOfWeek.FRIDAY -> "Fri"
        DayOfWeek.SATURDAY -> "Sat"
        DayOfWeek.SUNDAY -> "Sun"
    }

    override val intervalLabel: String = "Minutes between runs"

    override val intervalPlaceholder: String = "e.g., 90"

    override val timePickerTitle: String = "Pick a time"

    override val setLabel: String = "Set"

    override val cancelLabel: String = "Cancel"

    override val enableImmediately: String = "Enable Rule Immediately"

    override val createRuleButton: String = "Create Rule"

    override val saveChangesButton: String = "Save Changes"

    override val runningAutomation: String = "Running automation…"

    override fun runFinished(outcome: AutomationOutcome): String =
        "Automation run finished: ${outcomeLabel(outcome)}"

    override fun runFailed(detail: String?): String = "Automation run failed: $detail"

    override fun automationSaved(name: String): String = "Automation '$name' saved"

    override fun saveFailed(detail: String?): String = "Failed to save: $detail"

    override fun validationError(detail: String?): String = "Validation error: $detail"

    override val automationDeleted: String = "Automation deleted"

    override fun deleteFailed(detail: String?): String = "Error: $detail"
}

/**
 * Automations in Bengali, Bengali script throughout.
 *
 * Trigger kinds and outcomes are translated rather than transliterated — they are ideas Abu reads,
 * not names he says in English — while an action's own type name and a failure detail stay in Latin
 * for the reasons on [AutomationsWording].
 */
internal object BengaliAutomationsWording : AutomationsWording {

    override val title: String = "অটোমেশন"

    override val newRule: String = "নতুন অটোমেশন নিয়ম"

    override val noRulesMessage: String =
        "এখনও কোনো অটোমেশন নিয়ম তৈরি হয়নি। বারবার করা কাজ আর ব্রিফ স্বয়ংক্রিয় করুন।"

    override val addRule: String = "নিয়ম যোগ করুন"

    override val triggerWord: String = "ট্রিগার"

    override val actionWord: String = "অ্যাকশন"

    override val outcomeWord: String = "ফলাফল"

    override fun triggerLabel(trigger: AutomationTrigger): String = when (trigger) {
        AutomationTrigger.MANUAL -> "নিজে চালানো"
        AutomationTrigger.TIME_OF_DAY -> "দিনের সময়"
        AutomationTrigger.INTERVAL -> "নির্দিষ্ট বিরতি"
        AutomationTrigger.PLACE -> "স্থান"
        AutomationTrigger.NOTIFICATION_POSTED -> "নোটিফিকেশন এলে"
        AutomationTrigger.CALENDAR_EVENT -> "ক্যালেন্ডার ইভেন্ট"
        AutomationTrigger.DATE -> "তারিখ"
        AutomationTrigger.DAY_OF_WEEK -> "সপ্তাহের দিন"
        AutomationTrigger.TASK_DUE -> "কাজের সময়"
        AutomationTrigger.TASK_OVERDUE -> "কাজের সময় পেরোনো"
        AutomationTrigger.INVOICE_DUE -> "ইনভয়েসের সময়"
        AutomationTrigger.INVOICE_OVERDUE -> "ইনভয়েসের সময় পেরোনো"
        AutomationTrigger.SUBSCRIPTION_RENEWAL -> "সাবস্ক্রিপশন নবায়ন"
        AutomationTrigger.PROJECT_DEADLINE -> "প্রকল্পের সময়সীমা"
        AutomationTrigger.APP_START -> "অ্যাপ চালু হলে"
        AutomationTrigger.POWER_CONNECTED -> "চার্জ শুরু হলে"
        AutomationTrigger.POWER_DISCONNECTED -> "চার্জার খুললে"
    }

    override val runManually: String = "নিজে চালান"

    override val runHistory: String = "চালানোর ইতিহাস"

    override val editAutomation: String = "অটোমেশন বদলান"

    override val deleteAutomation: String = "অটোমেশন মুছুন"

    override fun runHistoryTitle(name: String): String = "চালানোর ইতিহাস: $name"

    override val noRunsMessage: String = "এই অটোমেশনের কোনো চালানোর রেকর্ড এখনও নেই।"

    override fun outcomeLabel(outcome: AutomationOutcome): String = when (outcome) {
        AutomationOutcome.SUCCESS -> "সফল"
        AutomationOutcome.FAILED -> "ব্যর্থ"
        AutomationOutcome.SKIPPED -> "এড়ানো হয়েছে"
    }

    override val createRuleTitle: String = "অটোমেশন নিয়ম তৈরি করুন"

    override val editRuleTitle: String = "অটোমেশন নিয়ম বদলান"

    override val nameLabel: String = "অটোমেশনের নাম"

    override val namePlaceholder: String = "যেমন, সাপ্তাহিক পরিকল্পনার কাজ, সকালের ব্রিফ"

    override val descriptionLabel: String = "বিবরণ (ঐচ্ছিক)"

    override val triggerSectionLabel: String = "কখন"

    override val manualTriggerNote: String = "শুধু নিয়মে চালান চাপলে এটি চলবে।"

    override fun recordTriggerNote(trigger: AutomationTrigger): String = when (trigger) {
        AutomationTrigger.INVOICE_DUE -> "ইনভয়েসের সময় এক সপ্তাহের মধ্যে এলে দৈনিক চেকে এটি চলবে।"
        AutomationTrigger.INVOICE_OVERDUE -> "ইনভয়েস অপরিশোধিত অবস্থায় সময় পেরোলে দৈনিক চেকে এটি চলবে।"
        AutomationTrigger.TASK_DUE -> "কোনো কাজের সময় আজ হলে দৈনিক চেকে এটি চলবে।"
        AutomationTrigger.TASK_OVERDUE -> "কোনো কাজের সময় পেরিয়ে গেলে দৈনিক চেকে এটি চলবে।"
        AutomationTrigger.SUBSCRIPTION_RENEWAL -> "সাবস্ক্রিপশন নবায়ন এক সপ্তাহের মধ্যে এলে দৈনিক চেকে এটি চলবে।"
        AutomationTrigger.PROJECT_DEADLINE -> "প্রকল্পের সময়সীমা দুই সপ্তাহের মধ্যে এলে দৈনিক চেকে এটি চলবে।"
        else -> "রেকর্ডের শর্ত পূরণ হলে দৈনিক চেকে এটি নিজে থেকে চলবে।"
    }

    override val choosePlaceLabel: String = "কোন জায়গা"

    override val noSavedPlacesYet: String =
        "এখনও কোনো জায়গা সংরক্ষিত নেই। আইদা যেটি খেয়াল রাখবে এমন একটি যোগ করে এখানে বাছুন।"

    override val openPlacesAction: String = "একটি জায়গা যোগ করুন"

    override fun placeRadiusNote(radiusMetres: Int): String =
        "এখান থেকে ${bengaliNumerals(radiusMetres.toString())} মিটারের মধ্যে পৌঁছালে চলবে।"

    override fun placeWatchRefused(reason: PlaceWatchRefusal): String = when (reason) {
        PlaceWatchRefusal.PERMISSION_MISSING ->
            "সংরক্ষণ হয়েছে, তবে এই জায়গা খেয়াল রাখতে আইদার অবস্থানের অনুমতি দরকার।"
        PlaceWatchRefusal.BACKGROUND_PERMISSION_MISSING ->
            "সংরক্ষণ হয়েছে, তবে এই জায়গা খেয়াল রাখতে অ্যান্ড্রয়েডে সবসময় অবস্থানের অনুমতি দরকার।"
        PlaceWatchRefusal.LOCATION_DISABLED ->
            "সংরক্ষণ হয়েছে, তবে অবস্থান বন্ধ থাকায় আইদা এই জায়গা খেয়াল রাখতে পারছে না।"
        PlaceWatchRefusal.NO_PROVIDER ->
            "সংরক্ষণ হয়েছে, তবে এই ফোনে আইদার ব্যবহারযোগ্য কোনো অবস্থান প্রোভাইডার নেই।"
        PlaceWatchRefusal.NO_FIX_YET ->
            "সংরক্ষণ হয়েছে, তবে আইদা এই মুহূর্তে আপনি কোথায় আছেন বুঝতে পারেনি।"
        PlaceWatchRefusal.LIMIT_REACHED ->
            "সংরক্ষণ হয়েছে, তবে অ্যান্ড্রয়েড যতগুলো জায়গা রাখতে দেয় আইদা ইতিমধ্যে ততগুলো খেয়াল রাখছে।"
        PlaceWatchRefusal.BATTERY_RESTRICTED ->
            "সংরক্ষণ হয়েছে, তবে অ্যান্ড্রয়েড ব্যাকগ্রাউন্ডে আইদাকে সীমিত করেছে, তাই পৌঁছানো টের না-ও পেতে পারে।"
    }

    override val actionSectionLabel: String = "এটি করো"

    override val actionRunCommand: String = "একটি নির্দেশ চালাও"

    override val actionNotify: String = "আমাকে জানাও"

    override val actionOpenLink: String = "একটি লিংক খোলো"

    override val actionPerformGlobalAction: String = "সিস্টেম জেসচার করো"

    override val actionLaunchApp: String = "একটি অ্যাপ খোলো"

    override val actionDailyBrief: String = "দিনের ব্রিফ পড়ো"

    override val chooseGestureLabel: String = "কোন জেসচার"

    override fun globalActionLabel(action: GlobalDeviceAction): String = when (action) {
        GlobalDeviceAction.BACK -> "পেছনে"
        GlobalDeviceAction.HOME -> "হোম"
        GlobalDeviceAction.RECENTS -> "সাম্প্রতিক অ্যাপ"
        GlobalDeviceAction.NOTIFICATIONS -> "নোটিফিকেশন শেড"
        GlobalDeviceAction.QUICK_SETTINGS -> "কুইক সেটিংস"
    }

    override val chooseAppLabel: String = "কোন অ্যাপ"

    override val noAppsFound: String = "এই ফোনে খোলার মতো কোনো অ্যাপ পাওয়া যায়নি।"

    override val crossAppTierOffNote: String =
        "ক্রস-অ্যাপ অটোমেশন বন্ধ। এটি চলার জন্য সেটিংস → ক্রস-অ্যাপ অটোমেশন থেকে চালু করুন।"

    override val accessibilityOffNote: String =
        "এর জন্য আইদার অ্যাক্সেসিবিলিটি সার্ভিস দরকার, যা বন্ধ। সে শুধু বোতাম চাপতে পারে, আপনার স্ক্রিন কখনও পড়ে না।"

    override val enableAccessibilityAction: String = "অ্যাক্সেসিবিলিটি সার্ভিস চালু করুন"

    override val notificationFromAppLabel: String = "কোন অ্যাপ থেকে"

    override val notificationAnyApp: String = "যেকোনো অ্যাপ"

    override val notificationTitleFilterLabel: String = "শিরোনামে যা আছে (ঐচ্ছিক)"

    override val notificationTitleFilterPlaceholder: String = "যেমন, পেমেন্ট এসেছে"

    override val notificationAccessOffNote: String =
        "এর জন্য নোটিফিকেশন অ্যাক্সেস দরকার, যা বন্ধ। আইদা শুধু এই নিয়ম মেলাতে নোটিফিকেশনের শিরোনাম পড়ে।"

    override val enableNotificationAccessAction: String = "নোটিফিকেশন অ্যাক্সেস চালু করুন"

    override val actionFieldLabel: String = "নির্দেশ"

    override val actionPlaceholder: String = "যেমন, সাপ্তাহিক পরিকল্পনার কাজ তৈরি করো, আমার কাজ দেখাও"

    override val notifyTitleLabel: String = "নোটিফিকেশনের শিরোনাম"

    override val notifyBodyLabel: String = "নোটিফিকেশনের লেখা (ঐচ্ছিক)"

    override val linkLabel: String = "লিংক"

    override val linkPlaceholder: String = "যেমন, https://example.com"

    override val timeLabel: String = "সময়"

    override val daysLabel: String = "এই দিনগুলোতে"

    override val everyDayNote: String = "প্রতিদিন"

    override fun dayLabel(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "সোম"
        DayOfWeek.TUESDAY -> "মঙ্গল"
        DayOfWeek.WEDNESDAY -> "বুধ"
        DayOfWeek.THURSDAY -> "বৃহ"
        DayOfWeek.FRIDAY -> "শুক্র"
        DayOfWeek.SATURDAY -> "শনি"
        DayOfWeek.SUNDAY -> "রবি"
    }

    override val intervalLabel: String = "কত মিনিট পরপর"

    override val intervalPlaceholder: String = "যেমন, ৯০"

    override val timePickerTitle: String = "সময় বাছুন"

    override val setLabel: String = "নির্ধারণ"

    override val cancelLabel: String = "বাতিল"

    override val enableImmediately: String = "নিয়মটি এখনই চালু করুন"

    override val createRuleButton: String = "নিয়ম তৈরি করুন"

    override val saveChangesButton: String = "পরিবর্তন সংরক্ষণ"

    override val runningAutomation: String = "অটোমেশন চালানো হচ্ছে…"

    override fun runFinished(outcome: AutomationOutcome): String =
        "অটোমেশন চালানো শেষ: ${outcomeLabel(outcome)}"

    override fun runFailed(detail: String?): String = "অটোমেশন চালানো ব্যর্থ: $detail"

    override fun automationSaved(name: String): String = "অটোমেশন '$name' সংরক্ষণ হয়েছে"

    override fun saveFailed(detail: String?): String = "সংরক্ষণ করা যায়নি: $detail"

    override fun validationError(detail: String?): String = "যাচাইয়ের ত্রুটি: $detail"

    override val automationDeleted: String = "অটোমেশন মুছে ফেলা হয়েছে"

    override fun deleteFailed(detail: String?): String = "ত্রুটি: $detail"
}

internal fun automationsWording(language: SpokenLanguage): AutomationsWording = when (language) {
    SpokenLanguage.BENGALI -> BengaliAutomationsWording
    SpokenLanguage.ENGLISH -> EnglishAutomationsWording
}
