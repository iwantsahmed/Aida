package com.rayluxeo.aida.ai.nlu

import com.rayluxeo.aida.domain.model.ExpenseCategory
import com.rayluxeo.aida.domain.model.PaymentMethod
import com.rayluxeo.aida.domain.model.TaskPriority

/**
 * Everything Mira can be asked for, one row per command she can actually carry out.
 *
 * The enum is deliberately the same shape as `AppCommand`: an intent with no command behind it
 * would be a promise the dispatcher cannot keep.
 */
enum class MiraIntent {
    CREATE_TASK,
    UPDATE_TASK,
    COMPLETE_TASK,
    DELETE_TASK,
    SEARCH_TASKS,
    CREATE_REMINDER,
    QUERY_REMINDERS,
    SNOOZE_REMINDER,
    CANCEL_REMINDER,
    FORGET_FACT,
    CREATE_ALARM,
    CREATE_TIMER,
    GET_DAILY_BRIEF,
    CREATE_NOTE,
    SEARCH_NOTES,
    STORE_MEMORY,
    SEARCH_MEMORY,
    CREATE_CLIENT,
    SEARCH_CLIENTS,
    CREATE_INVOICE_DRAFT,
    CREATE_QUOTE_DRAFT,
    RECORD_PAYMENT,
    RECORD_EXPENSE,
    RECORD_INCOME,
    PERFORM_CALCULATION,
    QUERY_EXPENSES,
    QUERY_INCOME,
    QUERY_SUBSCRIPTION,
    MARK_PRAYER_PRAYED,
    QUERY_NEXT_PRAYER,
    QUERY_PRAYER_TIME_REMAINING,
    QUERY_PRAYER_TIME,
    QUERY_PRAYER_TRACKING,
    SET_TAHAJJUD_REMINDER,
    CREATE_CALENDAR_EVENT,
    QUERY_SCHEDULE,
    FIND_FREE_TIME,
    UPDATE_CALENDAR_EVENT,
    DELETE_CALENDAR_EVENT,
    FIND_CONTACT,
    PREPARE_CALL,
    PREPARE_MESSAGE,
    QUERY_INVOICE,
    QUERY_CLIENT_STATEMENT,
    DELIVER_INVOICE,
    NEW_CONVERSATION,
    DELETE_CONVERSATION,
    DELETE_ALL_CONVERSATIONS,
    CHITCHAT,
}

/**
 * One token that argues for an intent, and how strongly. A token with a space in it is a phrase.
 *
 * [namesTheRequest] is false for the few triggers that are also the thing being asked about.
 * "task" in "add a task to call Ahmed" names the request and has no place in the title, while
 * "meeting" in "meeting with Ahmed tomorrow" is the title; dropping it would leave an event
 * called "Ahmed".
 */
data class IntentTrigger(
    val token: String,
    val weight: Float,
    val namesTheRequest: Boolean = true,
)

/**
 * Everything the classifier knows about one intent.
 *
 * [yieldsTo] is the part that earns its keep. "Ahmed ke call korar kaj ta reminder dao" names a
 * call, a task and a reminder in one breath, and Abu means the reminder — the rest is what the
 * reminder is about. Rather than tuning weights until the right one happens to win, the losing
 * intent states outright that it stands down. It only does so when the stronger intent matched a
 * trigger of its own that this intent cannot account for, which is what keeps two searches that
 * share the word "khujo" from both standing down and leaving Abu with nothing.
 */
data class IntentRule(
    val intent: MiraIntent,
    val triggers: List<IntentTrigger>,
    val yieldsTo: Set<MiraIntent> = emptySet(),
    /** Tokens that argue against this intent even when a trigger matched. */
    val negatives: Set<String> = emptySet(),
    /** Added when the utterance contains a time expression. */
    val temporalBonus: Float = 0f,
    /** Added when the utterance contains a span of time rather than a moment. */
    val durationBonus: Float = 0f,
    /** Added when the utterance names an amount of money outright. */
    val moneyBonus: Float = 0f,
    /** Added when the utterance contains an arithmetic expression. */
    val arithmeticBonus: Float = 0f,
)

/**
 * The intent table, and the slot vocabularies that go with it.
 *
 * Triggers are canonical tokens wherever [Vocabulary] provides one, so a Bengali, Banglish or
 * English phrasing of the same request all land on the same row — "reminder", "রিমাইন্ডার" and
 * "mone koriye" have all become [CanonicalToken.REMIND] before the classifier runs. Words the
 * shared vocabulary does not fold are listed here in every spelling Abu uses, because a word
 * that only names an intent belongs beside the intent rather than in the general dictionary.
 *
 * Rows are ordered most specific first: two intents that somehow score identically are settled
 * by the narrower reading, not by where a row happened to be typed.
 */
object Lexicon {

    val rules: List<IntentRule> = listOf(

        // ------------------------------------------------------------------ time

        // Reminder-management rules sit before CREATE_REMINDER so that, on a tie, acting on an
        // existing reminder beats making a new one. "reminder" folds to REMIND, so each of these
        // needs its own verb (snooze / cancel / show) to outscore plain creation.
        IntentRule(
            intent = MiraIntent.SNOOZE_REMINDER,
            triggers = listOf(
                IntentTrigger("snooze", 1.0f),
                IntentTrigger("postpone", 0.7f),
                IntentTrigger("pichiye", 0.7f),
                IntentTrigger("পিছিয়ে", 0.7f),
                IntentTrigger(CanonicalToken.REMIND, 0.6f),
            ),
            // A creating temporal+subject is a new reminder, not a snooze of an old one.
            yieldsTo = setOf(MiraIntent.CREATE_REMINDER),
            // "task pichiye dao" is rescheduling a task (UPDATE_TASK), never a reminder snooze.
            negatives = setOf(CanonicalToken.TASK),
            durationBonus = 0.3f,
        ),
        IntentRule(
            intent = MiraIntent.CANCEL_REMINDER,
            triggers = listOf(
                IntentTrigger("cancel", 0.9f),
                IntentTrigger("baatil", 0.8f),
                IntentTrigger("batil", 0.8f),
                IntentTrigger("বাতিল", 0.8f),
                IntentTrigger(CanonicalToken.REMIND, 0.7f),
            ),
            yieldsTo = setOf(MiraIntent.CREATE_REMINDER, MiraIntent.DELETE_CALENDAR_EVENT),
            // Cancelling a meeting is a calendar delete; the scope words keep this off it.
            negatives = setOf(CanonicalToken.TASK, CanonicalToken.MEETING, CanonicalToken.CALENDAR),
        ),
        IntentRule(
            intent = MiraIntent.QUERY_REMINDERS,
            triggers = listOf(
                IntentTrigger(CanonicalToken.REMIND, 0.8f),
                IntentTrigger("show", 0.6f),
                IntentTrigger("list", 0.6f),
                IntentTrigger("dekhao", 0.6f),
                IntentTrigger("দেখাও", 0.6f),
                IntentTrigger("upcoming", 0.5f),
                IntentTrigger("what", 0.3f),
            ),
            // Making or acting on a specific reminder beats listing them.
            yieldsTo = setOf(
                MiraIntent.CREATE_REMINDER,
                MiraIntent.SNOOZE_REMINDER,
                MiraIntent.CANCEL_REMINDER,
            ),
            negatives = setOf(CanonicalToken.TASK, CanonicalToken.NOTE),
        ),

        IntentRule(
            intent = MiraIntent.CREATE_REMINDER,
            triggers = listOf(
                IntentTrigger(CanonicalToken.REMIND, 1.0f),
                IntentTrigger("alert", 0.6f),
                IntentTrigger("notify", 0.6f),
            ),
            // "Remind me for Tahajjud" is the Tahajjud reminder setting, not an ad-hoc reminder;
            // it stands down only when the word "tahajjud" — which a plain reminder never owns —
            // is present.
            yieldsTo = setOf(MiraIntent.SET_TAHAJJUD_REMINDER),
            temporalBonus = 0.3f,
        ),
        IntentRule(
            intent = MiraIntent.CREATE_ALARM,
            triggers = listOf(
                IntentTrigger(CanonicalToken.ALARM, 1.0f),
                IntentTrigger("wake", 0.7f),
                IntentTrigger("uthiye", 0.6f),
                IntentTrigger("উঠিয়ে", 0.6f),
            ),
            // A bare "alarm dao" names no reminder, so nothing is taken from it; when Abu names
            // a reminder as well, the reminder is what he is actually asking for.
            yieldsTo = setOf(MiraIntent.CREATE_REMINDER),
            temporalBonus = 0.3f,
        ),
        IntentRule(
            intent = MiraIntent.CREATE_TIMER,
            triggers = listOf(
                IntentTrigger(CanonicalToken.TIMER, 1.0f),
                IntentTrigger("stopwatch", 0.6f),
            ),
            yieldsTo = setOf(MiraIntent.CREATE_REMINDER),
            durationBonus = 0.4f,
        ),
        IntentRule(
            intent = MiraIntent.CREATE_CALENDAR_EVENT,
            triggers = listOf(
                IntentTrigger(CanonicalToken.CALENDAR, 1.0f),
                IntentTrigger("event", 0.9f, namesTheRequest = false),
                IntentTrigger("ইভেন্ট", 0.9f, namesTheRequest = false),
                IntentTrigger("appointment", 0.9f, namesTheRequest = false),
                IntentTrigger(CanonicalToken.MEETING, 0.8f, namesTheRequest = false),
                IntentTrigger("meeting", 0.8f, namesTheRequest = false),
                IntentTrigger("মিটিং", 0.8f, namesTheRequest = false),
            ),
            yieldsTo = setOf(
                MiraIntent.CREATE_REMINDER,
                MiraIntent.UPDATE_CALENDAR_EVENT,
                MiraIntent.DELETE_CALENDAR_EVENT,
            ),
            negatives = setOf("cancel", "delete", "বাতিল"),
            temporalBonus = 0.3f,
        ),
        IntentRule(
            intent = MiraIntent.QUERY_SCHEDULE,
            triggers = listOf(
                IntentTrigger("what do i have", 1.2f),
                IntentTrigger("what have i", 1.2f),
                IntentTrigger("whats on", 1.1f),
                IntentTrigger("what is on", 1.1f),
                IntentTrigger("schedule", 1.0f),
                IntentTrigger("শিডিউল", 1.0f),
                IntentTrigger("আজকের শিডিউল", 1.2f),
                IntentTrigger("ki ache", 0.9f),
                IntentTrigger("ki ase", 0.9f),
                IntentTrigger("কি আছে", 0.9f),
                IntentTrigger("agenda", 0.8f),
            ),
            yieldsTo = setOf(MiraIntent.CREATE_REMINDER, MiraIntent.CREATE_CALENDAR_EVENT),
            temporalBonus = 0.4f,
        ),
        IntentRule(
            intent = MiraIntent.FIND_FREE_TIME,
            triggers = listOf(
                IntentTrigger("when am i free", 1.3f),
                IntentTrigger("am i free", 1.1f),
                IntentTrigger("free time", 1.1f),
                IntentTrigger("free achi", 1.0f),
                IntentTrigger("ফ্রি আছি", 1.0f),
                IntentTrigger("কখন ফ্রি", 1.1f),
                IntentTrigger("kokhon free", 1.1f),
                IntentTrigger("kobe free", 1.0f),
                IntentTrigger("available", 0.9f),
                IntentTrigger("free slot", 1.0f),
                IntentTrigger("free", 0.7f),
            ),
            yieldsTo = setOf(MiraIntent.CREATE_REMINDER, MiraIntent.CREATE_CALENDAR_EVENT),
            temporalBonus = 0.4f,
        ),
        IntentRule(
            intent = MiraIntent.UPDATE_CALENDAR_EVENT,
            triggers = listOf(
                IntentTrigger("reschedule meeting", 1.3f),
                IntentTrigger("meeting reschedule", 1.2f),
                IntentTrigger("move meeting", 1.2f),
                IntentTrigger("meeting pichiye", 1.2f),
                IntentTrigger("মিটিং পিছিয়ে", 1.2f),
                IntentTrigger("reschedule event", 1.3f),
                IntentTrigger("reschedule calendar", 1.3f),
                IntentTrigger("move appointment", 1.3f),
            ),
            yieldsTo = emptySet(),
            temporalBonus = 0.2f,
        ),
        IntentRule(
            intent = MiraIntent.DELETE_CALENDAR_EVENT,
            triggers = listOf(
                IntentTrigger("delete meeting", 1.3f),
                IntentTrigger("cancel meeting", 1.3f),
                IntentTrigger("cancel the meeting", 1.3f),
                IntentTrigger("delete the meeting", 1.3f),
                IntentTrigger("remove meeting", 1.2f),
                IntentTrigger("delete event", 1.3f),
                IntentTrigger("cancel event", 1.3f),
                IntentTrigger("meeting cancel", 1.2f),
                IntentTrigger("delete calendar", 1.3f),
                IntentTrigger("cancel calendar", 1.3f),
                IntentTrigger("মিটিং ক্যান্সেল", 1.3f),
                IntentTrigger("মিটিং বাতিল", 1.3f),
                IntentTrigger("মিটিং বাদ", 1.2f),
                IntentTrigger("cancel", 0.7f),
                IntentTrigger("delete", 0.7f),
            ),
            yieldsTo = emptySet(),
        ),

        // ------------------------------------------------------------------ work

        IntentRule(
            intent = MiraIntent.COMPLETE_TASK,
            triggers = listOf(
                IntentTrigger(CanonicalToken.DONE, 1.0f),
                IntentTrigger("complete", 0.9f),
                IntentTrigger("completed", 0.9f),
                IntentTrigger("finish", 0.9f),
                IntentTrigger("finished", 0.9f),
                IntentTrigger("mark", 0.5f),
                IntentTrigger(CanonicalToken.TASK, 0.3f),
            ),
            negatives = setOf(CanonicalToken.REMIND),
        ),
        IntentRule(
            intent = MiraIntent.DELETE_TASK,
            triggers = listOf(
                IntentTrigger("delete", 0.9f),
                IntentTrigger("remove", 0.8f),
                IntentTrigger("muche", 0.8f),
                IntentTrigger("মুছে", 0.8f),
                IntentTrigger("baad dao", 0.6f),
                IntentTrigger("বাদ দাও", 0.6f),
                IntentTrigger(CanonicalToken.TASK, 0.6f),
            ),
            // Deleting a meeting is a calendar delete; completing is not deleting.
            yieldsTo = setOf(MiraIntent.DELETE_CALENDAR_EVENT, MiraIntent.COMPLETE_TASK),
            negatives = setOf(CanonicalToken.REMIND, CanonicalToken.MEETING, CanonicalToken.NOTE, CanonicalToken.MEMORY),
        ),
        IntentRule(
            intent = MiraIntent.UPDATE_TASK,
            triggers = listOf(
                IntentTrigger(CanonicalToken.UPDATE, 1.0f),
                IntentTrigger("reschedule", 1.0f),
                IntentTrigger("postpone", 0.9f),
                IntentTrigger("pichiye", 0.8f),
                IntentTrigger("পিছিয়ে", 0.8f),
                IntentTrigger(CanonicalToken.TASK, 0.3f),
            ),
            // "reminder pichiye dao" is snoozing a reminder, not rescheduling a task.
            yieldsTo = setOf(MiraIntent.COMPLETE_TASK, MiraIntent.DELETE_TASK, MiraIntent.SNOOZE_REMINDER),
        ),
        IntentRule(
            intent = MiraIntent.SEARCH_TASKS,
            triggers = listOf(
                IntentTrigger(CanonicalToken.SEARCH, 1.0f),
                IntentTrigger("show", 0.7f),
                IntentTrigger("dekhao", 0.7f),
                IntentTrigger("দেখাও", 0.7f),
                IntentTrigger("list", 0.7f),
                IntentTrigger("talika", 0.7f),
                IntentTrigger("তালিকা", 0.7f),
                IntentTrigger("pending", 0.6f),
                IntentTrigger(CanonicalToken.TASK, 0.3f),
                IntentTrigger("what", 0.3f),
                IntentTrigger("which", 0.3f),
            ),
            yieldsTo = setOf(
                MiraIntent.CREATE_REMINDER,
                MiraIntent.COMPLETE_TASK,
                MiraIntent.GET_DAILY_BRIEF,
                MiraIntent.SEARCH_NOTES,
                MiraIntent.SEARCH_MEMORY,
                MiraIntent.SEARCH_CLIENTS,
                MiraIntent.FIND_CONTACT,
                MiraIntent.QUERY_SCHEDULE,
                MiraIntent.FIND_FREE_TIME,
            ),
        ),
        IntentRule(
            intent = MiraIntent.GET_DAILY_BRIEF,
            triggers = listOf(
                IntentTrigger(CanonicalToken.BRIEF, 1.0f),
                IntentTrigger("summary", 0.8f),
                IntentTrigger("agenda", 0.8f),
                IntentTrigger("schedule", 0.6f),
                IntentTrigger("plan", 0.5f),
            ),
            yieldsTo = setOf(
                MiraIntent.CREATE_REMINDER,
                MiraIntent.CREATE_CALENDAR_EVENT,
                MiraIntent.QUERY_SCHEDULE,
                MiraIntent.FIND_FREE_TIME,
                MiraIntent.CREATE_TASK,
            ),
        ),

        // ------------------------------------------------------------------ knowledge

        IntentRule(
            intent = MiraIntent.SEARCH_NOTES,
            triggers = listOf(
                IntentTrigger(CanonicalToken.SEARCH, 1.0f),
                // Same list/show verbs as SEARCH_TASKS, so "show my notes" / "list my notes" is a
                // lookup rather than a new note written with the word "show" as its body.
                IntentTrigger("show", 0.7f),
                IntentTrigger("dekhao", 0.7f),
                IntentTrigger("দেখাও", 0.7f),
                IntentTrigger("list", 0.7f),
                IntentTrigger("talika", 0.7f),
                IntentTrigger("তালিকা", 0.7f),
                IntentTrigger(CanonicalToken.NOTE, 0.6f),
                IntentTrigger("notes", 0.6f),
            ),
            yieldsTo = setOf(MiraIntent.SEARCH_MEMORY),
        ),
        IntentRule(
            intent = MiraIntent.CREATE_NOTE,
            triggers = listOf(
                IntentTrigger(CanonicalToken.NOTE, 1.0f),
                IntentTrigger("jot", 0.7f),
                IntentTrigger("likhe", 0.6f),
                IntentTrigger("লিখে", 0.6f),
                // The imperatives. Only the Bengali "লিখো" was here, so "ei kotha ta likho"
                // classified as nothing while the same sentence in script became a note.
                IntentTrigger("likho", 0.6f),
                IntentTrigger("lekho", 0.6f),
                IntentTrigger("লিখো", 0.6f),
                IntentTrigger("লেখো", 0.6f),
                IntentTrigger("write", 0.5f),
            ),
            yieldsTo = setOf(
                MiraIntent.SEARCH_NOTES,
                MiraIntent.STORE_MEMORY,
                MiraIntent.CREATE_REMINDER,
            ),
        ),
        IntentRule(
            intent = MiraIntent.FORGET_FACT,
            triggers = listOf(
                IntentTrigger("forget", 1.0f),
                IntentTrigger("bhule jao", 0.9f),
                IntentTrigger("bhule jaw", 0.9f),
                IntentTrigger("bhule jao dao", 0.9f),
                IntentTrigger("ভুলে যাও", 0.9f),
                IntentTrigger("ভুলে যান", 0.9f),
            ),
            // "forget" is not "recall" or "remember"; a search of memory stands down to it.
            yieldsTo = setOf(MiraIntent.SEARCH_MEMORY),
            negatives = setOf(CanonicalToken.TASK, CanonicalToken.REMIND),
        ),
        IntentRule(
            intent = MiraIntent.SEARCH_MEMORY,
            triggers = listOf(
                IntentTrigger(CanonicalToken.SEARCH, 1.0f),
                IntentTrigger("recall", 0.9f),
                IntentTrigger("mone ache", 0.9f),
                IntentTrigger("মনে আছে", 0.9f),
                IntentTrigger("mone porche", 0.8f),
                IntentTrigger("মনে পড়ে", 0.8f),
                IntentTrigger(CanonicalToken.MEMORY, 0.6f),
                IntentTrigger(CanonicalToken.REMEMBER, 0.3f),
            ),
        ),
        IntentRule(
            intent = MiraIntent.STORE_MEMORY,
            triggers = listOf(
                IntentTrigger(CanonicalToken.REMEMBER, 1.0f),
                IntentTrigger("memorise", 0.9f),
                IntentTrigger("memorize", 0.9f),
                IntentTrigger(CanonicalToken.MEMORY, 0.5f),
            ),
            yieldsTo = setOf(MiraIntent.SEARCH_MEMORY, MiraIntent.CREATE_REMINDER),
            negatives = setOf(CanonicalToken.SEARCH),
        ),

        // ------------------------------------------------------------------ business

        IntentRule(
            intent = MiraIntent.SEARCH_CLIENTS,
            triggers = listOf(
                IntentTrigger(CanonicalToken.SEARCH, 1.0f),
                // The list/show verbs, the same set SEARCH_TASKS carries. Without them "show my
                // clients" / "list my clients" found no search signal and fell through to
                // CREATE_CLIENT (which fires on a bare "client"), inventing a client named "show".
                IntentTrigger("show", 0.7f),
                IntentTrigger("dekhao", 0.7f),
                IntentTrigger("দেখাও", 0.7f),
                IntentTrigger("list", 0.7f),
                IntentTrigger("talika", 0.7f),
                IntentTrigger("তালিকা", 0.7f),
                IntentTrigger(CanonicalToken.CLIENT, 0.6f),
                IntentTrigger("clients", 0.6f),
            ),
        ),
        IntentRule(
            intent = MiraIntent.CREATE_CLIENT,
            triggers = listOf(
                IntentTrigger(CanonicalToken.CLIENT, 1.0f),
            ),
            yieldsTo = setOf(
                MiraIntent.SEARCH_CLIENTS,
                MiraIntent.CREATE_INVOICE_DRAFT,
                MiraIntent.CREATE_QUOTE_DRAFT,
                MiraIntent.RECORD_PAYMENT,
                MiraIntent.RECORD_INCOME,
            ),
        ),
        IntentRule(
            intent = MiraIntent.CREATE_INVOICE_DRAFT,
            triggers = listOf(
                IntentTrigger(CanonicalToken.INVOICE, 1.0f),
                // "bill" is the one invoice word that is also an everyday noun for a thing Abu PAYS,
                // so on its own it is weak evidence — "pay the electricity bill" is an expense, not a
                // draft. At 0.3 it cannot reach the acting threshold on its own, nor on edge position
                // alone ("pay the internet bill" lands "bill" at the end of a short sentence): it has
                // to be joined by a stated amount, which is what separates issuing a bill ("bill
                // Ahmed 5000 taka") from paying one. A penalty on "pay" was tried instead and dropped
                // — being global, it also killed the legitimate "create an invoice for Ahmed to pay",
                // where "pay" names what the client does with the invoice, not an expense of Abu's.
                IntentTrigger("bill", 0.3f),
            ),
            yieldsTo = setOf(
                MiraIntent.RECORD_PAYMENT,
                MiraIntent.RECORD_INCOME,
                MiraIntent.SEARCH_TASKS,
                MiraIntent.SEARCH_CLIENTS,
            ),
            // Looking an invoice up is not drafting one, and a draft is a mutating command.
            negatives = setOf(CanonicalToken.SEARCH),
            moneyBonus = 0.3f,
        ),
        // A quote is the offer before the bill. Its own words ("quote", "quotation", "estimate",
        // "কোটেশন") never fold to CanonicalToken.INVOICE, so this neither collides with the invoice
        // rule above nor steals a plain "invoice" from it.
        IntentRule(
            intent = MiraIntent.CREATE_QUOTE_DRAFT,
            triggers = listOf(
                IntentTrigger("quote", 1.0f),
                IntentTrigger("quotation", 1.0f),
                IntentTrigger("estimate", 0.85f),
                IntentTrigger("কোটেশন", 1.0f),
            ),
            yieldsTo = setOf(
                MiraIntent.RECORD_PAYMENT,
                MiraIntent.RECORD_INCOME,
                MiraIntent.SEARCH_CLIENTS,
            ),
            // Looking a quote up or converting one is not drafting a new one.
            negatives = setOf(CanonicalToken.SEARCH),
            moneyBonus = 0.3f,
        ),
        IntentRule(
            intent = MiraIntent.QUERY_INVOICE,
            triggers = listOf(
                IntentTrigger(CanonicalToken.INVOICE, 0.9f),
                IntentTrigger("koto", 0.7f),
                IntentTrigger("কত", 0.7f),
                IntentTrigger("outstanding", 0.9f),
                IntentTrigger("due", 0.8f),
                IntentTrigger("baki", 0.8f),
                IntentTrigger("বাকি", 0.8f),
                IntentTrigger("বকেয়া", 0.9f),
                IntentTrigger("owed", 0.8f),
            ),
            negatives = setOf("create", "draft", "make", "send", "pathao", "পাঠাও", "paid", "পরিশোধ"),
            yieldsTo = setOf(
                MiraIntent.CREATE_INVOICE_DRAFT,
                MiraIntent.RECORD_PAYMENT,
                MiraIntent.DELIVER_INVOICE,
            ),
        ),
        // A client's whole standing, not one invoice. "statement"/"ledger"/"স্টেটমেন্ট" are specific
        // enough that this never fires on a bare "how much does Ahmed owe" (that is QUERY_INVOICE).
        IntentRule(
            intent = MiraIntent.QUERY_CLIENT_STATEMENT,
            triggers = listOf(
                IntentTrigger("statement", 1.0f),
                IntentTrigger("স্টেটমেন্ট", 1.0f),
                IntentTrigger("ledger", 0.9f),
                IntentTrigger("লেজার", 0.9f),
            ),
            negatives = setOf("create", "draft", "make", "send", "pathao", "পাঠাও", "paid", "পরিশোধ"),
            yieldsTo = setOf(
                MiraIntent.CREATE_INVOICE_DRAFT,
                MiraIntent.CREATE_QUOTE_DRAFT,
                MiraIntent.RECORD_PAYMENT,
            ),
        ),
        IntentRule(
            intent = MiraIntent.DELIVER_INVOICE,
            triggers = listOf(
                IntentTrigger("send", 0.8f),
                IntentTrigger("pathao", 0.8f),
                IntentTrigger("pathiye", 0.8f),
                IntentTrigger("পাঠাও", 0.8f),
                IntentTrigger("পাঠিয়ে", 0.8f),
                IntentTrigger("পাঠিয়ে", 0.8f),
                IntentTrigger("deliver", 0.8f),
                IntentTrigger("share", 0.8f),
                IntentTrigger(CanonicalToken.INVOICE, 0.5f),
            ),
            negatives = setOf("create", "draft", "make", "paid"),
            yieldsTo = setOf(
                MiraIntent.CREATE_INVOICE_DRAFT,
            ),
        ),
        IntentRule(
            intent = MiraIntent.NEW_CONVERSATION,
            triggers = listOf(
                IntentTrigger("new conversation", 1.0f),
                IntentTrigger("start new conversation", 1.0f),
                IntentTrigger("new chat", 1.0f),
                IntentTrigger("নতুন কথোপকথন", 1.0f),
                IntentTrigger("নতুন চ্যাট", 1.0f),
                IntentTrigger("start fresh", 0.9f),
                IntentTrigger("clear chat", 0.8f),
            ),
        ),
        IntentRule(
            intent = MiraIntent.DELETE_ALL_CONVERSATIONS,
            triggers = listOf(
                IntentTrigger("delete all conversations", 1.0f),
                IntentTrigger("clear all conversations", 1.0f),
                IntentTrigger("delete all history", 1.0f),
                IntentTrigger("সব কথোপকথন মুছে", 1.0f),
                IntentTrigger("সব কথোপকথন ডিলিট", 1.0f),
            ),
        ),
        IntentRule(
            intent = MiraIntent.DELETE_CONVERSATION,
            triggers = listOf(
                IntentTrigger("delete conversation", 0.9f),
                IntentTrigger("delete this conversation", 1.0f),
                IntentTrigger("কথোপকথন মুছে", 0.9f),
                IntentTrigger("কথোপকথন ডিলিট", 0.9f),
            ),
            yieldsTo = setOf(MiraIntent.DELETE_ALL_CONVERSATIONS),
        ),

        // ------------------------------------------------------------------ money

        IntentRule(
            intent = MiraIntent.RECORD_PAYMENT,
            triggers = listOf(
                IntentTrigger(CanonicalToken.PAYMENT, 1.0f),
                IntentTrigger("settled", 0.5f),
                IntentTrigger(CanonicalToken.INVOICE, 0.3f),
            ),
            moneyBonus = 0.3f,
        ),
        IntentRule(
            intent = MiraIntent.RECORD_EXPENSE,
            triggers = listOf(
                IntentTrigger(CanonicalToken.EXPENSE, 1.0f),
            ),
            yieldsTo = setOf(MiraIntent.QUERY_EXPENSES),
            negatives = setOf("koto", "কত", "how much"),
            moneyBonus = 0.3f,
        ),
        IntentRule(
            intent = MiraIntent.QUERY_EXPENSES,
            triggers = listOf(
                IntentTrigger("${CanonicalToken.CALCULATE} ${CanonicalToken.EXPENSE}", 1.2f),
                IntentTrigger("koto khoroch", 1.0f),
                IntentTrigger("কত খরচ", 1.0f),
                IntentTrigger("khoroch hoyeche", 0.9f),
                IntentTrigger("${CanonicalToken.EXPENSE} ${CanonicalToken.DONE}", 1.0f),
                IntentTrigger("খরচ হয়েছে", 0.9f),
                IntentTrigger("খরচ হয়েছে", 0.9f),
                IntentTrigger("how much spent", 1.0f),
                IntentTrigger("how much spend", 0.9f),
                IntentTrigger("total spent", 0.8f),
                IntentTrigger(CanonicalToken.EXPENSE, 0.5f),
            ),
            negatives = setOf("number", "phone", "contact"),
        ),
        IntentRule(
            intent = MiraIntent.QUERY_INCOME,
            triggers = listOf(
                // "koto income"/"কত আয়"/"income koto" all fold to a CALCULATE+INCOME pair; the
                // literal Banglish and Bengali runs sit beside it so a sentence that reached the
                // classifier unfolded is still read as a query rather than a record.
                IntentTrigger("${CanonicalToken.CALCULATE} ${CanonicalToken.INCOME}", 1.2f),
                IntentTrigger("${CanonicalToken.INCOME} ${CanonicalToken.CALCULATE}", 1.2f),
                IntentTrigger("koto income", 1.0f),
                IntentTrigger("income koto", 1.0f),
                IntentTrigger("koto ay", 1.0f),
                IntentTrigger("কত আয়", 1.0f),
                IntentTrigger("আয় কত", 1.0f),
                IntentTrigger("how much income", 1.1f),
                IntentTrigger("how much did i earn", 1.1f),
                IntentTrigger("how much earned", 0.9f),
                IntentTrigger("total income", 0.8f),
                IntentTrigger("earnings", 0.7f),
                // Bare income stays weak on purpose: it loses to RECORD_INCOME so "income 5000
                // from Ahmed" records, exactly as the bare expense word loses to RECORD_EXPENSE.
                IntentTrigger(CanonicalToken.INCOME, 0.5f),
            ),
            negatives = setOf("number", "phone", "contact"),
        ),
        IntentRule(
            intent = MiraIntent.QUERY_SUBSCRIPTION,
            triggers = listOf(
                IntentTrigger("subscription next", 1.0f),
                IntentTrigger("next subscription", 0.9f),
                IntentTrigger("সাবস্ক্রিপশন next", 1.0f),
                IntentTrigger("subscription renewal", 0.9f),
                IntentTrigger("renewal", 0.8f),
            ),
        ),
        IntentRule(
            intent = MiraIntent.RECORD_INCOME,
            triggers = listOf(
                IntentTrigger(CanonicalToken.INCOME, 1.0f),
                IntentTrigger("received", 1.0f),
                IntentTrigger("receive", 0.8f),
                IntentTrigger("peyechi", 1.0f),
                IntentTrigger("peyeci", 1.0f),
                IntentTrigger("পেয়েছি", 1.0f),
                IntentTrigger("pelam", 0.9f),
                IntentTrigger("পেলাম", 0.9f),
            ),
            // Money arriving against an invoice is a payment; income is what is left when Abu
            // names neither an invoice nor a payment. And a question about income — "koto income",
            // "how much income" — is a query, not a record, exactly as RECORD_EXPENSE stands down
            // to QUERY_EXPENSES on the same question words.
            yieldsTo = setOf(MiraIntent.RECORD_PAYMENT, MiraIntent.QUERY_INCOME),
            negatives = setOf("koto", "কত", "how much"),
            moneyBonus = 0.3f,
        ),
        IntentRule(
            intent = MiraIntent.PERFORM_CALCULATION,
            triggers = listOf(
                IntentTrigger(CanonicalToken.CALCULATE, 0.7f),
                IntentTrigger("calc", 0.9f),
                IntentTrigger("evaluate", 0.8f),
                IntentTrigger("sum", 0.5f),
            ),
            yieldsTo = setOf(
                MiraIntent.FIND_CONTACT,
                MiraIntent.RECORD_EXPENSE,
                MiraIntent.RECORD_INCOME,
                MiraIntent.QUERY_EXPENSES,
                MiraIntent.QUERY_INCOME,
            ),
            // "koto" folds to CALCULATE and is simply the Bengali for "how much", so it ends a
            // question about a phone number as readily as one about a sum. Digits and an
            // operator are the real evidence; the words alone never carry it.
            negatives = setOf(
                "number",
                "nambar",
                "nombor",
                "নাম্বার",
                "নম্বর",
                CanonicalToken.CONTACT,
                "phone",
            ),
            arithmeticBonus = 1.2f,
        ),

        // ------------------------------------------------------------------ salah

        // Logging a prayer. The verb — "prayed", "porechi", "adai" — carries it; the prayer name
        // is the object. A counting question ("koy waqt porechi") is a tracking query instead, so
        // this stands down to it, but only when a counting word it does not itself own matched.
        IntentRule(
            intent = MiraIntent.MARK_PRAYER_PRAYED,
            triggers = listOf(
                IntentTrigger(CanonicalToken.PRAYED, 1.0f),
                IntentTrigger("mark", 0.4f),
                IntentTrigger(CanonicalToken.NAMAZ, 0.4f, namesTheRequest = false),
                IntentTrigger(CanonicalToken.FAJR, 0.4f, namesTheRequest = false),
                IntentTrigger(CanonicalToken.DHUHR, 0.4f, namesTheRequest = false),
                IntentTrigger(CanonicalToken.ASR, 0.4f, namesTheRequest = false),
                IntentTrigger(CanonicalToken.MAGHRIB, 0.4f, namesTheRequest = false),
                IntentTrigger(CanonicalToken.ISHA, 0.4f, namesTheRequest = false),
                IntentTrigger(CanonicalToken.TAHAJJUD, 0.4f, namesTheRequest = false),
            ),
            yieldsTo = setOf(MiraIntent.QUERY_PRAYER_TRACKING),
        ),

        // Turn on the Tahajjud reminder. Needs both Tahajjud and a reminder word; logging it as
        // prayed, or asking how many nights, is not setting a reminder, hence the negatives.
        IntentRule(
            intent = MiraIntent.SET_TAHAJJUD_REMINDER,
            triggers = listOf(
                IntentTrigger(CanonicalToken.TAHAJJUD, 0.9f),
                IntentTrigger(CanonicalToken.REMIND, 0.8f),
            ),
            negatives = setOf(
                CanonicalToken.PRAYED,
                "koy",
                "কয়",
                "koybar",
                "কয়বার",
                "koydin",
                "কয়দিন",
                "miss",
                "মিস",
            ),
        ),

        // How Abu's praying stands over a window. The discriminators are the counting words —
        // "koy", "koybar", "koydin", "how many", "miss", and "namaz" beside "hisab"/"কত" (both of
        // which fold to CALCULATE) — none of which the logging intent owns.
        IntentRule(
            intent = MiraIntent.QUERY_PRAYER_TRACKING,
            triggers = listOf(
                IntentTrigger("${CanonicalToken.NAMAZ} ${CanonicalToken.CALCULATE}", 1.2f),
                IntentTrigger("${CanonicalToken.CALCULATE} ${CanonicalToken.NAMAZ}", 1.2f),
                IntentTrigger("koy waqt", 1.1f),
                IntentTrigger("কয় ওয়াক্ত", 1.1f),
                IntentTrigger("koybar", 1.0f),
                IntentTrigger("কয়বার", 1.0f),
                IntentTrigger("koydin", 1.0f),
                IntentTrigger("কয়দিন", 1.0f),
                IntentTrigger("how many", 1.0f),
                IntentTrigger("miss", 0.8f),
                IntentTrigger("মিস", 0.8f),
                IntentTrigger("koy", 0.6f),
                IntentTrigger("কয়", 0.6f),
                IntentTrigger(CanonicalToken.NAMAZ, 0.5f, namesTheRequest = false),
                IntentTrigger(CanonicalToken.FAJR, 0.4f, namesTheRequest = false),
                IntentTrigger(CanonicalToken.DHUHR, 0.4f, namesTheRequest = false),
                IntentTrigger(CanonicalToken.ASR, 0.4f, namesTheRequest = false),
                IntentTrigger(CanonicalToken.MAGHRIB, 0.4f, namesTheRequest = false),
                IntentTrigger(CanonicalToken.ISHA, 0.4f, namesTheRequest = false),
                IntentTrigger(CanonicalToken.TAHAJJUD, 0.4f, namesTheRequest = false),
            ),
            negatives = setOf("number", "phone", CanonicalToken.CONTACT),
        ),

        // How long until a prayer. "baki" overlaps with the invoice query, so an invoice word
        // argues this one down; a counting question is a tracking query and wins the "baki" it
        // shares only through its own counting words.
        IntentRule(
            intent = MiraIntent.QUERY_PRAYER_TIME_REMAINING,
            triggers = listOf(
                IntentTrigger("somoy baki", 1.2f),
                IntentTrigger("সময় বাকি", 1.2f),
                IntentTrigger("time remaining", 1.2f),
                IntentTrigger("time left", 1.1f),
                IntentTrigger("how long", 1.0f),
                IntentTrigger("remaining", 0.8f),
                IntentTrigger("baki", 0.7f),
                IntentTrigger("বাকি", 0.7f),
                IntentTrigger("somoy", 0.4f),
                IntentTrigger("সময়", 0.4f),
                IntentTrigger(CanonicalToken.NAMAZ, 0.5f, namesTheRequest = false),
                IntentTrigger(CanonicalToken.FAJR, 0.5f, namesTheRequest = false),
                IntentTrigger(CanonicalToken.DHUHR, 0.5f, namesTheRequest = false),
                IntentTrigger(CanonicalToken.ASR, 0.5f, namesTheRequest = false),
                IntentTrigger(CanonicalToken.MAGHRIB, 0.5f, namesTheRequest = false),
                IntentTrigger(CanonicalToken.ISHA, 0.5f, namesTheRequest = false),
                IntentTrigger(CanonicalToken.TAHAJJUD, 0.5f, namesTheRequest = false),
            ),
            yieldsTo = setOf(MiraIntent.QUERY_PRAYER_TRACKING),
            negatives = setOf(CanonicalToken.INVOICE, "due", "outstanding", "বকেয়া"),
        ),

        // The clock time of a named prayer. A specific prayer name weighs more here than in the
        // next-prayer rule, which keys on the generic word, so "fajr time" lands here and "next
        // namaz kokhon" there.
        IntentRule(
            intent = MiraIntent.QUERY_PRAYER_TIME,
            triggers = listOf(
                IntentTrigger("time", 0.7f),
                IntentTrigger("kokhon", 0.5f),
                IntentTrigger("কখন", 0.5f),
                IntentTrigger("when", 0.4f),
                IntentTrigger(CanonicalToken.FAJR, 0.7f, namesTheRequest = false),
                IntentTrigger(CanonicalToken.DHUHR, 0.7f, namesTheRequest = false),
                IntentTrigger(CanonicalToken.ASR, 0.7f, namesTheRequest = false),
                IntentTrigger(CanonicalToken.MAGHRIB, 0.7f, namesTheRequest = false),
                IntentTrigger(CanonicalToken.ISHA, 0.7f, namesTheRequest = false),
                IntentTrigger(CanonicalToken.TAHAJJUD, 0.7f, namesTheRequest = false),
            ),
            yieldsTo = setOf(
                MiraIntent.QUERY_PRAYER_TIME_REMAINING,
                MiraIntent.QUERY_PRAYER_TRACKING,
                MiraIntent.MARK_PRAYER_PRAYED,
            ),
            negatives = setOf("baki", "বাকি"),
        ),

        // The next prayer and its countdown. Generic: it keys on the plain word for prayer, so a
        // named prayer with "time" is read as that prayer's time instead.
        IntentRule(
            intent = MiraIntent.QUERY_NEXT_PRAYER,
            triggers = listOf(
                IntentTrigger("next namaz", 1.3f),
                IntentTrigger("namaz kokhon", 1.2f),
                IntentTrigger("kokhon namaz", 1.2f),
                IntentTrigger("porer namaz", 1.2f),
                IntentTrigger("porer", 0.5f),
                IntentTrigger("kokhon", 0.6f),
                IntentTrigger("কখন", 0.6f),
                IntentTrigger("when", 0.4f),
                IntentTrigger(CanonicalToken.NAMAZ, 0.6f, namesTheRequest = false),
            ),
            yieldsTo = setOf(
                MiraIntent.QUERY_PRAYER_TIME,
                MiraIntent.QUERY_PRAYER_TIME_REMAINING,
                MiraIntent.QUERY_PRAYER_TRACKING,
                MiraIntent.MARK_PRAYER_PRAYED,
                MiraIntent.SET_TAHAJJUD_REMINDER,
            ),
        ),

        // ------------------------------------------------------------------ the phone itself

        IntentRule(
            intent = MiraIntent.FIND_CONTACT,
            triggers = listOf(
                IntentTrigger("number", 1.2f),
                IntentTrigger("nambar", 1.2f),
                IntentTrigger("nombor", 1.2f),
                IntentTrigger("নাম্বার", 1.2f),
                IntentTrigger("নম্বর", 1.2f),
                IntentTrigger(CanonicalToken.CONTACT, 1.0f),
                IntentTrigger("phone", 0.8f),
                IntentTrigger("ফোন", 0.8f),
                IntentTrigger("email", 0.8f),
                IntentTrigger("ইমেইল", 0.8f),
                IntentTrigger("ইমেল", 0.8f),
                IntentTrigger("mail", 0.7f),
                IntentTrigger("মেইল", 0.7f),
                IntentTrigger(CanonicalToken.SEARCH, 0.6f),
            ),
            // Abu asking for a number so that he can call is asking for the call.
            negatives = setOf(CanonicalToken.CALL, CanonicalToken.MESSAGE),
        ),
        IntentRule(
            intent = MiraIntent.PREPARE_MESSAGE,
            triggers = listOf(
                IntentTrigger(CanonicalToken.MESSAGE, 1.0f),
                IntentTrigger("whatsapp", 0.9f),
                IntentTrigger("বার্তা", 0.9f),
                IntentTrigger("text", 0.5f),
            ),
            yieldsTo = setOf(
                MiraIntent.CREATE_REMINDER,
                MiraIntent.CREATE_NOTE,
                MiraIntent.STORE_MEMORY,
            ),
        ),
        IntentRule(
            intent = MiraIntent.PREPARE_CALL,
            triggers = listOf(
                IntentTrigger(CanonicalToken.CALL, 1.0f),
                IntentTrigger("dial", 0.9f),
                IntentTrigger("ডায়াল", 0.9f),
                IntentTrigger("ring", 0.5f),
                IntentTrigger("phone", 0.4f),
            ),
            // Half of Abu's reminders, tasks and notes are about calling someone. The call word
            // is the subject of those sentences, never the request.
            yieldsTo = setOf(
                MiraIntent.CREATE_REMINDER,
                MiraIntent.CREATE_ALARM,
                MiraIntent.CREATE_TIMER,
                MiraIntent.CREATE_CALENDAR_EVENT,
                MiraIntent.QUERY_SCHEDULE,
                MiraIntent.FIND_FREE_TIME,
                MiraIntent.UPDATE_CALENDAR_EVENT,
                MiraIntent.DELETE_CALENDAR_EVENT,
                MiraIntent.CREATE_TASK,
                MiraIntent.UPDATE_TASK,
                MiraIntent.COMPLETE_TASK,
                MiraIntent.SEARCH_TASKS,
                MiraIntent.CREATE_NOTE,
                MiraIntent.STORE_MEMORY,
                MiraIntent.PREPARE_MESSAGE,
            ),
        ),

        // ------------------------------------------------------------------ the catch-all

        IntentRule(
            intent = MiraIntent.CREATE_TASK,
            triggers = listOf(
                IntentTrigger(CanonicalToken.TASK, 0.8f),
                IntentTrigger(CanonicalToken.CREATE, 0.5f),
                // Bengali needs no word for "task" to ask for one: "Ahmed ke proposal pathate
                // hobe" is an obligation, and the auxiliary is the whole of what makes it a
                // request rather than a remark. It names the request, so it leaves the title.
                IntentTrigger("hobe", 0.9f),
                IntentTrigger("hobey", 0.9f),
                IntentTrigger("হবে", 0.9f),
            ),
            // "create" and "kaj" are the words every other request also reaches for, so a task
            // is what Abu meant only when nothing more specific was named.
            //
            // COMPLETE_TASK is deliberately NOT in this list. "complete" and "finish" are the
            // commonest words inside a task's own title — "website complete korar task add
            // koro" — and standing down for them made every such sentence mark something done
            // instead of writing it down. A real completion still wins on its own score, and an
            // exact tie is settled by row order, where COMPLETE_TASK sits first.
            yieldsTo = setOf(
                MiraIntent.CREATE_REMINDER,
                MiraIntent.CREATE_ALARM,
                MiraIntent.CREATE_TIMER,
                MiraIntent.CREATE_CALENDAR_EVENT,
                MiraIntent.QUERY_SCHEDULE,
                MiraIntent.FIND_FREE_TIME,
                MiraIntent.UPDATE_CALENDAR_EVENT,
                MiraIntent.DELETE_CALENDAR_EVENT,
                MiraIntent.UPDATE_TASK,
                MiraIntent.SEARCH_TASKS,
                MiraIntent.CREATE_NOTE,
                MiraIntent.SEARCH_NOTES,
                MiraIntent.STORE_MEMORY,
                MiraIntent.SEARCH_MEMORY,
                MiraIntent.CREATE_CLIENT,
                MiraIntent.SEARCH_CLIENTS,
                MiraIntent.CREATE_INVOICE_DRAFT,
                MiraIntent.CREATE_QUOTE_DRAFT,
                MiraIntent.QUERY_CLIENT_STATEMENT,
                MiraIntent.RECORD_PAYMENT,
                MiraIntent.RECORD_EXPENSE,
                MiraIntent.RECORD_INCOME,
                MiraIntent.FIND_CONTACT,
                MiraIntent.PREPARE_MESSAGE,
            ),
            temporalBonus = 0.1f,
        ),

        // ------------------------------------------------------------------ small talk

        // A greeting or a pleasantry and nothing else. It sits last and yields to every real
        // request, so "hey, remind me at 5" is a reminder and only a bare "hey bro" lands here —
        // the one utterance Mira must answer rather than meet with "I didn't understand". Written
        // in every spelling Abu uses, because a hello is not in the shared vocabulary.
        IntentRule(
            intent = MiraIntent.CHITCHAT,
            triggers = listOf(
                IntentTrigger("hi", 0.9f),
                IntentTrigger("hey", 0.9f),
                IntentTrigger("hello", 0.9f),
                IntentTrigger("hiya", 0.8f),
                IntentTrigger("yo", 0.8f),
                IntentTrigger("sup", 0.8f),
                IntentTrigger("whats up", 1.0f),
                IntentTrigger("bro", 0.6f),
                IntentTrigger("vai", 0.6f),
                IntentTrigger("bhai", 0.6f),
                IntentTrigger("ভাই", 0.6f),
                IntentTrigger("হাই", 0.9f),
                IntentTrigger("হ্যালো", 0.9f),
                IntentTrigger("salam", 0.9f),
                IntentTrigger("assalamu alaikum", 1.3f),
                IntentTrigger("walaikum", 0.9f),
                IntentTrigger("সালাম", 0.9f),
                IntentTrigger("আসসালামু আলাইকুম", 1.3f),
                IntentTrigger("nomoskar", 0.9f),
                IntentTrigger("নমস্কার", 0.9f),
                IntentTrigger("good morning", 1.1f),
                IntentTrigger("good afternoon", 1.1f),
                IntentTrigger("good evening", 1.1f),
                IntentTrigger("kemon acho", 1.1f),
                IntentTrigger("kemon achen", 1.1f),
                IntentTrigger("কেমন আছো", 1.1f),
                IntentTrigger("কেমন আছেন", 1.1f),
                IntentTrigger("how are you", 1.1f),
                IntentTrigger("hows it going", 1.0f),
            ),
            // Yields to every real request: a greeting that prefaces a command must never swallow
            // it. Only an utterance with nothing else in it is left for the greeting to win.
            yieldsTo = setOf(
                MiraIntent.CREATE_TASK,
                MiraIntent.UPDATE_TASK,
                MiraIntent.COMPLETE_TASK,
                MiraIntent.SEARCH_TASKS,
                MiraIntent.CREATE_REMINDER,
                MiraIntent.CREATE_ALARM,
                MiraIntent.CREATE_TIMER,
                MiraIntent.GET_DAILY_BRIEF,
                MiraIntent.CREATE_NOTE,
                MiraIntent.SEARCH_NOTES,
                MiraIntent.STORE_MEMORY,
                MiraIntent.SEARCH_MEMORY,
                MiraIntent.CREATE_CLIENT,
                MiraIntent.SEARCH_CLIENTS,
                MiraIntent.CREATE_INVOICE_DRAFT,
                MiraIntent.CREATE_QUOTE_DRAFT,
                MiraIntent.RECORD_PAYMENT,
                MiraIntent.RECORD_EXPENSE,
                MiraIntent.RECORD_INCOME,
                MiraIntent.PERFORM_CALCULATION,
                MiraIntent.QUERY_EXPENSES,
                MiraIntent.QUERY_INCOME,
                MiraIntent.QUERY_SUBSCRIPTION,
                MiraIntent.MARK_PRAYER_PRAYED,
                MiraIntent.QUERY_NEXT_PRAYER,
                MiraIntent.QUERY_PRAYER_TIME_REMAINING,
                MiraIntent.QUERY_PRAYER_TIME,
                MiraIntent.QUERY_PRAYER_TRACKING,
                MiraIntent.SET_TAHAJJUD_REMINDER,
                MiraIntent.CREATE_CALENDAR_EVENT,
                MiraIntent.QUERY_SCHEDULE,
                MiraIntent.FIND_FREE_TIME,
                MiraIntent.UPDATE_CALENDAR_EVENT,
                MiraIntent.DELETE_CALENDAR_EVENT,
                MiraIntent.FIND_CONTACT,
                MiraIntent.PREPARE_CALL,
                MiraIntent.PREPARE_MESSAGE,
                MiraIntent.QUERY_INVOICE,
                MiraIntent.QUERY_CLIENT_STATEMENT,
                MiraIntent.DELIVER_INVOICE,
                MiraIntent.NEW_CONVERSATION,
                MiraIntent.DELETE_CONVERSATION,
                MiraIntent.DELETE_ALL_CONVERSATIONS,
            ),
        ),
    )

    val byIntent: Map<MiraIntent, IntentRule> = rules.associateBy { it.intent }

    /**
     * Words that set a task's priority rather than describing it. They are pulled out of a title
     * before it is built, because "urgent" is already recorded in the priority field and reading
     * it back inside the title would say the same thing twice.
     *
     * Only the raised priority is listed. The words that would argue for [TaskPriority.LOW] —
     * "low", "later", "kom" — all mean something else far more often than they mean a priority,
     * and a task quietly demoted is a task Abu never sees again.
     */
    val priorityWords: Map<String, TaskPriority> = mapOf(
        "urgent" to TaskPriority.HIGH,
        "urgently" to TaskPriority.HIGH,
        "important" to TaskPriority.HIGH,
        "jorori" to TaskPriority.HIGH,
        "joruri" to TaskPriority.HIGH,
        "জরুরি" to TaskPriority.HIGH,
        "জরুরী" to TaskPriority.HIGH,
        "গুরুত্বপূর্ণ" to TaskPriority.HIGH,
    )

    /** How an amount reached Abu. Anything unnamed stays [PaymentMethod.OTHER]. */
    val paymentMethods: Map<String, PaymentMethod> = mapOf(
        "cash" to PaymentMethod.CASH,
        "nogod" to PaymentMethod.CASH,
        "নগদ" to PaymentMethod.CASH,
        "bank" to PaymentMethod.BANK_TRANSFER,
        "ব্যাংক" to PaymentMethod.BANK_TRANSFER,
        "transfer" to PaymentMethod.BANK_TRANSFER,
        "neft" to PaymentMethod.BANK_TRANSFER,
        "bkash" to PaymentMethod.MOBILE_WALLET,
        "বিকাশ" to PaymentMethod.MOBILE_WALLET,
        "nagad" to PaymentMethod.MOBILE_WALLET,
        "rocket" to PaymentMethod.MOBILE_WALLET,
        "wallet" to PaymentMethod.MOBILE_WALLET,
        "card" to PaymentMethod.CARD,
        "কার্ড" to PaymentMethod.CARD,
        "cheque" to PaymentMethod.CHEQUE,
        "check" to PaymentMethod.CHEQUE,
        "চেক" to PaymentMethod.CHEQUE,
    )

    /** What an expense was for. Anything unrecognised stays [ExpenseCategory.OTHER]. */
    val expenseCategories: Map<String, ExpenseCategory> = mapOf(
        "software" to ExpenseCategory.SOFTWARE,
        "subscription" to ExpenseCategory.SOFTWARE,
        "hosting" to ExpenseCategory.SOFTWARE,
        "domain" to ExpenseCategory.SOFTWARE,
        "adobe" to ExpenseCategory.SOFTWARE,
        "সফটওয়্যার" to ExpenseCategory.SOFTWARE,
        "hardware" to ExpenseCategory.HARDWARE,
        "laptop" to ExpenseCategory.HARDWARE,
        "monitor" to ExpenseCategory.HARDWARE,
        "ল্যাপটপ" to ExpenseCategory.HARDWARE,
        "travel" to ExpenseCategory.TRAVEL,
        "uber" to ExpenseCategory.TRAVEL,
        "cng" to ExpenseCategory.TRAVEL,
        "rickshaw" to ExpenseCategory.TRAVEL,
        "flight" to ExpenseCategory.TRAVEL,
        "ভ্রমণ" to ExpenseCategory.TRAVEL,
        "food" to ExpenseCategory.FOOD,
        "lunch" to ExpenseCategory.FOOD,
        "dinner" to ExpenseCategory.FOOD,
        "khabar" to ExpenseCategory.FOOD,
        "খাবার" to ExpenseCategory.FOOD,
        "rent" to ExpenseCategory.RENT,
        "bhara" to ExpenseCategory.RENT,
        "ভাড়া" to ExpenseCategory.RENT,
        "electricity" to ExpenseCategory.UTILITIES,
        "internet" to ExpenseCategory.UTILITIES,
        "utility" to ExpenseCategory.UTILITIES,
        "বিদ্যুৎ" to ExpenseCategory.UTILITIES,
        "ইন্টারনেট" to ExpenseCategory.UTILITIES,
        "marketing" to ExpenseCategory.MARKETING,
        "ads" to ExpenseCategory.MARKETING,
        "advertising" to ExpenseCategory.MARKETING,
        "বিজ্ঞাপন" to ExpenseCategory.MARKETING,
        "lawyer" to ExpenseCategory.PROFESSIONAL_FEES,
        "accountant" to ExpenseCategory.PROFESSIONAL_FEES,
        "consultant" to ExpenseCategory.PROFESSIONAL_FEES,
        "tax" to ExpenseCategory.TAX,
        "vat" to ExpenseCategory.TAX,
        "ট্যাক্স" to ExpenseCategory.TAX,
        "ভ্যাট" to ExpenseCategory.TAX,
    )

    /**
     * True when [token] says this [trigger].
     *
     * Bengali script glues its case endings straight onto the noun — "খরচ" turns up as "খরচের"
     * and "নাম্বার" as "নাম্বারটা" — and there is no stemmer on the phone, so a Bengali trigger
     * matches a token that opens with it. Latin spellings stay exact: too many English words
     * open with another English word.
     */
    fun triggerMatches(trigger: String, token: String): Boolean =
        token == trigger || (isBengali(trigger) && token.startsWith(trigger))

    private fun isBengali(text: String): Boolean =
        text.any { it in BENGALI_BLOCK_START..BENGALI_BLOCK_END }

    private const val BENGALI_BLOCK_START = 'ঀ'
    private const val BENGALI_BLOCK_END = '৿'
}
