package com.rayluxeo.aida.domain.salah

import java.time.ZoneId

/**
 * Where Abu prays from, saved so prayer times work offline with no continuous GPS. The timezone is
 * captured when the place is saved (the device's zone while standing there) rather than read live,
 * so a saved location keeps giving correct times even far from home. [cityLabel] is typed by Abu —
 * there is no reverse-geocoder (privacy, and zero-cost).
 */
data class PrayerLocation(
    val latitude: Double,
    val longitude: Double,
    val cityLabel: String,
    val zoneId: ZoneId,
) {
    val coordinates: GeoCoordinates get() = GeoCoordinates(latitude, longitude)
}

/** The per-prayer and global switches for prayer notifications and after-prayer check-ins. */
data class SalahNotificationSettings(
    val prayerNotificationsEnabled: Boolean = true,
    val perPrayerNotify: Map<Prayer, Boolean> = Prayer.OBLIGATORY.associateWith { true },
    val checkInEnabled: Boolean = true,
    val perPrayerCheckIn: Map<Prayer, Boolean> = Prayer.OBLIGATORY.associateWith { true },
    val defaultAskDelayMinutes: Int = 20,
) {
    fun notifyFor(prayer: Prayer): Boolean = prayerNotificationsEnabled && (perPrayerNotify[prayer] ?: true)
    fun checkInFor(prayer: Prayer): Boolean = checkInEnabled && (perPrayerCheckIn[prayer] ?: true)
}

/** When Mira offers a Tahajjud reminder; always a window, never presented as a requirement. */
data class TahajjudSettings(
    val reminderEnabled: Boolean = false,
    val mode: TahajjudReminderMode = TahajjudReminderMode.LAST_THIRD,
    /** Minutes into the last third for [TahajjudReminderMode.CUSTOM]; ignored otherwise. */
    val customMinutesIntoLastThird: Int = 0,
)

/**
 * Everything Abu has set for Salah, read as one value. [location] is null until he sets one, which
 * is the signal the UI uses to offer first-time setup. The calculation [parameters] carry the
 * method, madhab, high-latitude rule, any custom angles and the per-prayer minute corrections.
 */
data class SalahSettings(
    val location: PrayerLocation? = null,
    val parameters: PrayerParameters = PrayerParameters(
        method = CalculationMethod.KARACHI,
        madhab = Madhab.HANAFI,
    ),
    val notifications: SalahNotificationSettings = SalahNotificationSettings(),
    val tahajjud: TahajjudSettings = TahajjudSettings(),
) {
    /** True once Abu has a place to compute from — the gate for showing real times instead of setup. */
    val isConfigured: Boolean get() = location != null
}
