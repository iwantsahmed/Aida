package com.rayluxeo.aida.data.work

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.data.TestDispatcherProvider
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.repository.SubtaskRepositoryImpl
import com.rayluxeo.aida.domain.repository.SubtaskRepository
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
 * Hilt is deliberately absent (its local-test transform does not run under AGP 9), so the
 * database and the repository are both built by hand.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SubtaskRepositoryImplTest {

    private lateinit var database: MiraDatabase
    private val clock = FakeMiraClock(LocalDateTime.of(2026, 3, 5, 14, 0))

    @Before
    fun openDatabase() {
        database = openTestDatabase()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `steps are appended at the end and read back in that order`() = runTest {
        val taskId = insertTask("Ship the site")
        val repository = repository()

        val first = repository.add(taskId, "Write the copy").successValue()
        val second = repository.add(taskId, "  Upload the assets  ").successValue()

        assertThat(first.position).isEqualTo(0)
        assertThat(second.position).isEqualTo(1)
        assertThat(second.title).isEqualTo("Upload the assets")
        assertThat(repository.observeForTask(taskId).first().map { it.title })
            .containsExactly("Write the copy", "Upload the assets")
            .inOrder()
    }

    @Test
    fun `a blank step is refused rather than stored`() = runTest {
        val taskId = insertTask("Ship the site")
        val repository = repository()

        assertThat(repository.add(taskId, "   ").failureCode()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(repository.observeForTask(taskId).first()).isEmpty()
    }

    @Test
    fun `a step for a task that does not exist is a failure, not an exception`() = runTest {
        val repository = repository()

        assertThat(repository.add(taskId = 404L, title = "Write the copy").failureCode())
            .isEqualTo(MiraErrorCode.STORAGE)
    }

    @Test
    fun `completing a step stamps the moment and clearing it takes it away`() = runTest {
        val taskId = insertTask("Ship the site")
        val repository = repository()
        val step = repository.add(taskId, "Write the copy").successValue()

        val done = repository.setDone(step.id, done = true).successValue()
        assertThat(done.isDone).isTrue()
        assertThat(done.completedAt).isEqualTo(clock.now())

        val reopened = repository.setDone(step.id, done = false).successValue()
        assertThat(reopened.isDone).isFalse()
        assertThat(reopened.completedAt).isNull()
    }

    @Test
    fun `completing every step leaves the parent task open`() = runTest {
        val taskId = insertTask("Ship the site")
        val repository = repository()
        val first = repository.add(taskId, "Write the copy").successValue()
        val second = repository.add(taskId, "Upload the assets").successValue()

        repository.setDone(first.id, done = true)
        repository.setDone(second.id, done = true)

        assertThat(database.taskDao().findById(taskId)?.isDone).isFalse()
    }

    @Test
    fun `reorder renumbers the whole list`() = runTest {
        val taskId = insertTask("Ship the site")
        val repository = repository()
        val first = repository.add(taskId, "Write the copy").successValue()
        val second = repository.add(taskId, "Upload the assets").successValue()
        val third = repository.add(taskId, "Point the domain").successValue()

        repository.reorder(taskId, listOf(third.id, first.id, second.id)).successValue()

        assertThat(repository.observeForTask(taskId).first().map { it.title })
            .containsExactly("Point the domain", "Write the copy", "Upload the assets")
            .inOrder()
        assertThat(repository.observeForTask(taskId).first().map { it.position })
            .containsExactly(0, 1, 2)
            .inOrder()
    }

    @Test
    fun `a reorder that does not name every step changes nothing`() = runTest {
        val taskId = insertTask("Ship the site")
        val repository = repository()
        val first = repository.add(taskId, "Write the copy").successValue()
        val second = repository.add(taskId, "Upload the assets").successValue()
        repository.add(taskId, "Point the domain").successValue()

        val refused = repository.reorder(taskId, listOf(second.id, first.id))

        assertThat(refused.failureCode()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(repository.observeForTask(taskId).first().map { it.title })
            .containsExactly("Write the copy", "Upload the assets", "Point the domain")
            .inOrder()
    }

    @Test
    fun `deleting the task takes its steps with it`() = runTest {
        val taskId = insertTask("Ship the site")
        val repository = repository()
        val step = repository.add(taskId, "Write the copy").successValue()

        database.hardDelete(table = "tasks", id = taskId)

        assertThat(database.subtaskDao().findById(step.id)).isNull()
    }

    @Test
    fun `binning the task hides its steps and restoring brings the same rows back`() = runTest {
        val taskId = insertTask("Ship the site")
        val repository = repository()
        val first = repository.add(taskId, "Write the copy").successValue()
        val second = repository.add(taskId, "Upload the assets").successValue()

        database.taskDao().softDelete(taskId, clock.now().toEpochMilli())
        assertThat(repository.observeForTask(taskId).first()).isEmpty()
        assertThat(database.subtaskDao().findById(first.id)).isNotNull()

        database.taskDao().restore(taskId, clock.now().toEpochMilli())

        assertThat(repository.observeForTask(taskId).first().map { it.id })
            .containsExactly(first.id, second.id)
            .inOrder()
    }

    @Test
    fun `deleting a step removes it once and reports the second attempt`() = runTest {
        val taskId = insertTask("Ship the site")
        val repository = repository()
        val step = repository.add(taskId, "Write the copy").successValue()

        repository.delete(step.id).successValue()

        assertThat(repository.observeForTask(taskId).first()).isEmpty()
        assertThat(repository.delete(step.id).failureCode()).isEqualTo(MiraErrorCode.NOT_FOUND)
        assertThat(repository.byId(step.id).failureCode()).isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    private suspend fun insertTask(title: String): Long =
        database.taskDao().insert(taskRow(title, clock.now().toEpochMilli()))

    private fun repository(): SubtaskRepository = SubtaskRepositoryImpl(
        dao = database.subtaskDao(),
        clock = clock,
        dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher()),
    )
}
