package com.rayluxeo.aida.ai.nlu

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.SpokenLanguage
import org.junit.Test

class TitleExtractorTest {

    private val extractor = TitleExtractor()

    @Test
    fun `drops the command word and the leading infinitive`() {
        assertThat(
            extractor.extract(
                remainder = "remind me to call ahmed",
                raw = "Remind me tomorrow at 10 to call Ahmed.",
                language = SpokenLanguage.ENGLISH,
                intent = MiraIntent.CREATE_REMINDER,
            )
        ).isEqualTo("call Ahmed")
    }

    @Test
    fun `puts a Bengali object-verb phrase into verb-first order`() {
        assertThat(
            extractor.extract(
                remainder = "ahmed to call do remind give",
                raw = "Kal shokal 10 tay Ahmed ke call korar reminder dao.",
                language = SpokenLanguage.BENGALI,
                intent = MiraIntent.CREATE_REMINDER,
            )
        ).isEqualTo("call Ahmed")
    }

    @Test
    fun `leaves English word order alone`() {
        assertThat(
            extractor.extract(
                remainder = "remind me to send the invoice to ahmed",
                raw = "Remind me tomorrow to send the invoice to Ahmed",
                language = SpokenLanguage.ENGLISH,
                intent = MiraIntent.CREATE_REMINDER,
            )
        ).isEqualTo("send the invoice to Ahmed")
    }

    @Test
    fun `restores the capitalisation Abu typed`() {
        assertThat(
            extractor.extract(
                remainder = "call ahmed about the dhaka office",
                raw = "Remind me to call Ahmed about the Dhaka office",
                language = SpokenLanguage.ENGLISH,
                intent = MiraIntent.CREATE_REMINDER,
            )
        ).isEqualTo("call Ahmed about the Dhaka office")
    }

    /**
     * The same word is the request in one sentence and the subject of the next. "call" is noise
     * in "Ahmed ke call koro" and the whole point of the reminder that mentions it.
     */
    @Test
    fun `drops a word only when it named the request`() {
        assertThat(
            extractor.extract("ahmed to call do", raw = "Ahmed ke call koro", language = SpokenLanguage.BENGALI, intent = MiraIntent.PREPARE_CALL)
        ).isEqualTo("Ahmed")
        assertThat(
            extractor.extract("ahmed to call do", raw = "Ahmed ke call korar reminder dao", language = SpokenLanguage.BENGALI, intent = MiraIntent.CREATE_REMINDER)
        ).isEqualTo("call Ahmed")
    }

    /** An event is named after what it is, so the word that found the intent stays in the title. */
    @Test
    fun `keeps a trigger that is also the title`() {
        assertThat(
            extractor.extract("ahmed er sathe meeting", raw = "Kal bikal 4 tay Ahmed er sathe meeting", language = SpokenLanguage.BENGALI, intent = MiraIntent.CREATE_CALENDAR_EVENT)
        ).isEqualTo("Ahmed meeting")
    }

    @Test
    fun `drops Bengali grammar that is not an English word whatever the language`() {
        assertThat(
            extractor.extract("meeting er", raw = "2 ghonta pore meeting er reminder dao", language = SpokenLanguage.BENGALI, intent = MiraIntent.CREATE_REMINDER)
        ).isEqualTo("meeting")
        // "Ahmed er number koto" leans on one Bengali word and still reads as English.
        assertThat(
            extractor.extract("ahmed er number calculate", raw = "Ahmed er number koto", language = SpokenLanguage.ENGLISH, intent = MiraIntent.FIND_CONTACT)
        ).isEqualTo("Ahmed")
    }

    @Test
    fun `drops a particle that is also an English word only from a Bengali sentence`() {
        assertThat(
            extractor.extract("meeting ta", raw = "Meeting ta", language = SpokenLanguage.BENGALI, intent = MiraIntent.CREATE_NOTE)
        ).isEqualTo("Meeting")
        assertThat(
            extractor.extract("meeting ta", raw = "Meeting ta", language = SpokenLanguage.ENGLISH, intent = MiraIntent.CREATE_NOTE)
        ).isEqualTo("Meeting ta")
    }

    @Test
    fun `drops a stray single letter left behind by a Bengali suffix`() {
        assertThat(
            extractor.extract("e ahmed to call", raw = "Kal dupur e Ahmed ke call korar reminder dao", language = SpokenLanguage.BENGALI, intent = MiraIntent.CREATE_REMINDER)
        ).isEqualTo("call Ahmed")
    }

    @Test
    fun `returns null when nothing but request words is left`() {
        assertThat(
            extractor.extract("remind give", raw = "reminder dao", language = SpokenLanguage.BENGALI, intent = MiraIntent.CREATE_REMINDER)
        ).isNull()
        assertThat(
            extractor.extract("alarm give", raw = "Kal shokal 6 tay alarm dao", language = SpokenLanguage.BENGALI, intent = MiraIntent.CREATE_ALARM)
        ).isNull()
        assertThat(
            extractor.extract("", raw = "", language = SpokenLanguage.ENGLISH, intent = MiraIntent.CREATE_TASK)
        ).isNull()
    }

    @Test
    fun `trims connectors from both ends but keeps them inside`() {
        assertThat(
            extractor.extract("remind me to pay the bill for", raw = "Remind me to pay the bill for", language = SpokenLanguage.ENGLISH, intent = MiraIntent.CREATE_REMINDER)
        ).isEqualTo("pay the bill")
        assertThat(
            extractor.extract("record a payment of from ahmed", raw = "Record a payment of 50000 taka from Ahmed", language = SpokenLanguage.ENGLISH, intent = MiraIntent.RECORD_PAYMENT)
        ).isEqualTo("Ahmed")
    }

    @Test
    fun `drops a Bengali trigger that carries a case ending`() {
        assertThat(
            extractor.extract("ahmed নাম্বারটা calculate", raw = "Ahmed নাম্বারটা কত", language = SpokenLanguage.BENGALI, intent = MiraIntent.FIND_CONTACT)
        ).isEqualTo("Ahmed")
    }

    @Test
    fun `builds a title for every intent without failing on a missing rule`() {
        for (intent in MiraIntent.entries) {
            extractor.extract("ahmed to call do", raw = "Ahmed ke call koro", language = SpokenLanguage.BENGALI, intent = intent)
        }
    }

    // ---------------------------------------------------------------- framing compound verbs

    @Test
    fun `drops a write-it-down compound that framed the request`() {
        // "likhe rakho" is one verb meaning "write it down". Nothing describes the expense here,
        // so the extractor finds nothing rather than handing back half a verb.
        assertThat(
            extractor.extract(
                remainder = "expense likhe rakho",
                raw = "50 hajar takar expense likhe rakho",
                language = SpokenLanguage.BENGALI,
                intent = MiraIntent.RECORD_EXPENSE,
            )
        ).isNull()
    }

    @Test
    fun `drops the compound in Bengali script too`() {
        assertThat(
            extractor.extract(
                remainder = "expense লিখে রাখো",
                raw = "৫০ হাজার টাকার খরচ লিখে রাখো",
                language = SpokenLanguage.BENGALI,
                intent = MiraIntent.RECORD_EXPENSE,
            )
        ).isNull()
    }

    @Test
    fun `drops the compound in its polite register`() {
        assertThat(
            extractor.extract(
                remainder = "expense likhe rakhun",
                raw = "50 hajar takar expense likhe rakhun",
                language = SpokenLanguage.BENGALI,
                intent = MiraIntent.RECORD_EXPENSE,
            )
        ).isNull()
    }

    @Test
    fun `keeps what the request was actually about`() {
        assertThat(
            extractor.extract(
                remainder = "internet bill expense likhe rakho",
                raw = "internet bill 1200 takar expense likhe rakho",
                language = SpokenLanguage.BENGALI,
                intent = MiraIntent.RECORD_EXPENSE,
            )
        ).isEqualTo("internet bill")
    }

    @Test
    fun `a stray participle never lands in the middle of a title`() {
        // The verb-first pivot puts the title back in Mira's order, so a leaked "likhe" used to
        // surface between the verb and its object rather than at the end where it was typed.
        assertThat(
            extractor.extract(
                remainder = "ahmed to call do remind likhe rakho",
                raw = "Kal shokal 10 tay Ahmed ke call korar reminder likhe rakho",
                language = SpokenLanguage.BENGALI,
                intent = MiraIntent.CREATE_REMINDER,
            )
        ).isEqualTo("call Ahmed")
    }

    @Test
    fun `a leaked verb in a lookup is fatal, not untidy`() {
        // "Ahmed likhe" matches no one, and Abu is told the contact does not exist. "number" is
        // FIND_CONTACT's own trigger and goes with it, which leaves the name to search on.
        assertThat(
            extractor.extract(
                remainder = "ahmed number ta likhe rakho",
                raw = "Ahmed er number ta likhe rakho",
                language = SpokenLanguage.BENGALI,
                intent = MiraIntent.FIND_CONTACT,
            )
        ).isEqualTo("Ahmed")
    }

    // ---------------------------------------------------------------- and what it must NOT drop

    @Test
    fun `keeps the participle when Abu is reporting what he did`() {
        // The guard that makes the adjacency rule worth its complexity. "likhe felechi" is "I have
        // written it"; dropping the participle alone would leave "ami felechi" — "I threw it
        // away", the opposite of what he wrote.
        assertThat(
            extractor.extract(
                remainder = "ei blog post ta ami likhe felechi",
                raw = "mone rakho ei blog post ta ami likhe felechi",
                language = SpokenLanguage.BENGALI,
                intent = MiraIntent.STORE_MEMORY,
            )
        ).contains("likhe")
    }

    @Test
    fun `keeps a keeping verb that is the whole instruction`() {
        // "rekhe" only ever goes behind a writing participle. On its own it is the task.
        assertThat(
            extractor.extract(
                remainder = "ahmed jonno ekta copy rekhe dio",
                raw = "Ahmed er jonno ekta copy rekhe dio",
                language = SpokenLanguage.BENGALI,
                intent = MiraIntent.CREATE_TASK,
            )
        ).contains("rekhe")
    }

    // ------------------------------------------------------- English phrasal verbs

    /**
     * "Wake me up" is one verb and the particle carries none of its meaning. Dropping the verb
     * alone left the particle standing, and Abu's alarm list read "up".
     */
    @Test
    fun `drops the particle of a phrasal verb along with the verb`() {
        assertThat(
            extractor.extract(
                remainder = "wake me up",
                raw = "Wake me up at 7 am every weekday",
                language = SpokenLanguage.ENGLISH,
                intent = MiraIntent.CREATE_ALARM,
            )
        ).isNull()
    }

    @Test
    fun `drops the particle when the verb takes no object`() {
        assertThat(
            extractor.extract(
                remainder = "set up the rayluxeo review",
                raw = "Set up the Rayluxeo review tomorrow at 10",
                language = SpokenLanguage.ENGLISH,
                intent = MiraIntent.CREATE_TASK,
            )
        ).isEqualTo("Rayluxeo review")
    }

    /** The guard: a particle only goes with a verb the request was already dropping. */
    @Test
    fun `keeps a particle that belongs to what Abu asked for`() {
        assertThat(
            extractor.extract(
                remainder = "follow up with ahmed",
                raw = "Remind me tomorrow to follow up with Ahmed",
                language = SpokenLanguage.ENGLISH,
                intent = MiraIntent.CREATE_REMINDER,
            )
        ).isEqualTo("follow up with Ahmed")
    }

    @Test
    fun `keeps the name Abu gave an alarm`() {
        assertThat(
            extractor.extract(
                remainder = "set an alarm for fajr",
                raw = "Set an alarm for fajr at 5 am",
                language = SpokenLanguage.ENGLISH,
                intent = MiraIntent.CREATE_ALARM,
            )
        ).isEqualTo("fajr")
    }

    /**
     * "কি" frames a question and is dropped, but it is matched WHOLE. Matched as a prefix it also
     * claims "কিনতে" — "to buy" — and the reminder records the ink without the buying.
     */
    @Test
    fun `the question particle does not swallow the verb it starts`() {
        assertThat(
            extractor.extract(
                remainder = "প্রিন্টার কালি কিনতে হবে",
                raw = "কাল প্রিন্টার কালি কিনতে হবে",
                language = SpokenLanguage.BENGALI,
                intent = MiraIntent.CREATE_REMINDER,
            )
        ).contains("কিনতে")
    }

    /** The other half: a real "কি" still goes, or every question keeps its question word. */
    @Test
    fun `the question particle itself is still dropped`() {
        assertThat(
            extractor.extract(
                remainder = "আহমেদের নম্বর কি",
                raw = "আহমেদের নম্বর কি",
                language = SpokenLanguage.BENGALI,
                intent = MiraIntent.FIND_CONTACT,
            )
        ).doesNotContain("কি")
    }
}
