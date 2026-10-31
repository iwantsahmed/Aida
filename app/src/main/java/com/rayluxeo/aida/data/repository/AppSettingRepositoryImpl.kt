package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.data.db.dao.AppSettingDao
import com.rayluxeo.aida.data.db.entity.AppSettingEntity
import com.rayluxeo.aida.domain.repository.AppSettingRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * The settings that live in the database rather than in DataStore.
 *
 * A missing key reads as null rather than as a failure: a setting that has never been written is
 * a normal state, and the default belongs at the call site where the type is known.
 */
@Singleton
class AppSettingRepositoryImpl @Inject constructor(
    private val dao: AppSettingDao,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : AppSettingRepository {

    override fun observe(key: String): Flow<String?> =
        dao.observe(key)
            .map { it?.value }
            .flowOn(dispatchers.io)
            .orOnFailure(null)

    override suspend fun get(key: String): AppResult<String?> =
        storageCall(dispatchers, "read setting $key") { dao.find(key)?.value }

    override suspend fun put(key: String, value: String): AppResult<Unit> =
        storageCall(dispatchers, "write setting $key") {
            dao.upsert(
                AppSettingEntity(
                    key = key,
                    value = value,
                    updatedAtEpochMillis = clock.now().toEpochMilli(),
                ),
            )
        }

    override suspend fun remove(key: String): AppResult<Unit> =
        storageCall(dispatchers, "remove setting $key") {
            dao.delete(key)
            Unit
        }

    override suspend fun takeNextCounterValue(key: String, startAt: Long): AppResult<Long> =
        storageCall(dispatchers, "allocate next value of $key") {
            dao.takeNextCounterValue(key, startAt, clock.now().toEpochMilli())
        }
}
