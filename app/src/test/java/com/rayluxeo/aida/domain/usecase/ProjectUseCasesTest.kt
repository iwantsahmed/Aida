package com.rayluxeo.aida.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.Project
import com.rayluxeo.aida.domain.model.ProjectFilter
import com.rayluxeo.aida.domain.model.ProjectMilestone
import com.rayluxeo.aida.domain.model.ProjectStatus
import com.rayluxeo.aida.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * These cover the only logic the project write path carries that is not the repository's: a project
 * must have a name, and its timestamps come from the clock rather than the wall (§4). The thin
 * status/bin transitions are checked to forward to the right place, so a future refactor that drops
 * one is a red test rather than a dead button.
 */
class ProjectUseCasesTest {

    private val now: Instant = Instant.parse("2026-10-16T09:00:00Z")
    private val clock = FixedClock(now)
    private val repository = FakeProjectRepository()

    @Test
    fun `create refuses a blank name`() = runTest {
        val result = CreateProjectUseCase(repository, clock)(name = "   ")

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        assertThat((result as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(repository.created).isNull()
    }

    @Test
    fun `create trims the name, stamps the clock and forwards to the repository`() = runTest {
        val result = CreateProjectUseCase(repository, clock)(
            name = "  Rayluxeo Website  ",
            clientId = 7L,
            description = "  the site  ",
            status = ProjectStatus.PLANNED,
            dueOn = LocalDate.of(2026, 12, 1),
            budget = Money.of("50000", Money.BDT),
        )

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        val stored = repository.created!!
        assertThat(stored.name).isEqualTo("Rayluxeo Website")
        assertThat(stored.description).isEqualTo("the site")
        assertThat(stored.clientId).isEqualTo(7L)
        assertThat(stored.status).isEqualTo(ProjectStatus.PLANNED)
        assertThat(stored.createdAt).isEqualTo(now)
        assertThat(stored.updatedAt).isEqualTo(now)
        // The budget is carried in its own currency, so the project's currency matches it (the
        // Project constructor would throw otherwise).
        assertThat(stored.currency).isEqualTo(Money.BDT)
    }

    @Test
    fun `create defaults an empty description to null and status to active`() = runTest {
        CreateProjectUseCase(repository, clock)(name = "Plain", description = "   ")

        val stored = repository.created!!
        assertThat(stored.description).isNull()
        assertThat(stored.status).isEqualTo(ProjectStatus.ACTIVE)
        assertThat(stored.budget).isNull()
    }

    @Test
    fun `update refuses a blank name`() = runTest {
        val result = UpdateProjectUseCase(repository, clock)(sampleProject.copy(name = "  "))

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        assertThat((result as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(repository.updated).isNull()
    }

    @Test
    fun `update restamps updatedAt and trims the name, leaving createdAt alone`() = runTest {
        val original = sampleProject.copy(
            name = "  Renamed  ",
            createdAt = Instant.parse("2020-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2020-01-01T00:00:00Z"),
        )

        UpdateProjectUseCase(repository, clock)(original)

        val stored = repository.updated!!
        assertThat(stored.name).isEqualTo("Renamed")
        assertThat(stored.updatedAt).isEqualTo(now)
        assertThat(stored.createdAt).isEqualTo(Instant.parse("2020-01-01T00:00:00Z"))
    }

    @Test
    fun `setStatus forwards the id and status to the repository`() = runTest {
        SetProjectStatusUseCase(repository)(id = 42L, status = ProjectStatus.COMPLETED)

        assertThat(repository.statusChange).isEqualTo(42L to ProjectStatus.COMPLETED)
    }

    @Test
    fun `softDelete and restore forward the id`() = runTest {
        SoftDeleteProjectUseCase(repository)(id = 5L)
        RestoreProjectUseCase(repository)(id = 5L)

        assertThat(repository.deletedId).isEqualTo(5L)
        assertThat(repository.restoredId).isEqualTo(5L)
    }

    private val sampleProject = Project(
        id = 1L,
        uuid = "uuid-1",
        name = "Sample",
        createdAt = now,
        updatedAt = now,
    )

    private class FixedClock(private val instant: Instant) : MiraClock {
        override fun now(): Instant = instant
        override fun zone(): ZoneId = ZoneId.of("UTC")
    }

    /**
     * A fake that records the write it was handed. Reads and milestone ops are not exercised by the
     * use cases under test, so they return harmless defaults rather than pretending to be a database.
     */
    private class FakeProjectRepository : ProjectRepository {
        var created: Project? = null
        var updated: Project? = null
        var statusChange: Pair<Long, ProjectStatus>? = null
        var deletedId: Long? = null
        var restoredId: Long? = null

        override fun observe(filter: ProjectFilter): Flow<List<Project>> = flowOf(emptyList())
        override fun observeForClient(clientId: Long): Flow<List<Project>> = flowOf(emptyList())
        override fun observeMilestones(projectId: Long): Flow<List<ProjectMilestone>> = flowOf(emptyList())

        override suspend fun byId(id: Long): AppResult<Project> =
            AppResult.Failure(com.rayluxeo.aida.core.common.MiraError(MiraErrorCode.NOT_FOUND))

        override suspend fun search(query: String?, filter: ProjectFilter): AppResult<List<Project>> =
            AppResult.Success(emptyList())

        override suspend fun create(project: Project, milestones: List<ProjectMilestone>): AppResult<Project> {
            created = project
            return AppResult.Success(project.copy(id = 1L, uuid = "uuid-created"))
        }

        override suspend fun update(project: Project): AppResult<Project> {
            updated = project
            return AppResult.Success(project)
        }

        override suspend fun setStatus(id: Long, status: ProjectStatus): AppResult<Project> {
            statusChange = id to status
            return AppResult.Success(project(id).copy(status = status))
        }

        override suspend fun softDelete(id: Long): AppResult<Unit> {
            deletedId = id
            return AppResult.Success(Unit)
        }

        override suspend fun restore(id: Long): AppResult<Project> {
            restoredId = id
            return AppResult.Success(project(id))
        }

        override suspend fun purge(id: Long): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun addMilestone(milestone: ProjectMilestone): AppResult<ProjectMilestone> =
            AppResult.Success(milestone)

        override suspend fun updateMilestone(milestone: ProjectMilestone): AppResult<ProjectMilestone> =
            AppResult.Success(milestone)

        override suspend fun setMilestoneComplete(id: Long, complete: Boolean): AppResult<ProjectMilestone> =
            AppResult.Failure(com.rayluxeo.aida.core.common.MiraError(MiraErrorCode.NOT_FOUND))

        override suspend fun deleteMilestone(id: Long): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun reorderMilestones(projectId: Long, orderedIds: List<Long>): AppResult<List<ProjectMilestone>> =
            AppResult.Success(emptyList())

        private fun project(id: Long): Project = Project(
            id = id,
            uuid = "uuid-$id",
            name = "Sample",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
    }
}
