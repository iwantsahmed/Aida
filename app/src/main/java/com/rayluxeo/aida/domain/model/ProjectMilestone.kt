package com.rayluxeo.aida.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * A checkpoint inside a project.
 *
 * [position] is the order Abu put them in, which is not the order they are due and not the order
 * they were created — a milestone list is a plan, and the plan is his. It is kept contiguous from
 * zero by the repository: adding appends, removing closes the gap, reordering renumbers the whole
 * project. Nothing else may set it, because two milestones sharing a position have no order at all.
 */
data class ProjectMilestone(
    val id: Long = 0L,
    val projectId: Long,
    val title: String,
    val dueOn: LocalDate? = null,
    val isComplete: Boolean = false,
    val completedAt: Instant? = null,
    /** Assigned by the repository. A value passed in on create is replaced, never trusted. */
    val position: Int = 0,
    val createdAt: Instant,
    val updatedAt: Instant,
)
