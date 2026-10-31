package com.rayluxeo.aida.ui.privacy

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.ai.nlu.FixedMiraClock
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.common.asSuccess
import com.rayluxeo.aida.domain.model.Task
import com.rayluxeo.aida.domain.repository.GeminiCredentialStore
import com.rayluxeo.aida.domain.repository.LocalDataEraser
import com.rayluxeo.aida.domain.repository.MiraSettings
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.repository.TaskCounts
import com.rayluxeo.aida.domain.repository.TaskGroup
import com.rayluxeo.aida.domain.repository.TaskOccurrence
import com.rayluxeo.aida.domain.repository.TaskRepository
import com.rayluxeo.aida.domain.usecase.EraseAllDataUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyDashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val clock = FixedMiraClock()

    private class FakeSettingsRepo : SettingsRepository {
        var currentSettings = MiraSettings()
        override val settings: Flow<MiraSettings> = flowOf(currentSettings)
        override suspend fun current() = currentSettings
        override suspend fun setLanguageMode(mode: com.rayluxeo.aida.core.common.LanguageMode) {}
        override suspend fun setActivationSound(enabled: Boolean) {}
        override suspend fun setHapticFeedback(enabled: Boolean) {}
        override suspend fun setOnboardingComplete(complete: Boolean) {}
        override suspend fun setAppLock(enabled: Boolean, timeoutSeconds: Int, lockWhenBackgrounded: Boolean) {
            currentSettings = currentSettings.copy(appLockEnabled = enabled)
        }
    }

    private class FakeGeminiCreds : GeminiCredentialStore {
        var key: String? = "test-key"
        override val hasKey: Flow<Boolean> = flowOf(true)
        override suspend fun apiKey(): String? = key
        override suspend fun setApiKey(key: String?) {
            this.key = key
        }
    }

    private class FakeTaskRepo : TaskRepository {
        val tasks = mutableListOf<Task>()
        override fun observe(filter: com.rayluxeo.aida.domain.model.TaskFilter) = flowOf(tasks)
        override fun observe(query: com.rayluxeo.aida.domain.model.TaskQuery) = flowOf(tasks)
        override suspend fun list(query: com.rayluxeo.aida.domain.model.TaskQuery) = tasks.asSuccess()
        override suspend fun count(query: com.rayluxeo.aida.domain.model.TaskQuery) = tasks.size.asSuccess()
        override suspend fun groupedByProject(query: com.rayluxeo.aida.domain.model.TaskQuery) = emptyList<TaskGroup>().asSuccess()
        override suspend fun groupedByClient(query: com.rayluxeo.aida.domain.model.TaskQuery) = emptyList<TaskGroup>().asSuccess()
        override fun observeCounts() = flowOf(TaskCounts(0, 0, 0, 0))
        override suspend fun byId(id: Long) = tasks.first().asSuccess()
        override suspend fun search(query: String?, filter: com.rayluxeo.aida.domain.model.TaskFilter) = tasks.asSuccess()
        override suspend fun create(task: Task) = task.copy(id = tasks.size + 1L).also { tasks.add(it) }.asSuccess()
        override suspend fun update(task: Task) = task.asSuccess()
        override suspend fun setDone(id: Long, isDone: Boolean) = tasks.first().asSuccess()
        override suspend fun setDoneAll(ids: Collection<Long>, done: Boolean) = ids.size.asSuccess()
        override suspend fun snooze(id: Long, until: Instant) = tasks.first().asSuccess()
        override suspend fun archive(id: Long) = tasks.first().asSuccess()
        override suspend fun unarchive(id: Long) = tasks.first().asSuccess()
        override suspend fun archiveAll(ids: Collection<Long>) = ids.size.asSuccess()
        override suspend fun delete(id: Long) = Unit.asSuccess()
        override suspend fun restore(id: Long) = tasks.first().asSuccess()
        override suspend fun deleteAll(ids: Collection<Long>) = ids.size.asSuccess()
        override suspend fun completeWithNextOccurrence(id: Long, nextDueAt: Instant) = TaskOccurrence(tasks.first(), tasks.first()).asSuccess()
        override suspend fun setTags(taskId: Long, tagIds: Collection<Long>) = tasks.first().asSuccess()
        override suspend fun addTag(taskId: Long, tagId: Long) = tasks.first().asSuccess()
        override suspend fun removeTag(taskId: Long, tagId: Long) = tasks.first().asSuccess()
        override suspend fun attachFile(taskId: Long, fileId: Long) = tasks.first().asSuccess()
        override suspend fun detachFile(taskId: Long, fileId: Long) = tasks.first().asSuccess()
    }

    private class FakeEraser(private val result: AppResult<Unit> = AppResult.Success(Unit)) : LocalDataEraser {
        var calls = 0
        override suspend fun eraseEverything(): AppResult<Unit> {
            calls++
            return result
        }
    }

    private fun viewModel(
        settingsRepo: SettingsRepository = FakeSettingsRepo(),
        geminiCreds: GeminiCredentialStore = FakeGeminiCreds(),
        taskRepo: TaskRepository = FakeTaskRepo(),
        eraser: LocalDataEraser = FakeEraser(),
    ) = PrivacyDashboardViewModel(
        settingsRepository = settingsRepo,
        geminiCredentials = geminiCreds,
        taskRepository = taskRepo,
        eraseAllData = EraseAllDataUseCase(eraser),
    )

    @Test
    fun `toggleGeminiEnabled clears stored key when disabled`() = runTest {
        val geminiCreds = FakeGeminiCreds()
        val vm = viewModel(geminiCreds = geminiCreds)

        vm.toggleGeminiEnabled(false)
        testScheduler.advanceUntilIdle()

        assertThat(geminiCreds.apiKey()).isNull()
        assertThat(vm.uiState.first().notice).contains("Gemini AI disabled")
    }

    @Test
    fun `toggleAppLock persists the setting and reports it`() = runTest {
        val repo = FakeSettingsRepo()
        val vm = viewModel(settingsRepo = repo)

        vm.toggleAppLock(true)
        testScheduler.advanceUntilIdle()

        assertThat(repo.currentSettings.appLockEnabled).isTrue()
        assertThat(vm.uiState.first().notice).contains("App lock on")
    }

    @Test
    fun `confirmEraseAllData runs the erase and reports success`() = runTest {
        val eraser = FakeEraser(AppResult.Success(Unit))
        val vm = viewModel(eraser = eraser)

        vm.confirmEraseAllData()
        testScheduler.advanceUntilIdle()

        assertThat(eraser.calls).isEqualTo(1)
        val uiState = vm.uiState.first()
        assertThat(uiState.isEraseConfirmationOpen).isFalse()
        assertThat(uiState.totalTasksCount).isEqualTo(0)
        assertThat(uiState.notice).contains("erased")
    }

    @Test
    fun `confirmEraseAllData reports a failure without claiming success`() = runTest {
        val eraser = FakeEraser(com.rayluxeo.aida.core.common.failure(com.rayluxeo.aida.core.common.MiraErrorCode.STORAGE))
        val vm = viewModel(eraser = eraser)

        vm.confirmEraseAllData()
        testScheduler.advanceUntilIdle()

        assertThat(eraser.calls).isEqualTo(1)
        assertThat(vm.uiState.first().notice).doesNotContain("erased")
    }
}
