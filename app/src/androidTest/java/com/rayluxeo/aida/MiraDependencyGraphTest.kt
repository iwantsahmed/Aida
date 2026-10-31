package com.rayluxeo.aida

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.ai.CommandRouter
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.di.GeminiEngine
import com.rayluxeo.aida.di.LocalEngine
import com.rayluxeo.aida.domain.command.CommandDispatcher
import com.rayluxeo.aida.domain.command.CommandEngine
import com.rayluxeo.aida.domain.command.CommandValidator
import com.rayluxeo.aida.domain.finance.Calculator
import com.rayluxeo.aida.domain.nlu.DateTimeParser
import com.rayluxeo.aida.domain.nlu.TextNormalizer
import com.rayluxeo.aida.domain.repository.BriefRepository
import com.rayluxeo.aida.domain.repository.ConversationRepository
import com.rayluxeo.aida.domain.repository.GeminiCredentialStore
import com.rayluxeo.aida.domain.repository.ReminderRepository
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.repository.TaskRepository
import com.rayluxeo.aida.domain.repository.VaultRepository
import com.rayluxeo.aida.domain.scheduler.ReminderScheduler
import com.rayluxeo.aida.domain.usecase.ExecuteCommandUseCase
import com.rayluxeo.aida.ui.response.ResponseRenderer
import com.rayluxeo.aida.domain.repository.VoicePreferenceRepository
import com.rayluxeo.aida.voice.MiraAudioFocusManager
import com.rayluxeo.aida.voice.MiraEarconPlayer
import com.rayluxeo.aida.voice.MiraTextToSpeech
import com.rayluxeo.aida.voice.SpeechRecognitionManager
import com.rayluxeo.aida.voice.VoiceConversationController
import com.rayluxeo.aida.voice.VoicePersonaManager
import com.rayluxeo.aida.voice.VoiceResponseFormatter
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves every seam in Mira is actually bound. A missing Hilt binding otherwise only shows up
 * when Abu opens the screen that needs it, which is far too late.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MiraDependencyGraphTest {

    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject lateinit var clock: MiraClock
    @Inject lateinit var dispatchers: DispatcherProvider

    @Inject lateinit var tasks: TaskRepository
    @Inject lateinit var reminders: ReminderRepository
    @Inject lateinit var vault: VaultRepository
    @Inject lateinit var conversation: ConversationRepository
    @Inject lateinit var briefs: BriefRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var geminiCredentials: GeminiCredentialStore
    @Inject lateinit var scheduler: ReminderScheduler

    @Inject lateinit var normalizer: TextNormalizer
    @Inject lateinit var dateTimeParser: DateTimeParser
    @Inject lateinit var calculator: Calculator
    @Inject lateinit var renderer: ResponseRenderer

    @Inject @LocalEngine lateinit var localEngine: CommandEngine
    @Inject @GeminiEngine lateinit var geminiEngine: CommandEngine
    @Inject lateinit var router: CommandRouter
    @Inject lateinit var validator: CommandValidator
    @Inject lateinit var commandDispatcher: CommandDispatcher
    @Inject lateinit var executeCommand: ExecuteCommandUseCase

    @Inject lateinit var voice: MiraTextToSpeech
    @Inject lateinit var personas: VoicePersonaManager
    @Inject lateinit var speech: SpeechRecognitionManager
    @Inject lateinit var earcons: MiraEarconPlayer
    @Inject lateinit var audioFocus: MiraAudioFocusManager
    @Inject lateinit var segmentation: VoiceResponseFormatter
    @Inject lateinit var voicePreferences: VoicePreferenceRepository
    @Inject lateinit var conversationController: VoiceConversationController

    @Before fun inject() = hilt.inject()

    @Test
    fun everySeamIsBound() {
        assertThat(
            listOf<Any>(
                clock, dispatchers,
                tasks, reminders, vault, conversation, briefs, settings, geminiCredentials, scheduler,
                normalizer, dateTimeParser, calculator, renderer,
                localEngine, geminiEngine, router, validator, commandDispatcher, executeCommand,
                voice, personas, speech, earcons, audioFocus, segmentation,
                voicePreferences, conversationController,
            )
        ).hasSize(28)
    }

    @Test
    fun engineQualifiersResolveToDistinctEngines() {
        assertThat(localEngine.id).isNotEqualTo(geminiEngine.id)
    }
}
