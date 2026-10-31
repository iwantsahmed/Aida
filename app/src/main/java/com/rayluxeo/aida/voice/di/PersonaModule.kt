package com.rayluxeo.aida.voice.di

import com.rayluxeo.aida.voice.VoicePersonaManager
import com.rayluxeo.aida.voice.VoicePersonaManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Voice discovery and auditioning.
 *
 * Singleton rather than screen-scoped because the implementation holds a bound TTS engine while
 * Abu is comparing samples; one owner for that binding is what makes `release()` mean something.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PersonaModule {

    // Unscoped alias: the implementation carries @Singleton itself, which is where the one
    // bound engine lives. Scoping both says the same thing twice, and the other voice modules
    // all state it in the one place.
    @Binds
    abstract fun bindVoicePersonaManager(impl: VoicePersonaManagerImpl): VoicePersonaManager
}
