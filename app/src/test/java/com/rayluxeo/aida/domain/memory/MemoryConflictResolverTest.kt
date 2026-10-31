package com.rayluxeo.aida.domain.memory

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.domain.model.MemoryCategory
import com.rayluxeo.aida.domain.model.MemoryFact
import org.junit.Test
import java.time.Instant

class MemoryConflictResolverTest {

    @Test
    fun `inferCategory identifies CLIENT_PREFERENCE for preferred communication method`() {
        val category = MemoryConflictResolver.inferCategory("Ahmed", "Ahmed prefers WhatsApp for client communication")
        assertThat(category).isEqualTo(MemoryCategory.CLIENT_PREFERENCE)
    }

    @Test
    fun `inferCategory identifies BUSINESS_RULE for deposit percentage`() {
        val category = MemoryConflictResolver.inferCategory("website deposit", "Standard website deposit is 50%")
        assertThat(category).isEqualTo(MemoryCategory.BUSINESS_RULE)
    }

    @Test
    fun `inferCategory identifies BUSINESS_RULE for invoice due terms`() {
        val category = MemoryConflictResolver.inferCategory("Rayluxeo invoices", "Rayluxeo invoices are normally due in seven days")
        assertThat(category).isEqualTo(MemoryCategory.BUSINESS_RULE)
    }

    @Test
    fun `findConflictingFact finds active memory with matching subject but different fact`() {
        val now = Instant.now()
        val existing = listOf(
            MemoryFact(id = 1L, subject = "Ahmed", fact = "Ahmed prefers Email", createdAt = now, updatedAt = now),
        )
        val newFact = MemoryFact(id = 0L, subject = "Ahmed", fact = "Ahmed prefers WhatsApp", createdAt = now, updatedAt = now)

        val conflict = MemoryConflictResolver.findConflictingFact(newFact, existing)
        assertThat(conflict).isNotNull()
        assertThat(conflict?.id).isEqualTo(1L)
    }
}
