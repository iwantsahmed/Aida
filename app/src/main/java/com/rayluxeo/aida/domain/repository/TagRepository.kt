package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.Tag
import kotlinx.coroutines.flow.Flow

/**
 * The labels Abu puts on tasks.
 *
 * A tag is its own record rather than free text on a task, so renaming one renames it everywhere
 * and the filter list is the tags that exist rather than every spelling ever typed.
 *
 * Putting a tag on a task is not here — that changes a task, so it lives on [TaskRepository].
 * This port owns the tags themselves.
 */
interface TagRepository {

    fun observeAll(): Flow<List<Tag>>

    /** Tags with how many live tasks carry each one, for a filter list that can show its weight. */
    fun observeUsage(): Flow<List<TagUsage>>

    suspend fun byId(id: Long): AppResult<Tag>

    /**
     * Returns the tag called [name], creating it only if it is not already there.
     *
     * Names are unique in the database, so a plain insert would throw the second time Abu says
     * "tag it urgent". Matching is case-insensitive and ignores surrounding space, because
     * "Urgent" and "urgent " are the same label to him and two rows would split his filter in two.
     */
    suspend fun ensure(name: String): AppResult<Tag>

    /** Fails with VALIDATION when [name] is already another tag's, rather than merging silently. */
    suspend fun rename(id: Long, name: String): AppResult<Tag>

    /** Removes the tag and takes it off every task. The tasks themselves are untouched. */
    suspend fun delete(id: Long): AppResult<Unit>
}

/** A tag and how many live tasks wear it. Archived, completed and binned tasks are not counted. */
data class TagUsage(
    val tag: Tag,
    val taskCount: Int,
)
