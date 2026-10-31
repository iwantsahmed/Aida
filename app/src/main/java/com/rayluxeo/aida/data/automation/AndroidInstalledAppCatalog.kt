package com.rayluxeo.aida.data.automation

import android.content.Context
import android.content.Intent
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.asSuccess
import com.rayluxeo.aida.domain.automation.InstalledAppCatalog
import com.rayluxeo.aida.domain.model.InstalledApp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * Reads the launchable apps through `PackageManager`, scoped by the manifest's
 * `<intent><action MAIN/><category LAUNCHER/></intent>` query — never `QUERY_ALL_PACKAGES` (§7.8).
 *
 * Mira herself is dropped (launching the automation's own app is not a cross-app action), and the
 * list is de-duplicated by package and sorted by label so the picker reads the way Abu expects. The
 * query runs off the main thread; a failure is a value, never a crash.
 */
@Singleton
class AndroidInstalledAppCatalog @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : InstalledAppCatalog {

    override suspend fun launchableApps(): AppResult<List<InstalledApp>> = withContext(dispatchers.io) {
        val packageManager = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = runCatching { packageManager.queryIntentActivities(launcher, 0) }.getOrDefault(emptyList())
        val apps = resolved.mapNotNull { info ->
            val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
            if (packageName == context.packageName) return@mapNotNull null
            val label = info.loadLabel(packageManager).toString().ifBlank { packageName }
            InstalledApp(packageName = packageName, label = label)
        }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
        apps.asSuccess()
    }
}
