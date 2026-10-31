package com.rayluxeo.aida.data.business

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.data.TestDispatcherProvider
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.db.entity.ProjectEntity
import com.rayluxeo.aida.data.db.entity.ProjectMilestoneEntity
import com.rayluxeo.aida.data.repository.ClientRepositoryImpl
import com.rayluxeo.aida.data.repository.ProjectRepositoryImpl
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.Project
import com.rayluxeo.aida.domain.model.ProjectFilter
import com.rayluxeo.aida.domain.model.ProjectMilestone
import com.rayluxeo.aida.domain.model.ProjectStatus
import com.rayluxeo.aida.domain.repository.ProjectRepository
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.repository.ActivityLogRepository
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Projects, their milestones, and the two rules the entities declare about them: milestones
 * cascade with their project, and a project outlives the client it was for.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ProjectRepositoryImplTest {

    private lateinit var database: MiraDatabase
    private val clock = FakeMiraClock(LocalDateTime.of(2026, 3, 5, 14, 0))

    @Before
    fun openDatabase() {
        database = openBusinessTestDatabase()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `create stores the project and the milestones typed with it, in the order given`() = runTest {
        val repository = repository()
        val plan = listOf(milestone("Wireframes"), milestone("Build"), milestone("Handover"))

        val created = repository.create(project(name = "Rayluxeo site"), plan).orFail()

        val stored = repository.observeMilestones(created.id).first()
        assertThat(stored.map { it.title }).containsExactly("Wireframes", "Build", "Handover").inOrder()
        assertThat(stored.map { it.position }).containsExactly(0, 1, 2).inOrder()
        assertThat(stored.map { it.projectId }).containsExactly(created.id, created.id, created.id)
    }

    @Test
    fun `a milestone insert that fails takes the project down with it`() = runTest {
        val repository = repository()
        val first = repository.create(project(name = "Rayluxeo site"), listOf(milestone("Wireframes"))).orFail()
        val existingMilestoneId = repository.observeMilestones(first.id).first().single().id

        val secondAttempt = runCatching {
            database.projectDao().insertWithMilestones(
                project = projectEntity(name = "Northfield app"),
                // The id collides with the milestone already stored, so SQLite refuses this row
                // after the project row has been written inside the same transaction.
                milestones = listOf(milestoneEntity(title = "Kickoff", id = existingMilestoneId)),
            )
        }

        assertThat(secondAttempt.isFailure).isTrue()
        assertThat(repository.observe(ProjectFilter.ALL).first().map { it.name })
            .containsExactly("Rayluxeo site")
        assertThat(repository.observeMilestones(first.id).first().map { it.title })
            .containsExactly("Wireframes")
    }

    @Test
    fun `create assigns a uuid and update cannot rewrite it`() = runTest {
        val repository = repository()
        val created = repository.create(project(name = "Rayluxeo site")).orFail()

        val edited = created.copy(uuid = "invented", name = "Rayluxeo site v2")
        val updated = repository.update(edited).orFail()

        assertThat(created.uuid).hasLength(36)
        assertThat(updated.uuid).isEqualTo(created.uuid)
        assertThat(updated.name).isEqualTo("Rayluxeo site v2")
    }

    @Test
    fun `a project attached to a client nobody has is refused before anything is written`() = runTest {
        val repository = repository()

        val created = repository.create(project(name = "Ghost work").copy(clientId = 404L))

        assertThat(created.errorOrFail().code).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(repository.observe(ProjectFilter.ALL).first()).isEmpty()
    }

    @Test
    fun `deleting a client leaves the project standing and orphans the link`() = runTest {
        val repository = repository()
        val clientId = newClient("Ahmed")
        val created = repository.create(project(name = "Rayluxeo site").copy(clientId = clientId)).orFail()

        database.openHelper.writableDatabase.execSQL("DELETE FROM clients WHERE id = $clientId")

        val read = repository.byId(created.id).orFail()
        assertThat(read.name).isEqualTo("Rayluxeo site")
        assertThat(read.clientId).isNull()
    }

    @Test
    fun `deleting a project for good takes its milestones with it`() = runTest {
        val repository = repository()
        val created = repository.create(project(name = "Rayluxeo site"), listOf(milestone("Wireframes"))).orFail()

        repository.purge(created.id).orFail()

        assertThat(database.projectDao().listMilestones(created.id)).isEmpty()
        assertThat(repository.observe(ProjectFilter.ALL).first()).isEmpty()
    }

    @Test
    fun `binning a project hides it and its milestones, and restoring brings both back`() = runTest {
        val repository = repository()
        val created = repository.create(project(name = "Rayluxeo site"), listOf(milestone("Wireframes"))).orFail()

        repository.softDelete(created.id).orFail()

        assertThat(repository.observe(ProjectFilter.ALL).first()).isEmpty()
        assertThat(repository.observeMilestones(created.id).first()).isEmpty()
        assertThat(repository.search(null, ProjectFilter.ALL).orFail()).isEmpty()
        assertThat(repository.byId(created.id).errorOrFail().code).isEqualTo(MiraErrorCode.NOT_FOUND)

        val restored = repository.restore(created.id).orFail()

        assertThat(restored.id).isEqualTo(created.id)
        assertThat(restored.uuid).isEqualTo(created.uuid)
        assertThat(repository.observeMilestones(created.id).first()).hasSize(1)
    }

    @Test
    fun `the open list leaves out what is finished`() = runTest {
        val repository = repository()
        val live = repository.create(project(name = "Rayluxeo site")).orFail()
        val finished = project(name = "Old brochure").copy(status = ProjectStatus.COMPLETED)
        val done = repository.create(finished).orFail()

        assertThat(repository.observe(ProjectFilter.OPEN).first().map { it.id }).containsExactly(live.id)
        assertThat(repository.observe(ProjectFilter.COMPLETED).first().map { it.id }).containsExactly(done.id)
        assertThat(repository.observe(ProjectFilter.ALL).first()).hasSize(2)
    }

    @Test
    fun `setStatus moves a project out of the open list`() = runTest {
        val repository = repository()
        val created = repository.create(project(name = "Rayluxeo site")).orFail()

        val updated = repository.setStatus(created.id, ProjectStatus.COMPLETED).orFail()

        assertThat(updated.status).isEqualTo(ProjectStatus.COMPLETED)
        assertThat(repository.observe(ProjectFilter.OPEN).first()).isEmpty()
    }

    @Test
    fun `search reads the description as well as the name`() = runTest {
        val repository = repository()
        val described = project(name = "Rayluxeo site").copy(description = "Brand refresh and build")
        repository.create(described).orFail()
        repository.create(project(name = "Northfield app")).orFail()

        val found = repository.search("brand", ProjectFilter.ALL).orFail()

        assertThat(found.map { it.name }).containsExactly("Rayluxeo site")
    }

    @Test
    fun `a blank query means no filter rather than no results`() = runTest {
        val repository = repository()
        repository.create(project(name = "Rayluxeo site")).orFail()
        repository.create(project(name = "Northfield app")).orFail()

        assertThat(repository.search(" ", ProjectFilter.ALL).orFail()).hasSize(2)
    }

    @Test
    fun `search honours the filter it was given`() = runTest {
        val repository = repository()
        val finished = project(name = "Rayluxeo site").copy(status = ProjectStatus.COMPLETED)
        repository.create(finished).orFail()

        assertThat(repository.search("rayluxeo", ProjectFilter.OPEN).orFail()).isEmpty()
        assertThat(repository.search("rayluxeo", ProjectFilter.ALL).orFail()).hasSize(1)
    }

    @Test
    fun `projects list only the ones for the client asked about`() = runTest {
        val repository = repository()
        val ahmed = newClient("Ahmed")
        val karim = newClient("Karim")
        repository.create(project(name = "Rayluxeo site").copy(clientId = ahmed)).orFail()
        repository.create(project(name = "Northfield app").copy(clientId = karim)).orFail()

        val forAhmed = repository.observeForClient(ahmed).first()

        assertThat(forAhmed.map { it.name }).containsExactly("Rayluxeo site")
    }

    @Test
    fun `a milestone is appended at the end whatever position it arrives with`() = runTest {
        val repository = repository()
        val created = repository.create(project(name = "Rayluxeo site"), listOf(milestone("Wireframes"))).orFail()

        val arriving = milestone("Build").copy(projectId = created.id, position = 99)
        val added = repository.addMilestone(arriving).orFail()

        assertThat(added.position).isEqualTo(1)
        assertThat(repository.observeMilestones(created.id).first().map { it.title })
            .containsExactly("Wireframes", "Build").inOrder()
    }

    @Test
    fun `a milestone for a project that is not there is refused`() = runTest {
        val added = repository().addMilestone(milestone("Build").copy(projectId = 404L))

        assertThat(added.errorOrFail().code).isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `deleting a milestone closes the gap it leaves`() = runTest {
        val repository = repository()
        val plan = listOf(milestone("Wireframes"), milestone("Build"), milestone("Handover"))
        val created = repository.create(project(name = "Rayluxeo site"), plan).orFail()
        val stored = repository.observeMilestones(created.id).first()

        repository.deleteMilestone(stored[1].id).orFail()

        val left = repository.observeMilestones(created.id).first()
        assertThat(left.map { it.title }).containsExactly("Wireframes", "Handover").inOrder()
        assertThat(left.map { it.position }).containsExactly(0, 1).inOrder()
    }

    @Test
    fun `reordering renumbers the whole project from zero`() = runTest {
        val repository = repository()
        val plan = listOf(milestone("Wireframes"), milestone("Build"), milestone("Handover"))
        val created = repository.create(project(name = "Rayluxeo site"), plan).orFail()
        val stored = repository.observeMilestones(created.id).first()

        val order = listOf(stored[2].id, stored[0].id, stored[1].id)
        val reordered = repository.reorderMilestones(created.id, order).orFail()

        assertThat(reordered.map { it.title }).containsExactly("Handover", "Wireframes", "Build").inOrder()
        assertThat(reordered.map { it.position }).containsExactly(0, 1, 2).inOrder()
        assertThat(repository.observeMilestones(created.id).first().map { it.title })
            .containsExactly("Handover", "Wireframes", "Build").inOrder()
    }

    @Test
    fun `an order that does not name every milestone is refused and changes nothing`() = runTest {
        val repository = repository()
        val plan = listOf(milestone("Wireframes"), milestone("Build"), milestone("Handover"))
        val created = repository.create(project(name = "Rayluxeo site"), plan).orFail()
        val stored = repository.observeMilestones(created.id).first()

        val partial = listOf(stored[2].id, stored[0].id)
        val reordered = repository.reorderMilestones(created.id, partial)

        assertThat(reordered.errorOrFail().code).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(repository.observeMilestones(created.id).first().map { it.title })
            .containsExactly("Wireframes", "Build", "Handover").inOrder()
    }

    @Test
    fun `completing a milestone stamps the time and undoing it clears the stamp`() = runTest {
        val repository = repository()
        val created = repository.create(project(name = "Rayluxeo site"), listOf(milestone("Wireframes"))).orFail()
        val id = repository.observeMilestones(created.id).first().single().id

        val done = repository.setMilestoneComplete(id, complete = true).orFail()
        assertThat(done.isComplete).isTrue()
        assertThat(done.completedAt).isEqualTo(clock.now())

        val undone = repository.setMilestoneComplete(id, complete = false).orFail()
        assertThat(undone.isComplete).isFalse()
        assertThat(undone.completedAt).isNull()
    }

    @Test
    fun `dates with no time of day survive as the same day`() = runTest {
        val repository = repository()
        val due = LocalDate.of(2026, 4, 30)
        val dated = project(name = "Rayluxeo site").copy(startedOn = LocalDate.of(2026, 3, 1), dueOn = due)

        val created = repository.create(dated, listOf(milestone("Wireframes").copy(dueOn = due))).orFail()

        val read = repository.byId(created.id).orFail()
        assertThat(read.dueOn).isEqualTo(due)
        assertThat(read.startedOn).isEqualTo(LocalDate.of(2026, 3, 1))
        assertThat(repository.observeMilestones(created.id).first().single().dueOn).isEqualTo(due)
        assertThat(database.projectDao().findById(created.id)?.dueOnEpochDay).isEqualTo(due.toEpochDay())
    }

    @Test
    fun `a budget survives the database exactly`() = runTest {
        val repository = repository()
        val budget = Money.of("1234.56")

        val created = repository.create(project(name = "Rayluxeo site").copy(budget = budget)).orFail()

        val read = repository.byId(created.id).orFail()
        assertThat(read.budget).isEqualTo(budget)
        assertThat(read.budget?.amount).isEqualTo(BigDecimal("1234.56"))
        assertThat(database.projectDao().findById(created.id)?.budgetMinor).isEqualTo(123_456L)
    }

    @Test
    fun `many budgets read back and added up do not drift by a paisa`() = runTest {
        val repository = repository()
        val amounts = listOf(
            "0.01", "0.07", "1234.56", "19.99", "0.03", "88888.88", "7.77", "0.05", "45678.90", "0.99",
        )
        amounts.forEachIndexed { index, amount ->
            repository.create(project(name = "Work $index").copy(budget = Money.of(amount))).orFail()
        }

        val total = repository.observe(ProjectFilter.ALL).first()
            .mapNotNull { it.budget }
            .fold(Money.zero()) { running, budget -> running + budget }

        assertThat(total.amount).isEqualTo(BigDecimal("135831.25"))
        assertThat(total).isEqualTo(Money.of("135831.25"))
    }

    @Test
    fun `a budget finer than its currency can hold is refused rather than rounded`() = runTest {
        val overPrecise = project(name = "Rayluxeo site").copy(budget = Money(BigDecimal("12.345"), Money.BDT))

        val created = repository().create(overPrecise)

        assertThat(created.errorOrFail().code).isEqualTo(MiraErrorCode.VALIDATION)
    }

    /** Typed as the port, because that is the surface the rest of the app will hold. */
    private fun repository(): ProjectRepository = ProjectRepositoryImpl(
        dao = database.projectDao(),
        clock = clock,
        dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher()),
    )

    private suspend fun newClient(name: String): Long {
        val clients = ClientRepositoryImpl(
            dao = database.clientDao(),
            activityLog = FakeTestActivityLog(),
            clock = clock,
            dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher()),
        )
        return clients.create(Client(name = name, createdAt = clock.now(), updatedAt = clock.now())).orFail().id
    }

    private fun project(name: String): Project = Project(
        name = name,
        createdAt = clock.now(),
        updatedAt = clock.now(),
    )

    private fun milestone(title: String): ProjectMilestone = ProjectMilestone(
        projectId = 0L,
        title = title,
        createdAt = clock.now(),
        updatedAt = clock.now(),
    )

    /** Built by hand rather than mapped, because this one is fed straight to the DAO. */
    private fun projectEntity(name: String) = ProjectEntity(
        uuid = "33333333-3333-3333-3333-333333333333",
        clientId = null,
        name = name,
        description = null,
        status = ProjectStatus.ACTIVE,
        startedOnEpochDay = null,
        dueOnEpochDay = null,
        budgetMinor = null,
        currencyCode = "BDT",
        deletedAtEpochMillis = null,
        createdAtEpochMillis = clock.now().toEpochMilli(),
        updatedAtEpochMillis = clock.now().toEpochMilli(),
    )

    private fun milestoneEntity(title: String, id: Long) = ProjectMilestoneEntity(
        id = id,
        projectId = 0L,
        title = title,
        dueOnEpochDay = null,
        isComplete = false,
        completedAtEpochMillis = null,
        position = 0,
        createdAtEpochMillis = clock.now().toEpochMilli(),
        updatedAtEpochMillis = clock.now().toEpochMilli(),
    )
}
