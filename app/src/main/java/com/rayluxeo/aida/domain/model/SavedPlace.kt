package com.rayluxeo.aida.domain.model

import java.time.Instant

/**
 * A place Abu named once so he can refer to it by name afterwards.
 *
 * This is what makes "remind me to buy printer ink when I get near the office" possible at all.
 * Mira has no geocoder and will not acquire one — looking an address up means sending Abu's
 * places to somebody's server, which §3 and `docs/PRIVACY.md` both rule out. So a place becomes
 * known the only way it honestly can: he stands somewhere and tells her to remember it.
 *
 * Distinct from [ReminderPlace], and the difference is the point. This is the directory entry —
 * "the office is here" — and it is reusable. [ReminderPlace] is one reminder's trigger, and it
 * keeps its OWN copy of the coordinate so that deleting or moving a saved place afterwards
 * cannot silently move a reminder Abu already set.
 *
 * There is deliberately no soft delete. Every other table carrying `deletedAtEpochMillis` holds a
 * record of something that happened and must survive; this holds a name for a spot on the map,
 * nothing downstream depends on it once a reminder has copied it, and a tombstone would collide
 * with the unique key the moment Abu re-saved a place under the name he had just freed.
 */
data class SavedPlace(
    val id: Long = 0L,
    /** As Abu wrote it, with his capitals and his script. Shown back to him unchanged. */
    val label: String,
    /** [label] reduced by [keyOf]. Stored so the unique index and the lookup agree by construction. */
    val labelKey: String,
    val latitude: Double,
    val longitude: Double,
    /** How close counts as "there". Bounded by the same limits a reminder's own radius is. */
    val radiusMeters: Float,
    val createdAt: Instant,
    val updatedAt: Instant,
) {

    companion object {

        /**
         * The form two spellings of one place have to agree on.
         *
         * Deliberately NOT [com.rayluxeo.aida.domain.nlu.TextNormalizer]. That folds Banglish onto
         * a consonant frame so a command can be understood, which is far too eager for a name Abu
         * chose: "Studio" and "Sadhu" reduce alike, and he would find one place answering to the
         * other's name. This does the two things a name lookup actually needs — case and spacing —
         * plus the leading article, because he saves "Office" and then says "the office".
         *
         * A Bengali-script label matches a Bengali-script utterance and a Latin one matches Latin.
         * Crossing scripts is not attempted: guessing wrong would attach a reminder to the wrong
         * place, and the alternative — Mira saying which places she knows — is one Abu can act on.
         */
        fun keyOf(label: String): String {
            val collapsed = label.trim().lowercase().split(WHITESPACE).filter { it.isNotEmpty() }
            val withoutArticle = collapsed.dropWhile { it in ARTICLES }
            // All article and nothing else: he named a place "the", so keep what he wrote rather
            // than reducing it to a key nothing can ever match.
            return (if (withoutArticle.isEmpty()) collapsed else withoutArticle).joinToString(" ")
        }

        private val WHITESPACE = Regex("\\s+")

        /** English and Banglish possessives Abu drops and re-adds between saving and speaking. */
        private val ARTICLES = setOf("the", "a", "an", "my", "amar", "আমার")
    }
}
