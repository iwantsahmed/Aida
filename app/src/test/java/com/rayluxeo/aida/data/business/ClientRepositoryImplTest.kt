package com.rayluxeo.aida.data.business

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.data.TestDispatcherProvider
import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.repository.ClientRepositoryImpl
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.ClientBilling
import com.rayluxeo.aida.domain.model.ClientFilter
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.repository.ClientRepository
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Currency
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.repository.ActivityLogRepository
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Hilt is deliberately absent — its local-test transform does not run under AGP 9 — so the
 * repository is constructed by hand over a real in-memory database with foreign keys enforced.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ClientRepositoryImplTest {

    private lateinit var database: MiraDatabase
    private val clock = FakeMiraClock(LocalDateTime.of(2026, 3, 5, 14, 0))

    @Before
    fun openDatabase() {
        database = openBusinessTestDatabase()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `create assigns a row id and a uuid the caller never supplied`() = runTest {
        val stored = repository().create(client(name = "Ahmed Rahman")).orFail()

        assertThat(stored.id).isGreaterThan(0L)
        assertThat(stored.uuid).hasLength(36)
    }

    @Test
    fun `two clients never share a uuid`() = runTest {
        val repository = repository()

        val first = repository.create(client(name = "Ahmed")).orFail()
        val second = repository.create(client(name = "Rahim")).orFail()

        assertThat(first.uuid).isNotEqualTo(second.uuid)
    }

    @Test
    fun `a client round trips through create and byId`() = runTest {
        val repository = repository()
        val detailed = client(name = "Ahmed Rahman", company = "Rayluxeo", email = "ahmed@rayluxeo.com")
        val created = repository.create(detailed).orFail()

        val read = repository.byId(created.id).orFail()

        assertThat(read.name).isEqualTo("Ahmed Rahman")
        assertThat(read.companyName).isEqualTo("Rayluxeo")
        assertThat(read.email).isEqualTo("ahmed@rayluxeo.com")
        assertThat(read.uuid).isEqualTo(created.uuid)
        assertThat(read.createdAt).isEqualTo(clock.now())
    }

    @Test
    fun `update changes the details and refuses to rewrite the uuid`() = runTest {
        val repository = repository()
        val created = repository.create(client(name = "Ahmed")).orFail()

        val edited = created.copy(uuid = "an-identity-the-caller-invented", name = "Ahmed Rahman")
        val stored = repository.update(edited).orFail()

        assertThat(stored.name).isEqualTo("Ahmed Rahman")
        assertThat(stored.uuid).isEqualTo(created.uuid)
        assertThat(stored.createdAt).isEqualTo(created.createdAt)
    }

    @Test
    fun `updating a client who is not there reports not found`() = runTest {
        val updated = repository().update(client(name = "Nobody").copy(id = 404L))

        assertThat(updated.errorOrFail().code).isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    @Test
    fun `archiving takes a client out of the active list but not out of the record`() = runTest {
        val repository = repository()
        val id = repository.create(client(name = "Ahmed")).orFail().id

        repository.setArchived(id, archived = true).orFail()

        assertThat(repository.observe(ClientFilter.ACTIVE).first()).isEmpty()
        assertThat(repository.observe(ClientFilter.ARCHIVED).first().map { it.name })
            .containsExactly("Ahmed")
        assertThat(repository.observe(ClientFilter.ALL).first()).hasSize(1)
    }

    @Test
    fun `a binned client is hidden from every read`() = runTest {
        val repository = repository()
        val id = repository.create(client(name = "Ahmed", company = "Rayluxeo")).orFail().id

        repository.softDelete(id).orFail()

        assertThat(repository.observe(ClientFilter.ALL).first()).isEmpty()
        assertThat(repository.observe(ClientFilter.ARCHIVED).first()).isEmpty()
        assertThat(repository.search(null, ClientFilter.ALL).orFail()).isEmpty()
        assertThat(repository.search("Rayluxeo", ClientFilter.ALL).orFail()).isEmpty()
        assertThat(repository.byId(id).errorOrFail().code).isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    @Test
    fun `restoring brings back the same row rather than a copy`() = runTest {
        val repository = repository()
        val created = repository.create(client(name = "Ahmed")).orFail()
        repository.softDelete(created.id).orFail()

        val restored = repository.restore(created.id).orFail()

        assertThat(restored.id).isEqualTo(created.id)
        assertThat(restored.uuid).isEqualTo(created.uuid)
        assertThat(restored.deletedAt).isNull()
        assertThat(repository.observe(ClientFilter.ACTIVE).first()).hasSize(1)
    }

    @Test
    fun `search finds a client by company as readily as by name`() = runTest {
        val repository = repository()
        repository.create(client(name = "Ahmed Rahman", company = "Rayluxeo")).orFail()
        repository.create(client(name = "Karim Uddin", company = "Northfield")).orFail()

        val found = repository.search("rayl", ClientFilter.ACTIVE).orFail()

        assertThat(found.map { it.name }).containsExactly("Ahmed Rahman")
    }

    @Test
    fun `a blank query means no filter rather than no results`() = runTest {
        val repository = repository()
        repository.create(client(name = "Ahmed Rahman")).orFail()
        repository.create(client(name = "Karim Uddin")).orFail()

        val found = repository.search("   ", ClientFilter.ACTIVE).orFail()

        assertThat(found.map { it.name }).containsExactly("Ahmed Rahman", "Karim Uddin")
    }

    @Test
    fun `search leaves archived clients out of the active set`() = runTest {
        val repository = repository()
        val id = repository.create(client(name = "Ahmed Rahman", company = "Rayluxeo")).orFail().id
        repository.setArchived(id, archived = true).orFail()

        assertThat(repository.search("rayluxeo", ClientFilter.ACTIVE).orFail()).isEmpty()
        assertThat(repository.search("rayluxeo", ClientFilter.ALL).orFail()).hasSize(1)
    }

    @Test
    fun `a default rate survives the database exactly`() = runTest {
        val repository = repository()
        val rate = Money.of("1234.56")

        val billed = client(name = "Ahmed").copy(billing = ClientBilling(Money.BDT, rate))
        val created = repository.create(billed).orFail()
        val read = repository.byId(created.id).orFail()

        assertThat(read.billing?.defaultRate).isEqualTo(rate)
        assertThat(read.billing?.defaultRate?.amount).isEqualTo(BigDecimal("1234.56"))
        assertThat(database.clientDao().findById(created.id)?.defaultRateMinor).isEqualTo(123_456L)
    }

    @Test
    fun `a rate in another currency keeps that currency`() = runTest {
        val repository = repository()
        val usd = Currency.getInstance("USD")
        val rate = Money.of("85.00", usd)

        val billed = client(name = "Ahmed").copy(billing = ClientBilling(usd, rate))
        val created = repository.create(billed).orFail()
        val read = repository.byId(created.id).orFail()

        assertThat(read.billing?.currency).isEqualTo(usd)
        assertThat(read.billing?.defaultRate).isEqualTo(rate)
        assertThat(database.clientDao().findById(created.id)?.defaultRateMinor).isEqualTo(8_500L)
    }

    @Test
    fun `a rate finer than its currency can hold is refused rather than rounded`() = runTest {
        val unrepresentable = Money(BigDecimal("12.345"), Money.BDT)
        val billed = client(name = "Ahmed").copy(billing = ClientBilling(Money.BDT, unrepresentable))

        val created = repository().create(billed)

        assertThat(created.errorOrFail().code).isEqualTo(MiraErrorCode.VALIDATION)
    }

    @Test
    fun `a stored rate with no currency beside it is read as no rate at all`() = runTest {
        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO clients (
                uuid, name, companyName, email, phone, addressLine, notes, defaultCurrencyCode,
                defaultRateMinor, isArchived, deletedAtEpochMillis, createdAtEpochMillis,
                updatedAtEpochMillis
            ) VALUES (
                '22222222-2222-2222-2222-222222222222', 'Ahmed', NULL, NULL, NULL, NULL, NULL, NULL,
                123456, 0, NULL, 1772000000000, 1772000000000
            )
            """.trimIndent()
        )

        val listed = repository().observe(ClientFilter.ACTIVE).first()

        assertThat(listed).hasSize(1)
        assertThat(listed.single().billing).isNull()
    }

    @Test
    fun `a client with an invoice against him cannot be deleted for good`() = runTest {
        val repository = repository()
        val id = repository.create(client(name = "Ahmed")).orFail().id
        database.insertInvoiceRow(clientId = id)

        val purged = repository.purge(id)

        assertThat(purged.errorOrFail().code).isEqualTo(MiraErrorCode.VALIDATION)
        assertThat(repository.byId(id).orFail().name).isEqualTo("Ahmed")
    }

    @Test
    fun `a client with nothing against him is deleted for good`() = runTest {
        val repository = repository()
        val id = repository.create(client(name = "Ahmed")).orFail().id
        database.insertInvoiceRow(clientId = id)
        database.deleteInvoiceRows()

        repository.purge(id).orFail()

        assertThat(repository.byId(id).errorOrFail().code).isEqualTo(MiraErrorCode.NOT_FOUND)
        assertThat(repository.observe(ClientFilter.ALL).first()).isEmpty()
    }

    @Test
    fun `deleting a client who was never there reports not found`() = runTest {
        val purged = repository().purge(404L)

        assertThat(purged.errorOrFail().code).isEqualTo(MiraErrorCode.NOT_FOUND)
    }

    /** Typed as the port, because that is the surface the rest of the app will hold. */
    private fun repository(): ClientRepository = ClientRepositoryImpl(
        dao = database.clientDao(),
        activityLog = FakeTestActivityLog(),
        clock = clock,
        dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher()),
    )

    private fun client(
        name: String,
        company: String? = null,
        email: String? = null,
    ): Client = Client(
        name = name,
        companyName = company,
        email = email,
        createdAt = clock.now(),
        updatedAt = clock.now(),
    )
}

internal class FakeTestActivityLog : ActivityLogRepository {
    override fun observeRecent(limit: Int) = flowOf(emptyList<com.rayluxeo.aida.domain.model.ActivityEntry>())
    override fun observeByCategory(category: com.rayluxeo.aida.domain.model.ActivityCategory, limit: Int) = flowOf(emptyList<com.rayluxeo.aida.domain.model.ActivityEntry>())
    override fun observeForSubject(subject: com.rayluxeo.aida.domain.model.ActivitySubject) = flowOf(emptyList<com.rayluxeo.aida.domain.model.ActivityEntry>())
    override suspend fun between(from: Instant, to: Instant) = AppResult.Success(emptyList<com.rayluxeo.aida.domain.model.ActivityEntry>())
    override suspend fun record(entry: com.rayluxeo.aida.domain.model.ActivityEntry) = AppResult.Success(entry)
}
