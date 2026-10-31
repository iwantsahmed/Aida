package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.withContext

/**
 * Room throws; the domain layer expects values. Every one-shot repository call funnels through
 * here so a SQLiteException can never surface at a use case as an exception, and so the io
 * dispatcher is chosen in exactly one place.
 */
internal suspend fun <T> storageCall(
    dispatchers: DispatcherProvider,
    detail: String,
    block: suspend () -> T,
): AppResult<T> = withContext(dispatchers.io) {
    try {
        AppResult.Success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        failure(MiraErrorCode.STORAGE, detail, error)
    }
}

/**
 * Observation has no failure channel in the repository contracts, so a read error degrades to
 * [fallback] rather than cancelling the collector and taking a screen down with it. Writes still
 * report STORAGE properly, which is where a real problem shows up anyway.
 */
internal fun <T> Flow<T>.orOnFailure(fallback: T): Flow<T> = catch { emit(fallback) }
