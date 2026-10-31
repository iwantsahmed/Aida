package com.rayluxeo.aida.data.di

import com.rayluxeo.aida.data.repository.AlarmRepositoryImpl
import com.rayluxeo.aida.data.repository.ReminderPlaceRepositoryImpl
import com.rayluxeo.aida.data.scheduler.AlarmClockSchedulerImpl
import com.rayluxeo.aida.data.scheduler.ExactAlarmAccessImpl
import com.rayluxeo.aida.data.scheduler.PlaceReminderSchedulerImpl
import com.rayluxeo.aida.data.scheduler.TimerSchedulerImpl
import com.rayluxeo.aida.domain.repository.AlarmRepository
import com.rayluxeo.aida.domain.repository.ReminderPlaceRepository
import com.rayluxeo.aida.domain.scheduler.AlarmClockScheduler
import com.rayluxeo.aida.domain.scheduler.ExactAlarmAccess
import com.rayluxeo.aida.domain.scheduler.PlaceReminderScheduler
import com.rayluxeo.aida.domain.scheduler.ReminderNotificationWording
import com.rayluxeo.aida.domain.scheduler.TimerScheduler
import com.rayluxeo.aida.ui.response.ReminderNotificationWordingImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Everything that asks the operating system to wake Mira up later.
 *
 * One module rather than one per scheduler, because these four make a single decision together:
 * which of them is allowed to promise a minute. They all read the same [ExactAlarmAccess], and
 * keeping the bindings in one file is what makes that visible.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SchedulerModule {

    /**
     * Singleton: the receiver behind [ExactAlarmAccess.changes] is registered per instance, and
     * every scheduler in the app is asking the same question of the same platform state.
     */
    @Binds
    @Singleton
    abstract fun bindExactAlarmAccess(impl: ExactAlarmAccessImpl): ExactAlarmAccess

    @Binds
    @Singleton
    abstract fun bindAlarmClockScheduler(impl: AlarmClockSchedulerImpl): AlarmClockScheduler

    @Binds
    @Singleton
    abstract fun bindTimerScheduler(impl: TimerSchedulerImpl): TimerScheduler

    @Binds
    @Singleton
    abstract fun bindPlaceReminderScheduler(impl: PlaceReminderSchedulerImpl): PlaceReminderScheduler

    @Binds
    @Singleton
    abstract fun bindAlarmRepository(impl: AlarmRepositoryImpl): AlarmRepository

    @Binds
    @Singleton
    abstract fun bindReminderPlaceRepository(impl: ReminderPlaceRepositoryImpl): ReminderPlaceRepository

    /**
     * The notification's words live in `ui/response` beside the rest of Mira's wording, while the
     * receiver that needs them is in `data/scheduler`. The port in `domain` is what keeps that
     * from being a dependency pointing outward (§4).
     */
    @Binds
    @Singleton
    abstract fun bindReminderNotificationWording(
        impl: ReminderNotificationWordingImpl,
    ): ReminderNotificationWording
}
