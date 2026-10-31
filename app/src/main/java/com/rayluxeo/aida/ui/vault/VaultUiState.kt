package com.rayluxeo.aida.ui.vault

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.VaultCategory
import com.rayluxeo.aida.domain.model.VaultItemSummary

/** Where the vault door is. Nothing but the lock itself is drawn outside [UNLOCKED]. */
enum class VaultPhase { LOCKED, UNLOCKING, UNLOCKED }

/** The list's three shapes, so a vault that is still loading never reads as a vault that is empty. */
sealed interface VaultItems {
    data object Loading : VaultItems
    data object Empty : VaultItems
    data class Content(val items: List<VaultItemSummary>) : VaultItems
}

/**
 * Why the door did not open.
 *
 * A value rather than a sentence: the wording lives in [VaultWording] so the vault can explain
 * itself in Bengali, and the view model stays free of English.
 */
enum class VaultLockReason {
    /** A finger or a face the sensor did not accept. The prompt is still up. */
    NOT_RECOGNISED,

    /** Abu dismissed the prompt. */
    CANCELLED,

    /** Too many attempts; the sensor has shut itself for a while. */
    LOCKED_OUT,

    /** No fingerprint, face or PIN on the phone at all. */
    NONE_ENROLLED,

    /** The prompt needs an Activity and there was none — a host Mira does not own. */
    NO_WINDOW,

    SENSOR_BUSY,

    /** The hardware is there but cannot answer right now. */
    CANNOT_VERIFY,

    /** The system's own screen-lock setup could not be opened. */
    ENROLMENT_UNAVAILABLE,
}

/** A line about the last refused unlock, which is either Mira's reason or the platform's own. */
sealed interface VaultLockNote {
    data class Refused(val reason: VaultLockReason) : VaultLockNote

    /** The system's wording, kept verbatim: it already arrives in the device's language. */
    data class System(val message: String) : VaultLockNote
}

/** A failed read or write of the vault itself, worded per language by [VaultWording]. */
enum class VaultProblem {
    /** The row is there but the key that wrote it is not. */
    CANNOT_DECRYPT,
    GONE,
    INCOMPLETE,
    STORAGE,
    UNKNOWN,
}

/**
 * A secret held in memory for exactly as long as the reveal sheet is open. Locking the vault
 * drops it, which is why it lives in the state rather than inside the sheet's own composition.
 */
data class RevealedSecret(
    val id: Long,
    val title: String,
    val category: VaultCategory,
    val secret: String,
)

/** A delete Abu has been asked about but has not agreed to yet. */
data class VaultDeletion(val id: Long, val title: String)

data class VaultDraft(
    val title: String = "",
    val secret: String = "",
    val category: VaultCategory = VaultCategory.NOTE,
) {
    val isComplete: Boolean get() = title.isNotBlank() && secret.isNotEmpty()
}

/**
 * [lock] explains the last refused unlock and [problem] explains a failed read or write.
 * They are separate because one belongs on the lock screen and the other belongs over the list.
 */
data class VaultUiState(
    val phase: VaultPhase = VaultPhase.LOCKED,
    /** Which language the vault writes in; everything visible is worded to match. */
    val language: SpokenLanguage = SpokenLanguage.ENGLISH,
    val items: VaultItems = VaultItems.Loading,
    val lock: VaultLockNote? = null,
    val needsEnrolment: Boolean = false,
    val revealing: Boolean = false,
    val revealed: RevealedSecret? = null,
    val composerOpen: Boolean = false,
    val draft: VaultDraft = VaultDraft(),
    val saving: Boolean = false,
    val confirmingDelete: VaultDeletion? = null,
    val problem: VaultProblem? = null,
) {
    val isUnlocked: Boolean get() = phase == VaultPhase.UNLOCKED
}
