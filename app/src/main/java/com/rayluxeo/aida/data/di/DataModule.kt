package com.rayluxeo.aida.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.rayluxeo.aida.core.common.DefaultDispatcherProvider
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.SystemMiraClock
import com.rayluxeo.aida.data.crypto.MiraKeystore
import com.rayluxeo.aida.data.crypto.VaultKeyProvider
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.db.MiraDatabaseCallback
import com.rayluxeo.aida.data.db.MiraMigrations
import com.rayluxeo.aida.data.db.dao.ConversationDao
import com.rayluxeo.aida.data.db.dao.AlarmDao
import com.rayluxeo.aida.data.db.dao.ReminderPlaceDao
import com.rayluxeo.aida.data.db.dao.ReminderDao
import com.rayluxeo.aida.data.db.dao.TaskDao
import com.rayluxeo.aida.data.db.dao.VaultDao
import com.rayluxeo.aida.data.prefs.GeminiCredentialStoreImpl
import com.rayluxeo.aida.data.prefs.MiraPreferences
import com.rayluxeo.aida.data.prefs.SettingsRepositoryImpl
import com.rayluxeo.aida.data.repository.BriefRepositoryImpl
import com.rayluxeo.aida.data.repository.ConversationRepositoryImpl
import com.rayluxeo.aida.data.repository.ReminderRepositoryImpl
import com.rayluxeo.aida.data.repository.TagRepositoryImpl
import com.rayluxeo.aida.domain.repository.TagRepository
import com.rayluxeo.aida.data.repository.TaskRepositoryImpl
import com.rayluxeo.aida.data.repository.VaultRepositoryImpl
import com.rayluxeo.aida.data.scheduler.ReminderSchedulerImpl
import com.rayluxeo.aida.domain.repository.BriefRepository
import com.rayluxeo.aida.domain.repository.ConversationRepository
import com.rayluxeo.aida.domain.repository.GeminiCredentialStore
import com.rayluxeo.aida.domain.repository.ReminderRepository
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.repository.TaskRepository
import com.rayluxeo.aida.domain.repository.VaultRepository
import com.rayluxeo.aida.domain.scheduler.ReminderScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * No destructive-migration fallback is configured anywhere here. Abu's tasks and vault are not
 * reproducible, so a future schema change must arrive as a written migration or fail loudly.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MiraDatabase =
        Room.databaseBuilder(context, MiraDatabase::class.java, MiraDatabase.NAME)
            .addMigrations(*MiraMigrations.all)
            .addCallback(MiraDatabaseCallback)
            // Deliberately no fallbackToDestructiveMigration: a missing migration must fail
            // loudly here rather than quietly deleting Abu's invoices and vault.
            .build()

    @Provides
    fun provideTaskDao(database: MiraDatabase): TaskDao = database.taskDao()

    @Provides
    fun provideReminderDao(database: MiraDatabase): ReminderDao = database.reminderDao()

    @Provides
    fun provideAlarmDao(database: MiraDatabase): AlarmDao = database.alarmDao()

    @Provides
    fun provideReminderPlaceDao(database: MiraDatabase): ReminderPlaceDao =
        database.reminderPlaceDao()

    @Provides
    fun provideVaultDao(database: MiraDatabase): VaultDao = database.vaultDao()

    @Provides
    fun provideConversationDao(database: MiraDatabase): ConversationDao = database.conversationDao()

    @Provides
    @Singleton
    fun provideClock(): MiraClock = SystemMiraClock()

    @Provides
    @Singleton
    fun provideDispatchers(): DispatcherProvider = DefaultDispatcherProvider()

    @Provides
    @Singleton
    fun providePreferences(preferences: MiraPreferences): DataStore<Preferences> = preferences.store
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindingsModule {

    @Binds
    @Singleton
    abstract fun bindVaultKeyProvider(impl: MiraKeystore): VaultKeyProvider

    @Binds
    @Singleton
    abstract fun bindTaskRepository(impl: TaskRepositoryImpl): TaskRepository

    @Binds
    @Singleton
    abstract fun bindTagRepository(impl: TagRepositoryImpl): TagRepository

    @Binds
    @Singleton
    abstract fun bindReminderRepository(impl: ReminderRepositoryImpl): ReminderRepository

    @Binds
    @Singleton
    abstract fun bindVaultRepository(impl: VaultRepositoryImpl): VaultRepository

    @Binds
    @Singleton
    abstract fun bindConversationRepository(impl: ConversationRepositoryImpl): ConversationRepository

    @Binds
    @Singleton
    abstract fun bindBriefRepository(impl: BriefRepositoryImpl): BriefRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindGeminiCredentialStore(impl: GeminiCredentialStoreImpl): GeminiCredentialStore

    @Binds
    @Singleton
    abstract fun bindReminderScheduler(impl: ReminderSchedulerImpl): ReminderScheduler

    @Binds
    @Singleton
    abstract fun bindAutomationDeviceGateway(
        impl: com.rayluxeo.aida.data.automation.AndroidAutomationDeviceGateway,
    ): com.rayluxeo.aida.domain.automation.AutomationDeviceGateway

    @Binds
    @Singleton
    abstract fun bindAutomationAlarmScheduler(
        impl: com.rayluxeo.aida.data.automation.AutomationAlarmSchedulerImpl,
    ): com.rayluxeo.aida.domain.automation.AutomationAlarmScheduler

    @Binds
    @Singleton
    abstract fun bindAutomationPlaceScheduler(
        impl: com.rayluxeo.aida.data.automation.AutomationPlaceSchedulerImpl,
    ): com.rayluxeo.aida.domain.automation.AutomationPlaceScheduler

    @Binds
    @Singleton
    abstract fun bindInstalledAppCatalog(
        impl: com.rayluxeo.aida.data.automation.AndroidInstalledAppCatalog,
    ): com.rayluxeo.aida.domain.automation.InstalledAppCatalog

    @Binds
    @Singleton
    abstract fun bindCrossAppAccess(
        impl: com.rayluxeo.aida.data.automation.AndroidCrossAppAccess,
    ): com.rayluxeo.aida.domain.automation.CrossAppAccess

    @Binds
    @Singleton
    abstract fun bindBackupRepository(impl: com.rayluxeo.aida.data.repository.BackupRepositoryImpl): com.rayluxeo.aida.domain.repository.BackupRepository

    @Binds
    @Singleton
    abstract fun bindLocalDataEraser(impl: com.rayluxeo.aida.data.privacy.LocalDataEraserImpl): com.rayluxeo.aida.domain.repository.LocalDataEraser
}
