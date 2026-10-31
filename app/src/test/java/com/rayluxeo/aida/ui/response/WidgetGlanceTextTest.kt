package com.rayluxeo.aida.ui.response

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.brief.BriefEvent
import com.rayluxeo.aida.domain.brief.BriefReminder
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.usecase.WidgetGlance
import org.junit.Test
import java.time.Instant

/**
 * The glance borrows the brief's phrasebook so its numbers read exactly as Home's do, and suppresses
 * money under the vault flag. These pin both: the lines match [briefWording] rather than a second
 * copy, the Bengali money carries Bengali numerals and no Latin, and a locked wallet shows nothing.
 */
class WidgetGlanceTextTest {

    private val english = briefWording(SpokenLanguage.ENGLISH)
    private val bengali = briefWording(SpokenLanguage.BENGALI)
    private val moment: (Instant) -> String = { "at 11 am" }

    private fun glance(
        open: Int = 0,
        due: Int = 0,
        overdue: Int = 0,
        outstanding: Money? = null,
        invoiceCount: Int = 0,
        overdueMoney: Money? = null,
        overdueCount: Int = 0,
        nextEvent: BriefEvent? = null,
        nextReminder: BriefReminder? = null,
    ) = WidgetGlance(
        openTaskCount = open,
        dueTodayCount = due,
        overdueCount = overdue,
        nextEvent = nextEvent,
        nextReminder = nextReminder,
        outstanding = outstanding,
        outstandingInvoiceCount = invoiceCount,
        overdue = overdueMoney,
        overdueInvoiceCount = overdueCount,
        calendarReadable = true,
    )

    @Test
    fun `the task line matches the brief's own phrasing`() {
        val g = glance(open = 5, due = 3, overdue = 1)
        val lines = widgetGlanceLines(g, SpokenLanguage.ENGLISH, includeMoney = true, momentLabel = moment)
        assertThat(lines.tasks).isEqualTo(english.taskSentence(dueToday = 3, overdue = 1, open = 5))
    }

    @Test
    fun `the money line matches the brief and is byte-identical in figure across languages`() {
        val g = glance(outstanding = Money.of("45000"), invoiceCount = 2)
        val en = widgetGlanceLines(g, SpokenLanguage.ENGLISH, includeMoney = true, momentLabel = moment)
        val bn = widgetGlanceLines(g, SpokenLanguage.BENGALI, includeMoney = true, momentLabel = moment)
        assertThat(en.money).isEqualTo(english.moneySentence(outstanding = Money.of("45000"), overdue = null))
        assertThat(bn.money).isEqualTo(bengali.moneySentence(outstanding = Money.of("45000"), overdue = null))
        // The grouped figure itself is the one thing that must not differ between languages.
        assertThat(moneyFigure(Money.of("45000"))).isEqualTo("45,000")
    }

    @Test
    fun `the Bengali money line uses Bengali numerals and no Latin letters`() {
        val g = glance(outstanding = Money.of("45000"), invoiceCount = 2)
        val bn = widgetGlanceLines(g, SpokenLanguage.BENGALI, includeMoney = true, momentLabel = moment)
        val money = bn.money!!
        assertThat(money.any { it in '০'..'৯' }).isTrue()
        assertThat(money.none { it in 'a'..'z' || it in 'A'..'Z' }).isTrue()
    }

    @Test
    fun `a locked wallet suppresses the money line entirely`() {
        val g = glance(outstanding = Money.of("45000"), invoiceCount = 2)
        val lines = widgetGlanceLines(g, SpokenLanguage.ENGLISH, includeMoney = false, momentLabel = moment)
        assertThat(lines.money).isNull()
    }

    @Test
    fun `a quiet day shows the quiet line and drops the rest`() {
        val lines = widgetGlanceLines(glance(), SpokenLanguage.ENGLISH, includeMoney = true, momentLabel = moment)
        assertThat(lines.tasks).isEqualTo(english.quietDay)
        assertThat(lines.money).isNull()
        assertThat(lines.nextEvent).isNull()
        assertThat(lines.nextReminder).isNull()
    }

    @Test
    fun `an event and a reminder each render their line, formatted with the moment`() {
        val event = BriefEvent(title = "Design Sync", startsAt = Instant.ofEpochSecond(1_800_000_000), endsAt = null, location = null)
        val reminder = BriefReminder(id = 1, title = "Call Ahmed", triggerAt = Instant.ofEpochSecond(1_800_000_500))
        val lines = widgetGlanceLines(
            glance(nextEvent = event, nextReminder = reminder),
            SpokenLanguage.ENGLISH,
            includeMoney = true,
            momentLabel = moment,
        )
        assertThat(lines.nextEvent).isEqualTo(english.nextEventSentence("Design Sync", "at 11 am"))
        assertThat(lines.nextReminder).isEqualTo(english.reminderSentence(1, "Call Ahmed", "at 11 am"))
    }
}
