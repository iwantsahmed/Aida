package com.rayluxeo.aida.data.di

import com.rayluxeo.aida.data.ota.GithubUpdateRepositoryImpl
import com.rayluxeo.aida.domain.ota.UpdateRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateModule {

    @Binds
    @Singleton
    abstract fun bindUpdateRepository(impl: GithubUpdateRepositoryImpl): UpdateRepository
}
