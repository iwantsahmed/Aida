package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.rayluxeo.aida.domain.model.SubscriptionCycle

/**
 * A recurring charge.
 *
 * [reminderId] is `SET NULL` so cancelling the reminder does not quietly delete the subscription
 * Abu is still paying for. [cycleDays] is read only when [cycle] is CUSTOM_DAYS.
 */
@Entity(
    tableName = "subscriptions",
    foreignKeys = [
        ForeignKey(
            entity = ReminderEntity::class,
            parentColumns = ["id"],
            childColumns = ["reminderId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["reminderId"]),
        Index(value = ["nextChargeOnEpochDay"]),
        Index(value = ["isActive"]),
        Index(value = ["deletedAtEpochMillis"]),
    ],
)
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val vendor: String?,
    val amountMinor: Long,
    val currencyCode: String,
    val cycle: SubscriptionCycle,
    val cycleDays: Int?,
    val nextChargeOnEpochDay: Long,
    val isActive: Boolean,
    val reminderId: Long?,
    val deletedAtEpochMillis: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
