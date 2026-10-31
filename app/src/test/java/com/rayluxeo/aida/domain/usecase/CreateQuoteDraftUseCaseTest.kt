package com.rayluxeo.aida.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.ai.nlu.FixedMiraClock
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.ActivityEntry
import com.rayluxeo.aida.domain.model.ActivityCategory
import com.rayluxeo.aida.domain.model.ActivitySubject
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.Quote
import com.rayluxeo.aida.domain.model.QuoteStatus
import com.rayluxeo.aida.domain.model.UserProfile
import com.rayluxeo.aida.domain.repository.ActivityLogRepository
import com.rayluxeo.aida.domain.repository.QuoteRepository
import com.rayluxeo.aida.domain.repository.UserProfileRepository
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The quote draft mirrors the invoice draft, and these pin the three decisions that file documents,
 * in the quote's own terms: it always lands as a DRAFT with exactly one line, an amount Abu named
 * becomes that line's price, and a quote raised without an amount is still a quote (its line at
 * zero). The pricing is [BuildQuoteTotalsUseCase]'s, so the stored total matches the one line.
 *
 * Hand-written fakes, no Hilt (§7.4).
 */
class CreateQuoteDraftUseCaseTest {

    private val clock = FixedMiraClock(FixedMiraClock.dhaka(2026, 10, 16, 10))
    private val quotes = FakeQuotes()
    private val activity = FakeActivityLog()
    private val useCase = CreateQuoteDraftUseCase(
        quotes = quotes,
        totals = BuildQuoteTotalsUseCase(),
        activityLog = activity,
        profile = FakeProfiles(),
        clock = clock,
    )

    private val client = Client(id = 7L, name = "Ahmed Traders", createdAt = clock.now(), updatedAt = clock.now())

    @Test
    fun `an amount becomes the one line's price, in a DRAFT quote valid for a month`() = runTest {
        val quote = (useCase(client, amount = Money.of("50000"), description = "Website") as AppResult.Success).value

        assertThat(quote.status).isEqualTo(QuoteStatus.DRAFT)
        assertThat(quote.clientId).isEqualTo(7L)
        assertThat(quote.lines).hasSize(1)
        assertThat(quote.lines[0].description).isEqualTo("Website")
        assertThat(quote.lines[0].unitPrice).isEqualTo(Money.of("50000"))
        assertThat(quote.total).isEqualTo(Money.of("50000.00"))
        assertThat(quote.validUntil).isEqualTo(clock.today().plusDays(30))
        assertThat(activity.recorded).hasSize(1)
        assertThat(activity.recorded.single().action).isEqualTo("quote.created")
    }

    @Test
    fun `a quote with no amount is still created, its one line at zero`() = runTest {
        val quote = (useCase(client, amount = null, description = null) as AppResult.Success).value

        assertThat(quote.status).isEqualTo(QuoteStatus.DRAFT)
        assertThat(quote.lines).hasSize(1)
        assertThat(quote.lines[0].unitPrice).isEqualTo(Money.zero())
        assertThat(quote.lines[0].description).isEmpty()
        assertThat(quote.total).isEqualTo(Money.zero())
    }

    private class FakeQuotes : QuoteRepository {
        private var nextId = 1L
        override suspend fun createWithNextNumber(quote: Quote): AppResult<Quote> {
            val saved = quote.copy(id = nextId, number = "QUO-${quote.issuedOn.year}-${nextId.toString().padStart(3, '0')}")
            nextId++
            return AppResult.Success(saved)
        }
        override fun observeAll(): Flow<List<Quote>> = flowOf(emptyList())
        override fun observeForClient(clientId: Long): Flow<List<Quote>> = flowOf(emptyList())
        override fun observeOpen(): Flow<List<Quote>> = flowOf(emptyList())
        override suspend fun byId(id: Long) = error("unused")
        override suspend fun byNumber(number: String) = error("unused")
        override suspend fun search(query: String?) = error("unused")
        override suspend fun create(quote: Quote) = error("unused")
        override suspend fun update(quote: Quote) = error("unused")
        override suspend fun setStatus(id: Long, status: QuoteStatus) = error("unused")
        override suspend fun markConverted(id: Long, invoiceId: Long) = error("unused")
        override suspend fun softDelete(id: Long) = error("unused")
        override suspend fun restore(id: Long) = error("unused")
        override suspend fun delete(id: Long) = error("unused")
    }

    private class FakeActivityLog : ActivityLogRepository {
        val recorded = mutableListOf<ActivityEntry>()
        override suspend fun record(entry: ActivityEntry): AppResult<ActivityEntry> {
            recorded += entry
            return AppResult.Success(entry)
        }
        override fun observeRecent(limit: Int) = flowOf(emptyList<ActivityEntry>())
        override fun observeByCategory(category: ActivityCategory, limit: Int) = flowOf(emptyList<ActivityEntry>())
        override fun observeForSubject(subject: ActivitySubject) = flowOf(emptyList<ActivityEntry>())
        override suspend fun between(from: Instant, to: Instant) = AppResult.Success(emptyList<ActivityEntry>())
    }

    private class FakeProfiles : UserProfileRepository {
        override fun observe(): Flow<UserProfile?> = flowOf(null)
        override suspend fun current(): AppResult<UserProfile?> = AppResult.Success(null)
        override suspend fun save(profile: UserProfile): AppResult<UserProfile> = AppResult.Success(profile)
    }
}
