package com.rayluxeo.aida.domain.model

import com.rayluxeo.aida.domain.automation.RecordTriggerSource
import java.time.Instant

/**
 * A ledger row: an automation that has already fired for a particular record occurrence, so the
 * daily check does not fire it again for the same fact.
 *
 * Internal bookkeeping, not an Abu-facing record — there is no soft-delete here, exactly like
 * [AutomationRun]. An occurrence is identified by [source] + [recordId] + [occurrenceFrom]; when a
 * genuinely new occurrence appears (a new overdue invoice, a task snoozed to a new day) its anchor
 * differs and the rule fires once more.
 */
data class FiredOccurrence(
    val automationId: Long,
    val source: RecordTriggerSource,
    val recordId: Long,
    val occurrenceFrom: Instant,
    val firedAt: Instant,
)
