package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.Project
import com.rayluxeo.aida.domain.model.ProjectFilter
import com.rayluxeo.aida.domain.model.ProjectMilestone
import com.rayluxeo.aida.domain.model.ProjectStatus
import kotlinx.coroutines.flow.Flow

/**
 * Projects and the milestones inside them.
 *
 * A project and its milestones are created together in one transaction: a plan that was saved
 * without its checkpoints is worse than one that was not saved at all, because it looks complete.
 *
 * Milestone [ProjectMilestone.position] is owned here and nowhere else. Every operation that can
 * disturb the order — adding, deleting, reordering — leaves the project's milestones numbered
 * contiguously from zero.
 */
interface ProjectRepository {

    fun observe(filter: ProjectFilter = ProjectFilter.OPEN): Flow<List<Project>>

    fun observeForClient(clientId: Long): Flow<List<Project>>

    /** Empty while the project itself is in the bin; its milestones go out of sight with it. */
    fun observeMilestones(projectId: Long): Flow<List<ProjectMilestone>>

    /** Fails with NOT_FOUND for a project in the bin, exactly as for one that never existed. */
    suspend fun byId(id: Long): AppResult<Project>

    /** Full-text over name and description. A blank query means the whole [filter] set. */
    suspend fun search(query: String?, filter: ProjectFilter = ProjectFilter.OPEN): AppResult<List<Project>>

    /**
     * Stores the project and [milestones] together, numbering the milestones in the order given.
     * Either all of it lands or none of it does.
     */
    suspend fun create(project: Project, milestones: List<ProjectMilestone> = emptyList()): AppResult<Project>

    /** Changes the details only. The uuid, the creation time and the bin state are left alone. */
    suspend fun update(project: Project): AppResult<Project>

    suspend fun setStatus(id: Long, status: ProjectStatus): AppResult<Project>

    suspend fun softDelete(id: Long): AppResult<Unit>

    suspend fun restore(id: Long): AppResult<Project>

    /**
     * Removes the project for good, and its milestones with it. Tasks and invoices that pointed at
     * it keep their own record and simply lose the link.
     */
    suspend fun purge(id: Long): AppResult<Unit>

    /** Appends to the end of the project's list, whatever position the milestone carries. */
    suspend fun addMilestone(milestone: ProjectMilestone): AppResult<ProjectMilestone>

    /** Changes the title and due date. Order and completion have their own operations. */
    suspend fun updateMilestone(milestone: ProjectMilestone): AppResult<ProjectMilestone>

    suspend fun setMilestoneComplete(id: Long, complete: Boolean): AppResult<ProjectMilestone>

    /** Removes the milestone and closes the gap it leaves in the order. */
    suspend fun deleteMilestone(id: Long): AppResult<Unit>

    /**
     * Renumbers the project's milestones into the order given. [orderedIds] has to name every
     * milestone of the project exactly once; anything else is refused with VALIDATION rather than
     * applied in part, because a half-applied order is not an order.
     */
    suspend fun reorderMilestones(projectId: Long, orderedIds: List<Long>): AppResult<List<ProjectMilestone>>
}
