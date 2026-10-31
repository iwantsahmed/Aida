package com.rayluxeo.aida.voice.di

import com.rayluxeo.aida.voice.MiraTextToSpeech
import com.rayluxeo.aida.voice.MiraTextToSpeechImpl
import com.rayluxeo.aida.voice.VoiceResponseFormatter
import com.rayluxeo.aida.voice.VoiceResponseFormatterImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds Mira's speaking side.
 *
 * Both implementations are singletons in their own right — one holds the process's single engine
 * connection, the other is stateless and worth building once — so the aliases here are unscoped.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TtsModule {

    @Binds
    abstract fun bindTextToSpeech(tts: MiraTextToSpeechImpl): MiraTextToSpeech

    @Binds
    abstract fun bindResponseFormatter(formatter: VoiceResponseFormatterImpl): VoiceResponseFormatter
}
