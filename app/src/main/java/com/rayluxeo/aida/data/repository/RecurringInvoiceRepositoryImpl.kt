package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.flatMap
import com.rayluxeo.aida.core.common.map
import com.rayluxeo.aida.domain.model.RecurringInvoiceProfile
import com.rayluxeo.aida.domain.model.SubscriptionCycle
import com.rayluxeo.aida.domain.repository.AppSettingRepository
import com.rayluxeo.aida.domain.repository.RecurringInvoiceRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class RecurringInvoiceRepositoryImpl @Inject constructor(
    private val settings: AppSettingRepository,
) : RecurringInvoiceRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun observeAll(): Flow<List<RecurringInvoiceProfile>> =
        settings.observe(KEY_PROFILES).map { raw ->
            decode(raw)
        }

    override suspend fun getAll(): AppResult<List<RecurringInvoiceProfile>> =
        settings.get(KEY_PROFILES).map { raw ->
            decode(raw)
        }

    override suspend fun save(profile: RecurringInvoiceProfile): AppResult<RecurringInvoiceProfile> {
        return getAll().flatMap { current ->
            val updated = current.filter { it.id != profile.id } + profile
            saveAll(updated).map { profile }
        }
    }

    override suspend fun saveAll(profiles: List<RecurringInvoiceProfile>): AppResult<Unit> {
        val encoded = encode(profiles)
        return settings.put(KEY_PROFILES, encoded)
    }

    override suspend fun delete(id: String): AppResult<Unit> {
        return getAll().flatMap { current ->
            val updated = current.filter { it.id != id }
            saveAll(updated)
        }
    }

    private fun decode(raw: String?): List<RecurringInvoiceProfile> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val dtos = json.decodeFromString<List<RecurringProfileDto>>(raw)
            dtos.map { it.toDomain() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun encode(profiles: List<RecurringInvoiceProfile>): String {
        val dtos = profiles.map { it.toDto() }
        return json.encodeToString(dtos)
    }

    @Serializable
    private data class RecurringProfileDto(
        val id: String,
        val sourceInvoiceId: Long,
        val sourceInvoiceNumber: String,
        val clientId: Long,
        val cycle: String,
        val customDays: Int? = null,
        val nextRunEpochDay: Long,
        val active: Boolean = true,
    ) {
        fun toDomain(): RecurringInvoiceProfile = RecurringInvoiceProfile(
            id = id,
            sourceInvoiceId = sourceInvoiceId,
            sourceInvoiceNumber = sourceInvoiceNumber,
            clientId = clientId,
            cycle = runCatching { SubscriptionCycle.valueOf(cycle) }.getOrDefault(SubscriptionCycle.MONTHLY),
            customDays = customDays,
            nextRunDate = LocalDate.ofEpochDay(nextRunEpochDay),
            active = active,
        )
    }

    private fun RecurringInvoiceProfile.toDto(): RecurringProfileDto = RecurringProfileDto(
        id = id,
        sourceInvoiceId = sourceInvoiceId,
        sourceInvoiceNumber = sourceInvoiceNumber,
        clientId = clientId,
        cycle = cycle.name,
        customDays = customDays,
        nextRunEpochDay = nextRunDate.toEpochDay(),
        active = active,
    )

    private companion object {
        const val KEY_PROFILES = "recurring_invoice_profiles"
    }
}
