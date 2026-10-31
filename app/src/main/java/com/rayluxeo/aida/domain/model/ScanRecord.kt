package com.rayluxeo.aida.domain.model

import java.time.Instant

/**
 * What happened when a document was scanned with the camera.
 *
 * A scan is an event with its own outcome, while the [FileRecord] it produced is the lasting
 * artefact — which is why the two are separate records. Exactly one scan belongs to one file, and
 * it goes when that file goes.
 */
data class ScanRecord(
    val id: Long = 0L,
    val fileId: Long,
    val pageCount: Int,
    /** Null until text recognition has been run over the scan. */
    val recognisedAt: Instant? = null,
    val createdAt: Instant,
)
