package com.rayluxeo.aida.data.platform

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.data.TestDispatcherProvider
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.repository.UserProfileRepositoryImpl
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.UserProfile
import com.rayluxeo.aida.domain.repository.UserProfileRepository
import java.time.Instant
import java.time.LocalDateTime
import java.util.Currency
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
class UserProfileRepositoryImplTest {

    private lateinit var database: MiraDatabase
    private val clock = FakeMiraClock(LocalDateTime.of(2026, 3, 5, 14, 0))
    private val createdAt: Instant = clock.now()

    @Before
    fun openDatabase() {
        database = openTestDatabase()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `there is no profile until Abu sets one`() = runTest {
        val repository = repository()

        assertThat(repository.observe().first()).isNull()
        assertThat((repository.current() as AppResult.Success).value).isNull()
    }

    @Test
    fun `a saved profile round trips with every field intact`() = runTest {
        val repository = repository()

        repository.save(
            profile().copy(
                businessName = "Rayluxeo",
                email = "abu@rayluxeo.com",
                phone = "+8801700000000",
                addressLine = "Dhaka",
                taxId = "TIN-4471",
            ),
        )

        val loaded = (repository.current() as AppResult.Success).value!!
        assertThat(loaded.displayName).isEqualTo("Abu Sayed Ahmed")
        assertThat(loaded.businessName).isEqualTo("Rayluxeo")
        assertThat(loaded.email).isEqualTo("abu@rayluxeo.com")
        assertThat(loaded.phone).isEqualTo("+8801700000000")
        assertThat(loaded.addressLine).isEqualTo("Dhaka")
        assertThat(loaded.taxId).isEqualTo("TIN-4471")
        assertThat(loaded.defaultCurrency).isEqualTo(Money.BDT)
        assertThat(loaded.createdAt).isEqualTo(createdAt)
    }

    @Test
    fun `saving again edits the one row and keeps when it was first set`() = runTest {
        val repository = repository()
        repository.save(profile())
        clock.moveTo(LocalDateTime.of(2026, 6, 1, 9, 30))

        val updated = (repository.save(profile().copy(phone = "+8801711111111")) as AppResult.Success).value

        assertThat(updated.phone).isEqualTo("+8801711111111")
        assertThat(updated.createdAt).isEqualTo(createdAt)
        assertThat(updated.updatedAt).isEqualTo(clock.now())
        assertThat(database.countRows("user_profile")).isEqualTo(1)
    }

    @Test
    fun `a stale copy cannot drag the creation time backwards`() = runTest {
        val repository = repository()
        repository.save(profile())

        repository.save(profile().copy(createdAt = Instant.EPOCH, displayName = "Abu"))

        assertThat((repository.current() as AppResult.Success).value!!.createdAt).isEqualTo(createdAt)
    }

    @Test
    fun `the profile is observable as it changes`() = runTest {
        val repository = repository()

        assertThat(repository.observe().first()).isNull()
        repository.save(profile())

        assertThat(repository.observe().first()?.displayName).isEqualTo("Abu Sayed Ahmed")
    }

    @Test
    fun `a currency the phone does not know decays to BDT instead of failing the read`() = runTest {
        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO user_profile (
                id, displayName, businessName, email, phone, addressLine, taxId,
                defaultCurrencyCode, createdAtEpochMillis, updatedAtEpochMillis
            ) VALUES (1, 'Abu Sayed Ahmed', NULL, NULL, NULL, NULL, NULL, 'ZZZ', 1, 1)
            """.trimIndent(),
        )

        val loaded = (repository().current() as AppResult.Success).value!!

        assertThat(loaded.defaultCurrency).isEqualTo(Money.BDT)
        assertThat(loaded.displayName).isEqualTo("Abu Sayed Ahmed")
    }

    @Test
    fun `a currency other than the default survives the round trip exactly`() = runTest {
        val repository = repository()
        val usd = Currency.getInstance("USD")

        repository.save(profile().copy(defaultCurrency = usd))

        assertThat((repository.current() as AppResult.Success).value!!.defaultCurrency).isEqualTo(usd)
    }

    private fun repository(): UserProfileRepository = UserProfileRepositoryImpl(
        dao = database.userProfileDao(),
        clock = clock,
        dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher()),
    )

    private fun profile() = UserProfile(
        displayName = "Abu Sayed Ahmed",
        createdAt = createdAt,
        updatedAt = createdAt,
    )
}
