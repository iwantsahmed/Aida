package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import com.rayluxeo.aida.domain.model.ProjectStatus

/**
 * A body of work, usually for a client.
 *
 * The client link is `SET NULL` rather than `CASCADE`: deleting a client must not silently take
 * their project history — and the invoices hanging off it — with them. Dates that carry no time
 * of day are stored as epoch days, so "due on the 3rd" cannot drift across a timezone change.
 */
@Entity(
    tableName = "projects",
    foreignKeys = [
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["clientId"]),
        Index(value = ["status"]),
        Index(value = ["dueOnEpochDay"]),
        Index(value = ["deletedAtEpochMillis"]),
    ],
)
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val uuid: String,
    val clientId: Long?,
    val name: String,
    val description: String?,
    val status: ProjectStatus,
    val startedOnEpochDay: Long?,
    val dueOnEpochDay: Long?,
    val budgetMinor: Long?,
    val currencyCode: String,
    val deletedAtEpochMillis: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "project_fts")
@Fts4(contentEntity = ProjectEntity::class)
data class ProjectFtsEntity(
    val name: String,
    val description: String?,
)
