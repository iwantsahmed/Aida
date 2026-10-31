package com.rayluxeo.aida.data.repository

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.data.TestDispatcherProvider
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.db.entity.SubtaskEntity
import com.rayluxeo.aida.data.db.entity.TaskEntity
import com.rayluxeo.aida.data.work.failureCode
import com.rayluxeo.aida.data.work.openTestDatabase
import com.rayluxeo.aida.data.work.successValue
import com.rayluxeo.aida.domain.model.Subtask
import com.rayluxeo.aida.domain.model.Tag
import com.rayluxeo.aida.domain.model.Task
import com.rayluxeo.aida.domain.model.TaskFilter
import com.rayluxeo.aida.domain.model.TaskPriority
import com.rayluxeo.aida.domain.model.TaskQuery
import com.rayluxeo.aida.domain.model.TaskSort
import com.rayluxeo.aida.domain.task.RecurrenceRule
import com.rayluxeo.aida.domain.task.TextRecurrenceCodec
import java.time.DayOfWeek
import java.time.Instant
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
 * The repository against a real SQLite database, because the things worth proving here are the
 * SQL: that Today, Upcoming and Overdue cannot hold the same task, that a binned task is gone
 * from every list but still reachable by id, and that a task comes back with its tags and steps
 * on it.
 *
 * No Hilt — its local-test bytecode transform does not run under AGP 9 (google/dagger#5261) — so
 * the database and the repository are built by hand and the clock is fixed at a known Thursday.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TaskRepositoryImplTest {

    private lateinit var database: MiraDatabase
    private lateinit var repository: TaskRepositoryImpl

    private val clock = FakeMiraClock(TaskTestData.now(hour = 14))
    private val today = TaskTestData.TODAY

    /**
     * One scheduler, shared by the repository's dispatcher and by every `runTest` below. Building
     * the dispatcher inline gave it a scheduler of its own, and any flow that crossed the two
     * threw "Detected use of different schedulers" — which `observeCounts` does, because it
     * combines several.
     */
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun openDatabase() {
        database = openTestDatabase()
        repository = TaskRepositoryImpl(
            dao = database.taskDao(),
            recurrence = TextRecurrenceCodec(),
            clock = clock,
            dispatchers = TestDispatcherProvider(dispatcher),
        )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    // ---- The views ----------------------------------------------------------------------------

    @Test
    fun `today upcoming and overdue never hold the same task`() = runTest(dispatcher) {
        insert(taskEntity("Due today", dueAt = TaskTestData.at(today, 10)))
        insert(taskEntity("Due yesterday", dueAt = TaskTestData.at(today.minusDays(1), 9)))
        insert(taskEntity("Due tomorrow", dueAt = TaskTestData.at(today.plusDays(1), 9)))
        insert(taskEntity("No date"))

        assertThat(titles(TaskFilter.DUE_TODAY)).containsExactly("Due today")
        assertThat(titles(TaskFilter.OVERDUE)).containsExactly("Due yesterday")
        assertThat(titles(TaskFilter.UPCOMING)).containsExactly("Due tomorrow")
        assertThat(titles(TaskFilter.NO_DATE)).containsExactly("No date")
    }

    @Test
    fun `the first minute of tomorrow is upcoming and not due today`() = runTest(dispatcher) {
        insert(taskEntity("Midnight", dueAt = TaskTestData.endOfDay()))
        insert(taskEntity("First minute of today", dueAt = TaskTestData.startOfDay()))

        assertThat(titles(TaskFilter.DUE_TODAY)).containsExactly("First minute of today")
        assertThat(titles(TaskFilter.UPCOMING)).containsExactly("Midnight")
    }

    @Test
    fun `completed tasks have their own view and leave the open one`() = runTest(dispatcher) {
        val id = insert(taskEntity("Pay rent"))

        repository.setDone(id, done = true).successValue()

        assertThat(titles(TaskFilter.OPEN)).isEmpty()
        assertThat(titles(TaskFilter.DONE)).containsExactly("Pay rent")
    }

    @Test
    fun `an archived task leaves every live list and appears in the archive`() = runTest(dispatcher) {
        val id = insert(taskEntity("Old brief", dueAt = TaskTestData.at(today, 10)))

        repository.archive(id).successValue()

        assertThat(titles(TaskFilter.ALL)).isEmpty()
        assertThat(titles(TaskFilter.OPEN)).isEmpty()
        assertThat(titles(TaskFilter.DUE_TODAY)).isEmpty()
        assertThat(titles(TaskFilter.ARCHIVED)).containsExactly("Old brief")
    }

    @Test
    fun `unarchiving puts the task back where it was`() = runTest(dispatcher) {
        val id = insert(taskEntity("Old brief", dueAt = TaskTestData.at(today, 10)))
        repository.archive(id).successValue()

        val back = repository.unarchive(id).successValue()

        assertThat(back.archivedAt).isNull()
        assertThat(titles(TaskFilter.DUE_TODAY)).containsExactly("Old brief")
        assertThat(titles(TaskFilter.ARCHIVED)).isEmpty()
    }

    @Test
    fun `a binned task is gone from every list and every count`() = runTest(dispatcher) {
        val id = insert(taskEntity("Renew the lease", dueAt = TaskTestData.at(today, 9)))

        repository.delete(id).successValue()

        for (filter in TaskFilter.entries) {
            assertThat(titles(filter)).isEmpty()
            assertThat(repository.count(TaskQuery(filter = filter)).successValue()).isEqualTo(0)
        }
    }

    @Test
    fun `a binned task is still reachable by id so undo can find it`() = runTest(dispatcher) {
        val id = insert(taskEntity("Renew the lease"))
        repository.delete(id).successValue()

        val binned = repository.byId(id).successValue()

        assertThat(binned.id).isEqualTo(id)
        assertThat(binned.deletedAt).isNotNull()
    }

    @Test
    fun `restore brings the task back under the same id`() = runTest(dispatcher) {
        val due = TaskTestData.at(today, 9)
        val id = insert(taskEntity("Renew the lease", dueAt = due))
        repository.delete(id).successValue()

        val back = repository.restore(id).successValue()

        assertThat(back.id).isEqualTo(id)
        assertThat(back.deletedAt).isNull()
        assertThat(back.dueAt).isEqualTo(Instant.ofEpochMilli(due))
        assertThat(titles(TaskFilter.DUE_TODAY)).containsExactly("Renew the lease")
    }

    @Test
    fun `restoring a task that is not in the bin is refused`() = runTest(dispatcher) {
        val id = insert(taskEntity("Renew the lease"))

        assertThat(repository.restore(id).failureCode()).isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    @Test
    fun `a count and its list always agree`() = runTest(dispatcher) {
        insert(taskEntity("Due today", dueAt = TaskTestData.at(today, 10)))
        insert(taskEntity("Due yesterday", dueAt = TaskTestData.at(today.minusDays(1), 9)))
        insert(taskEntity("No date"))
        val archived = insert(taskEntity("Filed away"))
        repository.archive(archived).successValue()

        for (filter in TaskFilter.entries) {
            val query = TaskQuery(filter = filter)
            assertThat(repository.count(query).successValue())
                .isEqualTo(repository.list(query).successValue().size)
        }
    }

    // ---- Whole tasks --------------------------------------------------------------------------

    @Test
    fun `a task created with tags steps and a file comes back carrying all three`() = runTest(dispatcher) {
        val tagId = database.taskDao().ensureTag("urgent", 0L)
        val fileId = database.insertFile("contract.pdf")

        val created = repository.create(
            newTask(
                title = "Finish the homepage",
                tags = listOf(Tag(id = tagId, name = "urgent")),
                subtasks = listOf(step("Hero"), step("Footer")),
                attachmentIds = listOf(fileId),
            )
        ).successValue()

        assertThat(created.tags.map { it.name }).containsExactly("urgent")
        assertThat(created.subtasks.map { it.title }).containsExactly("Hero", "Footer").inOrder()
        assertThat(created.attachmentIds).containsExactly(fileId)
    }

    @Test
    fun `the new columns survive a round trip`() = runTest(dispatcher) {
        val clientId = database.insertClient("Rayluxeo")
        val projectId = database.insertProject("Website", clientId = clientId)

        val created = repository.create(
            newTask(
                title = "Ship the site",
                description = "the full redesign",
                notes = "call first",
                dueAt = Instant.ofEpochMilli(TaskTestData.at(today.plusDays(1), 0)),
                dueAllDay = true,
                estimatedMinutes = 90,
                priority = TaskPriority.HIGH,
                projectId = projectId,
                clientId = clientId,
                recurrence = RecurrenceRule.Weekly(days = setOf(DayOfWeek.MONDAY)),
            )
        ).successValue()

        val reloaded = repository.byId(created.id).successValue()
        assertThat(reloaded.description).isEqualTo("the full redesign")
        assertThat(reloaded.notes).isEqualTo("call first")
        assertThat(reloaded.dueAllDay).isTrue()
        assertThat(reloaded.estimatedMinutes).isEqualTo(90)
        assertThat(reloaded.priority).isEqualTo(TaskPriority.HIGH)
        assertThat(reloaded.projectId).isEqualTo(projectId)
        assertThat(reloaded.clientId).isEqualTo(clientId)
        assertThat(reloaded.recurrence)
            .isEqualTo(RecurrenceRule.Weekly(days = setOf(DayOfWeek.MONDAY)))
    }

    @Test
    fun `a list carries every task's tags without a query per row`() = runTest(dispatcher) {
        val urgent = database.taskDao().ensureTag("urgent", 0L)
        val billing = database.taskDao().ensureTag("billing", 0L)
        val first = insert(taskEntity("Call Ahmed"))
        val second = insert(taskEntity("Send the invoice"))
        repository.setTags(first, listOf(urgent)).successValue()
        repository.setTags(second, listOf(urgent, billing)).successValue()

        val listed = repository.list(TaskQuery(filter = TaskFilter.OPEN, sort = TaskSort.TITLE))
            .successValue()

        assertThat(listed.first { it.id == first }.tags.map { it.name }).containsExactly("urgent")
        assertThat(listed.first { it.id == second }.tags.map { it.name })
            .containsExactly("billing", "urgent").inOrder()
    }

    // ---- Completing ---------------------------------------------------------------------------

    @Test
    fun `completing a task does not tick its steps`() = runTest(dispatcher) {
        val id = insert(taskEntity("Launch"))
        database.subtaskDao().append(subtaskRow(id, "Write the copy"))

        val done = repository.setDone(id, done = true).successValue()

        assertThat(done.isDone).isTrue()
        assertThat(done.subtasks.single().isDone).isFalse()
    }

    @Test
    fun `reopening a task clears the completion time`() = runTest(dispatcher) {
        val id = insert(taskEntity("Launch"))
        repository.setDone(id, done = true).successValue()

        val reopened = repository.setDone(id, done = false).successValue()

        assertThat(reopened.isDone).isFalse()
        assertThat(reopened.completedAt).isNull()
    }

    @Test
    fun `a binned task cannot be completed`() = runTest(dispatcher) {
        val id = insert(taskEntity("Renew the lease"))
        repository.delete(id).successValue()

        assertThat(repository.setDone(id, done = true).failureCode())
            .isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    // ---- Snooze -------------------------------------------------------------------------------

    @Test
    fun `snoozing moves the due moment and stops the task being all day`() = runTest(dispatcher) {
        val id = insert(taskEntity("Send the proposal", dueAt = TaskTestData.at(today, 0), dueAllDay = true))
        val tomorrowMorning = Instant.ofEpochMilli(TaskTestData.at(today.plusDays(1), 9))

        val snoozed = repository.snooze(id, tomorrowMorning).successValue()

        assertThat(snoozed.dueAt).isEqualTo(tomorrowMorning)
        assertThat(snoozed.dueAllDay).isFalse()
        assertThat(titles(TaskFilter.UPCOMING)).containsExactly("Send the proposal")
    }

    // ---- Bulk ---------------------------------------------------------------------------------

    @Test
    fun `a bulk complete ticks only the tasks it named`() = runTest(dispatcher) {
        val first = insert(taskEntity("One"))
        val second = insert(taskEntity("Two"))
        insert(taskEntity("Three"))

        assertThat(repository.setDoneAll(listOf(first, second), done = true).successValue())
            .isEqualTo(2)
        assertThat(titles(TaskFilter.OPEN)).containsExactly("Three")
    }

    @Test
    fun `bulk operations on nothing do nothing`() = runTest(dispatcher) {
        assertThat(repository.setDoneAll(emptyList(), done = true).successValue()).isEqualTo(0)
        assertThat(repository.archiveAll(emptyList()).successValue()).isEqualTo(0)
        assertThat(repository.deleteAll(emptyList()).successValue()).isEqualTo(0)
    }

    @Test
    fun `a bulk delete bins every task it named`() = runTest(dispatcher) {
        val first = insert(taskEntity("One"))
        val second = insert(taskEntity("Two"))

        assertThat(repository.deleteAll(listOf(first, second)).successValue()).isEqualTo(2)
        assertThat(titles(TaskFilter.ALL)).isEmpty()
    }

    @Test
    fun `a bulk archive counts only what it actually changed`() = runTest(dispatcher) {
        val first = insert(taskEntity("One"))
        repository.archive(first).successValue()
        val second = insert(taskEntity("Two"))

        assertThat(repository.archiveAll(listOf(first, second)).successValue()).isEqualTo(1)
    }

    // ---- Search and filters -------------------------------------------------------------------

    @Test
    fun `search reaches the description as well as the title and the notes`() = runTest(dispatcher) {
        insert(taskEntity("Ship it", description = "the homepage redesign", notes = "ask Ahmed"))
        insert(taskEntity("Buy groceries"))

        assertThat(searchTitles("redesign")).containsExactly("Ship it")
        assertThat(searchTitles("Ahmed")).containsExactly("Ship it")
        assertThat(searchTitles("ship")).containsExactly("Ship it")
    }

    @Test
    fun `search never finds a binned task`() = runTest(dispatcher) {
        val id = insert(taskEntity("Call Ahmed about the rent"))
        assertThat(searchTitles("Ahmed")).hasSize(1)

        repository.delete(id).successValue()

        assertThat(searchTitles("Ahmed")).isEmpty()
    }

    @Test
    fun `search follows a task through an edit`() = runTest(dispatcher) {
        val id = insert(taskEntity("Draft the letter"))
        val stored = repository.byId(id).successValue()

        repository.update(stored.copy(title = "Draft the invoice")).successValue()

        assertThat(searchTitles("invoice")).containsExactly("Draft the invoice")
        assertThat(searchTitles("letter")).isEmpty()
    }

    @Test
    fun `an edit leaves the tags a task already had alone`() = runTest(dispatcher) {
        val tagId = database.taskDao().ensureTag("urgent", 0L)
        val id = insert(taskEntity("Call Ahmed"))
        val stale = repository.byId(id).successValue()
        repository.addTag(id, tagId).successValue()

        val edited = repository.update(stale.copy(title = "Call Ahmed back")).successValue()

        assertThat(edited.title).isEqualTo("Call Ahmed back")
        assertThat(edited.tags.map { it.name }).containsExactly("urgent")
    }

    @Test
    fun `a blank search returns the filter whole`() = runTest(dispatcher) {
        insert(taskEntity("One"))
        insert(taskEntity("Two"))

        assertThat(repository.search("   ", TaskFilter.OPEN).successValue()).hasSize(2)
        assertThat(repository.search(null, TaskFilter.OPEN).successValue()).hasSize(2)
    }

    @Test
    fun `filtering by project and by client narrows the same list`() = runTest(dispatcher) {
        val client = database.insertClient("Rayluxeo")
        val other = database.insertClient("Someone else")
        val project = database.insertProject("Website", clientId = client)
        insert(taskEntity("Homepage", projectId = project, clientId = client))
        insert(taskEntity("Invoice", clientId = client))
        insert(taskEntity("Unrelated", clientId = other))

        assertThat(
            repository.list(TaskQuery(filter = TaskFilter.OPEN, projectId = project))
                .successValue().map { it.title },
        ).containsExactly("Homepage")
        assertThat(
            repository.list(TaskQuery(filter = TaskFilter.OPEN, clientId = client))
                .successValue().map { it.title },
        ).containsExactly("Homepage", "Invoice")
    }

    @Test
    fun `two tag chips narrow the list rather than widening it`() = runTest(dispatcher) {
        val urgent = database.taskDao().ensureTag("urgent", 0L)
        val billing = database.taskDao().ensureTag("billing", 0L)
        val both = insert(taskEntity("Chase the invoice"))
        val one = insert(taskEntity("Call Ahmed"))
        repository.setTags(both, listOf(urgent, billing)).successValue()
        repository.setTags(one, listOf(urgent)).successValue()

        val narrowed = repository.list(
            TaskQuery(filter = TaskFilter.OPEN, tagIds = setOf(urgent, billing)),
        ).successValue()

        assertThat(narrowed.map { it.title }).containsExactly("Chase the invoice")
    }

    @Test
    fun `filtering by priority keeps only the priorities asked for`() = runTest(dispatcher) {
        insert(taskEntity("Urgent", priority = TaskPriority.HIGH))
        insert(taskEntity("Normal", priority = TaskPriority.NORMAL))
        insert(taskEntity("Someday", priority = TaskPriority.LOW))

        val listed = repository.list(
            TaskQuery(filter = TaskFilter.OPEN, priorities = setOf(TaskPriority.HIGH, TaskPriority.LOW)),
        ).successValue()

        assertThat(listed.map { it.title }).containsExactly("Urgent", "Someday")
    }

    // ---- Sorting ------------------------------------------------------------------------------

    @Test
    fun `sorting by priority puts the urgent work first`() = runTest(dispatcher) {
        insert(taskEntity("Someday", priority = TaskPriority.LOW))
        insert(taskEntity("Urgent", priority = TaskPriority.HIGH))
        insert(taskEntity("Normal", priority = TaskPriority.NORMAL))

        val listed = repository.list(
            TaskQuery(filter = TaskFilter.OPEN, sort = TaskSort.PRIORITY),
        ).successValue()

        assertThat(listed.map { it.title }).containsExactly("Urgent", "Normal", "Someday").inOrder()
    }

    @Test
    fun `sorting by title ignores case`() = runTest(dispatcher) {
        insert(taskEntity("banana"))
        insert(taskEntity("Apple"))

        val listed = repository.list(
            TaskQuery(filter = TaskFilter.OPEN, sort = TaskSort.TITLE),
        ).successValue()

        assertThat(listed.map { it.title }).containsExactly("Apple", "banana").inOrder()
    }

    @Test
    fun `reversing the due date keeps undated tasks at the bottom`() = runTest(dispatcher) {
        insert(taskEntity("No date"))
        insert(taskEntity("Soon", dueAt = TaskTestData.at(today.plusDays(1), 9)))
        insert(taskEntity("Later", dueAt = TaskTestData.at(today.plusDays(5), 9)))

        val listed = repository.list(
            TaskQuery(filter = TaskFilter.OPEN, sort = TaskSort.DUE_DATE, ascending = false),
        ).successValue()

        assertThat(listed.map { it.title }).containsExactly("Later", "Soon", "No date").inOrder()
    }

    @Test
    fun `finished work sinks below outstanding work`() = runTest(dispatcher) {
        val done = insert(taskEntity("Finished", dueAt = TaskTestData.at(today.minusDays(2), 9)))
        insert(taskEntity("Outstanding", dueAt = TaskTestData.at(today.plusDays(2), 9)))
        repository.setDone(done, done = true).successValue()

        val listed = repository.list(TaskQuery(filter = TaskFilter.ALL)).successValue()

        assertThat(listed.map { it.title }).containsExactly("Outstanding", "Finished").inOrder()
    }

    // ---- Grouped views ------------------------------------------------------------------------

    @Test
    fun `by project resolves the headings and puts the unassigned last`() = runTest(dispatcher) {
        val website = database.insertProject("Website")
        val brand = database.insertProject("Brand")
        insert(taskEntity("Homepage", projectId = website))
        insert(taskEntity("Logo", projectId = brand))
        insert(taskEntity("Odd job"))

        val groups = repository.groupedByProject(TaskQuery(filter = TaskFilter.OPEN)).successValue()

        assertThat(groups.map { it.name }).containsExactly("Brand", "Website", null).inOrder()
        assertThat(groups.last().id).isNull()
        assertThat(groups.last().tasks.map { it.title }).containsExactly("Odd job")
    }

    @Test
    fun `by client resolves the headings`() = runTest(dispatcher) {
        val rayluxeo = database.insertClient("Rayluxeo")
        insert(taskEntity("Invoice", clientId = rayluxeo))

        val groups = repository.groupedByClient(TaskQuery(filter = TaskFilter.OPEN)).successValue()

        assertThat(groups.single().id).isEqualTo(rayluxeo)
        assertThat(groups.single().name).isEqualTo("Rayluxeo")
    }

    @Test
    fun `a grouped view of nothing is empty rather than one empty group`() = runTest(dispatcher) {
        assertThat(repository.groupedByProject(TaskQuery(filter = TaskFilter.OPEN)).successValue())
            .isEmpty()
    }

    // ---- Tags on a task -----------------------------------------------------------------------

    @Test
    fun `tagging a task twice leaves it with one tag`() = runTest(dispatcher) {
        val tagId = database.taskDao().ensureTag("urgent", 0L)
        val id = insert(taskEntity("Call Ahmed"))

        repository.addTag(id, tagId).successValue()
        val twice = repository.addTag(id, tagId).successValue()

        assertThat(twice.tags.map { it.name }).containsExactly("urgent")
    }

    @Test
    fun `setting tags replaces the whole set`() = runTest(dispatcher) {
        val urgent = database.taskDao().ensureTag("urgent", 0L)
        val billing = database.taskDao().ensureTag("billing", 0L)
        val id = insert(taskEntity("Chase the invoice"))
        repository.setTags(id, listOf(urgent)).successValue()

        val replaced = repository.setTags(id, listOf(billing)).successValue()

        assertThat(replaced.tags.map { it.name }).containsExactly("billing")
    }

    @Test
    fun `removing a tag leaves the task and the tag alone`() = runTest(dispatcher) {
        val tagId = database.taskDao().ensureTag("urgent", 0L)
        val id = insert(taskEntity("Call Ahmed"))
        repository.addTag(id, tagId).successValue()

        val untagged = repository.removeTag(id, tagId).successValue()

        assertThat(untagged.tags).isEmpty()
        assertThat(untagged.title).isEqualTo("Call Ahmed")
        assertThat(database.taskDao().findTagById(tagId)).isNotNull()
    }

    // ---- Attachments --------------------------------------------------------------------------

    @Test
    fun `attachments keep the order they were linked in`() = runTest(dispatcher) {
        val id = insert(taskEntity("Send the contract"))
        val first = database.insertFile("contract.pdf")
        val second = database.insertFile("appendix.pdf")

        repository.attachFile(id, first).successValue()
        val task = repository.attachFile(id, second).successValue()

        assertThat(task.attachmentIds).containsExactly(first, second).inOrder()
    }

    @Test
    fun `linking the same file twice changes nothing`() = runTest(dispatcher) {
        val id = insert(taskEntity("Send the contract"))
        val fileId = database.insertFile("contract.pdf")
        repository.attachFile(id, fileId).successValue()

        val again = repository.attachFile(id, fileId).successValue()

        assertThat(again.attachmentIds).containsExactly(fileId)
    }

    @Test
    fun `unlinking a file closes the gap it left`() = runTest(dispatcher) {
        val id = insert(taskEntity("Send the contract"))
        val first = database.insertFile("a.pdf")
        val second = database.insertFile("b.pdf")
        val third = database.insertFile("c.pdf")
        listOf(first, second, third).forEach { repository.attachFile(id, it).successValue() }

        repository.detachFile(id, second).successValue()
        val fourth = database.insertFile("d.pdf")
        val task = repository.attachFile(id, fourth).successValue()

        assertThat(task.attachmentIds).containsExactly(first, third, fourth).inOrder()
    }

    @Test
    fun `unlinking a file that is not there is reported rather than ignored`() = runTest(dispatcher) {
        val id = insert(taskEntity("Send the contract"))
        val fileId = database.insertFile("contract.pdf")

        assertThat(repository.detachFile(id, fileId).failureCode()).isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    // ---- Recurrence ---------------------------------------------------------------------------

    @Test
    fun `rolling a repeating task over completes it and opens the next occurrence`() = runTest(dispatcher) {
        val id = insert(
            taskEntity(
                title = "Review Rayluxeo",
                dueAt = TaskTestData.at(today, 9),
                recurrenceRule = TextRecurrenceCodec().encode(RecurrenceRule.Weekly()),
            )
        )
        val nextMonday = Instant.ofEpochMilli(TaskTestData.at(today.plusDays(4), 9))

        val occurrence = repository.completeWithNextOccurrence(id, nextMonday).successValue()

        assertThat(occurrence.completed.id).isEqualTo(id)
        assertThat(occurrence.completed.isDone).isTrue()
        assertThat(occurrence.next.id).isNotEqualTo(id)
        assertThat(occurrence.next.isDone).isFalse()
        assertThat(occurrence.next.dueAt).isEqualTo(nextMonday)
        assertThat(occurrence.next.recurrence).isEqualTo(RecurrenceRule.Weekly())
    }

    @Test
    fun `a roll over ties both occurrences to the same series`() = runTest(dispatcher) {
        val id = insert(
            taskEntity(
                title = "Review Rayluxeo",
                dueAt = TaskTestData.at(today, 9),
                recurrenceRule = TextRecurrenceCodec().encode(RecurrenceRule.Weekly()),
            )
        )

        val occurrence = repository.completeWithNextOccurrence(
            id,
            Instant.ofEpochMilli(TaskTestData.at(today.plusDays(7), 9)),
        ).successValue()

        assertThat(occurrence.completed.seriesUuid).isNotNull()
        assertThat(occurrence.next.seriesUuid).isEqualTo(occurrence.completed.seriesUuid)
    }

    @Test
    fun `a roll over carries the tags and reopens the steps`() = runTest(dispatcher) {
        val tagId = database.taskDao().ensureTag("weekly", 0L)
        val id = insert(
            taskEntity(
                title = "Review Rayluxeo",
                dueAt = TaskTestData.at(today, 9),
                recurrenceRule = TextRecurrenceCodec().encode(RecurrenceRule.Weekly()),
            )
        )
        repository.addTag(id, tagId).successValue()
        val step = database.subtaskDao().append(subtaskRow(id, "Read the numbers"))
        database.subtaskDao().setDone(step, done = true, completedAt = 1L, updatedAt = 1L)

        val occurrence = repository.completeWithNextOccurrence(
            id,
            Instant.ofEpochMilli(TaskTestData.at(today.plusDays(7), 9)),
        ).successValue()

        assertThat(occurrence.next.tags.map { it.name }).containsExactly("weekly")
        assertThat(occurrence.next.subtasks.map { it.title }).containsExactly("Read the numbers")
        assertThat(occurrence.next.subtasks.single().isDone).isFalse()
        assertThat(occurrence.completed.subtasks.single().isDone).isTrue()
    }

    @Test
    fun `rolling over a task that is not there is reported`() = runTest(dispatcher) {
        assertThat(
            repository.completeWithNextOccurrence(404L, Instant.ofEpochMilli(1L)).failureCode(),
        ).isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    // ---- Observation --------------------------------------------------------------------------

    @Test
    fun `an observed list carries the tags a task has now`() = runTest(dispatcher) {
        val tagId = database.taskDao().ensureTag("urgent", 0L)
        val id = insert(taskEntity("Call Ahmed"))
        assertThat(repository.observe(TaskFilter.OPEN).first().single().tags).isEmpty()

        repository.addTag(id, tagId).successValue()

        assertThat(repository.observe(TaskFilter.OPEN).first().single().tags.map { it.name })
            .containsExactly("urgent")
    }

    @Test
    fun `the counts separate open due today overdue and completed today`() = runTest(dispatcher) {
        insert(taskEntity("Due today", dueAt = TaskTestData.at(today, 10)))
        insert(taskEntity("Due yesterday", dueAt = TaskTestData.at(today.minusDays(1), 9)))
        insert(taskEntity("No date"))
        val done = insert(taskEntity("Paid the rent"))
        repository.setDone(done, done = true).successValue()

        val counts = repository.observeCounts().first()

        assertThat(counts.open).isEqualTo(3)
        assertThat(counts.dueToday).isEqualTo(1)
        assertThat(counts.overdue).isEqualTo(1)
        assertThat(counts.completedToday).isEqualTo(1)
    }

    // ---- Helpers ------------------------------------------------------------------------------

    private suspend fun insert(entity: TaskEntity): Long =
        database.taskDao().insert(entity)

    private suspend fun titles(filter: TaskFilter): List<String> =
        repository.list(TaskQuery(filter = filter)).successValue().map { it.title }

    private suspend fun searchTitles(text: String): List<String> =
        repository.search(text, TaskFilter.OPEN).successValue().map { it.title }

    private fun newTask(
        title: String,
        description: String? = null,
        notes: String? = null,
        dueAt: Instant? = null,
        dueAllDay: Boolean = false,
        estimatedMinutes: Int? = null,
        priority: TaskPriority = TaskPriority.NORMAL,
        projectId: Long? = null,
        clientId: Long? = null,
        recurrence: RecurrenceRule? = null,
        tags: List<Tag> = emptyList(),
        subtasks: List<Subtask> = emptyList(),
        attachmentIds: List<Long> = emptyList(),
    ): Task {
        val now = clock.now()
        return Task(
            title = title,
            description = description,
            notes = notes,
            dueAt = dueAt,
            dueAllDay = dueAllDay,
            estimatedMinutes = estimatedMinutes,
            priority = priority,
            projectId = projectId,
            clientId = clientId,
            recurrence = recurrence,
            tags = tags,
            subtasks = subtasks,
            attachmentIds = attachmentIds,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun step(title: String): Subtask = Subtask(
        taskId = 0L,
        title = title,
        isDone = false,
        completedAt = null,
        position = 0,
        createdAt = clock.now(),
        updatedAt = clock.now(),
    )

    private fun subtaskRow(taskId: Long, title: String): SubtaskEntity = SubtaskEntity(
        taskId = taskId,
        title = title,
        isDone = false,
        completedAtEpochMillis = null,
        position = 0,
        createdAtEpochMillis = clock.now().toEpochMilli(),
        updatedAtEpochMillis = clock.now().toEpochMilli(),
    )
}
