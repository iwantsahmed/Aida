package com.rayluxeo.aida.ui.backup

import com.rayluxeo.aida.core.common.SpokenLanguage

/**
 * Every word the Backup & Restore screen puts in front of Abu, in one language.
 *
 * The screen and its view model used to carry their sentences as English literals — including the
 * ones built for a failed backup, where the view model also spilled [com.rayluxeo.aida.core.common.MiraError.detail],
 * which AGENTS.md §4 reserves for logs and forbids speaking verbatim. That made this the one screen
 * Abu could not read in Bengali, and it is a screen about his own data leaving and returning to the
 * phone, where a sentence he cannot read is least acceptable.
 *
 * Both impls and the resolver live in this one file: the backup vocabulary is small and read in
 * exactly two places, so three files would start identical and only drift.
 *
 * Two algorithm names stay in Latin on purpose. "AES-GCM" and "PBKDF2" are the names of the
 * ciphers Mira actually uses, and naming them is the whole point of the line — transliterating
 * them would turn a precise promise about encryption into a word nobody could look up.
 */
internal interface BackupWording {

    val screenTitle: String

    // --- The encrypted backup card -------------------------------------------------------------

    val encryptedBackupTitle: String

    val encryptedBackupBody: String

    val passphraseLabel: String

    val passphrasePlaceholder: String

    val createBackup: String

    val restoreBackup: String

    // --- The export card -----------------------------------------------------------------------

    val exportTitle: String

    val exportJson: String

    val exportCsv: String

    // --- Status, while something is in flight --------------------------------------------------

    val passphraseRequiredForBackup: String

    val passphraseRequiredForRestore: String

    val creatingBackup: String

    val restoringBackup: String

    // --- What landed ---------------------------------------------------------------------------

    val backupCreated: String

    val backupRestored: String

    /**
     * Shown after a successful restore. The restored data is on disk, but the running app still
     * holds the old database and settings in memory, so Abu is told to restart for it to take hold.
     */
    val restoreRestartRequired: String

    /** The action beside [restoreRestartRequired] that closes and relaunches Mira. */
    val restartNow: String

    val jsonExported: String

    /**
     * [entity] is the table identifier the screen asked to export (today only "tasks"). It is a
     * data value passed straight through, not a word to translate, exactly as a filename would be.
     */
    fun csvExported(entity: String): String

    // --- What went wrong -----------------------------------------------------------------------

    val backupFailed: String

    val restoreFailed: String

    val jsonExportFailed: String

    val csvExportFailed: String
}

/** The backup screen in English: plain and exact, because every sentence is about Abu's own data. */
internal object EnglishBackupWording : BackupWording {

    override val screenTitle: String = "Backup & Restore"

    override val encryptedBackupTitle: String = "Portable Encrypted Backup"

    override val encryptedBackupBody: String =
        "Backups are encrypted using AES-GCM and PBKDF2 key derivation. Your passphrase is " +
            "required to restore on any device."

    override val passphraseLabel: String = "Encryption Passphrase"

    override val passphrasePlaceholder: String = "Enter a strong passphrase"

    override val createBackup: String = "Create Backup"

    override val restoreBackup: String = "Restore Backup"

    override val exportTitle: String = "Export Data"

    override val exportJson: String = "Export JSON"

    override val exportCsv: String = "Export CSV"

    override val passphraseRequiredForBackup: String =
        "Enter a passphrase to encrypt the backup."

    override val passphraseRequiredForRestore: String =
        "Enter the passphrase to decrypt the backup."

    override val creatingBackup: String = "Creating the encrypted backup…"

    override val restoringBackup: String = "Validating and restoring the backup…"

    override val backupCreated: String = "Backup created. Encrypted with AES-GCM."

    override val backupRestored: String = "Backup restored."

    override val restoreRestartRequired: String =
        "Backup restored. Restart Aida for the restored data to take effect."

    override val restartNow: String = "Restart now"

    override val jsonExported: String = "JSON exported."

    override fun csvExported(entity: String): String = "CSV exported for $entity."

    override val backupFailed: String =
        "The backup could not be created. Please try again."

    override val restoreFailed: String =
        "The backup could not be restored. Check your passphrase and the file, then try again."

    override val jsonExportFailed: String =
        "The export could not be completed. Please try again."

    override val csvExportFailed: String =
        "The CSV export could not be completed. Please try again."
}

/**
 * The backup screen in Bengali. Bengali script throughout, so a Bengali screen is not an English
 * card over a Bengali list. Only the two algorithm names and a passed-through table identifier
 * stay in Latin, for the reasons given on [BackupWording].
 */
internal object BengaliBackupWording : BackupWording {

    override val screenTitle: String = "ব্যাকআপ ও পুনরুদ্ধার"

    override val encryptedBackupTitle: String = "বহনযোগ্য এনক্রিপ্টেড ব্যাকআপ"

    override val encryptedBackupBody: String =
        "ব্যাকআপ AES-GCM আর PBKDF2 কী-ডেরিভেশন দিয়ে এনক্রিপ্ট করা হয়। যেকোনো ডিভাইসে " +
            "পুনরুদ্ধার করতে আপনার পাসফ্রেজ লাগবে।"

    override val passphraseLabel: String = "এনক্রিপশন পাসফ্রেজ"

    override val passphrasePlaceholder: String = "একটি শক্তিশালী পাসফ্রেজ দিন"

    override val createBackup: String = "ব্যাকআপ তৈরি করুন"

    override val restoreBackup: String = "ব্যাকআপ পুনরুদ্ধার করুন"

    override val exportTitle: String = "ডেটা এক্সপোর্ট"

    override val exportJson: String = "JSON এক্সপোর্ট করুন"

    override val exportCsv: String = "CSV এক্সপোর্ট করুন"

    override val passphraseRequiredForBackup: String =
        "ব্যাকআপ এনক্রিপ্ট করতে একটি পাসফ্রেজ দিন।"

    override val passphraseRequiredForRestore: String =
        "ব্যাকআপ ডিক্রিপ্ট করতে পাসফ্রেজটি দিন।"

    override val creatingBackup: String = "এনক্রিপ্টেড ব্যাকআপ তৈরি হচ্ছে…"

    override val restoringBackup: String = "ব্যাকআপ যাচাই করে পুনরুদ্ধার করা হচ্ছে…"

    override val backupCreated: String = "ব্যাকআপ তৈরি হয়েছে। AES-GCM দিয়ে এনক্রিপ্ট করা।"

    override val backupRestored: String = "ব্যাকআপ পুনরুদ্ধার হয়েছে।"

    override val restoreRestartRequired: String =
        "ব্যাকআপ পুনরুদ্ধার হয়েছে। পুনরুদ্ধার করা ডেটা কার্যকর করতে Aida আবার চালু করুন।"

    override val restartNow: String = "এখনই আবার চালু করুন"

    override val jsonExported: String = "JSON এক্সপোর্ট হয়েছে।"

    override fun csvExported(entity: String): String = "$entity-এর CSV এক্সপোর্ট হয়েছে।"

    override val backupFailed: String =
        "ব্যাকআপ তৈরি করা গেল না। আবার চেষ্টা করুন।"

    override val restoreFailed: String =
        "ব্যাকআপ পুনরুদ্ধার করা গেল না। পাসফ্রেজ আর ফাইলটি দেখে আবার চেষ্টা করুন।"

    override val jsonExportFailed: String =
        "এক্সপোর্ট সম্পূর্ণ করা গেল না। আবার চেষ্টা করুন।"

    override val csvExportFailed: String =
        "CSV এক্সপোর্ট সম্পূর্ণ করা গেল না। আবার চেষ্টা করুন।"
}

internal fun backupWording(language: SpokenLanguage): BackupWording = when (language) {
    SpokenLanguage.BENGALI -> BengaliBackupWording
    SpokenLanguage.ENGLISH -> EnglishBackupWording
}
