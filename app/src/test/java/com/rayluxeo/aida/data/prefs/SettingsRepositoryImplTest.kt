package com.rayluxeo.aida.data.prefs

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.data.TestDispatcherProvider
import com.rayluxeo.aida.domain.repository.MiraSettings
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
 * How Mira sounds is no longer part of these settings; it is covered by
 * [VoicePreferenceRepositoryImplTest], including the keys that moved port without moving on disk.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SettingsRepositoryImplTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var storeScope: CoroutineScope
    private lateinit var store: DataStore<Preferences>
    private lateinit var repository: SettingsRepositoryImpl

    @Before
    fun createStore() {
        storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        store = PreferenceDataStoreFactory.create(scope = storeScope) {
            File(temporaryFolder.root, "settings.preferences_pb")
        }
        repository = SettingsRepositoryImpl(store, TestDispatcherProvider(UnconfinedTestDispatcher()))
    }

    @After
    fun closeStore() {
        storeScope.cancel()
    }

    @Test
    fun `an untouched store reads back the documented defaults`() = runTest {
        assertThat(repository.current()).isEqualTo(MiraSettings())
    }

    @Test
    fun `cross-app automations default off and round trip`() = runTest {
        // Off by default is the opt-in invariant of the whole tier; the toggle must persist Abu's yes.
        assertThat(repository.current().crossAppAutomationsEnabled).isFalse()

        repository.setCrossAppAutomationsEnabled(true)

        assertThat(repository.current().crossAppAutomationsEnabled).isTrue()
    }

    @Test
    fun `every toggle round trips`() = runTest {
        repository.setLanguageMode(LanguageMode.BANGLA)
        repository.setActivationSound(false)
        repository.setHapticFeedback(false)
        repository.setOnboardingComplete(true)

        val settings = repository.current()

        assertThat(settings.languageMode).isEqualTo(LanguageMode.BANGLA)
        assertThat(settings.activationSound).isFalse()
        assertThat(settings.hapticFeedback).isFalse()
        assertThat(settings.onboardingComplete).isTrue()
    }

    /** A file written by a newer build, or corrupted, must not decide Mira's language for her. */
    @Test
    fun `an unrecognised language mode falls back to the default`() = runTest {
        store.edit { it[stringPreferencesKey("language_mode")] = "KLINGON" }

        assertThat(repository.current().languageMode).isEqualTo(MiraSettings().languageMode)
    }

    @Test
    fun `the settings flow emits the change`() = runTest {
        repository.setLanguageMode(LanguageMode.ENGLISH)

        assertThat(repository.settings.first().languageMode).isEqualTo(LanguageMode.ENGLISH)
    }

    @Test
    fun `gemini stays disabled until a key is stored`() = runTest {
        assertThat(repository.current().geminiEnabled).isFalse()
    }
}
