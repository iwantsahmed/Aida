package com.rayluxeo.aida.domain.model

import java.time.Instant

/**
 * A checklist line inside a task.
 *
 * Finishing every subtask does not finish the parent task. A task can have all its steps ticked
 * and still be open for a reason only Abu knows, and Mira closing it on his behalf would be a
 * guess dressed up as help.
 *
 * [position] is the order Abu put the steps in rather than the order they were created, so it is
 * renumbered as a block whenever the list is rearranged.
 */
data class Subtask(
    val id: Long = 0L,
    val taskId: Long,
    val title: String,
    val isDone: Boolean = false,
    val completedAt: Instant? = null,
    val position: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)
