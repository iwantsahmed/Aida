package com.rayluxeo.aida.voice.di

import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.repository.VoicePreferenceRepository
import com.rayluxeo.aida.domain.usecase.ExecuteCommandUseCase
import com.rayluxeo.aida.ui.response.ResponseRenderer
import com.rayluxeo.aida.voice.MiraAudioFocusManager
import com.rayluxeo.aida.voice.MiraEarconPlayer
import com.rayluxeo.aida.voice.MiraTextToSpeech
import com.rayluxeo.aida.voice.SpeechRecognitionManager
import com.rayluxeo.aida.voice.VoiceConversationController
import com.rayluxeo.aida.voice.VoiceConversationControllerImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Wires the spoken turn.
 *
 * It is assembled by hand rather than bound from an `@Inject` constructor because the controller
 * needs a [CoroutineScope], and a scope is not something Hilt can hand out sensibly: the choice of
 * dispatcher is part of the controller's contract with itself, not a dependency it happens to take.
 *
 * The scope is deliberately main-dispatched. The controller's state machine is driven from the
 * screen on the main thread and keeps its bookkeeping in plain fields; a background dispatcher
 * would put those fields on two threads at once. It is also process-lifetime and singleton, so a
 * turn already in flight survives the screen rotating under it — the microphone, the audio focus
 * and the engine connection are all released when the collections inside it end, and a
 * [VoiceConversationController.cancel] from the screen ends them early.
 */
@Module
@InstallIn(SingletonComponent::class)
object ConversationModule {

    @Provides
    @Singleton
    fun provideVoiceConversationController(
        recognizer: SpeechRecognitionManager,
        voice: MiraTextToSpeech,
        earcons: MiraEarconPlayer,
        audioFocus: MiraAudioFocusManager,
        execute: ExecuteCommandUseCase,
        renderer: ResponseRenderer,
        settings: SettingsRepository,
        voicePreferences: VoicePreferenceRepository,
        dispatchers: DispatcherProvider,
    ): VoiceConversationController = VoiceConversationControllerImpl(
        recognizer = recognizer,
        voice = voice,
        earcons = earcons,
        audioFocus = audioFocus,
        execute = execute,
        renderer = renderer,
        settings = settings,
        voicePreferences = voicePreferences,
        scope = CoroutineScope(SupervisorJob() + dispatchers.main),
    )
}
