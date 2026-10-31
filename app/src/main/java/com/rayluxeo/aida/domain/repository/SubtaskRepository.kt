package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.Subtask
import kotlinx.coroutines.flow.Flow

/**
 * The steps inside one task.
 *
 * Every read is scoped to a task, because a step has no meaning away from the task it belongs
 * to; there is deliberately no "all subtasks" view. A step whose task has been deleted is not
 * visible here and comes back with the task when it is restored.
 */
interface SubtaskRepository {

    fun observeForTask(taskId: Long): Flow<List<Subtask>>

    suspend fun byId(id: Long): AppResult<Subtask>

    /** Adds a step at the end of the task's list. A blank title is rejected, not stored. */
    suspend fun add(taskId: Long, title: String): AppResult<Subtask>

    suspend fun rename(id: Long, title: String): AppResult<Subtask>

    suspend fun setDone(id: Long, done: Boolean): AppResult<Subtask>

    /**
     * Renumbers the task's steps into [orderedIds]. The list must name exactly the task's steps
     * and nothing else — a partial list is refused rather than applied, since renumbering half
     * of them leaves two steps claiming the same place.
     */
    suspend fun reorder(taskId: Long, orderedIds: List<Long>): AppResult<Unit>

    /** Removes the step outright. Unlike a task, a checklist line has nothing to undo from. */
    suspend fun delete(id: Long): AppResult<Unit>
}
