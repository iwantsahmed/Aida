package com.rayluxeo.aida.data.repository

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.data.TestDispatcherProvider
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.work.failureCode
import com.rayluxeo.aida.data.work.openTestDatabase
import com.rayluxeo.aida.data.work.successValue
import com.rayluxeo.aida.domain.task.TextRecurrenceCodec
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
 * Tags against a real database, because the rule that matters here is enforced by a unique index:
 * a tag Abu asks for twice must come back as the one he already has, rather than as a constraint
 * violation in his face.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TagRepositoryImplTest {

    private lateinit var database: MiraDatabase
    private lateinit var tags: TagRepositoryImpl
    private lateinit var tasks: TaskRepositoryImpl

    private val clock = FakeMiraClock(TaskTestData.now(hour = 14))

    @Before
    fun openDatabase() {
        database = openTestDatabase()
        val dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher())
        tags = TagRepositoryImpl(
            dao = database.taskDao(),
            clock = clock,
            dispatchers = dispatchers,
        )
        tasks = TaskRepositoryImpl(
            dao = database.taskDao(),
            recurrence = TextRecurrenceCodec(),
            clock = clock,
            dispatchers = dispatchers,
        )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `asking for a tag twice returns the same one`() = runTest {
        val first = tags.ensure("urgent").successValue()
        val second = tags.ensure("urgent").successValue()

        assertThat(second.id).isEqualTo(first.id)
        assertThat(tags.observeAll().first()).hasSize(1)
    }

    @Test
    fun `a tag is the same label whatever the case and the spacing`() = runTest {
        val first = tags.ensure("Urgent").successValue()

        assertThat(tags.ensure("urgent").successValue().id).isEqualTo(first.id)
        assertThat(tags.ensure("  URGENT  ").successValue().id).isEqualTo(first.id)
        assertThat(tags.observeAll().first()).hasSize(1)
    }

    @Test
    fun `a tag keeps the spelling it was created with`() = runTest {
        tags.ensure("  Rayluxeo  ").successValue()

        assertThat(tags.observeAll().first().single().name).isEqualTo("Rayluxeo")
    }

    @Test
    fun `a blank name is refused rather than stored`() = runTest {
        assertThat(tags.ensure("   ").failureCode()).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(tags.observeAll().first()).isEmpty()
    }

    @Test
    fun `bengali and english tags sit side by side`() = runTest {
        tags.ensure("জরুরি").successValue()
        tags.ensure("urgent").successValue()

        assertThat(tags.observeAll().first().map { it.name }).containsExactly("জরুরি", "urgent")
    }

    @Test
    fun `renaming a tag renames it on every task`() = runTest {
        val tag = tags.ensure("urgent").successValue()
        val taskId = database.taskDao().insert(taskEntity("Call Ahmed"))
        tasks.addTag(taskId, tag.id).successValue()

        tags.rename(tag.id, "critical").successValue()

        assertThat(tasks.byId(taskId).successValue().tags.map { it.name })
            .containsExactly("critical")
    }

    @Test
    fun `renaming a tag onto another tag's name is refused`() = runTest {
        val urgent = tags.ensure("urgent").successValue()
        tags.ensure("billing").successValue()

        assertThat(tags.rename(urgent.id, "billing").failureCode())
            .isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(tags.byId(urgent.id).successValue().name).isEqualTo("urgent")
    }

    @Test
    fun `renaming a tag to what it already is succeeds`() = runTest {
        val tag = tags.ensure("urgent").successValue()

        assertThat(tags.rename(tag.id, "Urgent").successValue().name).isEqualTo("Urgent")
    }

    @Test
    fun `renaming a tag that is not there is reported`() = runTest {
        assertThat(tags.rename(404L, "urgent").failureCode()).isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    @Test
    fun `deleting a tag takes it off the tasks and leaves the tasks alone`() = runTest {
        val tag = tags.ensure("urgent").successValue()
        val taskId = database.taskDao().insert(taskEntity("Call Ahmed"))
        tasks.addTag(taskId, tag.id).successValue()

        tags.delete(tag.id).successValue()

        val task = tasks.byId(taskId).successValue()
        assertThat(task.title).isEqualTo("Call Ahmed")
        assertThat(task.tags).isEmpty()
        assertThat(tags.observeAll().first()).isEmpty()
    }

    @Test
    fun `deleting a tag that is not there is reported`() = runTest {
        assertThat(tags.delete(404L).failureCode()).isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    @Test
    fun `usage counts the outstanding work and nothing else`() = runTest {
        val tag = tags.ensure("urgent").successValue()
        val open = database.taskDao().insert(taskEntity("Open"))
        val done = database.taskDao().insert(taskEntity("Done"))
        val archived = database.taskDao().insert(taskEntity("Archived"))
        val binned = database.taskDao().insert(taskEntity("Binned"))
        listOf(open, done, archived, binned).forEach { tasks.addTag(it, tag.id).successValue() }
        tasks.setDone(done, done = true).successValue()
        tasks.archive(archived).successValue()
        tasks.delete(binned).successValue()

        val usage = tags.observeUsage().first().single()

        assertThat(usage.tag.name).isEqualTo("urgent")
        assertThat(usage.taskCount).isEqualTo(1)
    }

    @Test
    fun `a tag nobody has used yet still appears in the list`() = runTest {
        tags.ensure("someday").successValue()

        val usage = tags.observeUsage().first().single()

        assertThat(usage.tag.name).isEqualTo("someday")
        assertThat(usage.taskCount).isEqualTo(0)
    }

    @Test
    fun `looking up a tag that is not there is reported`() = runTest {
        assertThat(tags.byId(404L).failureCode()).isEqualTo(MiraErrorCode.NOT_FOUND)
    }
}
