package com.rayluxeo.aida.data.repository

import android.content.Context
import android.os.Environment
import com.rayluxeo.aida.domain.quran.QuranProgress
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages persistent on-device backups of Quran recitation progress.
 *
 * Backs up to external storage (Documents/Aida and Android/data/com.rayluxeo.aida/files) as well
 * as app-internal storage so that progress is preserved across app reinstalls, updates, or
 * device migrations.
 */
@Singleton
class QuranProgressBackupHelper @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    data class QuranBackupDto(
        val currentPage: Int,
        val khatamCount: Int,
        val dailyTarget: Int,
        val completedDatesCount: Int,
        val lastReadDate: String?,
        val lastReadPageToday: Int?,
        val lastStoppedPageToday: Int?,
        val pagesReadToday: Int,
        val readPagesSet: Set<Int>,
        val timestamp: Long,
    )

    fun writeBackup(progress: QuranProgress): Boolean {
        return try {
            val json = JSONObject().apply {
                put("version", 1)
                put("currentPage", progress.currentPage)
                put("khatamCount", progress.khatamCount)
                put("dailyTarget", progress.dailyTarget)
                put("completedDatesCount", progress.completedDatesCount)
                put("lastReadDate", progress.lastReadDate?.toString())
                put("lastReadPageToday", progress.lastReadPageToday ?: -1)
                put("lastStoppedPageToday", progress.lastStoppedPageToday ?: -1)
                put("pagesReadToday", progress.pagesReadToday)
                put("continueFromPage", progress.continueFromPage)
                put("timestamp", System.currentTimeMillis())

                val arr = JSONArray()
                progress.pagesReadSet.forEach { arr.put(it) }
                put("readPagesSet", arr)
            }
            val text = json.toString(2)

            // 1. External files directory (accessible and survives some updates)
            runCatching {
                val externalDir = context.getExternalFilesDir(null)
                if (externalDir != null) {
                    val file = File(externalDir, BACKUP_FILE_NAME)
                    file.writeText(text, Charsets.UTF_8)
                }
            }

            // 2. Public Documents directory (Aida/quran_progress_backup.json)
            runCatching {
                val docsDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "Aida",
                )
                if (!docsDir.exists()) docsDir.mkdirs()
                val docFile = File(docsDir, BACKUP_FILE_NAME)
                docFile.writeText(text, Charsets.UTF_8)
            }

            // 3. Internal app files directory
            runCatching {
                val internalFile = File(context.filesDir, BACKUP_FILE_NAME)
                internalFile.writeText(text, Charsets.UTF_8)
            }

            true
        } catch (_: Exception) {
            false
        }
    }

    fun readBackup(): QuranBackupDto? {
        val candidateFiles = listOfNotNull(
            // Check Documents/Aida/ first
            runCatching {
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "Aida/$BACKUP_FILE_NAME",
                )
            }.getOrNull(),
            // Then external files dir
            context.getExternalFilesDir(null)?.let { File(it, BACKUP_FILE_NAME) },
            // Then internal files
            File(context.filesDir, BACKUP_FILE_NAME),
        )

        for (file in candidateFiles) {
            if (file.exists() && file.canRead()) {
                val dto = parseFile(file)
                if (dto != null) return dto
            }
        }
        return null
    }

    private fun parseFile(file: File): QuranBackupDto? {
        return try {
            val text = file.readText(Charsets.UTF_8).trim()
            if (text.isEmpty()) return null
            val obj = JSONObject(text)

            val readSet = mutableSetOf<Int>()
            val arr = obj.optJSONArray("readPagesSet")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    readSet.add(arr.getInt(i))
                }
            }

            val lastReadPage = obj.optInt("lastReadPageToday", -1).takeIf { it > 0 }
            val lastStoppedPage = obj.optInt("lastStoppedPageToday", -1).takeIf { it > 0 }

            QuranBackupDto(
                currentPage = obj.optInt("currentPage", 1).coerceIn(1, QuranProgress.TOTAL_PAGES),
                khatamCount = obj.optInt("khatamCount", 0).coerceAtLeast(0),
                dailyTarget = obj.optInt("dailyTarget", QuranProgress.DEFAULT_DAILY_TARGET).coerceAtLeast(1),
                completedDatesCount = obj.optInt("completedDatesCount", 0).coerceAtLeast(0),
                lastReadDate = obj.optString("lastReadDate", "").takeIf { it.isNotBlank() },
                lastReadPageToday = lastReadPage,
                lastStoppedPageToday = lastStoppedPage,
                pagesReadToday = obj.optInt("pagesReadToday", 0).coerceAtLeast(0),
                readPagesSet = readSet,
                timestamp = obj.optLong("timestamp", 0L),
            )
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val BACKUP_FILE_NAME = "aida_quran_progress_backup.json"
    }
}
