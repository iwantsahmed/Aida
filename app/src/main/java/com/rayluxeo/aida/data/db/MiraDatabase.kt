package com.rayluxeo.aida.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.rayluxeo.aida.data.db.dao.ActivityLogDao
import com.rayluxeo.aida.data.db.dao.PrayerSettingsDao
import com.rayluxeo.aida.data.db.dao.PrayerTrackingDao
import com.rayluxeo.aida.data.db.entity.PrayerSettingsEntity
import com.rayluxeo.aida.data.db.entity.PrayerTrackingEntryEntity
import com.rayluxeo.aida.data.db.dao.AlarmRecordDao
import com.rayluxeo.aida.data.db.dao.AppSettingDao
import com.rayluxeo.aida.data.db.dao.AutomationDao
import com.rayluxeo.aida.data.db.dao.AutomationFireDao
import com.rayluxeo.aida.data.db.dao.BudgetDao
import com.rayluxeo.aida.data.db.dao.CalculationDao
import com.rayluxeo.aida.data.db.dao.CalendarLinkDao
import com.rayluxeo.aida.data.db.dao.ClientDao
import com.rayluxeo.aida.data.db.dao.ExpenseDao
import com.rayluxeo.aida.data.db.dao.FileDao
import com.rayluxeo.aida.data.db.dao.IncomeDao
import com.rayluxeo.aida.data.db.dao.InvoiceDao
import com.rayluxeo.aida.data.db.dao.MeetingDao
import com.rayluxeo.aida.data.db.dao.MemoryDao
import com.rayluxeo.aida.data.db.dao.NoteDao
import com.rayluxeo.aida.data.db.dao.PaymentDao
import com.rayluxeo.aida.data.db.dao.ProjectDao
import com.rayluxeo.aida.data.db.dao.QuoteDao
import com.rayluxeo.aida.data.db.dao.SubscriptionDao
import com.rayluxeo.aida.data.db.dao.SubtaskDao
import com.rayluxeo.aida.data.db.dao.TimerDao
import com.rayluxeo.aida.data.db.dao.UserProfileDao
import com.rayluxeo.aida.data.db.dao.VoiceNoteDao
import com.rayluxeo.aida.data.db.dao.ConversationDao
import com.rayluxeo.aida.data.db.dao.AlarmDao
import com.rayluxeo.aida.data.db.dao.ReminderPlaceDao
import com.rayluxeo.aida.data.db.dao.InkPageDao
import com.rayluxeo.aida.data.db.dao.SavedPlaceDao
import com.rayluxeo.aida.data.db.dao.ReminderDao
import com.rayluxeo.aida.data.db.dao.TaskDao
import com.rayluxeo.aida.data.db.dao.VaultDao
import com.rayluxeo.aida.data.db.entity.ActivityLogEntity
import com.rayluxeo.aida.data.db.entity.AlarmRecordEntity
import com.rayluxeo.aida.data.db.entity.InkPageEntity
import com.rayluxeo.aida.data.db.entity.NoteTagEntity
import com.rayluxeo.aida.data.db.entity.SavedPlaceEntity
import com.rayluxeo.aida.data.db.entity.AppSettingEntity
import com.rayluxeo.aida.data.db.entity.AutomationEntity
import com.rayluxeo.aida.data.db.entity.AutomationFireEntity
import com.rayluxeo.aida.data.db.entity.AutomationRunEntity
import com.rayluxeo.aida.data.db.entity.BudgetEntity
import com.rayluxeo.aida.data.db.entity.CalculationRecordEntity
import com.rayluxeo.aida.data.db.entity.CalendarLinkEntity
import com.rayluxeo.aida.data.db.entity.ClientEntity
import com.rayluxeo.aida.data.db.entity.ClientFtsEntity
import com.rayluxeo.aida.data.db.entity.ConversationEntity
import com.rayluxeo.aida.data.db.entity.ConversationMessageEntity
import com.rayluxeo.aida.data.db.entity.ConversationMessageFtsEntity
import com.rayluxeo.aida.data.db.entity.ExpenseEntity
import com.rayluxeo.aida.data.db.entity.FileRecordEntity
import com.rayluxeo.aida.data.db.entity.FileRecordFtsEntity
import com.rayluxeo.aida.data.db.entity.IncomeEntryEntity
import com.rayluxeo.aida.data.db.entity.InvoiceEntity
import com.rayluxeo.aida.data.db.entity.InvoiceItemEntity
import com.rayluxeo.aida.data.db.entity.MeetingEntity
import com.rayluxeo.aida.data.db.entity.MeetingNoteEntity
import com.rayluxeo.aida.data.db.entity.MemoryEntity
import com.rayluxeo.aida.data.db.entity.MemoryFtsEntity
import com.rayluxeo.aida.data.db.entity.NoteAttachmentEntity
import com.rayluxeo.aida.data.db.entity.NoteEntity
import com.rayluxeo.aida.data.db.entity.NoteFtsEntity
import com.rayluxeo.aida.data.db.entity.PaymentEntity
import com.rayluxeo.aida.data.db.entity.ProjectEntity
import com.rayluxeo.aida.data.db.entity.ProjectFtsEntity
import com.rayluxeo.aida.data.db.entity.ProjectMilestoneEntity
import com.rayluxeo.aida.data.db.entity.QuoteEntity
import com.rayluxeo.aida.data.db.entity.QuoteItemEntity
import com.rayluxeo.aida.data.db.entity.ReminderEntity
import com.rayluxeo.aida.data.db.entity.ScanRecordEntity
import com.rayluxeo.aida.data.db.entity.SubscriptionEntity
import com.rayluxeo.aida.data.db.entity.SubtaskEntity
import com.rayluxeo.aida.data.db.entity.TaskEntity
import com.rayluxeo.aida.data.db.entity.TagEntity
import com.rayluxeo.aida.data.db.entity.TaskTagEntity
import com.rayluxeo.aida.data.db.entity.ReminderPlaceEntity
import com.rayluxeo.aida.data.db.entity.AlarmEntity
import com.rayluxeo.aida.data.db.entity.TaskAttachmentEntity
import com.rayluxeo.aida.data.db.entity.TaskFtsEntity
import com.rayluxeo.aida.data.db.entity.TimerRecordEntity
import com.rayluxeo.aida.data.db.entity.UserProfileEntity
import com.rayluxeo.aida.data.db.entity.VaultItemEntity
import com.rayluxeo.aida.data.db.entity.VoiceNoteEntity

/**
 * Mira's only database.
 *
 * The schema is exported so every migration is written against a recorded version rather than a
 * guess — Abu's tasks, vault and invoices are not reproducible data. There is no destructive
 * fallback anywhere in this file or in the Hilt module that builds it: a schema change that
 * arrives without a written migration must fail loudly at startup, not delete his records.
 *
 * Foreign keys are only enforced when SQLite is told to; [MiraDatabaseCallback] turns them on for
 * every connection, including the ones Room opens during a migration.
 */
@Database(
    entities = [
        // Work
        TaskEntity::class,
        TaskFtsEntity::class,
        TagEntity::class,
        TaskTagEntity::class,
        TaskAttachmentEntity::class,
        ReminderPlaceEntity::class,
        SavedPlaceEntity::class,
        AlarmEntity::class,
        SubtaskEntity::class,
        ReminderEntity::class,
        AlarmRecordEntity::class,
        TimerRecordEntity::class,
        // Identity, settings and history
        UserProfileEntity::class,
        AppSettingEntity::class,
        ActivityLogEntity::class,
        // Business
        ClientEntity::class,
        ClientFtsEntity::class,
        ProjectEntity::class,
        ProjectFtsEntity::class,
        ProjectMilestoneEntity::class,
        // Money
        InvoiceEntity::class,
        InvoiceItemEntity::class,
        QuoteEntity::class,
        QuoteItemEntity::class,
        PaymentEntity::class,
        ExpenseEntity::class,
        IncomeEntryEntity::class,
        BudgetEntity::class,
        SubscriptionEntity::class,
        CalculationRecordEntity::class,
        // Knowledge
        NoteEntity::class,
        NoteFtsEntity::class,
        NoteAttachmentEntity::class,
        NoteTagEntity::class,
        InkPageEntity::class,
        MemoryEntity::class,
        MemoryFtsEntity::class,
        VoiceNoteEntity::class,
        FileRecordEntity::class,
        FileRecordFtsEntity::class,
        ScanRecordEntity::class,
        VaultItemEntity::class,
        // Assistant
        ConversationEntity::class,
        ConversationMessageEntity::class,
        ConversationMessageFtsEntity::class,
        AutomationEntity::class,
        AutomationRunEntity::class,
        AutomationFireEntity::class,
        // Calendar and meetings
        MeetingEntity::class,
        MeetingNoteEntity::class,
        CalendarLinkEntity::class,
        // Salah
        PrayerSettingsEntity::class,
        PrayerTrackingEntryEntity::class,
    ],
    version = MiraDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(MiraConverters::class)
abstract class MiraDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao

    abstract fun reminderDao(): ReminderDao

    abstract fun alarmDao(): AlarmDao

    abstract fun reminderPlaceDao(): ReminderPlaceDao

    abstract fun savedPlaceDao(): SavedPlaceDao

    abstract fun inkPageDao(): InkPageDao

    abstract fun vaultDao(): VaultDao

    abstract fun conversationDao(): ConversationDao

    // Work
    abstract fun subtaskDao(): SubtaskDao

    abstract fun alarmRecordDao(): AlarmRecordDao

    abstract fun timerDao(): TimerDao

    // Business
    abstract fun clientDao(): ClientDao

    abstract fun projectDao(): ProjectDao

    // Money
    abstract fun invoiceDao(): InvoiceDao

    abstract fun quoteDao(): QuoteDao

    abstract fun paymentDao(): PaymentDao

    abstract fun expenseDao(): ExpenseDao

    abstract fun incomeDao(): IncomeDao

    abstract fun budgetDao(): BudgetDao

    abstract fun subscriptionDao(): SubscriptionDao

    abstract fun calculationDao(): CalculationDao

    // Knowledge
    abstract fun noteDao(): NoteDao

    abstract fun memoryDao(): MemoryDao

    abstract fun voiceNoteDao(): VoiceNoteDao

    abstract fun fileDao(): FileDao

    // Assistant
    abstract fun automationDao(): AutomationDao

    abstract fun automationFireDao(): AutomationFireDao

    // Calendar
    abstract fun meetingDao(): MeetingDao

    abstract fun calendarLinkDao(): CalendarLinkDao

    // Platform
    abstract fun userProfileDao(): UserProfileDao

    abstract fun appSettingDao(): AppSettingDao

    abstract fun activityLogDao(): ActivityLogDao

    // Salah
    abstract fun prayerSettingsDao(): PrayerSettingsDao

    abstract fun prayerTrackingDao(): PrayerTrackingDao

    companion object {
        const val NAME = "mira.db"

        /**
         * 1: tasks, reminders, vault, flat conversation turns. 8: full record set with portable
         * backup. 9: Salah — prayer settings and the prayer-tracking log. 10: the record-driven
         * automation fire ledger (`automation_record_fires`) that dedups record-triggered rules.
         */
        const val VERSION = 10
    }
}
