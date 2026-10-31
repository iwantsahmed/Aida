package com.rayluxeo.aida.data.work.di

import android.content.Context
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The one binding deferred work needs: WorkManager itself.
 *
 * It is provided rather than resolved at each call site so that anything scheduling work takes it
 * as a dependency instead of reaching for a `Context` and a static getter — the same reason every
 * other system service in Mira arrives through a port. `DerivedReminderWorker` is not bound here:
 * `@HiltWorker` generates its own factory entry, and the worker is created by `HiltWorkerFactory`.
 *
 * [WorkManager.getInstance] is what starts WorkManager on demand, which requires `MiraApplication`
 * to supply the `HiltWorkerFactory` through `androidx.work.Configuration.Provider` and the
 * manifest to remove the default initializer. Without both, the first worker to run cannot be
 * constructed and fails with a missing no-argument constructor.
 */
@Module
@InstallIn(SingletonComponent::class)
object WorkModule {

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}
