package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.data.db.dao.UserProfileDao
import com.rayluxeo.aida.data.db.entity.UserProfileEntity
import com.rayluxeo.aida.domain.model.UserProfile
import com.rayluxeo.aida.domain.repository.UserProfileRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Abu's profile, over the single-row table.
 *
 * Saving stamps `updatedAt` here and leaves `createdAt` to the DAO, which keeps the original: when
 * he first set his details is a fact about the profile, and changing his phone number is not a new
 * profile.
 */
@Singleton
class UserProfileRepositoryImpl @Inject constructor(
    private val dao: UserProfileDao,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : UserProfileRepository {

    override fun observe(): Flow<UserProfile?> =
        dao.observe(UserProfileEntity.SINGLETON_ID)
            .map { row -> row?.toDomain() }
            .flowOn(dispatchers.io)
            .orOnFailure(null)

    override suspend fun current(): AppResult<UserProfile?> =
        storageCall(dispatchers, "load profile") {
            dao.find(UserProfileEntity.SINGLETON_ID)?.toDomain()
        }

    /**
     * The saved row is read back rather than echoed, so what the caller gets is what the database
     * holds — including the `createdAt` the DAO preserved, which the caller's copy may predate.
     */
    override suspend fun save(profile: UserProfile): AppResult<UserProfile> {
        val stamped = profile.copy(updatedAt = clock.now())
        val saved = storageCall(dispatchers, "save profile") {
            dao.save(stamped.toEntity())
            dao.find(UserProfileEntity.SINGLETON_ID)
        }
        return when (saved) {
            is AppResult.Failure -> saved
            is AppResult.Success -> saved.value?.toDomain()?.let { AppResult.Success(it) }
                ?: failure(MiraErrorCode.STORAGE, "Profile was saved but could not be read back")
        }
    }
}
