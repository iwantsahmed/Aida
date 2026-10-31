package com.rayluxeo.aida.data.platform

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.data.TestDispatcherProvider
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.repository.AppSettingRepositoryImpl
import com.rayluxeo.aida.domain.repository.AppSettingRepository
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppSettingRepositoryImplTest {

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
    fun `a key that was never written reads as nothing rather than as a failure`() = runTest {
        val repository = repository()

        val read = repository.get(NEXT_INVOICE)

        assertThat(read).isInstanceOf(AppResult.Success::class.java)
        assertThat((read as AppResult.Success).value).isNull()
        assertThat(repository.observe(NEXT_INVOICE).first()).isNull()
    }

    @Test
    fun `a written setting is readable and observable`() = runTest {
        val repository = repository()

        repository.put(NEXT_INVOICE, "0042")

        assertThat((repository.get(NEXT_INVOICE) as AppResult.Success).value).isEqualTo("0042")
        assertThat(repository.observe(NEXT_INVOICE).first()).isEqualTo("0042")
    }

    @Test
    fun `writing the same key again replaces the value and stamps the time`() = runTest {
        val repository = repository()
        repository.put(NEXT_INVOICE, "0042")
        clock.moveTo(LocalDateTime.of(2026, 3, 6, 9, 0))

        repository.put(NEXT_INVOICE, "0043")

        assertThat((repository.get(NEXT_INVOICE) as AppResult.Success).value).isEqualTo("0043")
        assertThat(database.countRows("app_settings")).isEqualTo(1)
        assertThat(database.appSettingDao().find(NEXT_INVOICE)?.updatedAtEpochMillis)
            .isEqualTo(clock.now().toEpochMilli())
    }

    @Test
    fun `removing a setting leaves nothing behind`() = runTest {
        val repository = repository()
        repository.put(NEXT_INVOICE, "0042")

        repository.remove(NEXT_INVOICE)

        assertThat((repository.get(NEXT_INVOICE) as AppResult.Success).value).isNull()
        assertThat(database.countRows("app_settings")).isEqualTo(0)
    }

    @Test
    fun `removing a key that was never written is not a failure`() = runTest {
        assertThat(repository().remove(NEXT_INVOICE)).isInstanceOf(AppResult.Success::class.java)
    }

    @Test
    fun `the counter starts where it is told to and never hands out a value twice`() = runTest {
        val repository = repository()

        val allocated = listOf(
            (repository.takeNextCounterValue(NEXT_INVOICE, startAt = 1001L) as AppResult.Success).value,
            (repository.takeNextCounterValue(NEXT_INVOICE, startAt = 1001L) as AppResult.Success).value,
            (repository.takeNextCounterValue(NEXT_INVOICE, startAt = 1001L) as AppResult.Success).value,
        )

        assertThat(allocated).containsExactly(1001L, 1002L, 1003L).inOrder()
        assertThat((repository.get(NEXT_INVOICE) as AppResult.Success).value).isEqualTo("1003")
    }

    @Test
    fun `two counters do not interfere with one another`() = runTest {
        val repository = repository()

        repository.takeNextCounterValue(NEXT_INVOICE, startAt = 1L)
        repository.takeNextCounterValue(NEXT_INVOICE, startAt = 1L)
        val quote = (repository.takeNextCounterValue(NEXT_QUOTE, startAt = 1L) as AppResult.Success).value

        assertThat(quote).isEqualTo(1L)
        assertThat((repository.get(NEXT_INVOICE) as AppResult.Success).value).isEqualTo("2")
    }

    @Test
    fun `a stored value that is not a number is refused rather than quietly restarting the count`() =
        runTest {
            val repository = repository()
            repository.put(NEXT_INVOICE, "INV-0042")

            val refused = repository.takeNextCounterValue(NEXT_INVOICE, startAt = 1L)

            assertThat(refused).isInstanceOf(AppResult.Failure::class.java)
            assertThat((refused as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.STORAGE)
            assertThat((repository.get(NEXT_INVOICE) as AppResult.Success).value).isEqualTo("INV-0042")
        }

    private fun repository(): AppSettingRepository = AppSettingRepositoryImpl(
        dao = database.appSettingDao(),
        clock = clock,
        dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher()),
    )

    private companion object {
        const val NEXT_INVOICE = "invoice.next_number"
        const val NEXT_QUOTE = "quote.next_number"
    }
}
