package com.rayluxeo.aida.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.rayluxeo.aida.data.db.entity.VaultItemEntity
import com.rayluxeo.aida.domain.model.VaultCategory
import com.rayluxeo.aida.domain.model.VaultFileType
import kotlinx.coroutines.flow.Flow

/**
 * Listing projection. It exists so the vault list query cannot accidentally pull ciphertext into
 * memory — the columns simply are not selected.
 */
data class VaultSummaryRow(
    val id: Long,
    val title: String,
    val category: VaultCategory,
    val fileType: VaultFileType = VaultFileType.OTHER,
    val folder: String? = null,
    val tagsCsv: String? = null,
    val clientId: Long? = null,
    val projectId: Long? = null,
    val invoiceId: Long? = null,
    val updatedAtEpochMillis: Long,
)

@Dao
interface VaultDao {

    @Query(
        """
        SELECT id, title, category, fileType, folder, tagsCsv, clientId, projectId, invoiceId, updatedAtEpochMillis FROM vault_items
        ORDER BY updatedAtEpochMillis DESC, id DESC
        """
    )
    fun observeSummaries(): Flow<List<VaultSummaryRow>>

    @Query("SELECT * FROM vault_items WHERE id = :id")
    suspend fun findById(id: Long): VaultItemEntity?

    @Insert
    suspend fun insert(item: VaultItemEntity): Long

    @Update
    suspend fun update(item: VaultItemEntity): Int

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
