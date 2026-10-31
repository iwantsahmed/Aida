package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A step inside a task.
 *
 * A separate table rather than a self-reference on `tasks`, because a subtask is a checklist
 * line — it has no due date, no priority and no reminder of its own — and modelling it as a task
 * would mean every task query had to remember to exclude it.
 */
@Entity(
    tableName = "subtasks",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["taskId"])],
)
data class SubtaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val taskId: Long,
    val title: String,
    val isDone: Boolean,
    val completedAtEpochMillis: Long?,
    val position: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
