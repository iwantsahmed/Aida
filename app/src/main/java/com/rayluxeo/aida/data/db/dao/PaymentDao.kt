package com.rayluxeo.aida.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.rayluxeo.aida.data.db.entity.PaymentEntity
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.PaymentPolicy
import kotlinx.coroutines.flow.Flow

/**
 * Just enough of an invoice to decide what a payment does to it.
 *
 * A projection rather than the whole row: this DAO has no business reading an invoice's lines,
 * notes or client, and a narrow SELECT makes that impossible rather than merely discouraged.
 */
data class PaymentInvoiceState(
    val id: Long,
    val currencyCode: String,
    val totalMinor: Long,
    val amountPaidMinor: Long,
    val status: InvoiceStatus,
    val deletedAtEpochMillis: Long?,
)

/**
 * What happened when a payment was applied to an invoice.
 *
 * Every refusal is a value rather than an exception, and every one of them is decided *before*
 * the insert, so a refused payment leaves the database exactly as it found it — there is nothing
 * to roll back and no half-written state to reason about.
 */
sealed interface PaymentApplication {

    data class Applied(
        val paymentId: Long,
        val invoiceStatus: InvoiceStatus,
        val amountPaidMinor: Long,
    ) : PaymentApplication

    /** The invoice is gone, or in the bin, so there is nothing to settle. */
    data object InvoiceMissing : PaymentApplication

    /** The invoice was voided. A voided bill is not owed, so money against it needs a decision. */
    data object InvoiceNotPayable : PaymentApplication

    /** Mira holds no exchange rates, and inventing one would be worse than refusing. */
    data class CurrencyMismatch(val invoiceCurrencyCode: String) : PaymentApplication

    /** More than is outstanding. [outstandingMinor] is what the invoice would actually accept. */
    data class Overpayment(val outstandingMinor: Long) : PaymentApplication
}

/**
 * Payments, and the one narrow window onto the invoices table that settling one requires.
 *
 * Applying a payment has to read an invoice's total, add to its paid figure and move its status
 * without anything in between — two payments arriving together must not both read the same
 * "amount paid" and each write it back. That makes it one transaction, and a transaction cannot
 * be split across two owners' files, so the two invoice statements live here rather than in
 * InvoiceDao. They are deliberately the only ones: a targeted read and a targeted update, both
 * reachable only from [recordAgainstInvoice].
 *
 * Payments carry no soft-delete column on purpose — a payment that did not happen is a mistake
 * to be removed, not a record to be archived — so [deleteAndUnapply] is a real delete. There is
 * no update either: an amount edited in place would silently leave the invoice's paid figure
 * behind, so a mistyped payment is removed and recorded again.
 */
@Dao
abstract class PaymentDao {

    @Query("SELECT * FROM payments ORDER BY receivedAtEpochMillis DESC, id DESC LIMIT :limit")
    abstract fun observeRecent(limit: Int): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE invoiceId = :invoiceId ORDER BY receivedAtEpochMillis ASC, id ASC")
    abstract fun observeForInvoice(invoiceId: Long): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE id = :id")
    abstract suspend fun findById(id: Long): PaymentEntity?

    @Query("SELECT * FROM payments WHERE invoiceId = :invoiceId ORDER BY receivedAtEpochMillis ASC, id ASC")
    abstract suspend fun listForInvoice(invoiceId: Long): List<PaymentEntity>

    /**
     * Summed in SQL over whole minor units, so a hundred payments total to the same figure
     * however they are ordered. The currency is part of the filter rather than an assumption:
     * adding paisa to cents would be arithmetic on two different things.
     */
    @Query(
        """
        SELECT COALESCE(SUM(amountMinor), 0) FROM payments
        WHERE currencyCode = :currencyCode
          AND receivedAtEpochMillis >= :fromEpochMillis
          AND receivedAtEpochMillis < :toEpochMillis
        """
    )
    abstract suspend fun sumReceivedMinor(
        fromEpochMillis: Long,
        toEpochMillis: Long,
        currencyCode: String,
    ): Long

    @Insert
    abstract suspend fun insert(payment: PaymentEntity): Long

    @Query(
        """
        SELECT * FROM payments
        WHERE (reference LIKE :pattern ESCAPE '\' OR IFNULL(note, '') LIKE :pattern ESCAPE '\')
        ORDER BY receivedAtEpochMillis DESC, id DESC
        """
    )
    abstract suspend fun search(pattern: String): List<PaymentEntity>

    @Query("DELETE FROM payments WHERE id = :id")
    protected abstract suspend fun deleteById(id: Long): Int

    @Query(
        """
        SELECT id, currencyCode, totalMinor, amountPaidMinor, status, deletedAtEpochMillis
        FROM invoices WHERE id = :invoiceId
        """
    )
    protected abstract suspend fun paymentInvoiceState(invoiceId: Long): PaymentInvoiceState?

    @Query(
        """
        UPDATE invoices
        SET amountPaidMinor = :amountPaidMinor,
            status = :status,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :invoiceId
        """
    )
    protected abstract suspend fun applyToInvoice(
        invoiceId: Long,
        amountPaidMinor: Long,
        status: InvoiceStatus,
        updatedAtEpochMillis: Long,
    ): Int

    /**
     * Records a payment and moves the invoice it settles, in one transaction.
     *
     * The invoice is read, checked and written inside the same transaction as the insert, so an
     * invoice can never end up with a payment attached and a paid figure that does not include
     * it. Every refusal returns before the insert, so a refused payment writes nothing at all.
     */
    @Transaction
    open suspend fun recordAgainstInvoice(payment: PaymentEntity): PaymentApplication {
        val invoiceId = payment.invoiceId ?: return PaymentApplication.InvoiceMissing
        val invoice = paymentInvoiceState(invoiceId) ?: return PaymentApplication.InvoiceMissing
        if (invoice.deletedAtEpochMillis != null) return PaymentApplication.InvoiceMissing
        if (invoice.status == InvoiceStatus.VOID) return PaymentApplication.InvoiceNotPayable
        if (invoice.currencyCode != payment.currencyCode) {
            return PaymentApplication.CurrencyMismatch(invoice.currencyCode)
        }
        val outstandingMinor = invoice.totalMinor - invoice.amountPaidMinor
        if (payment.amountMinor > outstandingMinor) {
            return PaymentApplication.Overpayment(outstandingMinor)
        }
        val amountPaidMinor = invoice.amountPaidMinor + payment.amountMinor
        val status = PaymentPolicy.statusAfter(amountPaidMinor, invoice.totalMinor, invoice.status)
        val paymentId = insert(payment)
        applyToInvoice(
            invoiceId = invoiceId,
            amountPaidMinor = amountPaidMinor,
            status = status,
            updatedAtEpochMillis = payment.updatedAtEpochMillis,
        )
        return PaymentApplication.Applied(paymentId, status, amountPaidMinor)
    }

    /**
     * Removes a payment and takes it back off the invoice it settled, in one transaction.
     *
     * The invoice's paid figure is derived from the payments pointing at it, so deleting one
     * without walking the figure back would leave an invoice reading PAID with nothing behind
     * it. Returns the number of rows removed, so a payment that was not there reports 0 rather
     * than silently succeeding.
     */
    @Transaction
    open suspend fun deleteAndUnapply(id: Long, atEpochMillis: Long): Int {
        val payment = findById(id) ?: return 0
        val removed = deleteById(id)
        if (removed == 0) return 0
        val invoiceId = payment.invoiceId ?: return removed
        val invoice = paymentInvoiceState(invoiceId) ?: return removed
        val amountPaidMinor = (invoice.amountPaidMinor - payment.amountMinor).coerceAtLeast(0L)
        applyToInvoice(
            invoiceId = invoiceId,
            amountPaidMinor = amountPaidMinor,
            status = PaymentPolicy.statusAfter(amountPaidMinor, invoice.totalMinor, invoice.status),
            updatedAtEpochMillis = atEpochMillis,
        )
        return removed
    }
}
