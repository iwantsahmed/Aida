package com.rayluxeo.aida.domain.model

import java.time.Instant

data class BackupMetadata(
    val version: Int = 1,
    val miraDbVersion: Int = 8,
    val appVersion: String = "1.0.0",
    val createdAt: Instant,
    val deviceModel: String = android.os.Build.MODEL ?: "Android Device",
    val taskCount: Int = 0,
    val noteCount: Int = 0,
    val invoiceCount: Int = 0,
)
