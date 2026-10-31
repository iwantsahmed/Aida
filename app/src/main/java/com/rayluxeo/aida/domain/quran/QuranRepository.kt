package com.rayluxeo.aida.domain.quran

import com.rayluxeo.aida.core.common.AppResult
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Port for persisting Quran recitation progress and bookmarks.
 */
interface QuranRepository {
    fun observeProgress(date: LocalDate): Flow<QuranProgress>
    suspend fun getProgress(date: LocalDate): AppResult<QuranProgress>
    suspend fun markPageRead(pageNumber: Int, date: LocalDate): AppResult<QuranProgress>
    suspend fun setCurrentPage(pageNumber: Int): AppResult<Unit>
    suspend fun setDailyTarget(target: Int): AppResult<Unit>
    suspend fun saveLastViewedPage(pageNumber: Int, date: LocalDate): AppResult<Unit>
    suspend fun backupToDevice(): AppResult<String>
    suspend fun restoreFromDevice(date: LocalDate): AppResult<QuranProgress>
}
