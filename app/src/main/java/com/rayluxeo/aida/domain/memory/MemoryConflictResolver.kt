package com.rayluxeo.aida.domain.memory

import com.rayluxeo.aida.domain.model.MemoryCategory
import com.rayluxeo.aida.domain.model.MemoryFact
import java.util.Locale

object MemoryConflictResolver {

    fun inferCategory(subject: String, fact: String): MemoryCategory {
        val combined = "$subject $fact".lowercase(Locale.ROOT)

        return when {
            containsAny(combined, "prefer", "prefers", "whatsapp", "email", "communication", "contact method", "call time") ->
                if (containsAny(combined, "client", "customer", "invoice")) MemoryCategory.CLIENT_PREFERENCE else MemoryCategory.PERSONAL_PREFERENCE

            containsAny(combined, "deposit", "policy", "terms", "rule", "standard", "normally due", "due in", "agreement") ->
                MemoryCategory.BUSINESS_RULE

            containsAny(combined, "rate", "price", "pricing", "cost", "fee", "percent", "%", "bdt", "usd", "hourly") ->
                MemoryCategory.PRICING

            containsAny(combined, "birthday", "married", "son", "daughter", "brother", "sister", "wife", "friend", "lives in") ->
                MemoryCategory.PERSON

            containsAny(combined, "every", "daily", "routine", "schedule", "always on", "habits") ->
                MemoryCategory.ROUTINE

            containsAny(combined, "project", "repo", "stack", "deadline", "version", "milestone") ->
                MemoryCategory.PROJECT_FACT

            else -> MemoryCategory.GENERAL_FACT
        }
    }

    fun findConflictingFact(newFact: MemoryFact, existingFacts: List<MemoryFact>): MemoryFact? {
        val cleanSubject = newFact.subject.trim().lowercase(Locale.ROOT)
        return existingFacts.firstOrNull { existing ->
            existing.id != newFact.id &&
                existing.deletedAt == null &&
                existing.subject.trim().lowercase(Locale.ROOT) == cleanSubject &&
                existing.fact.trim() != newFact.fact.trim()
        }
    }

    private fun containsAny(text: String, vararg keywords: String): Boolean =
        keywords.any { text.contains(it) }
}
