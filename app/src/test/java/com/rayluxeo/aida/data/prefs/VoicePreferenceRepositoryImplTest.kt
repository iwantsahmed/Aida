package com.rayluxeo.aida.data.prefs

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.data.TestDispatcherProvider
import com.rayluxeo.aida.domain.repository.VoicePreferences
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The DataStore is built over a throwaway file rather than through [MiraPreferences], because
 * DataStore allows only one live instance per path and every test here wants a clean one.
 *
 * The key strings below are written out literally rather than taken from [MiraPreferenceKeys].
 * That duplication is the point: these are the names on Abu's phone, and a test that read the
 * same constant as the code would agree with any rename, including the one that silently forgets
 * the voice he chose.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class VoicePreferenceRepositoryImplTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var storeScope: CoroutineScope
    private lateinit var store: DataStore<Preferences>
    private lateinit var repository: VoicePreferenceRepositoryImpl

    @Before
    fun createStore() {
        storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        store = PreferenceDataStoreFactory.create(scope = storeScope) {
            File(temporaryFolder.root, "voice.preferences_pb")
        }
        repository = VoicePreferenceRepositoryImpl(store, TestDispatcherProvider(UnconfinedTestDispatcher()))
    }

    @After
    fun closeStore() {
        storeScope.cancel()
    }

    @Test
    fun `an untouched store reads back the documented defaults`() = runTest {
        assertThat(repository.current()).isEqualTo(VoicePreferences.DEFAULT)
    }

    @Test
    fun `every choice round trips`() = runTest {
        repository.setEngine("com.google.android.tts")
        repository.setVoiceId(SpokenLanguage.ENGLISH, "en-us-x-tpf-local")
        repository.setVoiceId(SpokenLanguage.BENGALI, "bn-bd-x-ban-local")
        repository.setSpeechRate(1.15f)
        repository.setPitch(0.95f)
        repository.setSpokenResponsesEnabled(false)
        repository.setSetupComplete(true)

        assertThat(repository.current()).isEqualTo(
            VoicePreferences(
                engine = "com.google.android.tts",
                englishVoiceId = "en-us-x-tpf-local",
                bengaliVoiceId = "bn-bd-x-ban-local",
                speechRate = 1.15f,
                pitch = 0.95f,
                spokenResponsesEnabled = false,
                setupComplete = true,
            ),
        )
    }

    /**
     * The migration's whole purpose: the voice Abu picked before the configuration moved port is
     * still the voice Mira speaks with afterwards. Written here as the raw preference file this
     * class inherited from `MiraSettings`.
     */
    @Test
    fun `the keys Abu's phone already holds are the keys that are read`() = runTest {
        store.edit { stored ->
            stored[stringPreferencesKey("voice_engine")] = "com.samsung.SMT"
            stored[stringPreferencesKey("voice_english_name")] = "en-GB-language"
            stored[stringPreferencesKey("voice_bengali_name")] = "bn-IN-language"
            stored[floatPreferencesKey("voice_speech_rate")] = 1.25f
            stored[floatPreferencesKey("voice_pitch")] = 0.85f
            stored[booleanPreferencesKey("spoken_responses")] = false
            stored[booleanPreferencesKey("voice_setup_complete")] = true
        }

        val restored = repository.current()

        assertThat(restored.engine).isEqualTo("com.samsung.SMT")
        assertThat(restored.englishVoiceId).isEqualTo("en-GB-language")
        assertThat(restored.bengaliVoiceId).isEqualTo("bn-IN-language")
        assertThat(restored.speechRate).isWithin(TOLERANCE).of(1.25f)
        assertThat(restored.pitch).isWithin(TOLERANCE).of(0.85f)
        assertThat(restored.spokenResponsesEnabled).isFalse()
        assertThat(restored.setupComplete).isTrue()
    }

    /** And the other direction: a write still lands on the keys an older build would find. */
    @Test
    fun `a written choice lands on the same keys it was read from`() = runTest {
        repository.setEngine("com.samsung.SMT")
        repository.setVoiceId(SpokenLanguage.BENGALI, "bn-IN-language")
        repository.setSpeechRate(1.4f)

        val raw = store.data.first()

        assertThat(raw[stringPreferencesKey("voice_engine")]).isEqualTo("com.samsung.SMT")
        assertThat(raw[stringPreferencesKey("voice_bengali_name")]).isEqualTo("bn-IN-language")
        assertThat(raw[floatPreferencesKey("voice_speech_rate")]).isWithin(TOLERANCE).of(1.4f)
    }

    @Test
    fun `an out of range rate or pitch is clamped before it is stored`() = runTest {
        repository.setSpeechRate(9f)
        repository.setPitch(0f)

        val raw = store.data.first()
        assertThat(raw[floatPreferencesKey("voice_speech_rate")])
            .isWithin(TOLERANCE).of(VoicePreferences.RATE_RANGE.endInclusive)
        assertThat(raw[floatPreferencesKey("voice_pitch")])
            .isWithin(TOLERANCE).of(VoicePreferences.PITCH_RANGE.start)
    }

    /** A file written by an older build, or edited by hand, must still produce a usable engine. */
    @Test
    fun `a rate already out of range on disk is clamped on the way out`() = runTest {
        store.edit { stored ->
            stored[floatPreferencesKey("voice_speech_rate")] = 12f
            stored[floatPreferencesKey("voice_pitch")] = -3f
        }

        val stored = repository.current()

        assertThat(stored.speechRate).isWithin(TOLERANCE).of(VoicePreferences.RATE_RANGE.endInclusive)
        assertThat(stored.pitch).isWithin(TOLERANCE).of(VoicePreferences.PITCH_RANGE.start)
    }

    @Test
    fun `clearing a voice removes the key rather than storing a blank`() = runTest {
        repository.setVoiceId(SpokenLanguage.ENGLISH, "en-us-x-tpf-local")
        repository.setVoiceId(SpokenLanguage.ENGLISH, null)

        assertThat(store.data.first()[stringPreferencesKey("voice_english_name")]).isNull()
        assertThat(repository.current().englishVoiceId).isNull()
    }

    /** An engine picker handing back an empty string means "no engine", not an engine named "". */
    @Test
    fun `a blank engine or voice is treated as no choice at all`() = runTest {
        repository.setEngine("com.google.android.tts")
        repository.setVoiceId(SpokenLanguage.BENGALI, "bn-bd-x-ban-local")

        repository.setEngine("  ")
        repository.setVoiceId(SpokenLanguage.BENGALI, "")

        val cleared = repository.current()
        assertThat(cleared.engine).isNull()
        assertThat(cleared.bengaliVoiceId).isNull()
    }

    @Test
    fun `each language keeps its own voice`() = runTest {
        repository.setVoiceId(SpokenLanguage.ENGLISH, "en-us-x-tpf-local")
        repository.setVoiceId(SpokenLanguage.BENGALI, "bn-bd-x-ban-local")

        val chosen = repository.current()

        assertThat(chosen.voiceIdFor(SpokenLanguage.ENGLISH)).isEqualTo("en-us-x-tpf-local")
        assertThat(chosen.voiceIdFor(SpokenLanguage.BENGALI)).isEqualTo("bn-bd-x-ban-local")
    }

    /** The settings file is shared, so an unrelated write must not disturb how Mira sounds. */
    @Test
    fun `a write to another area of the settings file leaves the voice alone`() = runTest {
        repository.setEngine("com.google.android.tts")
        repository.setPitch(1.1f)
        val before = repository.current()

        store.edit { stored ->
            stored[booleanPreferencesKey("activation_sound")] = false
            stored[stringPreferencesKey("language_mode")] = "BANGLA"
        }

        assertThat(repository.current()).isEqualTo(before)
    }

    @Test
    fun `the preference flow emits the change`() = runTest {
        repository.setSpokenResponsesEnabled(false)

        assertThat(repository.preferences.first().spokenResponsesEnabled).isFalse()
    }

    private companion object {
        const val TOLERANCE = 1e-6f
    }
}
