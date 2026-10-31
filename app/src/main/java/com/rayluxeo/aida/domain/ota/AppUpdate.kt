package com.rayluxeo.aida.domain.ota

/**
 * Information about an available Over-The-Air (OTA) update hosted on GitHub Releases.
 */
data class AppUpdate(
    val currentVersionName: String,
    val currentVersionCode: Long,
    val latestVersionName: String,
    val latestVersionCode: Long,
    val releaseTitle: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val apkSizeBytes: Long,
    val hasUpdate: Boolean,
)
