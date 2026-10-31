package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.core.common.flatMap
import com.rayluxeo.aida.data.db.dao.QuoteDao
import com.rayluxeo.aida.data.db.entity.QuoteEntity
import com.rayluxeo.aida.data.db.entity.QuoteItemEntity
import com.rayluxeo.aida.domain.model.Quote
import com.rayluxeo.aida.domain.model.QuoteStatus
import com.rayluxeo.aida.domain.repository.QuoteRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * The quote side of the same gate as [InvoiceRepositoryImpl]: one currency per document, and
 * stored totals that add up to the stored lines, or the write is refused.
 */
@Singleton
class QuoteRepositoryImpl @Inject constructor(
    private val dao: QuoteDao,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : QuoteRepository {

    override fun observeAll(): Flow<List<Quote>> =
        dao.observeAll()
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override fun observeForClient(clientId: Long): Flow<List<Quote>> =
        dao.observeForClient(clientId)
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override fun observeOpen(): Flow<List<Quote>> =
        dao.observeOpen(drafted = QuoteStatus.DRAFT, sent = QuoteStatus.SENT)
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override suspend fun byId(id: Long): AppResult<Quote> =
        storageCall(dispatchers, "load quote $id") { dao.findById(id) }
            .flatMap { row ->
                row?.toDomain()?.let { AppResult.Success(it) }
                    ?: failure(MiraErrorCode.NOT_FOUND, "No quote with id $id")
            }

    override suspend fun byNumber(number: String): AppResult<Quote> =
        storageCall(dispatchers, "load quote $number") { dao.findByNumber(number) }
            .flatMap { row ->
                row?.toDomain()?.let { AppResult.Success(it) }
                    ?: failure(MiraErrorCode.NOT_FOUND, "No quote numbered $number")
            }

    override suspend fun search(query: String?): AppResult<List<Quote>> =
        storageCall(dispatchers, "search quotes") {
            dao.search(likePattern(query)).map { it.toDomain() }
        }

    override suspend fun create(quote: Quote): AppResult<Quote> =
        writable(quote, requireNumber = true).flatMap { row ->
            storageCall(dispatchers, "create quote ${quote.number}") {
                dao.insertWithLines(row.header, row.lines)
            }.flatMap { id ->
                id?.let { byId(it) }
                    ?: failure(MiraErrorCode.VALIDATION, "Quote number ${quote.number} is already in use")
            }
        }

    override suspend fun createWithNextNumber(quote: Quote): AppResult<Quote> =
        writable(quote, requireNumber = false).flatMap { row ->
            val prefix = prefixFor(quote.issuedOn.year)
            storageCall(dispatchers, "create quote for ${quote.issuedOn.year}") {
                dao.insertWithGeneratedNumber(row.header, row.lines, prefix) { highest ->
                    nextNumber(prefix, highest)
                }
            }.flatMap { id -> byId(id) }
        }

    override suspend fun update(quote: Quote): AppResult<Quote> =
        writable(quote, requireNumber = true).flatMap { row ->
            val stamped = row.header.copy(updatedAtEpochMillis = clock.now().toEpochMilli())
            storageCall(dispatchers, "update quote ${quote.id}") {
                dao.updateWithLines(stamped, row.lines)
            }.flatMap { changed ->
                when (changed) {
                    null -> failure(MiraErrorCode.VALIDATION, "Quote number ${quote.number} is already in use")
                    0 -> failure(MiraErrorCode.NOT_FOUND, "No quote with id ${quote.id}")
                    else -> byId(quote.id)
                }
            }
        }

    override suspend fun setStatus(id: Long, status: QuoteStatus): AppResult<Quote> =
        storageCall(dispatchers, "set quote $id to $status") {
            dao.setStatus(id, status, clock.now().toEpochMilli())
        }.flatMap { changed ->
            if (changed > 0) byId(id) else failure(MiraErrorCode.NOT_FOUND, "No quote with id $id")
        }

    override suspend fun markConverted(id: Long, invoiceId: Long): AppResult<Quote> =
        storageCall(dispatchers, "record quote $id as invoice $invoiceId") {
            dao.recordConversion(
                id = id,
                invoiceId = invoiceId,
                status = QuoteStatus.ACCEPTED,
                atEpochMillis = clock.now().toEpochMilli(),
            )
        }.flatMap { changed ->
            if (changed > 0) byId(id) else failure(MiraErrorCode.NOT_FOUND, "No quote with id $id")
        }

    override suspend fun softDelete(id: Long): AppResult<Unit> =
        storageCall(dispatchers, "bin quote $id") {
            dao.softDelete(id, clock.now().toEpochMilli())
        }.flatMap { changed ->
            if (changed > 0) AppResult.Success(Unit)
            else failure(MiraErrorCode.NOT_FOUND, "No live quote with id $id")
        }

    override suspend fun restore(id: Long): AppResult<Quote> =
        storageCall(dispatchers, "restore quote $id") {
            dao.restore(id, clock.now().toEpochMilli())
        }.flatMap { changed ->
            if (changed > 0) byId(id) else failure(MiraErrorCode.NOT_FOUND, "No binned quote with id $id")
        }

    override suspend fun delete(id: Long): AppResult<Unit> =
        storageCall(dispatchers, "delete quote $id") { dao.deleteById(id) }
            .flatMap { removed ->
                if (removed > 0) AppResult.Success(Unit)
                else failure(MiraErrorCode.NOT_FOUND, "No quote with id $id")
            }

    private fun writable(quote: Quote, requireNumber: Boolean): AppResult<QuoteRow> {
        if (requireNumber && quote.number.isBlank()) {
            return failure(MiraErrorCode.VALIDATION, "Quote number was blank")
        }
        if (quote.lines.isEmpty()) {
            return failure(MiraErrorCode.VALIDATION, "A quote with no lines offers nothing")
        }
        val currency = quote.currency
        val mismatched = quote.lines.firstOrNull {
            it.unitPrice.currency != currency || it.lineTotal.currency != currency
        }
        if (mismatched != null) {
            return failure(
                MiraErrorCode.VALIDATION,
                "Line \"${mismatched.description}\" is not in the quote's ${currency.currencyCode}",
            )
        }
        val headerAmounts = listOf(quote.subtotal, quote.discount, quote.tax, quote.total)
        if (headerAmounts.any { it.currency != currency }) {
            return failure(
                MiraErrorCode.VALIDATION,
                "Quote ${quote.number} mixes currencies on its own totals",
            )
        }
        val row = try {
            QuoteRow(
                header = quote.toEntity().copy(uuid = quote.uuid.ifBlank { UUID.randomUUID().toString() }),
                lines = quote.lines.map { it.toEntity(quote.id) },
            )
        } catch (error: ArithmeticException) {
            return failure(
                MiraErrorCode.VALIDATION,
                "An amount on quote ${quote.number} is finer than the database stores",
                error,
            )
        }
        val agree = documentTotalsAgree(
            lineTotalsMinor = row.lines.map { it.lineTotalMinor },
            subtotalMinor = row.header.subtotalMinor,
            discountMinor = row.header.discountMinor,
            taxMinor = row.header.taxMinor,
            totalMinor = row.header.totalMinor,
        )
        if (!agree) {
            return failure(
                MiraErrorCode.VALIDATION,
                "Quote ${quote.number} stores totals that its own lines do not add up to",
            )
        }
        return AppResult.Success(row)
    }

    private class QuoteRow(val header: QuoteEntity, val lines: List<QuoteItemEntity>)

    /** A LIKE pattern with its wildcards escaped; quotes have no full-text index. */
    private fun likePattern(query: String?): String {
        val trimmed = query?.trim().orEmpty()
        if (trimmed.isEmpty()) return "%"
        val escaped = trimmed
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return "%$escaped%"
    }

    private fun prefixFor(year: Int): String = "$NUMBER_PREFIX-$year-"

    private fun nextNumber(prefix: String, highestIssued: String?): String {
        val previous = highestIssued?.substringAfterLast('-')?.toIntOrNull() ?: 0
        return prefix + (previous + 1).toString().padStart(SEQUENCE_WIDTH, '0')
    }

    private companion object {
        const val NUMBER_PREFIX = "QUO"
        const val SEQUENCE_WIDTH = 3
    }
}
