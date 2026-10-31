package com.rayluxeo.aida.data.db

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.data.db.dao.TaskDao
import com.rayluxeo.aida.data.db.entity.SubtaskEntity
import com.rayluxeo.aida.data.repository.TaskTestData
import com.rayluxeo.aida.data.repository.insertFile
import com.rayluxeo.aida.data.repository.taskEntity
import com.rayluxeo.aida.data.work.openTestDatabase
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The statements themselves: the guards on the writes, the transactions that span two tables and
 * the bulk reads the repository hydrates from. The shapes of the lists are proved one layer up,
 * against the query the repository actually builds.
 *
 * Hilt is deliberately absent: its local-test bytecode transform does not run under AGP 9
 * (google/dagger#5261), so the database is built by hand and the application is pinned to the
 * plain [Application]. Foreign keys are on, so a cascade that is asserted is a cascade that ran.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TaskDaoTest {

    private lateinit var database: MiraDatabase
    private lateinit var dao: TaskDao

    private val today: LocalDate = TaskTestData.TODAY
    private val startOfDay = TaskTestData.startOfDay()
    private val endOfDay = TaskTestData.endOfDay()

    @Before
    fun openDatabase() {
        database = openTestDatabase()
        dao = database.taskDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    // ---- Completion ---------------------------------------------------------------------------

    @Test
    fun `setDone marks the task complete and stamps the completion time`() = runTest {
        val id = dao.insert(taskEntity("Pay rent"))
        val completedAt = at(today, hour = 15)

        val changed = dao.setDone(id = id, done = true, completedAt = completedAt, updatedAt = completedAt)

        assertThat(changed).isEqualTo(1)
        val stored = dao.findById(id)
        assertThat(stored?.isDone).isTrue()
        assertThat(stored?.completedAtEpochMillis).isEqualTo(completedAt)
    }

    @Test
    fun `setDone reports no rows changed for a task that does not exist`() = runTest {
        assertThat(dao.setDone(id = 404L, done = true, completedAt = 1L, updatedAt = 1L)).isEqualTo(0)
    }

    @Test
    fun `a deleted task cannot be completed`() = runTest {
        val id = dao.insert(taskEntity("Renew the lease"))
        dao.softDelete(id, at(today, hour = 16))

        val changed = dao.setDone(id, done = true, completedAt = at(today, 17), updatedAt = at(today, 17))

        assertThat(changed).isEqualTo(0)
    }

    @Test
    fun `a bulk complete touches only the tasks it names and skips the binned ones`() = runTest {
        val first = dao.insert(taskEntity("One"))
        val second = dao.insert(taskEntity("Two"))
        val binned = dao.insert(taskEntity("Three"))
        dao.softDelete(binned, at(today, hour = 8))
        val stamp = at(today, hour = 15)

        val changed = dao.setDoneIn(
            ids = listOf(first, second, binned),
            done = true,
            completedAt = stamp,
            updatedAt = stamp,
        )

        assertThat(changed).isEqualTo(2)
        assertThat(dao.findById(binned)?.isDone).isFalse()
    }

    // ---- The bin and the archive --------------------------------------------------------------

    @Test
    fun `deleting and restoring are each refused the second time`() = runTest {
        val id = dao.insert(taskEntity("Renew the lease"))

        assertThat(dao.softDelete(id, at(today, hour = 16))).isEqualTo(1)
        assertThat(dao.softDelete(id, at(today, hour = 17))).isEqualTo(0)
        assertThat(dao.restore(id, at(today, hour = 18))).isEqualTo(1)
        assertThat(dao.restore(id, at(today, hour = 19))).isEqualTo(0)
    }

    /**
     * The one read that does not filter the bin. Undo has to be able to load the task it is about
     * to put back, and a reminder pointing at a binned task still has to resolve to something.
     */
    @Test
    fun `a deleted task is still reachable by id`() = runTest {
        val id = dao.insert(taskEntity("Renew the lease"))
        val binnedAt = at(today, hour = 16)

        dao.softDelete(id, binnedAt)

        val stored = dao.findById(id)
        assertThat(stored?.id).isEqualTo(id)
        assertThat(stored?.deletedAtEpochMillis).isEqualTo(binnedAt)
    }

    @Test
    fun `restore brings the task back under the same id with its due date`() = runTest {
        val due = at(today, hour = 9)
        val id = dao.insert(taskEntity("Renew the lease", dueAt = due))
        dao.softDelete(id, at(today, hour = 16))

        assertThat(dao.restore(id, at(today, hour = 17))).isEqualTo(1)

        val back = dao.findById(id)
        assertThat(back?.id).isEqualTo(id)
        assertThat(back?.deletedAtEpochMillis).isNull()
        assertThat(back?.dueAtEpochMillis).isEqualTo(due)
    }

    @Test
    fun `archiving and unarchiving are each refused the second time`() = runTest {
        val id = dao.insert(taskEntity("Old brief"))

        assertThat(dao.archive(id, at(today, hour = 16))).isEqualTo(1)
        assertThat(dao.archive(id, at(today, hour = 17))).isEqualTo(0)
        assertThat(dao.unarchive(id, at(today, hour = 18))).isEqualTo(1)
        assertThat(dao.unarchive(id, at(today, hour = 19))).isEqualTo(0)
    }

    @Test
    fun `a binned task cannot be archived`() = runTest {
        val id = dao.insert(taskEntity("Old brief"))
        dao.softDelete(id, at(today, hour = 8))

        assertThat(dao.archive(id, at(today, hour = 16))).isEqualTo(0)
    }

    // ---- Counts -------------------------------------------------------------------------------

    @Test
    fun `counts separate due today from overdue and completed today`() = runTest {
        dao.insert(taskEntity("Due today", dueAt = at(today, hour = 10)))
        dao.insert(taskEntity("Due yesterday", dueAt = at(today.minusDays(1), hour = 9)))
        dao.insert(taskEntity("No due date"))
        dao.insert(taskEntity("Done today", isDone = true, completedAt = at(today, hour = 8)))
        dao.insert(
            taskEntity("Done yesterday", isDone = true, completedAt = at(today.minusDays(1), hour = 20)),
        )

        assertThat(dao.countOpen()).isEqualTo(3)
        assertThat(dao.countDueBetween(startOfDay, endOfDay)).isEqualTo(1)
        assertThat(dao.countOverdue(startOfDay)).isEqualTo(1)
        assertThat(dao.countCompletedBetween(startOfDay, endOfDay)).isEqualTo(1)
    }

    @Test
    fun `a task due at the first minute of tomorrow is not due today`() = runTest {
        dao.insert(taskEntity("Just after midnight", dueAt = endOfDay))
        dao.insert(taskEntity("First minute of today", dueAt = startOfDay))

        assertThat(dao.countDueBetween(startOfDay, endOfDay)).isEqualTo(1)
    }

    @Test
    fun `a deleted task leaves every count`() = runTest {
        val due = dao.insert(taskEntity("Due today", dueAt = at(today, hour = 9)))
        val overdue = dao.insert(taskEntity("Overdue letter", dueAt = at(today.minusDays(1), hour = 9)))
        val done = dao.insert(taskEntity("Paid the rent", isDone = true, completedAt = at(today, hour = 8)))

        listOf(due, overdue, done).forEach { dao.softDelete(it, at(today, hour = 16)) }

        assertThat(dao.countOpen()).isEqualTo(0)
        assertThat(dao.countDueBetween(startOfDay, endOfDay)).isEqualTo(0)
        assertThat(dao.countOverdue(startOfDay)).isEqualTo(0)
        assertThat(dao.countCompletedBetween(startOfDay, endOfDay)).isEqualTo(0)
    }

    @Test
    fun `an archived task leaves every count`() = runTest {
        val id = dao.insert(taskEntity("Old brief", dueAt = at(today, hour = 9)))

        dao.archive(id, at(today, hour = 16))

        assertThat(dao.countOpen()).isEqualTo(0)
        assertThat(dao.countDueBetween(startOfDay, endOfDay)).isEqualTo(0)
    }

    // ---- Tags ---------------------------------------------------------------------------------

    @Test
    fun `a tag asked for twice is created once`() = runTest {
        val first = dao.ensureTag("urgent", at(today, hour = 9))
        val second = dao.ensureTag("urgent", at(today, hour = 10))

        assertThat(second).isEqualTo(first)
        assertThat(dao.observeTags().first()).hasSize(1)
    }

    @Test
    fun `attaching a tag touches the task it was put on`() = runTest {
        val id = dao.insert(taskEntity("Call Ahmed"))
        val tagId = dao.ensureTag("urgent", at(today, hour = 9))
        val tagged = at(today, hour = 11)

        dao.addTag(taskId = id, tagId = tagId, at = tagged)

        assertThat(dao.findById(id)?.updatedAtEpochMillis).isEqualTo(tagged)
        assertThat(dao.tagsForTasks(listOf(id)).map { it.name }).containsExactly("urgent")
    }

    @Test
    fun `the same tag on the same task twice leaves one link`() = runTest {
        val id = dao.insert(taskEntity("Call Ahmed"))
        val tagId = dao.ensureTag("urgent", at(today, hour = 9))

        dao.addTag(id, tagId, at(today, hour = 10))
        dao.addTag(id, tagId, at(today, hour = 11))

        assertThat(dao.tagIdsFor(id)).containsExactly(tagId)
    }

    @Test
    fun `replacing a task's tags leaves exactly the new set`() = runTest {
        val id = dao.insert(taskEntity("Chase the invoice"))
        val urgent = dao.ensureTag("urgent", at(today, hour = 9))
        val billing = dao.ensureTag("billing", at(today, hour = 9))
        dao.addTag(id, urgent, at(today, hour = 10))

        dao.replaceTags(id, listOf(billing), at(today, hour = 11))

        assertThat(dao.tagIdsFor(id)).containsExactly(billing)
    }

    @Test
    fun `deleting a tag takes its links with it`() = runTest {
        val id = dao.insert(taskEntity("Call Ahmed"))
        val tagId = dao.ensureTag("urgent", at(today, hour = 9))
        dao.addTag(id, tagId, at(today, hour = 10))

        dao.deleteTag(tagId)

        assertThat(dao.tagIdsFor(id)).isEmpty()
        assertThat(dao.findById(id)).isNotNull()
    }

    @Test
    fun `tags for several tasks come back in one read`() = runTest {
        val first = dao.insert(taskEntity("One"))
        val second = dao.insert(taskEntity("Two"))
        val urgent = dao.ensureTag("urgent", at(today, hour = 9))
        val billing = dao.ensureTag("billing", at(today, hour = 9))
        dao.addTag(first, urgent, at(today, hour = 10))
        dao.addTag(second, billing, at(today, hour = 10))

        val rows = dao.tagsForTasks(listOf(first, second))

        assertThat(rows.filter { it.taskId == first }.map { it.name }).containsExactly("urgent")
        assertThat(rows.filter { it.taskId == second }.map { it.name }).containsExactly("billing")
    }

    @Test
    fun `tag usage counts the outstanding work only`() = runTest {
        val tagId = dao.ensureTag("urgent", at(today, hour = 9))
        val open = dao.insert(taskEntity("Open"))
        val done = dao.insert(taskEntity("Done", isDone = true, completedAt = at(today, hour = 8)))
        listOf(open, done).forEach { dao.addTag(it, tagId, at(today, hour = 10)) }

        assertThat(dao.observeTagUsage().first().single().taskCount).isEqualTo(1)
    }

    // ---- Attachments --------------------------------------------------------------------------

    @Test
    fun `attachments are numbered from the end of the list`() = runTest {
        val id = dao.insert(taskEntity("Send the contract"))
        val first = database.insertFile("a.pdf")
        val second = database.insertFile("b.pdf")

        dao.appendAttachment(id, first, at(today, hour = 10))
        dao.appendAttachment(id, second, at(today, hour = 11))

        assertThat(dao.attachmentsForTasks(listOf(id)).map { it.fileId })
            .containsExactly(first, second).inOrder()
    }

    @Test
    fun `unlinking an attachment closes the gap it left`() = runTest {
        val id = dao.insert(taskEntity("Send the contract"))
        val first = database.insertFile("a.pdf")
        val second = database.insertFile("b.pdf")
        val third = database.insertFile("c.pdf")
        listOf(first, second, third).forEach { dao.appendAttachment(id, it, at(today, hour = 10)) }

        assertThat(dao.removeAttachment(id, second, at(today, hour = 11))).isEqualTo(1)
        val fourth = database.insertFile("d.pdf")
        dao.appendAttachment(id, fourth, at(today, hour = 12))

        assertThat(dao.attachmentsForTasks(listOf(id)).map { it.fileId })
            .containsExactly(first, third, fourth).inOrder()
    }

    @Test
    fun `unlinking a file that is not attached changes nothing`() = runTest {
        val id = dao.insert(taskEntity("Send the contract"))
        val fileId = database.insertFile("a.pdf")

        assertThat(dao.removeAttachment(id, fileId, at(today, hour = 11))).isEqualTo(0)
    }

    // ---- Creating a whole task ----------------------------------------------------------------

    @Test
    fun `a task written with its children carries all of them`() = runTest {
        val tagId = dao.ensureTag("urgent", at(today, hour = 9))
        val fileId = database.insertFile("brief.pdf")

        val id = dao.insertWithChildren(
            task = taskEntity("Finish the homepage"),
            tagIds = listOf(tagId),
            subtaskTitles = listOf("Hero", "Footer"),
            attachmentFileIds = listOf(fileId),
        )

        assertThat(dao.tagIdsFor(id)).containsExactly(tagId)
        assertThat(dao.subtasksForTasks(listOf(id)).map { it.title })
            .containsExactly("Hero", "Footer").inOrder()
        assertThat(dao.attachmentsForTasks(listOf(id)).map { it.fileId }).containsExactly(fileId)
    }

    @Test
    fun `a task written with no children is still a task`() = runTest {
        val id = dao.insertWithChildren(
            task = taskEntity("Call Ahmed"),
            tagIds = emptyList(),
            subtaskTitles = emptyList(),
            attachmentFileIds = emptyList(),
        )

        assertThat(dao.findById(id)?.title).isEqualTo("Call Ahmed")
    }

    // ---- Recurrence ---------------------------------------------------------------------------

    @Test
    fun `a roll over completes one occurrence and opens the next`() = runTest {
        val id = dao.insert(
            taskEntity("Review Rayluxeo", dueAt = at(today, hour = 9), recurrenceRule = "freq=WEEKLY;interval=1"),
        )
        val nextDue = at(today.plusDays(7), hour = 9)
        val stamp = at(today, hour = 18)

        val nextId = dao.completeWithNextOccurrence(id, nextDue, "series-1", stamp)

        assertThat(nextId).isNotNull()
        val completed = dao.findById(id)!!
        val next = dao.findById(nextId!!)!!
        assertThat(completed.isDone).isTrue()
        assertThat(completed.completedAtEpochMillis).isEqualTo(stamp)
        assertThat(next.isDone).isFalse()
        assertThat(next.dueAtEpochMillis).isEqualTo(nextDue)
        assertThat(next.recurrenceRule).isEqualTo("freq=WEEKLY;interval=1")
    }

    @Test
    fun `a roll over ties both occurrences to one series`() = runTest {
        val id = dao.insert(taskEntity("Review Rayluxeo", recurrenceRule = "freq=WEEKLY;interval=1"))

        val nextId = dao.completeWithNextOccurrence(id, at(today.plusDays(7), 9), "series-1", at(today, 18))

        assertThat(dao.findById(id)?.seriesUuid).isEqualTo("series-1")
        assertThat(dao.findById(nextId!!)?.seriesUuid).isEqualTo("series-1")
    }

    @Test
    fun `a roll over keeps the series a task already belonged to`() = runTest {
        val id = dao.insert(
            taskEntity("Review Rayluxeo", recurrenceRule = "freq=WEEKLY;interval=1", seriesUuid = "old"),
        )

        val nextId = dao.completeWithNextOccurrence(id, at(today.plusDays(7), 9), "new", at(today, 18))

        assertThat(dao.findById(id)?.seriesUuid).isEqualTo("old")
        assertThat(dao.findById(nextId!!)?.seriesUuid).isEqualTo("old")
    }

    @Test
    fun `a roll over carries the tags and reopens the steps`() = runTest {
        val id = dao.insert(taskEntity("Review Rayluxeo", recurrenceRule = "freq=WEEKLY;interval=1"))
        val tagId = dao.ensureTag("weekly", at(today, hour = 9))
        dao.addTag(id, tagId, at(today, hour = 9))
        database.subtaskDao().append(
            SubtaskEntity(
                taskId = id,
                title = "Read the numbers",
                isDone = true,
                completedAtEpochMillis = at(today, hour = 12),
                position = 0,
                createdAtEpochMillis = at(today, hour = 9),
                updatedAtEpochMillis = at(today, hour = 12),
            )
        )

        val nextId = dao.completeWithNextOccurrence(id, at(today.plusDays(7), 9), "series-1", at(today, 18))!!

        assertThat(dao.tagIdsFor(nextId)).containsExactly(tagId)
        val step = dao.subtasksForTasks(listOf(nextId)).single()
        assertThat(step.title).isEqualTo("Read the numbers")
        assertThat(step.isDone).isFalse()
        assertThat(step.completedAtEpochMillis).isNull()
    }

    /** What Abu jotted and attached last week was about last week; what the task *is* carries on. */
    @Test
    fun `a roll over leaves the notes and the attachments behind but keeps the description`() = runTest {
        val id = dao.insert(
            taskEntity(
                title = "Review Rayluxeo",
                description = "the weekly numbers",
                notes = "chased Ahmed about the invoice",
                recurrenceRule = "freq=WEEKLY;interval=1",
            )
        )
        dao.appendAttachment(id, database.insertFile("last-week.pdf"), at(today, hour = 9))

        val nextId = dao.completeWithNextOccurrence(id, at(today.plusDays(7), 9), "series-1", at(today, 18))!!

        val next = dao.findById(nextId)!!
        assertThat(next.description).isEqualTo("the weekly numbers")
        assertThat(next.notes).isNull()
        assertThat(dao.attachmentsForTasks(listOf(nextId))).isEmpty()
        assertThat(dao.findById(id)?.notes).isEqualTo("chased Ahmed about the invoice")
    }

    @Test
    fun `a roll over of a task that is not there opens nothing`() = runTest {
        assertThat(dao.completeWithNextOccurrence(404L, at(today, 9), "series-1", at(today, 18)))
            .isNull()
    }

    @Test
    fun `a roll over of a binned task opens nothing`() = runTest {
        val id = dao.insert(taskEntity("Review Rayluxeo", recurrenceRule = "freq=WEEKLY;interval=1"))
        dao.softDelete(id, at(today, hour = 8))

        assertThat(
            dao.completeWithNextOccurrence(id, at(today.plusDays(7), 9), "series-1", at(today, 18)),
        ).isNull()
    }

    private fun at(date: LocalDate, hour: Int): Long = TaskTestData.at(date, hour)
}
