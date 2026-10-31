package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.data.db.entity.PrayerSettingsEntity
import com.rayluxeo.aida.data.db.entity.PrayerTrackingEntryEntity
import com.rayluxeo.aida.domain.salah.CalculationMethod
import com.rayluxeo.aida.domain.salah.HighLatitudeRule
import com.rayluxeo.aida.domain.salah.Madhab
import com.rayluxeo.aida.domain.salah.ObligatoryTracking
import com.rayluxeo.aida.domain.salah.Prayer
import com.rayluxeo.aida.domain.salah.PrayerAdjustments
import com.rayluxeo.aida.domain.salah.PrayerLocation
import com.rayluxeo.aida.domain.salah.PrayerParameters
import com.rayluxeo.aida.domain.salah.PrayerStatus
import com.rayluxeo.aida.domain.salah.SalahDayTracking
import com.rayluxeo.aida.domain.salah.SalahNotificationSettings
import com.rayluxeo.aida.domain.salah.SalahSettings
import com.rayluxeo.aida.domain.salah.TahajjudReminderMode
import com.rayluxeo.aida.domain.salah.TahajjudSettings
import com.rayluxeo.aida.domain.salah.TahajjudStatus
import com.rayluxeo.aida.domain.salah.TahajjudTracking
import com.rayluxeo.aida.domain.salah.TrackingSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** An enum read from disk, falling back to [default] rather than crashing on an unexpected name. */
internal inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default

internal fun PrayerSettingsEntity.toDomain(): SalahSettings {
    val location = if (latitude != null && longitude != null && timezone != null) {
        runCatching { ZoneId.of(timezone) }.getOrNull()?.let { zone ->
            PrayerLocation(latitude, longitude, locationName.orEmpty(), zone)
        }
    } else {
        null
    }
    return SalahSettings(
        location = location,
        parameters = PrayerParameters(
            method = enumOrDefault(calculationMethod, CalculationMethod.KARACHI),
            madhab = enumOrDefault(madhab, Madhab.HANAFI),
            highLatitudeRule = enumOrDefault(highLatitudeRule, HighLatitudeRule.MIDDLE_OF_NIGHT),
            customFajrAngle = customFajrAngle,
            customIshaAngle = customIshaAngle,
            adjustments = PrayerAdjustments(fajrAdjustment, dhuhrAdjustment, asrAdjustment, maghribAdjustment, ishaAdjustment),
        ),
        notifications = SalahNotificationSettings(
            prayerNotificationsEnabled = prayerNotificationsEnabled,
            perPrayerNotify = mapOf(
                Prayer.FAJR to notifyFajr,
                Prayer.DHUHR to notifyDhuhr,
                Prayer.ASR to notifyAsr,
                Prayer.MAGHRIB to notifyMaghrib,
                Prayer.ISHA to notifyIsha,
            ),
            checkInEnabled = checkInEnabled,
            perPrayerCheckIn = mapOf(
                Prayer.FAJR to checkInFajr,
                Prayer.DHUHR to checkInDhuhr,
                Prayer.ASR to checkInAsr,
                Prayer.MAGHRIB to checkInMaghrib,
                Prayer.ISHA to checkInIsha,
            ),
            defaultAskDelayMinutes = defaultAskDelayMinutes,
        ),
        tahajjud = TahajjudSettings(
            reminderEnabled = tahajjudReminderEnabled,
            mode = enumOrDefault(tahajjudReminderMode, TahajjudReminderMode.LAST_THIRD),
            customMinutesIntoLastThird = tahajjudCustomMinutes,
        ),
    )
}

internal fun SalahSettings.toEntity(nowMillis: Long, createdAtMillis: Long): PrayerSettingsEntity {
    val notify = notifications.perPrayerNotify
    val checkIn = notifications.perPrayerCheckIn
    return PrayerSettingsEntity(
        id = PrayerSettingsEntity.SINGLETON_ID,
        latitude = location?.latitude,
        longitude = location?.longitude,
        locationName = location?.cityLabel,
        timezone = location?.zoneId?.id,
        calculationMethod = parameters.method.name,
        madhab = parameters.madhab.name,
        highLatitudeRule = parameters.highLatitudeRule.name,
        customFajrAngle = parameters.customFajrAngle,
        customIshaAngle = parameters.customIshaAngle,
        fajrAdjustment = parameters.adjustments.fajr,
        dhuhrAdjustment = parameters.adjustments.dhuhr,
        asrAdjustment = parameters.adjustments.asr,
        maghribAdjustment = parameters.adjustments.maghrib,
        ishaAdjustment = parameters.adjustments.isha,
        prayerNotificationsEnabled = notifications.prayerNotificationsEnabled,
        notifyFajr = notify[Prayer.FAJR] ?: true,
        notifyDhuhr = notify[Prayer.DHUHR] ?: true,
        notifyAsr = notify[Prayer.ASR] ?: true,
        notifyMaghrib = notify[Prayer.MAGHRIB] ?: true,
        notifyIsha = notify[Prayer.ISHA] ?: true,
        checkInEnabled = notifications.checkInEnabled,
        checkInFajr = checkIn[Prayer.FAJR] ?: true,
        checkInDhuhr = checkIn[Prayer.DHUHR] ?: true,
        checkInAsr = checkIn[Prayer.ASR] ?: true,
        checkInMaghrib = checkIn[Prayer.MAGHRIB] ?: true,
        checkInIsha = checkIn[Prayer.ISHA] ?: true,
        defaultAskDelayMinutes = notifications.defaultAskDelayMinutes,
        tahajjudReminderEnabled = tahajjud.reminderEnabled,
        tahajjudReminderMode = tahajjud.mode.name,
        tahajjudCustomMinutes = tahajjud.customMinutesIntoLastThird,
        createdAtEpochMillis = createdAtMillis,
        updatedAtEpochMillis = nowMillis,
    )
}

private fun PrayerTrackingEntryEntity.toObligatory(): ObligatoryTracking? {
    val prayerValue = Prayer.entries.firstOrNull { it.name == prayer } ?: return null
    if (prayerValue == Prayer.TAHAJJUD) return null
    return ObligatoryTracking(
        localDate = LocalDate.ofEpochDay(localDateEpochDay),
        prayer = prayerValue,
        status = enumOrDefault(status, PrayerStatus.NOT_RECORDED),
        scheduledAt = scheduledAtEpochMillis?.let(Instant::ofEpochMilli),
        prayedAt = prayedAtEpochMillis?.let(Instant::ofEpochMilli),
        source = enumOrDefault(trackingSource, TrackingSource.MANUAL),
        notes = notes,
    )
}

private fun PrayerTrackingEntryEntity.toTahajjud(): TahajjudTracking? {
    if (prayer != Prayer.TAHAJJUD.name) return null
    return TahajjudTracking(
        localDate = LocalDate.ofEpochDay(localDateEpochDay),
        status = enumOrDefault(status, TahajjudStatus.NOT_RECORDED),
        prayedAt = prayedAtEpochMillis?.let(Instant::ofEpochMilli),
        source = enumOrDefault(trackingSource, TrackingSource.MANUAL),
        notes = notes,
    )
}

/** Group a flat list of rows into one [SalahDayTracking] per day, obligatory and Tahajjud apart. */
internal fun List<PrayerTrackingEntryEntity>.toDayTracking(): List<SalahDayTracking> =
    groupBy { it.localDateEpochDay }
        .map { (day, rows) ->
            SalahDayTracking(
                date = LocalDate.ofEpochDay(day),
                obligatory = rows.mapNotNull { it.toObligatory() },
                tahajjud = rows.firstNotNullOfOrNull { it.toTahajjud() },
            )
        }
        .sortedBy { it.date }

internal fun List<PrayerTrackingEntryEntity>.toTahajjudList(): List<TahajjudTracking> =
    mapNotNull { it.toTahajjud() }
