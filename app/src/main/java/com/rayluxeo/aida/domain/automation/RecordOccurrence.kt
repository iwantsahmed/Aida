package com.rayluxeo.aida.domain.automation

import java.time.Instant

/**
 * Which kind of record an automation occurrence came from. Persisted by name (like the RecordState
 * enums), so a reorder here would be a silent data change — append, never reorder.
 */
enum class RecordTriggerSource { INVOICE, TASK, SUBSCRIPTION, PROJECT }

/**
 * One moment a record met a record-driven automation's condition — "invoice 7 went overdue",
 * "project 3's deadline arrived".
 *
 * [occurrenceFrom] is the stable anchor that identifies *this* occurrence, not just the record:
 * it moves only when the fact itself is genuinely new (a later due date, a task snoozed to a new
 * day), so the fire ledger can tell a condition that still holds — and must not fire again — from
 * one that has only now appeared. It is the same no-nag idea [DerivedReminderRules] uses for the
 * reminders Mira raises from the same records.
 */
data class RecordOccurrence(
    val source: RecordTriggerSource,
    val recordId: Long,
    val occurrenceFrom: Instant,
)
