package com.rayluxeo.aida.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.rayluxeo.aida.data.db.entity.ProjectEntity
import com.rayluxeo.aida.data.db.entity.ProjectMilestoneEntity
import com.rayluxeo.aida.domain.model.ProjectStatus
import kotlinx.coroutines.flow.Flow

/**
 * Projects and their milestones.
 *
 * Every project read filters `deletedAtEpochMillis IS NULL`, and every milestone read joins back
 * to its project so a binned project takes its checkpoints out of sight with it — the milestone
 * table has no bin of its own, because a milestone has no life apart from its project.
 *
 * The writes that can disturb milestone order are transactions rather than plain queries. A list
 * with two milestones at position 3, or a project stored without the plan that was typed with it,
 * is the failure this DAO is shaped to prevent.
 *
 * Ordering is uniform: due date first, undated last, then name — so a project keeps its place when
 * a screen changes filter.
 */
@Dao
abstract class ProjectDao {

    @Query(
        """
        SELECT * FROM projects
        WHERE deletedAtEpochMillis IS NULL
        ORDER BY dueOnEpochDay IS NULL ASC, dueOnEpochDay ASC, name COLLATE NOCASE ASC, id ASC
        """
    )
    abstract fun observeAll(): Flow<List<ProjectEntity>>

    @Query(
        """
        SELECT * FROM projects
        WHERE deletedAtEpochMillis IS NULL AND status NOT IN (:closed)
        ORDER BY dueOnEpochDay IS NULL ASC, dueOnEpochDay ASC, name COLLATE NOCASE ASC, id ASC
        """
    )
    abstract fun observeOpen(closed: List<ProjectStatus>): Flow<List<ProjectEntity>>

    @Query(
        """
        SELECT * FROM projects
        WHERE deletedAtEpochMillis IS NULL AND status = :status
        ORDER BY dueOnEpochDay IS NULL ASC, dueOnEpochDay ASC, name COLLATE NOCASE ASC, id ASC
        """
    )
    abstract fun observeByStatus(status: ProjectStatus): Flow<List<ProjectEntity>>

    @Query(
        """
        SELECT * FROM projects
        WHERE deletedAtEpochMillis IS NULL AND clientId = :clientId
        ORDER BY dueOnEpochDay IS NULL ASC, dueOnEpochDay ASC, name COLLATE NOCASE ASC, id ASC
        """
    )
    abstract fun observeForClient(clientId: Long): Flow<List<ProjectEntity>>

    @Query(
        """
        SELECT * FROM projects
        WHERE deletedAtEpochMillis IS NULL
        ORDER BY dueOnEpochDay IS NULL ASC, dueOnEpochDay ASC, name COLLATE NOCASE ASC, id ASC
        """
    )
    abstract suspend fun listAll(): List<ProjectEntity>

    /** [match] is FTS4 MATCH syntax built by a sanitiser, never a raw phrase Abu dictated. */
    @Query(
        """
        SELECT projects.* FROM projects
        JOIN project_fts ON project_fts.rowid = projects.id
        WHERE project_fts MATCH :match AND projects.deletedAtEpochMillis IS NULL
        ORDER BY projects.dueOnEpochDay IS NULL ASC, projects.dueOnEpochDay ASC,
                 projects.name COLLATE NOCASE ASC, projects.id ASC
        """
    )
    abstract suspend fun search(match: String): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE id = :id AND deletedAtEpochMillis IS NULL")
    abstract suspend fun findLiveById(id: Long): ProjectEntity?

    /** Reads the row whatever state it is in, which is what restoring from the bin needs. */
    @Query("SELECT * FROM projects WHERE id = :id")
    abstract suspend fun findById(id: Long): ProjectEntity?

    /**
     * Whether a client is there to attach work to. A binned client does not count: SQLite would
     * accept the link, but Abu deleted him.
     */
    @Query("SELECT COUNT(*) FROM clients WHERE id = :id AND deletedAtEpochMillis IS NULL")
    abstract suspend fun countLiveClient(id: Long): Int

    @Query(
        """
        SELECT project_milestones.* FROM project_milestones
        JOIN projects ON projects.id = project_milestones.projectId
        WHERE project_milestones.projectId = :projectId AND projects.deletedAtEpochMillis IS NULL
        ORDER BY project_milestones.position ASC, project_milestones.id ASC
        """
    )
    abstract fun observeMilestones(projectId: Long): Flow<List<ProjectMilestoneEntity>>

    @Query(
        """
        SELECT * FROM project_milestones WHERE projectId = :projectId
        ORDER BY position ASC, id ASC
        """
    )
    abstract suspend fun listMilestones(projectId: Long): List<ProjectMilestoneEntity>

    @Query("SELECT id FROM project_milestones WHERE projectId = :projectId ORDER BY position ASC, id ASC")
    abstract suspend fun milestoneIds(projectId: Long): List<Long>

    @Query("SELECT * FROM project_milestones WHERE id = :id")
    abstract suspend fun findMilestoneById(id: Long): ProjectMilestoneEntity?

    @Insert
    protected abstract suspend fun insertProject(project: ProjectEntity): Long

    @Insert
    protected abstract suspend fun insertMilestone(milestone: ProjectMilestoneEntity): Long

    @Update
    protected abstract suspend fun updateProject(project: ProjectEntity): Int

    @Update
    protected abstract suspend fun updateMilestone(milestone: ProjectMilestoneEntity): Int

    @Query("SELECT MAX(position) FROM project_milestones WHERE projectId = :projectId")
    protected abstract suspend fun maxPosition(projectId: Long): Int?

    @Query("UPDATE project_milestones SET position = :position, updatedAtEpochMillis = :updatedAt WHERE id = :id")
    protected abstract suspend fun setPosition(id: Long, position: Int, updatedAt: Long): Int

    @Query(
        """
        UPDATE project_milestones SET position = position - 1, updatedAtEpochMillis = :updatedAt
        WHERE projectId = :projectId AND position > :removedPosition
        """
    )
    protected abstract suspend fun closeGapAfter(projectId: Long, removedPosition: Int, updatedAt: Long): Int

    @Query("DELETE FROM project_milestones WHERE id = :id")
    protected abstract suspend fun deleteMilestoneRow(id: Long): Int

    /**
     * Stores the project and the milestones typed with it as one unit, numbering them in the order
     * they were given. Half a plan is not a plan, so neither part lands without the other.
     */
    @Transaction
    open suspend fun insertWithMilestones(
        project: ProjectEntity,
        milestones: List<ProjectMilestoneEntity>,
    ): Long {
        val projectId = insertProject(project)
        milestones.forEachIndexed { index, milestone ->
            insertMilestone(milestone.copy(projectId = projectId, position = index))
        }
        return projectId
    }

    /**
     * The only way project details are written back. The uuid and the creation time come from the
     * stored row, so the identity this project is known by outside the database cannot be rewritten
     * by a caller holding a stale copy. A project in the bin is not editable: restore first.
     */
    @Transaction
    open suspend fun updateDetails(project: ProjectEntity): Int {
        val existing = findById(project.id) ?: return 0
        if (existing.deletedAtEpochMillis != null) return 0
        return updateProject(
            project.copy(
                uuid = existing.uuid,
                createdAtEpochMillis = existing.createdAtEpochMillis,
                deletedAtEpochMillis = null,
            )
        )
    }

    /** Appends at the end of the project's list; the position the caller passed is not trusted. */
    @Transaction
    open suspend fun appendMilestone(milestone: ProjectMilestoneEntity): Long {
        val next = (maxPosition(milestone.projectId) ?: -1) + 1
        return insertMilestone(milestone.copy(position = next))
    }

    /** Changes the words only: position and completion are moved by their own operations. */
    @Transaction
    open suspend fun updateMilestoneDetails(milestone: ProjectMilestoneEntity): Int {
        val existing = findMilestoneById(milestone.id) ?: return 0
        return updateMilestone(
            milestone.copy(
                projectId = existing.projectId,
                position = existing.position,
                isComplete = existing.isComplete,
                completedAtEpochMillis = existing.completedAtEpochMillis,
                createdAtEpochMillis = existing.createdAtEpochMillis,
            )
        )
    }

    @Query(
        """
        UPDATE project_milestones
        SET isComplete = :complete, completedAtEpochMillis = :completedAt, updatedAtEpochMillis = :updatedAt
        WHERE id = :id
        """
    )
    abstract suspend fun setMilestoneComplete(
        id: Long,
        complete: Boolean,
        completedAt: Long?,
        updatedAt: Long,
    ): Int

    /** Removes the milestone and pulls the ones after it down, so positions stay contiguous. */
    @Transaction
    open suspend fun removeMilestone(id: Long, updatedAt: Long): Int {
        val existing = findMilestoneById(id) ?: return 0
        val removed = deleteMilestoneRow(id)
        if (removed > 0) {
            closeGapAfter(existing.projectId, existing.position, updatedAt)
        }
        return removed
    }

    /**
     * Renumbers the whole project from zero in the order given.
     *
     * Returns false — changing nothing — unless [orderedIds] names every milestone of the project
     * exactly once. A reorder applied to a stale list would otherwise leave two milestones sharing
     * a position, which is an order no screen can render and no undo can recover.
     */
    @Transaction
    open suspend fun reorderMilestones(projectId: Long, orderedIds: List<Long>, updatedAt: Long): Boolean {
        val current = milestoneIds(projectId)
        if (current.size != orderedIds.size || current.toSet() != orderedIds.toSet()) return false
        orderedIds.forEachIndexed { index, id -> setPosition(id, index, updatedAt) }
        return true
    }

    @Query(
        """
        UPDATE projects SET status = :status, updatedAtEpochMillis = :updatedAt
        WHERE id = :id AND deletedAtEpochMillis IS NULL
        """
    )
    abstract suspend fun setStatus(id: Long, status: ProjectStatus, updatedAt: Long): Int

    @Query(
        """
        UPDATE projects SET deletedAtEpochMillis = :atEpochMillis, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NULL
        """
    )
    abstract suspend fun softDelete(id: Long, atEpochMillis: Long): Int

    @Query(
        """
        UPDATE projects SET deletedAtEpochMillis = NULL, updatedAtEpochMillis = :updatedAt
        WHERE id = :id AND deletedAtEpochMillis IS NOT NULL
        """
    )
    abstract suspend fun restore(id: Long, updatedAt: Long): Int

    /** Permanent. The milestones go with it by cascade; tasks and invoices only lose the link. */
    @Query("DELETE FROM projects WHERE id = :id")
    abstract suspend fun deleteById(id: Long): Int
}
