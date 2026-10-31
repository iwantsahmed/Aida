package com.rayluxeo.aida.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.domain.brief.BriefSchedule
import com.rayluxeo.aida.domain.brief.BriefScheduleRepository
import java.io.IOException
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * When Mira is allowed to speak up unasked, kept in the same preferences file as Abu's other
 * choices.
 *
 * A stored time is minutes since midnight — see [MiraPreferenceKeys] for why that rather than
 * ISO-8601 — and it is folded back into the day on the way out, so a file written by a future
 * build, or hand-edited on a rooted phone, still reads as a time the scheduler can arm.
 *
 * [schedule] is de-duplicated because it arms alarms: an emission caused by an unrelated key
 * must not cost a cancel-and-rearm of both briefs.
 */
@Singleton
class BriefScheduleRepositoryImpl @Inject constructor(
    private val store: DataStore<Preferences>,
    private val dispatchers: DispatcherProvider,
) : BriefScheduleRepository {

    override val schedule: Flow<BriefSchedule> = store.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map(::toSchedule)
        .distinctUntilChanged()

    override suspend fun current(): BriefSchedule = withContext(dispatchers.io) { schedule.first() }

    override suspend fun setMorningEnabled(enabled: Boolean) = write {
        it[MiraPreferenceKeys.MORNING_BRIEF_ENABLED] = enabled
    }

    override suspend fun setMorningAt(time: LocalTime) = write {
        it[MiraPreferenceKeys.MORNING_BRIEF_MINUTES] = time.toMinuteOfDay()
    }

    override suspend fun setEveningEnabled(enabled: Boolean) = write {
        it[MiraPreferenceKeys.EVENING_BRIEF_ENABLED] = enabled
    }

    override suspend fun setEveningAt(time: LocalTime) = write {
        it[MiraPreferenceKeys.EVENING_BRIEF_MINUTES] = time.toMinuteOfDay()
    }

    private suspend fun write(block: (MutablePreferences) -> Unit) {
        withContext(dispatchers.io) { store.edit { stored -> block(stored) } }
    }

    private fun toSchedule(stored: Preferences): BriefSchedule {
        val defaults = BriefSchedule.DEFAULT
        return BriefSchedule(
            morningEnabled = stored[MiraPreferenceKeys.MORNING_BRIEF_ENABLED] ?: defaults.morningEnabled,
            morningAt = stored[MiraPreferenceKeys.MORNING_BRIEF_MINUTES]?.toLocalTime()
                ?: defaults.morningAt,
            eveningEnabled = stored[MiraPreferenceKeys.EVENING_BRIEF_ENABLED] ?: defaults.eveningEnabled,
            eveningAt = stored[MiraPreferenceKeys.EVENING_BRIEF_MINUTES]?.toLocalTime()
                ?: defaults.eveningAt,
        )
    }

    /** Seconds are dropped deliberately: a brief is an appointment to the minute, not to the second. */
    private fun LocalTime.toMinuteOfDay(): Int = hour * MINUTES_PER_HOUR + minute

    /** Wrapped rather than clamped, so an out-of-range value still names a real time of day. */
    private fun Int.toLocalTime(): LocalTime =
        LocalTime.ofSecondOfDay(Math.floorMod(this, MINUTES_PER_DAY).toLong() * SECONDS_PER_MINUTE)

    private companion object {
        const val MINUTES_PER_HOUR = 60
        const val MINUTES_PER_DAY = 24 * 60
        const val SECONDS_PER_MINUTE = 60L
    }
}
