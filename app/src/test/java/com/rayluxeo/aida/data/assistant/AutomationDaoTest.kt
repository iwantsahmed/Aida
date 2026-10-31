package com.rayluxeo.aida.data.assistant

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.db.dao.AutomationDao
import com.rayluxeo.aida.data.db.entity.AutomationEntity
import com.rayluxeo.aida.data.db.entity.AutomationRunEntity
import com.rayluxeo.aida.domain.model.AutomationOutcome
import com.rayluxeo.aida.domain.model.AutomationTrigger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Hilt is deliberately absent: its local-test bytecode transform does not run under AGP 9.
 *
 * Foreign keys are switched on by hand. An in-memory builder leaves them off, which would make
 * every constraint test below pass for the wrong reason.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AutomationDaoTest {

    private lateinit var database: MiraDatabase
    private lateinit var dao: AutomationDao

    @Before
    fun openDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            MiraDatabase::class.java,
        )
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .allowMainThreadQueries()
            .build()
        database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON")
        dao = database.automationDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `an inserted automation is readable by id and observable`() = runTest {
        val id = dao.insert(automation(name = "Morning brief"))

        assertThat(id).isGreaterThan(0L)
        assertThat(dao.findById(id)?.name).isEqualTo("Morning brief")
        assertThat(dao.observeAll().first().map { it.name }).containsExactly("Morning brief")
    }

    @Test
    fun `only enabled automations reach the enabled flow`() = runTest {
        dao.insert(automation(name = "Morning brief", isEnabled = true))
        dao.insert(automation(name = "Evening wrap", isEnabled = false))

        assertThat(dao.observeEnabled().first().map { it.name }).containsExactly("Morning brief")
        assertThat(dao.observeAll().first()).hasSize(2)
    }

    @Test
    fun `a soft deleted automation disappears from every read`() = runTest {
        val id = dao.insert(automation(name = "Morning brief", isEnabled = true))

        assertThat(dao.softDelete(id, DELETED_AT)).isEqualTo(1)

        assertThat(dao.findById(id)).isNull()
        assertThat(dao.observeAll().first()).isEmpty()
        assertThat(dao.observeEnabled().first()).isEmpty()
        assertThat(dao.setEnabled(id, enabled = true, updatedAt = DELETED_AT)).isEqualTo(0)
        assertThat(dao.softDelete(id, DELETED_AT)).isEqualTo(0)
    }

    @Test
    fun `starting a run writes the run and stamps the automation together`() = runTest {
        val id = dao.insert(automation(name = "Morning brief"))
        assertThat(dao.findById(id)?.lastRunAtEpochMillis).isNull()

        val runId = dao.startRun(run(automationId = id, startedAt = STARTED_AT))

        assertThat(dao.runById(runId)?.automationId).isEqualTo(id)
        assertThat(dao.findById(id)?.lastRunAtEpochMillis).isEqualTo(STARTED_AT)
    }

    @Test
    fun `a run against an automation that does not exist is refused and leaves nothing behind`() =
        runTest {
            val thrown = runCatching {
                dao.startRun(run(automationId = 404L, startedAt = STARTED_AT))
            }.exceptionOrNull()

            assertThat(thrown).isInstanceOf(SQLiteConstraintException::class.java)
            assertThat(dao.listInFlight()).isEmpty()
            assertThat(countRows("automation_runs")).isEqualTo(0)
        }

    @Test
    fun `deleting an automation for good takes its runs with it`() = runTest {
        val id = dao.insert(automation(name = "Morning brief"))
        val runId = dao.startRun(run(automationId = id, startedAt = STARTED_AT))

        database.openHelper.writableDatabase.execSQL("DELETE FROM automations WHERE id = $id")

        assertThat(dao.runById(runId)).isNull()
        assertThat(countRows("automation_runs")).isEqualTo(0)
    }

    @Test
    fun `a run is in flight until it is finished`() = runTest {
        val id = dao.insert(automation(name = "Morning brief"))
        val runId = dao.startRun(run(automationId = id, startedAt = STARTED_AT))

        assertThat(dao.listInFlight().map { it.id }).containsExactly(runId)
        assertThat(dao.runById(runId)?.outcome).isEqualTo(AutomationOutcome.FAILED)

        val closed = dao.finishRun(runId, FINISHED_AT, AutomationOutcome.SUCCESS, null)

        assertThat(closed).isEqualTo(1)
        assertThat(dao.listInFlight()).isEmpty()
        assertThat(dao.runById(runId)?.finishedAtEpochMillis).isEqualTo(FINISHED_AT)
    }

    @Test
    fun `finishing a run twice cannot rewrite how it ended`() = runTest {
        val id = dao.insert(automation(name = "Morning brief"))
        val runId = dao.startRun(run(automationId = id, startedAt = STARTED_AT))
        dao.finishRun(runId, FINISHED_AT, AutomationOutcome.SUCCESS, null)

        val second = dao.finishRun(runId, FINISHED_AT + 1_000L, AutomationOutcome.FAILED, null)

        assertThat(second).isEqualTo(0)
        assertThat(dao.runById(runId)?.outcome).isEqualTo(AutomationOutcome.SUCCESS)
        assertThat(dao.runById(runId)?.finishedAtEpochMillis).isEqualTo(FINISHED_AT)
    }

    @Test
    fun `the runs of a binned automation are no longer listed but are still on disk`() = runTest {
        val id = dao.insert(automation(name = "Morning brief"))
        val runId = dao.startRun(run(automationId = id, startedAt = STARTED_AT))

        dao.softDelete(id, DELETED_AT)

        assertThat(dao.observeRuns(id).first()).isEmpty()
        assertThat(dao.listInFlight()).isEmpty()
        assertThat(dao.runById(runId)).isNotNull()
    }

    @Test
    fun `runs are listed newest first`() = runTest {
        val id = dao.insert(automation(name = "Morning brief"))
        val older = dao.startRun(run(automationId = id, startedAt = STARTED_AT))
        val newer = dao.startRun(run(automationId = id, startedAt = STARTED_AT + 60_000L))

        assertThat(dao.observeRuns(id).first().map { it.id }).containsExactly(newer, older).inOrder()
    }

    private fun countRows(table: String): Int =
        database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun automation(
        name: String,
        isEnabled: Boolean = false,
    ): AutomationEntity = AutomationEntity(
        name = name,
        description = null,
        trigger = AutomationTrigger.MANUAL,
        triggerConfigJson = "{}",
        actionConfigJson = """{"kind":"RUN_UTTERANCE","utterance":"aajker brief dao"}""",
        isEnabled = isEnabled,
        lastRunAtEpochMillis = null,
        deletedAtEpochMillis = null,
        createdAtEpochMillis = CREATED_AT,
        updatedAtEpochMillis = CREATED_AT,
    )

    private fun run(automationId: Long, startedAt: Long): AutomationRunEntity = AutomationRunEntity(
        automationId = automationId,
        startedAtEpochMillis = startedAt,
        finishedAtEpochMillis = null,
        outcome = AutomationOutcome.FAILED,
        detail = null,
    )

    private companion object {
        const val CREATED_AT = 1_772_000_000_000L
        const val STARTED_AT = 1_772_000_100_000L
        const val FINISHED_AT = 1_772_000_200_000L
        const val DELETED_AT = 1_772_000_300_000L
    }
}
