package com.rayluxeo.aida.ui.response

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.model.ConversationStyle
import org.junit.Test

/**
 * The three conversation styles change how much Mira says around an answer, never the answer.
 *
 * [ConversationStyle.BALANCED] must stay byte-for-byte what Mira has always said — the rest of the
 * renderer suite pins those strings, so here BALANCED is checked against the same literals the
 * phrasebooks shipped with. [ConversationStyle.CONCISE] strips the framing; [ConversationStyle.DETAILED]
 * adds one truthful line, and in every case the figure and the count are identical to BALANCED.
 */
class ConversationStyleRenderingTest {

    private class FixedStyle(private val style: ConversationStyle) : ConversationStyleProvider {
        override fun current(): ConversationStyle = style
    }

    private fun renderer(style: ConversationStyle) = MiraResponseRenderer(
        FixedClock(testInstant(TEST_TODAY, 9, 0), TEST_ZONE),
        FixedStyle(style),
    )

    private fun speak(style: ConversationStyle, response: MiraResponse, language: SpokenLanguage) =
        renderer(style).speak(response, language)

    private fun display(style: ConversationStyle, response: MiraResponse, language: SpokenLanguage) =
        renderer(style).display(response, language)

    // --- English ---------------------------------------------------------------------------------

    @Test
    fun `english greeting drops the invitation when concise`() {
        val greeting = MiraResponse.Greeting

        val concise = speak(ConversationStyle.CONCISE, greeting, SpokenLanguage.ENGLISH)
        val balanced = speak(ConversationStyle.BALANCED, greeting, SpokenLanguage.ENGLISH)
        val detailed = speak(ConversationStyle.DETAILED, greeting, SpokenLanguage.ENGLISH)

        assertThat(concise).isEqualTo("Good morning, Abu.")
        assertThat(balanced).isEqualTo("Good morning, Abu. What can I do for you?")
        // A bare greeting has no truthful extra line, so DETAILED reads as BALANCED does.
        assertThat(detailed).isEqualTo(balanced)
        assertThat(concise.length).isLessThan(balanced.length)
    }

    @Test
    fun `english income summary keeps the amount but varies the framing`() {
        val report = MiraResponse.IncomeSummaryReport(totalReceived = taka("225000"), count = 12)

        val concise = speak(ConversationStyle.CONCISE, report, SpokenLanguage.ENGLISH)
        val balanced = speak(ConversationStyle.BALANCED, report, SpokenLanguage.ENGLISH)
        val detailed = speak(ConversationStyle.DETAILED, report, SpokenLanguage.ENGLISH)

        assertThat(concise).isEqualTo("BDT 2,25,000.")
        assertThat(balanced).isEqualTo("Income this period: BDT 2,25,000 across 12 entries.")
        assertThat(detailed).isEqualTo(
            "Income this period: BDT 2,25,000 across 12 entries. " +
                "That covers every income entry logged for the period.",
        )
        // The figure and the count never move between styles — only the framing.
        assertThat(concise).contains("BDT 2,25,000")
        assertThat(detailed).startsWith(balanced)
        assertThat(detailed.length).isGreaterThan(balanced.length)
        assertThat(concise.length).isLessThan(balanced.length)
    }

    @Test
    fun `english income summary display strips to the amount and expands with a line`() {
        val report = MiraResponse.IncomeSummaryReport(totalReceived = taka("225000"), count = 12)

        assertThat(display(ConversationStyle.CONCISE, report, SpokenLanguage.ENGLISH))
            .isEqualTo("BDT 2,25,000")
        assertThat(display(ConversationStyle.BALANCED, report, SpokenLanguage.ENGLISH))
            .isEqualTo("Income: BDT 2,25,000\nCount: 12")
        assertThat(display(ConversationStyle.DETAILED, report, SpokenLanguage.ENGLISH))
            .isEqualTo("Income: BDT 2,25,000\nCount: 12\nEvery income entry in the period is counted.")
    }

    @Test
    fun `english outstanding adds the invoiced total only when detailed`() {
        val report = MiraResponse.InvoiceOutstandingReport(
            clientName = "Ahmed",
            number = "INV-2026-001",
            outstanding = taka("45000"),
            total = taka("120000"),
        )

        val concise = speak(ConversationStyle.CONCISE, report, SpokenLanguage.ENGLISH)
        val balanced = speak(ConversationStyle.BALANCED, report, SpokenLanguage.ENGLISH)
        val detailed = speak(ConversationStyle.DETAILED, report, SpokenLanguage.ENGLISH)

        assertThat(concise).isEqualTo("BDT 45,000 outstanding.")
        assertThat(balanced).isEqualTo("Ahmed has BDT 45,000 outstanding.")
        assertThat(detailed).isEqualTo("Ahmed has BDT 45,000 outstanding, out of BDT 1,20,000 invoiced.")
        // The invoiced total is real context drawn from the response, surfaced only in DETAILED.
        assertThat(balanced).doesNotContain("1,20,000")
        assertThat(detailed).contains("BDT 1,20,000")
    }

    // --- Bengali ---------------------------------------------------------------------------------

    @Test
    fun `bengali greeting drops the invitation when concise`() {
        val greeting = MiraResponse.Greeting

        val concise = speak(ConversationStyle.CONCISE, greeting, SpokenLanguage.BENGALI)
        val balanced = speak(ConversationStyle.BALANCED, greeting, SpokenLanguage.BENGALI)
        val detailed = speak(ConversationStyle.DETAILED, greeting, SpokenLanguage.BENGALI)

        assertThat(concise).isEqualTo("শুভ সকাল, আবু।")
        assertThat(balanced).isEqualTo("শুভ সকাল, আবু। বলুন, কী করতে পারি।")
        assertThat(detailed).isEqualTo(balanced)
        assertThat(concise.length).isLessThan(balanced.length)
    }

    @Test
    fun `bengali expense summary keeps the amount but varies the framing`() {
        val report = MiraResponse.ExpenseSummaryReport(
            totalSpent = taka("2500"),
            category = null,
            count = 3,
        )

        val concise = speak(ConversationStyle.CONCISE, report, SpokenLanguage.BENGALI)
        val balanced = speak(ConversationStyle.BALANCED, report, SpokenLanguage.BENGALI)
        val detailed = speak(ConversationStyle.DETAILED, report, SpokenLanguage.BENGALI)

        assertThat(concise).isEqualTo("২,৫০০ টাকা।")
        assertThat(balanced).isEqualTo("মোট খরচ ২,৫০০ টাকা।")
        assertThat(detailed).isEqualTo("মোট খরচ ২,৫০০ টাকা। এই সময়ের সব খরচ এতে ধরা আছে।")
        assertThat(concise).contains("২,৫০০ টাকা")
        assertThat(detailed).startsWith(balanced)
        assertThat(detailed.length).isGreaterThan(balanced.length)
        assertThat(concise.length).isLessThan(balanced.length)
    }

    @Test
    fun `bengali outstanding adds the invoiced total only when detailed`() {
        val report = MiraResponse.InvoiceOutstandingReport(
            clientName = "Ahmed",
            number = "INV-2026-001",
            outstanding = taka("45000"),
            total = taka("120000"),
        )

        val concise = speak(ConversationStyle.CONCISE, report, SpokenLanguage.BENGALI)
        val balanced = speak(ConversationStyle.BALANCED, report, SpokenLanguage.BENGALI)
        val detailed = speak(ConversationStyle.DETAILED, report, SpokenLanguage.BENGALI)

        assertThat(concise).isEqualTo("৪৫,০০০ টাকা বকেয়া।")
        assertThat(balanced).isEqualTo("Ahmed-এর ৪৫,০০০ টাকা বকেয়া রয়েছে।")
        assertThat(detailed).isEqualTo("Ahmed-এর ৪৫,০০০ টাকা বকেয়া রয়েছে, মোট ১,২০,০০০ টাকা ইনভয়েসের মধ্যে।")
        assertThat(balanced).doesNotContain("১,২০,০০০")
        assertThat(detailed).contains("১,২০,০০০ টাকা")
    }

    // --- Scope and default wiring ----------------------------------------------------------------

    @Test
    fun `the default renderer is balanced`() {
        val greeting = MiraResponse.Greeting

        // testRenderer() uses the clock-only path, which defaults to ConversationStyleProvider.Balanced.
        assertThat(testRenderer().speak(greeting, SpokenLanguage.ENGLISH))
            .isEqualTo(speak(ConversationStyle.BALANCED, greeting, SpokenLanguage.ENGLISH))
    }

    @Test
    fun `an untargeted response reads the same in every style`() {
        val calc = MiraResponse.CalculationResult(expression = "1200 + 50", value = "1,250")

        val concise = speak(ConversationStyle.CONCISE, calc, SpokenLanguage.ENGLISH)
        val balanced = speak(ConversationStyle.BALANCED, calc, SpokenLanguage.ENGLISH)
        val detailed = speak(ConversationStyle.DETAILED, calc, SpokenLanguage.ENGLISH)

        assertThat(concise).isEqualTo(balanced)
        assertThat(detailed).isEqualTo(balanced)
    }
}
