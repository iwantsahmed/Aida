package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.asSuccess
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.core.common.getOrNull
import com.rayluxeo.aida.domain.quran.QuranProgress
import com.rayluxeo.aida.domain.quran.QuranRepository
import com.rayluxeo.aida.domain.repository.AppSettingRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Room-backed persistence for Quran recitation target and progress, with automatic
 * on-device JSON backups to external storage so progress survives reinstalls.
 */
@Singleton
class QuranRepositoryImpl @Inject constructor(
    private val appSettings: AppSettingRepository,
    private val backupHelper: QuranProgressBackupHelper? = null,
) : QuranRepository {

    override fun observeProgress(date: LocalDate): Flow<QuranProgress> {
        val dateStr = date.toString()
        val flows = listOf(
            appSettings.observe(KEY_CURRENT_PAGE),
            appSettings.observe(KEY_DAILY_TARGET),
            appSettings.observe(keyPagesRead(dateStr)),
            appSettings.observe(KEY_COMPLETED_DAYS),
            appSettings.observe(keyReadSet(dateStr)),
            appSettings.observe(keyLastViewed(dateStr)),
            appSettings.observe(KEY_KHATAM_COUNT),
        )
        return combine(flows) { values: Array<String?> ->
            val pageStr = values[0]
            val targetStr = values[1]
            val readStr = values[2]
            val streakStr = values[3]
            val readSetRaw = values[4]
            val lastViewedStr = values[5]
            val khatamStr = values[6]

            val pagesSet = readSetRaw.orEmpty().split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .toSet()
            val lastReadToday = pagesSet.maxOrNull()
            val lastViewedToday = lastViewedStr?.toIntOrNull()
            val bookmarkedPage = pageStr?.toIntOrNull()?.coerceIn(1, QuranProgress.TOTAL_PAGES) ?: 1
            val khatamCount = khatamStr?.toIntOrNull()?.coerceAtLeast(0) ?: 0

            val continuePage = when {
                lastViewedToday != null -> lastViewedToday.coerceIn(1, QuranProgress.TOTAL_PAGES)
                lastReadToday != null -> {
                    if (lastReadToday < QuranProgress.TOTAL_PAGES) lastReadToday + 1 else 1
                }
                else -> bookmarkedPage
            }

            QuranProgress(
                currentPage = bookmarkedPage,
                pagesReadToday = readStr?.toIntOrNull()?.coerceAtLeast(0) ?: pagesSet.size,
                dailyTarget = targetStr?.toIntOrNull()?.coerceAtLeast(1) ?: QuranProgress.DEFAULT_DAILY_TARGET,
                lastReadDate = date,
                completedDatesCount = streakStr?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                pagesReadSet = pagesSet,
                lastReadPageToday = lastReadToday,
                lastStoppedPageToday = lastViewedToday ?: lastReadToday,
                continueFromPage = continuePage,
                khatamCount = khatamCount,
            )
        }
    }

    override suspend fun getProgress(date: LocalDate): AppResult<QuranProgress> {
        val dateStr = date.toString()
        var pageStr = appSettings.get(KEY_CURRENT_PAGE).getOrNull()

        // If settings are completely empty (e.g. freshly installed app), check for on-device backup
        if (pageStr == null && backupHelper != null) {
            val backup = backupHelper.readBackup()
            if (backup != null) {
                appSettings.put(KEY_CURRENT_PAGE, backup.currentPage.toString())
                appSettings.put(KEY_KHATAM_COUNT, backup.khatamCount.toString())
                appSettings.put(KEY_DAILY_TARGET, backup.dailyTarget.toString())
                appSettings.put(KEY_COMPLETED_DAYS, backup.completedDatesCount.toString())
                if (backup.lastStoppedPageToday != null) {
                    appSettings.put(KEY_LAST_STOPPED_PAGE, backup.lastStoppedPageToday.toString())
                }
                pageStr = backup.currentPage.toString()
            }
        }

        val targetStr = appSettings.get(KEY_DAILY_TARGET).getOrNull()
        val readStr = appSettings.get(keyPagesRead(dateStr)).getOrNull()
        val streakStr = appSettings.get(KEY_COMPLETED_DAYS).getOrNull()
        val readSetRaw = appSettings.get(keyReadSet(dateStr)).getOrNull().orEmpty()
        val lastViewedStr = appSettings.get(keyLastViewed(dateStr)).getOrNull()
        val khatamStr = appSettings.get(KEY_KHATAM_COUNT).getOrNull()

        val pagesSet = readSetRaw.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()
        val lastReadToday = pagesSet.maxOrNull()
        val lastViewedToday = lastViewedStr?.toIntOrNull()
        val bookmarkedPage = pageStr?.toIntOrNull()?.coerceIn(1, QuranProgress.TOTAL_PAGES) ?: 1
        val khatamCount = khatamStr?.toIntOrNull()?.coerceAtLeast(0) ?: 0

        val continuePage = when {
            lastViewedToday != null -> lastViewedToday.coerceIn(1, QuranProgress.TOTAL_PAGES)
            lastReadToday != null -> {
                if (lastReadToday < QuranProgress.TOTAL_PAGES) lastReadToday + 1 else 1
            }
            else -> bookmarkedPage
        }

        val progress = QuranProgress(
            currentPage = bookmarkedPage,
            pagesReadToday = readStr?.toIntOrNull()?.coerceAtLeast(0) ?: pagesSet.size,
            dailyTarget = targetStr?.toIntOrNull()?.coerceAtLeast(1) ?: QuranProgress.DEFAULT_DAILY_TARGET,
            lastReadDate = date,
            completedDatesCount = streakStr?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            pagesReadSet = pagesSet,
            lastReadPageToday = lastReadToday,
            lastStoppedPageToday = lastViewedToday ?: lastReadToday,
            continueFromPage = continuePage,
            khatamCount = khatamCount,
        )

        return progress.asSuccess()
    }

    override suspend fun markPageRead(pageNumber: Int, date: LocalDate): AppResult<QuranProgress> {
        val dateStr = date.toString()
        val setKey = keyReadSet(dateStr)
        val readSetRaw = appSettings.get(setKey).getOrNull().orEmpty()
        val pagesSet = readSetRaw.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .toMutableSet()

        val wasAdded = pagesSet.add(pageNumber)
        val target = appSettings.get(KEY_DAILY_TARGET).getOrNull()?.toIntOrNull() ?: QuranProgress.DEFAULT_DAILY_TARGET

        if (wasAdded) {
            appSettings.put(setKey, pagesSet.joinToString(","))
            val newReadCount = pagesSet.size
            appSettings.put(keyPagesRead(dateStr), newReadCount.toString())

            if (newReadCount == target) {
                val currentStreak = appSettings.get(KEY_COMPLETED_DAYS).getOrNull()?.toIntOrNull() ?: 0
                appSettings.put(KEY_COMPLETED_DAYS, (currentStreak + 1).toString())
            }
        }

        // Advance bookmark to next page. When reaching 604 (end of Quran), wrap to page 1 and increment Khatam count!
        val nextPage: Int
        if (pageNumber >= QuranProgress.TOTAL_PAGES) {
            nextPage = 1
            val currentKhatams = appSettings.get(KEY_KHATAM_COUNT).getOrNull()?.toIntOrNull() ?: 0
            appSettings.put(KEY_KHATAM_COUNT, (currentKhatams + 1).toString())
        } else {
            nextPage = pageNumber + 1
        }

        appSettings.put(KEY_CURRENT_PAGE, nextPage.toString())
        appSettings.put(KEY_LAST_STOPPED_PAGE, pageNumber.toString())
        appSettings.put(keyLastViewed(dateStr), nextPage.toString())

        val updated = (getProgress(date) as? AppResult.Success)?.value
        if (updated != null) {
            backupHelper?.writeBackup(updated)
        }

        return getProgress(date)
    }

    override suspend fun saveLastViewedPage(pageNumber: Int, date: LocalDate): AppResult<Unit> {
        val clamped = pageNumber.coerceIn(1, QuranProgress.TOTAL_PAGES)
        val dateStr = date.toString()
        appSettings.put(keyLastViewed(dateStr), clamped.toString())
        appSettings.put(KEY_LAST_STOPPED_PAGE, clamped.toString())
        appSettings.put(KEY_CURRENT_PAGE, clamped.toString())

        val updated = (getProgress(date) as? AppResult.Success)?.value
        if (updated != null) {
            backupHelper?.writeBackup(updated)
        }
        return AppResult.Success(Unit)
    }

    override suspend fun setCurrentPage(pageNumber: Int): AppResult<Unit> {
        val clamped = pageNumber.coerceIn(1, QuranProgress.TOTAL_PAGES)
        appSettings.put(KEY_LAST_STOPPED_PAGE, clamped.toString())
        val result = appSettings.put(KEY_CURRENT_PAGE, clamped.toString())
        val updated = (getProgress(LocalDate.now()) as? AppResult.Success)?.value
        if (updated != null) {
            backupHelper?.writeBackup(updated)
        }
        return result
    }

    override suspend fun setDailyTarget(target: Int): AppResult<Unit> {
        val clamped = target.coerceAtLeast(1)
        val result = appSettings.put(KEY_DAILY_TARGET, clamped.toString())
        val updated = (getProgress(LocalDate.now()) as? AppResult.Success)?.value
        if (updated != null) {
            backupHelper?.writeBackup(updated)
        }
        return result
    }

    override suspend fun backupToDevice(): AppResult<String> {
        val current = (getProgress(LocalDate.now()) as? AppResult.Success)?.value
            ?: return failure(MiraErrorCode.STORAGE, "অগ্রগতি তথ্য পাওয়া যায়নি")
        val success = backupHelper?.writeBackup(current) ?: false
        return if (success) {
            "ডিভাইসের স্টোরেজে ব্যাকআপ সফলভাবে সংরক্ষিত হয়েছে".asSuccess()
        } else {
            failure(MiraErrorCode.STORAGE, "ব্যাকআপ ফাইল সংরক্ষণ করা যায়নি")
        }
    }

    override suspend fun restoreFromDevice(date: LocalDate): AppResult<QuranProgress> {
        val backup = backupHelper?.readBackup()
            ?: return failure(MiraErrorCode.NOT_FOUND, "ডিভাইসে কোনো ব্যাকআপ পাওয়া যায়নি")

        appSettings.put(KEY_CURRENT_PAGE, backup.currentPage.toString())
        appSettings.put(KEY_KHATAM_COUNT, backup.khatamCount.toString())
        appSettings.put(KEY_DAILY_TARGET, backup.dailyTarget.toString())
        appSettings.put(KEY_COMPLETED_DAYS, backup.completedDatesCount.toString())
        if (backup.lastStoppedPageToday != null) {
            appSettings.put(KEY_LAST_STOPPED_PAGE, backup.lastStoppedPageToday.toString())
        }

        return getProgress(date)
    }

    companion object {
        private const val KEY_CURRENT_PAGE = "quran.current_page"
        private const val KEY_LAST_STOPPED_PAGE = "quran.last_stopped_page"
        private const val KEY_DAILY_TARGET = "quran.daily_target"
        private const val KEY_COMPLETED_DAYS = "quran.completed_days_count"
        private const val KEY_KHATAM_COUNT = "quran.khatam_count"
        private fun keyPagesRead(dateStr: String) = "quran.pages_read.$dateStr"
        private fun keyReadSet(dateStr: String) = "quran.read_pages_set.$dateStr"
        private fun keyLastViewed(dateStr: String) = "quran.last_viewed.$dateStr"
    }
}
