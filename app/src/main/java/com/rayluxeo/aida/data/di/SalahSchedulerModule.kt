package com.rayluxeo.aida.data.di

import com.rayluxeo.aida.data.scheduler.PrayerSchedulerImpl
import com.rayluxeo.aida.domain.salah.PrayerScheduler
import com.rayluxeo.aida.domain.salah.SalahNotificationWording
import com.rayluxeo.aida.ui.response.SalahNotificationWordingImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The Salah scheduling and notification ports, bound to their implementations.
 *
 * Its own module rather than a few lines in [SalahModule], which the lead owns: the prayer alarms
 * belong with the other schedulers conceptually but are added here so that file stays untouched.
 * [PrayerSchedulerImpl]'s `PrayerAlarms` collaborator needs no binding of its own — Hilt builds a
 * concrete `@Inject`-constructed class directly.
 *
 * The wording binding mirrors the reminder one: the words live in `ui/response` beside the rest of
 * Mira's wording, the port in `domain` keeps the posting code in `data/scheduler` from importing
 * `ui`, and this is where the two are tied together (§4).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SalahSchedulerModule {

    @Binds
    @Singleton
    abstract fun bindPrayerScheduler(impl: PrayerSchedulerImpl): PrayerScheduler

    @Binds
    @Singleton
    abstract fun bindSalahNotificationWording(impl: SalahNotificationWordingImpl): SalahNotificationWording
}
