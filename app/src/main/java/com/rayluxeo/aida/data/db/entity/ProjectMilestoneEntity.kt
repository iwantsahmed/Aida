package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A checkpoint inside a project.
 *
 * `CASCADE` is safe here in a way it is not for invoices: a milestone has no meaning apart from
 * its project, nothing else references it, and nothing financial depends on it.
 */
@Entity(
    tableName = "project_milestones",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["projectId"]),
        Index(value = ["dueOnEpochDay"]),
    ],
)
data class ProjectMilestoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val projectId: Long,
    val title: String,
    val dueOnEpochDay: Long?,
    val isComplete: Boolean,
    val completedAtEpochMillis: Long?,
    val position: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
