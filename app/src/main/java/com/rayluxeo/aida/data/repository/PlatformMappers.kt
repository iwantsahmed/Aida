package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.data.db.entity.ActivityLogEntity
import com.rayluxeo.aida.data.db.entity.FileRecordEntity
import com.rayluxeo.aida.data.db.entity.ScanRecordEntity
import com.rayluxeo.aida.data.db.entity.UserProfileEntity
import com.rayluxeo.aida.domain.model.ActivityEntry
import com.rayluxeo.aida.domain.model.ActivitySubject
import com.rayluxeo.aida.domain.model.FileRecord
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.ScanRecord
import com.rayluxeo.aida.domain.model.UserProfile
import java.time.Instant
import java.util.Currency

/**
 * Files, scans, the profile, and the activity log across the storage boundary.
 *
 * Epoch millis are a storage detail and [Instant] is the domain currency; the conversion lives
 * here rather than in a Room TypeConverter so the indexed time columns stay primitive integers
 * that SQLite can compare and sort.
 */
internal fun FileRecordEntity.toDomain(): FileRecord = FileRecord(
    id = id,
    uuid = uuid,
    displayName = displayName,
    relativePath = relativePath,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    source = source,
    extractedText = extractedText,
    clientId = clientId,
    projectId = projectId,
    deletedAt = deletedAtEpochMillis?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)

internal fun FileRecord.toEntity(): FileRecordEntity = FileRecordEntity(
    id = id,
    uuid = uuid,
    displayName = displayName,
    relativePath = relativePath,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    source = source,
    extractedText = extractedText,
    clientId = clientId,
    projectId = projectId,
    deletedAtEpochMillis = deletedAt?.toEpochMilli(),
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

internal fun ScanRecordEntity.toDomain(): ScanRecord = ScanRecord(
    id = id,
    fileId = fileId,
    pageCount = pageCount,
    recognisedAt = recognisedAtEpochMillis?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
)

internal fun ScanRecord.toEntity(): ScanRecordEntity = ScanRecordEntity(
    id = id,
    fileId = fileId,
    pageCount = pageCount,
    recognisedAtEpochMillis = recognisedAt?.toEpochMilli(),
    createdAtEpochMillis = createdAt.toEpochMilli(),
)

/**
 * An unknown currency code decays to BDT rather than throwing, for the same reason an unknown
 * enum name does: one unreadable field must not take the whole profile — and with it every screen
 * that prints Abu's details — down. A code Java knows round-trips exactly.
 */
internal fun UserProfileEntity.toDomain(): UserProfile = UserProfile(
    displayName = displayName,
    businessName = businessName,
    email = email,
    phone = phone,
    addressLine = addressLine,
    taxId = taxId,
    defaultCurrency = runCatching { Currency.getInstance(defaultCurrencyCode) }.getOrDefault(Money.BDT),
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)

internal fun UserProfile.toEntity(): UserProfileEntity = UserProfileEntity(
    id = UserProfileEntity.SINGLETON_ID,
    displayName = displayName,
    businessName = businessName,
    email = email,
    phone = phone,
    addressLine = addressLine,
    taxId = taxId,
    defaultCurrencyCode = defaultCurrency.currencyCode,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

/**
 * The subject is two nullable columns on disk and one nullable value in the domain. Rebuilding it
 * only when both halves are present is what keeps a table name with no id — or an id belonging to
 * no table — from being read back as a reference to something.
 */
internal fun ActivityLogEntity.toDomain(): ActivityEntry = ActivityEntry(
    id = id,
    category = category,
    action = action,
    summary = summary,
    subject = subjectTable?.let { table -> subjectId?.let { ActivitySubject(table, it) } },
    occurredAt = Instant.ofEpochMilli(occurredAtEpochMillis),
)

internal fun ActivityEntry.toEntity(): ActivityLogEntity = ActivityLogEntity(
    id = id,
    category = category,
    action = action,
    summary = summary,
    subjectTable = subject?.table,
    subjectId = subject?.id,
    occurredAtEpochMillis = occurredAt.toEpochMilli(),
)
