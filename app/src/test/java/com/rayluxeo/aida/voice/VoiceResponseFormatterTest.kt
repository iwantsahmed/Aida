package com.rayluxeo.aida.voice

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.SpokenLanguage
import org.junit.Test

/**
 * The segmenting rule is pure string work, so the thing that actually matters about it — that Mira
 * does not swap voices over a name — is decided here rather than by listening on a device.
 *
 * Each case is written as a reply Mira could really produce: the phrasebooks answer in one language
 * and the data they quote (client names, task titles, "invoice") arrives in the other.
 */
class VoiceResponseFormatterTest {

    private val formatter = VoiceResponseFormatterImpl()

    @Test
    fun `a Bengali reply carrying an English name is one Bengali segment`() {
        val reply = "ঠিক আছে। কাল সকাল ১০টায় Ahmed-কে কল করার রিমাইন্ডার সেট করেছি।"

        val segments = formatter.segment(reply, SpokenLanguage.BENGALI)

        assertThat(segments).containsExactly(SpokenSegment(reply, SpokenLanguage.BENGALI))
    }

    @Test
    fun `an English reply is one English segment`() {
        val reply = "Done. Homepage is due tonight."

        val segments = formatter.segment(reply, SpokenLanguage.ENGLISH)

        assertThat(segments).containsExactly(SpokenSegment(reply, SpokenLanguage.ENGLISH))
    }

    @Test
    fun `English nouns inside a Bengali sentence never split it`() {
        val reply = "কাল Rahman Textiles-এর জন্য invoice পাঠাবো।"

        val segments = formatter.segment(reply, SpokenLanguage.BENGALI)

        assertThat(segments).containsExactly(SpokenSegment(reply, SpokenLanguage.BENGALI))
    }

    @Test
    fun `a whole English sentence inside a Bengali reply gets the English voice`() {
        val reply = "ঠিক আছে। The homepage design is due tonight. আমি মনে করিয়ে দেবো।"

        val segments = formatter.segment(reply, SpokenLanguage.BENGALI)

        assertThat(segments).containsExactly(
            SpokenSegment("ঠিক আছে।", SpokenLanguage.BENGALI),
            SpokenSegment("The homepage design is due tonight.", SpokenLanguage.ENGLISH),
            SpokenSegment("আমি মনে করিয়ে দেবো।", SpokenLanguage.BENGALI),
        ).inOrder()
    }

    @Test
    fun `a whole Bengali sentence inside an English reply gets the Bengali voice`() {
        val reply = "Done. আমি রিমাইন্ডার সেট করে দিয়েছি।"

        val segments = formatter.segment(reply, SpokenLanguage.ENGLISH)

        assertThat(segments).containsExactly(
            SpokenSegment("Done.", SpokenLanguage.ENGLISH),
            SpokenSegment("আমি রিমাইন্ডার সেট করে দিয়েছি।", SpokenLanguage.BENGALI),
        ).inOrder()
    }

    @Test
    fun `an English sentence too short to be a clause is not worth a switch`() {
        val reply = "ঠিক আছে। Ahmed."

        val segments = formatter.segment(reply, SpokenLanguage.BENGALI)

        assertThat(segments).containsExactly(SpokenSegment(reply, SpokenLanguage.BENGALI))
    }

    @Test
    fun `consecutive English sentences are spoken as one segment`() {
        val reply = "Homepage is due tonight. 10 items remain."

        val segments = formatter.segment(reply, SpokenLanguage.BENGALI)

        assertThat(segments).containsExactly(SpokenSegment(reply, SpokenLanguage.ENGLISH))
    }

    @Test
    fun `a bare time between two sentences does not break the run apart`() {
        val reply = "The invoice is ready. 10:30. Please check it now."

        val segments = formatter.segment(reply, SpokenLanguage.BENGALI)

        assertThat(segments).containsExactly(SpokenSegment(reply, SpokenLanguage.ENGLISH))
    }

    @Test
    fun `text with no sentence punctuation at all is one segment`() {
        val reply = "রিমাইন্ডার সেট করেছি"

        val segments = formatter.segment(reply, SpokenLanguage.BENGALI)

        assertThat(segments).containsExactly(SpokenSegment(reply, SpokenLanguage.BENGALI))
    }

    @Test
    fun `a decimal point is not read as the end of a sentence`() {
        val reply = "The invoice total is 12.5 lakh"

        val segments = formatter.segment(reply, SpokenLanguage.ENGLISH)

        assertThat(segments).containsExactly(SpokenSegment(reply, SpokenLanguage.ENGLISH))
    }

    @Test
    fun `a line break inside one segment is kept rather than trimmed away`() {
        val reply = "যোগ করেছি: Homepage redesign\nসময়: কাল সকাল ১০টা"

        val segments = formatter.segment(reply, SpokenLanguage.BENGALI)

        assertThat(segments).containsExactly(SpokenSegment(reply, SpokenLanguage.BENGALI))
    }

    @Test
    fun `empty text is nothing to say`() {
        assertThat(formatter.segment("", SpokenLanguage.BENGALI)).isEmpty()
        assertThat(formatter.segment("", SpokenLanguage.ENGLISH)).isEmpty()
    }

    @Test
    fun `whitespace is nothing to say`() {
        assertThat(formatter.segment("   \n\t ", SpokenLanguage.BENGALI)).isEmpty()
        assertThat(formatter.segment("   ", SpokenLanguage.ENGLISH)).isEmpty()
    }

    @Test
    fun `a reply is never partly lost between its segments`() {
        val reply = "ঠিক আছে। The homepage design is due tonight. আমি মনে করিয়ে দেবো।"

        val segments = formatter.segment(reply, SpokenLanguage.BENGALI)

        assertThat(segments.joinToString(" ") { it.text }).isEqualTo(reply)
    }

    @Test
    fun `no segment is blank and no two neighbours share a voice`() {
        val replies = listOf(
            "ঠিক আছে। The homepage design is due tonight. আমি মনে করিয়ে দেবো।",
            "Done. আমি রিমাইন্ডার সেট করে দিয়েছি।",
            "ঠিক আছে। ১০:৩০। কাজ শেষ।",
            "Homepage is due tonight. 10 items remain.",
        )

        for (reply in replies) {
            for (primary in SpokenLanguage.entries) {
                val segments = formatter.segment(reply, primary)

                assertThat(segments).isNotEmpty()
                assertThat(segments.map { it.text }.filter { it.isBlank() }).isEmpty()
                assertThat(segments.map { it.text }.filter { it != it.trim() }).isEmpty()
                assertThat(segments.zipWithNext().filter { (a, b) -> a.language == b.language })
                    .isEmpty()
            }
        }
    }
}
