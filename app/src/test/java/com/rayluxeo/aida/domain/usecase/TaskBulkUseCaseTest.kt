package com.rayluxeo.aida.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.ai.nlu.FixedMiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.getOrNull
import com.rayluxeo.aida.domain.task.DefaultRecurrenceCalculator
import com.rayluxeo.aida.domain.task.RecurrenceRule
import java.time.DayOfWeek
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Bulk select, and the three things Abu can do to a selection at once.
 *
 * The whole point of these is the report they come back with. Twenty tasks vanish from the list
 * whether or not all twenty were dealt with, so a bulk action that quietly dropped three would
 * look exactly like one that worked — which is why every test here asserts what was reported as
 * much as what was written.
 *
 * Nothing is injected (google/dagger#5261) and the fakes are the ones in `TaskUseCaseTest`.
 */
class TaskBulkUseCaseTest {

    private val clock = FixedMiraClock()
    private val tasks = FakeTaskRepository(clock)
    private val steps = FakeSubtaskRepository(clock)
    private val recurrences = DefaultRecurrenceCalculator()

    private val complete = CompleteTaskByIdUseCase(tasks, recurrences, clock)
    private val bulkComplete = BulkCompleteTasksUseCase(tasks, complete)
    private val bulkArchive = BulkArchiveTasksUseCase(tasks)
    private val bulkDelete = BulkDeleteTasksUseCase(tasks)

    @Test
    fun `bulk complete finishes everything selected`() = runTest {
        val selected = tasks.seed(task(title = "One"), task(title = "Two"), task(title = "Three"))

        val outcome = bulkComplete(selected.map { it.id }).getOrNull()!!

        assertThat(outcome.succeeded).containsExactlyElementsIn(selected.map { it.id }).inOrder()
        assertThat(outcome.failed).isEmpty()
        assertThat(outcome.allSucceeded).isTrue()
        assertThat(tasks.snapshot().all { it.isDone }).isTrue()
    }

    @Test
    fun `bulk complete names the ids it could not act on and still finishes the rest`() = runTest {
        val real = tasks.seed(task(title = "One"), task(title = "Two"))

        val outcome = bulkComplete(listOf(real[0].id, 404L, real[1].id)).getOrNull()!!

        assertThat(outcome.succeeded).containsExactly(real[0].id, real[1].id).inOrder()
        assertThat(outcome.failed.map { it.taskId }).containsExactly(404L)
        assertThat(outcome.failed.single().error.code).isEqualTo(MiraErrorCode.NOT_FOUND)
        assertThat(outcome.requested).isEqualTo(3)
        assertThat(tasks.row(real[0].id)!!.isDone).isTrue()
    }

    @Test
    fun `bulk complete refuses an empty selection`() = runTest {
        assertThat(bulkComplete(emptyList()).errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(bulkArchive(emptyList()).errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(bulkDelete(emptyList()).errorCode()).isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `bulk complete counts a task selected twice once`() = runTest {
        val stored = tasks.seed(task(title = "One")).first()

        val outcome = bulkComplete(listOf(stored.id, stored.id)).getOrNull()!!

        assertThat(outcome.succeeded).containsExactly(stored.id)
        assertThat(outcome.requested).isEqualTo(1)
    }

    @Test
    fun `bulk complete opens the next occurrence of the repeating ones`() = runTest {
        val once = tasks.seed(task(title = "Send the proposal")).first()
        val repeating = tasks.seed(
            task(
                title = "Weekly review",
                dueAt = FixedMiraClock.dhaka(2026, 3, 9, 10),
                recurrence = RecurrenceRule.Weekly(days = setOf(DayOfWeek.MONDAY)),
                seriesUuid = "series-1",
            ),
        ).first()

        val outcome = bulkComplete(listOf(once.id, repeating.id)).getOrNull()!!

        assertThat(outcome.succeeded).containsExactly(once.id, repeating.id).inOrder()
        assertThat(tasks.completions.map { it.id }).containsExactly(repeating.id)
        assertThat(tasks.completions.single().nextDueAt).isEqualTo(FixedMiraClock.dhaka(2026, 3, 16, 10))
        assertThat(tasks.snapshot().count { it.seriesUuid == "series-1" && !it.isDone }).isEqualTo(1)
    }

    @Test
    fun `bulk complete reports every task when the write itself fails`() = runTest {
        val selected = tasks.seed(task(title = "One"), task(title = "Two"))
        tasks.failBulkWith = MiraErrorCode.STORAGE

        val outcome = bulkComplete(selected.map { it.id }).getOrNull()!!

        assertThat(outcome.succeeded).isEmpty()
        assertThat(outcome.failed.map { it.taskId }).containsExactlyElementsIn(selected.map { it.id })
        assertThat(outcome.failed.map { it.error.code }.toSet()).containsExactly(MiraErrorCode.STORAGE)
        assertThat(tasks.snapshot().none { it.isDone }).isTrue()
    }

    @Test
    fun `bulk archive puts every selected task aside`() = runTest {
        val selected = tasks.seed(task(title = "One"), task(title = "Two"))

        val outcome = bulkArchive(selected.map { it.id } + 404L).getOrNull()!!

        assertThat(outcome.succeeded).containsExactlyElementsIn(selected.map { it.id }).inOrder()
        assertThat(outcome.failed.map { it.taskId }).containsExactly(404L)
        assertThat(tasks.snapshot().all { it.archivedAt == clock.now() }).isTrue()
    }

    @Test
    fun `bulk delete bins every selected task and leaves the rows behind`() = runTest {
        val selected = tasks.seed(task(title = "One"), task(title = "Two"))

        val outcome = bulkDelete(selected.map { it.id }).getOrNull()!!

        assertThat(outcome.allSucceeded).isTrue()
        assertThat(tasks.snapshot()).hasSize(2)
        assertThat(tasks.snapshot().all { it.deletedAt == clock.now() }).isTrue()
    }

    @Test
    fun `the report keeps the order Abu selected in`() = runTest {
        val selected = tasks.seed(task(title = "One"), task(title = "Two"), task(title = "Three"))
        val order = listOf(selected[2].id, 404L, selected[0].id, selected[1].id)

        val outcome = bulkArchive(order).getOrNull()!!

        assertThat(outcome.succeeded)
            .containsExactly(selected[2].id, selected[0].id, selected[1].id)
            .inOrder()
    }

    @Test
    fun `a selection of tasks with steps is unaffected by the steps`() = runTest {
        val stored = tasks.seed(task(title = "Ship the site")).first()
        steps.add(stored.id, "copy")
        tasks.attachSteps(stored.id, steps.forTask(stored.id))

        val outcome = bulkComplete(listOf(stored.id)).getOrNull()!!

        assertThat(outcome.allSucceeded).isTrue()
        assertThat(steps.forTask(stored.id).none { it.isDone }).isTrue()
    }
}
