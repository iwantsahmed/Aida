package com.rayluxeo.aida.domain.model

import com.rayluxeo.aida.domain.task.RecurrenceRule
import java.time.Instant

enum class ReminderState { SCHEDULED, FIRED, CANCELLED }

/**
 * What sort of thing Mira is reminding Abu about.
 *
 * It is stored rather than derived from whichever link happens to be non-null, because the kind
 * decides the wording, the notification actions and what a tap opens — and a reminder can carry
 * more than one link. An invoice reminder for a client has both.
 *
 * [PLACE] is the odd one: it has no moment of its own and fires when Abu arrives somewhere.
 */
enum class ReminderKind {
    ONE_TIME,
    RECURRING,
    TASK,
    INVOICE,
    CLIENT_FOLLOW_UP,
    BIRTHDAY,
    PROJECT_DEADLINE,
    SUBSCRIPTION,
    PLACE,
}

/** Whether a place reminder fires on arriving or on leaving. */
enum class PlaceTrigger { ENTER, EXIT }

/**
 * Something Abu asked to be told about.
 *
 * [triggerAt] is the reminder's own moment and [snoozedUntil] is a moment pushed in front of it;
 * the two are kept apart rather than collapsed into one column so that calling off a snooze puts
 * the reminder back where Abu originally set it instead of at a time Mira invented. [armedAt] is
 * the single place that difference is resolved, so no caller has to remember the rule.
 *
 * Every link is `SET NULL` on the storage side. A reminder is Abu's instruction and stands on its
 * own: losing the invoice it grew out of must leave the reminder firing, not delete it.
 */
data class Reminder(
    val id: Long = 0L,
    val title: String,
    val triggerAt: Instant,
    val state: ReminderState = ReminderState.SCHEDULED,
    val kind: ReminderKind = ReminderKind.ONE_TIME,
    /** Shared with tasks — the same [RecurrenceRule] and the same codec, never a second one. */
    val recurrence: RecurrenceRule? = null,
    val snoozedUntil: Instant? = null,
    val linkedTaskId: Long? = null,
    val linkedInvoiceId: Long? = null,
    val linkedClientId: Long? = null,
    val linkedProjectId: Long? = null,
    val linkedSubscriptionId: Long? = null,
    val origin: RecordOrigin = RecordOrigin.MANUAL,
    val createdAt: Instant,
) {

    /**
     * The moment the OS alarm is actually set for: the snooze if there is one, the reminder's own
     * moment otherwise. Read this rather than [triggerAt] anywhere an alarm is being armed, or a
     * snoozed reminder goes off at the time Abu just pushed away.
     */
    val armedAt: Instant get() = snoozedUntil ?: triggerAt

    val isSnoozed: Boolean get() = snoozedUntil != null

    val repeats: Boolean get() = recurrence != null

    /**
     * True when the reminder waits for a place rather than a time. Its arming belongs to the
     * place scheduler, so the time scheduler must leave it alone.
     */
    val waitsForPlace: Boolean get() = kind == ReminderKind.PLACE
}
