package com.rayluxeo.aida.domain.automation

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.InstalledApp

/**
 * The launchable apps on the device, for building a "launch an app" automation.
 *
 * A port, because reading the installed apps needs a `PackageManager` (Android) that `domain` must
 * not touch. The implementation reads only apps with a launcher entry, through a scoped `<queries>`
 * entry — never `QUERY_ALL_PACKAGES` (AGENTS.md §7.8).
 */
interface InstalledAppCatalog {

    /** Every launchable app except Mira herself, sorted by label. A read failure is a value. */
    suspend fun launchableApps(): AppResult<List<InstalledApp>>
}
