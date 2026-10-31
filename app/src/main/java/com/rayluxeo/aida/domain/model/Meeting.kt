package com.rayluxeo.aida.domain.model

import java.time.Instant

/**
 * A conversation with someone else, and what Abu needs to have ready for it.
 *
 * Both [clientId] and [projectId] outlive the records they point at: a meeting that happened is
 * part of Abu's history whether or not the client is still on file, so deleting either one
 * empties the field rather than taking the meeting with it.
 */
data class Meeting(
    val id: Long = 0L,
    val title: String,
    val clientId: Long? = null,
    val projectId: Long? = null,
    val startsAt: Instant,
    val endsAt: Instant? = null,
    val location: String? = null,
    /**
     * When the meeting was sent to the bin, or null while it is live.
     *
     * Deleting is soft so undo restores the same row. A restored copy would take a new id, and
     * every calendar link pointing at the old one would quietly stop resolving.
     */
    val deletedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
