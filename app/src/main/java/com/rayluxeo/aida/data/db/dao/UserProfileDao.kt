package com.rayluxeo.aida.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.rayluxeo.aida.data.db.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

/**
 * The single profile row.
 *
 * There is no insert and no update, only [save]: the caller never knows whether Abu has filled
 * the profile in before, and an insert-or-update decided outside a transaction is a race that
 * ends in a constraint violation the first time two screens save at once.
 *
 * Saving preserves the original `createdAtEpochMillis`, because when Abu first set his details is
 * a fact about the profile and editing his phone number does not change it.
 */
@Dao
abstract class UserProfileDao {

    @Query("SELECT * FROM user_profile WHERE id = :id")
    abstract fun observe(id: Long): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = :id")
    abstract suspend fun find(id: Long): UserProfileEntity?

    @Upsert
    abstract suspend fun upsert(profile: UserProfileEntity)

    @Transaction
    open suspend fun save(profile: UserProfileEntity) {
        val existing = find(UserProfileEntity.SINGLETON_ID)
        upsert(
            profile.copy(
                id = UserProfileEntity.SINGLETON_ID,
                createdAtEpochMillis = existing?.createdAtEpochMillis ?: profile.createdAtEpochMillis,
            ),
        )
    }
}
