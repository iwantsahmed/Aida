package com.rayluxeo.aida.data.di

import com.rayluxeo.aida.data.repository.QuranRepositoryImpl
import com.rayluxeo.aida.domain.quran.QuranRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class QuranModule {

    @Binds
    @Singleton
    abstract fun bindQuranRepository(impl: QuranRepositoryImpl): QuranRepository
}
