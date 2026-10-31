package com.rayluxeo.aida.domain.usecase

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.model.VaultCategory
import com.rayluxeo.aida.domain.model.VaultItem
import com.rayluxeo.aida.domain.model.VaultItemSummary
import com.rayluxeo.aida.domain.repository.VaultRepository
import javax.inject.Inject

class CreateVaultItemUseCase @Inject constructor(
    private val vault: VaultRepository,
    private val clock: MiraClock,
) {
    suspend operator fun invoke(
        title: String,
        secret: String,
        category: VaultCategory,
    ): AppResult<VaultItemSummary> {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return failure(MiraErrorCode.VALIDATION, "Vault title was blank")
        if (secret.isEmpty()) return failure(MiraErrorCode.VALIDATION, "Vault secret was blank")
        val now = clock.now()
        return vault.create(
            VaultItem(title = cleanTitle, secret = secret, category = category, createdAt = now, updatedAt = now)
        )
    }
}

class RevealVaultItemUseCase @Inject constructor(private val vault: VaultRepository) {
    suspend operator fun invoke(id: Long): AppResult<VaultItem> = vault.reveal(id)
}

class DeleteVaultItemUseCase @Inject constructor(private val vault: VaultRepository) {
    suspend operator fun invoke(id: Long): AppResult<Unit> = vault.delete(id)
}
