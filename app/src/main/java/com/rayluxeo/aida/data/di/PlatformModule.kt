package com.rayluxeo.aida.data.di

import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.db.dao.ActivityLogDao
import com.rayluxeo.aida.data.db.dao.AppSettingDao
import com.rayluxeo.aida.data.db.dao.FileDao
import com.rayluxeo.aida.data.db.dao.UserProfileDao
import com.rayluxeo.aida.data.repository.ActivityLogRepositoryImpl
import com.rayluxeo.aida.data.repository.AppSettingRepositoryImpl
import com.rayluxeo.aida.data.repository.FileRepositoryImpl
import com.rayluxeo.aida.data.repository.UserProfileRepositoryImpl
import com.rayluxeo.aida.domain.repository.ActivityLogRepository
import com.rayluxeo.aida.domain.repository.AppSettingRepository
import com.rayluxeo.aida.domain.repository.FileRepository
import com.rayluxeo.aida.domain.repository.UserProfileRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Files, scans, Abu's profile, the database-side settings and the activity log.
 *
 * Kept apart from `DataModule` because these are the records the rest of the app stands on rather
 * than a feature of its own, and because one module per cluster is what keeps several people able
 * to add bindings without editing the same file.
 */
@Module
@InstallIn(SingletonComponent::class)
object PlatformModule {

    @Provides
    fun provideFileDao(database: MiraDatabase): FileDao = database.fileDao()

    @Provides
    fun provideUserProfileDao(database: MiraDatabase): UserProfileDao = database.userProfileDao()

    @Provides
    fun provideAppSettingDao(database: MiraDatabase): AppSettingDao = database.appSettingDao()

    @Provides
    fun provideActivityLogDao(database: MiraDatabase): ActivityLogDao = database.activityLogDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PlatformBindingsModule {

    @Binds
    @Singleton
    abstract fun bindFileRepository(impl: FileRepositoryImpl): FileRepository

    @Binds
    @Singleton
    abstract fun bindUserProfileRepository(impl: UserProfileRepositoryImpl): UserProfileRepository

    @Binds
    @Singleton
    abstract fun bindAppSettingRepository(impl: AppSettingRepositoryImpl): AppSettingRepository

    @Binds
    @Singleton
    abstract fun bindActivityLogRepository(impl: ActivityLogRepositoryImpl): ActivityLogRepository

    @Binds
    @Singleton
    abstract fun bindAttentionDismissalRepository(
        impl: com.rayluxeo.aida.data.repository.AttentionDismissalRepositoryImpl,
    ): com.rayluxeo.aida.domain.attention.AttentionDismissalRepository
}

