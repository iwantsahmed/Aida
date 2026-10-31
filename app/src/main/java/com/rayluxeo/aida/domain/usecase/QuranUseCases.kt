package com.rayluxeo.aida.domain.usecase

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.domain.quran.QuranProgress
import com.rayluxeo.aida.domain.quran.QuranRepository
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Observes and retrieves Quran recitation progress.
 */
class GetQuranProgressUseCase @Inject constructor(
    private val repository: QuranRepository,
    private val clock: MiraClock,
) {
    fun observe(date: LocalDate = clock.today()): Flow<QuranProgress> =
        repository.observeProgress(date)

    suspend operator fun invoke(date: LocalDate = clock.today()): AppResult<QuranProgress> =
        repository.getProgress(date)
}

/**
 * Marks a Quran page as recited for today, advancing progress towards the daily target (default 2 pages).
 */
class MarkQuranPageReadUseCase @Inject constructor(
    private val repository: QuranRepository,
    private val clock: MiraClock,
) {
    suspend operator fun invoke(pageNumber: Int, date: LocalDate = clock.today()): AppResult<QuranProgress> =
        repository.markPageRead(pageNumber, date)
}

/**
 * Updates the user's bookmarked Quran page.
 */
class SetQuranCurrentPageUseCase @Inject constructor(
    private val repository: QuranRepository,
) {
    suspend operator fun invoke(pageNumber: Int): AppResult<Unit> =
        repository.setCurrentPage(pageNumber)
}

/**
 * Configures the daily recitation target (default: 2 pages/day).
 */
class SetQuranDailyTargetUseCase @Inject constructor(
    private val repository: QuranRepository,
) {
    suspend operator fun invoke(target: Int): AppResult<Unit> =
        repository.setDailyTarget(target)
}

/**
 * Saves the page currently being viewed or read today so reading can resume seamlessly.
 */
class SaveQuranLastViewedPageUseCase @Inject constructor(
    private val repository: QuranRepository,
    private val clock: MiraClock,
) {
    suspend operator fun invoke(pageNumber: Int, date: LocalDate = clock.today()): AppResult<Unit> =
        repository.saveLastViewedPage(pageNumber, date)
}

/**
 * Saves a durable backup of Quran progress to device storage.
 */
class BackupQuranProgressUseCase @Inject constructor(
    private val repository: QuranRepository,
) {
    suspend operator fun invoke(): AppResult<String> = repository.backupToDevice()
}

/**
 * Restores Quran progress from on-device backup file.
 */
class RestoreQuranProgressUseCase @Inject constructor(
    private val repository: QuranRepository,
    private val clock: MiraClock,
) {
    suspend operator fun invoke(date: LocalDate = clock.today()): AppResult<QuranProgress> =
        repository.restoreFromDevice(date)
}
