package com.rayluxeo.aida.ui.mira

import com.rayluxeo.aida.domain.command.MiraCapability

/**
 * Mira's own screen in English: plain, short, and never claiming more than it does.
 *
 * Every label on a prepared action opens something and hands it over — not one of them finishes
 * the act, and the words must not suggest otherwise. "Open the dialler" is the truth; "Call
 * Ahmed" would be a lie told by a button.
 */
internal object EnglishMiraWording : MiraWording {

    override val title: String = "Aida"
    override val conversationHistory: String = "Conversation history"
    override val settings: String = "Settings"
    override val alsoTry: String = "You can also try"
    override val askAnything: String = "Ask Aida anything…"
    override val suggestions: List<MiraSuggestionText> = listOf(
        MiraSuggestionText("Read my brief", "Read my brief"),
        MiraSuggestionText("Add a task", "Add a task to "),
        MiraSuggestionText("Set a reminder", "Remind me to "),
        MiraSuggestionText("What’s due today", "What’s due today"),
    )

    override val listening: String = "Listening"

    override val thinking: String = "Thinking"

    override val carryingOut: String = "Working on it"

    override val speaking: String = "Speaking"

    override val microphoneOff: String = "Microphone off — typing still works"

    override val answeredOnDevice: String = "Answered on-device"

    override val answeredByGemini: String = "Answered by Gemini"
 
    override val listeningStopped: String = "Listening stopped"

    override val tapToSpeak: String = "Tap to speak"




    override val send: String = "Send"

    override val dismiss: String = "Dismiss"

    override val tryAgain: String = "Try again"

    override val allowMicrophone: String = "Allow"

    override val openMicrophoneSettings: String = "Open settings"

    override val confirmYes: String = "Yes, do it"

    override val confirmNo: String = "No"

    override val speakToMira: String = "Speak to Aida"

    override val stopListening: String = "Stop listening"

    override val stopMira: String = "Stop Aida"

    override val speechOff: String =
        "Speech is off — allow the microphone in settings, or type to Aida"

    override val openDialler: String = "Open the dialler"

    override val openMessage: String = "Open the message"

    override val openWhatsApp: String = "Open WhatsApp"

    override val openAppSettings: String = "Open app settings"

    override fun commandResult(commandName: String): String? = when (commandName) {
        "CreateReminder", "CreatePlaceReminder" -> "Reminder set"
        "CreateAlarm" -> "Alarm set"
        "CreateTimer" -> "Timer started"
        "CreateTask" -> "Task added"
        "UpdateTask" -> "Task updated"
        "CompleteTask" -> "Task done"
        "CreateNote" -> "Note saved"
        "RecordPayment" -> "Payment recorded"
        "RecordExpense" -> "Expense recorded"
        "RecordIncome" -> "Income recorded"
        "CreateClient" -> "Client added"
        "CreateCalendarEvent" -> "Event added"
        "UpdateCalendarEvent" -> "Event updated"
        "DeleteCalendarEvent" -> "Event removed"
        "CreateInvoiceDraft" -> "Invoice drafted"
        else -> null
    }

    override fun grant(capability: MiraCapability): String = when (capability) {
        MiraCapability.CONTACTS -> "Allow contacts"
        MiraCapability.CALENDAR -> "Allow the calendar"
        MiraCapability.EXACT_ALARMS -> "Allow exact alarms"
        MiraCapability.NOTIFICATIONS -> "Allow notifications"
        MiraCapability.MICROPHONE -> "Allow the microphone"
    }
}
