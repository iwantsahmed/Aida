package com.rayluxeo.aida.data.di

import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.db.dao.AlarmRecordDao
import com.rayluxeo.aida.data.db.dao.SubtaskDao
import com.rayluxeo.aida.data.db.dao.TimerDao
import com.rayluxeo.aida.data.repository.AlarmRecordRepositoryImpl
import com.rayluxeo.aida.data.repository.SubtaskRepositoryImpl
import com.rayluxeo.aida.data.repository.TimerRepositoryImpl
import com.rayluxeo.aida.domain.repository.AlarmRecordRepository
import com.rayluxeo.aida.domain.repository.SubtaskRepository
import com.rayluxeo.aida.domain.repository.TimerRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Subtasks, alarm records and timers: the three tables that sit beside a task rather than inside
 * it. They live in their own module rather than in `DataModule` because that file is everything's
 * and a file every agent edits is a file every agent conflicts in. The DAOs are unscoped — Room
 * already returns the same instance from the database.
 */
@Module
@InstallIn(SingletonComponent::class)
object WorkDaoModule {

    @Provides
    fun provideSubtaskDao(database: MiraDatabase): SubtaskDao = database.subtaskDao()

    @Provides
    fun provideAlarmRecordDao(database: MiraDatabase): AlarmRecordDao = database.alarmRecordDao()

    @Provides
    fun provideTimerDao(database: MiraDatabase): TimerDao = database.timerDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkModule {

    @Binds
    @Singleton
    abstract fun bindSubtaskRepository(impl: SubtaskRepositoryImpl): SubtaskRepository

    @Binds
    @Singleton
    abstract fun bindAlarmRecordRepository(impl: AlarmRecordRepositoryImpl): AlarmRecordRepository

    @Binds
    @Singleton
    abstract fun bindTimerRepository(impl: TimerRepositoryImpl): TimerRepository
}
