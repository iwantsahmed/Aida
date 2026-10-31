package com.rayluxeo.aida.domain.model

import java.time.Instant

/**
 * What an activity entry was about: a table name and a row id, with no foreign key behind it.
 *
 * The looseness is the point. The log has to survive the row it describes being deleted, so it
 * cannot hold a reference the database would enforce or cascade. Binding the two halves together
 * in one value is what stops a half-filled reference — a table with no id, or an id belonging to
 * no table — from ever reaching storage.
 */
data class ActivitySubject(
    val table: String,
    val id: Long,
)

/**
 * One line of what Mira did.
 *
 * The log is append-only and is never deleted, softly or otherwise: a record that can be edited
 * afterwards cannot answer "what happened to that invoice".
 *
 * [summary] is stored as it was written, in the language it was written in, because a log is a
 * record of what happened rather than a template to be re-rendered later. Callers render it in
 * Abu's language at the moment it is recorded.
 */
data class ActivityEntry(
    val id: Long = 0L,
    val category: ActivityCategory,
    /** A stable machine-readable verb, such as `invoice.sent`. Never shown to Abu as it stands. */
    val action: String,
    val summary: String,
    val subject: ActivitySubject? = null,
    val occurredAt: Instant,
)
