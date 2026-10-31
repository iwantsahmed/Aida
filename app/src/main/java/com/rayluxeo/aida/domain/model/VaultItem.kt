package com.rayluxeo.aida.domain.model

import java.time.Instant

enum class VaultCategory { NOTE, CREDENTIAL, DOCUMENT_REF, FINANCIAL, FILE }

enum class VaultFileType { PDF, IMAGE, RECEIPT, INVOICE, DOCUMENT, AUDIO, OTHER }

/**
 * Domain-level vault record. [secret] is plaintext in memory only; the data layer is
 * responsible for encrypting it with a key before it touches disk.
 */
data class VaultItem(
    val id: Long = 0L,
    val title: String,
    val secret: String,
    val category: VaultCategory = VaultCategory.NOTE,
    val fileType: VaultFileType = VaultFileType.OTHER,
    val folder: String? = null,
    val tags: List<String> = emptyList(),
    val clientId: Long? = null,
    val projectId: Long? = null,
    val invoiceId: Long? = null,
    val appPrivateFilePath: String? = null,
    val metadataJson: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Listing projection that never carries the decrypted secret. */
data class VaultItemSummary(
    val id: Long,
    val title: String,
    val category: VaultCategory,
    val fileType: VaultFileType = VaultFileType.OTHER,
    val folder: String? = null,
    val tags: List<String> = emptyList(),
    val clientId: Long? = null,
    val projectId: Long? = null,
    val invoiceId: Long? = null,
    val updatedAt: Instant,
)
