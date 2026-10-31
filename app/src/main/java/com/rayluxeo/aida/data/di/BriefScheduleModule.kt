package com.rayluxeo.aida.data.di

import com.rayluxeo.aida.data.prefs.BriefScheduleRepositoryImpl
import com.rayluxeo.aida.data.scheduler.BriefSchedulerImpl
import com.rayluxeo.aida.domain.brief.BriefScheduleRepository
import com.rayluxeo.aida.domain.brief.BriefScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The two bindings behind Mira's scheduled wake-ups: when Abu wants them, and the alarms that
 * deliver them.
 *
 * It sits apart from `DataModule` for the same reason the other area modules do — a file every
 * agent edits is a file every agent conflicts in. The `DataStore<Preferences>`, the `MiraClock`
 * and the `DispatcherProvider` these take are already provided by `DataModule`, and providing any
 * of them again here would be a duplicate binding.
 *
 * The engines themselves are bound elsewhere: this module knows only when a brief happens, never
 * what it says.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BriefScheduleModule {

    @Binds
    @Singleton
    abstract fun bindBriefScheduleRepository(
        impl: BriefScheduleRepositoryImpl,
    ): BriefScheduleRepository

    @Binds
    @Singleton
    abstract fun bindBriefScheduler(impl: BriefSchedulerImpl): BriefScheduler
}
