package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.data.db.dao.PrayerTrackingDao
import com.rayluxeo.aida.data.db.entity.PrayerTrackingEntryEntity
import com.rayluxeo.aida.domain.salah.Prayer
import com.rayluxeo.aida.domain.salah.PrayerStatus
import com.rayluxeo.aida.domain.salah.SalahDayTracking
import com.rayluxeo.aida.domain.salah.SalahTrackingRepository
import com.rayluxeo.aida.domain.salah.TahajjudStatus
import com.rayluxeo.aida.domain.salah.TahajjudTracking
import com.rayluxeo.aida.domain.salah.TrackingSource
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class SalahTrackingRepositoryImpl @Inject constructor(
    private val dao: PrayerTrackingDao,
    private val clock: MiraClock,
    private val dispatchers: DispatcherProvider,
) : SalahTrackingRepository {

    override fun observeDay(date: LocalDate): Flow<SalahDayTracking> =
        dao.observeDay(date.toEpochDay())
            .map { rows -> rows.toDayTracking().firstOrNull() ?: SalahDayTracking(date, emptyList(), null) }
            .orOnFailure(SalahDayTracking(date, emptyList(), null))

    override fun observeMonth(month: YearMonth): Flow<List<SalahDayTracking>> =
        dao.observeBetween(month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay())
            .map { it.toDayTracking() }
            .orOnFailure(emptyList())

    override suspend fun daysBetween(from: LocalDate, to: LocalDate): AppResult<List<SalahDayTracking>> =
        storageCall(dispatchers, "salah days between") {
            dao.listBetween(from.toEpochDay(), to.toEpochDay()).toDayTracking()
        }

    override suspend fun tahajjudBetween(from: LocalDate, to: LocalDate): AppResult<List<TahajjudTracking>> =
        storageCall(dispatchers, "tahajjud between") {
            dao.listBetween(from.toEpochDay(), to.toEpochDay()).toTahajjudList()
        }

    override suspend fun setStatus(
        date: LocalDate,
        prayer: Prayer,
        status: PrayerStatus,
        scheduledAt: Instant?,
        prayedAt: Instant?,
        source: TrackingSource,
        notes: String?,
    ): AppResult<Unit> = storageCall(dispatchers, "set prayer status") {
        val now = clock.now().toEpochMilli()
        dao.upsert(
            PrayerTrackingEntryEntity(
                localDateEpochDay = date.toEpochDay(),
                prayer = prayer.name,
                scheduledAtEpochMillis = scheduledAt?.toEpochMilli(),
                status = status.name,
                prayedAtEpochMillis = prayedAt?.toEpochMilli(),
                trackingSource = source.name,
                notes = notes,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
    }

    override suspend fun setTahajjud(
        date: LocalDate,
        status: TahajjudStatus,
        prayedAt: Instant?,
        source: TrackingSource,
        notes: String?,
    ): AppResult<Unit> = storageCall(dispatchers, "set tahajjud status") {
        val now = clock.now().toEpochMilli()
        dao.upsert(
            PrayerTrackingEntryEntity(
                localDateEpochDay = date.toEpochDay(),
                prayer = Prayer.TAHAJJUD.name,
                scheduledAtEpochMillis = null,
                status = status.name,
                prayedAtEpochMillis = prayedAt?.toEpochMilli(),
                trackingSource = source.name,
                notes = notes,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
    }

    override suspend fun clearAll(): AppResult<Unit> =
        storageCall(dispatchers, "reset salah tracking") { dao.clearAll() }
}
