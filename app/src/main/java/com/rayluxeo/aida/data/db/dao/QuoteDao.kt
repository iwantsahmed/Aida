package com.rayluxeo.aida.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.rayluxeo.aida.data.db.entity.QuoteEntity
import com.rayluxeo.aida.data.db.entity.QuoteItemEntity
import com.rayluxeo.aida.domain.model.QuoteStatus
import kotlinx.coroutines.flow.Flow

/** A quote read together with its lines; the mapper restores position order. */
data class QuoteWithItems(
    @Embedded val quote: QuoteEntity,
    @Relation(parentColumn = "id", entityColumn = "quoteId")
    val items: List<QuoteItemEntity>,
)

/**
 * Quotes and their lines.
 *
 * Shaped exactly like [InvoiceDao] and for the same reasons: a header and its lines are written
 * in one transaction, and a number is allocated inside that transaction rather than read first
 * and used a moment later.
 */
@Dao
abstract class QuoteDao {

    @Transaction
    @Query(
        """
        SELECT * FROM quotes
        WHERE deletedAtEpochMillis IS NULL
        ORDER BY issuedOnEpochDay DESC, id DESC
        """
    )
    abstract fun observeAll(): Flow<List<QuoteWithItems>>

    @Transaction
    @Query(
        """
        SELECT * FROM quotes
        WHERE deletedAtEpochMillis IS NULL AND clientId = :clientId
        ORDER BY issuedOnEpochDay DESC, id DESC
        """
    )
    abstract fun observeForClient(clientId: Long): Flow<List<QuoteWithItems>>

    /**
     * Offers still in play: drafted or sent, not yet turned into a bill.
     *
     * The two states arrive as parameters rather than as literals in the SQL, so the stored enum
     * names stay the type converter's business and a rename cannot leave a query silently
     * matching nothing.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM quotes
        WHERE deletedAtEpochMillis IS NULL AND convertedInvoiceId IS NULL
          AND (status = :drafted OR status = :sent)
        ORDER BY issuedOnEpochDay DESC, id DESC
        """
    )
    abstract fun observeOpen(drafted: QuoteStatus, sent: QuoteStatus): Flow<List<QuoteWithItems>>

    @Transaction
    @Query("SELECT * FROM quotes WHERE deletedAtEpochMillis IS NULL AND id = :id")
    abstract suspend fun findById(id: Long): QuoteWithItems?

    /** The raw header, binned or not. Only the update transaction needs this view. */
    @Query("SELECT * FROM quotes WHERE id = :id")
    abstract suspend fun headerById(id: Long): QuoteEntity?

    @Transaction
    @Query("SELECT * FROM quotes WHERE deletedAtEpochMillis IS NULL AND number = :number")
    abstract suspend fun findByNumber(number: String): QuoteWithItems?

    /** [pattern] is a LIKE pattern with `\` as its escape character, built by the repository. */
    @Transaction
    @Query(
        """
        SELECT * FROM quotes
        WHERE deletedAtEpochMillis IS NULL
          AND (number LIKE :pattern ESCAPE '\' OR IFNULL(notes, '') LIKE :pattern ESCAPE '\')
        ORDER BY issuedOnEpochDay DESC, id DESC
        """
    )
    abstract suspend fun search(pattern: String): List<QuoteWithItems>

    /** Ignores soft deletion: a number that has been shown to a client is spent either way. */
    @Query("SELECT id FROM quotes WHERE number = :number LIMIT 1")
    abstract suspend fun idForNumber(number: String): Long?

    @Query(
        """
        SELECT number FROM quotes
        WHERE number LIKE :numberPrefix || '%'
        ORDER BY LENGTH(number) DESC, number DESC
        LIMIT 1
        """
    )
    abstract suspend fun highestNumberWithPrefix(numberPrefix: String): String?

    @Insert
    abstract suspend fun insertQuote(quote: QuoteEntity): Long

    @Insert
    abstract suspend fun insertItems(items: List<QuoteItemEntity>): List<Long>

    @Update
    abstract suspend fun updateQuote(quote: QuoteEntity): Int

    @Query("DELETE FROM quote_items WHERE quoteId = :quoteId")
    abstract suspend fun deleteItemsFor(quoteId: Long)

    @Query(
        """
        UPDATE quotes SET status = :status, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NULL
        """
    )
    abstract suspend fun setStatus(id: Long, status: QuoteStatus, atEpochMillis: Long): Int

    @Query(
        """
        UPDATE quotes
        SET convertedInvoiceId = :invoiceId, status = :status, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NULL
        """
    )
    abstract suspend fun recordConversion(
        id: Long,
        invoiceId: Long,
        status: QuoteStatus,
        atEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE quotes
        SET deletedAtEpochMillis = :atEpochMillis, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NULL
        """
    )
    abstract suspend fun softDelete(id: Long, atEpochMillis: Long): Int

    @Query(
        """
        UPDATE quotes
        SET deletedAtEpochMillis = NULL, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NOT NULL
        """
    )
    abstract suspend fun restore(id: Long, atEpochMillis: Long): Int

    /** The lines go with it by cascade. */
    @Query("DELETE FROM quotes WHERE id = :id")
    abstract suspend fun deleteById(id: Long): Int

    /** Null when the number is already taken; see [InvoiceDao.insertWithLines]. */
    @Transaction
    open suspend fun insertWithLines(quote: QuoteEntity, lines: List<QuoteItemEntity>): Long? {
        if (idForNumber(quote.number) != null) return null
        return writeDocument(quote, lines)
    }

    /** Allocates the number inside the insert's own transaction; see [InvoiceDao]. */
    @Transaction
    open suspend fun insertWithGeneratedNumber(
        quote: QuoteEntity,
        lines: List<QuoteItemEntity>,
        numberPrefix: String,
        nextNumber: (highestIssued: String?) -> String,
    ): Long = writeDocument(quote.copy(number = nextNumber(highestNumberWithPrefix(numberPrefix))), lines)

    /**
     * Replaces the header and the whole set of lines together.
     *
     * `convertedInvoiceId` and `createdAtEpochMillis` are carried over from the stored row, and
     * the binned flag with them: which bill a quote became is [recordConversion]'s to say, not an
     * editing screen's, and editing must not resurrect a binned quote.
     *
     * Returns null when the number now belongs to a different quote, and 0 when no live quote
     * has this id.
     */
    @Transaction
    open suspend fun updateWithLines(quote: QuoteEntity, lines: List<QuoteItemEntity>): Int? {
        val holder = idForNumber(quote.number)
        if (holder != null && holder != quote.id) return null
        val stored = headerById(quote.id) ?: return 0
        if (stored.deletedAtEpochMillis != null) return 0
        val changed = updateQuote(
            quote.copy(
                convertedInvoiceId = stored.convertedInvoiceId,
                createdAtEpochMillis = stored.createdAtEpochMillis,
                deletedAtEpochMillis = null,
            )
        )
        if (changed == 0) return 0
        deleteItemsFor(quote.id)
        insertItems(lines.mapIndexed { index, item -> item.copy(id = 0L, quoteId = quote.id, position = index) })
        return changed
    }

    private suspend fun writeDocument(quote: QuoteEntity, lines: List<QuoteItemEntity>): Long {
        val id = insertQuote(quote.copy(id = 0L))
        insertItems(lines.mapIndexed { index, item -> item.copy(id = 0L, quoteId = id, position = index) })
        return id
    }
}
