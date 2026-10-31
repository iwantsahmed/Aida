package com.rayluxeo.aida.domain.ota

import com.rayluxeo.aida.core.common.AppResult
import java.io.File

interface UpdateRepository {

    /**
     * Checks GitHub Releases for the latest release and compares with the currently installed version.
     */
    suspend fun checkForUpdate(
        currentVersionCode: Long,
        currentVersionName: String,
        repositoryOwner: String = DEFAULT_OWNER,
        repositoryName: String = DEFAULT_REPO,
    ): AppResult<AppUpdate>

    /**
     * Downloads the APK file from [downloadUrl] and reports progress from 0.0 to 1.0.
     * Returns the downloaded [File] ready for installation.
     */
    suspend fun downloadUpdate(
        downloadUrl: String,
        targetFileName: String,
        onProgress: (Float) -> Unit,
    ): AppResult<File>

    companion object {
        const val DEFAULT_OWNER = "iwantsahmed"
        const val DEFAULT_REPO = "Aida"
    }
}
