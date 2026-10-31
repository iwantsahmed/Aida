package com.rayluxeo.aida.domain.model

import com.rayluxeo.aida.core.common.SpokenLanguage
import java.time.Instant

enum class MemoryCategory {
    PERSONAL_PREFERENCE,
    BUSINESS_RULE,
    CLIENT_PREFERENCE,
    PRICING,
    PERSON,
    ROUTINE,
    PROJECT_FACT,
    GENERAL_FACT,
}

/**
 * Something Mira has been told to remember.
 *
 * Named `MemoryFact` and not `Memory` deliberately: "memory" reads as a runtime concept and would
 * collide in every file that also touches the engine.
 *
 * [subject] is what the fact is *about* and [fact] is its wording, kept apart so recall can match
 * the subject first. [recallCount] and [lastRecalledAt] are Mira's own honesty about which facts
 * she actually leans on — they are never edited by hand, only stamped when a fact is used.
 */
data class MemoryFact(
    val id: Long = 0L,
    val subject: String,
    val fact: String,
    val category: MemoryCategory = MemoryCategory.GENERAL_FACT,
    val isPinned: Boolean = false,
    val source: MemorySource = MemorySource.INFERRED,
    val language: SpokenLanguage = SpokenLanguage.ENGLISH,
    val clientId: Long? = null,
    val projectId: Long? = null,
    val recallCount: Int = 0,
    val lastRecalledAt: Instant? = null,
    /** When the fact was forgotten, or null while Mira still holds it. */
    val deletedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
