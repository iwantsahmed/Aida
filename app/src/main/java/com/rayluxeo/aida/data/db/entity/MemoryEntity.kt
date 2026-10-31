package com.rayluxeo.aida.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import com.rayluxeo.aida.domain.model.MemoryCategory
import com.rayluxeo.aida.domain.model.MemorySource

/**
 * Something Mira has been told to remember, and nothing she was not.
 *
 * Split into a [subject] and a [fact] rather than one blob so recall can match on what the fact
 * is *about* before matching its wording. [recallCount] and [lastRecalledAtEpochMillis] exist so
 * Abu can see what she actually leans on, and prune what she does not.
 */
@Entity(
    tableName = "memory",
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
        Index(value = ["subject"]),
        Index(value = ["category"]),
        Index(value = ["isPinned"]),
        Index(value = ["clientId"]),
        Index(value = ["projectId"]),
        Index(value = ["deletedAtEpochMillis"]),
    ],
)
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val subject: String,
    val fact: String,
    val category: MemoryCategory = MemoryCategory.GENERAL_FACT,
    val isPinned: Boolean = false,
    val source: MemorySource,
    val languageTag: String,
    val clientId: Long?,
    val projectId: Long?,
    val recallCount: Int,
    val lastRecalledAtEpochMillis: Long?,
    val deletedAtEpochMillis: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "memory_fts")
@Fts4(contentEntity = MemoryEntity::class)
data class MemoryFtsEntity(
    val subject: String,
    val fact: String,
)
