package com.rayluxeo.aida.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.rayluxeo.aida.data.db.entity.AppSettingEntity
import kotlinx.coroutines.flow.Flow

/**
 * The key/value settings that live beside the records rather than in DataStore.
 *
 * [takeNextCounterValue] is why this table is in the database at all: allocating the next invoice
 * number has to be a read and a write that nothing can interleave with, and it has to be able to
 * join the same transaction as the invoice that consumes it. A counter kept in DataStore could
 * hand the same number out twice.
 */
@Dao
abstract class AppSettingDao {

    @Query("SELECT * FROM app_settings WHERE `key` = :key")
    abstract fun observe(key: String): Flow<AppSettingEntity?>

    @Query("SELECT * FROM app_settings WHERE `key` = :key")
    abstract suspend fun find(key: String): AppSettingEntity?

    @Upsert
    abstract suspend fun upsert(setting: AppSettingEntity)

    @Query("DELETE FROM app_settings WHERE `key` = :key")
    abstract suspend fun delete(key: String): Int

    /**
     * Hands out the next value of a counter and stores it in the same transaction, so no two
     * callers can ever be given the same one. [startAt] is what the first call returns.
     *
     * A stored value that is not a number throws rather than restarting the count: quietly
     * resetting an invoice counter would reissue a number Abu has already sent out.
     */
    @Transaction
    open suspend fun takeNextCounterValue(key: String, startAt: Long, atEpochMillis: Long): Long {
        val stored = find(key)
        val next = if (stored == null) {
            startAt
        } else {
            val current = stored.value.toLongOrNull()
                ?: error("Setting $key holds \"${stored.value}\", which is not a counter")
            current + 1
        }
        upsert(AppSettingEntity(key = key, value = next.toString(), updatedAtEpochMillis = atEpochMillis))
        return next
    }
}
