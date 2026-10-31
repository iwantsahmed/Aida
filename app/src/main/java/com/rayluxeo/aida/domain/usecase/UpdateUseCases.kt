package com.rayluxeo.aida.domain.usecase

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.ota.AppUpdate
import com.rayluxeo.aida.domain.ota.UpdateRepository
import java.io.File
import javax.inject.Inject

class CheckAppUpdateUseCase @Inject constructor(
    private val repository: UpdateRepository,
) {
    suspend operator fun invoke(
        currentVersionCode: Long,
        currentVersionName: String,
        repositoryOwner: String = UpdateRepository.DEFAULT_OWNER,
        repositoryName: String = UpdateRepository.DEFAULT_REPO,
    ): AppResult<AppUpdate> {
        return repository.checkForUpdate(
            currentVersionCode = currentVersionCode,
            currentVersionName = currentVersionName,
            repositoryOwner = repositoryOwner,
            repositoryName = repositoryName,
        )
    }
}

class DownloadAppUpdateUseCase @Inject constructor(
    private val repository: UpdateRepository,
) {
    suspend operator fun invoke(
        downloadUrl: String,
        targetFileName: String,
        onProgress: (Float) -> Unit,
    ): AppResult<File> {
        return repository.downloadUpdate(
            downloadUrl = downloadUrl,
            targetFileName = targetFileName,
            onProgress = onProgress,
        )
    }
}
