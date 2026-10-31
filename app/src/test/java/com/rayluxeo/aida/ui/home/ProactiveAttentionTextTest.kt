package com.rayluxeo.aida.ui.home

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.attention.AttentionAction
import com.rayluxeo.aida.domain.attention.AttentionCategory
import com.rayluxeo.aida.domain.attention.AttentionRecordRef
import com.rayluxeo.aida.domain.attention.AttentionRecordType
import com.rayluxeo.aida.domain.attention.AttentionSeverity
import com.rayluxeo.aida.domain.attention.ProactiveAttentionItem
import com.rayluxeo.aida.domain.model.Money
import java.time.LocalDate
import org.junit.Test

/**
 * The Needs-Attention card's wording lives here, not in the detection engine (AGENTS.md §4 rule 6).
 * These checks pin both the English and the Bengali sentence for each category, and the plural /
 * "today" edges that the old engine-level strings used to cover.
 */
class ProactiveAttentionTextTest {

    // Deterministic formatters so the sentences are the only thing under test.
    private val englishDigits: (Long) -> String = { it.toString() }
    private val bengaliDigits: (Long) -> String = { value ->
        value.toString().map { "০১২৩৪৫৬৭৮৯"[it - '0'] }.joinToString("")
    }

    private fun item(
        category: AttentionCategory,
        title: String,
        subtitle: String? = null,
        days: Long? = null,
        count: Int? = null,
        hours: Long? = null,
        dueOn: LocalDate? = null,
        type: AttentionRecordType = AttentionRecordType.INVOICE,
    ) = ProactiveAttentionItem(
        id = "x",
        category = category,
        severity = AttentionSeverity.NOTICE,
        relatedRecord = AttentionRecordRef(type = type, id = 1L, title = title, subtitle = subtitle),
        availableActions = listOf(AttentionAction.OPEN),
        days = days,
        count = count,
        hours = hours,
        dueOn = dueOn,
    )

    private fun english(
        item: ProactiveAttentionItem,
        amountText: String? = null,
        secondaryText: String? = null,
        dueText: String? = null,
    ) = proactiveAttentionText(item, SpokenLanguage.ENGLISH, amountText, secondaryText, dueText, englishDigits)

    private fun bengali(
        item: ProactiveAttentionItem,
        amountText: String? = null,
        secondaryText: String? = null,
        dueText: String? = null,
    ) = proactiveAttentionText(item, SpokenLanguage.BENGALI, amountText, secondaryText, dueText, bengaliDigits)

    @Test
    fun `overdue invoice reads in English with the client and amount`() {
        val (what, why) = english(
            item(AttentionCategory.OVERDUE_INVOICE, title = "INV-001", subtitle = "Ahmed", days = 4),
            amountText = "BDT 45,000",
        )
        assertThat(what).isEqualTo("Ahmed's invoice is 4 days overdue.")
        assertThat(why).isEqualTo("BDT 45,000 outstanding past the due date.")
    }

    @Test
    fun `overdue invoice reads in Bengali with Bengali digits`() {
        val (what, why) = bengali(
            item(AttentionCategory.OVERDUE_INVOICE, title = "INV-001", subtitle = "Ahmed", days = 4),
            amountText = "৳৪৫,০০০",
        )
        assertThat(what).isEqualTo("Ahmed-এর ইনভয়েস ৪ দিন ধরে বকেয়া।")
        assertThat(why).isEqualTo("নির্ধারিত তারিখ পেরিয়ে ৳৪৫,০০০ বকেয়া।")
    }

    @Test
    fun `overdue invoice uses singular day in English`() {
        val (what, _) = english(
            item(AttentionCategory.OVERDUE_INVOICE, title = "INV-001", subtitle = "Ahmed", days = 1),
            amountText = "BDT 100",
        )
        assertThat(what).isEqualTo("Ahmed's invoice is 1 day overdue.")
    }

    @Test
    fun `overdue task falls back to no client`() {
        val (what, why) = english(
            item(AttentionCategory.OVERDUE_TASK, title = "Submit tax report", days = 1, type = AttentionRecordType.TASK),
            dueText = "8 Mar",
        )
        assertThat(what).isEqualTo("Task 'Submit tax report' is 1 day overdue.")
        assertThat(why).isEqualTo("Was due on 8 Mar.")
    }

    @Test
    fun `deadline within 48 hours names tomorrow and the unfinished count`() {
        val (what, _) = english(
            item(AttentionCategory.DEADLINE_WITHIN_48_HOURS, title = "Homepage", days = 1, count = 3, type = AttentionRecordType.PROJECT),
        )
        assertThat(what).isEqualTo("Homepage is due tomorrow with 3 tasks unfinished.")
    }

    @Test
    fun `subscription renewing today reads without a day count`() {
        val en = english(item(AttentionCategory.SUBSCRIPTION_RENEWING_SOON, title = "Adobe", days = 0, type = AttentionRecordType.SUBSCRIPTION), amountText = "BDT 3,500")
        assertThat(en.first).isEqualTo("Adobe renews today.")
        val bn = bengali(item(AttentionCategory.SUBSCRIPTION_RENEWING_SOON, title = "Adobe", days = 0, type = AttentionRecordType.SUBSCRIPTION), amountText = "৳৩,৫০০")
        assertThat(bn.first).isEqualTo("Adobe আজ নবায়ন হবে।")
    }

    @Test
    fun `partially paid invoice names both amounts`() {
        val (what, why) = english(
            item(AttentionCategory.PARTIALLY_PAID_INVOICE, title = "INV-004", subtitle = "Ahmed", type = AttentionRecordType.INVOICE),
            amountText = "BDT 10,000",
            secondaryText = "BDT 15,000",
        )
        assertThat(what).isEqualTo("Ahmed's invoice is partially paid.")
        assertThat(why).isEqualTo("BDT 15,000 paid, BDT 10,000 still outstanding.")
    }

    @Test
    fun `duplicate expense reads in both languages`() {
        val dup = item(AttentionCategory.POSSIBLE_DUPLICATE_EXPENSE, title = "Office Supplies", hours = 1, type = AttentionRecordType.EXPENSE)
        assertThat(english(dup, amountText = "BDT 1,200").first)
            .isEqualTo("Possible duplicate expense of BDT 1,200 for 'Office Supplies'.")
        assertThat(bengali(dup, amountText = "৳১,২০০").first)
            .isEqualTo("'Office Supplies'-এর জন্য ৳১,২০০-এর সম্ভাব্য ডুপ্লিকেট খরচ।")
    }

    @Test
    fun `every category produces a non-blank sentence in both languages`() {
        AttentionCategory.entries.forEach { category ->
            val probe = item(category, title = "Thing", subtitle = "Ahmed", days = 2, count = 1, hours = 2, dueOn = LocalDate.of(2026, 3, 10))
            listOf(
                english(probe, amountText = "BDT 1", secondaryText = "BDT 1", dueText = "10 Mar"),
                bengali(probe, amountText = "৳১", secondaryText = "৳১", dueText = "১০ মার্চ"),
            ).forEach { (what, why) ->
                assertThat(what.isNotBlank()).isTrue()
                assertThat(why.isNotBlank()).isTrue()
            }
        }
    }
}
