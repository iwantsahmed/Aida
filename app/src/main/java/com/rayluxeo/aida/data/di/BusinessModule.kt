package com.rayluxeo.aida.data.di

import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.db.dao.ClientDao
import com.rayluxeo.aida.data.db.dao.ProjectDao
import com.rayluxeo.aida.data.repository.ClientRepositoryImpl
import com.rayluxeo.aida.data.repository.ProjectRepositoryImpl
import com.rayluxeo.aida.domain.repository.ClientRepository
import com.rayluxeo.aida.domain.repository.ProjectRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The clients and projects side of the data layer.
 *
 * It lives beside the code it binds rather than in the shared [DataModule], so the business
 * records can grow their own bindings without every area editing one file.
 */
@Module
@InstallIn(SingletonComponent::class)
object BusinessModule {

    @Provides
    fun provideClientDao(database: MiraDatabase): ClientDao = database.clientDao()

    @Provides
    fun provideProjectDao(database: MiraDatabase): ProjectDao = database.projectDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BusinessBindingsModule {

    @Binds
    @Singleton
    abstract fun bindClientRepository(impl: ClientRepositoryImpl): ClientRepository

    @Binds
    @Singleton
    abstract fun bindProjectRepository(impl: ProjectRepositoryImpl): ProjectRepository
}
