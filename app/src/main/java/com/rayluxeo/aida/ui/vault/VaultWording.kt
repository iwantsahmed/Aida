package com.rayluxeo.aida.ui.vault

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.VaultCategory

/**
 * Every word the vault puts in front of Abu, in one language.
 *
 * The screen used to carry its sentences as English literals, including the ones the view model
 * built for a refused unlock and a failed decryption. That made the vault the one place in Mira
 * that could not answer in Bengali — and it is the place where a sentence Abu cannot read is
 * least acceptable, because the thing being explained is why his own data will not open.
 *
 * The view model now hands the screen a [VaultLockNote] or a [VaultProblem] and this is where
 * either becomes words, which is the same split the rest of the app uses: the reason is a value,
 * the wording is presentation.
 */
internal interface VaultWording {

    val vaultTitle: String

    /** The subtitle on the bar, which is the fastest read of which state the door is in. */
    val locked: String

    val unlocked: String

    val addEntry: String

    val dismiss: String

    // --- The door ----------------------------------------------------------------------------

    val lockedHeadline: String

    val lockedBody: String

    val unlock: String

    /** While the platform prompt is up. Not a spinner: Mira is waiting on Abu, not on herself. */
    val unlocking: String

    val setUpScreenLock: String

    /** The prompt's own title. The system draws it, so it has to be worded here too. */
    val promptTitle: String

    val promptSubtitle: String

    // --- The list ----------------------------------------------------------------------------

    val nothingStored: String

    val addFirstEntry: String

    // --- One entry ---------------------------------------------------------------------------

    val copy: String

    val delete: String

    val keepIt: String

    fun deleteTitle(title: String): String

    val deleteMessage: String

    // --- Adding one --------------------------------------------------------------------------

    val newEntry: String

    val entryTitleLabel: String

    val entryTitlePlaceholder: String

    /** Why the title is not a secret. Said once, where Abu is typing it. */
    val entryTitleNote: String

    val secretLabel: String

    val categoryLabel: String

    val save: String

    val saving: String

    fun category(category: VaultCategory): String

    // --- What went wrong ---------------------------------------------------------------------

    fun lockReason(reason: VaultLockReason): String

    fun problem(problem: VaultProblem): String

    /**
     * The platform's own sentence is passed through untranslated on purpose: it already arrives
     * in the device's language, and rewriting it would lose what the system actually said.
     */
    fun lockNote(note: VaultLockNote): String = when (note) {
        is VaultLockNote.Refused -> lockReason(note.reason)
        is VaultLockNote.System -> note.message
    }
}

internal fun vaultWording(language: SpokenLanguage): VaultWording = when (language) {
    SpokenLanguage.BENGALI -> BengaliVaultWording
    SpokenLanguage.ENGLISH -> EnglishVaultWording
}
