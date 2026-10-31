package com.rayluxeo.aida.ui.response

import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.domain.command.MiraCapability
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.model.Contact
import com.rayluxeo.aida.domain.model.ConversationStyle
import com.rayluxeo.aida.domain.model.DailyBrief
import com.rayluxeo.aida.domain.model.DayPart
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.PlaceTrigger
import com.rayluxeo.aida.domain.salah.Prayer
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.Currency

/**
 * Mira's Bengali wording. She speaks to Abu respectfully but not stiffly, in Bengali script and
 * Bengali numerals, and she says what she did rather than how she feels about it.
 */
internal class BengaliPhrasebook(private val times: MiraTimes) : Phrasebook {

    override fun speak(response: MiraResponse, style: ConversationStyle): String = when (response) {
        is MiraResponse.TaskCreated -> taskCreatedSpeak(response, style)

        is MiraResponse.TaskUpdated -> response.dueAt
            ?.let { "বদলে দিয়েছি। ${whenPhrase(it)} ${response.title.forSpeech()}।" }
            ?: "বদলে দিয়েছি — ${response.title.forSpeech()}।"

        is MiraResponse.TaskCompleted -> "হয়ে গেছে — ${response.title.forSpeech()}।"

        is MiraResponse.TasksFound -> listSpeak(response.count, response.titles, TASKS)

        is MiraResponse.ReminderCreated ->
            "${whenPhrase(response.triggerAt)} মনে করিয়ে দেব — ${response.title.forSpeech()}।"

        is MiraResponse.AlarmSet -> response.label.cleaned()
            ?.let { "${alarmMoment(response)} অ্যালার্ম দিয়েছি — ${it.forSpeech()}।" }
            ?: "${alarmMoment(response)} অ্যালার্ম দিয়েছি।"

        is MiraResponse.PlaceReminderCreated -> {
            val place = response.placeLabel.forSpeech()
            val title = response.title.forSpeech()
            if (response.isWatching) {
                "${placeClause(place, response.trigger)} মনে করিয়ে দেব — $title।"
            } else {
                // No promise here, deliberately. A reminder nothing is waiting on reads exactly
                // like an armed one, and this is the only place Abu could be told the difference.
                "রেখে দিয়েছি — $title। তবে $place-এর উপর এখনো নজর রাখতে পারছি না, " +
                    "তাই এটি নিজে থেকে আসবে না।"
            }
        }

        is MiraResponse.PlaceUnknown -> {
            val label = response.placeLabel.forSpeech()
            if (response.known.isEmpty()) {
                "$label নামে কোনো জায়গা নেই। সেটিংসে একটি যোগ করলে কাজে লাগাতে পারব।"
            } else {
                "$label নামে কোনো জায়গা নেই। আমি চিনি ${placesSpeak(response.known)}।"
            }
        }

        is MiraResponse.TimerStarted -> {
            val length = lengthPhrase(response.duration)
            val ends = whenPhrase(response.endsAt)
            response.label.cleaned()
                ?.let { "${it.forSpeech()} — $length টাইমার চালু করেছি, শেষ হবে $ends।" }
                ?: "$length টাইমার চালু করেছি, শেষ হবে $ends।"
        }

        is MiraResponse.BriefReady ->
            "${greeting(response.brief.dayPart)} ${taskSentence(response.brief)}${reminderSentence(response.brief)}"

        is MiraResponse.NoteCreated -> response.title.cleaned()
            ?.let { "নোট রেখেছি — ${it.forSpeech()}।" }
            ?: "নোট রেখেছি।"

        is MiraResponse.NotesFound -> listSpeak(response.count, response.titles, NOTES)

        is MiraResponse.MemoryStored ->
            "মনে রাখলাম — ${response.subject.forSpeech()}: ${response.fact.forSpeech()}।"

        is MiraResponse.MemoryForgotten ->
            "তথ্য মুছে ফেলা হয়েছে।"

        is MiraResponse.MemoriesFound -> {
            val spoken = response.facts.take(SPOKEN_TITLE_COUNT).map { it.forSpeech() }
            when {
                response.count == 0 -> "এ বিষয়ে কিছু মনে নেই।"
                spoken.isEmpty() && response.count == 1 -> "একটি বিষয় মনে আছে।"
                spoken.isEmpty() -> "${digits(response.count)}টি বিষয় মনে আছে।"
                response.count == 1 -> "মনে আছে — ${spoken.first()}।"
                else -> "${digits(response.count)}টি মনে আছে — ${joinWithO(spoken)}।"
            }
        }

        is MiraResponse.UniversalSearchResultsFound ->
            if (response.totalCount == 0) "কোনো তথ্য পাওয়া যায়নি।"
            else "মোট ${digits(response.totalCount)}টি ফলাফল পাওয়া গেছে।"

        is MiraResponse.ClientCreated -> "ক্লায়েন্ট যোগ করেছি — ${response.name.forSpeech()}।"

        is MiraResponse.ClientsFound -> listSpeak(response.count, response.names, CLIENTS)

        is MiraResponse.InvoiceDraftCreated ->
            "ইনভয়েস ড্রাফট ${response.number.trim()} — " +
                "${response.clientName.forSpeech()}, ${money(response.total)}।"

        is MiraResponse.QuoteDraftCreated ->
            "কোটেশন ড্রাফট ${response.number.trim()} — " +
                "${response.clientName.forSpeech()}, ${money(response.total)}।"

        is MiraResponse.RecurringInvoiceScheduled ->
            "নিয়মিত ইনভয়েস শিডিউল করা হয়েছে — ${response.invoiceNumber}।"

        is MiraResponse.PaymentRecorded -> {
            val client = response.clientName.cleaned()
            val invoice = response.invoiceNumber.cleaned()
            when {
                client != null ->
                    "পেমেন্ট লিখে রেখেছি — ${client.forSpeech()} থেকে ${money(response.amount)}।"
                invoice != null ->
                    "পেমেন্ট লিখে রেখেছি — $invoice-এর ${money(response.amount)}।"
                else -> "পেমেন্ট লিখে রেখেছি — ${money(response.amount)}।"
            }
        }

        is MiraResponse.InvoiceDraftRequested ->
            "${response.clientName.forSpeech()}-এর জন্য একটি ইনভয়েস ড্রাফট করব" +
                (response.amount?.let { " — ${money(it)}" } ?: "") +
                (response.description.cleaned()?.let { ", ${it.forSpeech()}-এর জন্য" } ?: "") + "।"

        is MiraResponse.QuoteDraftRequested ->
            "${response.clientName.forSpeech()}-এর জন্য একটি কোটেশন ড্রাফট করব" +
                (response.amount?.let { " — ${money(it)}" } ?: "") +
                (response.description.cleaned()?.let { ", ${it.forSpeech()}-এর জন্য" } ?: "") + "।"

        is MiraResponse.PaymentByPercentageRequested -> {
            val client = response.clientName.cleaned()
            val invoice = response.invoiceNumber.cleaned()
            val share = "${bengaliNumerals(response.percentage.stripTrailingZeros().toPlainString())}%"
            when {
                invoice != null && client != null -> "$invoice-এর $share ${client.forSpeech()} থেকে লিখব।"
                invoice != null -> "$invoice-এর $share লিখব।"
                client != null -> "${client.forSpeech()}-এর ইনভয়েসের $share লিখব।"
                else -> "ইনভয়েসের $share লিখব।"
            }
        }

        is MiraResponse.ExpenseRecorded -> expenseRecordedSpeak(response, style)

        is MiraResponse.IncomeRecorded -> incomeRecordedSpeak(response, style)

        is MiraResponse.CalculationResult -> "${bengaliNumerals(response.value)} হয়।"

        is MiraResponse.ExpenseSummaryReport -> expenseSummarySpeak(response, style)

        is MiraResponse.IncomeSummaryReport -> incomeSummarySpeak(response, style)

        is MiraResponse.SubscriptionRenewalInfo ->
            "${response.name.forSpeech()} পরবর্তী রিনিউয়াল ${response.nextChargeOn}, ${money(response.amount)}।"

        is MiraResponse.PrayerMarked -> {
            val day = response.onDate
                ?.takeIf { times.offsetOf(it) != DayOffset.TODAY }
                ?.let { "${dayWordForDate(it)} " }
                ?: ""
            "$day${prayerName(response.prayer)} পড়েছেন বলে চিহ্নিত করলাম।"
        }

        is MiraResponse.NextPrayerReport -> {
            val base = "পরবর্তী নামাজ ${prayerName(response.prayer)}, ${atPhrase(response.at)}।"
            if (response.remaining.isZero || response.remaining.isNegative) base
            else "$base আর ${lengthPhrase(response.remaining)} বাকি।"
        }

        is MiraResponse.PrayerTimeRemainingReport ->
            if (response.remaining.isZero || response.remaining.isNegative) {
                "এখন ${prayerName(response.prayer)}ের সময়।"
            } else {
                "${prayerName(response.prayer)} আর ${lengthPhrase(response.remaining)} বাকি।"
            }

        is MiraResponse.PrayerTimeReport ->
            "${prayerName(response.prayer)} ${whenPhrase(response.at)}।"

        is MiraResponse.PrayerTrackingReport -> prayerTrackingSpeak(response)

        is MiraResponse.TahajjudReminderSet -> response.at
            ?.let { "তাহাজ্জুদের রিমাইন্ডার দিয়েছি। শেষ তৃতীয়াংশ ${clockPhrase(times.at(it))} শুরু হবে।" }
            ?: "তাহাজ্জুদের রিমাইন্ডার দিয়েছি — রাতের শেষ তৃতীয়াংশে।"

        is MiraResponse.InvoiceOutstandingReport -> invoiceOutstandingSpeak(response, style)

        is MiraResponse.ClientStatementReport -> clientStatementSpeak(response.statement, style)

        is MiraResponse.InvoiceDeliveryPrepared -> "ইনভয়েস ড্রাফট তৈরি করা হয়েছে।"

        is MiraResponse.AmbiguousInvoice -> {
            val client = response.clientName?.forSpeech()
            val candidates = response.candidateNumbers.joinToString(" নাকি ")
            if (client != null) "${client}-এর কোন ইনভয়েস: $candidates?"
            else "কোন ইনভয়েস: $candidates?"
        }

        is MiraResponse.NewConversationStarted -> "নতুন কথোপকথন শুরু হয়েছে।"
        is MiraResponse.ConversationDeleted -> "কথোপকথন মুছে ফেলা হয়েছে।"
        is MiraResponse.AllConversationsDeleted -> "সব কথোপকথন মুছে ফেলা হয়েছে।"
        is MiraResponse.ConversationsFound -> {
            if (response.count == 0) "কোনো কথোপকথন পাওয়া যায়নি।"
            else "${digits(response.count)}টি কথোপকথন পাওয়া গেছে।"
        }

        is MiraResponse.CalendarEventCreated -> {
            val conflict = if (response.conflicts.isNotEmpty()) {
                " সতর্কবার্তা: ${response.conflicts.joinToString(", ") { it.forSpeech() }}-এর সাথে সময় মিলে যাচ্ছে।"
            } else ""
            "ক্যালেন্ডারে রেখেছি। ${whenPhrase(response.startsAt)} ${response.title.forSpeech()}।$conflict"
        }

        is MiraResponse.ScheduleReport -> {
            if (response.events.isEmpty()) {
                "কোনো শিডিউল বা ইভেন্ট নেই।"
            } else {
                val list = response.events.take(SPOKEN_TITLE_COUNT).map {
                    "${it.title.forSpeech()} (${clockPhrase(times.at(it.startsAt))})"
                }
                "${digits(response.events.size)}টি ইভেন্ট: ${joinWithO(list)}।"
            }
        }

        is MiraResponse.FreeSlotsReport -> {
            if (response.slots.isEmpty()) {
                "কাজের সময়ে কোনো ফাঁকা সময় নেই।"
            } else {
                val list = response.slots.take(SPOKEN_TITLE_COUNT).map {
                    "${clockPhrase(times.at(it.startsAt))} থেকে ${clockPhrase(times.at(it.endsAt))}"
                }
                "ফাঁকা সময়: ${joinWithO(list)}।"
            }
        }

        is MiraResponse.CalendarEventUpdated ->
            "ক্যালেন্ডার ইভেন্ট পরিবর্তন করেছি — ${response.title.forSpeech()}, ${whenPhrase(response.startsAt)}।"

        is MiraResponse.CalendarEventDeleted ->
            "ক্যালেন্ডার থেকে মুছে ফেলেছি — ${response.title.forSpeech()}।"

        is MiraResponse.ContactsFound ->
            listSpeak(response.count, response.contacts.map { it.displayName }, CONTACTS)

        is MiraResponse.ContactDetails -> {
            val name = response.contact.displayName.forSpeech()
            when (response.requestedField) {
                com.rayluxeo.aida.domain.model.ContactField.PHONE -> response.contact.phoneNumber.cleaned()
                    ?.let { "$name-এর ফোন নম্বর $it।" }
                    ?: "$name-এর কোনো ফোন নম্বর পাইনি।"
                com.rayluxeo.aida.domain.model.ContactField.EMAIL -> response.contact.email.cleaned()
                    ?.let { "$name-এর ইমেইল $it।" }
                    ?: "$name-এর কোনো ইমেইল পাইনি।"
                com.rayluxeo.aida.domain.model.ContactField.ANY -> {
                    val phone = response.contact.phoneNumber.cleaned()
                    val email = response.contact.email.cleaned()
                    when {
                        phone != null && email != null -> "$name: ফোন $phone, ইমেইল $email।"
                        phone != null -> "$name: ফোন $phone।"
                        email != null -> "$name: ইমেইল $email।"
                        else -> "$name-এর কোনো তথ্য সংরক্ষিত নেই।"
                    }
                }
            }
        }

        is MiraResponse.CallPrepared -> {
            val name = response.contact.displayName.forSpeech()
            if (response.contact.isCallable) "$name-কে কল করতে প্রস্তুত।"
            else "$name আছে, কিন্তু নম্বর নেই।"
        }

        is MiraResponse.MessagePrepared -> {
            val name = response.contact.displayName.forSpeech()
            if (response.app == com.rayluxeo.aida.domain.model.MessagingApp.WHATSAPP) {
                if (response.body.cleaned() != null) "$name-এর জন্য হোয়াটসঅ্যাপ মেসেজ তৈরি।"
                else "$name-কে হোয়াটসঅ্যাপ মেসেজ পাঠাতে প্রস্তুত।"
            } else {
                if (response.body.cleaned() != null) "$name-এর মেসেজ তৈরি।"
                else "$name-কে মেসেজ পাঠাতে প্রস্তুত।"
            }
        }

        is MiraResponse.AmbiguousTask ->
            ambiguousSpeak(response.candidates, "কোন কাজের কথা বলছেন?", "কোনটি")

        is MiraResponse.AmbiguousClient ->
            ambiguousSpeak(response.candidates, "কোন ক্লায়েন্টের কথা বলছেন?", "কোনজন")

        is MiraResponse.AmbiguousContact -> ambiguousSpeak(
            candidates = response.candidates.map { it.displayName },
            ask = "কোন কন্টাক্টের কথা বলছেন?",
            opener = "কোনজন",
        )

        is MiraResponse.TaskDeleted ->
            "মুছে ফেলা হয়েছে — ${response.title.forSpeech()}। ভুল হলে কাজ থেকে ফিরিয়ে আনুন।"
        is MiraResponse.RemindersFound -> listSpeak(response.count, response.titles, REMINDERS)
        is MiraResponse.ReminderSnoozed -> "পিছিয়ে দিলাম — ${response.title.forSpeech()}।"
        is MiraResponse.ReminderCancelled -> "রিমাইন্ডার বাতিল — ${response.title.forSpeech()}।"
        is MiraResponse.FactForgotten -> "ভুলে গেলাম — ${response.subject.forSpeech()}।"
        is MiraResponse.AmbiguousReminder ->
            ambiguousSpeak(response.candidates, "কোন রিমাইন্ডারের কথা বলছেন?", "কোনটি")
        is MiraResponse.AmbiguousFact ->
            ambiguousSpeak(response.facts, "কোন তথ্যের কথা বলছেন?", "কোনটি")

        is MiraResponse.PermissionNeeded -> permission(response.capability)

        MiraResponse.Abandoned -> "ঠিক আছে, বাদ দিলাম।"
        MiraResponse.Greeting -> greetingLine(style)
        is MiraResponse.Conversational -> response.answer
        is MiraResponse.NotUnderstood -> "ঠিক বুঝতে পারিনি — আবার বলবেন?"

        is MiraResponse.Problem -> problem(response.code)
    }

    override fun display(response: MiraResponse, style: ConversationStyle): String = when (response) {
        is MiraResponse.TaskCreated -> buildString {
            append("যোগ করেছি: ${response.title.trim()}")
            response.dueAt?.let { append("\nসময়: ${whenPhrase(it)}") }
            if (response.repeats) append("\nনিয়মিত")
        }

        is MiraResponse.TaskUpdated -> response.dueAt
            ?.let { "বদলে দিয়েছি: ${response.title.trim()}\nসময়: ${whenPhrase(it)}" }
            ?: "বদলে দিয়েছি: ${response.title.trim()}"

        is MiraResponse.TaskCompleted -> "সম্পন্ন: ${response.title.trim()}"

        is MiraResponse.TasksFound -> listDisplay(response.count, response.titles, TASKS)

        is MiraResponse.ReminderCreated ->
            "রিমাইন্ডার: ${response.title.trim()}\nসময়: ${whenPhrase(response.triggerAt)}"

        is MiraResponse.AlarmSet -> buildString {
            append(response.label.cleaned()?.let { "অ্যালার্ম: $it" } ?: "অ্যালার্ম দিয়েছি")
            val repeat = repeatPhrase(response.repeatDays, BengaliRepeatWording)
            if (repeat == null) {
                append("\nসময়: ${whenPhrase(response.triggerAt)}")
            } else {
                append("\nসময়: ${clockPhrase(times.at(response.triggerAt))}")
                append("\nপুনরাবৃত্তি: $repeat")
            }
        }

        is MiraResponse.PlaceReminderCreated -> buildString {
            append("জায়গার রিমাইন্ডার: ${response.title.trim()}")
            append("\nকখন: ${placeClause(response.placeLabel.trim(), response.trigger)}")
            if (!response.isWatching) {
                append("\nএখনো নজর রাখা যাচ্ছে না — অ্যান্ড্রয়েড এই জায়গাটি খেয়াল রাখতে দেয়নি, ")
                append("তাই এটি নিজে থেকে আসবে না। রিমাইন্ডার ট্যাবে কারণ লেখা আছে।")
            }
        }

        is MiraResponse.PlaceUnknown -> buildString {
            append("\"${response.placeLabel.trim()}\" নামে কোনো জায়গা আমার কাছে নেই।")
            if (response.known.isEmpty()) {
                append("\nএখনো কোনো জায়গা রাখা হয়নি — সেটিংস থেকে যোগ করতে পারেন।")
            } else {
                append("\nযেগুলো চিনি:")
                response.known.forEach { append("\n• ${it.trim()}") }
            }
        }

        is MiraResponse.TimerStarted -> buildString {
            val label = response.label.cleaned()
            if (label == null) {
                append("টাইমার: ${lengthPhrase(response.duration)}")
            } else {
                append("টাইমার: $label")
                append("\nসময়কাল: ${lengthPhrase(response.duration)}")
            }
            append("\nশেষ: ${whenPhrase(response.endsAt)}")
        }

        is MiraResponse.BriefReady -> briefDisplay(response.brief)

        is MiraResponse.NoteCreated -> response.title.cleaned()
            ?.let { "নোট: $it" }
            ?: "নোট রেখেছি।"

        is MiraResponse.NotesFound -> listDisplay(response.count, response.titles, NOTES)

        is MiraResponse.MemoryStored ->
            "মনে রাখলাম: ${response.subject.trim()}\n${response.fact.trim()}"

        is MiraResponse.MemoryForgotten ->
            "তথ্য মুছে ফেলা হয়েছে।"

        is MiraResponse.MemoriesFound -> memoriesDisplay(response)

        is MiraResponse.UniversalSearchResultsFound ->
            "ফলাফল (${digits(response.totalCount)}টি):\n${response.topSnippets.joinToString("\n")}"

        is MiraResponse.ClientCreated -> "ক্লায়েন্ট: ${response.name.trim()}"

        is MiraResponse.ClientsFound -> listDisplay(response.count, response.names, CLIENTS)

        is MiraResponse.InvoiceDraftCreated ->
            "ইনভয়েস ড্রাফট ${response.number.trim()}\n" +
                "ক্লায়েন্ট: ${response.clientName.trim()}\n" +
                "মোট: ${money(response.total)}"

        is MiraResponse.QuoteDraftCreated ->
            "কোটেশন ড্রাফট ${response.number.trim()}\n" +
                "ক্লায়েন্ট: ${response.clientName.trim()}\n" +
                "মোট: ${money(response.total)}"

        is MiraResponse.RecurringInvoiceScheduled ->
            "নিয়মিত ইনভয়েস শিডিউল\n" +
                "ইনভয়েস: ${response.invoiceNumber}"

        is MiraResponse.PaymentRecorded -> buildString {
            append("পেমেন্ট: ${money(response.amount)}")
            response.clientName.cleaned()?.let { append("\nক্লায়েন্ট: $it") }
            response.invoiceNumber.cleaned()?.let { append("\nইনভয়েস: $it") }
        }

        is MiraResponse.InvoiceDraftRequested -> buildString {
            append("ইনভয়েস ড্রাফট\nক্লায়েন্ট: ${response.clientName.trim()}")
            response.amount?.let { append("\nমোট: ${money(it)}") }
            response.description.cleaned()?.let { append("\nবিবরণ: $it") }
        }

        is MiraResponse.QuoteDraftRequested -> buildString {
            append("কোটেশন ড্রাফট\nক্লায়েন্ট: ${response.clientName.trim()}")
            response.amount?.let { append("\nমোট: ${money(it)}") }
            response.description.cleaned()?.let { append("\nবিবরণ: $it") }
        }

        is MiraResponse.PaymentByPercentageRequested -> buildString {
            append("পেমেন্ট: ${bengaliNumerals(response.percentage.stripTrailingZeros().toPlainString())}%")
            response.invoiceNumber.cleaned()?.let { append("\nইনভয়েস: $it") }
            response.clientName.cleaned()?.let { append("\nক্লায়েন্ট: $it") }
        }

        is MiraResponse.ExpenseRecorded -> response.description.cleaned()
            ?.let { "খরচ: ${money(response.amount)}\nবিবরণ: $it" }
            ?: "খরচ: ${money(response.amount)}"

        is MiraResponse.IncomeRecorded -> response.source.cleaned()
            ?.let { "আয়: ${money(response.amount)}\nউৎস: $it" }
            ?: "আয়: ${money(response.amount)}"

        // The expression stays exactly as Abu gave it — arithmetic notation is not language, and
        // rewriting his own digits back at him makes the line harder to check, not easier.
        is MiraResponse.CalculationResult -> "${response.expression} = ${response.value}"

        is MiraResponse.ExpenseSummaryReport -> expenseSummaryDisplay(response, style)

        is MiraResponse.IncomeSummaryReport -> incomeSummaryDisplay(response, style)

        is MiraResponse.SubscriptionRenewalInfo ->
            "সাবস্ক্রিপশন: ${response.name.trim()}\nপরবর্তী রিনিউয়াল: ${response.nextChargeOn}\nপরিমাণ: ${money(response.amount)}"

        is MiraResponse.PrayerMarked -> response.onDate
            ?.takeIf { times.offsetOf(it) != DayOffset.TODAY }
            ?.let { "পড়া হয়েছে: ${prayerName(response.prayer)} (${dayWordForDate(it)})" }
            ?: "পড়া হয়েছে: ${prayerName(response.prayer)}"

        is MiraResponse.NextPrayerReport -> buildString {
            append("পরবর্তী নামাজ: ${prayerName(response.prayer)}")
            append("\nসময়: ${whenPhrase(response.at)}")
            if (!response.remaining.isZero && !response.remaining.isNegative) {
                append("\nবাকি: ${lengthPhrase(response.remaining)}")
            }
        }

        is MiraResponse.PrayerTimeRemainingReport -> buildString {
            append(prayerName(response.prayer))
            if (response.remaining.isZero || response.remaining.isNegative) append(": এখন")
            else append(": আর ${lengthPhrase(response.remaining)}")
            append("\nসময়: ${whenPhrase(response.at)}")
        }

        is MiraResponse.PrayerTimeReport ->
            "${prayerName(response.prayer)}: ${whenPhrase(response.at)}"

        is MiraResponse.PrayerTrackingReport -> prayerTrackingDisplay(response)

        is MiraResponse.TahajjudReminderSet -> response.at
            ?.let { "তাহাজ্জুদের রিমাইন্ডার\nশেষ তৃতীয়াংশ শুরু: ${whenPhrase(it)}" }
            ?: "তাহাজ্জুদের রিমাইন্ডার দিয়েছি।"

        is MiraResponse.InvoiceOutstandingReport -> invoiceOutstandingDisplay(response, style)

        is MiraResponse.ClientStatementReport -> clientStatementDisplay(response.statement, style)

        is MiraResponse.InvoiceDeliveryPrepared -> "ইনভয়েস ড্রাফট তৈরি করা হয়েছে।"

        is MiraResponse.AmbiguousInvoice -> {
            val client = response.clientName?.trim()
            val candidates = response.candidateNumbers.joinToString(" নাকি ")
            if (client != null) "${client}-এর কোন ইনভয়েস: $candidates?"
            else "কোন ইনভয়েস: $candidates?"
        }

        is MiraResponse.NewConversationStarted -> "নতুন কথোপকথন শুরু হয়েছে।"
        is MiraResponse.ConversationDeleted -> "কথোপকথন মুছে ফেলা হয়েছে।"
        is MiraResponse.AllConversationsDeleted -> "সব কথোপকথন মুছে ফেলা হয়েছে।"
        is MiraResponse.ConversationsFound -> {
            if (response.count == 0) "কোনো কথোপকথন পাওয়া যায়নি।"
            else "${digits(response.count)}টি কথোপকথন পাওয়া গেছে।"
        }

        is MiraResponse.CalendarEventCreated -> buildString {
            append("ক্যালেন্ডার: ${response.title.trim()}\nসময়: ${whenPhrase(response.startsAt)}")
            if (response.conflicts.isNotEmpty()) {
                append("\nসতর্কবার্তা: ${response.conflicts.joinToString(", ")} এর সাথে সময় মিলে যাচ্ছে")
            }
        }

        is MiraResponse.ScheduleReport -> buildString {
            append("শিডিউল:")
            if (response.events.isEmpty()) {
                append(" কোনো ইভেন্ট নেই।")
            } else {
                response.events.forEach { event ->
                    val end = event.endsAt?.let { " - ${clockPhrase(times.at(it))}" } ?: ""
                    append("\n• ${event.title.trim()} (${clockPhrase(times.at(event.startsAt))}$end)")
                }
            }
        }

        is MiraResponse.FreeSlotsReport -> buildString {
            append("ফাঁকা সময়:")
            if (response.slots.isEmpty()) {
                append(" কোনো ফাঁকা সময় নেই।")
            } else {
                response.slots.forEach { slot ->
                    append("\n• ${clockPhrase(times.at(slot.startsAt))} - ${clockPhrase(times.at(slot.endsAt))}")
                }
            }
        }

        is MiraResponse.CalendarEventUpdated ->
            "পরিবর্তন করা হয়েছে: ${response.title.trim()}\nসময়: ${whenPhrase(response.startsAt)}"

        is MiraResponse.CalendarEventDeleted ->
            "ক্যালেন্ডার থেকে মোছা হয়েছে: ${response.title.trim()}"

        is MiraResponse.ContactsFound ->
            listDisplay(response.count, response.contacts.map { contactLine(it) }, CONTACTS)

        is MiraResponse.ContactDetails -> buildString {
            append("কন্টাক্ট: ${response.contact.displayName.trim()}")
            response.contact.phoneNumber.cleaned()?.let { append("\nফোন: $it") }
            response.contact.email.cleaned()?.let { append("\nইমেইল: $it") }
            if (response.contact.phoneNumber.cleaned() == null && response.contact.email.cleaned() == null) {
                append("\nকোনো তথ্য সংরক্ষিত নেই।")
            }
        }

        is MiraResponse.CallPrepared -> {
            val contact = response.contact
            "কল: ${contact.displayName.trim()}\n${contact.phoneNumber.cleaned() ?: "নম্বর নেই।"}"
        }

        is MiraResponse.MessagePrepared -> buildString {
            if (response.app == com.rayluxeo.aida.domain.model.MessagingApp.WHATSAPP) {
                append("হোয়াটসঅ্যাপ: ${response.contact.displayName.trim()}")
            } else {
                append("মেসেজ: ${response.contact.displayName.trim()}")
            }
            response.body.cleaned()?.let { append("\n\"$it\"") }
        }

        is MiraResponse.AmbiguousTask -> ambiguousDisplay(
            query = response.title,
            candidates = response.candidates,
            noun = "কাজ",
            ask = "কোন কাজের কথা বলছেন?",
            closing = "কোনটি?",
        )

        is MiraResponse.AmbiguousClient -> ambiguousDisplay(
            query = response.query,
            candidates = response.candidates,
            noun = "ক্লায়েন্ট",
            ask = "কোন ক্লায়েন্টের কথা বলছেন?",
            closing = "কোনজন?",
        )

        is MiraResponse.AmbiguousContact -> ambiguousDisplay(
            query = response.query,
            candidates = response.candidates.map { contactLine(it) },
            noun = "কন্টাক্ট",
            ask = "কোন কন্টাক্টের কথা বলছেন?",
            closing = "কোনজন?",
        )

        is MiraResponse.TaskDeleted -> "মুছে ফেলা হয়েছে: ${response.title.trim()}"
        is MiraResponse.RemindersFound -> listDisplay(response.count, response.titles, REMINDERS)
        is MiraResponse.ReminderSnoozed -> "পিছিয়ে দেওয়া হয়েছে: ${response.title.trim()}"
        is MiraResponse.ReminderCancelled -> "রিমাইন্ডার বাতিল: ${response.title.trim()}"
        is MiraResponse.FactForgotten -> "ভুলে ফেলা হয়েছে: ${response.subject.trim()}"
        is MiraResponse.AmbiguousReminder -> ambiguousDisplay(
            query = response.query,
            candidates = response.candidates,
            noun = "রিমাইন্ডার",
            ask = "কোন রিমাইন্ডারের কথা বলছেন?",
            closing = "কোনটি?",
        )
        is MiraResponse.AmbiguousFact -> ambiguousDisplay(
            query = response.query,
            candidates = response.facts,
            noun = "তথ্য",
            ask = "কোন তথ্যের কথা বলছেন?",
            closing = "কোনটি?",
        )

        is MiraResponse.PermissionNeeded ->
            "${permission(response.capability)}\n${permissionNextStep(response.capability)}"

        MiraResponse.Abandoned -> "ঠিক আছে, বাদ দিলাম।"
        MiraResponse.Greeting -> greetingLine(style)
        is MiraResponse.Conversational -> response.answer
        is MiraResponse.NotUnderstood -> response.input.trim()
            .takeIf { it.isNotEmpty() }
            ?.let { "ঠিক বুঝতে পারিনি।\nযা শুনেছি: \"${it.clipped(ECHO_LIMIT)}\"" }
            ?: "ঠিক বুঝতে পারিনি।"

        is MiraResponse.Problem -> problemDisplay(response.code)
    }

    /**
     * A repeating alarm is read back by its clock time and its repeat. Naming the next day as
     * well would be true and useless — "আগামীকাল সকাল ৭টায় প্রতিদিন" says nothing the repeat has
     * not already said, and it invites Abu to read it as a one-off.
     */
    private fun alarmMoment(response: MiraResponse.AlarmSet): String =
        repeatPhrase(response.repeatDays, BengaliRepeatWording)
            ?.let { "$it ${clockPhrase(times.at(response.triggerAt))}" }
            ?: whenPhrase(response.triggerAt)

    /**
     * Bengali marks arriving and leaving on the place itself rather than with a separate verb, so
     * the whole clause is built here instead of splicing a word in at the call site.
     */
    private fun placeClause(place: String, trigger: PlaceTrigger): String = when (trigger) {
        PlaceTrigger.ENTER -> "$place-এ পৌঁছালে"
        PlaceTrigger.EXIT -> "$place থেকে বের হলে"
    }

    /**
     * The saved places, as far as one sentence will carry them. Past the second, the count is
     * said rather than the names — the same line is already carrying the name Mira did not know.
     */
    private fun placesSpeak(known: List<String>): String {
        val shown = known.take(SPOKEN_PLACE_COUNT).map { it.forSpeech() }
        val remaining = known.size - shown.size
        return if (remaining > 0) {
            "${shown.joinToString(", ")} ও আরও ${digits(remaining)}টি"
        } else {
            joinWithO(shown)
        }
    }

    private fun listSpeak(count: Int, items: List<String>, counted: Counted): String {
        val spoken = items.take(SPOKEN_TITLE_COUNT).map { it.forSpeech() }
        return when {
            count == 0 -> counted.none
            spoken.isEmpty() && count == 1 -> "${counted.singular} ${counted.noun} আছে।"
            spoken.isEmpty() -> "${digits(count)}${counted.classifier} ${counted.noun} আছে।"
            count == 1 -> "${counted.singular} ${counted.noun} — ${spoken.first()}।"
            else -> "${digits(count)}${counted.classifier} ${counted.noun} — ${joinWithO(spoken)}।"
        }
    }

    private fun listDisplay(count: Int, items: List<String>, counted: Counted): String {
        if (count == 0) return counted.none
        val shown = items.take(SPOKEN_TITLE_COUNT)
        return buildString {
            append("${digits(count)}${counted.classifier} ${counted.noun}")
            shown.forEach { append("\n• ${it.trim()}") }
            val remaining = count - shown.size
            if (remaining > 0) append("\nআরও ${digits(remaining)}${counted.classifier}")
        }
    }

    private fun memoriesDisplay(response: MiraResponse.MemoriesFound): String {
        if (response.count == 0) return "এ বিষয়ে কিছু মনে নেই।"
        val shown = response.facts.take(SPOKEN_TITLE_COUNT)
        return buildString {
            append("মনে আছে:")
            shown.forEach { append("\n• ${it.trim()}") }
            val remaining = response.count - shown.size
            if (remaining > 0) append("\nআরও ${digits(remaining)}টি")
        }
    }

    private fun ambiguousSpeak(candidates: List<String>, ask: String, opener: String): String {
        val spoken = candidates.take(SPOKEN_TITLE_COUNT).map { it.forSpeech() }
        return if (spoken.isEmpty()) ask else "$opener — ${joinWithNaki(spoken)}?"
    }

    /** Naming the candidates is Mira declining to guess, so every one of them is listed. */
    private fun ambiguousDisplay(
        query: String,
        candidates: List<String>,
        noun: String,
        ask: String,
        closing: String,
    ): String {
        if (candidates.isEmpty()) return ask
        val trimmed = query.trim()
        val header =
            if (trimmed.isEmpty()) "একাধিক $noun মিলে গেছে:"
            else "\"$trimmed\" নামে একাধিক $noun আছে:"
        return buildString {
            append(header)
            candidates.forEach { append("\n• ${it.trim()}") }
            append("\n$closing")
        }
    }

    private fun briefDisplay(brief: DailyBrief): String = buildString {
        append(greeting(brief.dayPart))
        val counts = buildList {
            if (brief.overdueCount > 0) add("সময় পেরিয়েছে: ${digits(brief.overdueCount)}টি")
            if (brief.dueTodayCount > 0) add("আজকের কাজ: ${digits(brief.dueTodayCount)}টি")
            if (brief.openTaskCount > 0) add("বাকি আছে: ${digits(brief.openTaskCount)}টি")
            if (brief.completedTodayCount > 0) add("আজ শেষ: ${digits(brief.completedTodayCount)}টি")
        }
        append('\n')
        append(if (counts.isEmpty()) "আজ নির্দিষ্ট কোনো কাজ নেই।" else counts.joinToString(" · "))
        val title = brief.nextReminderTitle?.trim()?.takeIf { it.isNotEmpty() }
        val at = brief.nextReminderAt
        if (title != null && at != null) {
            append("\nপরের রিমাইন্ডার: $title, ${whenPhrase(at)}")
        }
    }

    /**
     * NIGHT shares EVENING's greeting: "শুভ রাত্রি" is what you say on the way out, so Mira does
     * not open a brief with it.
     */
    private fun greeting(part: DayPart): String = when (part) {
        DayPart.MORNING -> "শুভ সকাল।"
        DayPart.AFTERNOON -> "শুভ দুপুর।"
        DayPart.EVENING -> "শুভ সন্ধ্যা।"
        DayPart.NIGHT -> "শুভ সন্ধ্যা।"
    }

    /**
     * আইদা আবুকে অভিবাদন ফিরিয়ে দেয় — দিনের সময়, তার নাম, আর শুরু করার আমন্ত্রণ।
     *
     * [ConversationStyle.CONCISE] সময় আর নাম রাখে, আমন্ত্রণটি বাদ দেয়; অভিবাদনে সত্যি যোগ করার মতো
     * বাড়তি কিছু নেই, তাই DETAILED আর BALANCED একইভাবে পড়ে।
     */
    private fun greetingLine(style: ConversationStyle): String {
        val opener = "${greeting(times.currentDayPart()).removeSuffix("।")}, আবু"
        return when (style) {
            ConversationStyle.CONCISE -> "${opener}।"
            ConversationStyle.BALANCED, ConversationStyle.DETAILED -> "${opener}। বলুন, কী করতে পারি।"
        }
    }

    // CONCISE "যোগ করেছি" ভূমিকাটি বাদ দেয়; কাজের নাম আর তার সময় — যেগুলো তথ্য — সব ধরনে থাকে।
    private fun taskCreatedSpeak(response: MiraResponse.TaskCreated, style: ConversationStyle): String {
        val title = response.title.forSpeech()
        val due = response.dueAt
        return when (style) {
            ConversationStyle.CONCISE -> when {
                due != null && response.repeats -> "${whenPhrase(due)} থেকে ${title}, নিয়মিত।"
                due != null -> "${whenPhrase(due)} ${title}।"
                response.repeats -> "${title}, নিয়মিত।"
                else -> "${title}।"
            }
            ConversationStyle.BALANCED, ConversationStyle.DETAILED -> when {
                due != null && response.repeats -> "যোগ করেছি। ${whenPhrase(due)} থেকে ${title}, নিয়মিত।"
                due != null -> "যোগ করেছি। ${whenPhrase(due)} ${title}।"
                response.repeats -> "যোগ করেছি — ${title}, নিয়মিত।"
                else -> "যোগ করেছি — ${title}।"
            }
        }
    }

    private fun incomeRecordedSpeak(response: MiraResponse.IncomeRecorded, style: ConversationStyle): String {
        val amount = money(response.amount)
        val source = response.source.cleaned()?.forSpeech()
        return when (style) {
            ConversationStyle.CONCISE -> if (source != null) "${source} থেকে ${amount}।" else "${amount}।"
            ConversationStyle.BALANCED, ConversationStyle.DETAILED ->
                if (source != null) "আয় লিখে রেখেছি — ${source} থেকে ${amount}।"
                else "আয় লিখে রেখেছি — ${amount}।"
        }
    }

    private fun expenseRecordedSpeak(response: MiraResponse.ExpenseRecorded, style: ConversationStyle): String {
        val amount = money(response.amount)
        val description = response.description.cleaned()?.forSpeech()
        return when (style) {
            ConversationStyle.CONCISE -> if (description != null) "${amount}, ${description}।" else "${amount}।"
            ConversationStyle.BALANCED, ConversationStyle.DETAILED ->
                if (description != null) "খরচ লিখে রেখেছি — ${amount}, ${description}।"
                else "খরচ লিখে রেখেছি — ${amount}।"
        }
    }

    private fun incomeSummarySpeak(response: MiraResponse.IncomeSummaryReport, style: ConversationStyle): String {
        val total = money(response.totalReceived)
        return when (style) {
            ConversationStyle.CONCISE -> "${total}।"
            ConversationStyle.BALANCED -> "এই সময়ে মোট আয় ${total} — ${digits(response.count)}টি।"
            ConversationStyle.DETAILED ->
                "এই সময়ে মোট আয় ${total} — ${digits(response.count)}টি। এই সময়ের সব আয় এতে ধরা আছে।"
        }
    }

    private fun expenseSummarySpeak(response: MiraResponse.ExpenseSummaryReport, style: ConversationStyle): String {
        val cat = response.category?.let { categoryName(it) } ?: "মোট"
        val total = money(response.totalSpent)
        return when (style) {
            ConversationStyle.CONCISE -> "${total}।"
            ConversationStyle.BALANCED -> "${cat} খরচ ${total}।"
            ConversationStyle.DETAILED -> "${cat} খরচ ${total}। ${expenseScopeLine(response.category)}"
        }
    }

    private fun invoiceOutstandingSpeak(
        response: MiraResponse.InvoiceOutstandingReport,
        style: ConversationStyle,
    ): String {
        val client = response.clientName.forSpeech()
        val isClear = response.outstanding.amount.compareTo(java.math.BigDecimal.ZERO) == 0
        val outstanding = money(response.outstanding)
        return when (style) {
            ConversationStyle.CONCISE -> if (isClear) "কোনো বকেয়া নেই।" else "${outstanding} বকেয়া।"
            ConversationStyle.BALANCED ->
                if (isClear) "${client}-এর কোনো বকেয়া ইনভয়েস নেই।"
                else "${client}-এর ${outstanding} বকেয়া রয়েছে।"
            ConversationStyle.DETAILED ->
                if (isClear) "${client}-এর কোনো বকেয়া ইনভয়েস নেই।"
                else "${client}-এর ${outstanding} বকেয়া রয়েছে, মোট ${money(response.total)} ইনভয়েসের মধ্যে।"
        }
    }

    private fun clientStatementSpeak(
        statement: com.rayluxeo.aida.domain.model.ClientStatement,
        style: ConversationStyle,
    ): String {
        val client = statement.clientName.forSpeech()
        return when {
            statement.invoiceCount == 0 -> "${client}-এর এখনও কোনো ইস্যু করা ইনভয়েস নেই।"
            statement.currencyMixed ->
                "${client}-এর একাধিক মুদ্রায় ইনভয়েস রয়েছে, তাই এখানে যোগ করা যাচ্ছে না।"
            else -> {
                val owed = statement.outstanding!!
                val invoiced = money(statement.totalInvoiced!!)
                val outstanding = money(owed)
                val isClear = owed.amount.signum() == 0
                when (style) {
                    ConversationStyle.CONCISE ->
                        if (isClear) "কোনো বকেয়া নেই।" else "${outstanding} বকেয়া।"
                    ConversationStyle.BALANCED ->
                        if (isClear) "${client} পুরো পরিশোধ করেছে, মোট ${invoiced} ইনভয়েসের।"
                        else "${client}-এর ${invoiced} ইনভয়েসের মধ্যে ${outstanding} বকেয়া।"
                    ConversationStyle.DETAILED ->
                        "${client}: ${invoiced} ইনভয়েস, ${money(statement.totalPaid!!)} পরিশোধিত, " +
                            "${outstanding} বকেয়া।"
                }
            }
        }
    }

    private fun clientStatementDisplay(
        statement: com.rayluxeo.aida.domain.model.ClientStatement,
        style: ConversationStyle,
    ): String {
        val client = statement.clientName.trim()
        return when {
            statement.invoiceCount == 0 -> "$client\nএখনও কোনো ইস্যু করা ইনভয়েস নেই।"
            statement.currencyMixed ->
                "$client\nএকাধিক মুদ্রায় ইনভয়েস — মোট একত্র করা যাচ্ছে না।"
            else -> {
                val owed = statement.outstanding!!
                val invoiced = money(statement.totalInvoiced!!)
                val outstanding = money(owed)
                when (style) {
                    ConversationStyle.CONCISE ->
                        if (owed.amount.signum() == 0) "কোনো বকেয়া নেই।"
                        else "${outstanding} বকেয়া।"
                    ConversationStyle.BALANCED ->
                        "$client\nইনভয়েস: $invoiced\nবকেয়া: $outstanding"
                    ConversationStyle.DETAILED ->
                        "$client\nইনভয়েস: $invoiced\nপরিশোধিত: ${money(statement.totalPaid!!)}\n" +
                            "বকেয়া: $outstanding"
                }
            }
        }
    }

    private fun incomeSummaryDisplay(response: MiraResponse.IncomeSummaryReport, style: ConversationStyle): String {
        val total = money(response.totalReceived)
        val count = bengaliNumerals(response.count.toString())
        return when (style) {
            ConversationStyle.CONCISE -> total
            ConversationStyle.BALANCED -> "মোট আয়: ${total}\nসংখ্যা: ${count}"
            ConversationStyle.DETAILED -> "মোট আয়: ${total}\nসংখ্যা: ${count}\nএই সময়ের সব আয় এতে ধরা আছে।"
        }
    }

    private fun expenseSummaryDisplay(response: MiraResponse.ExpenseSummaryReport, style: ConversationStyle): String {
        val cat = response.category?.let { categoryName(it) } ?: "মোট"
        val total = money(response.totalSpent)
        val count = bengaliNumerals(response.count.toString())
        return when (style) {
            ConversationStyle.CONCISE -> total
            ConversationStyle.BALANCED -> "${cat} খরচ: ${total}\nসংখ্যা: ${count}"
            ConversationStyle.DETAILED -> "${cat} খরচ: ${total}\nসংখ্যা: ${count}\n${expenseScopeLine(response.category)}"
        }
    }

    private fun invoiceOutstandingDisplay(
        response: MiraResponse.InvoiceOutstandingReport,
        style: ConversationStyle,
    ): String {
        val client = response.clientName.trim()
        val isClear = response.outstanding.amount.compareTo(java.math.BigDecimal.ZERO) == 0
        val outstanding = money(response.outstanding)
        return when (style) {
            ConversationStyle.CONCISE -> if (isClear) "কোনো বকেয়া নেই।" else "${outstanding} বকেয়া।"
            ConversationStyle.BALANCED ->
                if (isClear) "${client}-এর কোনো বকেয়া ইনভয়েস নেই।"
                else "${client}-এর ${outstanding} বকেয়া রয়েছে।"
            ConversationStyle.DETAILED ->
                if (isClear) "${client}-এর কোনো বকেয়া ইনভয়েস নেই।"
                else "${client}-এর ${outstanding} বকেয়া রয়েছে।\nমোট ${money(response.total)} ইনভয়েসের মধ্যে।"
        }
    }

    /** একটি সারাংশ মোট যা ঢেকে রাখে, সহজভাবে বলা — DETAILED যেন অলংকার নয়, তথ্য যোগ করে। */
    private fun expenseScopeLine(category: com.rayluxeo.aida.domain.model.ExpenseCategory?): String =
        category?.let { "${categoryName(it)} খাতের সব খরচ এই সময়ে ধরা হয়েছে।" }
            ?: "এই সময়ের সব খরচ এতে ধরা আছে।"

    /** Overdue work is named first, because that is the part Abu can still be surprised by. */
    private fun taskSentence(brief: DailyBrief): String = when {
        brief.overdueCount > 0 && brief.dueTodayCount > 0 ->
            "${digits(brief.overdueCount)}টির সময় পেরিয়ে গেছে, আজ ${digits(brief.dueTodayCount)}টি আছে।"
        brief.overdueCount > 0 -> "${digits(brief.overdueCount)}টির সময় পেরিয়ে গেছে।"
        brief.dueTodayCount > 0 -> "আজ ${digits(brief.dueTodayCount)}টি কাজ আছে।"
        brief.openTaskCount > 0 -> "আজ কিছু নেই, ${digits(brief.openTaskCount)}টি কাজ বাকি।"
        else -> "আজ নির্দিষ্ট কোনো কাজ নেই।"
    }

    private fun reminderSentence(brief: DailyBrief): String {
        val title = brief.nextReminderTitle?.trim()?.takeIf { it.isNotEmpty() } ?: return ""
        val at = brief.nextReminderAt ?: return ""
        return " এরপর ${whenPhrase(at)} — ${title.forSpeech()}।"
    }

    /**
     * One line, naming the capability and what it buys. Abu asked for something reasonable and
     * the phone said no; repeating that in three sentences would be nagging.
     */
    private fun permission(capability: MiraCapability): String = when (capability) {
        MiraCapability.CONTACTS -> "কন্টাক্টসের অনুমতি পেলে নাম আর নম্বর খুঁজে দিতে পারব।"
        MiraCapability.CALENDAR -> "ক্যালেন্ডারের অনুমতি পেলে সেখানে ইভেন্ট রাখতে পারব।"
        MiraCapability.EXACT_ALARMS -> "অ্যালার্মের অনুমতি পেলে ঠিক যে সময় বলেছেন তখনই বাজাতে পারব।"
        MiraCapability.NOTIFICATIONS -> "নোটিফিকেশনের অনুমতি পেলে রিমাইন্ডার আপনার কাছে পৌঁছাবে।"
        MiraCapability.MICROPHONE -> "মাইক্রোফোনের অনুমতি পেলে শুনতে পারব।"
    }

    /**
     * What Abu does next. The alarm is the exception — see the English phrasebook for why:
     * the alarm row is kept whatever the operating system says and arms itself when the
     * permission returns, so telling him to ask again would give him a second alarm.
     */
    private fun permissionNextStep(capability: MiraCapability): String = when (capability) {
        MiraCapability.EXACT_ALARMS ->
            "অ্যালার্মটা রাখা আছে — সেটিংসে অনুমতি দিলে নিজে থেকেই বসে যাবে।"
        MiraCapability.CONTACTS,
        MiraCapability.CALENDAR,
        MiraCapability.NOTIFICATIONS,
        MiraCapability.MICROPHONE,
        -> "সেটিংসে গিয়ে অনুমতি দিন, তারপর আবার বলুন।"
    }

    private fun problem(code: MiraErrorCode): String = when (code) {
        MiraErrorCode.VALIDATION -> "কিছু একটা বাদ পড়েছে — নাম আর সময় দিয়ে আবার বলুন।"
        MiraErrorCode.NOT_FOUND -> "ওটা খুঁজে পাইনি।"
        MiraErrorCode.STORAGE -> "সেভ করতে পারিনি — আরেকবার চেষ্টা করুন।"
        MiraErrorCode.NOT_UNDERSTOOD -> "ঠিক বুঝতে পারিনি।"
        MiraErrorCode.ENGINE_UNAVAILABLE ->
            "অনলাইন ইঞ্জিন পাওয়া যাচ্ছে না — সংযোগ দেখুন, নয়তো অফলাইনেই চালিয়ে যান।"
        MiraErrorCode.PERMISSION_REQUIRED -> "এর জন্য অনুমতি দরকার — সেটিংস থেকে দিন।"
        MiraErrorCode.AUTHENTICATION_FAILED -> "আনলক হয়নি — আবার চেষ্টা করুন।"
        MiraErrorCode.UNKNOWN -> "কিছু একটা ভুল হয়েছে — আবার চেষ্টা করুন।"
    }

    /** The two codes Abu can actually resolve get the extra line the transcript has room for. */
    private fun problemDisplay(code: MiraErrorCode): String = when (code) {
        MiraErrorCode.ENGINE_UNAVAILABLE ->
            "অনলাইন ইঞ্জিন পাওয়া যাচ্ছে না।\n" +
                "সংযোগ দেখে নিন। কাজ, রিমাইন্ডার আর ব্রিফ অফলাইনেও চলে।"
        MiraErrorCode.PERMISSION_REQUIRED ->
            "এর জন্য অনুমতি দরকার।\nসেটিংসে গিয়ে অনুমতি দিন, তারপর আবার বলুন।"
        else -> problem(code)
    }

    /** Blank is the same as absent in everything Abu types, and neither earns a line of its own. */
    private fun String?.cleaned(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    /** Taka in Bengali numerals. A foreign currency keeps its code — Mira does not translate it. */
    private fun money(value: Money): String =
        "${bengaliNumerals(moneyFigure(value))} ${currencyWord(value.currency)}"

    private fun currencyWord(currency: Currency): String =
        if (currency == Money.BDT) "টাকা" else currency.currencyCode

    private fun prayerName(prayer: Prayer): String = when (prayer) {
        Prayer.FAJR -> "ফজর"
        Prayer.DHUHR -> "যোহর"
        Prayer.ASR -> "আসর"
        Prayer.MAGHRIB -> "মাগরিব"
        Prayer.ISHA -> "এশা"
        Prayer.TAHAJJUD -> "তাহাজ্জুদ"
    }

    /** The next prayer's moment reads just the clock time when it is today, and names the day otherwise. */
    private fun atPhrase(instant: Instant): String {
        val time = times.at(instant)
        return if (time.offset == DayOffset.TODAY) clockPhrase(time) else whenPhrase(instant)
    }

    private fun dayWordForDate(date: LocalDate): String = when (times.offsetOf(date)) {
        DayOffset.TODAY -> "আজ"
        DayOffset.TOMORROW -> "আগামীকাল"
        DayOffset.YESTERDAY -> "গতকাল"
        DayOffset.OTHER -> {
            val month = MONTHS[date.monthValue - 1]
            val year = if (date.year != times.today().year) " ${digits(date.year)}" else ""
            "${digits(date.dayOfMonth)} $month$year"
        }
    }

    private fun trackingMonthWord(from: LocalDate?): String {
        val month = from?.let { YearMonth.from(it) } ?: YearMonth.from(times.today())
        if (month == YearMonth.from(times.today())) return "এই মাসে"
        val name = MONTHS[month.monthValue - 1]
        val year = if (month.year != times.today().year) " ${digits(month.year)}" else ""
        return "$name$year মাসে"
    }

    private fun prayerTrackingSpeak(response: MiraResponse.PrayerTrackingReport): String {
        val per = response.perPrayer
        return if (per == null) {
            val day = dayWordForDate(response.from ?: times.today())
            val base = "$day ${digits(response.possible)} ওয়াক্তের মধ্যে " +
                "${digits(response.completed)} ওয়াক্ত নামাজ পড়েছেন।"
            if (response.tahajjudNights > 0) "$base তাহাজ্জুদও পড়েছেন।" else base
        } else {
            val base = "${trackingMonthWord(response.from)} ${digits(response.possible)} ওয়াক্তের মধ্যে " +
                "${digits(response.completed)} ওয়াক্ত পড়েছেন, ${digits(response.daysAllFive)} দিন পাঁচ ওয়াক্তই পূর্ণ হয়েছে।"
            if (response.tahajjudNights > 0) "$base ${digits(response.tahajjudNights)} রাত তাহাজ্জুদ।" else base
        }
    }

    private fun prayerTrackingDisplay(response: MiraResponse.PrayerTrackingReport): String {
        val per = response.perPrayer
        return if (per == null) {
            buildString {
                val day = dayWordForDate(response.from ?: times.today())
                append("$day: ${digits(response.completed)}/${digits(response.possible)} পড়া হয়েছে")
                if (response.tahajjudNights > 0) append("\nতাহাজ্জুদ: পড়া হয়েছে")
            }
        } else {
            buildString {
                append("${trackingMonthWord(response.from)}: ${digits(response.completed)}/${digits(response.possible)}")
                append("\nপাঁচ ওয়াক্ত: ${digits(response.daysAllFive)} দিন")
                per.forEach { (prayer, count) -> append("\n${prayerName(prayer)}: ${digits(count)}") }
                if (response.tahajjudNights > 0) append("\nতাহাজ্জুদ: ${digits(response.tahajjudNights)} রাত")
            }
        }
    }

    private fun lengthPhrase(duration: Duration): String {
        val total = maxOf(duration.seconds, 0L)
        val hours = total / SECONDS_PER_HOUR
        val minutes = (total % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val seconds = total % SECONDS_PER_MINUTE
        val parts = buildList {
            if (hours > 0) add("${digits(hours)} ঘণ্টা")
            if (minutes > 0) add("${digits(minutes)} মিনিট")
            // Seconds beside hours are noise in a spoken length, and a timer that long is not set
            // to the second anyway.
            if (seconds > 0 && hours == 0L) add("${digits(seconds)} সেকেন্ড")
        }
        return if (parts.isEmpty()) "${digits(0L)} সেকেন্ড" else parts.joinToString(" ")
    }

    /**
     * A phone number stays in the digits Abu can dial and copy. Bengali numerals would make the
     * line unusable, and the same holds for an invoice number, which is a name rather than a count.
     */
    private fun contactLine(contact: Contact): String = contact.phoneNumber.cleaned()
        ?.let { "${contact.displayName.trim()} — $it" }
        ?: contact.displayName.trim()

    private fun whenPhrase(instant: Instant): String {
        val time = times.at(instant)
        return "${dayPhrase(time)} ${clockPhrase(time)}"
    }

    private fun dayPhrase(time: WallTime): String = when (time.offset) {
        DayOffset.TODAY -> "আজ"
        DayOffset.TOMORROW -> "আগামীকাল"
        DayOffset.YESTERDAY -> "গতকাল"
        DayOffset.OTHER -> datePhrase(time)
    }

    private fun datePhrase(time: WallTime): String {
        val month = MONTHS[time.local.monthValue - 1]
        val day = digits(time.local.dayOfMonth)
        return if (time.sameYear) "$day $month" else "$day $month ${digits(time.local.year)}"
    }

    /** A bare 24-hour clock sounds like a timetable, so the hour carries its part of the day. */
    private fun clockPhrase(time: WallTime): String {
        val part = partOfDay(time.local.hour)
        val hour = digits(time.hour12)
        return if (time.minute == 0) {
            "$part ${hour}টায়"
        } else {
            "$part ${hour}টা ${digits(time.minute)} মিনিটে"
        }
    }

    private fun partOfDay(hour: Int): String = when (hour) {
        in 4..11 -> "সকাল"
        in 12..14 -> "দুপুর"
        in 15..17 -> "বিকেল"
        in 18..19 -> "সন্ধ্যা"
        else -> "রাত"
    }

    private fun joinWithO(items: List<String>): String = join(items, "ও")

    private fun joinWithNaki(items: List<String>): String = join(items, "নাকি")

    private fun join(items: List<String>, conjunction: String): String = when (items.size) {
        0 -> ""
        1 -> items.first()
        else -> "${items.dropLast(1).joinToString(", ")} $conjunction ${items.last()}"
    }

    private fun digits(value: Int): String = bengaliNumerals(value.toString())

    private fun digits(value: Long): String = bengaliNumerals(value.toString())

    /**
     * One kind of thing Mira counts. Bengali counts people and things with different classifiers —
     * three tasks are ৩টি and three clients are ৩জন — so the word travels with the noun.
     */
    private data class Counted(
        val noun: String,
        val classifier: String,
        val singular: String,
        val none: String,
    )

    private companion object {
        const val ECHO_LIMIT = 120

        const val SECONDS_PER_MINUTE = 60L
        const val SECONDS_PER_HOUR = 3600L

        val TASKS = Counted("কাজ", "টি", "একটি", "কোনো কাজ পাইনি।")
        val NOTES = Counted("নোট", "টি", "একটি", "কোনো নোট পাইনি।")
        val CLIENTS = Counted("ক্লায়েন্ট", "জন", "একজন", "কোনো ক্লায়েন্ট পাইনি।")
        val CONTACTS = Counted("কন্টাক্ট", "জন", "একজন", "কোনো কন্টাক্ট পাইনি।")
        val REMINDERS = Counted("রিমাইন্ডার", "টি", "একটি", "আসন্ন কোনো রিমাইন্ডার নেই।")

        /** Gregorian months as Bengali speakers in Dhaka write them. */
        val MONTHS = listOf(
            "জানুয়ারি",
            "ফেব্রুয়ারি",
            "মার্চ",
            "এপ্রিল",
            "মে",
            "জুন",
            "জুলাই",
            "আগস্ট",
            "সেপ্টেম্বর",
            "অক্টোবর",
            "নভেম্বর",
            "ডিসেম্বর",
        )

        fun categoryName(category: com.rayluxeo.aida.domain.model.ExpenseCategory): String = when (category) {
            com.rayluxeo.aida.domain.model.ExpenseCategory.SOFTWARE -> "সফটওয়্যার"
            com.rayluxeo.aida.domain.model.ExpenseCategory.HARDWARE -> "হার্ডওয়্যার"
            com.rayluxeo.aida.domain.model.ExpenseCategory.TRAVEL -> "ভ্রমণ"
            com.rayluxeo.aida.domain.model.ExpenseCategory.FOOD -> "খাবার"
            com.rayluxeo.aida.domain.model.ExpenseCategory.RENT -> "ভাড়া"
            com.rayluxeo.aida.domain.model.ExpenseCategory.UTILITIES -> "ইউটিলিটি"
            com.rayluxeo.aida.domain.model.ExpenseCategory.MARKETING -> "মার্কেটিং"
            com.rayluxeo.aida.domain.model.ExpenseCategory.PROFESSIONAL_FEES -> "পেশাদার ফি"
            com.rayluxeo.aida.domain.model.ExpenseCategory.TAX -> "ট্যাক্স"
            com.rayluxeo.aida.domain.model.ExpenseCategory.OTHER -> "অন্যান্য"
        }
    }
}

private val BENGALI_DIGITS = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')

/** Rewrites ASCII digits into Bengali ones and leaves everything else — separators, signs — alone. */
internal fun bengaliNumerals(text: String): String = buildString(text.length) {
    for (character in text) {
        append(if (character in '0'..'9') BENGALI_DIGITS[character - '0'] else character)
    }
}
