package com.rayluxeo.aida.data.di

import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.db.dao.CalendarLinkDao
import com.rayluxeo.aida.data.db.dao.MeetingDao
import com.rayluxeo.aida.data.repository.CalendarLinkRepositoryImpl
import com.rayluxeo.aida.data.repository.MeetingRepositoryImpl
import com.rayluxeo.aida.domain.repository.CalendarLinkRepository
import com.rayluxeo.aida.domain.repository.MeetingRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Meetings and calendar links.
 *
 * They live in their own module rather than in `DataModule` because they are one area's storage
 * and `DataModule` is everything's: a file that every agent edits is a file every agent conflicts
 * in. The DAOs are unscoped — Room already returns the same instance from the database.
 */
@Module
@InstallIn(SingletonComponent::class)
object CalendarDaoModule {

    @Provides
    fun provideMeetingDao(database: MiraDatabase): MeetingDao = database.meetingDao()

    @Provides
    fun provideCalendarLinkDao(database: MiraDatabase): CalendarLinkDao = database.calendarLinkDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CalendarModule {

    @Binds
    @Singleton
    abstract fun bindMeetingRepository(impl: MeetingRepositoryImpl): MeetingRepository

    @Binds
    @Singleton
    abstract fun bindCalendarLinkRepository(impl: CalendarLinkRepositoryImpl): CalendarLinkRepository
}
