package com.rayluxeo.aida.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.rayluxeo.aida.data.db.entity.SubtaskEntity
import kotlinx.coroutines.flow.Flow

/**
 * The steps inside a task.
 *
 * `subtasks` has no deleted column of its own because a step only exists inside its task, so the
 * reads join `tasks` instead: a soft-deleted task takes its checklist out of sight with it, and
 * restoring the task brings the same rows back rather than a copy.
 *
 * Ordering is position then id, so two steps added in the same millisecond still have a stable
 * order instead of swapping places between reads.
 */
@Dao
abstract class SubtaskDao {

    @Query(
        """
        SELECT s.* FROM subtasks AS s
        JOIN tasks AS t ON t.id = s.taskId
        WHERE s.taskId = :taskId AND t.deletedAtEpochMillis IS NULL
        ORDER BY s.position ASC, s.id ASC
        """
    )
    abstract fun observeForTask(taskId: Long): Flow<List<SubtaskEntity>>

    @Query(
        """
        SELECT s.* FROM subtasks AS s
        JOIN tasks AS t ON t.id = s.taskId
        WHERE s.taskId = :taskId AND t.deletedAtEpochMillis IS NULL
        ORDER BY s.position ASC, s.id ASC
        """
    )
    abstract suspend fun listForTask(taskId: Long): List<SubtaskEntity>

    @Query("SELECT * FROM subtasks WHERE id = :id")
    abstract suspend fun findById(id: Long): SubtaskEntity?

    @Query("SELECT MAX(position) FROM subtasks WHERE taskId = :taskId")
    abstract suspend fun lastPosition(taskId: Long): Int?

    @Insert
    abstract suspend fun insert(subtask: SubtaskEntity): Long

    @Query("UPDATE subtasks SET title = :title, updatedAtEpochMillis = :updatedAt WHERE id = :id")
    abstract suspend fun rename(id: Long, title: String, updatedAt: Long): Int

    @Query(
        """
        UPDATE subtasks
        SET isDone = :done, completedAtEpochMillis = :completedAt, updatedAtEpochMillis = :updatedAt
        WHERE id = :id
        """
    )
    abstract suspend fun setDone(id: Long, done: Boolean, completedAt: Long?, updatedAt: Long): Int

    @Query(
        """
        UPDATE subtasks SET position = :position, updatedAtEpochMillis = :updatedAt
        WHERE id = :id AND taskId = :taskId
        """
    )
    abstract suspend fun setPosition(taskId: Long, id: Long, position: Int, updatedAt: Long)

    @Query("DELETE FROM subtasks WHERE id = :id")
    abstract suspend fun deleteById(id: Long): Int

    /**
     * Appends a step at the end of its task's list. Reading the last position and inserting are
     * one transaction: two steps added together must not both land on the same position and then
     * trade places on the next read.
     */
    @Transaction
    open suspend fun append(subtask: SubtaskEntity): Long {
        val position = (lastPosition(subtask.taskId) ?: -1) + 1
        return insert(subtask.copy(position = position))
    }

    /**
     * Renumbers a task's steps to [orderedIds], all of it or none of it. A half-applied reorder
     * leaves two steps claiming the same position, which reads as a list that shuffles itself.
     *
     * The caller is expected to have checked that [orderedIds] is exactly this task's steps; ids
     * belonging to another task match nothing here and are silently no-ops.
     */
    @Transaction
    open suspend fun reorder(taskId: Long, orderedIds: List<Long>, updatedAt: Long) {
        orderedIds.forEachIndexed { index, id ->
            setPosition(taskId = taskId, id = id, position = index, updatedAt = updatedAt)
        }
    }
}
