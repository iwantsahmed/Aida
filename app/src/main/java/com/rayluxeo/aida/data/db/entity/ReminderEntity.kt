package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.rayluxeo.aida.domain.model.RecordOrigin
import com.rayluxeo.aida.domain.model.ReminderKind
import com.rayluxeo.aida.domain.model.PlaceTrigger
import com.rayluxeo.aida.domain.model.ReminderState

/**
 * What Abu asked to be reminded of. The OS-side fact of an alarm being armed is
 * [AlarmRecordEntity]; this row is the intent behind it.
 *
 * [linkedTaskId] is `SET NULL`. A reminder is Abu's instruction and stands on its own — losing the
 * task it grew out of must leave the reminder to fire, not delete it. The constraint exists at all
 * because `TaskDao` still offers a hard delete for purging the bin, and without it a purge would
 * leave the reminder pointing at a row that is no longer there.
 */
@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["linkedTaskId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        // Every one of these is SET NULL for the same reason as the task: a reminder is Abu's
        // instruction, and losing the record it grew out of must leave it firing rather than
        // delete it. A CASCADE here would silently throw away something he asked to be told.
        ForeignKey(
            entity = InvoiceEntity::class,
            parentColumns = ["id"],
            childColumns = ["linkedInvoiceId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["linkedClientId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["linkedProjectId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = SubscriptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["linkedSubscriptionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["triggerAtEpochMillis"]),
        Index(value = ["linkedTaskId"]),
        Index(value = ["linkedInvoiceId"]),
        Index(value = ["linkedClientId"]),
        Index(value = ["linkedProjectId"]),
        Index(value = ["linkedSubscriptionId"]),
        Index(value = ["kind"]),
        Index(value = ["state"]),
    ],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val triggerAtEpochMillis: Long,
    val state: ReminderState,
    /**
     * What sort of reminder this is. It decides the wording, the notification actions and which
     * record a tap opens — a birthday and an overdue invoice want different sentences and
     * different buttons, and deriving that from which link happens to be non-null would break the
     * moment a reminder had two.
     */
    val kind: ReminderKind = ReminderKind.ONE_TIME,
    /** Serialised by `RecurrenceCodec`, the same format tasks use. Null for a one-off. */
    val recurrenceRule: String? = null,
    /**
     * Set while Abu has pushed this one away. The trigger is left untouched so that cancelling a
     * snooze puts the reminder back where it was rather than at a time Mira invented.
     */
    val snoozedUntilEpochMillis: Long? = null,
    val linkedTaskId: Long?,
    val linkedInvoiceId: Long? = null,
    val linkedClientId: Long? = null,
    val linkedProjectId: Long? = null,
    val linkedSubscriptionId: Long? = null,
    val origin: RecordOrigin,
    val createdAtEpochMillis: Long,
)

/**
 * Where a reminder waits for Abu to arrive, when he asked for one by place rather than by time.
 *
 * Its own table rather than five more columns on `reminders`, because the number of registered
 * places is a question Mira has to answer: the platform caps how many proximity alerts an app may
 * hold, and a count is the only honest way to tell Abu she has run out.
 *
 * CASCADE: a place is meaningless without the reminder it belongs to.
 */
@Entity(
    tableName = "reminder_places",
    foreignKeys = [
        ForeignKey(
            entity = ReminderEntity::class,
            parentColumns = ["id"],
            childColumns = ["reminderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["reminderId"], unique = true),
        Index(value = ["isRegistered"]),
    ],
)
data class ReminderPlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val reminderId: Long,
    /** What Abu called it — "the office" — not a geocoded address Mira went and looked up. */
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val trigger: PlaceTrigger,
    /**
     * Whether the OS is currently holding this alert.
     *
     * False after a reboot, after the permission is withdrawn, and when the platform's limit is
     * already spent — all of which are ordinary, and all of which Abu is told about rather than
     * left to discover when nothing happens.
     */
    val isRegistered: Boolean = false,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

/**
 * An alarm clock, which is not the same thing as [AlarmRecordEntity].
 *
 * `alarm_records` is the ledger of what Mira asked the OS to do. This is what Abu set: a time of
 * day, the days it repeats on, a label, and whether it is switched on.
 */
@Entity(
    tableName = "alarms",
    indices = [Index(value = ["isEnabled"]), Index(value = ["nextTriggerAtEpochMillis"])],
)
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val label: String,
    /** Minutes from local midnight. Stored as a time of day, not an instant, because a repeating
     *  alarm means 07:00 wherever Abu is, not an instant fixed in one zone. */
    val minuteOfDay: Int,
    /**
     * Which days it repeats on, as `DayOfWeek` names joined by commas, empty for a one-time alarm.
     * Written in week order so two equal alarms always produce identical text.
     */
    val repeatDays: String = "",
    val isEnabled: Boolean = true,
    /** The next moment this alarm is armed for, or null while it is switched off. */
    val nextTriggerAtEpochMillis: Long? = null,
    val snoozedUntilEpochMillis: Long? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
