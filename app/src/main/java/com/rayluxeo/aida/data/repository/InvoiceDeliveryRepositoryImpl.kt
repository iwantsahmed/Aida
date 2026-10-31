package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.model.InvoiceDeliveryMethod
import com.rayluxeo.aida.domain.model.InvoiceDeliveryRecord
import com.rayluxeo.aida.domain.model.InvoiceDeliveryStatus
import com.rayluxeo.aida.domain.repository.AppSettingRepository
import com.rayluxeo.aida.domain.repository.InvoiceDeliveryRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class SerializedDeliveryRecord(
    val id: String,
    val invoiceId: Long,
    val invoiceNumber: String,
    val clientName: String,
    val recipient: String,
    val method: String,
    val status: String,
    val subject: String,
    val message: String,
    val attachmentPath: String?,
    val timestampEpochMillis: Long,
    val notes: String? = null,
) {
    fun toDomain(): InvoiceDeliveryRecord = InvoiceDeliveryRecord(
        id = id,
        invoiceId = invoiceId,
        invoiceNumber = invoiceNumber,
        clientName = clientName,
        recipient = recipient,
        method = InvoiceDeliveryMethod.valueOf(method),
        status = InvoiceDeliveryStatus.valueOf(status),
        subject = subject,
        message = message,
        attachmentPath = attachmentPath,
        timestamp = Instant.ofEpochMilli(timestampEpochMillis),
        notes = notes,
    )

    companion object {
        fun fromDomain(record: InvoiceDeliveryRecord): SerializedDeliveryRecord = SerializedDeliveryRecord(
            id = record.id,
            invoiceId = record.invoiceId,
            invoiceNumber = record.invoiceNumber,
            clientName = record.clientName,
            recipient = record.recipient,
            method = record.method.name,
            status = record.status.name,
            subject = record.subject,
            message = record.message,
            attachmentPath = record.attachmentPath,
            timestampEpochMillis = record.timestamp.toEpochMilli(),
            notes = record.notes,
        )
    }
}

@Singleton
class InvoiceDeliveryRepositoryImpl @Inject constructor(
    private val appSettingRepository: AppSettingRepository,
    private val dispatchers: DispatcherProvider,
) : InvoiceDeliveryRepository {

    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun observeAll(): Flow<List<InvoiceDeliveryRecord>> =
        appSettingRepository.observe(STORAGE_KEY)
            .map { raw ->
                if (raw.isNullOrBlank()) {
                    emptyList()
                } else {
                    try {
                        json.decodeFromString<List<SerializedDeliveryRecord>>(raw)
                            .map { it.toDomain() }
                            .sortedByDescending { it.timestamp }
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
            }
            .flowOn(dispatchers.io)

    override fun observeForInvoice(invoiceId: Long): Flow<List<InvoiceDeliveryRecord>> =
        observeAll().map { records -> records.filter { it.invoiceId == invoiceId } }

    override suspend fun record(delivery: InvoiceDeliveryRecord): AppResult<InvoiceDeliveryRecord> {
        return mutex.withLock {
            try {
                val currentRaw = appSettingRepository.get(STORAGE_KEY).let { res ->
                    if (res is AppResult.Success) res.value else null
                }
                val list = if (currentRaw.isNullOrBlank()) {
                    mutableListOf<SerializedDeliveryRecord>()
                } else {
                    try {
                        json.decodeFromString<List<SerializedDeliveryRecord>>(currentRaw).toMutableList()
                    } catch (e: Exception) {
                        mutableListOf()
                    }
                }

                list.add(SerializedDeliveryRecord.fromDomain(delivery))
                val updatedRaw = json.encodeToString(list)
                val putResult = appSettingRepository.put(STORAGE_KEY, updatedRaw)

                if (putResult is AppResult.Success) {
                    AppResult.Success(delivery)
                } else {
                    failure((putResult as AppResult.Failure).error.code, putResult.error.detail)
                }
            } catch (e: Exception) {
                failure(com.rayluxeo.aida.core.common.MiraErrorCode.STORAGE, "Failed to store delivery record: ${e.message}")
            }
        }
    }

    companion object {
        private const val STORAGE_KEY = "invoice_delivery_records"
    }
}
