package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A conversation with someone else, and what Abu needs to have ready for it. */
@Entity(
    tableName = "meetings",
    foreignKeys = [
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["clientId"]),
        Index(value = ["projectId"]),
        Index(value = ["startsAtEpochMillis"]),
        Index(value = ["deletedAtEpochMillis"]),
    ],
)
data class MeetingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val clientId: Long?,
    val projectId: Long?,
    val startsAtEpochMillis: Long,
    val endsAtEpochMillis: Long?,
    val location: String?,
    val deletedAtEpochMillis: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
