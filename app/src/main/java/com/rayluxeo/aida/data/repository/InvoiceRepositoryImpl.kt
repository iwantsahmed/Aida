package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.core.common.flatMap
import com.rayluxeo.aida.data.db.dao.InvoiceDao
import com.rayluxeo.aida.data.db.entity.InvoiceEntity
import com.rayluxeo.aida.data.db.entity.InvoiceItemEntity
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.repository.InvoiceRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * The gate every invoice passes through on its way to disk.
 *
 * Two things are checked here that the DAO cannot check for itself, and both are refused as
 * [MiraErrorCode.VALIDATION] rather than stored:
 *
 * 1. Every amount on the document is in the document's own currency. A `Long` of minor units on
 *    disk carries no currency of its own — the header's code is what gives it meaning — so a
 *    line priced in something else would be stored as if it were not.
 * 2. The stored totals add up to the stored lines. `BuildInvoiceTotalsUseCase` is what produces
 *    figures that pass; this is the check that they were in fact used.
 */
@Singleton
class InvoiceRepositoryImpl @Inject constructor(
    private val dao: InvoiceDao,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : InvoiceRepository {

    override fun observeAll(): Flow<List<Invoice>> =
        dao.observeAll()
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override fun observeForClient(clientId: Long): Flow<List<Invoice>> =
        dao.observeForClient(clientId)
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override fun observeUnpaid(): Flow<List<Invoice>> =
        dao.observeUnpaid(InvoiceStatus.VOID)
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    /** Today is resolved once per collection, in Abu's zone, exactly as the task lists do it. */
    override fun observeOverdue(): Flow<List<Invoice>> = flow<List<Invoice>> {
        val today = clock.today().toEpochDay()
        emitAll(
            dao.observeOverdue(today, InvoiceStatus.VOID).map { rows -> rows.map { it.toDomain() } }
        )
    }.flowOn(dispatchers.io).orOnFailure(emptyList())

    override suspend fun byId(id: Long): AppResult<Invoice> =
        storageCall(dispatchers, "load invoice $id") { dao.findById(id) }
            .flatMap { row ->
                row?.toDomain()?.let { AppResult.Success(it) }
                    ?: failure(MiraErrorCode.NOT_FOUND, "No invoice with id $id")
            }

    override suspend fun byNumber(number: String): AppResult<Invoice> =
        storageCall(dispatchers, "load invoice $number") {
            dao.findByNumber(number)
                ?: if (number.all { it.isDigit() }) {
                    val padded = number.padStart(SEQUENCE_WIDTH, '0')
                    dao.search("%-$padded").firstOrNull { it.invoice.number.endsWith("-$padded") }
                        ?: dao.search("%$number").firstOrNull { it.invoice.number.endsWith("-$number") }
                } else null
        }.flatMap { row ->
            row?.toDomain()?.let { AppResult.Success(it) }
                ?: failure(MiraErrorCode.NOT_FOUND, "No invoice numbered $number")
        }

    override suspend fun search(query: String?): AppResult<List<Invoice>> =
        storageCall(dispatchers, "search invoices") {
            dao.search(likePattern(query)).map { it.toDomain() }
        }

    override suspend fun create(invoice: Invoice): AppResult<Invoice> =
        writable(invoice, requireNumber = true).flatMap { row ->
            storageCall(dispatchers, "create invoice ${invoice.number}") {
                dao.insertWithLines(row.header, row.lines)
            }.flatMap { id ->
                id?.let { byId(it) }
                    ?: failure(MiraErrorCode.VALIDATION, "Invoice number ${invoice.number} is already in use")
            }
        }

    override suspend fun createWithNextNumber(invoice: Invoice): AppResult<Invoice> =
        writable(invoice, requireNumber = false).flatMap { row ->
            val prefix = prefixFor(invoice.issuedOn.year)
            storageCall(dispatchers, "create invoice for ${invoice.issuedOn.year}") {
                dao.insertWithGeneratedNumber(row.header, row.lines, prefix) { highest ->
                    nextNumber(prefix, highest)
                }
            }.flatMap { id -> byId(id) }
        }

    override suspend fun update(invoice: Invoice): AppResult<Invoice> =
        writable(invoice, requireNumber = true).flatMap { row ->
            val stamped = row.header.copy(updatedAtEpochMillis = clock.now().toEpochMilli())
            storageCall(dispatchers, "update invoice ${invoice.id}") {
                dao.updateWithLines(stamped, row.lines)
            }.flatMap { changed ->
                when (changed) {
                    null -> failure(MiraErrorCode.VALIDATION, "Invoice number ${invoice.number} is already in use")
                    0 -> failure(MiraErrorCode.NOT_FOUND, "No invoice with id ${invoice.id}")
                    else -> byId(invoice.id)
                }
            }
        }

    override suspend fun setStatus(id: Long, status: InvoiceStatus): AppResult<Invoice> {
        if (status in PAYMENT_OWNED_STATES) {
            return failure(MiraErrorCode.VALIDATION, "Only a payment moves invoice $id to $status")
        }
        return storageCall(dispatchers, "set invoice $id to $status") {
            dao.setStatus(id, status, clock.now().toEpochMilli())
        }.flatMap { changed ->
            if (changed > 0) byId(id) else failure(MiraErrorCode.NOT_FOUND, "No invoice with id $id")
        }
    }

    override suspend fun softDelete(id: Long): AppResult<Unit> =
        storageCall(dispatchers, "bin invoice $id") {
            dao.softDelete(id, clock.now().toEpochMilli())
        }.flatMap { changed ->
            if (changed > 0) AppResult.Success(Unit)
            else failure(MiraErrorCode.NOT_FOUND, "No live invoice with id $id")
        }

    override suspend fun restore(id: Long): AppResult<Invoice> =
        storageCall(dispatchers, "restore invoice $id") {
            dao.restore(id, clock.now().toEpochMilli())
        }.flatMap { changed ->
            if (changed > 0) byId(id) else failure(MiraErrorCode.NOT_FOUND, "No binned invoice with id $id")
        }

    override suspend fun delete(id: Long): AppResult<Unit> =
        storageCall(dispatchers, "delete invoice $id") { dao.deleteById(id) }
            .flatMap { removed ->
                if (removed > 0) AppResult.Success(Unit)
                else failure(MiraErrorCode.NOT_FOUND, "No invoice with id $id")
            }

    /**
     * Turns a domain invoice into the rows it will occupy, or explains why it cannot be one.
     *
     * The uuid is minted here, once, because it is the identity the row keeps when it leaves the
     * phone in an export — a document that has never been stored has none yet.
     */
    private fun writable(invoice: Invoice, requireNumber: Boolean): AppResult<InvoiceRow> {
        if (requireNumber && invoice.number.isBlank()) {
            return failure(MiraErrorCode.VALIDATION, "Invoice number was blank")
        }
        if (invoice.lines.isEmpty()) {
            return failure(MiraErrorCode.VALIDATION, "An invoice with no lines has nothing to charge for")
        }
        val currency = invoice.currency
        val mismatched = invoice.lines.firstOrNull {
            it.unitPrice.currency != currency || it.lineTotal.currency != currency
        }
        if (mismatched != null) {
            return failure(
                MiraErrorCode.VALIDATION,
                "Line \"${mismatched.description}\" is not in the invoice's ${currency.currencyCode}",
            )
        }
        val headerAmounts = listOf(invoice.subtotal, invoice.discount, invoice.tax, invoice.total, invoice.amountPaid)
        if (headerAmounts.any { it.currency != currency }) {
            return failure(
                MiraErrorCode.VALIDATION,
                "Invoice ${invoice.number} mixes currencies on its own totals",
            )
        }
        val row = try {
            InvoiceRow(
                header = invoice.toEntity().copy(uuid = invoice.uuid.ifBlank { UUID.randomUUID().toString() }),
                lines = invoice.lines.map { it.toEntity(invoice.id) },
            )
        } catch (error: ArithmeticException) {
            return failure(
                MiraErrorCode.VALIDATION,
                "An amount on invoice ${invoice.number} is finer than the database stores",
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
                "Invoice ${invoice.number} stores totals that its own lines do not add up to",
            )
        }
        return AppResult.Success(row)
    }

    private class InvoiceRow(val header: InvoiceEntity, val lines: List<InvoiceItemEntity>)

    /**
     * A LIKE pattern, not an FTS query: invoices have no full-text index, so the search is a
     * contains match over the number and the notes. The wildcards are escaped, because Abu
     * dictating "50%" must look for a percent sign rather than for everything.
     */
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

    /**
     * The next number under [prefix]. The sequence is read off the tail of the highest number
     * already issued that year, so a year Abu has not billed in yet starts at one.
     */
    private fun nextNumber(prefix: String, highestIssued: String?): String {
        val previous = highestIssued?.substringAfterLast('-')?.toIntOrNull() ?: 0
        return prefix + (previous + 1).toString().padStart(SEQUENCE_WIDTH, '0')
    }

    private companion object {
        const val NUMBER_PREFIX = "INV"
        const val SEQUENCE_WIDTH = 3

        /** Set only by money arriving. See [InvoiceStatus]. */
        val PAYMENT_OWNED_STATES = setOf(InvoiceStatus.PAID, InvoiceStatus.PARTIALLY_PAID)
    }
}
