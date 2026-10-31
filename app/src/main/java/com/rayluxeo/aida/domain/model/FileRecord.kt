package com.rayluxeo.aida.domain.model

import java.time.Instant

/**
 * A document Mira holds on Abu's behalf.
 *
 * The bytes live on disk; this is the index entry that makes them findable. Two invariants come
 * with it and both are enforced below the domain:
 *
 * - [relativePath] is unique. One path is one record, so a file can never end up with two
 *   registrations that disagree about what it is.
 * - Deleting is always soft. A record removed while its bytes are still on disk would leave the
 *   file unreachable and unaccounted for, so [deletedAt] is set first and the bytes are reclaimed
 *   deliberately afterwards.
 */
data class FileRecord(
    val id: Long = 0L,
    /**
     * Stable identity outside this database — what an export or a backup carries, since an
     * autoincrement id is only unique within one copy of it. Blank until the record is
     * registered; the repository assigns it then and it never changes afterwards.
     */
    val uuid: String = "",
    val displayName: String,
    /** Relative to Mira's own files directory. An absolute path would not survive a restore. */
    val relativePath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val source: FileSource,
    /** Text read out of the document, if any. What makes a scanned receipt findable. */
    val extractedText: String? = null,
    val clientId: Long? = null,
    val projectId: Long? = null,
    /** Set while the record is in the bin and its bytes are still waiting to be reclaimed. */
    val deletedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
