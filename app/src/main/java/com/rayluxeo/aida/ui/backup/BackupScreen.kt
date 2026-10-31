package com.rayluxeo.aida.ui.backup

import com.rayluxeo.aida.core.design.component.MiraIcons

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraButtonStyle
import com.rayluxeo.aida.core.design.component.MiraCard
import com.rayluxeo.aida.core.design.component.MiraInlineNotice
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.core.design.component.MiraScaffold
import com.rayluxeo.aida.core.design.component.MiraTextField
import com.rayluxeo.aida.core.design.component.MiraTopBar
import android.content.Context
import android.content.Intent

@Composable
fun BackupScreen(
    onNavigateUp: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val words = backupWording(state.language)
    val context = LocalContext.current

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        uri?.let {
            val outputStream = context.contentResolver.openOutputStream(it)
            if (outputStream != null) {
                viewModel.createBackup(outputStream)
            }
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            val inputStream = context.contentResolver.openInputStream(it)
            if (inputStream != null) {
                viewModel.restoreBackup(inputStream)
            }
        }
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let {
            val outputStream = context.contentResolver.openOutputStream(it)
            if (outputStream != null) {
                viewModel.exportJson(outputStream)
            }
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        uri?.let {
            val outputStream = context.contentResolver.openOutputStream(it)
            if (outputStream != null) {
                viewModel.exportCsv("tasks", outputStream)
            }
        }
    }

    MiraScaffold(
        topBar = {
            MiraTopBar(
                title = words.screenTitle,
                onBack = onNavigateUp,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.md),
        ) {
            state.notice?.let { notice ->
                MiraInlineNotice(
                    text = notice,
                    tone = if (state.isSuccess) MiraNoticeTone.CONFIRM else MiraNoticeTone.INFO,
                    // A restore only takes hold on restart, so the notice carries the way to do it.
                    actionText = if (state.restartRequired) words.restartNow else null,
                    onAction = if (state.restartRequired) {
                        { restartApp(context) }
                    } else {
                        null
                    },
                    onDismiss = { viewModel.dismissNotice() },
                )
            }

            MiraCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MiraTheme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
                ) {
                    Text(
                        text = words.encryptedBackupTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )

                    Text(
                        text = words.encryptedBackupBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    MiraTextField(
                        value = state.passphraseInput,
                        onValueChange = { viewModel.updatePassphrase(it) },
                        label = words.passphraseLabel,
                        placeholder = words.passphrasePlaceholder,
                    )

                    // Stacked full-width rather than two-up: the Bengali labels are longer than the
                    // English, and side by side with a leading icon they truncated to "ব্যাক…".
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
                    ) {
                        MiraButton(
                            text = words.createBackup,
                            onClick = { createBackupLauncher.launch("mira_backup_${System.currentTimeMillis()}.mbak") },
                            leadingIcon = MiraIcons.FileUpload,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        MiraButton(
                            text = words.restoreBackup,
                            onClick = { restoreBackupLauncher.launch(arrayOf("*/*")) },
                            leadingIcon = MiraIcons.FileDownload,
                            style = MiraButtonStyle.SECONDARY,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            MiraCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MiraTheme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
                ) {
                    Text(
                        text = words.exportTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
                    ) {
                        MiraButton(
                            text = words.exportJson,
                            onClick = { exportJsonLauncher.launch("mira_export.json") },
                            leadingIcon = MiraIcons.Save,
                            style = MiraButtonStyle.SECONDARY,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        MiraButton(
                            text = words.exportCsv,
                            onClick = { exportCsvLauncher.launch("mira_tasks.csv") },
                            leadingIcon = MiraIcons.Download,
                            style = MiraButtonStyle.SECONDARY,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Closes and relaunches Mira so a just-restored database and settings are read fresh.
 *
 * A restore replaces the files on disk, but the running process still holds the previous database
 * handle and the in-memory settings cache; a clean relaunch is the honest way to apply it. The
 * launch intent starts a new task and the current process then exits, so nothing of the old state
 * survives into the restored session.
 */
private fun restartApp(context: Context) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    if (launchIntent != null) {
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
    }
    Runtime.getRuntime().exit(0)
}
