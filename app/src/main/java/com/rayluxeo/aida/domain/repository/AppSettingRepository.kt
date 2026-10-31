package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * The settings that belong with the records rather than with the app shell.
 *
 * **Where the boundary with DataStore runs.** DataStore holds what Mira needs *before* the
 * database is open — Abu's language, his voice, whether onboarding is done — because those decide
 * how the first screen is drawn and cannot wait on a database that may still be migrating.
 * This repository holds the settings that only mean anything beside the records: the next invoice
 * number, a default tax rate, the last reconciled date. They live in the same database as the
 * rows that consume them so they can be written in the same transaction, which is what
 * [takeNextCounterValue] depends on.
 *
 * Values are stored as text. Anything that is not text is the caller's to encode, because a
 * setting's type is known where it is used and nowhere else.
 */
interface AppSettingRepository {

    fun observe(key: String): Flow<String?>

    suspend fun get(key: String): AppResult<String?>

    suspend fun put(key: String, value: String): AppResult<Unit>

    suspend fun remove(key: String): AppResult<Unit>

    /**
     * Returns the next value of a counter and stores it in the same transaction, so a number is
     * never handed out twice. The first call returns [startAt].
     */
    suspend fun takeNextCounterValue(key: String, startAt: Long = 1L): AppResult<Long>
}
