package com.rayluxeo.aida.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraDivider
import com.rayluxeo.aida.core.design.component.MiraIcons
import com.rayluxeo.aida.core.design.component.MiraListRow
import com.rayluxeo.aida.core.design.component.MiraScaffold
import com.rayluxeo.aida.core.design.component.MiraTopBar
import com.rayluxeo.aida.domain.command.MiraCapability
import com.rayluxeo.aida.ui.mira.hasGranted
import com.rayluxeo.aida.ui.mira.permissions
import com.rayluxeo.aida.ui.mira.settingsIntents
import com.rayluxeo.aida.ui.mira.tryStartActivity

/**
 * What Mira may reach on the phone, each capability named in Abu's terms with its live grant state.
 *
 * Nothing is cached: the status is asked of Android on every resume and after every request, so a
 * permission revoked in system settings shows as revoked the instant Abu comes back. A capability
 * with a runtime dialog is requested in place; one without (exact alarms, notifications on old
 * Android) opens the system screen that can grant it.
 */
@Composable
fun PermissionsScreen(
    onBack: () -> Unit,
    viewModel: PermissionsViewModel = hiltViewModel(),
) {
    val language by viewModel.language.collectAsStateWithLifecycle()
    val words = permissionsWording(language)
    val spacing = MiraTheme.spacing
    val context = LocalContext.current

    // Bumped on resume and after a request so each row re-asks the OS for its grant state.
    var refreshKey by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshKey++ }

    val requestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { refreshKey++ }

    MiraScaffold(
        topBar = { MiraTopBar(title = words.title, onBack = onBack, backContentDescription = words.back) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(spacing.md))
            Text(
                text = words.intro,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(spacing.lg))

            MiraCapability.entries.forEachIndexed { index, capability ->
                if (index > 0) MiraDivider()
                val granted = remember(refreshKey, capability) { context.hasGranted(capability) }
                MiraListRow(
                    title = words.name(capability),
                    supporting = words.purpose(capability),
                    onClick = {
                        val runtimePermissions = capability.permissions()
                        if (!context.hasGranted(capability) && runtimePermissions.isNotEmpty()) {
                            requestLauncher.launch(runtimePermissions.toTypedArray())
                        } else {
                            capability.settingsIntents(context).firstOrNull()
                                ?.let { context.tryStartActivity(it) }
                        }
                    },
                    leading = {
                        Icon(
                            imageVector = capability.glyph(),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailing = {
                        Text(
                            text = if (granted) words.granted else words.notGranted,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (granted) MiraTheme.colors.positive else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
            Spacer(Modifier.height(spacing.xxl))
        }
    }
}

@Composable
private fun MiraCapability.glyph() = when (this) {
    MiraCapability.MICROPHONE -> MiraIcons.Mic
    MiraCapability.CONTACTS -> MiraIcons.Person
    MiraCapability.CALENDAR -> MiraIcons.CalendarMonth
    MiraCapability.NOTIFICATIONS -> MiraIcons.NotificationsActive
    MiraCapability.EXACT_ALARMS -> MiraIcons.Alarm
}
