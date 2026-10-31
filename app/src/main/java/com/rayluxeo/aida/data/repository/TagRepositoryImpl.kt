package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.data.db.dao.TaskDao
import com.rayluxeo.aida.domain.model.Tag
import com.rayluxeo.aida.domain.repository.TagRepository
import com.rayluxeo.aida.domain.repository.TagUsage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Tags are stored through [TaskDao] rather than a DAO of their own: putting a tag on a task writes
 * the link and touches the task in the same breath, and a `@Transaction` can only span methods of
 * one DAO. This class is still separate, because what a tag *is* and what a task *is* are two
 * different questions and the screens ask them separately.
 *
 * Names are trimmed and compared case-insensitively before anything is written. `tags.name` is
 * unique, so without that, "urgent" and "Urgent " would either split Abu's filter in two or throw
 * a constraint violation in his face.
 */
@Singleton
class TagRepositoryImpl @Inject constructor(
    private val dao: TaskDao,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : TagRepository {

    override fun observeAll(): Flow<List<Tag>> =
        dao.observeTags()
            .map { rows -> rows.map { Tag(id = it.id, name = it.name) } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override fun observeUsage(): Flow<List<TagUsage>> =
        dao.observeTagUsage()
            .map { rows ->
                rows.map { TagUsage(Tag(id = it.tagId, name = it.name), taskCount = it.taskCount) }
            }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override suspend fun byId(id: Long): AppResult<Tag> {
        val found = storageCall(dispatchers, "load tag $id") { dao.findTagById(id) }
        return when (found) {
            is AppResult.Failure -> found
            is AppResult.Success -> found.value?.let { AppResult.Success(Tag(it.id, it.name)) }
                ?: failure(MiraErrorCode.NOT_FOUND, "No tag with id $id")
        }
    }

    override suspend fun ensure(name: String): AppResult<Tag> {
        val clean = name.trim()
        if (clean.isEmpty()) return failure(MiraErrorCode.VALIDATION, "Tag name was blank")
        val id = storageCall(dispatchers, "ensure tag $clean") {
            dao.ensureTag(clean, clock.now().toEpochMilli())
        }
        return when (id) {
            is AppResult.Failure -> id
            is AppResult.Success -> byId(id.value)
        }
    }

    /**
     * Refuses a name another tag already has instead of merging the two. Merging would take a tag
     * off tasks Abu never touched, and there is no undo for that.
     *
     * The unique index is still the real guard; this check is what turns the collision into a
     * VALIDATION he can act on rather than a storage fault he cannot.
     */
    override suspend fun rename(id: Long, name: String): AppResult<Tag> {
        val clean = name.trim()
        if (clean.isEmpty()) return failure(MiraErrorCode.VALIDATION, "Tag name was blank")
        val clash = when (val existing = storageCall(dispatchers, "look for tag $clean") {
            dao.findTagByName(clean)
        }) {
            is AppResult.Failure -> return existing
            is AppResult.Success -> existing.value
        }
        if (clash != null && clash.id != id) {
            return failure(MiraErrorCode.VALIDATION, "Another tag is already called $clean")
        }
        val changed = storageCall(dispatchers, "rename tag $id") {
            dao.renameTag(id = id, name = clean, at = clock.now().toEpochMilli())
        }
        return when (changed) {
            is AppResult.Failure -> changed
            is AppResult.Success ->
                if (changed.value > 0) byId(id)
                else failure(MiraErrorCode.NOT_FOUND, "No tag with id $id")
        }
    }

    /**
     * A hard delete, unlike a task's. A tag carries nothing of Abu's own — the tasks it was on are
     * untouched and keep every word he wrote — so there is nothing for an undo to protect.
     */
    override suspend fun delete(id: Long): AppResult<Unit> {
        val removed = storageCall(dispatchers, "delete tag $id") { dao.deleteTag(id) }
        return when (removed) {
            is AppResult.Failure -> removed
            is AppResult.Success ->
                if (removed.value > 0) AppResult.Success(Unit)
                else failure(MiraErrorCode.NOT_FOUND, "No tag with id $id")
        }
    }
}
