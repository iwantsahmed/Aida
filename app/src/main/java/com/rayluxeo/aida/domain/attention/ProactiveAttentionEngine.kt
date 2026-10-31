package com.rayluxeo.aida.domain.attention

import com.rayluxeo.aida.core.common.AppResult

/**
 * Proactively scans Abu's local records to detect conditions requiring attention.
 *
 * Runs locally on device with zero monetary cost and offline-first guarantees.
 * Never executes mutating external actions automatically (no auto-sending messages or invoices,
 * no auto-charging, no auto-deleting).
 */
interface ProactiveAttentionEngine {

    /**
     * Evaluates all 10 local detection categories and returns proactive attention items,
     * ranked by severity and urgency, excluding items Abu has already dismissed.
     */
    suspend fun detectItems(): AppResult<List<ProactiveAttentionItem>>

    /**
     * Dismisses an attention item by ID.
     */
    suspend fun dismiss(id: String)
}
