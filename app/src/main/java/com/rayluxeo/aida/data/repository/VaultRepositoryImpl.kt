package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.data.crypto.FieldCipher
import com.rayluxeo.aida.data.db.dao.VaultDao
import com.rayluxeo.aida.data.db.entity.VaultItemEntity
import com.rayluxeo.aida.domain.model.VaultItem
import com.rayluxeo.aida.domain.model.VaultItemSummary
import com.rayluxeo.aida.domain.repository.VaultRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

@Singleton
class VaultRepositoryImpl @Inject constructor(
    private val dao: VaultDao,
    private val cipher: FieldCipher,
    private val dispatchers: DispatcherProvider,
) : VaultRepository {

    override fun observeSummaries(): Flow<List<VaultItemSummary>> =
        dao.observeSummaries()
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .orOnFailure(emptyList())

    override suspend fun reveal(id: Long): AppResult<VaultItem> {
        val found = storageCall(dispatchers, "load vault item $id") { dao.findById(id) }
        val entity = when (found) {
            is AppResult.Failure -> return found
            is AppResult.Success -> found.value
                ?: return failure(MiraErrorCode.NOT_FOUND, "No vault item with id $id")
        }
        return when (val decrypted = cipher.decrypt(entity.cipherText, entity.iv)) {
            is AppResult.Failure -> decrypted
            is AppResult.Success -> AppResult.Success(
                VaultItem(
                    id = entity.id,
                    title = entity.title,
                    secret = decrypted.value,
                    category = entity.category,
                    fileType = entity.fileType,
                    folder = entity.folder,
                    tags = entity.tagsCsv?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
                    clientId = entity.clientId,
                    projectId = entity.projectId,
                    invoiceId = entity.invoiceId,
                    appPrivateFilePath = entity.appPrivateFilePath,
                    metadataJson = entity.metadataJson,
                    createdAt = Instant.ofEpochMilli(entity.createdAtEpochMillis),
                    updatedAt = Instant.ofEpochMilli(entity.updatedAtEpochMillis),
                )
            )
        }
    }

    override suspend fun create(item: VaultItem): AppResult<VaultItemSummary> {
        val sealed = when (val encrypted = cipher.encrypt(item.secret)) {
            is AppResult.Failure -> return encrypted
            is AppResult.Success -> encrypted.value
        }
        return storageCall(dispatchers, "create vault item") {
            val id = dao.insert(
                VaultItemEntity(
                    title = item.title,
                    cipherText = sealed.cipherText,
                    iv = sealed.iv,
                    category = item.category,
                    fileType = item.fileType,
                    folder = item.folder,
                    tagsCsv = item.tags.joinToString(",").takeIf { it.isNotBlank() },
                    clientId = item.clientId,
                    projectId = item.projectId,
                    invoiceId = item.invoiceId,
                    appPrivateFilePath = item.appPrivateFilePath,
                    metadataJson = item.metadataJson,
                    createdAtEpochMillis = item.createdAt.toEpochMilli(),
                    updatedAtEpochMillis = item.updatedAt.toEpochMilli(),
                )
            )
            VaultItemSummary(
                id = id,
                title = item.title,
                category = item.category,
                fileType = item.fileType,
                folder = item.folder,
                tags = item.tags,
                clientId = item.clientId,
                projectId = item.projectId,
                invoiceId = item.invoiceId,
                updatedAt = item.updatedAt,
            )
        }
    }

    override suspend fun update(item: VaultItem): AppResult<VaultItemSummary> {
        val sealed = when (val encrypted = cipher.encrypt(item.secret)) {
            is AppResult.Failure -> return encrypted
            is AppResult.Success -> encrypted.value
        }
        val changed = storageCall(dispatchers, "update vault item ${item.id}") {
            dao.update(
                VaultItemEntity(
                    id = item.id,
                    title = item.title,
                    cipherText = sealed.cipherText,
                    iv = sealed.iv,
                    category = item.category,
                    fileType = item.fileType,
                    folder = item.folder,
                    tagsCsv = item.tags.joinToString(",").takeIf { it.isNotBlank() },
                    clientId = item.clientId,
                    projectId = item.projectId,
                    invoiceId = item.invoiceId,
                    appPrivateFilePath = item.appPrivateFilePath,
                    metadataJson = item.metadataJson,
                    createdAtEpochMillis = item.createdAt.toEpochMilli(),
                    updatedAtEpochMillis = item.updatedAt.toEpochMilli(),
                )
            )
        }
        return when (changed) {
            is AppResult.Failure -> changed
            is AppResult.Success ->
                if (changed.value > 0) {
                    AppResult.Success(
                        VaultItemSummary(
                            id = item.id,
                            title = item.title,
                            category = item.category,
                            fileType = item.fileType,
                            folder = item.folder,
                            tags = item.tags,
                            clientId = item.clientId,
                            projectId = item.projectId,
                            invoiceId = item.invoiceId,
                            updatedAt = item.updatedAt,
                        )
                    )
                } else {
                    failure(MiraErrorCode.NOT_FOUND, "No vault item with id ${item.id}")
                }
        }
    }

    override suspend fun delete(id: Long): AppResult<Unit> {
        val removed = storageCall(dispatchers, "delete vault item $id") { dao.deleteById(id) }
        return when (removed) {
            is AppResult.Failure -> removed
            is AppResult.Success ->
                if (removed.value > 0) AppResult.Success(Unit)
                else failure(MiraErrorCode.NOT_FOUND, "No vault item with id $id")
        }
    }
}
