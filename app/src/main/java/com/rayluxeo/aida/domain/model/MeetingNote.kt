package com.rayluxeo.aida.domain.model

import java.time.Instant

/**
 * Something said or agreed at a meeting.
 *
 * [isActionItem] and [taskId] are kept apart on purpose. An action item that has not become a
 * task yet is exactly the thing worth putting in front of Abu, and folding the two into one
 * nullable field would make that state indistinguishable from an ordinary note.
 */
data class MeetingNote(
    val id: Long = 0L,
    val meetingId: Long,
    val body: String,
    val isActionItem: Boolean = false,
    /** Set once this action item has been turned into a real task. */
    val taskId: Long? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /** An action item still waiting to be turned into a task. */
    val isOpenActionItem: Boolean get() = isActionItem && taskId == null
}
