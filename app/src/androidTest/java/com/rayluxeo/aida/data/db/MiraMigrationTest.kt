package com.rayluxeo.aida.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

/**
 * The migration is the one piece of this app that runs against data Mira did not create in the
 * same session, on a phone the developer cannot inspect. It gets tested against a real version 1
 * database with real rows in it, not against an empty one — an empty database migrates cleanly
 * even when the statement that moves Abu's data is wrong.
 *
 * Instrumented rather than local on purpose. MigrationTestHelper opens a real database file, and
 * Robolectric's FileDescriptor interception cannot serve one on JDK 21 — every test fails with
 * "Failed to interact with raw FileDescriptor internals" before a single statement runs. Against
 * real SQLite on a device it also tests the SQLite that will actually run the migration.
 */
@RunWith(AndroidJUnit4::class)
class MiraMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MiraDatabase::class.java,
    )

    @Test
    fun `version 1 opens at version 2 with every table in place`() {
        helper.createDatabase(TEST_DB, 1).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        // runMigrationsAndValidate already compares against the exported schema; this asserts the
        // tables Abu's records actually live in, so a silently empty migration cannot pass.
        val tables = db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        assertThat(tables).containsAtLeast(
            "tasks", "subtasks", "reminders", "alarm_records", "timer_records",
            "clients", "projects", "project_milestones",
            "invoices", "invoice_items", "quotes", "quote_items",
            "payments", "expenses", "income_entries", "budgets", "subscriptions",
            "notes", "note_attachments", "memory", "voice_notes", "files", "scan_records",
            "conversations", "conversation_messages",
            "automations", "automation_runs", "meetings", "meeting_notes", "calendar_links",
            "user_profile", "app_settings", "activity_log", "vault_items",
        )
        assertThat(tables).doesNotContain("conversation_turns")
        assertThat(tables).doesNotContain("tasks_v1")
        db.close()
    }

    @Test
    fun `a task written at version 1 survives the rebuild with its id and fields`() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO tasks (
                    id, title, notes, dueAtEpochMillis, priority, isDone,
                    completedAtEpochMillis, origin, createdAtEpochMillis, updatedAtEpochMillis
                ) VALUES (7, 'Call Ahmed', 'about the quote', 1800000000000, 'HIGH', 0,
                          NULL, 'VOICE', 1700000000000, 1700000000000)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        db.query("SELECT id, title, notes, priority, origin, projectId, deletedAtEpochMillis FROM tasks")
            .use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getLong(0)).isEqualTo(7L)
                assertThat(cursor.getString(1)).isEqualTo("Call Ahmed")
                assertThat(cursor.getString(2)).isEqualTo("about the quote")
                assertThat(cursor.getString(3)).isEqualTo("HIGH")
                assertThat(cursor.getString(4)).isEqualTo("VOICE")
                assertThat(cursor.isNull(5)).isTrue()
                assertThat(cursor.isNull(6)).isTrue()
                assertThat(cursor.getCount()).isEqualTo(1)
            }
        db.close()
    }

    @Test
    fun `the rebuilt task is still findable by full-text search`() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO tasks (
                    id, title, notes, dueAtEpochMillis, priority, isDone,
                    completedAtEpochMillis, origin, createdAtEpochMillis, updatedAtEpochMillis
                ) VALUES (1, 'Renew the lease', NULL, NULL, 'NORMAL', 0, NULL, 'MANUAL', 1, 1)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        // The FTS index belonged to the old table and was rebuilt from the copied rows; if the
        // rebuild were skipped this returns nothing while the task itself looks perfectly fine.
        db.query("SELECT t.title FROM tasks t JOIN task_fts f ON f.rowid = t.id WHERE task_fts MATCH 'lease*'")
            .use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getString(0)).isEqualTo("Renew the lease")
            }
        db.close()
    }

    @Test
    fun `existing conversation turns move into one thread rather than being discarded`() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO conversation_turns (id, role, text, languageTag, source, commandName, createdAtEpochMillis)
                VALUES (1, 'ABU', 'Remind me tomorrow at 10', 'en-US', 'TEXT', NULL, 1000),
                       (2, 'MIRA', 'Reminder set.', 'en-US', 'SYSTEM', 'CreateReminder', 2000)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        db.query("SELECT id, messageCount, startedAtEpochMillis, lastMessageAtEpochMillis FROM conversations")
            .use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getLong(0)).isEqualTo(1L)
                assertThat(cursor.getInt(1)).isEqualTo(2)
                assertThat(cursor.getLong(2)).isEqualTo(1000L)
                assertThat(cursor.getLong(3)).isEqualTo(2000L)
            }
        db.query("SELECT id, conversationId, role, text, commandName FROM conversation_messages ORDER BY id")
            .use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getLong(0)).isEqualTo(1L)
                assertThat(cursor.getLong(1)).isEqualTo(1L)
                assertThat(cursor.getString(2)).isEqualTo("ABU")
                assertThat(cursor.getString(3)).isEqualTo("Remind me tomorrow at 10")
                assertThat(cursor.moveToNext()).isTrue()
                assertThat(cursor.getString(4)).isEqualTo("CreateReminder")
                assertThat(cursor.getCount()).isEqualTo(2)
            }
        db.close()
    }

    @Test
    fun `an empty version 1 database migrates without inventing a conversation`() {
        helper.createDatabase(TEST_DB, 1).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        db.query("SELECT COUNT(*) FROM conversations").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getInt(0)).isEqualTo(0)
        }
        db.close()
    }

    @Test
    fun `a vault item written at version 1 is untouched`() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO vault_items (id, title, cipherText, iv, category, createdAtEpochMillis, updatedAtEpochMillis)
                VALUES (3, 'Bank login', X'0102', X'0304', 'CREDENTIAL', 10, 20)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        db.query("SELECT id, title, category FROM vault_items").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getLong(0)).isEqualTo(3L)
            assertThat(cursor.getString(1)).isEqualTo("Bank login")
            assertThat(cursor.getString(2)).isEqualTo("CREDENTIAL")
        }
        db.close()
    }

    @Test
    fun `turns moved from version 1 are findable by full-text search afterwards`() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO conversation_turns (id, role, text, languageTag, source, commandName, createdAtEpochMillis)
                VALUES (1, 'ABU', 'Remind me to call Ahmed about the lease', 'en-US', 'TEXT', NULL, 1000)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        // The rows were copied in before the FTS sync triggers existed, so without an explicit
        // rebuild the transcript survives the migration and becomes permanently unsearchable.
        db.query(
            """
            SELECT m.text FROM conversation_messages m
            JOIN conversation_message_fts ON conversation_message_fts.rowid = m.id
            WHERE conversation_message_fts MATCH 'lease*'
            """.trimIndent()
        ).use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).contains("lease")
        }
        db.close()
    }

    @Test
    fun `a reminder keeps a live task link and loses a dangling one`() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO tasks (
                    id, title, notes, dueAtEpochMillis, priority, isDone,
                    completedAtEpochMillis, origin, createdAtEpochMillis, updatedAtEpochMillis
                ) VALUES (4, 'Call Ahmed', NULL, NULL, 'NORMAL', 0, NULL, 'VOICE', 1, 1)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO reminders (id, title, triggerAtEpochMillis, state, linkedTaskId, origin, createdAtEpochMillis)
                VALUES (1, 'call Ahmed', 1800000000000, 'SCHEDULED', 4, 'VOICE', 1),
                       (2, 'chase the invoice', 1800000000000, 'SCHEDULED', 99, 'VOICE', 1)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        // Version 1 had no constraint on linkedTaskId, so a reminder could already be pointing at
        // a task that was never there. The rebuild nulls those rather than carrying a broken link
        // into a schema that now forbids one.
        db.query("SELECT id, linkedTaskId FROM reminders ORDER BY id").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getLong(0)).isEqualTo(1L)
            assertThat(cursor.getLong(1)).isEqualTo(4L)
            assertThat(cursor.moveToNext()).isTrue()
            assertThat(cursor.getLong(0)).isEqualTo(2L)
            assertThat(cursor.isNull(1)).isTrue()
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "mira-migration-test.db"

        /** A coordinate survives a migration exactly or not at all; this is only REAL's slack. */
        const val TOLERANCE = 0.000001
    }

    // ------------------------------------------------------------------ version 2 to 3

    @Test
    fun `a task written at version 2 survives the rebuild with its id and fields`() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                """
                INSERT INTO tasks (
                    id, title, notes, dueAtEpochMillis, priority, isDone,
                    completedAtEpochMillis, origin, projectId, deletedAtEpochMillis,
                    createdAtEpochMillis, updatedAtEpochMillis
                ) VALUES (
                    7, 'Call the landlord', 'about the lease', 1790000000000, 'HIGH', 0,
                    NULL, 'VOICE', NULL, NULL, 1789000000000, 1789000000000
                )
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        db.query("SELECT id, title, notes, priority, origin, dueAllDay, description FROM tasks").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            // The id matters more than it looks: reminders.linkedTaskId points at it, and a task
            // that came back with a new id would leave that reminder resolving to nothing.
            assertThat(cursor.getLong(0)).isEqualTo(7L)
            assertThat(cursor.getString(1)).isEqualTo("Call the landlord")
            assertThat(cursor.getString(2)).isEqualTo("about the lease")
            assertThat(cursor.getString(3)).isEqualTo("HIGH")
            assertThat(cursor.getString(4)).isEqualTo("VOICE")
            // Tasks written at version 2 could only ever hold an instant, so timed is what they are.
            assertThat(cursor.getInt(5)).isEqualTo(0)
            assertThat(cursor.isNull(6)).isTrue()
        }
        db.close()
    }

    @Test
    fun `a task written at version 2 is still searchable afterwards`() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                """
                INSERT INTO tasks (
                    id, title, notes, dueAtEpochMillis, priority, isDone,
                    completedAtEpochMillis, origin, projectId, deletedAtEpochMillis,
                    createdAtEpochMillis, updatedAtEpochMillis
                ) VALUES (
                    1, 'Send Ahmed the proposal', NULL, NULL, 'NORMAL', 0,
                    NULL, 'MANUAL', NULL, NULL, 1789000000000, 1789000000000
                )
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        // The index is external-content and is NOT retrospective: without the rebuild statement
        // every task Abu already had would be missing from search for ever, silently.
        db.query(
            "SELECT tasks.title FROM task_fts JOIN tasks ON task_fts.rowid = tasks.id " +
                "WHERE task_fts MATCH 'ahmed'"
        ).use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("Send Ahmed the proposal")
        }
        db.close()
    }

    @Test
    fun `the rebuild keeps the indices that make the task lists fast`() {
        helper.createDatabase(TEST_DB, 2).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        // ALTER TABLE RENAME carries the old indices onto the renamed table, so creating them
        // before the drop is a silent no-op and the drop then takes them away. This is the
        // assertion that caught exactly that defect in the 1-to-2 migration.
        val indices = db.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'tasks'"
        ).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        assertThat(indices).containsAtLeast(
            "index_tasks_dueAtEpochMillis",
            "index_tasks_isDone",
            "index_tasks_projectId",
            "index_tasks_clientId",
            "index_tasks_deletedAtEpochMillis",
            "index_tasks_archivedAtEpochMillis",
            "index_tasks_seriesUuid",
        )
        db.close()
    }

    @Test
    fun `a reminder still points at its task after the rebuild`() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                """
                INSERT INTO tasks (
                    id, title, notes, dueAtEpochMillis, priority, isDone,
                    completedAtEpochMillis, origin, projectId, deletedAtEpochMillis,
                    createdAtEpochMillis, updatedAtEpochMillis
                ) VALUES (
                    4, 'Review Rayluxeo', NULL, NULL, 'NORMAL', 0,
                    NULL, 'MANUAL', NULL, NULL, 1789000000000, 1789000000000
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO reminders (
                    id, title, triggerAtEpochMillis, state, linkedTaskId, origin,
                    createdAtEpochMillis
                ) VALUES (2, 'Review Rayluxeo', 1790000000000, 'SCHEDULED', 4, 'MANUAL', 1789000000000)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        // tasks is dropped and rebuilt under a reminder that references it. The link has to come
        // out the other side pointing at the same row, or the reminder fires for nothing.
        db.query("SELECT linkedTaskId FROM reminders WHERE id = 2").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getLong(0)).isEqualTo(4L)
        }
        db.close()
    }

    @Test
    fun `the new task tables arrive and nothing is left behind`() {
        helper.createDatabase(TEST_DB, 2).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        val tables = db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        assertThat(tables).containsAtLeast("tags", "task_tags", "task_attachments")
        assertThat(tables).doesNotContain("tasks_v2")
        db.close()
    }

    // ------------------------------------------------------------------ version 3 to 4

    @Test
    fun `a plain reminder written at version 3 becomes a one-time reminder`() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.execSQL(
                """
                INSERT INTO reminders (
                    id, title, triggerAtEpochMillis, state, linkedTaskId, origin,
                    createdAtEpochMillis
                ) VALUES (5, 'Call the bank', 1790000000000, 'SCHEDULED', NULL, 'VOICE', 1789000000000)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)

        db.query("SELECT id, title, kind, state, origin FROM reminders WHERE id = 5").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getLong(0)).isEqualTo(5L)
            assertThat(cursor.getString(1)).isEqualTo("Call the bank")
            // Nothing is guessed: a reminder Abu made by hand stays one-time.
            assertThat(cursor.getString(2)).isEqualTo("ONE_TIME")
            assertThat(cursor.getString(3)).isEqualTo("SCHEDULED")
            assertThat(cursor.getString(4)).isEqualTo("VOICE")
        }
        db.close()
    }

    @Test
    fun `a reminder that already pointed at a task becomes a task reminder`() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.execSQL(
                """
                INSERT INTO tasks (
                    id, title, description, notes, dueAtEpochMillis, dueAllDay, estimatedMinutes,
                    priority, isDone, completedAtEpochMillis, origin, projectId, clientId,
                    recurrenceRule, seriesUuid, archivedAtEpochMillis, deletedAtEpochMillis,
                    createdAtEpochMillis, updatedAtEpochMillis
                ) VALUES (
                    9, 'Send the proposal', NULL, NULL, NULL, 0, NULL,
                    'NORMAL', 0, NULL, 'MANUAL', NULL, NULL,
                    NULL, NULL, NULL, NULL, 1789000000000, 1789000000000
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO reminders (
                    id, title, triggerAtEpochMillis, state, linkedTaskId, origin,
                    createdAtEpochMillis
                ) VALUES (6, 'Send the proposal', 1790000000000, 'SCHEDULED', 9, 'MANUAL', 1789000000000)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)

        // The kind is the word for what the row already was, and the link has to survive the
        // rebuild or the reminder fires for a task it can no longer name.
        db.query("SELECT kind, linkedTaskId FROM reminders WHERE id = 6").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("TASK")
            assertThat(cursor.getLong(1)).isEqualTo(9L)
        }
        db.close()
    }

    @Test
    fun `the reminder rebuild keeps its indices and leaves nothing behind`() {
        helper.createDatabase(TEST_DB, 3).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)

        val indices = db.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'reminders'"
        ).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        assertThat(indices).containsAtLeast(
            "index_reminders_triggerAtEpochMillis",
            "index_reminders_linkedTaskId",
            "index_reminders_linkedInvoiceId",
            "index_reminders_kind",
            "index_reminders_state",
        )

        val tables = db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        assertThat(tables).containsAtLeast("reminder_places", "alarms")
        assertThat(tables).doesNotContain("reminders_v3")
        db.close()
    }

    @Test
    fun `an alarm record still points at its reminder after the rebuild`() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.execSQL(
                """
                INSERT INTO reminders (
                    id, title, triggerAtEpochMillis, state, linkedTaskId, origin,
                    createdAtEpochMillis
                ) VALUES (3, 'Stand-up', 1790000000000, 'SCHEDULED', NULL, 'MANUAL', 1789000000000)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO alarm_records (
                    id, reminderId, label, triggerAtEpochMillis, isExact, state,
                    firedAtEpochMillis, requestCode, createdAtEpochMillis, updatedAtEpochMillis
                ) VALUES (1, 3, 'Stand-up', 1790000000000, 1, 'SCHEDULED', NULL, 4242,
                          1789000000000, 1789000000000)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)

        // reminders is dropped and rebuilt underneath a row that references it. The OS-side
        // ledger has to come out pointing at the same reminder, or Mira cannot cancel the alarm.
        db.query("SELECT reminderId, requestCode FROM alarm_records WHERE id = 1").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getLong(0)).isEqualTo(3L)
            assertThat(cursor.getInt(1)).isEqualTo(4242)
        }
        db.close()
    }

    // ------------------------------------------------------------------ version 4 to 5

    @Test
    fun `version 4 opens at version 5 with the place directory in place`() {
        helper.createDatabase(TEST_DB, 4).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)

        val tables = db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        assertThat(tables).contains("saved_places")
        // Nothing is rebuilt by this migration, so the tables it sits beside must be untouched.
        assertThat(tables).containsAtLeast("reminders", "reminder_places", "alarms")

        val indices = db.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'saved_places'"
        ).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        assertThat(indices).containsAtLeast(
            "index_saved_places_labelKey",
            "index_saved_places_label",
        )
        db.close()
    }

    @Test
    fun `a place reminder written at version 4 is untouched by the directory arriving`() {
        helper.createDatabase(TEST_DB, 4).use { db ->
            db.execSQL(
                """
                INSERT INTO reminders (
                    id, title, triggerAtEpochMillis, state, kind, recurrenceRule,
                    snoozedUntilEpochMillis, linkedTaskId, linkedInvoiceId, linkedClientId,
                    linkedProjectId, linkedSubscriptionId, origin, createdAtEpochMillis
                ) VALUES (11, 'Buy printer ink', 1790000000000, 'SCHEDULED', 'PLACE', NULL,
                          NULL, NULL, NULL, NULL, NULL, NULL, 'VOICE', 1789000000000)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO reminder_places (
                    id, reminderId, label, latitude, longitude, radiusMeters, trigger,
                    isRegistered, createdAtEpochMillis, updatedAtEpochMillis
                ) VALUES (4, 11, 'the office', 23.8103, 90.4125, 150.0, 'ENTER', 1,
                          1789000000000, 1789000000000)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)

        // A reminder already waiting at a place keeps its OWN copy of the coordinate. The new
        // directory is a name lookup for making new ones and must not disturb an armed alert.
        db.query(
            "SELECT reminderId, label, latitude, longitude, isRegistered FROM reminder_places WHERE id = 4"
        ).use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getLong(0)).isEqualTo(11L)
            assertThat(cursor.getString(1)).isEqualTo("the office")
            assertThat(cursor.getDouble(2)).isWithin(TOLERANCE).of(23.8103)
            assertThat(cursor.getDouble(3)).isWithin(TOLERANCE).of(90.4125)
            assertThat(cursor.getInt(4)).isEqualTo(1)
        }
        db.close()
    }

    @Test
    fun `two places cannot share a name`() {
        helper.createDatabase(TEST_DB, 4).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)

        db.execSQL(
            """
            INSERT INTO saved_places (
                id, label, labelKey, latitude, longitude, radiusMeters,
                createdAtEpochMillis, updatedAtEpochMillis
            ) VALUES (1, 'Office', 'office', 23.8103, 90.4125, 150.0, 1789000000000, 1789000000000)
            """.trimIndent()
        )

        // The unique key is what makes "the office" resolve to exactly one place. Without it a
        // second row under the same name would win or lose at random and attach a reminder to
        // whichever coordinate SQLite happened to return.
        val duplicate = runCatching {
            db.execSQL(
                """
                INSERT INTO saved_places (
                    id, label, labelKey, latitude, longitude, radiusMeters,
                    createdAtEpochMillis, updatedAtEpochMillis
                ) VALUES (2, 'the office', 'office', 1.0, 2.0, 150.0, 1789000000000, 1789000000000)
                """.trimIndent()
            )
        }
        assertThat(duplicate.isFailure).isTrue()
        db.close()
    }

    // ------------------------------------------------------------------ version 5 to 6

    @Test
    fun `version 5 opens at version 6 with tags and handwriting in place`() {
        helper.createDatabase(TEST_DB, 5).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6)

        val tables = db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        assertThat(tables).containsAtLeast("note_tags", "ink_pages")
        assertThat(tables).doesNotContain("notes_v5")

        val indices = db.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'notes'"
        ).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        // Created AFTER the drop. Before it they would have collided with the renamed table's
        // own indices, CREATE INDEX IF NOT EXISTS would have quietly done nothing, and the drop
        // would have taken them away — leaving a correct-looking table with no indices at all.
        assertThat(indices).containsAtLeast(
            "index_notes_clientId",
            "index_notes_projectId",
            "index_notes_isPinned",
            "index_notes_updatedAtEpochMillis",
            "index_notes_deletedAtEpochMillis",
            "index_notes_taskId",
            "index_notes_archivedAtEpochMillis",
        )
        db.close()
    }

    @Test
    fun `a note written at version 5 survives the rebuild with its id and fields`() {
        helper.createDatabase(TEST_DB, 5).use { db ->
            db.execSQL(
                """
                INSERT INTO notes (
                    id, title, body, isPinned, clientId, projectId, languageTag,
                    deletedAtEpochMillis, createdAtEpochMillis, updatedAtEpochMillis
                ) VALUES (12, 'Printer', 'buy ink for the studio printer', 1, NULL, NULL, 'en-US',
                          NULL, 1700000000000, 1700000000000)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6)

        db.query(
            "SELECT id, title, body, isPinned, taskId, archivedAtEpochMillis FROM notes WHERE id = 12"
        ).use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getLong(0)).isEqualTo(12L)
            assertThat(cursor.getString(1)).isEqualTo("Printer")
            assertThat(cursor.getString(2)).isEqualTo("buy ink for the studio printer")
            assertThat(cursor.getInt(3)).isEqualTo(1)
            // Nothing is guessed: a note written before these columns existed arrives linked to
            // no task and unarchived, which is what it already was.
            assertThat(cursor.isNull(4)).isTrue()
            assertThat(cursor.isNull(5)).isTrue()
        }
        db.close()
    }

    @Test
    fun `a note written at version 5 is still findable by search afterwards`() {
        helper.createDatabase(TEST_DB, 5).use { db ->
            db.execSQL(
                """
                INSERT INTO notes (
                    id, title, body, isPinned, clientId, projectId, languageTag,
                    deletedAtEpochMillis, createdAtEpochMillis, updatedAtEpochMillis
                ) VALUES (21, 'Studio', 'replace the laminator roller', 0, NULL, NULL, 'en-US',
                          NULL, 1700000000000, 1700000000000)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6)

        // The whole reason the migration ends with a 'rebuild'. An external-content FTS index is
        // NOT retrospective: the note survives the rebuild perfectly and becomes permanently
        // unfindable, which looks like nothing at all went wrong until Abu searches for it.
        db.query(
            "SELECT content.id FROM note_fts JOIN notes AS content ON content.id = note_fts.docid " +
                "WHERE note_fts MATCH 'laminator' AND content.deletedAtEpochMillis IS NULL"
        ).use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getLong(0)).isEqualTo(21L)
        }
        db.close()
    }

    @Test
    fun `the search triggers are back on the rebuilt table`() {
        helper.createDatabase(TEST_DB, 5).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6)

        val triggers = db.query(
            "SELECT name FROM sqlite_master WHERE type = 'trigger' AND tbl_name = 'notes'"
        ).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        assertThat(triggers).containsAtLeast(
            "room_fts_content_sync_note_fts_BEFORE_UPDATE",
            "room_fts_content_sync_note_fts_BEFORE_DELETE",
            "room_fts_content_sync_note_fts_AFTER_UPDATE",
            "room_fts_content_sync_note_fts_AFTER_INSERT",
        )

        // Proves them live rather than merely present: the migration drops these four before the
        // rename so the teardown of the old table cannot delete from the index by a rowid that
        // now belongs to a different note. A note written AFTER the migration has to be indexed
        // by the recreated triggers, or every note Abu writes from here on is unsearchable.
        db.execSQL(
            """
            INSERT INTO notes (
                id, title, body, isPinned, clientId, projectId, taskId, languageTag,
                archivedAtEpochMillis, deletedAtEpochMillis, createdAtEpochMillis, updatedAtEpochMillis
            ) VALUES (33, 'Fresh', 'chase the anodising quote', 0, NULL, NULL, NULL, 'en-US',
                      NULL, NULL, 1700000000000, 1700000000000)
            """.trimIndent()
        )
        db.query(
            "SELECT content.id FROM note_fts JOIN notes AS content ON content.id = note_fts.docid " +
                "WHERE note_fts MATCH 'anodising'"
        ).use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getLong(0)).isEqualTo(33L)
        }
        db.close()
    }

    @Test
    fun `a note binned at version 5 does not walk back into search`() {
        helper.createDatabase(TEST_DB, 5).use { db ->
            db.execSQL(
                """
                INSERT INTO notes (
                    id, title, body, isPinned, clientId, projectId, languageTag,
                    deletedAtEpochMillis, createdAtEpochMillis, updatedAtEpochMillis
                ) VALUES (44, 'Old', 'cancel the rhodium order', 0, NULL, NULL, 'en-US',
                          1700000001000, 1700000000000, 1700000001000)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6)

        // The index shadows binned rows too, so the soft-delete filter has to sit on the CONTENT
        // table. The rebuild re-indexes everything including the bin; only the join keeps it out.
        db.query(
            "SELECT content.id FROM note_fts JOIN notes AS content ON content.id = note_fts.docid " +
                "WHERE note_fts MATCH 'rhodium' AND content.deletedAtEpochMillis IS NULL"
        ).use { cursor ->
            assertThat(cursor.moveToFirst()).isFalse()
        }
        db.close()
    }

    @Test
    fun `handwriting survives its note being deleted only as far as it should`() {
        helper.createDatabase(TEST_DB, 5).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6)
        db.execSQL("PRAGMA foreign_keys = ON")

        db.execSQL(
            """
            INSERT INTO notes (
                id, title, body, isPinned, clientId, projectId, taskId, languageTag,
                archivedAtEpochMillis, deletedAtEpochMillis, createdAtEpochMillis, updatedAtEpochMillis
            ) VALUES (55, NULL, '', 0, NULL, NULL, NULL, 'bn-BD', NULL, NULL, 1700000000000, 1700000000000)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO ink_pages (
                id, noteId, strokeFileId, imageFileId, widthPx, heightPx, strokeCount,
                createdAtEpochMillis, updatedAtEpochMillis
            ) VALUES (1, 55, NULL, NULL, 1440, 3088, 34, 1700000000000, 1700000000000)
            """.trimIndent()
        )

        // CASCADE from notes: a page of handwriting with no note is meaningless, which is exactly
        // the case section 8 says CASCADE is for. A note with an empty body and only ink is a
        // legitimate note, which is why the row above has no title and no body.
        db.execSQL("DELETE FROM notes WHERE id = 55")
        db.query("SELECT COUNT(*) FROM ink_pages WHERE noteId = 55").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getInt(0)).isEqualTo(0)
        }
        db.close()
    }

    // ------------------------------------------------------------------ version 9 to 10

    @Test
    fun `an automation written at version 9 survives the migration to 10`() {
        helper.createDatabase(TEST_DB, 9).use { db ->
            db.execSQL(
                """
                INSERT INTO automations (
                    id, name, description, trigger, triggerConfigJson, actionConfigJson,
                    isEnabled, lastRunAtEpochMillis, deletedAtEpochMillis,
                    createdAtEpochMillis, updatedAtEpochMillis
                ) VALUES (
                    3, 'Overdue alert', NULL, 'INVOICE_OVERDUE', '{}',
                    '{"kind":"notify","title":"Overdue"}', 1, NULL, NULL,
                    1700000000000, 1700000000000
                )
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 10, true, MIGRATION_9_10)

        db.query("SELECT name, trigger FROM automations WHERE id = 3").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("Overdue alert")
            assertThat(cursor.getString(1)).isEqualTo("INVOICE_OVERDUE")
        }
        db.close()
    }

    @Test
    fun `the fire ledger is created at version 10 and cascades from its automation`() {
        helper.createDatabase(TEST_DB, 9).use { db ->
            db.execSQL(
                """
                INSERT INTO automations (
                    id, name, description, trigger, triggerConfigJson, actionConfigJson,
                    isEnabled, lastRunAtEpochMillis, deletedAtEpochMillis,
                    createdAtEpochMillis, updatedAtEpochMillis
                ) VALUES (
                    3, 'Overdue alert', NULL, 'INVOICE_OVERDUE', '{}',
                    '{"kind":"notify","title":"Overdue"}', 1, NULL, NULL,
                    1700000000000, 1700000000000
                )
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 10, true, MIGRATION_9_10)
        db.execSQL("PRAGMA foreign_keys = ON")

        db.execSQL(
            "INSERT INTO automation_record_fires (automationId, source, recordId, " +
                "occurrenceFromEpochMillis, firedAtEpochMillis) VALUES (3, 'INVOICE', 7, 1700000000000, 1700000000001)",
        )
        // The unique dedup index must reject the same occurrence a second time.
        try {
            db.execSQL(
                "INSERT INTO automation_record_fires (automationId, source, recordId, " +
                    "occurrenceFromEpochMillis, firedAtEpochMillis) VALUES (3, 'INVOICE', 7, 1700000000000, 1700000000002)",
            )
            throw AssertionError("The unique index should have rejected a duplicate occurrence")
        } catch (expected: android.database.sqlite.SQLiteConstraintException) {
            // expected
        }

        // CASCADE from automations: binning the rule for good takes its ledger with it.
        db.execSQL("DELETE FROM automations WHERE id = 3")
        db.query("SELECT COUNT(*) FROM automation_record_fires WHERE automationId = 3").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getInt(0)).isEqualTo(0)
        }
        db.close()
    }
}

