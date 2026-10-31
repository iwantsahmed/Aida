package com.rayluxeo.aida.data.di

import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.db.dao.AutomationDao
import com.rayluxeo.aida.data.db.dao.AutomationFireDao
import com.rayluxeo.aida.data.repository.AutomationFireLedgerRepositoryImpl
import com.rayluxeo.aida.data.repository.AutomationRepositoryImpl
import com.rayluxeo.aida.data.repository.ConversationThreadRepositoryImpl
import com.rayluxeo.aida.domain.repository.AutomationFireLedgerRepository
import com.rayluxeo.aida.domain.repository.AutomationRepository
import com.rayluxeo.aida.domain.repository.ConversationThreadRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Mira's own records: conversation threads and automations.
 *
 * `ConversationDao` is not provided here — `DataModule` already binds it for the live transcript,
 * and a second provider for the same type is a duplicate-binding error. Both conversation
 * repositories take the one DAO.
 */
@Module
@InstallIn(SingletonComponent::class)
object AssistantModule {

    @Provides
    fun provideAutomationDao(database: MiraDatabase): AutomationDao = database.automationDao()

    @Provides
    fun provideAutomationFireDao(database: MiraDatabase): AutomationFireDao = database.automationFireDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AssistantBindingsModule {

    @Binds
    @Singleton
    abstract fun bindConversationThreadRepository(
        impl: ConversationThreadRepositoryImpl,
    ): ConversationThreadRepository

    @Binds
    @Singleton
    abstract fun bindAutomationRepository(impl: AutomationRepositoryImpl): AutomationRepository

    @Binds
    @Singleton
    abstract fun bindAutomationFireLedgerRepository(
        impl: AutomationFireLedgerRepositoryImpl,
    ): AutomationFireLedgerRepository
}
