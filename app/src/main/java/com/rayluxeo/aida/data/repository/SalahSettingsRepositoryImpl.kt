package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.data.db.dao.PrayerSettingsDao
import com.rayluxeo.aida.domain.salah.SalahSettings
import com.rayluxeo.aida.domain.salah.SalahSettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class SalahSettingsRepositoryImpl @Inject constructor(
    private val dao: PrayerSettingsDao,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : SalahSettingsRepository {

    override val settings: Flow<SalahSettings> =
        dao.observe().map { it?.toDomain() ?: SalahSettings() }.orOnFailure(SalahSettings())

    override suspend fun current(): SalahSettings =
        withContext(dispatchers.io) { dao.find()?.toDomain() ?: SalahSettings() }

    override suspend fun save(settings: SalahSettings): AppResult<SalahSettings> =
        storageCall(dispatchers, "save salah settings") {
            val now = clock.now().toEpochMilli()
            val created = dao.find()?.createdAtEpochMillis ?: now
            dao.upsert(settings.toEntity(nowMillis = now, createdAtMillis = created))
            settings
        }
}
