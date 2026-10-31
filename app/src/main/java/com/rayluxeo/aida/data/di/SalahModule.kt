package com.rayluxeo.aida.data.di

import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.db.dao.PrayerSettingsDao
import com.rayluxeo.aida.data.db.dao.PrayerTrackingDao
import com.rayluxeo.aida.data.repository.SalahSettingsRepositoryImpl
import com.rayluxeo.aida.data.repository.SalahTrackingRepositoryImpl
import com.rayluxeo.aida.domain.salah.SalahSettingsRepository
import com.rayluxeo.aida.domain.salah.SalahTrackingRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** The Salah DAOs, drawn from the one database. */
@Module
@InstallIn(SingletonComponent::class)
object SalahDaoModule {

    @Provides
    fun providePrayerSettingsDao(database: MiraDatabase): PrayerSettingsDao = database.prayerSettingsDao()

    @Provides
    fun providePrayerTrackingDao(database: MiraDatabase): PrayerTrackingDao = database.prayerTrackingDao()
}

/** The Salah repository ports, bound to their Room-backed implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SalahBindingsModule {

    @Binds
    @Singleton
    abstract fun bindSalahSettingsRepository(impl: SalahSettingsRepositoryImpl): SalahSettingsRepository

    @Binds
    @Singleton
    abstract fun bindSalahTrackingRepository(impl: SalahTrackingRepositoryImpl): SalahTrackingRepository
}
