package com.rayluxeo.aida.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.data.TestDispatcherProvider
import com.rayluxeo.aida.data.crypto.FakeVaultKeyProvider
import com.rayluxeo.aida.data.crypto.FieldCipher
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.domain.model.VaultCategory
import com.rayluxeo.aida.domain.model.VaultItem
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class VaultRepositoryImplTest {

    private lateinit var database: MiraDatabase
    private val clock = FakeMiraClock(LocalDateTime.of(2026, 3, 5, 14, 0))
    private val cipher = FieldCipher(FakeVaultKeyProvider())

    @Before
    fun openDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            MiraDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `create then reveal returns the original secret`() = runTest {
        val repository = repository()

        val created = repository.create(item(title = "Bank login", secret = SECRET))
        val id = (created as AppResult.Success).value.id

        val revealed = repository.reveal(id)

        assertThat(revealed).isInstanceOf(AppResult.Success::class.java)
        assertThat((revealed as AppResult.Success).value.secret).isEqualTo(SECRET)
        assertThat(revealed.value.title).isEqualTo("Bank login")
        assertThat(revealed.value.category).isEqualTo(VaultCategory.CREDENTIAL)
    }

    @Test
    fun `the stored row never holds the plaintext`() = runTest {
        val repository = repository()
        val created = repository.create(item(title = "Bank login", secret = SECRET))
        val id = (created as AppResult.Success).value.id

        val stored = database.vaultDao().findById(id)!!

        assertThat(stored.cipherText.containsSequence(SECRET.toByteArray(Charsets.UTF_8))).isFalse()
        assertThat(stored.iv).isNotEmpty()
        assertThat(stored.cipherText).isNotEmpty()
    }

    @Test
    fun `two records with the same secret get different nonces and different ciphertext`() = runTest {
        val repository = repository()
        val first = (repository.create(item("One", SECRET)) as AppResult.Success).value.id
        val second = (repository.create(item("Two", SECRET)) as AppResult.Success).value.id

        val a = database.vaultDao().findById(first)!!
        val b = database.vaultDao().findById(second)!!

        assertThat(a.iv).isNotEqualTo(b.iv)
        assertThat(a.cipherText).isNotEqualTo(b.cipherText)
    }

    @Test
    fun `summaries list the titles without touching the secrets`() = runTest {
        val repository = repository()
        repository.create(item("Bank login", SECRET))
        repository.create(item("Passport number", "BX 4417711"))

        val summaries = repository.observeSummaries().first()

        assertThat(summaries.map { it.title }).containsExactly("Bank login", "Passport number")
    }

    @Test
    fun `revealing a missing record reports not found`() = runTest {
        val revealed = repository().reveal(404L)

        assertThat(revealed).isInstanceOf(AppResult.Failure::class.java)
        assertThat((revealed as AppResult.Failure).error.code).isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    @Test
    fun `update replaces the secret and reveal returns the new one`() = runTest {
        val repository = repository()
        val id = (repository.create(item("Bank login", SECRET)) as AppResult.Success).value.id

        repository.update(item("Bank login", "rotated-secret").copy(id = id))

        assertThat((repository.reveal(id) as AppResult.Success).value.secret).isEqualTo("rotated-secret")
    }

    private fun repository(): VaultRepositoryImpl = VaultRepositoryImpl(
        dao = database.vaultDao(),
        cipher = cipher,
        dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher()),
    )

    private fun item(title: String, secret: String): VaultItem = VaultItem(
        title = title,
        secret = secret,
        category = VaultCategory.CREDENTIAL,
        createdAt = clock.now(),
        updatedAt = clock.now(),
    )

    private fun ByteArray.containsSequence(needle: ByteArray): Boolean {
        if (needle.isEmpty() || needle.size > size) return false
        outer@ for (start in 0..size - needle.size) {
            for (offset in needle.indices) {
                if (this[start + offset] != needle[offset]) continue@outer
            }
            return true
        }
        return false
    }

    private companion object {
        const val SECRET = "9f3-lion-gate-4471"
    }
}
