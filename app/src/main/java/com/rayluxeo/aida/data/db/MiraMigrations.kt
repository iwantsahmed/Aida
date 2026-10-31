package com.rayluxeo.aida.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every migration Mira has ever needed, in order.
 *
 * There is no destructive fallback. Abu's tasks, vault, conversation history and — from version 2
 * — his invoices are not reproducible, so a schema change that arrives without a migration must
 * fail loudly at startup rather than quietly emptying the database.
 *
 * Each migration's SQL is taken verbatim from the schema Room exported for that version, rather
 * than retyped. A migration that disagrees with the generated schema by one column type passes
 * every test that does not open a real old database, and then fails on the one phone that has
 * the old data.
 */
object MiraMigrations {

    val all: Array<Migration> = arrayOf(
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
        MIGRATION_8_9, MIGRATION_9_10,
    )
}

/**
 * Version 10 adds the record-driven automation fire ledger ([automation_record_fires]): one new
 * table recording which record occurrence an automation has already fired for, so the daily check
 * does not fire the same rule twice for the same fact. CASCADE-deleted with its automation, with a
 * unique dedup index on (automationId, source, recordId, occurrenceFromEpochMillis) and a plain
 * index on firedAtEpochMillis for pruning. A new table only, so nothing Abu already has is touched.
 * The DDL is taken verbatim from the schema Room exported for version 10.
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `automation_record_fires` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`automationId` INTEGER NOT NULL, `source` TEXT NOT NULL, `recordId` INTEGER NOT NULL, " +
                "`occurrenceFromEpochMillis` INTEGER NOT NULL, `firedAtEpochMillis` INTEGER NOT NULL, " +
                "FOREIGN KEY(`automationId`) REFERENCES `automations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_automation_record_fires_automationId_source_recordId_occurrenceFromEpochMillis` " +
                "ON `automation_record_fires` (`automationId`, `source`, `recordId`, `occurrenceFromEpochMillis`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_automation_record_fires_firedAtEpochMillis` " +
                "ON `automation_record_fires` (`firedAtEpochMillis`)",
        )
    }
}

/**
 * Version 9 adds the Salah feature: a single [prayer_settings] configuration row and the
 * [prayer_tracking_entry] prayer log (one row per prayer per day, Tahajjud included as its own
 * prayer value). Both are new tables, so nothing Abu already has is touched. The DDL is taken
 * verbatim from the schema Room exported for version 9.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `prayer_settings` (`id` INTEGER NOT NULL, `latitude` REAL, " +
                "`longitude` REAL, `locationName` TEXT, `timezone` TEXT, `calculationMethod` TEXT NOT NULL, " +
                "`madhab` TEXT NOT NULL, `highLatitudeRule` TEXT NOT NULL, `customFajrAngle` REAL, " +
                "`customIshaAngle` REAL, `fajrAdjustment` INTEGER NOT NULL, `dhuhrAdjustment` INTEGER NOT NULL, " +
                "`asrAdjustment` INTEGER NOT NULL, `maghribAdjustment` INTEGER NOT NULL, `ishaAdjustment` INTEGER NOT NULL, " +
                "`prayerNotificationsEnabled` INTEGER NOT NULL, `notifyFajr` INTEGER NOT NULL, `notifyDhuhr` INTEGER NOT NULL, " +
                "`notifyAsr` INTEGER NOT NULL, `notifyMaghrib` INTEGER NOT NULL, `notifyIsha` INTEGER NOT NULL, " +
                "`checkInEnabled` INTEGER NOT NULL, `checkInFajr` INTEGER NOT NULL, `checkInDhuhr` INTEGER NOT NULL, " +
                "`checkInAsr` INTEGER NOT NULL, `checkInMaghrib` INTEGER NOT NULL, `checkInIsha` INTEGER NOT NULL, " +
                "`defaultAskDelayMinutes` INTEGER NOT NULL, `tahajjudReminderEnabled` INTEGER NOT NULL, " +
                "`tahajjudReminderMode` TEXT NOT NULL, `tahajjudCustomMinutes` INTEGER NOT NULL, " +
                "`createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `prayer_tracking_entry` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`localDateEpochDay` INTEGER NOT NULL, `prayer` TEXT NOT NULL, `scheduledAtEpochMillis` INTEGER, " +
                "`status` TEXT NOT NULL, `prayedAtEpochMillis` INTEGER, `trackingSource` TEXT NOT NULL, `notes` TEXT, " +
                "`createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_prayer_tracking_entry_localDateEpochDay` " +
                "ON `prayer_tracking_entry` (`localDateEpochDay`)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_prayer_tracking_entry_localDateEpochDay_prayer` " +
                "ON `prayer_tracking_entry` (`localDateEpochDay`, `prayer`)",
        )
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `memories` ADD COLUMN `category` TEXT NOT NULL DEFAULT 'GENERAL'")
        db.execSQL("ALTER TABLE `memories` ADD COLUMN `isPinned` INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `vault_items` ADD COLUMN `fileType` TEXT NOT NULL DEFAULT 'OTHER'")
        db.execSQL("ALTER TABLE `vault_items` ADD COLUMN `folder` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE `vault_items` ADD COLUMN `tagsCsv` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE `vault_items` ADD COLUMN `clientId` INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE `vault_items` ADD COLUMN `projectId` INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE `vault_items` ADD COLUMN `invoiceId` INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE `vault_items` ADD COLUMN `appPrivateFilePath` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE `vault_items` ADD COLUMN `metadataJson` TEXT DEFAULT NULL")
    }
}

/**
 * Version 1 held tasks, reminders, the vault and a flat list of conversation turns. Version 2
 * adds the full record set — clients, projects, invoices, quotes, money, notes, memory, files,
 * automations, meetings — and reshapes the transcript into conversations and their messages.
 *
 * Three things here are not plain table creation:
 *
 * 1. `tasks` and `reminders` are both rebuilt rather than altered. Each gains a foreign key —
 *    tasks to projects, reminders to tasks — and SQLite cannot add one with ALTER TABLE. A
 *    reminder whose task no longer exists has its link nulled on the way across, so the new
 *    constraint holds for data written before it existed. The rows are copied by explicit column list so a future
 *    column order change cannot silently shift Abu's data into the wrong fields.
 * 2. `task_fts` is dropped and rebuilt, because an external-content FTS index belongs to the
 *    table it shadows and that table no longer exists by the time the copy is done.
 * 3. Every existing conversation turn is moved into a single thread rather than discarded. The
 *    ids are preserved, so nothing that referenced a turn is left pointing at the wrong message.
 *    The thread insert is guarded with HAVING, not WHERE: an aggregate SELECT with no GROUP BY
 *    returns one row however few rows it read, so a WHERE guard would still have created an empty
 *    conversation on a database that had never been spoken to.
 */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SupportSQLiteDatabase) {
        STATEMENTS.forEach(connection::execSQL)
    }

    private val STATEMENTS = listOf(
            "CREATE TABLE IF NOT EXISTS `clients` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `name` TEXT NOT NULL, `companyName` TEXT, `email` TEXT, `phone` TEXT, `addressLine` TEXT, `notes` TEXT, `defaultCurrencyCode` TEXT, `defaultRateMinor` INTEGER, `isArchived` INTEGER NOT NULL, `deletedAtEpochMillis` INTEGER, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_clients_uuid` ON `clients` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_clients_name` ON `clients` (`name`)",
            "CREATE INDEX IF NOT EXISTS `index_clients_isArchived` ON `clients` (`isArchived`)",
            "CREATE INDEX IF NOT EXISTS `index_clients_deletedAtEpochMillis` ON `clients` (`deletedAtEpochMillis`)",
            "CREATE VIRTUAL TABLE IF NOT EXISTS `client_fts` USING FTS4(`name` TEXT NOT NULL, `companyName` TEXT, `notes` TEXT, content=`clients`)",
            "CREATE TABLE IF NOT EXISTS `projects` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `clientId` INTEGER, `name` TEXT NOT NULL, `description` TEXT, `status` TEXT NOT NULL, `startedOnEpochDay` INTEGER, `dueOnEpochDay` INTEGER, `budgetMinor` INTEGER, `currencyCode` TEXT NOT NULL, `deletedAtEpochMillis` INTEGER, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, FOREIGN KEY(`clientId`) REFERENCES `clients`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_projects_uuid` ON `projects` (`uuid`)",
            "CREATE INDEX IF NOT EXISTS `index_projects_clientId` ON `projects` (`clientId`)",
            "CREATE INDEX IF NOT EXISTS `index_projects_status` ON `projects` (`status`)",
            "CREATE INDEX IF NOT EXISTS `index_projects_dueOnEpochDay` ON `projects` (`dueOnEpochDay`)",
            "CREATE INDEX IF NOT EXISTS `index_projects_deletedAtEpochMillis` ON `projects` (`deletedAtEpochMillis`)",
            "CREATE VIRTUAL TABLE IF NOT EXISTS `project_fts` USING FTS4(`name` TEXT NOT NULL, `description` TEXT, content=`projects`)",
            "ALTER TABLE `tasks` RENAME TO `tasks_v1`",
            "CREATE TABLE IF NOT EXISTS `tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `notes` TEXT, `dueAtEpochMillis` INTEGER, `priority` TEXT NOT NULL, `isDone` INTEGER NOT NULL, `completedAtEpochMillis` INTEGER, `origin` TEXT NOT NULL, `projectId` INTEGER, `deletedAtEpochMillis` INTEGER, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            """INSERT INTO `tasks` (
        `id`, `title`, `notes`, `dueAtEpochMillis`, `priority`, `isDone`,
        `completedAtEpochMillis`, `origin`, `projectId`, `deletedAtEpochMillis`,
        `createdAtEpochMillis`, `updatedAtEpochMillis`
    ) SELECT
        `id`, `title`, `notes`, `dueAtEpochMillis`, `priority`, `isDone`,
        `completedAtEpochMillis`, `origin`, NULL, NULL,
        `createdAtEpochMillis`, `updatedAtEpochMillis`
    FROM `tasks_v1`""",
            "DROP TABLE `tasks_v1`",
            "CREATE INDEX IF NOT EXISTS `index_tasks_dueAtEpochMillis` ON `tasks` (`dueAtEpochMillis`)",
            "CREATE INDEX IF NOT EXISTS `index_tasks_isDone` ON `tasks` (`isDone`)",
            "CREATE INDEX IF NOT EXISTS `index_tasks_projectId` ON `tasks` (`projectId`)",
            "CREATE INDEX IF NOT EXISTS `index_tasks_deletedAtEpochMillis` ON `tasks` (`deletedAtEpochMillis`)",
            "DROP TABLE IF EXISTS `task_fts`",
            "CREATE VIRTUAL TABLE IF NOT EXISTS `task_fts` USING FTS4(`title` TEXT NOT NULL, `notes` TEXT, content=`tasks`)",
            "INSERT INTO `task_fts`(`task_fts`) VALUES('rebuild')",
            "ALTER TABLE `reminders` RENAME TO `reminders_v1`",
            "CREATE TABLE IF NOT EXISTS `reminders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `triggerAtEpochMillis` INTEGER NOT NULL, `state` TEXT NOT NULL, `linkedTaskId` INTEGER, `origin` TEXT NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, FOREIGN KEY(`linkedTaskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            """INSERT INTO `reminders` (
        `id`, `title`, `triggerAtEpochMillis`, `state`, `linkedTaskId`, `origin`,
        `createdAtEpochMillis`
    ) SELECT
        `id`, `title`, `triggerAtEpochMillis`, `state`,
        CASE WHEN `linkedTaskId` IN (SELECT `id` FROM `tasks`) THEN `linkedTaskId` ELSE NULL END,
        `origin`, `createdAtEpochMillis`
    FROM `reminders_v1`""",
            "DROP TABLE `reminders_v1`",
            "CREATE INDEX IF NOT EXISTS `index_reminders_triggerAtEpochMillis` ON `reminders` (`triggerAtEpochMillis`)",
            "CREATE INDEX IF NOT EXISTS `index_reminders_linkedTaskId` ON `reminders` (`linkedTaskId`)",
            "CREATE TABLE IF NOT EXISTS `subtasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `taskId` INTEGER NOT NULL, `title` TEXT NOT NULL, `isDone` INTEGER NOT NULL, `completedAtEpochMillis` INTEGER, `position` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_subtasks_taskId` ON `subtasks` (`taskId`)",
            "CREATE TABLE IF NOT EXISTS `alarm_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `reminderId` INTEGER, `label` TEXT NOT NULL, `triggerAtEpochMillis` INTEGER NOT NULL, `isExact` INTEGER NOT NULL, `state` TEXT NOT NULL, `firedAtEpochMillis` INTEGER, `requestCode` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, FOREIGN KEY(`reminderId`) REFERENCES `reminders`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE INDEX IF NOT EXISTS `index_alarm_records_reminderId` ON `alarm_records` (`reminderId`)",
            "CREATE INDEX IF NOT EXISTS `index_alarm_records_requestCode` ON `alarm_records` (`requestCode`)",
            "CREATE INDEX IF NOT EXISTS `index_alarm_records_state` ON `alarm_records` (`state`)",
            "CREATE INDEX IF NOT EXISTS `index_alarm_records_triggerAtEpochMillis` ON `alarm_records` (`triggerAtEpochMillis`)",
            "CREATE TABLE IF NOT EXISTS `timer_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `label` TEXT NOT NULL, `durationMillis` INTEGER NOT NULL, `startedAtEpochMillis` INTEGER NOT NULL, `endsAtEpochMillis` INTEGER NOT NULL, `state` TEXT NOT NULL, `remainingMillisWhenPaused` INTEGER, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_timer_records_state` ON `timer_records` (`state`)",
            "CREATE INDEX IF NOT EXISTS `index_timer_records_endsAtEpochMillis` ON `timer_records` (`endsAtEpochMillis`)",
            "CREATE TABLE IF NOT EXISTS `user_profile` (`id` INTEGER NOT NULL, `displayName` TEXT NOT NULL, `businessName` TEXT, `email` TEXT, `phone` TEXT, `addressLine` TEXT, `taxId` TEXT, `defaultCurrencyCode` TEXT NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `app_settings` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`key`))",
            "CREATE TABLE IF NOT EXISTS `activity_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `category` TEXT NOT NULL, `action` TEXT NOT NULL, `summary` TEXT NOT NULL, `subjectTable` TEXT, `subjectId` INTEGER, `occurredAtEpochMillis` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_activity_log_occurredAtEpochMillis` ON `activity_log` (`occurredAtEpochMillis`)",
            "CREATE INDEX IF NOT EXISTS `index_activity_log_category` ON `activity_log` (`category`)",
            "CREATE INDEX IF NOT EXISTS `index_activity_log_subjectTable_subjectId` ON `activity_log` (`subjectTable`, `subjectId`)",
            "CREATE TABLE IF NOT EXISTS `project_milestones` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `projectId` INTEGER NOT NULL, `title` TEXT NOT NULL, `dueOnEpochDay` INTEGER, `isComplete` INTEGER NOT NULL, `completedAtEpochMillis` INTEGER, `position` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_project_milestones_projectId` ON `project_milestones` (`projectId`)",
            "CREATE INDEX IF NOT EXISTS `index_project_milestones_dueOnEpochDay` ON `project_milestones` (`dueOnEpochDay`)",
            "CREATE TABLE IF NOT EXISTS `invoices` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `number` TEXT NOT NULL, `clientId` INTEGER NOT NULL, `projectId` INTEGER, `status` TEXT NOT NULL, `issuedOnEpochDay` INTEGER NOT NULL, `dueOnEpochDay` INTEGER NOT NULL, `currencyCode` TEXT NOT NULL, `subtotalMinor` INTEGER NOT NULL, `discountMinor` INTEGER NOT NULL, `taxMinor` INTEGER NOT NULL, `totalMinor` INTEGER NOT NULL, `amountPaidMinor` INTEGER NOT NULL, `notes` TEXT, `deletedAtEpochMillis` INTEGER, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, FOREIGN KEY(`clientId`) REFERENCES `clients`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_invoices_uuid` ON `invoices` (`uuid`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_invoices_number` ON `invoices` (`number`)",
            "CREATE INDEX IF NOT EXISTS `index_invoices_clientId` ON `invoices` (`clientId`)",
            "CREATE INDEX IF NOT EXISTS `index_invoices_projectId` ON `invoices` (`projectId`)",
            "CREATE INDEX IF NOT EXISTS `index_invoices_status` ON `invoices` (`status`)",
            "CREATE INDEX IF NOT EXISTS `index_invoices_dueOnEpochDay` ON `invoices` (`dueOnEpochDay`)",
            "CREATE INDEX IF NOT EXISTS `index_invoices_deletedAtEpochMillis` ON `invoices` (`deletedAtEpochMillis`)",
            "CREATE TABLE IF NOT EXISTS `invoice_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `invoiceId` INTEGER NOT NULL, `description` TEXT NOT NULL, `quantityMilli` INTEGER NOT NULL, `unitPriceMinor` INTEGER NOT NULL, `taxRateBasisPoints` INTEGER NOT NULL, `lineTotalMinor` INTEGER NOT NULL, `position` INTEGER NOT NULL, FOREIGN KEY(`invoiceId`) REFERENCES `invoices`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_invoice_items_invoiceId` ON `invoice_items` (`invoiceId`)",
            "CREATE TABLE IF NOT EXISTS `quotes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `number` TEXT NOT NULL, `clientId` INTEGER NOT NULL, `projectId` INTEGER, `status` TEXT NOT NULL, `issuedOnEpochDay` INTEGER NOT NULL, `validUntilEpochDay` INTEGER, `currencyCode` TEXT NOT NULL, `subtotalMinor` INTEGER NOT NULL, `discountMinor` INTEGER NOT NULL, `taxMinor` INTEGER NOT NULL, `totalMinor` INTEGER NOT NULL, `convertedInvoiceId` INTEGER, `notes` TEXT, `deletedAtEpochMillis` INTEGER, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, FOREIGN KEY(`clientId`) REFERENCES `clients`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`convertedInvoiceId`) REFERENCES `invoices`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_quotes_uuid` ON `quotes` (`uuid`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_quotes_number` ON `quotes` (`number`)",
            "CREATE INDEX IF NOT EXISTS `index_quotes_clientId` ON `quotes` (`clientId`)",
            "CREATE INDEX IF NOT EXISTS `index_quotes_projectId` ON `quotes` (`projectId`)",
            "CREATE INDEX IF NOT EXISTS `index_quotes_convertedInvoiceId` ON `quotes` (`convertedInvoiceId`)",
            "CREATE INDEX IF NOT EXISTS `index_quotes_status` ON `quotes` (`status`)",
            "CREATE INDEX IF NOT EXISTS `index_quotes_deletedAtEpochMillis` ON `quotes` (`deletedAtEpochMillis`)",
            "CREATE TABLE IF NOT EXISTS `quote_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `quoteId` INTEGER NOT NULL, `description` TEXT NOT NULL, `quantityMilli` INTEGER NOT NULL, `unitPriceMinor` INTEGER NOT NULL, `taxRateBasisPoints` INTEGER NOT NULL, `lineTotalMinor` INTEGER NOT NULL, `position` INTEGER NOT NULL, FOREIGN KEY(`quoteId`) REFERENCES `quotes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_quote_items_quoteId` ON `quote_items` (`quoteId`)",
            "CREATE TABLE IF NOT EXISTS `payments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `invoiceId` INTEGER, `clientId` INTEGER, `amountMinor` INTEGER NOT NULL, `currencyCode` TEXT NOT NULL, `method` TEXT NOT NULL, `receivedAtEpochMillis` INTEGER NOT NULL, `reference` TEXT, `note` TEXT, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, FOREIGN KEY(`invoiceId`) REFERENCES `invoices`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`clientId`) REFERENCES `clients`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE INDEX IF NOT EXISTS `index_payments_invoiceId` ON `payments` (`invoiceId`)",
            "CREATE INDEX IF NOT EXISTS `index_payments_clientId` ON `payments` (`clientId`)",
            "CREATE INDEX IF NOT EXISTS `index_payments_receivedAtEpochMillis` ON `payments` (`receivedAtEpochMillis`)",
            "CREATE TABLE IF NOT EXISTS `expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `description` TEXT NOT NULL, `category` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `currencyCode` TEXT NOT NULL, `spentAtEpochMillis` INTEGER NOT NULL, `clientId` INTEGER, `projectId` INTEGER, `receiptFileId` INTEGER, `isBillable` INTEGER NOT NULL, `deletedAtEpochMillis` INTEGER, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, FOREIGN KEY(`clientId`) REFERENCES `clients`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`receiptFileId`) REFERENCES `files`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE INDEX IF NOT EXISTS `index_expenses_clientId` ON `expenses` (`clientId`)",
            "CREATE INDEX IF NOT EXISTS `index_expenses_projectId` ON `expenses` (`projectId`)",
            "CREATE INDEX IF NOT EXISTS `index_expenses_receiptFileId` ON `expenses` (`receiptFileId`)",
            "CREATE INDEX IF NOT EXISTS `index_expenses_spentAtEpochMillis` ON `expenses` (`spentAtEpochMillis`)",
            "CREATE INDEX IF NOT EXISTS `index_expenses_category` ON `expenses` (`category`)",
            "CREATE INDEX IF NOT EXISTS `index_expenses_deletedAtEpochMillis` ON `expenses` (`deletedAtEpochMillis`)",
            "CREATE TABLE IF NOT EXISTS `income_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `source` TEXT NOT NULL, `description` TEXT, `amountMinor` INTEGER NOT NULL, `currencyCode` TEXT NOT NULL, `receivedAtEpochMillis` INTEGER NOT NULL, `clientId` INTEGER, `projectId` INTEGER, `paymentId` INTEGER, `deletedAtEpochMillis` INTEGER, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, FOREIGN KEY(`clientId`) REFERENCES `clients`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`paymentId`) REFERENCES `payments`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE INDEX IF NOT EXISTS `index_income_entries_clientId` ON `income_entries` (`clientId`)",
            "CREATE INDEX IF NOT EXISTS `index_income_entries_projectId` ON `income_entries` (`projectId`)",
            "CREATE INDEX IF NOT EXISTS `index_income_entries_paymentId` ON `income_entries` (`paymentId`)",
            "CREATE INDEX IF NOT EXISTS `index_income_entries_receivedAtEpochMillis` ON `income_entries` (`receivedAtEpochMillis`)",
            "CREATE INDEX IF NOT EXISTS `index_income_entries_deletedAtEpochMillis` ON `income_entries` (`deletedAtEpochMillis`)",
            "CREATE TABLE IF NOT EXISTS `budgets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `category` TEXT, `periodStartEpochDay` INTEGER NOT NULL, `periodEndEpochDay` INTEGER NOT NULL, `limitMinor` INTEGER NOT NULL, `currencyCode` TEXT NOT NULL, `deletedAtEpochMillis` INTEGER, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_budgets_periodStartEpochDay_periodEndEpochDay` ON `budgets` (`periodStartEpochDay`, `periodEndEpochDay`)",
            "CREATE INDEX IF NOT EXISTS `index_budgets_category` ON `budgets` (`category`)",
            "CREATE INDEX IF NOT EXISTS `index_budgets_deletedAtEpochMillis` ON `budgets` (`deletedAtEpochMillis`)",
            "CREATE TABLE IF NOT EXISTS `subscriptions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `vendor` TEXT, `amountMinor` INTEGER NOT NULL, `currencyCode` TEXT NOT NULL, `cycle` TEXT NOT NULL, `cycleDays` INTEGER, `nextChargeOnEpochDay` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, `reminderId` INTEGER, `deletedAtEpochMillis` INTEGER, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, FOREIGN KEY(`reminderId`) REFERENCES `reminders`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE INDEX IF NOT EXISTS `index_subscriptions_reminderId` ON `subscriptions` (`reminderId`)",
            "CREATE INDEX IF NOT EXISTS `index_subscriptions_nextChargeOnEpochDay` ON `subscriptions` (`nextChargeOnEpochDay`)",
            "CREATE INDEX IF NOT EXISTS `index_subscriptions_isActive` ON `subscriptions` (`isActive`)",
            "CREATE INDEX IF NOT EXISTS `index_subscriptions_deletedAtEpochMillis` ON `subscriptions` (`deletedAtEpochMillis`)",
            "CREATE TABLE IF NOT EXISTS `calculation_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `expression` TEXT NOT NULL, `resultText` TEXT NOT NULL, `languageTag` TEXT NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_calculation_records_createdAtEpochMillis` ON `calculation_records` (`createdAtEpochMillis`)",
            "CREATE TABLE IF NOT EXISTS `notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT, `body` TEXT NOT NULL, `isPinned` INTEGER NOT NULL, `clientId` INTEGER, `projectId` INTEGER, `languageTag` TEXT NOT NULL, `deletedAtEpochMillis` INTEGER, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, FOREIGN KEY(`clientId`) REFERENCES `clients`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE INDEX IF NOT EXISTS `index_notes_clientId` ON `notes` (`clientId`)",
            "CREATE INDEX IF NOT EXISTS `index_notes_projectId` ON `notes` (`projectId`)",
            "CREATE INDEX IF NOT EXISTS `index_notes_isPinned` ON `notes` (`isPinned`)",
            "CREATE INDEX IF NOT EXISTS `index_notes_updatedAtEpochMillis` ON `notes` (`updatedAtEpochMillis`)",
            "CREATE INDEX IF NOT EXISTS `index_notes_deletedAtEpochMillis` ON `notes` (`deletedAtEpochMillis`)",
            "CREATE VIRTUAL TABLE IF NOT EXISTS `note_fts` USING FTS4(`title` TEXT, `body` TEXT NOT NULL, content=`notes`)",
            "CREATE TABLE IF NOT EXISTS `note_attachments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `noteId` INTEGER NOT NULL, `fileId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`fileId`) REFERENCES `files`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_note_attachments_noteId_fileId` ON `note_attachments` (`noteId`, `fileId`)",
            "CREATE INDEX IF NOT EXISTS `index_note_attachments_fileId` ON `note_attachments` (`fileId`)",
            "CREATE TABLE IF NOT EXISTS `memory` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `subject` TEXT NOT NULL, `fact` TEXT NOT NULL, `source` TEXT NOT NULL, `languageTag` TEXT NOT NULL, `clientId` INTEGER, `projectId` INTEGER, `recallCount` INTEGER NOT NULL, `lastRecalledAtEpochMillis` INTEGER, `deletedAtEpochMillis` INTEGER, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, FOREIGN KEY(`clientId`) REFERENCES `clients`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE INDEX IF NOT EXISTS `index_memory_subject` ON `memory` (`subject`)",
            "CREATE INDEX IF NOT EXISTS `index_memory_clientId` ON `memory` (`clientId`)",
            "CREATE INDEX IF NOT EXISTS `index_memory_projectId` ON `memory` (`projectId`)",
            "CREATE INDEX IF NOT EXISTS `index_memory_deletedAtEpochMillis` ON `memory` (`deletedAtEpochMillis`)",
            "CREATE VIRTUAL TABLE IF NOT EXISTS `memory_fts` USING FTS4(`subject` TEXT NOT NULL, `fact` TEXT NOT NULL, content=`memory`)",
            "CREATE TABLE IF NOT EXISTS `voice_notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `audioFileId` INTEGER, `noteId` INTEGER, `transcript` TEXT, `durationMillis` INTEGER NOT NULL, `languageTag` TEXT NOT NULL, `recordedAtEpochMillis` INTEGER NOT NULL, `deletedAtEpochMillis` INTEGER, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, FOREIGN KEY(`audioFileId`) REFERENCES `files`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE INDEX IF NOT EXISTS `index_voice_notes_audioFileId` ON `voice_notes` (`audioFileId`)",
            "CREATE INDEX IF NOT EXISTS `index_voice_notes_noteId` ON `voice_notes` (`noteId`)",
            "CREATE INDEX IF NOT EXISTS `index_voice_notes_recordedAtEpochMillis` ON `voice_notes` (`recordedAtEpochMillis`)",
            "CREATE INDEX IF NOT EXISTS `index_voice_notes_deletedAtEpochMillis` ON `voice_notes` (`deletedAtEpochMillis`)",
            "CREATE TABLE IF NOT EXISTS `files` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `displayName` TEXT NOT NULL, `relativePath` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `source` TEXT NOT NULL, `extractedText` TEXT, `clientId` INTEGER, `projectId` INTEGER, `deletedAtEpochMillis` INTEGER, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, FOREIGN KEY(`clientId`) REFERENCES `clients`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_files_uuid` ON `files` (`uuid`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_files_relativePath` ON `files` (`relativePath`)",
            "CREATE INDEX IF NOT EXISTS `index_files_clientId` ON `files` (`clientId`)",
            "CREATE INDEX IF NOT EXISTS `index_files_projectId` ON `files` (`projectId`)",
            "CREATE INDEX IF NOT EXISTS `index_files_source` ON `files` (`source`)",
            "CREATE INDEX IF NOT EXISTS `index_files_deletedAtEpochMillis` ON `files` (`deletedAtEpochMillis`)",
            "CREATE VIRTUAL TABLE IF NOT EXISTS `file_fts` USING FTS4(`displayName` TEXT NOT NULL, `extractedText` TEXT, content=`files`)",
            "CREATE TABLE IF NOT EXISTS `scan_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fileId` INTEGER NOT NULL, `pageCount` INTEGER NOT NULL, `recognisedAtEpochMillis` INTEGER, `createdAtEpochMillis` INTEGER NOT NULL, FOREIGN KEY(`fileId`) REFERENCES `files`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_scan_records_fileId` ON `scan_records` (`fileId`)",
            "CREATE TABLE IF NOT EXISTS `conversations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT, `startedAtEpochMillis` INTEGER NOT NULL, `lastMessageAtEpochMillis` INTEGER NOT NULL, `messageCount` INTEGER NOT NULL, `isArchived` INTEGER NOT NULL, `deletedAtEpochMillis` INTEGER, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_conversations_lastMessageAtEpochMillis` ON `conversations` (`lastMessageAtEpochMillis`)",
            "CREATE INDEX IF NOT EXISTS `index_conversations_isArchived` ON `conversations` (`isArchived`)",
            "CREATE INDEX IF NOT EXISTS `index_conversations_deletedAtEpochMillis` ON `conversations` (`deletedAtEpochMillis`)",
            "CREATE TABLE IF NOT EXISTS `conversation_messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `conversationId` INTEGER NOT NULL, `role` TEXT NOT NULL, `text` TEXT NOT NULL, `languageTag` TEXT NOT NULL, `source` TEXT NOT NULL, `commandName` TEXT, `engineName` TEXT, `createdAtEpochMillis` INTEGER NOT NULL, FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_conversation_messages_conversationId_createdAtEpochMillis` ON `conversation_messages` (`conversationId`, `createdAtEpochMillis`)",
            "CREATE INDEX IF NOT EXISTS `index_conversation_messages_role` ON `conversation_messages` (`role`)",
            "CREATE VIRTUAL TABLE IF NOT EXISTS `conversation_message_fts` USING FTS4(`text` TEXT NOT NULL, content=`conversation_messages`)",
            "CREATE TABLE IF NOT EXISTS `automations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `description` TEXT, `trigger` TEXT NOT NULL, `triggerConfigJson` TEXT NOT NULL, `actionConfigJson` TEXT NOT NULL, `isEnabled` INTEGER NOT NULL, `lastRunAtEpochMillis` INTEGER, `deletedAtEpochMillis` INTEGER, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_automations_isEnabled` ON `automations` (`isEnabled`)",
            "CREATE INDEX IF NOT EXISTS `index_automations_trigger` ON `automations` (`trigger`)",
            "CREATE INDEX IF NOT EXISTS `index_automations_deletedAtEpochMillis` ON `automations` (`deletedAtEpochMillis`)",
            "CREATE TABLE IF NOT EXISTS `automation_runs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `automationId` INTEGER NOT NULL, `startedAtEpochMillis` INTEGER NOT NULL, `finishedAtEpochMillis` INTEGER, `outcome` TEXT NOT NULL, `detail` TEXT, FOREIGN KEY(`automationId`) REFERENCES `automations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_automation_runs_automationId_startedAtEpochMillis` ON `automation_runs` (`automationId`, `startedAtEpochMillis`)",
            "CREATE INDEX IF NOT EXISTS `index_automation_runs_outcome` ON `automation_runs` (`outcome`)",
            "CREATE TABLE IF NOT EXISTS `meetings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `clientId` INTEGER, `projectId` INTEGER, `startsAtEpochMillis` INTEGER NOT NULL, `endsAtEpochMillis` INTEGER, `location` TEXT, `deletedAtEpochMillis` INTEGER, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, FOREIGN KEY(`clientId`) REFERENCES `clients`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE INDEX IF NOT EXISTS `index_meetings_clientId` ON `meetings` (`clientId`)",
            "CREATE INDEX IF NOT EXISTS `index_meetings_projectId` ON `meetings` (`projectId`)",
            "CREATE INDEX IF NOT EXISTS `index_meetings_startsAtEpochMillis` ON `meetings` (`startsAtEpochMillis`)",
            "CREATE INDEX IF NOT EXISTS `index_meetings_deletedAtEpochMillis` ON `meetings` (`deletedAtEpochMillis`)",
            "CREATE TABLE IF NOT EXISTS `meeting_notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `meetingId` INTEGER NOT NULL, `body` TEXT NOT NULL, `isActionItem` INTEGER NOT NULL, `taskId` INTEGER, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, FOREIGN KEY(`meetingId`) REFERENCES `meetings`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE INDEX IF NOT EXISTS `index_meeting_notes_meetingId` ON `meeting_notes` (`meetingId`)",
            "CREATE INDEX IF NOT EXISTS `index_meeting_notes_taskId` ON `meeting_notes` (`taskId`)",
            "CREATE TABLE IF NOT EXISTS `calendar_links` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `systemEventId` INTEGER NOT NULL, `systemCalendarId` INTEGER NOT NULL, `title` TEXT NOT NULL, `startsAtEpochMillis` INTEGER NOT NULL, `endsAtEpochMillis` INTEGER, `taskId` INTEGER, `projectId` INTEGER, `meetingId` INTEGER, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`meetingId`) REFERENCES `meetings`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_calendar_links_systemEventId` ON `calendar_links` (`systemEventId`)",
            "CREATE INDEX IF NOT EXISTS `index_calendar_links_taskId` ON `calendar_links` (`taskId`)",
            "CREATE INDEX IF NOT EXISTS `index_calendar_links_projectId` ON `calendar_links` (`projectId`)",
            "CREATE INDEX IF NOT EXISTS `index_calendar_links_meetingId` ON `calendar_links` (`meetingId`)",
            "CREATE INDEX IF NOT EXISTS `index_calendar_links_startsAtEpochMillis` ON `calendar_links` (`startsAtEpochMillis`)",
            """INSERT INTO `conversations` (
        `id`, `title`, `startedAtEpochMillis`, `lastMessageAtEpochMillis`, `messageCount`,
        `isArchived`, `deletedAtEpochMillis`, `createdAtEpochMillis`, `updatedAtEpochMillis`
    ) SELECT
        1, NULL,
        COALESCE(MIN(`createdAtEpochMillis`), 0), COALESCE(MAX(`createdAtEpochMillis`), 0),
        COUNT(*), 0, NULL,
        COALESCE(MIN(`createdAtEpochMillis`), 0), COALESCE(MAX(`createdAtEpochMillis`), 0)
    FROM `conversation_turns`
    HAVING COUNT(*) > 0""",
            """INSERT INTO `conversation_messages` (
        `id`, `conversationId`, `role`, `text`, `languageTag`, `source`,
        `commandName`, `engineName`, `createdAtEpochMillis`
    ) SELECT
        `id`, 1, `role`, `text`, `languageTag`, `source`,
        `commandName`, NULL, `createdAtEpochMillis`
    FROM `conversation_turns`""",
            "INSERT INTO `conversation_message_fts`(`conversation_message_fts`) VALUES('rebuild')",
            "DROP TABLE `conversation_turns`",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_client_fts_AFTER_INSERT AFTER INSERT ON `clients` BEGIN INSERT INTO `client_fts`(`docid`, `name`, `companyName`, `notes`) VALUES (NEW.`rowid`, NEW.`name`, NEW.`companyName`, NEW.`notes`); END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_client_fts_AFTER_UPDATE AFTER UPDATE ON `clients` BEGIN INSERT INTO `client_fts`(`docid`, `name`, `companyName`, `notes`) VALUES (NEW.`rowid`, NEW.`name`, NEW.`companyName`, NEW.`notes`); END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_client_fts_BEFORE_DELETE BEFORE DELETE ON `clients` BEGIN DELETE FROM `client_fts` WHERE `docid`=OLD.`rowid`; END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_client_fts_BEFORE_UPDATE BEFORE UPDATE ON `clients` BEGIN DELETE FROM `client_fts` WHERE `docid`=OLD.`rowid`; END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_conversation_message_fts_AFTER_INSERT AFTER INSERT ON `conversation_messages` BEGIN INSERT INTO `conversation_message_fts`(`docid`, `text`) VALUES (NEW.`rowid`, NEW.`text`); END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_conversation_message_fts_AFTER_UPDATE AFTER UPDATE ON `conversation_messages` BEGIN INSERT INTO `conversation_message_fts`(`docid`, `text`) VALUES (NEW.`rowid`, NEW.`text`); END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_conversation_message_fts_BEFORE_DELETE BEFORE DELETE ON `conversation_messages` BEGIN DELETE FROM `conversation_message_fts` WHERE `docid`=OLD.`rowid`; END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_conversation_message_fts_BEFORE_UPDATE BEFORE UPDATE ON `conversation_messages` BEGIN DELETE FROM `conversation_message_fts` WHERE `docid`=OLD.`rowid`; END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_file_fts_AFTER_INSERT AFTER INSERT ON `files` BEGIN INSERT INTO `file_fts`(`docid`, `displayName`, `extractedText`) VALUES (NEW.`rowid`, NEW.`displayName`, NEW.`extractedText`); END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_file_fts_AFTER_UPDATE AFTER UPDATE ON `files` BEGIN INSERT INTO `file_fts`(`docid`, `displayName`, `extractedText`) VALUES (NEW.`rowid`, NEW.`displayName`, NEW.`extractedText`); END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_file_fts_BEFORE_DELETE BEFORE DELETE ON `files` BEGIN DELETE FROM `file_fts` WHERE `docid`=OLD.`rowid`; END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_file_fts_BEFORE_UPDATE BEFORE UPDATE ON `files` BEGIN DELETE FROM `file_fts` WHERE `docid`=OLD.`rowid`; END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_memory_fts_AFTER_INSERT AFTER INSERT ON `memory` BEGIN INSERT INTO `memory_fts`(`docid`, `subject`, `fact`) VALUES (NEW.`rowid`, NEW.`subject`, NEW.`fact`); END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_memory_fts_AFTER_UPDATE AFTER UPDATE ON `memory` BEGIN INSERT INTO `memory_fts`(`docid`, `subject`, `fact`) VALUES (NEW.`rowid`, NEW.`subject`, NEW.`fact`); END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_memory_fts_BEFORE_DELETE BEFORE DELETE ON `memory` BEGIN DELETE FROM `memory_fts` WHERE `docid`=OLD.`rowid`; END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_memory_fts_BEFORE_UPDATE BEFORE UPDATE ON `memory` BEGIN DELETE FROM `memory_fts` WHERE `docid`=OLD.`rowid`; END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_note_fts_AFTER_INSERT AFTER INSERT ON `notes` BEGIN INSERT INTO `note_fts`(`docid`, `title`, `body`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`body`); END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_note_fts_AFTER_UPDATE AFTER UPDATE ON `notes` BEGIN INSERT INTO `note_fts`(`docid`, `title`, `body`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`body`); END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_note_fts_BEFORE_DELETE BEFORE DELETE ON `notes` BEGIN DELETE FROM `note_fts` WHERE `docid`=OLD.`rowid`; END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_note_fts_BEFORE_UPDATE BEFORE UPDATE ON `notes` BEGIN DELETE FROM `note_fts` WHERE `docid`=OLD.`rowid`; END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_project_fts_AFTER_INSERT AFTER INSERT ON `projects` BEGIN INSERT INTO `project_fts`(`docid`, `name`, `description`) VALUES (NEW.`rowid`, NEW.`name`, NEW.`description`); END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_project_fts_AFTER_UPDATE AFTER UPDATE ON `projects` BEGIN INSERT INTO `project_fts`(`docid`, `name`, `description`) VALUES (NEW.`rowid`, NEW.`name`, NEW.`description`); END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_project_fts_BEFORE_DELETE BEFORE DELETE ON `projects` BEGIN DELETE FROM `project_fts` WHERE `docid`=OLD.`rowid`; END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_project_fts_BEFORE_UPDATE BEFORE UPDATE ON `projects` BEGIN DELETE FROM `project_fts` WHERE `docid`=OLD.`rowid`; END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_task_fts_AFTER_INSERT AFTER INSERT ON `tasks` BEGIN INSERT INTO `task_fts`(`docid`, `title`, `notes`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`notes`); END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_task_fts_AFTER_UPDATE AFTER UPDATE ON `tasks` BEGIN INSERT INTO `task_fts`(`docid`, `title`, `notes`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`notes`); END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_task_fts_BEFORE_DELETE BEFORE DELETE ON `tasks` BEGIN DELETE FROM `task_fts` WHERE `docid`=OLD.`rowid`; END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_task_fts_BEFORE_UPDATE BEFORE UPDATE ON `tasks` BEGIN DELETE FROM `task_fts` WHERE `docid`=OLD.`rowid`; END",
        )
}

/**
 * Version 3 makes a task a whole record rather than a line with a date.
 *
 * `tasks` gains a description, an all-day flag, an estimate, a client, a repeat rule, the series
 * uuid that ties occurrences together, and an archive column. Three tables arrive beside it: `tags`
 * and `task_tags`, and `task_attachments`.
 *
 * Two things here are not plain table creation, and both are traps this project has already been
 * caught by once:
 *
 * 1. `tasks` is REBUILT rather than altered, because it gains a foreign key to `clients` and SQLite
 *    cannot add one with ALTER TABLE. The indices are created AFTER the old table is dropped:
 *    `ALTER TABLE ... RENAME` carries the existing indices across to the renamed table, so a
 *    `CREATE INDEX IF NOT EXISTS` before the drop is a silent no-op on a name that is already
 *    taken, and the drop then takes the index with it. Rows are copied by explicit column list so a
 *    future column-order change cannot shift Abu's data into the wrong fields.
 * 2. `task_fts` is dropped and rebuilt. It is an external-content index over `tasks`, so it belongs
 *    to a table that no longer exists by the time the copy is done — and an external-content index
 *    is not retrospective, so the `rebuild` command is what actually puts the existing tasks back
 *    into it. Without it every task Abu already had would be unsearchable for ever.
 *
 * `dueAllDay` defaults to 0 for every existing row on purpose: those tasks were created when the
 * only thing Mira could record was an instant, so treating them as timed is what they actually are.
 */
internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SupportSQLiteDatabase) {
        STATEMENTS.forEach(connection::execSQL)
    }

    private val STATEMENTS = listOf(
        "CREATE TABLE IF NOT EXISTS `tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)",

        "ALTER TABLE `tasks` RENAME TO `tasks_v2`",
        "CREATE TABLE IF NOT EXISTS `tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `description` TEXT, `notes` TEXT, `dueAtEpochMillis` INTEGER, `dueAllDay` INTEGER NOT NULL, `estimatedMinutes` INTEGER, `priority` TEXT NOT NULL, `isDone` INTEGER NOT NULL, `completedAtEpochMillis` INTEGER, `origin` TEXT NOT NULL, `projectId` INTEGER, `clientId` INTEGER, `recurrenceRule` TEXT, `seriesUuid` TEXT, `archivedAtEpochMillis` INTEGER, `deletedAtEpochMillis` INTEGER, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`clientId`) REFERENCES `clients`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        """INSERT INTO `tasks` (
        `id`, `title`, `description`, `notes`, `dueAtEpochMillis`, `dueAllDay`, `estimatedMinutes`,
        `priority`, `isDone`, `completedAtEpochMillis`, `origin`, `projectId`, `clientId`,
        `recurrenceRule`, `seriesUuid`, `archivedAtEpochMillis`, `deletedAtEpochMillis`,
        `createdAtEpochMillis`, `updatedAtEpochMillis`
    ) SELECT
        `id`, `title`, NULL, `notes`, `dueAtEpochMillis`, 0, NULL,
        `priority`, `isDone`, `completedAtEpochMillis`, `origin`, `projectId`, NULL,
        NULL, NULL, NULL, `deletedAtEpochMillis`,
        `createdAtEpochMillis`, `updatedAtEpochMillis`
    FROM `tasks_v2`""",
        "DROP TABLE `tasks_v2`",
        // After the drop, never before: the rename above carried version 2's indices onto
        // `tasks_v2`, so creating them here is the only way they survive.
        "CREATE INDEX IF NOT EXISTS `index_tasks_dueAtEpochMillis` ON `tasks` (`dueAtEpochMillis`)",
        "CREATE INDEX IF NOT EXISTS `index_tasks_isDone` ON `tasks` (`isDone`)",
        "CREATE INDEX IF NOT EXISTS `index_tasks_projectId` ON `tasks` (`projectId`)",
        "CREATE INDEX IF NOT EXISTS `index_tasks_clientId` ON `tasks` (`clientId`)",
        "CREATE INDEX IF NOT EXISTS `index_tasks_deletedAtEpochMillis` ON `tasks` (`deletedAtEpochMillis`)",
        "CREATE INDEX IF NOT EXISTS `index_tasks_archivedAtEpochMillis` ON `tasks` (`archivedAtEpochMillis`)",
        "CREATE INDEX IF NOT EXISTS `index_tasks_seriesUuid` ON `tasks` (`seriesUuid`)",

        "DROP TABLE IF EXISTS `task_fts`",
        "CREATE VIRTUAL TABLE IF NOT EXISTS `task_fts` USING FTS4(`title` TEXT NOT NULL, `description` TEXT, `notes` TEXT, content=`tasks`)",
        // External-content FTS is not retrospective. Without this every task Abu already had
        // would be missing from search for ever.
        "INSERT INTO `task_fts`(`task_fts`) VALUES('rebuild')",

        "CREATE TABLE IF NOT EXISTS `task_tags` (`taskId` INTEGER NOT NULL, `tagId` INTEGER NOT NULL, PRIMARY KEY(`taskId`, `tagId`), FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_task_tags_tagId` ON `task_tags` (`tagId`)",

        "CREATE TABLE IF NOT EXISTS `task_attachments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `taskId` INTEGER NOT NULL, `fileId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`fileId`) REFERENCES `files`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_task_attachments_taskId_fileId` ON `task_attachments` (`taskId`, `fileId`)",
        "CREATE INDEX IF NOT EXISTS `index_task_attachments_fileId` ON `task_attachments` (`fileId`)",
    )
}

/**
 * Version 4 turns a reminder from a title and a moment into something Mira can reason about.
 *
 * `reminders` gains a kind, a repeat rule, a snooze, and links to the invoice, client, project or
 * subscription it grew out of. Two tables arrive beside it: `reminder_places`, for a reminder that
 * waits on arriving somewhere rather than on a time, and `alarms`, which is the alarm clock Abu
 * sets — distinct from `alarm_records`, which is the ledger of what Mira asked the OS to do.
 *
 * `reminders` is REBUILT rather than altered, because it gains four foreign keys and SQLite cannot
 * add one with ALTER TABLE. The indices are created AFTER the old table is dropped: the rename
 * carries version 3's indices onto `reminders_v3`, so creating them earlier is a silent no-op on a
 * name already taken and the drop then takes them with it.
 *
 * Every existing row becomes `ONE_TIME` unless it points at a task, in which case it becomes
 * `TASK` — which is what those rows already were, just without a word for it. Nothing is guessed
 * beyond that: a reminder Abu made by hand stays one-time even if its title mentions an invoice.
 */
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SupportSQLiteDatabase) {
        STATEMENTS.forEach(connection::execSQL)
    }

    private val STATEMENTS = listOf(
        "ALTER TABLE `reminders` RENAME TO `reminders_v3`",
        "CREATE TABLE IF NOT EXISTS `reminders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `triggerAtEpochMillis` INTEGER NOT NULL, `state` TEXT NOT NULL, `kind` TEXT NOT NULL, `recurrenceRule` TEXT, `snoozedUntilEpochMillis` INTEGER, `linkedTaskId` INTEGER, `linkedInvoiceId` INTEGER, `linkedClientId` INTEGER, `linkedProjectId` INTEGER, `linkedSubscriptionId` INTEGER, `origin` TEXT NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, FOREIGN KEY(`linkedTaskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`linkedInvoiceId`) REFERENCES `invoices`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`linkedClientId`) REFERENCES `clients`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`linkedProjectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`linkedSubscriptionId`) REFERENCES `subscriptions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        """INSERT INTO `reminders` (
        `id`, `title`, `triggerAtEpochMillis`, `state`, `kind`, `recurrenceRule`,
        `snoozedUntilEpochMillis`, `linkedTaskId`, `linkedInvoiceId`, `linkedClientId`,
        `linkedProjectId`, `linkedSubscriptionId`, `origin`, `createdAtEpochMillis`
    ) SELECT
        `id`, `title`, `triggerAtEpochMillis`, `state`,
        CASE WHEN `linkedTaskId` IS NULL THEN 'ONE_TIME' ELSE 'TASK' END,
        NULL, NULL, `linkedTaskId`, NULL, NULL, NULL, NULL, `origin`, `createdAtEpochMillis`
    FROM `reminders_v3`""",
        "DROP TABLE `reminders_v3`",
        // After the drop, never before — see the KDoc.
        "CREATE INDEX IF NOT EXISTS `index_reminders_triggerAtEpochMillis` ON `reminders` (`triggerAtEpochMillis`)",
        "CREATE INDEX IF NOT EXISTS `index_reminders_linkedTaskId` ON `reminders` (`linkedTaskId`)",
        "CREATE INDEX IF NOT EXISTS `index_reminders_linkedInvoiceId` ON `reminders` (`linkedInvoiceId`)",
        "CREATE INDEX IF NOT EXISTS `index_reminders_linkedClientId` ON `reminders` (`linkedClientId`)",
        "CREATE INDEX IF NOT EXISTS `index_reminders_linkedProjectId` ON `reminders` (`linkedProjectId`)",
        "CREATE INDEX IF NOT EXISTS `index_reminders_linkedSubscriptionId` ON `reminders` (`linkedSubscriptionId`)",
        "CREATE INDEX IF NOT EXISTS `index_reminders_kind` ON `reminders` (`kind`)",
        "CREATE INDEX IF NOT EXISTS `index_reminders_state` ON `reminders` (`state`)",

        "CREATE TABLE IF NOT EXISTS `reminder_places` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `reminderId` INTEGER NOT NULL, `label` TEXT NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `radiusMeters` REAL NOT NULL, `trigger` TEXT NOT NULL, `isRegistered` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, FOREIGN KEY(`reminderId`) REFERENCES `reminders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_reminder_places_reminderId` ON `reminder_places` (`reminderId`)",
        "CREATE INDEX IF NOT EXISTS `index_reminder_places_isRegistered` ON `reminder_places` (`isRegistered`)",

        "CREATE TABLE IF NOT EXISTS `alarms` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `label` TEXT NOT NULL, `minuteOfDay` INTEGER NOT NULL, `repeatDays` TEXT NOT NULL, `isEnabled` INTEGER NOT NULL, `nextTriggerAtEpochMillis` INTEGER, `snoozedUntilEpochMillis` INTEGER, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL)",
        "CREATE INDEX IF NOT EXISTS `index_alarms_isEnabled` ON `alarms` (`isEnabled`)",
        "CREATE INDEX IF NOT EXISTS `index_alarms_nextTriggerAtEpochMillis` ON `alarms` (`nextTriggerAtEpochMillis`)",
    )
}

/**
 * Version 5 adds `saved_places`: the directory that lets Abu say "when I get near the office"
 * and have Mira know where that is.
 *
 * The plainest migration in the file — one new table and its indices, nothing rebuilt and nothing
 * copied. The place directory is new information Mira could not previously hold, so there is no
 * old column it could be derived from and nothing to back-fill. Every existing place reminder
 * keeps its own coordinate in `reminder_places` and is untouched by this.
 */
internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SupportSQLiteDatabase) {
        STATEMENTS.forEach(connection::execSQL)
    }

    private val STATEMENTS = listOf(
        "CREATE TABLE IF NOT EXISTS `saved_places` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `label` TEXT NOT NULL, `labelKey` TEXT NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `radiusMeters` REAL NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_saved_places_labelKey` ON `saved_places` (`labelKey`)",
        "CREATE INDEX IF NOT EXISTS `index_saved_places_label` ON `saved_places` (`label`)",
    )
}

/**
 * Version 6 makes a note the thing the product brief describes: it gains a task link, an archive
 * that is not the bin, tags shared with tasks, and — new — pages of handwriting.
 *
 * `notes` is REBUILT, because `taskId` is a foreign key and SQLite cannot add one with
 * `ALTER TABLE`. That makes this the fourth rebuild in the file and it carries BOTH of the traps
 * the earlier ones documented, plus a third that is specific to this table:
 *
 * 1. **The indices are created after the `DROP`.** `RENAME` carries the old indices to
 *    `notes_v5` under their original names, so creating them earlier is a silent no-op that the
 *    drop then takes away with it.
 * 2. **`note_fts` is rebuilt**, because an external-content index is not retrospective: rows
 *    copied in before the sync triggers exist are invisible to search for ever.
 * 3. **The FTS triggers are dropped FIRST, and recreated last.** This is the one 1 → 2 did not
 *    have to think about, because `note_fts` did not exist then. The four triggers are attached
 *    to `notes`, so the rename carries them to `notes_v5` — where a `BEFORE DELETE` trigger then
 *    fires once per row as `notes_v5` is dropped, deleting from `note_fts` by a rowid that now
 *    belongs to a DIFFERENT note in the rebuilt table. Dropping them up front is what stops the
 *    teardown of the old table quietly shredding the index of the new one.
 *
 * Neither back-filled column is guessed. Every existing note arrives unarchived and linked to no
 * task, which is what those rows already were.
 */
internal val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SupportSQLiteDatabase) {
        STATEMENTS.forEach(connection::execSQL)
    }

    private val STATEMENTS = listOf(
        // 1. Off first — see the KDoc. Nothing may be attached to `notes` while it is torn down.
        "DROP TRIGGER IF EXISTS room_fts_content_sync_note_fts_BEFORE_UPDATE",
        "DROP TRIGGER IF EXISTS room_fts_content_sync_note_fts_BEFORE_DELETE",
        "DROP TRIGGER IF EXISTS room_fts_content_sync_note_fts_AFTER_UPDATE",
        "DROP TRIGGER IF EXISTS room_fts_content_sync_note_fts_AFTER_INSERT",

        "ALTER TABLE `notes` RENAME TO `notes_v5`",
        "CREATE TABLE IF NOT EXISTS `notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT, `body` TEXT NOT NULL, `isPinned` INTEGER NOT NULL, `clientId` INTEGER, `projectId` INTEGER, `taskId` INTEGER, `languageTag` TEXT NOT NULL, `archivedAtEpochMillis` INTEGER, `deletedAtEpochMillis` INTEGER, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, FOREIGN KEY(`clientId`) REFERENCES `clients`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        """INSERT INTO `notes` (
        `id`, `title`, `body`, `isPinned`, `clientId`, `projectId`, `taskId`, `languageTag`,
        `archivedAtEpochMillis`, `deletedAtEpochMillis`, `createdAtEpochMillis`, `updatedAtEpochMillis`
    ) SELECT
        `id`, `title`, `body`, `isPinned`, `clientId`, `projectId`, NULL, `languageTag`,
        NULL, `deletedAtEpochMillis`, `createdAtEpochMillis`, `updatedAtEpochMillis`
    FROM `notes_v5`""",
        "DROP TABLE `notes_v5`",

        // 2. After the drop, never before.
        "CREATE INDEX IF NOT EXISTS `index_notes_clientId` ON `notes` (`clientId`)",
        "CREATE INDEX IF NOT EXISTS `index_notes_projectId` ON `notes` (`projectId`)",
        "CREATE INDEX IF NOT EXISTS `index_notes_isPinned` ON `notes` (`isPinned`)",
        "CREATE INDEX IF NOT EXISTS `index_notes_updatedAtEpochMillis` ON `notes` (`updatedAtEpochMillis`)",
        "CREATE INDEX IF NOT EXISTS `index_notes_deletedAtEpochMillis` ON `notes` (`deletedAtEpochMillis`)",
        "CREATE INDEX IF NOT EXISTS `index_notes_taskId` ON `notes` (`taskId`)",
        "CREATE INDEX IF NOT EXISTS `index_notes_archivedAtEpochMillis` ON `notes` (`archivedAtEpochMillis`)",

        // 3. The triggers go back on the NEW table, verbatim from the exported schema.
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_note_fts_BEFORE_UPDATE BEFORE UPDATE ON `notes` BEGIN DELETE FROM `note_fts` WHERE `docid`=OLD.`rowid`; END",
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_note_fts_BEFORE_DELETE BEFORE DELETE ON `notes` BEGIN DELETE FROM `note_fts` WHERE `docid`=OLD.`rowid`; END",
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_note_fts_AFTER_UPDATE AFTER UPDATE ON `notes` BEGIN INSERT INTO `note_fts`(`docid`, `title`, `body`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`body`); END",
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_note_fts_AFTER_INSERT AFTER INSERT ON `notes` BEGIN INSERT INTO `note_fts`(`docid`, `title`, `body`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`body`); END",

        // Every note copied above predates these triggers, so without this every word Abu has
        // ever written down is in the table and findable by nothing.
        "INSERT INTO `note_fts`(`note_fts`) VALUES('rebuild')",

        "CREATE TABLE IF NOT EXISTS `note_tags` (`noteId` INTEGER NOT NULL, `tagId` INTEGER NOT NULL, PRIMARY KEY(`noteId`, `tagId`), FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_note_tags_tagId` ON `note_tags` (`tagId`)",

        "CREATE TABLE IF NOT EXISTS `ink_pages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `noteId` INTEGER NOT NULL, `strokeFileId` INTEGER, `imageFileId` INTEGER, `widthPx` INTEGER NOT NULL, `heightPx` INTEGER NOT NULL, `strokeCount` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`strokeFileId`) REFERENCES `files`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`imageFileId`) REFERENCES `files`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        "CREATE INDEX IF NOT EXISTS `index_ink_pages_noteId` ON `ink_pages` (`noteId`)",
        "CREATE INDEX IF NOT EXISTS `index_ink_pages_strokeFileId` ON `ink_pages` (`strokeFileId`)",
        "CREATE INDEX IF NOT EXISTS `index_ink_pages_imageFileId` ON `ink_pages` (`imageFileId`)",
    )
}
