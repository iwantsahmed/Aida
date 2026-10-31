package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.core.common.map
import com.rayluxeo.aida.data.db.dao.ProjectDao
import com.rayluxeo.aida.domain.model.Project
import com.rayluxeo.aida.domain.model.ProjectFilter
import com.rayluxeo.aida.domain.model.ProjectMilestone
import com.rayluxeo.aida.domain.model.ProjectStatus
import com.rayluxeo.aida.domain.repository.ProjectRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Projects and milestones, seen as domain records.
 *
 * The uuid is minted once at the first insert and carried forward by the DAO on every later write,
 * for the same reason a client's is. A project arrives with its milestones in one transaction, so
 * there is no moment at which a saved plan is missing the checkpoints that were typed with it.
 *
 * Milestone positions are the repository's to assign. Whatever position a caller puts on a
 * milestone is replaced — appended at the end on create, renumbered on reorder, closed up on
 * delete — because two milestones sharing a position is an order that cannot be drawn.
 */
@Singleton
class ProjectRepositoryImpl @Inject constructor(
    private val dao: ProjectDao,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : ProjectRepository {

    override fun observe(filter: ProjectFilter): Flow<List<Project>> =
        when (filter) {
            ProjectFilter.ALL -> dao.observeAll()
            ProjectFilter.OPEN -> dao.observeOpen(Project.CLOSED_STATUSES.toList())
            ProjectFilter.ACTIVE -> dao.observeByStatus(ProjectStatus.ACTIVE)
            ProjectFilter.COMPLETED -> dao.observeByStatus(ProjectStatus.COMPLETED)
            ProjectFilter.CANCELLED -> dao.observeByStatus(ProjectStatus.CANCELLED)
        }
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override fun observeForClient(clientId: Long): Flow<List<Project>> =
        dao.observeForClient(clientId)
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override fun observeMilestones(projectId: Long): Flow<List<ProjectMilestone>> =
        dao.observeMilestones(projectId)
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override suspend fun byId(id: Long): AppResult<Project> {
        val found = storageCall(dispatchers, "load project $id") { dao.findLiveById(id) }
        return when (found) {
            is AppResult.Failure -> found
            is AppResult.Success -> found.value?.toDomain()?.let { AppResult.Success(it) }
                ?: failure(MiraErrorCode.NOT_FOUND, "No project with id $id")
        }
    }

    override suspend fun search(query: String?, filter: ProjectFilter): AppResult<List<Project>> =
        storageCall(dispatchers, "search projects") {
            val match = query?.let(::ftsMatchOrNull)
            val rows = if (match == null) dao.listAll() else dao.search(match)
            rows.map { it.toDomain() }.filter { it.matches(filter) }
        }

    override suspend fun create(
        project: Project,
        milestones: List<ProjectMilestone>,
    ): AppResult<Project> {
        unstorableBudget(project)?.let { return it }
        unknownClient(project.clientId)?.let { return it }
        val uuid = UUID.randomUUID().toString()
        val stored = storageCall(dispatchers, "create project") {
            dao.insertWithMilestones(
                project = project.copy(id = 0L).toEntity(uuid),
                milestones = milestones.map { it.copy(id = 0L).toEntity() },
            )
        }
        return stored.map { id -> project.copy(id = id, uuid = uuid) }
    }

    override suspend fun update(project: Project): AppResult<Project> {
        unstorableBudget(project)?.let { return it }
        unknownClient(project.clientId)?.let { return it }
        val changed = storageCall(dispatchers, "update project ${project.id}") {
            dao.updateDetails(project.copy(updatedAt = clock.now()).toEntity(project.uuid))
        }
        return when (changed) {
            is AppResult.Failure -> changed
            is AppResult.Success ->
                if (changed.value > 0) byId(project.id)
                else failure(MiraErrorCode.NOT_FOUND, "No project with id ${project.id}")
        }
    }

    override suspend fun setStatus(id: Long, status: ProjectStatus): AppResult<Project> {
        val changed = storageCall(dispatchers, "set project $id status $status") {
            dao.setStatus(id = id, status = status, updatedAt = clock.now().toEpochMilli())
        }
        return when (changed) {
            is AppResult.Failure -> changed
            is AppResult.Success ->
                if (changed.value > 0) byId(id)
                else failure(MiraErrorCode.NOT_FOUND, "No project with id $id")
        }
    }

    override suspend fun softDelete(id: Long): AppResult<Unit> {
        val changed = storageCall(dispatchers, "bin project $id") {
            dao.softDelete(id = id, atEpochMillis = clock.now().toEpochMilli())
        }
        return when (changed) {
            is AppResult.Failure -> changed
            is AppResult.Success ->
                if (changed.value > 0) AppResult.Success(Unit)
                else failure(MiraErrorCode.NOT_FOUND, "No project with id $id")
        }
    }

    override suspend fun restore(id: Long): AppResult<Project> {
        val changed = storageCall(dispatchers, "restore project $id") {
            dao.restore(id = id, updatedAt = clock.now().toEpochMilli())
        }
        return when (changed) {
            is AppResult.Failure -> changed
            is AppResult.Success ->
                if (changed.value > 0) byId(id)
                else failure(MiraErrorCode.NOT_FOUND, "No project with id $id in the bin")
        }
    }

    override suspend fun purge(id: Long): AppResult<Unit> {
        val removed = storageCall(dispatchers, "delete project $id") { dao.deleteById(id) }
        return when (removed) {
            is AppResult.Failure -> removed
            is AppResult.Success ->
                if (removed.value > 0) AppResult.Success(Unit)
                else failure(MiraErrorCode.NOT_FOUND, "No project with id $id")
        }
    }

    override suspend fun addMilestone(milestone: ProjectMilestone): AppResult<ProjectMilestone> {
        val known = storageCall(dispatchers, "load project ${milestone.projectId}") {
            dao.findLiveById(milestone.projectId) != null
        }
        when (known) {
            is AppResult.Failure -> return known
            is AppResult.Success -> if (!known.value) {
                return failure(MiraErrorCode.VALIDATION, "No project with id ${milestone.projectId}")
            }
        }
        val stored = storageCall(dispatchers, "add milestone to project ${milestone.projectId}") {
            dao.appendMilestone(milestone.copy(id = 0L).toEntity())
        }
        return when (stored) {
            is AppResult.Failure -> stored
            is AppResult.Success -> milestoneById(stored.value)
        }
    }

    override suspend fun updateMilestone(milestone: ProjectMilestone): AppResult<ProjectMilestone> {
        val changed = storageCall(dispatchers, "update milestone ${milestone.id}") {
            dao.updateMilestoneDetails(milestone.copy(updatedAt = clock.now()).toEntity())
        }
        return when (changed) {
            is AppResult.Failure -> changed
            is AppResult.Success ->
                if (changed.value > 0) milestoneById(milestone.id)
                else failure(MiraErrorCode.NOT_FOUND, "No milestone with id ${milestone.id}")
        }
    }

    override suspend fun setMilestoneComplete(id: Long, complete: Boolean): AppResult<ProjectMilestone> {
        val now = clock.now()
        val changed = storageCall(dispatchers, "set milestone $id complete=$complete") {
            dao.setMilestoneComplete(
                id = id,
                complete = complete,
                completedAt = if (complete) now.toEpochMilli() else null,
                updatedAt = now.toEpochMilli(),
            )
        }
        return when (changed) {
            is AppResult.Failure -> changed
            is AppResult.Success ->
                if (changed.value > 0) milestoneById(id)
                else failure(MiraErrorCode.NOT_FOUND, "No milestone with id $id")
        }
    }

    override suspend fun deleteMilestone(id: Long): AppResult<Unit> {
        val removed = storageCall(dispatchers, "delete milestone $id") {
            dao.removeMilestone(id = id, updatedAt = clock.now().toEpochMilli())
        }
        return when (removed) {
            is AppResult.Failure -> removed
            is AppResult.Success ->
                if (removed.value > 0) AppResult.Success(Unit)
                else failure(MiraErrorCode.NOT_FOUND, "No milestone with id $id")
        }
    }

    override suspend fun reorderMilestones(
        projectId: Long,
        orderedIds: List<Long>,
    ): AppResult<List<ProjectMilestone>> {
        val applied = storageCall(dispatchers, "reorder milestones of project $projectId") {
            dao.reorderMilestones(
                projectId = projectId,
                orderedIds = orderedIds,
                updatedAt = clock.now().toEpochMilli(),
            )
        }
        return when (applied) {
            is AppResult.Failure -> applied
            is AppResult.Success ->
                if (applied.value) {
                    storageCall(dispatchers, "load milestones of project $projectId") {
                        dao.listMilestones(projectId).map { it.toDomain() }
                    }
                } else {
                    failure(
                        MiraErrorCode.VALIDATION,
                        "The order given does not cover the milestones of project $projectId exactly once",
                    )
                }
        }
    }

    private suspend fun milestoneById(id: Long): AppResult<ProjectMilestone> {
        val found = storageCall(dispatchers, "load milestone $id") { dao.findMilestoneById(id) }
        return when (found) {
            is AppResult.Failure -> found
            is AppResult.Success -> found.value?.toDomain()?.let { AppResult.Success(it) }
                ?: failure(MiraErrorCode.NOT_FOUND, "No milestone with id $id")
        }
    }

    /** Mirrors the SQL predicates, so a search result is filtered the way the lists are. */
    private fun Project.matches(filter: ProjectFilter): Boolean = when (filter) {
        ProjectFilter.ALL -> true
        ProjectFilter.OPEN -> isOpen
        ProjectFilter.ACTIVE -> status == ProjectStatus.ACTIVE
        ProjectFilter.COMPLETED -> status == ProjectStatus.COMPLETED
        ProjectFilter.CANCELLED -> status == ProjectStatus.CANCELLED
    }

    private fun unstorableBudget(project: Project): AppResult.Failure? {
        val budget = project.budget ?: return null
        if (isStorable(budget)) return null
        return failure(
            MiraErrorCode.VALIDATION,
            "Budget ${budget.amount} is finer than ${budget.currency.currencyCode} can hold exactly",
        )
    }

    /**
     * Checked before the write rather than left to the foreign key, and checked more strictly than
     * the foreign key can: a client in the bin still satisfies SQLite, but attaching new work to
     * someone Abu has deleted is a stale pick, and he should be told that rather than shown a
     * storage error.
     */
    private suspend fun unknownClient(clientId: Long?): AppResult.Failure? {
        if (clientId == null) return null
        return when (val known = storageCall(dispatchers, "load client $clientId") { dao.countLiveClient(clientId) }) {
            is AppResult.Failure -> known
            is AppResult.Success ->
                if (known.value > 0) null
                else failure(MiraErrorCode.VALIDATION, "No client with id $clientId to attach the project to")
        }
    }
}
