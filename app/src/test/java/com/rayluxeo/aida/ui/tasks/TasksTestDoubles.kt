package com.rayluxeo.aida.ui.tasks

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.ClientFilter
import com.rayluxeo.aida.domain.model.DailyBrief
import com.rayluxeo.aida.domain.model.Project
import com.rayluxeo.aida.domain.model.ProjectFilter
import com.rayluxeo.aida.domain.model.ProjectMilestone
import com.rayluxeo.aida.domain.model.ProjectStatus
import com.rayluxeo.aida.domain.model.Reminder
import com.rayluxeo.aida.domain.model.ReminderPlace
import com.rayluxeo.aida.domain.model.ReminderKind
import com.rayluxeo.aida.domain.model.ReminderState
import com.rayluxeo.aida.domain.model.Subtask
import com.rayluxeo.aida.domain.model.Tag
import com.rayluxeo.aida.domain.model.Task
import com.rayluxeo.aida.domain.model.TaskFilter
import com.rayluxeo.aida.domain.model.TaskPriority
import com.rayluxeo.aida.domain.model.TaskQuery
import com.rayluxeo.aida.domain.model.TaskSort
import com.rayluxeo.aida.domain.repository.BriefRepository
import com.rayluxeo.aida.domain.repository.ClientRepository
import com.rayluxeo.aida.domain.repository.MiraSettings
import com.rayluxeo.aida.domain.repository.ProjectRepository
import com.rayluxeo.aida.domain.repository.ReminderPlaceRepository
import com.rayluxeo.aida.domain.repository.ReminderRepository
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.repository.SubtaskRepository
import com.rayluxeo.aida.domain.repository.TagRepository
import com.rayluxeo.aida.domain.repository.TagUsage
import com.rayluxeo.aida.domain.repository.TaskCounts
import com.rayluxeo.aida.domain.repository.TaskGroup
import com.rayluxeo.aida.domain.repository.TaskOccurrence
import com.rayluxeo.aida.domain.repository.TaskRepository
import com.rayluxeo.aida.domain.scheduler.PlaceReminderScheduler
import com.rayluxeo.aida.domain.scheduler.PlaceWatchRefusal
import com.rayluxeo.aida.domain.scheduler.PlaceWatchResult
import com.rayluxeo.aida.domain.scheduler.ReminderScheduler
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Hand-written collaborators for the Home and Tasks view models.
 *
 * Hilt's test bytecode transform does not run under AGP 9, so nothing here is injected: each test
 * builds the view model itself out of these. They behave like the real repositories in the ways the
 * screens depend on — the day windows match, the flows are live, and failures are values.
 */
class FakeTaskRepository(private val clock: MiraClock) : TaskRepository {

    private val rows = MutableStateFlow<List<Task>>(emptyList())
    private var nextId = 1L

    /** Set to make the next mutating call fail, so error paths can be exercised. */
    var failWith: MiraErrorCode? = null

    /** The names storage would join in for the grouped views, and for [setTags]. */
    val projectNames = mutableMapOf<Long, String>()
    val clientNames = mutableMapOf<Long, String>()
    val tagNames = mutableMapOf<Long, String>()

    override fun observe(filter: TaskFilter): Flow<List<Task>> = observe(TaskQuery(filter = filter))

    override fun observe(query: TaskQuery): Flow<List<Task>> = rows.map { it.select(query) }

    override fun observeCounts(): Flow<TaskCounts> = rows.map { all ->
        TaskCounts(
            open = all.select(TaskQuery(filter = TaskFilter.OPEN)).size,
            dueToday = all.select(TaskQuery(filter = TaskFilter.DUE_TODAY)).size,
            overdue = all.select(TaskQuery(filter = TaskFilter.OVERDUE)).size,
            completedToday = all.count { task ->
                task.deletedAt == null && task.isDone &&
                    task.completedAt?.let { it >= startOfToday() && it < startOfTomorrow() } == true
            },
        )
    }

    /** Reads the row in any state, including binned, exactly as the port promises restore needs. */
    override suspend fun byId(id: Long): AppResult<Task> =
        rows.value.firstOrNull { it.id == id }
            ?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No task with id $id")

    override suspend fun list(query: TaskQuery): AppResult<List<Task>> {
        consumeFailure()?.let { return it }
        return AppResult.Success(rows.value.select(query))
    }

    override suspend fun count(query: TaskQuery): AppResult<Int> {
        consumeFailure()?.let { return it }
        return AppResult.Success(rows.value.select(query).size)
    }

    override suspend fun search(query: String?, filter: TaskFilter): AppResult<List<Task>> {
        consumeFailure()?.let { return it }
        val text = query?.trim()?.takeIf { it.isNotEmpty() }
        return AppResult.Success(rows.value.select(TaskQuery(filter = filter, text = text)))
    }

    override suspend fun groupedByProject(query: TaskQuery): AppResult<List<TaskGroup>> =
        group(query, { it.projectId }, projectNames)

    override suspend fun groupedByClient(query: TaskQuery): AppResult<List<TaskGroup>> =
        group(query, { it.clientId }, clientNames)

    override suspend fun create(task: Task): AppResult<Task> {
        consumeFailure()?.let { return it }
        val saved = task.copy(id = nextId++)
        rows.value = rows.value + saved
        return AppResult.Success(saved)
    }

    /** Tags, steps and attachments are not read off [task], exactly as the port says. */
    override suspend fun update(task: Task): AppResult<Task> {
        consumeFailure()?.let { return it }
        val existing = rows.value.firstOrNull { it.id == task.id }
            ?: return failure(MiraErrorCode.NOT_FOUND, "No task with id ${task.id}")
        val saved = task.copy(
            tags = existing.tags,
            subtasks = existing.subtasks,
            attachmentIds = existing.attachmentIds,
            updatedAt = clock.now(),
        )
        replace(saved)
        return AppResult.Success(saved)
    }

    override suspend fun setDone(id: Long, done: Boolean): AppResult<Task> =
        change(id) { it.copy(isDone = done, completedAt = if (done) clock.now() else null) }

    override suspend fun setDoneAll(ids: Collection<Long>, done: Boolean): AppResult<Int> =
        changeAll(ids) { it.copy(isDone = done, completedAt = if (done) clock.now() else null) }

    override suspend fun snooze(id: Long, until: Instant): AppResult<Task> =
        change(id) { it.copy(dueAt = until, dueAllDay = false) }

    override suspend fun archive(id: Long): AppResult<Task> = change(id) { it.copy(archivedAt = clock.now()) }

    override suspend fun unarchive(id: Long): AppResult<Task> = change(id) { it.copy(archivedAt = null) }

    override suspend fun archiveAll(ids: Collection<Long>): AppResult<Int> =
        changeAll(ids) { it.copy(archivedAt = clock.now()) }

    /** Soft, like the real repository: the row stays so [restore] can bring back the same id. */
    override suspend fun delete(id: Long): AppResult<Unit> {
        consumeFailure()?.let { return it }
        if (rows.value.live().none { it.id == id }) {
            return failure(MiraErrorCode.NOT_FOUND, "No task with id $id")
        }
        replace(rows.value.first { it.id == id }.copy(deletedAt = clock.now(), updatedAt = clock.now()))
        return AppResult.Success(Unit)
    }

    override suspend fun deleteAll(ids: Collection<Long>): AppResult<Int> {
        consumeFailure()?.let { return it }
        val live = rows.value.live().filter { it.id in ids }
        live.forEach { replace(it.copy(deletedAt = clock.now(), updatedAt = clock.now())) }
        return AppResult.Success(live.size)
    }

    override suspend fun restore(id: Long): AppResult<Task> {
        consumeFailure()?.let { return it }
        val deleted = rows.value.firstOrNull { it.id == id && it.deletedAt != null }
            ?: return failure(MiraErrorCode.NOT_FOUND, "No deleted task with id $id")
        val restored = deleted.copy(deletedAt = null, updatedAt = clock.now())
        replace(restored)
        return AppResult.Success(restored)
    }

    override suspend fun completeWithNextOccurrence(id: Long, nextDueAt: Instant): AppResult<TaskOccurrence> {
        consumeFailure()?.let { return it }
        val existing = rows.value.firstOrNull { it.id == id }
            ?: return failure(MiraErrorCode.NOT_FOUND, "No task with id $id")
        val completed = existing.copy(isDone = true, completedAt = clock.now(), updatedAt = clock.now())
        replace(completed)
        val next = existing.copy(
            id = nextId++,
            isDone = false,
            completedAt = null,
            dueAt = nextDueAt,
            createdAt = clock.now(),
            updatedAt = clock.now(),
        )
        rows.value = rows.value + next
        return AppResult.Success(TaskOccurrence(completed = completed, next = next))
    }

    override suspend fun setTags(taskId: Long, tagIds: Collection<Long>): AppResult<Task> =
        change(taskId) { task -> task.copy(tags = tagIds.map { Tag(it, tagNames[it] ?: "tag $it") }) }

    override suspend fun addTag(taskId: Long, tagId: Long): AppResult<Task> = change(taskId) { task ->
        if (task.tags.any { it.id == tagId }) {
            task
        } else {
            task.copy(tags = task.tags + Tag(tagId, tagNames[tagId] ?: "tag $tagId"))
        }
    }

    override suspend fun removeTag(taskId: Long, tagId: Long): AppResult<Task> =
        change(taskId) { task -> task.copy(tags = task.tags.filterNot { it.id == tagId }) }

    override suspend fun attachFile(taskId: Long, fileId: Long): AppResult<Task> =
        change(taskId) { it.copy(attachmentIds = (it.attachmentIds + fileId).distinct()) }

    override suspend fun detachFile(taskId: Long, fileId: Long): AppResult<Task> =
        change(taskId) { it.copy(attachmentIds = it.attachmentIds - fileId) }

    /** Inserts tasks without going through [create], so ids stay predictable in assertions. */
    fun seed(vararg tasks: Task) {
        rows.value = tasks.map { task ->
            if (task.id == 0L) task.copy(id = nextId++) else task.also { nextId = maxOf(nextId, it.id + 1) }
        }
    }

    /** What the screens would see: binned tasks are as good as gone. */
    fun snapshot(): List<Task> = rows.value.live()

    private fun replace(task: Task) {
        rows.value = rows.value.map { if (it.id == task.id) task else it }
    }

    private fun change(id: Long, transform: (Task) -> Task): AppResult<Task> {
        consumeFailure()?.let { return it }
        val existing = rows.value.firstOrNull { it.id == id }
            ?: return failure(MiraErrorCode.NOT_FOUND, "No task with id $id")
        val saved = transform(existing).copy(updatedAt = clock.now())
        replace(saved)
        return AppResult.Success(saved)
    }

    private fun changeAll(ids: Collection<Long>, transform: (Task) -> Task): AppResult<Int> {
        consumeFailure()?.let { return it }
        val affected = rows.value.live().filter { it.id in ids }
        affected.forEach { replace(transform(it).copy(updatedAt = clock.now())) }
        return AppResult.Success(affected.size)
    }

    private fun group(
        query: TaskQuery,
        key: (Task) -> Long?,
        names: Map<Long, String>,
    ): AppResult<List<TaskGroup>> {
        consumeFailure()?.let { return it }
        return AppResult.Success(
            rows.value.select(query)
                .groupBy(key)
                .map { (id, tasks) -> TaskGroup(id = id, name = id?.let(names::get), tasks = tasks) }
                .sortedWith(compareBy({ it.id == null }, { it.name.orEmpty() })),
        )
    }

    private fun List<Task>.live(): List<Task> = filter { it.deletedAt == null }

    private fun consumeFailure(): AppResult.Failure? {
        val code = failWith ?: return null
        failWith = null
        return failure(code, "Injected by FakeTaskRepository")
    }

    /** Every dimension of [TaskQuery], applied the way the screens assume storage applies them. */
    private fun List<Task>.select(query: TaskQuery): List<Task> = live()
        .filter { it.matches(query.filter) }
        .filter { task -> query.text?.let { task.matchesText(it) } ?: true }
        .filter { query.projectId == null || it.projectId == query.projectId }
        .filter { query.clientId == null || it.clientId == query.clientId }
        .filter { task -> query.tagIds.isEmpty() || task.tags.any { it.id in query.tagIds } }
        .filter { query.priorities.isEmpty() || it.priority in query.priorities }
        .sortedWith(query.ordering())

    private fun Task.matchesText(text: String): Boolean {
        val needle = text.trim().lowercase()
        return needle.isEmpty() ||
            title.lowercase().contains(needle) ||
            description?.lowercase()?.contains(needle) == true ||
            notes?.lowercase()?.contains(needle) == true
    }

    /**
     * An undated task sorts last whichever way the dates run: reversing the whole order would put
     * "no date" at the top of a list that is meant to be about what is next.
     */
    private fun TaskQuery.ordering(): Comparator<Task> = when (sort) {
        TaskSort.DUE_DATE -> {
            val dated = compareBy<Task> { it.dueAt }
            compareBy<Task> { it.dueAt == null }
                .then(if (ascending) dated else dated.reversed())
                .thenBy { -it.createdAt.toEpochMilli() }
        }
        TaskSort.PRIORITY -> direction(compareBy { it.priority.ordinal })
        TaskSort.CREATED -> direction(compareBy { it.createdAt })
        TaskSort.UPDATED -> direction(compareBy { it.updatedAt })
        TaskSort.TITLE -> direction(compareBy { it.title.lowercase() })
    }

    private fun TaskQuery.direction(base: Comparator<Task>): Comparator<Task> =
        if (ascending) base else base.reversed()

    /**
     * Archived work is out of every list but [TaskFilter.ALL] and [TaskFilter.ARCHIVED].
     *
     * That is what makes archiving reversible: the All view is the one place Abu can find a task
     * he archived by a swipe, and take it back.
     */
    private fun Task.matches(filter: TaskFilter): Boolean {
        val due = dueAt
        val archived = archivedAt != null
        return when (filter) {
            TaskFilter.ALL -> true
            TaskFilter.OPEN -> !isDone && !archived
            TaskFilter.DONE -> isDone && !archived
            TaskFilter.DUE_TODAY ->
                !isDone && !archived && due != null && due >= startOfToday() && due < startOfTomorrow()
            TaskFilter.OVERDUE -> !isDone && !archived && due != null && due < startOfToday()
            TaskFilter.UPCOMING -> !isDone && !archived && due != null && due >= startOfTomorrow()
            TaskFilter.ARCHIVED -> archived
            TaskFilter.NO_DATE -> !isDone && !archived && due == null
        }
    }

    private fun startOfToday(): Instant = clock.today().atStartOfDay(clock.zone()).toInstant()

    private fun startOfTomorrow(): Instant = clock.today().plusDays(1).atStartOfDay(clock.zone()).toInstant()
}

/** The steps inside a task, for the paths that create one with a checklist. */
class FakeSubtaskRepository : SubtaskRepository {

    private val rows = MutableStateFlow<List<Subtask>>(emptyList())
    private var nextId = 1L

    override fun observeForTask(taskId: Long): Flow<List<Subtask>> =
        rows.map { list -> list.filter { it.taskId == taskId }.sortedBy { it.position } }

    override suspend fun byId(id: Long): AppResult<Subtask> =
        rows.value.firstOrNull { it.id == id }
            ?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No subtask with id $id")

    override suspend fun add(taskId: Long, title: String): AppResult<Subtask> {
        val clean = title.trim()
        if (clean.isEmpty()) return failure(MiraErrorCode.VALIDATION, "Blank step")
        val step = Subtask(
            id = nextId++,
            taskId = taskId,
            title = clean,
            position = rows.value.count { it.taskId == taskId },
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        rows.value = rows.value + step
        return AppResult.Success(step)
    }

    override suspend fun rename(id: Long, title: String): AppResult<Subtask> = unusedDouble()

    override suspend fun setDone(id: Long, done: Boolean): AppResult<Subtask> = unusedDouble()

    override suspend fun reorder(taskId: Long, orderedIds: List<Long>): AppResult<Unit> = unusedDouble()

    override suspend fun delete(id: Long): AppResult<Unit> = unusedDouble()
}

/** The labels the filter sheet offers. The Tasks list reads them and never changes them. */
class FakeTagRepository : TagRepository {

    private val rows = MutableStateFlow<List<TagUsage>>(emptyList())

    override fun observeAll(): Flow<List<Tag>> = rows.map { list -> list.map { it.tag } }

    override fun observeUsage(): Flow<List<TagUsage>> = rows

    override suspend fun byId(id: Long): AppResult<Tag> =
        rows.value.firstOrNull { it.tag.id == id }
            ?.let { AppResult.Success(it.tag) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No tag with id $id")

    override suspend fun ensure(name: String): AppResult<Tag> = unusedDouble()

    override suspend fun rename(id: Long, name: String): AppResult<Tag> = unusedDouble()

    override suspend fun delete(id: Long): AppResult<Unit> = unusedDouble()

    fun seed(vararg usage: TagUsage) {
        rows.value = usage.toList()
    }
}

/** Only [observe] is real: the Tasks list reads project names and changes nothing about them. */
class FakeProjectRepository : ProjectRepository {

    private val rows = MutableStateFlow<List<Project>>(emptyList())

    override fun observe(filter: ProjectFilter): Flow<List<Project>> = rows

    override fun observeForClient(clientId: Long): Flow<List<Project>> =
        rows.map { list -> list.filter { it.clientId == clientId } }

    override fun observeMilestones(projectId: Long): Flow<List<ProjectMilestone>> = unusedDouble()

    override suspend fun byId(id: Long): AppResult<Project> =
        rows.value.firstOrNull { it.id == id }
            ?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No project with id $id")

    /** Real for the same reason the client double's is — see [FakeClientRepository.search]. */
    override suspend fun search(query: String?, filter: ProjectFilter): AppResult<List<Project>> {
        val needle = query?.trim()?.lowercase()
        return AppResult.Success(
            rows.value.filter { candidate ->
                needle.isNullOrEmpty() || candidate.name.lowercase().contains(needle)
            },
        )
    }

    override suspend fun create(project: Project, milestones: List<ProjectMilestone>): AppResult<Project> =
        unusedDouble()

    override suspend fun update(project: Project): AppResult<Project> = unusedDouble()

    override suspend fun setStatus(id: Long, status: ProjectStatus): AppResult<Project> = unusedDouble()

    override suspend fun softDelete(id: Long): AppResult<Unit> = unusedDouble()

    override suspend fun restore(id: Long): AppResult<Project> = unusedDouble()

    override suspend fun purge(id: Long): AppResult<Unit> = unusedDouble()

    override suspend fun addMilestone(milestone: ProjectMilestone): AppResult<ProjectMilestone> = unusedDouble()

    override suspend fun updateMilestone(milestone: ProjectMilestone): AppResult<ProjectMilestone> = unusedDouble()

    override suspend fun setMilestoneComplete(id: Long, complete: Boolean): AppResult<ProjectMilestone> =
        unusedDouble()

    override suspend fun deleteMilestone(id: Long): AppResult<Unit> = unusedDouble()

    override suspend fun reorderMilestones(
        projectId: Long,
        orderedIds: List<Long>,
    ): AppResult<List<ProjectMilestone>> = unusedDouble()

    fun seed(vararg projects: Project) {
        rows.value = projects.toList()
    }
}

/** Only [observe] is real, for the same reason as [FakeProjectRepository]. */
class FakeClientRepository : ClientRepository {

    private val rows = MutableStateFlow<List<Client>>(emptyList())

    override fun observe(filter: ClientFilter): Flow<List<Client>> = rows

    override suspend fun byId(id: Long): AppResult<Client> =
        rows.value.firstOrNull { it.id == id }
            ?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No client with id $id")

    /**
     * Real, not a refusal.
     *
     * `OrganizeNoteUseCase` reads candidates through the one-shot reads on purpose, so that a
     * storage failure is reported as one rather than swallowed into an empty list. While this
     * threw, that read blew up inside `viewModelScope.launch`, where a `SupervisorJob` sends the
     * exception to the default handler instead of failing the test — so Organize appeared to do
     * nothing at all and the screen state simply never changed. A double that throws where
     * production reads is not a neutral stub.
     */
    override suspend fun search(query: String?, filter: ClientFilter): AppResult<List<Client>> {
        val needle = query?.trim()?.lowercase()
        return AppResult.Success(
            rows.value
                .filter { filter != ClientFilter.ACTIVE || !it.isArchived }
                .filter { candidate ->
                    needle.isNullOrEmpty() ||
                        candidate.name.lowercase().contains(needle) ||
                        candidate.companyName?.lowercase()?.contains(needle) == true
                },
        )
    }

    override suspend fun create(client: Client): AppResult<Client> = unusedDouble()

    override suspend fun update(client: Client): AppResult<Client> = unusedDouble()

    override suspend fun setArchived(id: Long, archived: Boolean): AppResult<Client> = unusedDouble()

    override suspend fun softDelete(id: Long): AppResult<Unit> = unusedDouble()

    override suspend fun restore(id: Long): AppResult<Client> = unusedDouble()

    override suspend fun purge(id: Long): AppResult<Unit> = unusedDouble()

    fun seed(vararg clients: Client) {
        rows.value = clients.toList()
    }
}

class FakeReminderRepository : ReminderRepository {

    private val rows = MutableStateFlow<List<Reminder>>(emptyList())
    private var nextId = 1L

    override fun observeUpcoming(): Flow<List<Reminder>> =
        rows.map { list -> list.filter { it.state == ReminderState.SCHEDULED }.sortedBy { it.triggerAt } }

    override fun observeByKind(kind: ReminderKind): Flow<List<Reminder>> =
        rows.map { list ->
            list.filter { it.state == ReminderState.SCHEDULED && it.kind == kind }
                .sortedBy { it.armedAt }
        }

    override suspend fun byId(id: Long): AppResult<Reminder> =
        rows.value.firstOrNull { it.id == id }
            ?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No reminder with id $id")

    override suspend fun update(reminder: Reminder): AppResult<Reminder> {
        if (rows.value.none { it.id == reminder.id }) {
            return failure(MiraErrorCode.NOT_FOUND, "No reminder with id ${reminder.id}")
        }
        rows.value = rows.value.map { if (it.id == reminder.id) reminder else it }
        return AppResult.Success(reminder)
    }

    override suspend fun create(reminder: Reminder): AppResult<Reminder> {
        val saved = reminder.copy(id = nextId++, state = ReminderState.SCHEDULED)
        rows.value = rows.value + saved
        return AppResult.Success(saved)
    }

    override suspend fun markFired(id: Long): AppResult<Unit> = setState(id, ReminderState.FIRED)

    override suspend fun cancel(id: Long): AppResult<Unit> = setState(id, ReminderState.CANCELLED)

    override suspend fun nextScheduled(): AppResult<Reminder?> = AppResult.Success(
        rows.value.filter { it.state == ReminderState.SCHEDULED }.minByOrNull { it.triggerAt },
    )

    override suspend fun rescheduleAll(): AppResult<Int> =
        AppResult.Success(rows.value.count { it.state == ReminderState.SCHEDULED })

    fun seed(vararg reminders: Reminder) {
        rows.value = reminders.map { reminder ->
            if (reminder.id == 0L) reminder.copy(id = nextId++) else reminder.also { nextId = maxOf(nextId, it.id + 1) }
        }
    }

    fun snapshot(): List<Reminder> = rows.value

    private fun setState(id: Long, state: ReminderState): AppResult<Unit> {
        if (rows.value.none { it.id == id }) {
            return failure(MiraErrorCode.NOT_FOUND, "No reminder with id $id")
        }
        rows.value = rows.value.map { if (it.id == id) it.copy(state = state) else it }
        return AppResult.Success(Unit)
    }
    override suspend fun listOfKinds(
        kinds: Collection<ReminderKind>,
    ): AppResult<List<Reminder>> = AppResult.Success(rows.value.filter { it.kind in kinds })

}

/**
 * The places reminders wait at.
 *
 * [registered] is settable so a test can put the repository in the state a reboot leaves behind —
 * rows Mira still holds with nothing watching for them — which is the whole of what the Reminders
 * tab has to notice.
 */
class FakeReminderPlaceRepository : ReminderPlaceRepository {

    private val rows = MutableStateFlow<List<ReminderPlace>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<ReminderPlace>> = rows

    override suspend fun byReminderId(reminderId: Long): AppResult<ReminderPlace?> =
        AppResult.Success(rows.value.firstOrNull { it.reminderId == reminderId })

    override suspend fun save(place: ReminderPlace): AppResult<ReminderPlace> {
        val existing = rows.value.firstOrNull { it.reminderId == place.reminderId }
        val saved = place.copy(id = existing?.id ?: nextId++)
        rows.value = rows.value.filterNot { it.reminderId == place.reminderId } + saved
        return AppResult.Success(saved)
    }

    override suspend fun setRegistered(id: Long, isRegistered: Boolean): AppResult<Unit> {
        rows.value = rows.value.map { if (it.id == id) it.copy(isRegistered = isRegistered) else it }
        return AppResult.Success(Unit)
    }

    override suspend fun delete(id: Long): AppResult<Unit> {
        rows.value = rows.value.filterNot { it.id == id }
        return AppResult.Success(Unit)
    }

    override suspend fun pending(): AppResult<List<ReminderPlace>> = AppResult.Success(rows.value)

    override suspend fun countRegisteredOtherThan(placeId: Long): AppResult<Int> =
        AppResult.Success(rows.value.count { it.id != placeId && it.isRegistered })

    fun seed(vararg places: ReminderPlace) {
        rows.value = places.map { place ->
            if (place.id == 0L) place.copy(id = nextId++) else place.also { nextId = maxOf(nextId, it.id + 1) }
        }
    }

    fun snapshot(): List<ReminderPlace> = rows.value
}

/**
 * The platform's proximity alerts.
 *
 * It writes `isRegistered` back through [places], as the real scheduler does, because that flag is
 * the only thing telling the Reminders tab whether anything is actually listening — a fake that
 * left it alone would make the screen look broken when it is working.
 *
 * [refuseWith] is what makes the refusal path testable: the tab has to say a different sentence
 * for each reason, and only a scheduler that can be told to refuse proves it.
 */
class FakePlaceReminderScheduler(
    private val places: FakeReminderPlaceRepository,
    override val capacity: Int = 100,
) : PlaceReminderScheduler {

    var refuseWith: PlaceWatchRefusal? = null

    val watched = mutableListOf<Long>()
    val stopped = mutableListOf<Long>()

    override suspend fun watch(place: ReminderPlace): AppResult<PlaceWatchResult> {
        val reason = refuseWith
        if (reason != null) {
            places.setRegistered(place.id, false)
            return AppResult.Success(PlaceWatchResult.Refused(reason))
        }
        watched += place.id
        places.setRegistered(place.id, true)
        return AppResult.Success(PlaceWatchResult.Watching)
    }

    override suspend fun stopWatching(placeId: Long): AppResult<Unit> {
        stopped += placeId
        places.setRegistered(placeId, false)
        return AppResult.Success(Unit)
    }

    override suspend fun rewatchAll(): AppResult<Unit> = AppResult.Success(Unit)
}

/** Records what was armed and disarmed, so a cancel can be proved to reach the OS alarm. */
class FakeReminderScheduler : ReminderScheduler {

    val scheduled = mutableListOf<Long>()
    val cancelled = mutableListOf<Long>()

    override fun canScheduleExact(): Boolean = true

    override suspend fun schedule(reminder: Reminder): AppResult<Unit> {
        scheduled += reminder.id
        return AppResult.Success(Unit)
    }

    override suspend fun cancel(reminderId: Long): AppResult<Unit> {
        cancelled += reminderId
        return AppResult.Success(Unit)
    }
}

class FakeSettingsRepository(initial: MiraSettings = MiraSettings()) : SettingsRepository {

    private val state = MutableStateFlow(initial)

    override val settings: Flow<MiraSettings> = state

    override suspend fun current(): MiraSettings = state.value

    override suspend fun setLanguageMode(mode: LanguageMode) {
        state.value = state.value.copy(languageMode = mode)
    }

    override suspend fun setActivationSound(enabled: Boolean) {
        state.value = state.value.copy(activationSound = enabled)
    }

    override suspend fun setHapticFeedback(enabled: Boolean) {
        state.value = state.value.copy(hapticFeedback = enabled)
    }

    override suspend fun setOnboardingComplete(complete: Boolean) {
        state.value = state.value.copy(onboardingComplete = complete)
    }
}

/** The brief each Home test wants, or the failure it wants instead. */
class FakeBriefRepository(var brief: DailyBrief) : BriefRepository {

    var failWith: MiraErrorCode? = null

    override suspend fun todayBrief(): AppResult<DailyBrief> =
        failWith?.let { failure(it, "Injected by FakeBriefRepository") } ?: AppResult.Success(brief)
}

/** A task with only the fields a screen test cares about spelled out. */
fun testTask(
    id: Long = 0L,
    title: String,
    description: String? = null,
    dueAt: Instant? = null,
    priority: TaskPriority = TaskPriority.NORMAL,
    isDone: Boolean = false,
    completedAt: Instant? = null,
    archivedAt: Instant? = null,
    projectId: Long? = null,
    clientId: Long? = null,
    tags: List<Tag> = emptyList(),
    subtasks: List<Subtask> = emptyList(),
    createdAt: Instant,
): Task = Task(
    id = id,
    title = title,
    description = description,
    dueAt = dueAt,
    priority = priority,
    isDone = isDone,
    completedAt = completedAt,
    archivedAt = archivedAt,
    projectId = projectId,
    clientId = clientId,
    tags = tags,
    subtasks = subtasks,
    createdAt = createdAt,
    updatedAt = createdAt,
)

/** A step on a task, for the rows that show "1 of 3 steps". */
fun testSubtask(id: Long, taskId: Long, title: String, isDone: Boolean = false, position: Int = 0): Subtask =
    Subtask(
        id = id,
        taskId = taskId,
        title = title,
        isDone = isDone,
        position = position,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

/**
 * Fails loudly rather than returning something plausible.
 *
 * A double that quietly answers a call the screen was never meant to make is how a test ends up
 * proving the fake rather than the code.
 */
private fun unusedDouble(): Nothing = error("The Tasks screens do not use this")
