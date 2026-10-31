package com.rayluxeo.aida.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.rayluxeo.aida.data.db.entity.InkPageEntity
import kotlinx.coroutines.flow.Flow

/**
 * The handwriting pages.
 *
 * No soft delete here, deliberately, and it is not the oversight §8 warns about: a page is a
 * pointer to two files and carries nothing that could not be rebuilt from them. The FILES are
 * soft deleted, which is where the recoverable history actually lives — keeping a tombstone row
 * as well would mean two bins for one act of deleting.
 */
@Dao
interface InkPageDao {

    @Query("SELECT * FROM ink_pages WHERE noteId = :noteId ORDER BY createdAtEpochMillis ASC, id ASC")
    fun observeForNote(noteId: Long): Flow<List<InkPageEntity>>

    @Query("SELECT * FROM ink_pages WHERE noteId = :noteId ORDER BY createdAtEpochMillis ASC, id ASC")
    suspend fun forNote(noteId: Long): List<InkPageEntity>

    @Query("SELECT * FROM ink_pages WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): InkPageEntity?

    /** Which notes have handwriting at all — the one query the ink filter on a note list needs. */
    @Query("SELECT DISTINCT noteId FROM ink_pages")
    suspend fun noteIdsWithInk(): List<Long>

    @Insert
    suspend fun insert(page: InkPageEntity): Long

    @Update
    suspend fun update(page: InkPageEntity): Int

    @Query("DELETE FROM ink_pages WHERE id = :id")
    suspend fun delete(id: Long)
}
