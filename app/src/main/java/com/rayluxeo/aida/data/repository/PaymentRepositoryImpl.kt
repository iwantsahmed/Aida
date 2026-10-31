package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.data.db.dao.PaymentApplication
import com.rayluxeo.aida.data.db.dao.PaymentDao
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.MoneyAmount
import com.rayluxeo.aida.domain.model.Payment
import com.rayluxeo.aida.domain.repository.PaymentRepository
import java.time.Instant
import java.util.Currency
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Every refusal the DAO can return is turned into a [MiraErrorCode] here, one layer above the
 * transaction that decided it. A currency mismatch and an overpayment are VALIDATION because
 * they are things Abu can correct and be told about; a missing invoice is NOT_FOUND.
 */
@Singleton
class PaymentRepositoryImpl @Inject constructor(
    private val dao: PaymentDao,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : PaymentRepository {

    override fun observeRecent(limit: Int): Flow<List<Payment>> =
        dao.observeRecent(limit)
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override fun observeForInvoice(invoiceId: Long): Flow<List<Payment>> =
        dao.observeForInvoice(invoiceId)
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override suspend fun byId(id: Long): AppResult<Payment> {
        val found = storageCall(dispatchers, "load payment $id") { dao.findById(id) }
        return when (found) {
            is AppResult.Failure -> found
            is AppResult.Success -> found.value?.toDomain()?.let { AppResult.Success(it) }
                ?: failure(MiraErrorCode.NOT_FOUND, "No payment with id $id")
        }
    }

    override suspend fun forInvoice(invoiceId: Long): AppResult<List<Payment>> =
        storageCall(dispatchers, "load payments for invoice $invoiceId") {
            dao.listForInvoice(invoiceId).map { it.toDomain() }
        }

    override suspend fun search(query: String?, limit: Int): AppResult<List<Payment>> =
        storageCall(dispatchers, "search payments") {
            val pattern = "%${query?.trim() ?: ""}%"
            dao.search(pattern).map { it.toDomain() }
        }

    override suspend fun record(payment: Payment): AppResult<Payment> {
        val invoiceId = payment.invoiceId
            ?: return storageCall(dispatchers, "record payment") {
                val id = dao.insert(payment.copy(id = 0L).toEntity())
                payment.copy(id = id)
            }
        val applied = storageCall(dispatchers, "record payment against invoice $invoiceId") {
            dao.recordAgainstInvoice(payment.copy(id = 0L).toEntity())
        }
        return when (applied) {
            is AppResult.Failure -> applied
            is AppResult.Success -> when (val outcome = applied.value) {
                is PaymentApplication.Applied ->
                    AppResult.Success(payment.copy(id = outcome.paymentId))

                PaymentApplication.InvoiceMissing ->
                    failure(MiraErrorCode.NOT_FOUND, "No live invoice with id $invoiceId")

                PaymentApplication.InvoiceNotPayable -> failure(
                    MiraErrorCode.VALIDATION,
                    "Invoice $invoiceId is void and cannot take a payment",
                )

                is PaymentApplication.CurrencyMismatch -> failure(
                    MiraErrorCode.VALIDATION,
                    "Invoice $invoiceId is in ${outcome.invoiceCurrencyCode}, " +
                        "payment in ${payment.amount.currency.currencyCode}",
                )

                is PaymentApplication.Overpayment -> failure(
                    MiraErrorCode.VALIDATION,
                    "Invoice $invoiceId has only ${outcome.outstandingMinor} minor units outstanding",
                )
            }
        }
    }

    override suspend fun totalReceivedBetween(
        from: Instant,
        to: Instant,
        currency: Currency,
    ): AppResult<Money> = storageCall(dispatchers, "total payments received") {
        dao.sumReceivedMinor(
            fromEpochMillis = from.toEpochMilli(),
            toEpochMillis = to.toEpochMilli(),
            currencyCode = currency.currencyCode,
        ).let { MoneyAmount.fromMinorUnits(it, currency.currencyCode) }
    }

    override suspend fun delete(id: Long): AppResult<Unit> {
        val removed = storageCall(dispatchers, "delete payment $id") {
            dao.deleteAndUnapply(id, clock.now().toEpochMilli())
        }
        return when (removed) {
            is AppResult.Failure -> removed
            is AppResult.Success ->
                if (removed.value > 0) AppResult.Success(Unit)
                else failure(MiraErrorCode.NOT_FOUND, "No payment with id $id")
        }
    }
}
