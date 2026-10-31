package com.rayluxeo.aida.domain.attention

import com.rayluxeo.aida.domain.model.Money
import java.time.Instant
import java.time.LocalDate

/**
 * One item in Mira's proactive attention system.
 *
 * It carries the **facts** of what was detected — the category, the record it is about, and the
 * figures that make the sentence (days, amounts, counts) — and NEVER the sentence itself. Human
 * wording is the UI layer's job, rendered per language (AGENTS.md §4 rule 6): the engine that fills
 * this is pure domain and must not hold English, or Abu's Bengali card reads in English.
 *
 * Mira NEVER automatically sends messages, sends invoices, charges anything, or deletes anything.
 */
data class ProactiveAttentionItem(
    val id: String,
    val category: AttentionCategory,
    val severity: AttentionSeverity,
    val relatedRecord: AttentionRecordRef,
    val availableActions: List<AttentionAction>,
    /** The primary amount — the outstanding total, the renewal charge, the expense. */
    val amount: Money? = null,
    /** A second amount where the sentence needs one — the part already paid on an invoice. */
    val secondaryAmount: Money? = null,
    /** Days overdue, days away, or days since, read by the category's sentence. */
    val days: Long? = null,
    /** The date the sentence names — a task's due date, a project's target. */
    val dueOn: LocalDate? = null,
    /** A count the sentence needs — unfinished tasks on a project. */
    val count: Int? = null,
    /** Hours apart, for a possible duplicate expense. */
    val hours: Long? = null,
    val timestamp: Instant? = null,
)

enum class AttentionCategory {
    OVERDUE_INVOICE,
    OVERDUE_TASK,
    DEADLINE_WITHIN_48_HOURS,
    SCHEDULED_CLIENT_FOLLOW_UP,
    SUBSCRIPTION_RENEWING_SOON,
    PARTIALLY_PAID_INVOICE,
    HIGH_PRIORITY_UNFINISHED_TASK,
    PROJECT_UPCOMING_DEADLINE,
    PROJECT_NO_RECENT_ACTIVITY,
    POSSIBLE_DUPLICATE_EXPENSE,
}

enum class AttentionSeverity {
    CRITICAL,
    WARNING,
    NOTICE,
}

enum class AttentionAction {
    OPEN,
    REMIND_ME,
    PREPARE_FOLLOW_UP,
    MARK_DONE,
    DISMISS,
}

data class AttentionRecordRef(
    val type: AttentionRecordType,
    val id: Long,
    val title: String,
    val subtitle: String? = null,
)

enum class AttentionRecordType {
    INVOICE,
    TASK,
    PROJECT,
    CLIENT,
    SUBSCRIPTION,
    EXPENSE,
}
