package com.rayluxeo.aida.voice.di

import com.rayluxeo.aida.voice.SpeechRecognitionManager
import com.rayluxeo.aida.voice.SpeechRecognitionManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds Mira's listening side.
 *
 * The implementation is the one place that opens and releases an
 * [android.speech.SpeechRecognizer], and a second live instance would be a second claim on the
 * microphone, so it is `@Singleton` in its own right and the alias here is deliberately unscoped.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SpeechModule {

    @Binds
    abstract fun bindSpeechRecognitionManager(
        impl: SpeechRecognitionManagerImpl,
    ): SpeechRecognitionManager
}
