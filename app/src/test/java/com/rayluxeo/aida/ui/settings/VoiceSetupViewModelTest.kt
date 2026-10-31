package com.rayluxeo.aida.ui.settings

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.repository.MiraSettings
import com.rayluxeo.aida.domain.repository.VoicePreferenceRepository
import com.rayluxeo.aida.domain.repository.VoicePreferences
import com.rayluxeo.aida.voice.TtsEngineInfo
import com.rayluxeo.aida.voice.TtsVoiceInfo
import com.rayluxeo.aida.voice.VoicePersonaManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Voice setup is the one flow where being wrong is silent — Mira simply never speaks — so these
 * tests pin the things that decide that: which engine is asked, which voice is heard, and what is
 * written down.
 *
 * The screen itself needs an instrumented test: `TextToSpeech` cannot be constructed here, so
 * whether a sample actually comes out of the speaker is not something this file can answer.
 */
class VoiceSetupViewModelTest {

    private val personas = FakeVoicePersonas()
    private val settings = FakeSettingsRepository()

    @Before
    fun installMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun removeMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the pinned engine's voices are listed per language`() = runTest {
        personas.installedEngines = listOf(GOOGLE, SAMSUNG)
        personas.catalogue = mapOf(
            GOOGLE.packageName to mapOf(
                SpokenLanguage.ENGLISH to listOf(voiceInfo("en-us-x-a", "en-US")),
                SpokenLanguage.BENGALI to listOf(voiceInfo("bn-bd-x-a", "bn-BD")),
            ),
        )
        val viewModel = viewModel(VoicePreferences(engine = GOOGLE.packageName))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.loading).isFalse()
        assertThat(state.engines).containsExactly(GOOGLE, SAMSUNG).inOrder()
        assertThat(state.englishVoices.map { it.name }).containsExactly("en-us-x-a")
        assertThat(state.bengaliVoices.map { it.name }).containsExactly("bn-bd-x-a")
        assertThat(state.bengaliMissing).isFalse()
    }

    @Test
    fun `tapping a voice both plays it and keeps it, and that finishes setup`() = runTest {
        personas.installedEngines = listOf(GOOGLE)
        personas.catalogue = mapOf(
            GOOGLE.packageName to mapOf(
                SpokenLanguage.ENGLISH to listOf(voiceInfo("en-us-x-a", "en-US")),
            ),
        )
        val stored = InMemoryVoicePreferences(VoicePreferences(engine = GOOGLE.packageName))
        val viewModel = VoiceSetupViewModel(personas, stored, settings)
        advanceUntilIdle()

        viewModel.chooseVoice("en-us-x-a", SpokenLanguage.ENGLISH)
        advanceUntilIdle()

        assertThat(personas.previews.map { it.voiceId }).containsExactly("en-us-x-a")
        assertThat(stored.saved.englishVoiceId).isEqualTo("en-us-x-a")
        assertThat(stored.saved.bengaliVoiceId).isNull()
        assertThat(stored.saved.setupComplete).isTrue()
        assertThat(viewModel.state.value.previewing).isNull()
    }

    @Test
    fun `an engine with no Bengali voice says so rather than showing an empty list`() = runTest {
        personas.installedEngines = listOf(SAMSUNG)
        personas.catalogue = mapOf(
            SAMSUNG.packageName to mapOf(
                SpokenLanguage.ENGLISH to listOf(voiceInfo("en-gb-x-a", "en-GB")),
            ),
        )
        val viewModel = viewModel(VoicePreferences(engine = SAMSUNG.packageName))
        advanceUntilIdle()

        assertThat(viewModel.state.value.bengaliMissing).isTrue()
        assertThat(viewModel.state.value.englishMissing).isFalse()
    }

    @Test
    fun `nothing is missing until the engine has actually been asked`() = runTest {
        personas.installedEngines = listOf(GOOGLE)
        val viewModel = viewModel(VoicePreferences(engine = GOOGLE.packageName))

        // Before the answer comes back an empty list is ignorance, not a fact about the device.
        assertThat(VoiceSetupUiState(voicesLoading = true).bengaliMissing).isFalse()
        advanceUntilIdle()
        assertThat(viewModel.state.value.bengaliMissing).isTrue()
    }

    @Test
    fun `switching engine drops the voices only the old engine knew and reopens setup`() = runTest {
        personas.installedEngines = listOf(GOOGLE, SAMSUNG)
        personas.catalogue = mapOf(
            GOOGLE.packageName to mapOf(
                SpokenLanguage.ENGLISH to listOf(voiceInfo("en-us-x-a", "en-US")),
            ),
            SAMSUNG.packageName to mapOf(
                SpokenLanguage.ENGLISH to listOf(voiceInfo("en-gb-x-a", "en-GB")),
            ),
        )
        val stored = InMemoryVoicePreferences(
            VoicePreferences(
                engine = GOOGLE.packageName,
                englishVoiceId = "en-us-x-a",
                setupComplete = true,
            ),
        )
        val viewModel = VoiceSetupViewModel(personas, stored, settings)
        advanceUntilIdle()

        viewModel.selectEngine(SAMSUNG.packageName)
        advanceUntilIdle()

        assertThat(stored.saved.engine).isEqualTo(SAMSUNG.packageName)
        assertThat(stored.saved.englishVoiceId).isNull()
        assertThat(stored.saved.bengaliVoiceId).isNull()
        assertThat(stored.saved.setupComplete).isFalse()
        assertThat(viewModel.state.value.englishVoices.map { it.name }).containsExactly("en-gb-x-a")
    }

    @Test
    fun `pinning the engine already pinned writes nothing`() = runTest {
        personas.installedEngines = listOf(GOOGLE)
        val stored = InMemoryVoicePreferences(VoicePreferences(engine = GOOGLE.packageName))
        val viewModel = VoiceSetupViewModel(personas, stored, settings)
        advanceUntilIdle()
        val before = stored.writes

        viewModel.selectEngine(GOOGLE.packageName)
        advanceUntilIdle()

        assertThat(stored.writes).isEqualTo(before)
    }

    @Test
    fun `the system default is a choice of its own, not the absence of one`() = runTest {
        personas.installedEngines = listOf(GOOGLE)
        personas.catalogue = mapOf(
            GOOGLE.packageName to mapOf(
                SpokenLanguage.ENGLISH to listOf(voiceInfo("en-us-x-a", "en-US")),
            ),
            null to mapOf(
                SpokenLanguage.ENGLISH to listOf(voiceInfo("en-us-x-default", "en-US")),
            ),
        )
        val stored = InMemoryVoicePreferences(VoicePreferences(engine = GOOGLE.packageName))
        val viewModel = VoiceSetupViewModel(personas, stored, settings)
        advanceUntilIdle()

        viewModel.selectEngine(null)
        advanceUntilIdle()

        assertThat(stored.saved.engine).isNull()
        assertThat(viewModel.state.value.englishVoices.map { it.name })
            .containsExactly("en-us-x-default")
    }

    @Test
    fun `a sample plays the voice already chosen for that language`() = runTest {
        personas.installedEngines = listOf(GOOGLE)
        personas.catalogue = mapOf(
            GOOGLE.packageName to mapOf(
                SpokenLanguage.ENGLISH to listOf(voiceInfo("en-us-x-a", "en-US")),
            ),
        )
        val viewModel = viewModel(
            VoicePreferences(engine = GOOGLE.packageName, englishVoiceId = "en-us-x-a"),
        )
        advanceUntilIdle()

        viewModel.playSample(SpokenLanguage.ENGLISH)
        viewModel.playSample(SpokenLanguage.BENGALI)
        advanceUntilIdle()

        assertThat(personas.previews.map { it.voiceId }).containsExactly("en-us-x-a")
        assertThat(viewModel.state.value.previewing).isNull()
    }

    @Test
    fun `a sample carries the rate on the slider, before the thumb has been let go`() = runTest {
        personas.installedEngines = listOf(GOOGLE)
        val viewModel = viewModel(
            VoicePreferences(engine = GOOGLE.packageName, englishVoiceId = "en-us-x-a"),
        )
        advanceUntilIdle()

        viewModel.onSpeechRateChange(1.6f)
        viewModel.onPitchChange(0.8f)
        viewModel.playSample(SpokenLanguage.ENGLISH)
        advanceUntilIdle()

        val played = personas.previews.single()
        assertThat(played.rate).isEqualTo(1.6f)
        assertThat(played.pitch).isEqualTo(0.8f)
        assertThat(played.engine).isEqualTo(GOOGLE.packageName)
    }

    @Test
    fun `sliders are clamped, and written only once the thumb is let go`() = runTest {
        personas.installedEngines = listOf(GOOGLE)
        val stored = InMemoryVoicePreferences(VoicePreferences(engine = GOOGLE.packageName))
        val viewModel = VoiceSetupViewModel(personas, stored, settings)
        advanceUntilIdle()

        viewModel.onSpeechRateChange(5f)
        viewModel.onPitchChange(0.1f)
        advanceUntilIdle()
        assertThat(viewModel.state.value.preferences.speechRate)
            .isEqualTo(VoicePreferences.RATE_RANGE.endInclusive)
        assertThat(viewModel.state.value.preferences.pitch)
            .isEqualTo(VoicePreferences.PITCH_RANGE.start)
        assertThat(stored.saved.speechRate).isEqualTo(VoicePreferences.DEFAULT.speechRate)

        viewModel.commitSpeechRate()
        viewModel.commitPitch()
        advanceUntilIdle()

        assertThat(stored.saved.speechRate).isEqualTo(VoicePreferences.RATE_RANGE.endInclusive)
        assertThat(stored.saved.pitch).isEqualTo(VoicePreferences.PITCH_RANGE.start)
    }

    @Test
    fun `a rate saved with no voice chosen does not claim setup is finished`() = runTest {
        personas.installedEngines = listOf(GOOGLE)
        val stored = InMemoryVoicePreferences(VoicePreferences(engine = GOOGLE.packageName))
        val viewModel = VoiceSetupViewModel(personas, stored, settings)
        advanceUntilIdle()

        viewModel.onSpeechRateChange(1.2f)
        viewModel.commitSpeechRate()
        advanceUntilIdle()

        assertThat(stored.saved.setupComplete).isFalse()
    }

    @Test
    fun `a voice that will not play says so, and the row stops showing as speaking`() = runTest {
        personas.installedEngines = listOf(GOOGLE)
        personas.previewResult = failure(MiraErrorCode.ENGINE_UNAVAILABLE, "no such voice")
        val stored = InMemoryVoicePreferences(VoicePreferences(engine = GOOGLE.packageName))
        val viewModel = VoiceSetupViewModel(personas, stored, settings)
        advanceUntilIdle()

        viewModel.chooseVoice("bn-bd-x-a", SpokenLanguage.BENGALI)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.notice).isNotEmpty()
        assertThat(state.previewing).isNull()
        // The choice still stands: the engine may simply be downloading the voice.
        assertThat(stored.saved.bengaliVoiceId).isEqualTo("bn-bd-x-a")

        viewModel.dismissNotice()
        assertThat(viewModel.state.value.notice).isNull()
    }

    @Test
    fun `a second tap interrupts the first sample rather than queueing behind it`() = runTest {
        personas.installedEngines = listOf(GOOGLE)
        personas.speaking = CompletableDeferred()
        val viewModel = viewModel(VoicePreferences(engine = GOOGLE.packageName))
        advanceUntilIdle()

        viewModel.chooseVoice("en-us-x-a", SpokenLanguage.ENGLISH)
        assertThat(viewModel.state.value.previewing).isEqualTo("en-us-x-a")

        viewModel.chooseVoice("en-us-x-b", SpokenLanguage.ENGLISH)
        assertThat(viewModel.state.value.previewing).isEqualTo("en-us-x-b")

        personas.speaking?.complete(Unit)
        advanceUntilIdle()

        assertThat(personas.previews.map { it.voiceId })
            .containsExactly("en-us-x-a", "en-us-x-b").inOrder()
        assertThat(viewModel.state.value.previewing).isNull()
    }

    @Test
    fun `spoken replies can be turned off here`() = runTest {
        personas.installedEngines = listOf(GOOGLE)
        val stored = InMemoryVoicePreferences(VoicePreferences(engine = GOOGLE.packageName))
        val viewModel = VoiceSetupViewModel(personas, stored, settings)
        advanceUntilIdle()

        viewModel.setSpokenResponsesEnabled(false)
        advanceUntilIdle()

        assertThat(stored.saved.spokenResponsesEnabled).isFalse()
        assertThat(viewModel.state.value.preferences.spokenResponsesEnabled).isFalse()
    }

    @Test
    fun `the screen speaks the language Abu chose, including what it says when a voice fails`() =
        runTest {
            personas.installedEngines = listOf(GOOGLE)
            personas.previewResult = failure(MiraErrorCode.ENGINE_UNAVAILABLE, "no such voice")
            val bangla = FakeSettingsRepository(MiraSettings(languageMode = LanguageMode.BANGLA))
            val viewModel = VoiceSetupViewModel(
                personas,
                InMemoryVoicePreferences(VoicePreferences(engine = GOOGLE.packageName)),
                bangla,
            )
            advanceUntilIdle()

            assertThat(viewModel.state.value.language).isEqualTo(SpokenLanguage.BENGALI)

            viewModel.chooseVoice("bn-bd-x-a", SpokenLanguage.BENGALI)
            advanceUntilIdle()

            assertThat(viewModel.state.value.notice)
                .isEqualTo(BengaliVoiceSetupWording.previewFailed)
        }

    private fun viewModel(preferences: VoicePreferences): VoiceSetupViewModel =
        VoiceSetupViewModel(personas, InMemoryVoicePreferences(preferences), settings)
}

private val GOOGLE = TtsEngineInfo("com.google.android.tts", "Google")
private val SAMSUNG = TtsEngineInfo("com.samsung.SMT", "Samsung")

/** What the screen asked the engine to say, so a test can check it was the right voice. */
private data class PreviewCall(
    val engine: String?,
    val voiceId: String,
    val language: SpokenLanguage,
    val rate: Float,
    val pitch: Float,
)

/**
 * A device's voice catalogue, keyed by engine package — null meaning the system default.
 *
 * [speaking], when set, holds [preview] open the way a real sample does, so a test can have two
 * taps overlap and see which one wins.
 */
private class FakeVoicePersonas : VoicePersonaManager {

    var installedEngines: List<TtsEngineInfo> = emptyList()
    var catalogue: Map<String?, Map<SpokenLanguage, List<TtsVoiceInfo>>> = emptyMap()
    var previewResult: AppResult<Unit> = AppResult.Success(Unit)
    var speaking: CompletableDeferred<Unit>? = null

    val previews = mutableListOf<PreviewCall>()

    override suspend fun engines(): List<TtsEngineInfo> = installedEngines

    override suspend fun voices(
        enginePackage: String?,
        language: SpokenLanguage,
    ): List<TtsVoiceInfo> = catalogue[enginePackage]?.get(language).orEmpty()

    override suspend fun preview(
        enginePackage: String?,
        voiceName: String,
        language: SpokenLanguage,
        rate: Float,
        pitch: Float,
    ): AppResult<Unit> {
        previews += PreviewCall(enginePackage, voiceName, language, rate, pitch)
        speaking?.await()
        return previewResult
    }

    /** Nothing to shut down here; whether the real engine is released needs a device. */
    override fun release() = Unit
}

/** The stored voice preferences, in memory. [saved] is what the tests assert against. */
private class InMemoryVoicePreferences(
    initial: VoicePreferences = VoicePreferences.DEFAULT,
) : VoicePreferenceRepository {

    private val _preferences = MutableStateFlow(initial)
    override val preferences: Flow<VoicePreferences> = _preferences.asStateFlow()

    val saved: VoicePreferences get() = _preferences.value
    var writes = 0
        private set

    override suspend fun current(): VoicePreferences = _preferences.value

    override suspend fun setEngine(packageName: String?) = write {
        it.copy(engine = packageName)
    }

    override suspend fun setVoiceId(language: SpokenLanguage, voiceId: String?) = write {
        when (language) {
            SpokenLanguage.ENGLISH -> it.copy(englishVoiceId = voiceId)
            SpokenLanguage.BENGALI -> it.copy(bengaliVoiceId = voiceId)
        }
    }

    override suspend fun setSpeechRate(rate: Float) = write { it.copy(speechRate = rate) }

    override suspend fun setPitch(pitch: Float) = write { it.copy(pitch = pitch) }

    override suspend fun setSpokenResponsesEnabled(enabled: Boolean) = write {
        it.copy(spokenResponsesEnabled = enabled)
    }

    override suspend fun setSetupComplete(complete: Boolean) = write {
        it.copy(setupComplete = complete)
    }

    private fun write(change: (VoicePreferences) -> VoicePreferences) {
        writes++
        _preferences.update(change)
    }
}

/** A voice row shaped like the ones a real engine reports. */
private fun voiceInfo(name: String, localeTag: String): TtsVoiceInfo = TtsVoiceInfo(
    name = name,
    localeTag = localeTag,
    displayLabel = name,
    quality = 400,
    latency = 200,
    requiresNetwork = false,
    isInstalled = true,
)
