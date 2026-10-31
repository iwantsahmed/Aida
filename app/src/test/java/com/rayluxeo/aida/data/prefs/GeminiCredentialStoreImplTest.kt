package com.rayluxeo.aida.data.prefs

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.data.TestDispatcherProvider
import com.rayluxeo.aida.data.crypto.FakeVaultKeyProvider
import com.rayluxeo.aida.data.crypto.FieldCipher
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class GeminiCredentialStoreImplTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var storeScope: CoroutineScope
    private lateinit var store: DataStore<Preferences>
    private lateinit var credentials: GeminiCredentialStoreImpl
    private lateinit var settings: SettingsRepositoryImpl

    @Before
    fun createStore() {
        storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        store = PreferenceDataStoreFactory.create(scope = storeScope) {
            File(temporaryFolder.root, "settings.preferences_pb")
        }
        val dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher())
        credentials = GeminiCredentialStoreImpl(store, FieldCipher(FakeVaultKeyProvider()), dispatchers)
        settings = SettingsRepositoryImpl(store, dispatchers)
    }

    @After
    fun closeStore() {
        storeScope.cancel()
    }

    @Test
    fun `no key is stored until one is set`() = runTest {
        assertThat(credentials.apiKey()).isNull()
        assertThat(credentials.hasKey.first()).isFalse()
    }

    @Test
    fun `a stored key round trips and never appears in plaintext on disk`() = runTest {
        credentials.setApiKey(API_KEY)

        assertThat(credentials.apiKey()).isEqualTo(API_KEY)
        assertThat(credentials.hasKey.first()).isTrue()

        val written = store.data.first().asMap().values.map { it.toString() }
        assertThat(written).isNotEmpty()
        assertThat(written.none { it.contains(API_KEY) }).isTrue()
    }

    @Test
    fun `storing a key is what enables gemini in settings`() = runTest {
        assertThat(settings.current().geminiEnabled).isFalse()

        credentials.setApiKey(API_KEY)

        assertThat(settings.current().geminiEnabled).isTrue()
    }

    @Test
    fun `clearing the key removes it`() = runTest {
        credentials.setApiKey(API_KEY)
        credentials.setApiKey(null)

        assertThat(credentials.apiKey()).isNull()
        assertThat(credentials.hasKey.first()).isFalse()
        assertThat(settings.current().geminiEnabled).isFalse()
    }

    @Test
    fun `a blank key is treated as no key at all`() = runTest {
        credentials.setApiKey("   ")

        assertThat(credentials.apiKey()).isNull()
        assertThat(credentials.hasKey.first()).isFalse()
    }

    private companion object {
        const val API_KEY = "AIzaSyTestKeyForMiraUnitTests0000000000"
    }
}
