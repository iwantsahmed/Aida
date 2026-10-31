package com.rayluxeo.aida.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.rayluxeo.aida.data.db.entity.MemoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * What Mira remembers.
 *
 * Forgetting is soft, so every list, observation and search filters `deletedAtEpochMillis IS
 * NULL`; `memory_fts` is external-content and would otherwise hand a forgotten fact straight back
 * through search. [findById] is the one exception, because restoring a fact has to read it.
 *
 * Recall is deliberately an increment in SQL and never a read-modify-write of the row. Two
 * recalls of the same fact in the same moment have to add up to two.
 */
@Dao
abstract class MemoryDao {

    @Query(
        """
        SELECT * FROM memory
        WHERE deletedAtEpochMillis IS NULL
        ORDER BY isPinned DESC, recallCount DESC, updatedAtEpochMillis DESC, id DESC
        """
    )
    abstract fun observeAll(): Flow<List<MemoryEntity>>

    @Query(
        """
        SELECT * FROM memory
        WHERE deletedAtEpochMillis IS NULL AND category = :category
        ORDER BY isPinned DESC, recallCount DESC, updatedAtEpochMillis DESC, id DESC
        """
    )
    abstract fun observeByCategory(category: com.rayluxeo.aida.domain.model.MemoryCategory): Flow<List<MemoryEntity>>

    @Query(
        """
        UPDATE memory
        SET isPinned = :isPinned, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NULL
        """
    )
    abstract suspend fun setPinned(id: Long, isPinned: Boolean, atEpochMillis: Long): Int

    @Query(
        """
        UPDATE memory
        SET category = :category, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NULL
        """
    )
    abstract suspend fun updateCategory(id: Long, category: com.rayluxeo.aida.domain.model.MemoryCategory, atEpochMillis: Long): Int

    @Query(
        """
        SELECT * FROM memory
        WHERE deletedAtEpochMillis IS NULL AND subject = :subject
        ORDER BY updatedAtEpochMillis DESC, id DESC
        """
    )
    abstract fun observeForSubject(subject: String): Flow<List<MemoryEntity>>

    @Query(
        """
        SELECT * FROM memory
        WHERE deletedAtEpochMillis IS NULL
        ORDER BY recallCount DESC, updatedAtEpochMillis DESC, id DESC
        LIMIT :limit
        """
    )
    abstract suspend fun listAll(limit: Int): List<MemoryEntity>

    /** Unfiltered by design: restoring a forgotten fact has to be able to read it. */
    @Query("SELECT * FROM memory WHERE id = :id")
    abstract suspend fun findById(id: Long): MemoryEntity?

    @Query("SELECT * FROM memory WHERE id IN (:ids)")
    abstract suspend fun findAllByIds(ids: List<Long>): List<MemoryEntity>

    /**
     * [match] is FTS4 MATCH syntax, not a literal — callers build it with `ftsMatchOrNull`.
     *
     * Most-recalled first: the fact Mira has leaned on before is the one most likely wanted
     * again, and it is the only ranking available without an external-content rank function.
     */
    @Query(
        """
        SELECT memory.* FROM memory
        JOIN memory_fts ON memory_fts.rowid = memory.id
        WHERE memory_fts MATCH :match AND memory.deletedAtEpochMillis IS NULL
        ORDER BY memory.recallCount DESC, memory.updatedAtEpochMillis DESC, memory.id DESC
        LIMIT :limit
        """
    )
    abstract suspend fun search(match: String, limit: Int): List<MemoryEntity>

    /**
     * `updatedAtEpochMillis` is deliberately left alone: using a fact is not editing it, and
     * bumping it would push every recalled fact to the top of a list ordered by last change.
     */
    @Query(
        """
        UPDATE memory
        SET recallCount = recallCount + 1, lastRecalledAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NULL
        """
    )
    abstract suspend fun recordRecall(id: Long, atEpochMillis: Long): Int

    @Insert
    abstract suspend fun insert(memory: MemoryEntity): Long

    @Update
    abstract suspend fun update(memory: MemoryEntity): Int

    @Query(
        """
        UPDATE memory SET deletedAtEpochMillis = :atEpochMillis, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NULL
        """
    )
    abstract suspend fun softDelete(id: Long, atEpochMillis: Long): Int

    @Query(
        """
        UPDATE memory SET deletedAtEpochMillis = NULL, updatedAtEpochMillis = :atEpochMillis
        WHERE id = :id AND deletedAtEpochMillis IS NOT NULL
        """
    )
    abstract suspend fun restore(id: Long, atEpochMillis: Long): Int

    /**
     * The lookup Mira makes when she is about to use what she knows, with the use recorded.
     *
     * One transaction so a caller can never see facts that were served but not counted, and the
     * rows are re-read afterwards so the counts returned are the ones now on disk rather than the
     * pre-increment values, which would be a quiet lie in the one place that must not tell one.
     */
    @Transaction
    open suspend fun searchAndRecordRecall(
        match: String,
        limit: Int,
        atEpochMillis: Long,
    ): List<MemoryEntity> {
        val matches = search(match, limit)
        if (matches.isEmpty()) return matches
        matches.forEach { recordRecall(it.id, atEpochMillis) }
        val refreshed = findAllByIds(matches.map { it.id }).associateBy { it.id }
        return matches.mapNotNull { refreshed[it.id] }
    }
}
