package com.rayluxeo.aida.data.di

import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.db.dao.SavedPlaceDao
import com.rayluxeo.aida.data.location.CurrentPlaceFinderImpl
import com.rayluxeo.aida.data.repository.SavedPlaceRepositoryImpl
import com.rayluxeo.aida.domain.location.CurrentPlaceFinder
import com.rayluxeo.aida.domain.repository.SavedPlaceRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Abu's named places: the directory and the one thing that can fill a new entry in.
 *
 * Its own file rather than an addition to [SchedulerModule], whose stated job is everything that
 * asks the operating system to wake Mira up later — a place directory does not, and a single fix
 * taken while Abu is watching does not either. These two change together instead: a place is saved
 * from a fix, and both go through `LocationAvailability` for the same answers about this phone.
 * The database itself still comes from [DataModule] — there is one.
 */
@Module
@InstallIn(SingletonComponent::class)
object PlaceModule {

    @Provides
    fun provideSavedPlaceDao(database: MiraDatabase): SavedPlaceDao = database.savedPlaceDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaceBindingsModule {

    @Binds
    @Singleton
    abstract fun bindSavedPlaceRepository(impl: SavedPlaceRepositoryImpl): SavedPlaceRepository

    @Binds
    @Singleton
    abstract fun bindCurrentPlaceFinder(impl: CurrentPlaceFinderImpl): CurrentPlaceFinder
}
