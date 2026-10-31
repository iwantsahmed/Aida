package com.rayluxeo.aida.ai.nlu

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The classifier only ever sees canonical tokens, so a Bengali, Banglish or English phrasing of
 * one request reaches it as the same string. What differs between the languages and is worth
 * asserting here is word ORDER: English leads with the verb and Bengali ends with it, so the
 * same request arrives with its trigger at opposite ends of the sentence.
 */
class CommandClassifierTest {

    private val classifier = CommandClassifier()

    private fun intentOf(normalized: String, signals: IntentSignals = IntentSignals()): MiraIntent? =
        classifier.classify(normalized, signals)?.intent

    private fun confidenceOf(normalized: String, signals: IntentSignals = IntentSignals()): Float =
        classifier.classify(normalized, signals)?.confidence ?: 0f

    // ------------------------------------------------------------------ work

    @Test
    fun `recognises a task in either word order`() {
        assertThat(intentOf("create a task to call ahmed")).isEqualTo(MiraIntent.CREATE_TASK)
        assertThat(intentOf("ahmed to call do task create")).isEqualTo(MiraIntent.CREATE_TASK)
    }

    /**
     * Bengali asks for a task without a word for one. "Ahmed ke proposal pathate hobe" is an
     * obligation, and the auxiliary carrying it is all the evidence there is.
     */
    @Test
    fun `recognises an obligation as a task with no task word in the sentence`() {
        assertThat(intentOf("tomorrow ahmed to proposal pathate hobe", TIMED))
            .isEqualTo(MiraIntent.CREATE_TASK)
        assertThat(intentOf("ahmed to call do হবে")).isEqualTo(MiraIntent.CREATE_TASK)
    }

    @Test
    fun `recognises an update`() {
        assertThat(intentOf("reschedule the ahmed call to tomorrow at 10", TIMED))
            .isEqualTo(MiraIntent.UPDATE_TASK)
        assertThat(intentOf("ahmed er task ta tomorrow 10 at pichiye give", TIMED))
            .isEqualTo(MiraIntent.UPDATE_TASK)
        assertThat(intentOf("update the ahmed task", TIMED)).isEqualTo(MiraIntent.UPDATE_TASK)
    }

    @Test
    fun `recognises a completion in either word order`() {
        assertThat(intentOf("mark the ahmed call as done")).isEqualTo(MiraIntent.COMPLETE_TASK)
        assertThat(intentOf("call ahmed task ta done")).isEqualTo(MiraIntent.COMPLETE_TASK)
    }

    @Test
    fun `recognises a task search`() {
        assertThat(intentOf("search invoice")).isEqualTo(MiraIntent.SEARCH_TASKS)
        assertThat(intentOf("show my pending task")).isEqualTo(MiraIntent.SEARCH_TASKS)
        assertThat(intentOf("today task gulo search", TIMED)).isEqualTo(MiraIntent.SEARCH_TASKS)
        assertThat(intentOf("task দেখাও")).isEqualTo(MiraIntent.SEARCH_TASKS)
    }

    @Test
    fun `recognises a request for the daily brief`() {
        assertThat(intentOf("give me the brief")).isEqualTo(MiraIntent.GET_DAILY_BRIEF)
        assertThat(intentOf("brief give")).isEqualTo(MiraIntent.GET_DAILY_BRIEF)
    }

    // ------------------------------------------------------------------ time

    @Test
    fun `recognises a reminder in either word order`() {
        assertThat(intentOf("remind me tomorrow at 10 to call ahmed", TIMED))
            .isEqualTo(MiraIntent.CREATE_REMINDER)
        assertThat(intentOf("tomorrow morning 10 at ahmed to call do remind give", TIMED))
            .isEqualTo(MiraIntent.CREATE_REMINDER)
    }

    @Test
    fun `recognises an alarm`() {
        assertThat(intentOf("set an alarm for tomorrow at 6", TIMED)).isEqualTo(MiraIntent.CREATE_ALARM)
        assertThat(intentOf("tomorrow morning 6 at alarm give", TIMED)).isEqualTo(MiraIntent.CREATE_ALARM)
    }

    @Test
    fun `recognises a timer`() {
        val signals = IntentSignals(hasTemporal = true, hasDuration = true)
        assertThat(intentOf("set a timer for 20 minutes", signals)).isEqualTo(MiraIntent.CREATE_TIMER)
        assertThat(intentOf("20 minute timer give", signals)).isEqualTo(MiraIntent.CREATE_TIMER)
    }

    @Test
    fun `recognises a calendar event`() {
        assertThat(intentOf("create a calendar event with ahmed tomorrow at 4 pm", TIMED))
            .isEqualTo(MiraIntent.CREATE_CALENDAR_EVENT)
        assertThat(intentOf("tomorrow afternoon 4 at ahmed er sathe meeting", TIMED))
            .isEqualTo(MiraIntent.CREATE_CALENDAR_EVENT)
    }

    // ------------------------------------------------------------------ knowledge

    @Test
    fun `recognises a note`() {
        assertThat(intentOf("create a note that ahmed wants the dhaka office"))
            .isEqualTo(MiraIntent.CREATE_NOTE)
        assertThat(intentOf("note লিখো ahmed dhaka office")).isEqualTo(MiraIntent.CREATE_NOTE)
    }

    @Test
    fun `recognises a note search`() {
        assertThat(intentOf("search the note about dhaka")).isEqualTo(MiraIntent.SEARCH_NOTES)
        assertThat(intentOf("dhaka er note search")).isEqualTo(MiraIntent.SEARCH_NOTES)
    }

    @Test
    fun `recognises a memory being stored`() {
        assertThat(intentOf("remember that ahmed prefers morning call"))
            .isEqualTo(MiraIntent.STORE_MEMORY)
        assertThat(intentOf("ahmed er jonmodin remember")).isEqualTo(MiraIntent.STORE_MEMORY)
    }

    @Test
    fun `recognises a memory being recalled`() {
        assertThat(intentOf("search my memory for ahmed")).isEqualTo(MiraIntent.SEARCH_MEMORY)
        assertThat(intentOf("ahmed somporke ki mone ache")).isEqualTo(MiraIntent.SEARCH_MEMORY)
        assertThat(intentOf("ahmed সম্পর্কে কি মনে আছে")).isEqualTo(MiraIntent.SEARCH_MEMORY)
    }

    /**
     * Both memory intents are about remembering, and only one of them is a search. Storing must
     * not be reached through a word the recall side also owns.
     */
    @Test
    fun `tells storing a memory apart from recalling one`() {
        assertThat(intentOf("remember ahmed birthday")).isEqualTo(MiraIntent.STORE_MEMORY)
        assertThat(intentOf("memory search ahmed")).isEqualTo(MiraIntent.SEARCH_MEMORY)
    }

    // ------------------------------------------------------------------ business

    @Test
    fun `recognises a client being created`() {
        assertThat(intentOf("create a client ahmed rahman")).isEqualTo(MiraIntent.CREATE_CLIENT)
        assertThat(intentOf("create client ahmed rahman")).isEqualTo(MiraIntent.CREATE_CLIENT)
    }

    @Test
    fun `recognises a client search`() {
        assertThat(intentOf("search the client ahmed")).isEqualTo(MiraIntent.SEARCH_CLIENTS)
        assertThat(intentOf("client ahmed to search")).isEqualTo(MiraIntent.SEARCH_CLIENTS)
    }

    /**
     * "show"/"list" are list verbs, the same ones SEARCH_TASKS already carries. Clients and notes
     * were missing them, so "show my clients" found no search signal and fell through to
     * CREATE_CLIENT — which fires on a bare "client" — and invented a client named "show".
     */
    @Test
    fun `listing clients or notes is a search, not a create`() {
        assertThat(intentOf("show my clients")).isEqualTo(MiraIntent.SEARCH_CLIENTS)
        assertThat(intentOf("list my clients")).isEqualTo(MiraIntent.SEARCH_CLIENTS)
        assertThat(intentOf("show clients")).isEqualTo(MiraIntent.SEARCH_CLIENTS)
        assertThat(intentOf("show my notes")).isEqualTo(MiraIntent.SEARCH_NOTES)
        // Creating a client with a name is still a create.
        assertThat(intentOf("create a client ahmed rahman")).isEqualTo(MiraIntent.CREATE_CLIENT)
    }

    @Test
    fun `recognises an invoice draft`() {
        assertThat(intentOf("create an invoice for ahmed for 50000 taka", PAID))
            .isEqualTo(MiraIntent.CREATE_INVOICE_DRAFT)
        assertThat(intentOf("ahmed er jonno 50 thousand taka invoice create", PAID))
            .isEqualTo(MiraIntent.CREATE_INVOICE_DRAFT)
    }

    /**
     * "bill" is also the everyday word for a thing Abu PAYS, so a sentence about paying or merely
     * naming one must not become a request to issue one. This is the classifier half of the defect
     * where "pay the electricity bill before the 25th" drafted an invoice to "25 taka". The weak
     * "bill" trigger needs a stated amount to carry a draft; it is not enough on its own or at the
     * edge of a short sentence.
     */
    @Test
    fun `paying or naming a bill is not drafting an invoice`() {
        assertThat(intentOf("pay the electricity bill before the 25th"))
            .isNotEqualTo(MiraIntent.CREATE_INVOICE_DRAFT)
        // Short sentence: "bill" lands at the edge but still must not draft without an amount.
        assertThat(intentOf("pay the internet bill"))
            .isNotEqualTo(MiraIntent.CREATE_INVOICE_DRAFT)
        assertThat(intentOf("paying the internet bill this week"))
            .isNotEqualTo(MiraIntent.CREATE_INVOICE_DRAFT)
        // A bare mid-sentence "bill" with no corroboration is too weak to carry a draft.
        assertThat(intentOf("the electricity bill for this month"))
            .isNotEqualTo(MiraIntent.CREATE_INVOICE_DRAFT)
        // But "bill" as a verb at the edge, with an amount, is still a draft.
        assertThat(intentOf("bill ahmed 50000 taka", PAID)).isEqualTo(MiraIntent.CREATE_INVOICE_DRAFT)
        // The strong "invoice" word is NOT penalised by a stray "pay": an invoice is a thing the
        // client pays, so "to pay" / "will pay" belong in a perfectly ordinary draft.
        assertThat(intentOf("create an invoice for ahmed to pay", PAID))
            .isEqualTo(MiraIntent.CREATE_INVOICE_DRAFT)
        assertThat(intentOf("create an invoice for ahmed to pay"))
            .isEqualTo(MiraIntent.CREATE_INVOICE_DRAFT)
    }

    @Test
    fun `recognises a quote draft, and keeps it distinct from an invoice`() {
        assertThat(intentOf("quote ahmed for 50000 taka", PAID))
            .isEqualTo(MiraIntent.CREATE_QUOTE_DRAFT)
        assertThat(intentOf("ahmed er jonno 50 thousand taka quotation create", PAID))
            .isEqualTo(MiraIntent.CREATE_QUOTE_DRAFT)
        // A quote word must not pull a plain invoice onto the quote row, nor the reverse.
        assertThat(intentOf("create an invoice for ahmed for 50000 taka", PAID))
            .isEqualTo(MiraIntent.CREATE_INVOICE_DRAFT)
    }

    @Test
    fun `recognises a client statement, and keeps it distinct from an invoice query`() {
        assertThat(intentOf("show ahmed statement")).isEqualTo(MiraIntent.QUERY_CLIENT_STATEMENT)
        assertThat(intentOf("ahmed er statement")).isEqualTo(MiraIntent.QUERY_CLIENT_STATEMENT)
        // Asking what is outstanding is still the invoice query, not a whole statement.
        assertThat(intentOf("ahmed er invoice koto baki")).isEqualTo(MiraIntent.QUERY_INVOICE)
    }

    // ------------------------------------------------------------------ money

    @Test
    fun `recognises a payment`() {
        assertThat(intentOf("record a payment of 50000 taka from ahmed", PAID))
            .isEqualTo(MiraIntent.RECORD_PAYMENT)
        assertThat(intentOf("ahmed er 50 thousand taka payment peyechi", PAID))
            .isEqualTo(MiraIntent.RECORD_PAYMENT)
    }

    @Test
    fun `recognises an expense`() {
        assertThat(intentOf("expense 1200 taka on lunch", PAID)).isEqualTo(MiraIntent.RECORD_EXPENSE)
        assertThat(intentOf("lunch e 1200 taka expense", PAID)).isEqualTo(MiraIntent.RECORD_EXPENSE)
        assertThat(intentOf("1200 taka খাবার expense", PAID)).isEqualTo(MiraIntent.RECORD_EXPENSE)
    }

    @Test
    fun `recognises income`() {
        assertThat(intentOf("received 50000 taka from ahmed as income", PAID))
            .isEqualTo(MiraIntent.RECORD_INCOME)
        assertThat(intentOf("ahmed theke 50 thousand taka income peyechi", PAID))
            .isEqualTo(MiraIntent.RECORD_INCOME)
    }

    /**
     * All three money commands carry an amount and a name. What separates them is one word, and
     * getting it wrong files Abu's spending as his earnings.
     */
    @Test
    fun `tells an expense from income and both from a payment`() {
        assertThat(intentOf("1200 taka expense", PAID)).isEqualTo(MiraIntent.RECORD_EXPENSE)
        assertThat(intentOf("50000 taka income peyechi", PAID)).isEqualTo(MiraIntent.RECORD_INCOME)
        // Money against an invoice is a payment even when Abu says he received it.
        assertThat(intentOf("ahmed er invoice er 50000 taka payment peyechi", PAID))
            .isEqualTo(MiraIntent.RECORD_PAYMENT)
    }

    @Test
    fun `recognises an income query`() {
        assertThat(intentOf("how much income this month")).isEqualTo(MiraIntent.QUERY_INCOME)
        assertThat(intentOf("koto income")).isEqualTo(MiraIntent.QUERY_INCOME)
        assertThat(intentOf("কত আয়")).isEqualTo(MiraIntent.QUERY_INCOME)
    }

    /**
     * The query and the record share the word "income". A question about it looks something up,
     * while a bare amount records it — getting it wrong files Abu's earnings as a question.
     */
    @Test
    fun `an income query is told apart from recording income`() {
        assertThat(intentOf("income 5000 from ahmed", PAID)).isEqualTo(MiraIntent.RECORD_INCOME)
        assertThat(intentOf("koto income")).isEqualTo(MiraIntent.QUERY_INCOME)
    }

    @Test
    fun `recognises a calculation from the arithmetic alone`() {
        assertThat(intentOf("12 * 4", COUNTED)).isEqualTo(MiraIntent.PERFORM_CALCULATION)
        assertThat(intentOf("what is 12 * 4", COUNTED)).isEqualTo(MiraIntent.PERFORM_CALCULATION)
        assertThat(intentOf("12 * 4 calculate", COUNTED)).isEqualTo(MiraIntent.PERFORM_CALCULATION)
    }

    /** A number is not a sum. Most of what Abu says has a number in it somewhere. */
    @Test
    fun `does not read every number as a calculation`() {
        assertThat(intentOf("remind me tomorrow at 10 to call ahmed", TIMED))
            .isEqualTo(MiraIntent.CREATE_REMINDER)
        assertThat(intentOf("1200 taka খাবার expense", PAID)).isEqualTo(MiraIntent.RECORD_EXPENSE)
        assertThat(intentOf("create client ahmed 01712345678")).isEqualTo(MiraIntent.CREATE_CLIENT)
    }

    // ------------------------------------------------------------------ the phone itself

    @Test
    fun `recognises a contact lookup`() {
        assertThat(intentOf("what is the number for ahmed")).isEqualTo(MiraIntent.FIND_CONTACT)
        assertThat(intentOf("ahmed er number calculate")).isEqualTo(MiraIntent.FIND_CONTACT)
        assertThat(intentOf("ahmed এর নাম্বার calculate")).isEqualTo(MiraIntent.FIND_CONTACT)
    }

    @Test
    fun `recognises a call`() {
        assertThat(intentOf("call ahmed")).isEqualTo(MiraIntent.PREPARE_CALL)
        assertThat(intentOf("ahmed to call koro")).isEqualTo(MiraIntent.PREPARE_CALL)
        assertThat(intentOf("ahmed to call করো")).isEqualTo(MiraIntent.PREPARE_CALL)
    }

    @Test
    fun `recognises a message`() {
        assertThat(intentOf("message ahmed that i am late")).isEqualTo(MiraIntent.PREPARE_MESSAGE)
        assertThat(intentOf("ahmed to message koro je ami late")).isEqualTo(MiraIntent.PREPARE_MESSAGE)
    }

    // ------------------------------------------------------- the precedence traps

    @Test
    fun `a reminder beats a task when the sentence names both`() {
        // "reminder dao" sentences routinely mention the work the reminder is about.
        assertThat(intentOf("ahmed to call do task remind give", TIMED))
            .isEqualTo(MiraIntent.CREATE_REMINDER)
        assertThat(intentOf("task remind", TIMED)).isEqualTo(MiraIntent.CREATE_REMINDER)
    }

    @Test
    fun `a completion beats a task when the sentence names both`() {
        assertThat(intentOf("call ahmed task done")).isEqualTo(MiraIntent.COMPLETE_TASK)
    }

    /**
     * Except when the sentence also asks for the task to be created. "complete" and "finish"
     * are the commonest words inside a task's own title, and reading "website complete korar
     * task add koro" as a completion marked something done instead of writing it down.
     */
    @Test
    fun `a task to finish something is a task, not a completion`() {
        assertThat(intentOf("next friday website complete do task create করো", TIMED))
            .isEqualTo(MiraIntent.CREATE_TASK)
        assertThat(intentOf("website complete do task create")).isEqualTo(MiraIntent.CREATE_TASK)
        assertThat(intentOf("tonight homepage finish do task create করো", TIMED))
            .isEqualTo(MiraIntent.CREATE_TASK)
        // Nothing in these asks for anything to be created, so they are completions still.
        assertThat(intentOf("website complete do")).isEqualTo(MiraIntent.COMPLETE_TASK)
        assertThat(intentOf("call ahmed task ta done")).isEqualTo(MiraIntent.COMPLETE_TASK)
    }

    /** A repeat says how often, never what was asked for. The intent is decided without it. */
    @Test
    fun `a repeat does not change what Abu asked for`() {
        assertThat(intentOf("every monday remind me to review rayluxeo", TIMED))
            .isEqualTo(MiraIntent.CREATE_REMINDER)
        assertThat(intentOf("protidin website check do task create")).isEqualTo(MiraIntent.CREATE_TASK)
    }

    /** An alarm is a bare time with no subject; a reminder is about something. */
    @Test
    fun `an alarm wins on a bare time and stands down to a reminder`() {
        assertThat(intentOf("tomorrow morning 6 at alarm give", TIMED)).isEqualTo(MiraIntent.CREATE_ALARM)
        assertThat(intentOf("tomorrow morning 6 at alarm remind give", TIMED))
            .isEqualTo(MiraIntent.CREATE_REMINDER)
    }

    @Test
    fun `a call is a call only when nothing else was asked for`() {
        assertThat(intentOf("call ahmed")).isEqualTo(MiraIntent.PREPARE_CALL)
        assertThat(intentOf("remind me to call ahmed")).isEqualTo(MiraIntent.CREATE_REMINDER)
        assertThat(intentOf("create a task to call ahmed")).isEqualTo(MiraIntent.CREATE_TASK)
        assertThat(intentOf("call ahmed task ta done")).isEqualTo(MiraIntent.COMPLETE_TASK)
    }

    /** "Ahmed ke call koro" is a call; "Ahmed er number koto" is a lookup. */
    @Test
    fun `a lookup and a call are told apart by which one Abu asked for`() {
        assertThat(intentOf("ahmed er number calculate")).isEqualTo(MiraIntent.FIND_CONTACT)
        assertThat(intentOf("ahmed to call koro")).isEqualTo(MiraIntent.PREPARE_CALL)
        // Asking for the number so that he can call is asking for the call.
        assertThat(intentOf("ahmed er number to call koro")).isEqualTo(MiraIntent.PREPARE_CALL)
    }

    /**
     * Every search reaches for the same word. The narrower search has to win on the object it
     * names without the broader one standing down for a word they both own.
     */
    @Test
    fun `a search lands on whatever Abu named, and on tasks when he named nothing`() {
        assertThat(intentOf("search invoice")).isEqualTo(MiraIntent.SEARCH_TASKS)
        assertThat(intentOf("search the note about dhaka")).isEqualTo(MiraIntent.SEARCH_NOTES)
        assertThat(intentOf("search the client ahmed")).isEqualTo(MiraIntent.SEARCH_CLIENTS)
        assertThat(intentOf("search my memory for ahmed")).isEqualTo(MiraIntent.SEARCH_MEMORY)
        assertThat(intentOf("search the number for ahmed")).isEqualTo(MiraIntent.FIND_CONTACT)
    }

    @Test
    fun `looking an invoice up is not drafting one`() {
        assertThat(intentOf("search invoice")).isEqualTo(MiraIntent.SEARCH_TASKS)
        assertThat(intentOf("create an invoice for ahmed for 50000 taka", PAID))
            .isEqualTo(MiraIntent.CREATE_INVOICE_DRAFT)
    }

    // ------------------------------------------------------------------ the machinery

    @Test
    fun `a time expression strengthens a reminder`() {
        val withTime = confidenceOf("remind me to call ahmed", TIMED)
        val withoutTime = confidenceOf("remind me to call ahmed")
        assertThat(intentOf("remind me to call ahmed", TIMED)).isEqualTo(MiraIntent.CREATE_REMINDER)
        assertThat(intentOf("remind me to call ahmed")).isEqualTo(MiraIntent.CREATE_REMINDER)
        assertThat(withTime).isGreaterThan(withoutTime)
    }

    @Test
    fun `an amount of money strengthens an expense`() {
        assertThat(confidenceOf("1200 taka expense", PAID))
            .isGreaterThan(confidenceOf("1200 taka expense"))
    }

    @Test
    fun `refuses to guess at gibberish`() {
        assertThat(classifier.classify("asdkjh qwe zxc")).isNull()
        assertThat(classifier.classify("blorf")).isNull()
        assertThat(classifier.classify("")).isNull()
    }

    @Test
    fun `clamps confidence into the unit range however strong the evidence`() {
        val strong = requireNotNull(classifier.classify("calculate 12 * 4", COUNTED))
        assertThat(strong.score).isGreaterThan(1.6f)
        assertThat(strong.confidence).isEqualTo(1f)
    }

    @Test
    fun `explains itself`() {
        val match = requireNotNull(classifier.classify("remind me to call ahmed", TIMED))
        assertThat(match.evidence).isNotEmpty()
        assertThat(match.evidence.any { it.contains(CanonicalToken.REMIND) }).isTrue()
        assertThat(match.evidence.any { it.contains("time expression") }).isTrue()
        assertThat(match.evidence.any { it.contains("edge") }).isTrue()
    }

    @Test
    fun `matches a phrase only as a run`() {
        assertThat(intentOf("ahmed somporke ki mone ache")).isEqualTo(MiraIntent.SEARCH_MEMORY)
        // "mone" on its own means "in mind" and argues for nothing.
        assertThat(classifier.classify("mone ahmed")).isNull()
    }

    /** Bengali glues its case endings onto the noun, so a trigger has to survive one. */
    @Test
    fun `reads a Bengali trigger that carries a case ending`() {
        assertThat(intentOf("ahmed নাম্বারটা calculate")).isEqualTo(MiraIntent.FIND_CONTACT)
    }

    @Test
    fun `recognises query schedule`() {
        assertThat(intentOf("what do i have tomorrow", TIMED)).isEqualTo(MiraIntent.QUERY_SCHEDULE)
        assertThat(intentOf("show schedule for today", TIMED)).isEqualTo(MiraIntent.QUERY_SCHEDULE)
    }

    @Test
    fun `recognises find free time`() {
        assertThat(intentOf("when am i free thursday", TIMED)).isEqualTo(MiraIntent.FIND_FREE_TIME)
        assertThat(intentOf("check free time tomorrow", TIMED)).isEqualTo(MiraIntent.FIND_FREE_TIME)
    }

    @Test
    fun `recognises update calendar event`() {
        assertThat(intentOf("reschedule meeting with ahmed to 4 pm", TIMED)).isEqualTo(MiraIntent.UPDATE_CALENDAR_EVENT)
    }

    @Test
    fun `recognises delete calendar event`() {
        assertThat(intentOf("cancel meeting with ahmed")).isEqualTo(MiraIntent.DELETE_CALENDAR_EVENT)
        assertThat(intentOf("delete calendar event review")).isEqualTo(MiraIntent.DELETE_CALENDAR_EVENT)
    }

    @Test
    fun `recognises text message`() {
        assertThat(intentOf("text ahmed i am running late")).isEqualTo(MiraIntent.PREPARE_MESSAGE)
        assertThat(intentOf("whatsapp ahmed i will be there")).isEqualTo(MiraIntent.PREPARE_MESSAGE)
    }

    // ------------------------------------------------------------------ the table itself

    @Test
    fun `every intent Mira can act on has a rule`() {
        assertThat(Lexicon.byIntent.keys).containsExactlyElementsIn(MiraIntent.entries)
    }

    /**
     * Two intents that each stand down for the other cancel out and leave Abu with "I did not
     * follow" for a sentence Mira understood perfectly well.
     */
    @Test
    fun `no two intents stand down for each other`() {
        for (rule in Lexicon.rules) {
            for (stronger in rule.yieldsTo) {
                assertThat(Lexicon.byIntent.getValue(stronger).yieldsTo).doesNotContain(rule.intent)
            }
        }
    }

    @Test
    fun `no intent stands down for itself`() {
        for (rule in Lexicon.rules) {
            assertThat(rule.yieldsTo).doesNotContain(rule.intent)
        }
    }

    // ------------------------------------------------------------------ small talk

    @Test
    fun `recognises a bare greeting as chit-chat`() {
        assertThat(intentOf("hey bro")).isEqualTo(MiraIntent.CHITCHAT)
        assertThat(intentOf("hi")).isEqualTo(MiraIntent.CHITCHAT)
        assertThat(intentOf("hello")).isEqualTo(MiraIntent.CHITCHAT)
        assertThat(intentOf("assalamu alaikum")).isEqualTo(MiraIntent.CHITCHAT)
        assertThat(intentOf("kemon acho")).isEqualTo(MiraIntent.CHITCHAT)
        assertThat(intentOf("হ্যালো")).isEqualTo(MiraIntent.CHITCHAT)
        assertThat(intentOf("কেমন আছেন")).isEqualTo(MiraIntent.CHITCHAT)
    }

    /** A greeting that prefaces a real request must never swallow it. */
    @Test
    fun `a greeting before a command yields to the command`() {
        assertThat(intentOf("hey remind me to call ahmed")).isEqualTo(MiraIntent.CREATE_REMINDER)
        assertThat(intentOf("hi show schedule for today", TIMED)).isEqualTo(MiraIntent.QUERY_SCHEDULE)
    }

    // --------------------------------------------------- smarter-assistant verbs

    /** "reminder" folds to REMIND, so these needed their own verb to beat creation and search. */
    @Test
    fun `reminder management classifies to its own intent, not to creating a reminder`() {
        assertThat(intentOf("what remind do i have")).isEqualTo(MiraIntent.QUERY_REMINDERS)
        assertThat(intentOf("show my remind")).isEqualTo(MiraIntent.QUERY_REMINDERS)
        assertThat(intentOf("snooze the call remind", DURATION)).isEqualTo(MiraIntent.SNOOZE_REMINDER)
        assertThat(intentOf("cancel the dentist remind")).isEqualTo(MiraIntent.CANCEL_REMINDER)
    }

    /** The collisions that make this track risky: each must land on the right side. */
    @Test
    fun `the reminder verbs do not steal creating a reminder, rescheduling a task, or a calendar delete`() {
        // A creating temporal+subject is a NEW reminder, not a query/snooze/cancel of an old one.
        assertThat(intentOf("remind me tomorrow at 5 to call the bank", TIMED))
            .isEqualTo(MiraIntent.CREATE_REMINDER)
        // "pichiye" on a TASK is still rescheduling a task; only a reminder snooze names a reminder.
        assertThat(intentOf("ahmed er task ta pichiye dao", TIMED)).isEqualTo(MiraIntent.UPDATE_TASK)
        // Cancelling a meeting is a calendar delete, never a reminder cancel.
        assertThat(intentOf("cancel the meeting with ahmed")).isEqualTo(MiraIntent.DELETE_CALENDAR_EVENT)
    }

    /** Deleting a task is scoped to TASK, apart from completing it or deleting a meeting. */
    @Test
    fun `delete task classifies apart from complete task and a calendar delete`() {
        assertThat(intentOf("delete the task buy milk")).isEqualTo(MiraIntent.DELETE_TASK)
        assertThat(intentOf("buy milk task muche felo")).isEqualTo(MiraIntent.DELETE_TASK)
        // Completing is not deleting.
        assertThat(intentOf("mark buy milk task as done")).isEqualTo(MiraIntent.COMPLETE_TASK)
    }

    /** Forgetting a fact is the "forget" verb, apart from recalling or storing one. */
    @Test
    fun `forget a fact classifies apart from searching memory`() {
        assertThat(intentOf("forget that ahmed likes tea")).isEqualTo(MiraIntent.FORGET_FACT)
        assertThat(intentOf("ahmed er kotha ভুলে যাও")).isEqualTo(MiraIntent.FORGET_FACT)
        // Recall is a search, not a forget.
        assertThat(intentOf("recall what ahmed likes")).isEqualTo(MiraIntent.SEARCH_MEMORY)
    }

    private companion object {
        val TIMED = IntentSignals(hasTemporal = true)
        val DURATION = IntentSignals(hasDuration = true)
        val PAID = IntentSignals(hasMoney = true)
        val COUNTED = IntentSignals(hasArithmetic = true)
    }
}
