package com.rayluxeo.aida.data.moneyflow

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.db.dao.CalculationDao
import com.rayluxeo.aida.data.db.entity.CalculationRecordEntity
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
 * The stored answer is the text Abu was actually given, so what is asserted here is that it
 * comes back byte for byte rather than being re-derived from the expression.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CalculationDaoTest {

    private lateinit var database: MiraDatabase
    private lateinit var dao: CalculationDao

    @Before
    fun openDatabase() {
        database = openMoneyFlowDatabase()
        dao = database.calculationDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `the answer comes back exactly as it was given`() = runTest {
        dao.insert(
            record(expression = "12450 * 3 + 7.5% of 2400", resultText = "37,530.00", at = NOW),
        )

        val stored = dao.mostRecent()!!

        assertThat(stored.expression).isEqualTo("12450 * 3 + 7.5% of 2400")
        assertThat(stored.resultText).isEqualTo("37,530.00")
        assertThat(stored.languageTag).isEqualTo("en-US")
    }

    @Test
    fun `the most recent record is the one that answers what was that again`() = runTest {
        dao.insert(record(expression = "2 + 2", resultText = "4", at = NOW - 2_000L))
        dao.insert(record(expression = "10 / 4", resultText = "2.5", at = NOW - 1_000L))
        dao.insert(record(expression = "1200 * 3", resultText = "3,600", at = NOW))

        assertThat(dao.mostRecent()?.expression).isEqualTo("1200 * 3")
    }

    @Test
    fun `an empty history has no most recent record rather than failing`() = runTest {
        assertThat(dao.mostRecent()).isNull()
    }

    @Test
    fun `the history is newest first and honours the limit`() = runTest {
        dao.insert(record(expression = "oldest", resultText = "1", at = NOW - 3_000L))
        dao.insert(record(expression = "middle", resultText = "2", at = NOW - 2_000L))
        dao.insert(record(expression = "newest", resultText = "3", at = NOW - 1_000L))

        assertThat(dao.observeRecent(2).first().map { it.expression })
            .containsExactly("newest", "middle")
            .inOrder()
    }

    @Test
    fun `clearing the history removes all of it`() = runTest {
        dao.insert(record(expression = "2 + 2", resultText = "4", at = NOW))
        dao.insert(record(expression = "3 + 3", resultText = "6", at = NOW))

        dao.deleteAll()

        assertThat(dao.observeRecent(50).first()).isEmpty()
        assertThat(dao.mostRecent()).isNull()
    }

    private fun record(
        expression: String,
        resultText: String,
        at: Long,
        languageTag: String = "en-US",
    ): CalculationRecordEntity = CalculationRecordEntity(
        expression = expression,
        resultText = resultText,
        languageTag = languageTag,
        createdAtEpochMillis = at,
    )

    private companion object {
        val NOW: Long = LocalDate.of(2026, 3, 5).atMillis(hour = 11)
    }
}
