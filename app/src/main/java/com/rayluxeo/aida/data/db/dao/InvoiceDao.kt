package com.rayluxeo.aida.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.rayluxeo.aida.data.db.entity.InvoiceEntity
import com.rayluxeo.aida.data.db.entity.InvoiceItemEntity
import com.rayluxeo.aida.domain.model.InvoiceStatus
import kotlinx.coroutines.flow.Flow

/**
 * An invoice read together with the lines that justify its total.
 *
 * Room returns the lines in whatever order the join produced, so the mapper sorts them by
 * position; a bill whose lines rearrange between reads is not the same bill to look at.
 */
data class InvoiceWithItems(
    @Embedded val invoice: InvoiceEntity,
    @Relation(parentColumn = "id", entityColumn = "invoiceId")
    val items: List<InvoiceItemEntity>,
)

/**
 * Invoices and their lines.
 *
 * Two invariants shape this DAO and both are enforced inside transactions rather than trusted to
 * callers:
 *
 * 1. A header and its lines are written together. A total that arrived without the lines behind
 *    it is worse than no invoice at all, because it looks correct.
 * 2. A number is allocated by reading the highest one already issued for that prefix, in the same
 *    transaction as the insert. Two invoices raised in the same second would otherwise both read
 *    "013" and both try to become "014".
 *
 * The uniqueness probes deliberately ignore `deletedAtEpochMillis`. A number Abu has already put
 * in front of a client is spent, whether or not the row was later binned, and the unique index
 * covers the whole table regardless.
 */
@Dao
abstract class InvoiceDao {

    @Transaction
    @Query(
        """
        SELECT * FROM invoices
        WHERE deletedAtEpochMillis IS NULL
        ORDER BY issuedOnEpochDay DESC, id DESC
        """
    )
    abstract fun observeAll(): Flow<List<InvoiceWithItems>>

    @Transaction
    @Query(
        """
        SELECT * FROM invoices
        WHERE deletedAtEpochMillis IS NULL AND clientId = :clientId
        ORDER BY issuedOnEpochDay DESC, id DESC
        """
    )
    abstract fun observeForClient(clientId: Long): Flow<List<InvoiceWithItems>>

    /** Everything still owed: the money question Abu actually asks. Voided bills are not owed. */
    @Transaction
    @Query(
        """
        SELECT * FROM invoices
        WHERE deletedAtEpochMillis IS NULL AND status != :excluded AND totalMinor > amountPaidMinor
        ORDER BY dueOnEpochDay ASC, id ASC
        """
    )
    abstract fun observeUnpaid(excluded: InvoiceStatus): Flow<List<InvoiceWithItems>>

    /**
     * Late as of [todayEpochDay]. The boundary is passed in rather than taken from SQLite's own
     * date functions, which resolve "now" in UTC and would call a bill due today overdue at 6 a.m.
     * in Dhaka.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM invoices
        WHERE deletedAtEpochMillis IS NULL AND status != :excluded
          AND totalMinor > amountPaidMinor AND dueOnEpochDay < :todayEpochDay
        ORDER BY dueOnEpochDay ASC, id ASC
        """
    )
    abstract fun observeOverdue(todayEpochDay: Long, excluded: InvoiceStatus): Flow<List<InvoiceWithItems>>

    @Transaction
    @Query("SELECT * FROM invoices WHERE deletedAtEpochMillis IS NULL AND id = :id")
    abstract suspend fun findById(id: Long): InvoiceWithItems?

    /** The raw header, binned or not. Only the update transaction needs this view. */
    @Query("SELECT * FROM invoices WHERE id = :id")
    abstract suspend fun headerById(id: Long): InvoiceEntity?

    @Transaction
    @Query("SELECT * FROM invoices WHERE deletedAtEpochMillis IS NULL AND number = :number")
    abstract suspend fun findByNumber(number: String): InvoiceWithItems?

    /**
     * [pattern] is a LIKE pattern with `\` as its escape character; the repository builds it, so
     * a stray `%` dictated into the search box cannot turn into a wildcard.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM invoices
        WHERE deletedAtEpochMillis IS NULL
          AND (number LIKE :pattern ESCAPE '\' OR IFNULL(notes, '') LIKE :pattern ESCAPE '\')
        ORDER BY issuedOnEpochDay DESC, id DESC
        """
    )
    abstract suspend fun search(pattern: String): List<InvoiceWithItems>

    /** Ignores soft deletion on purpose: see the class KDoc. */
    @Query("SELECT id FROM invoices WHERE number = :number LIMIT 1")
    abstract suspend fun idForNumber(number: String): Long?

    /**
     * The highest number already issued under [numberPrefix]. Ordering by length first makes the
     * comparison numeric rather than lexicographic, so "INV-2026-9" still sorts below
     * "INV-2026-010" if the padding width is ever changed.
     */
    @Query(
        """
        SELECT number FROM invoices
        WHERE number LIKE :numberPrefix || '%'
        ORDER BY LENGTH(number) DESC, number DESC
        LIMIT 1
        """
    )
    abstract suspend fun highestNumberWithPrefix(numberPrefix: String): String?

    @Insert
    abstract suspend fun insertInvoice(invoice: InvoiceEntity): Long

    @Insert
    abstract suspend fun insertItems(items: List<InvoiceItemEntity>): List<Long>

    @Update
    abstract suspend fun updateInvoice(invoice: InvoiceEntity): Int

    @Query("DELETE FROM invoice_items WHERE invoiceId = :invoiceId")
    abstract suspend fun deleteItemsFor(invoiceId: Long)

    @Query(
        """
        UPDATE invoices SET status = :status, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NULL
        """
    )
    abstract suspend fun setStatus(id: Long, status: InvoiceStatus, atEpochMillis: Long): Int

    @Query(
        """
        UPDATE invoices
        SET deletedAtEpochMillis = :atEpochMillis, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NULL
        """
    )
    abstract suspend fun softDelete(id: Long, atEpochMillis: Long): Int

    @Query(
        """
        UPDATE invoices
        SET deletedAtEpochMillis = NULL, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NOT NULL
        """
    )
    abstract suspend fun restore(id: Long, atEpochMillis: Long): Int

    /** The lines go with it by cascade; payments pointing at it are orphaned, not deleted. */
    @Query("DELETE FROM invoices WHERE id = :id")
    abstract suspend fun deleteById(id: Long): Int

    /**
     * Writes the header and every line as one unit.
     *
     * Returns null when [invoice] carries a number that is already taken. A duplicate is an
     * expected condition — Abu can type a number himself — so it is reported as a value rather
     * than left to surface as a constraint exception.
     */
    @Transaction
    open suspend fun insertWithLines(invoice: InvoiceEntity, lines: List<InvoiceItemEntity>): Long? {
        if (idForNumber(invoice.number) != null) return null
        return writeDocument(invoice, lines)
    }

    /**
     * As [insertWithLines], but allocates the number too. [nextNumber] is handed the highest
     * number currently issued under [numberPrefix], or null when this is the first of the year,
     * and returns the one to use. Both halves run in the same transaction, which is the whole
     * point: the read that decides the number cannot be overtaken by another insert.
     */
    @Transaction
    open suspend fun insertWithGeneratedNumber(
        invoice: InvoiceEntity,
        lines: List<InvoiceItemEntity>,
        numberPrefix: String,
        nextNumber: (highestIssued: String?) -> String,
    ): Long = writeDocument(invoice.copy(number = nextNumber(highestNumberWithPrefix(numberPrefix))), lines)

    /**
     * Replaces the header and the whole set of lines together, so a total can never be stored
     * beside the lines it no longer matches. The lines are rewritten rather than diffed: nothing
     * in the schema points at an invoice line, so their ids are free to change. Positions are
     * renumbered from the order given, which is what keeps them contiguous.
     *
     * Three columns are taken from the stored row rather than from [invoice], inside this same
     * transaction: `amountPaidMinor`, which belongs to the payments cluster and would otherwise
     * be clobbered by whatever the editing screen last read; `createdAtEpochMillis`, which is
     * history; and `deletedAtEpochMillis`, so that editing can never quietly resurrect a binned
     * invoice.
     *
     * Returns null when the number now belongs to a different invoice, and 0 when no live
     * invoice has this id.
     */
    @Transaction
    open suspend fun updateWithLines(invoice: InvoiceEntity, lines: List<InvoiceItemEntity>): Int? {
        val holder = idForNumber(invoice.number)
        if (holder != null && holder != invoice.id) return null
        val stored = headerById(invoice.id) ?: return 0
        if (stored.deletedAtEpochMillis != null) return 0
        val changed = updateInvoice(
            invoice.copy(
                amountPaidMinor = stored.amountPaidMinor,
                createdAtEpochMillis = stored.createdAtEpochMillis,
                deletedAtEpochMillis = null,
            )
        )
        if (changed == 0) return 0
        deleteItemsFor(invoice.id)
        insertItems(lines.mapIndexed { index, item -> item.copy(id = 0L, invoiceId = invoice.id, position = index) })
        return changed
    }

    private suspend fun writeDocument(invoice: InvoiceEntity, lines: List<InvoiceItemEntity>): Long {
        val id = insertInvoice(invoice.copy(id = 0L))
        insertItems(lines.mapIndexed { index, item -> item.copy(id = 0L, invoiceId = id, position = index) })
        return id
    }
}
