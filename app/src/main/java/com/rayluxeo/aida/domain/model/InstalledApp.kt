package com.rayluxeo.aida.domain.model

/**
 * A launchable app on the device, for the picker behind the "launch an app" automation action.
 *
 * Only apps with a launcher entry are ever surfaced, read through a scoped `<queries>` entry — never
 * [android.Manifest.permission.QUERY_ALL_PACKAGES], which AGENTS.md §7.8 forbids. [label] is what Abu
 * reads and picks; [packageName] is what the action launches.
 */
data class InstalledApp(
    val packageName: String,
    val label: String,
)
