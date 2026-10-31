package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.getOrNull
import com.rayluxeo.aida.domain.attention.AttentionDismissalRepository
import com.rayluxeo.aida.domain.repository.AppSettingRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persists dismissed proactive attention item IDs using [AppSettingRepository].
 */
@Singleton
class AttentionDismissalRepositoryImpl @Inject constructor(
    private val settings: AppSettingRepository,
) : AttentionDismissalRepository {

    override fun observeDismissedIds(): Flow<Set<String>> =
        settings.observe(KEY_DISMISSED).map { raw ->
            decode(raw)
        }

    override suspend fun getDismissedIds(): Set<String> {
        val raw = settings.get(KEY_DISMISSED).getOrNull()
        return decode(raw)
    }

    override suspend fun dismiss(id: String) {
        val current = getDismissedIds()
        val updated = current + id
        settings.put(KEY_DISMISSED, updated.joinToString(SEPARATOR))
    }

    override suspend fun clearDismissed() {
        settings.remove(KEY_DISMISSED)
    }

    private fun decode(raw: String?): Set<String> =
        raw?.split(SEPARATOR)?.filter { it.isNotBlank() }?.toSet().orEmpty()

    private companion object {
        const val KEY_DISMISSED = "proactive_attention_dismissed_ids"
        const val SEPARATOR = ","
    }
}
