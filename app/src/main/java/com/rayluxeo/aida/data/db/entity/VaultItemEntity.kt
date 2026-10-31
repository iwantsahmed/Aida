package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.rayluxeo.aida.domain.model.VaultCategory
import com.rayluxeo.aida.domain.model.VaultFileType

/**
 * Encrypted vault record.
 */
@Entity(tableName = "vault_items")
data class VaultItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val cipherText: ByteArray,
    val iv: ByteArray,
    val category: VaultCategory,
    val fileType: VaultFileType = VaultFileType.OTHER,
    val folder: String? = null,
    val tagsCsv: String? = null,
    val clientId: Long? = null,
    val projectId: Long? = null,
    val invoiceId: Long? = null,
    val appPrivateFilePath: String? = null,
    val metadataJson: String? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
