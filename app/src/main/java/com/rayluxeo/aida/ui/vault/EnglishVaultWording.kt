package com.rayluxeo.aida.ui.vault

import com.rayluxeo.aida.domain.model.VaultCategory

/** The vault in English: plain and exact, because every sentence here is about Abu's own data. */
internal object EnglishVaultWording : VaultWording {

    override val vaultTitle: String = "Vault"

    override val locked: String = "Locked"

    override val unlocked: String = "Unlocked"

    override val addEntry: String = "Add an entry"

    override val dismiss: String = "Dismiss"

    override val lockedHeadline: String = "The vault is locked."

    override val lockedBody: String =
        "Nothing in here is shown until you unlock it with your fingerprint, your face or your " +
            "device PIN."

    override val unlock: String = "Unlock"

    override val unlocking: String = "Waiting for you"

    override val setUpScreenLock: String = "Set up a screen lock"

    override val promptTitle: String = "Unlock the vault"

    override val promptSubtitle: String = "Everything in here stays on this phone."

    override val nothingStored: String =
        "Nothing stored yet. Anything you put here is encrypted on this phone and never leaves it."

    override val addFirstEntry: String = "Add an entry"

    override val copy: String = "Copy"

    override val delete: String = "Delete"

    override val keepIt: String = "Keep it"

    override fun deleteTitle(title: String): String = "Delete \"$title\"?"

    override val deleteMessage: String =
        "The encrypted entry is removed from this phone. There is no backup and no undo."

    override val newEntry: String = "New vault entry"

    override val entryTitleLabel: String = "Title"

    override val entryTitlePlaceholder: String = "Gmail recovery code"

    override val entryTitleNote: String =
        "The title is stored in the clear so the list can be read without unlocking. Keep the " +
            "secret itself in the field below."

    override val secretLabel: String = "Secret"

    override val categoryLabel: String = "Category"

    override val save: String = "Save to the vault"

    override val saving: String = "Saving"

    override fun category(category: VaultCategory): String = when (category) {
        VaultCategory.NOTE -> "Note"
        VaultCategory.CREDENTIAL -> "Credential"
        VaultCategory.DOCUMENT_REF -> "Document"
        VaultCategory.FINANCIAL -> "Financial"
        VaultCategory.FILE -> "File"
    }

    override fun lockReason(reason: VaultLockReason): String = when (reason) {
        VaultLockReason.NOT_RECOGNISED -> "That was not recognised. Try again, or use your PIN."
        VaultLockReason.CANCELLED -> "The vault stayed locked."
        VaultLockReason.LOCKED_OUT ->
            "Too many attempts. Wait a moment, or unlock with your device PIN."
        VaultLockReason.NONE_ENROLLED ->
            "This phone has no fingerprint, face or PIN set up. The vault needs one of those " +
                "before it will open."
        VaultLockReason.NO_WINDOW -> "The vault can only be unlocked from Aida's own window."
        VaultLockReason.SENSOR_BUSY -> "The fingerprint sensor is busy — try again in a moment."
        VaultLockReason.CANNOT_VERIFY -> "This phone cannot verify who you are right now."
        VaultLockReason.ENROLMENT_UNAVAILABLE ->
            "Aida could not open the screen-lock settings. Set a lock up from the system Settings " +
                "app, then come back."
    }

    override fun problem(problem: VaultProblem): String = when (problem) {
        VaultProblem.CANNOT_DECRYPT ->
            "That entry could not be decrypted. It was written with a key this phone no longer has."
        VaultProblem.GONE -> "That entry is no longer in the vault."
        VaultProblem.INCOMPLETE -> "A title and a secret are both needed."
        VaultProblem.STORAGE -> "The vault could not be written to. Try once more."
        VaultProblem.UNKNOWN -> "Something went wrong in the vault. Try again."
    }
}
