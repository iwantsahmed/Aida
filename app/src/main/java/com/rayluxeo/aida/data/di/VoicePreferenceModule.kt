package com.rayluxeo.aida.data.di

import com.rayluxeo.aida.data.prefs.VoicePreferenceRepositoryImpl
import com.rayluxeo.aida.domain.repository.VoicePreferenceRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The one binding for how Mira sounds.
 *
 * It sits apart from `DataModule` for the same reason the other area modules do: a file every
 * agent edits is a file every agent conflicts in. The `DataStore<Preferences>` the implementation
 * takes is already provided by `DataModule`, and providing it a second time here would be a
 * duplicate binding.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class VoicePreferenceModule {

    @Binds
    @Singleton
    abstract fun bindVoicePreferenceRepository(
        impl: VoicePreferenceRepositoryImpl,
    ): VoicePreferenceRepository
}
