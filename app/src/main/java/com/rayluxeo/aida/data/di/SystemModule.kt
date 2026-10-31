package com.rayluxeo.aida.data.di

import com.rayluxeo.aida.data.system.CalendarRepositoryImpl
import com.rayluxeo.aida.data.system.ContactsRepositoryImpl
import com.rayluxeo.aida.domain.command.handler.DefaultPhoneCommandHandler
import com.rayluxeo.aida.domain.command.handler.PhoneCommandHandler
import com.rayluxeo.aida.domain.repository.CalendarRepository
import com.rayluxeo.aida.domain.repository.ContactsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The parts of the phone that are not Mira's: its address book and its calendars, and the handler
 * that reaches into them.
 *
 * These two repositories are the only ones in the app backed by a system ContentProvider rather
 * than by Room, which is why they bind here instead of alongside the database modules — nothing
 * they return is ever written to Mira's own tables.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SystemModule {

    @Binds
    @Singleton
    abstract fun bindContactsRepository(impl: ContactsRepositoryImpl): ContactsRepository

    @Binds
    @Singleton
    abstract fun bindCalendarRepository(impl: CalendarRepositoryImpl): CalendarRepository

    @Binds
    @Singleton
    abstract fun bindPhoneCommandHandler(impl: DefaultPhoneCommandHandler): PhoneCommandHandler
}
