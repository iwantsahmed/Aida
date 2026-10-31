package com.rayluxeo.aida.ui.backup

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.SpokenLanguage
import org.junit.Test

/**
 * The backup phrasebook is the one place the Backup & Restore screen and its view model get their
 * words, so a missed translation here is not a red build — it is an English label sitting in a
 * Bengali screen, which nobody notices until Abu does.
 *
 * Every assertion below is about a gap a human eye skips: a member added and worded in one language
 * only, or copy pasted across from English and never translated.
 */
class BackupWordingTest {

    private val english = backupWording(SpokenLanguage.ENGLISH)
    private val bengali = backupWording(SpokenLanguage.BENGALI)

    /** A sample table identifier for the one member that takes runtime data. */
    private val sampleEntity = "tasks"

    @Test
    fun `each language resolves to its own phrasebook`() {
        assertThat(english).isSameInstanceAs(EnglishBackupWording)
        assertThat(bengali).isSameInstanceAs(BengaliBackupWording)
    }

    @Test
    fun `every member is written in both languages`() {
        listOf(english, bengali).forEach { words ->
            words.allLines().forEach { line ->
                assertThat(line).isNotEmpty()
            }
        }
    }

    @Test
    fun `no member was left in English on the Bengali side`() {
        english.allLines().zip(bengali.allLines()).forEach { (englishLine, bengaliLine) ->
            assertThat(bengaliLine).isNotEqualTo(englishLine)
        }
    }

    /**
     * Every user-facing line the phrasebook holds, in a fixed order so the two languages line up
     * one-to-one. The runtime-data member is sampled with [sampleEntity].
     */
    private fun BackupWording.allLines(): List<String> = listOf(
        screenTitle,
        encryptedBackupTitle,
        encryptedBackupBody,
        passphraseLabel,
        passphrasePlaceholder,
        createBackup,
        restoreBackup,
        exportTitle,
        exportJson,
        exportCsv,
        passphraseRequiredForBackup,
        passphraseRequiredForRestore,
        creatingBackup,
        restoringBackup,
        backupCreated,
        backupRestored,
        jsonExported,
        csvExported(sampleEntity),
        backupFailed,
        restoreFailed,
        jsonExportFailed,
        csvExportFailed,
    )
}
