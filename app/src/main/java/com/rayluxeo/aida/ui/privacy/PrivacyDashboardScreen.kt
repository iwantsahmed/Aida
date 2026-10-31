package com.rayluxeo.aida.ui.privacy

import com.rayluxeo.aida.core.design.component.MiraIcons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraCard
import com.rayluxeo.aida.core.design.component.MiraDialog
import com.rayluxeo.aida.core.design.component.MiraChip
import com.rayluxeo.aida.core.design.component.MiraInlineNotice
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.core.design.component.MiraScaffold
import com.rayluxeo.aida.core.design.component.MiraTopBar
import com.rayluxeo.aida.ui.tasks.displayLanguage

@Composable
fun PrivacyDashboardScreen(
    onNavigateUp: () -> Unit,
    viewModel: PrivacyDashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val words = privacyWording(settings.languageMode.displayLanguage())

    MiraScaffold(
        topBar = {
            MiraTopBar(
                title = words.dashboardTitle,
                onBack = onNavigateUp,
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.md),
        ) {
            item {
                state.notice?.let { notice ->
                    MiraInlineNotice(
                        text = notice,
                        tone = MiraNoticeTone.INFO,
                        onDismiss = { viewModel.dismissNotice() },
                    )
                }
            }

            item {
                MiraCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MiraTheme.spacing.md),
                        verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
                    ) {
                        Text(
                            text = words.offlineArchitectureTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )

                        Text(
                            text = words.offlineArchitectureBody,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
                        ) {
                            MiraChip(text = words.cloudDisabledChip, selected = false)
                            MiraChip(text = words.onDeviceNluChip, selected = true)
                        }
                    }
                }
            }

            item {
                MiraCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MiraTheme.spacing.md),
                        verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
                    ) {
                        Text(
                            text = words.cloudAiTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = words.geminiEngineTitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = if (settings.geminiEnabled) words.geminiActiveSubtitle else words.geminiOffSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Switch(
                                checked = settings.geminiEnabled,
                                onCheckedChange = { viewModel.toggleGeminiEnabled(it) },
                            )
                        }
                    }
                }
            }

            item {
                MiraCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MiraTheme.spacing.md),
                        verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
                    ) {
                        Text(
                            text = words.appLockTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = words.appLockRowTitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = if (settings.appLockEnabled) {
                                        words.appLockOnSubtitle
                                    } else {
                                        words.appLockOffSubtitle
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Switch(
                                checked = settings.appLockEnabled,
                                onCheckedChange = { viewModel.toggleAppLock(it) },
                            )
                        }
                    }
                }
            }

            item {
                MiraCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MiraTheme.spacing.md),
                        verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
                    ) {
                        Text(
                            text = words.destructiveActionsTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MiraTheme.colors.danger,
                            fontWeight = FontWeight.Bold,
                        )

                        MiraButton(
                            text = words.eraseAllButton,
                            onClick = { viewModel.openEraseConfirmation() },
                            leadingIcon = MiraIcons.Delete,
                            accent = MiraTheme.colors.danger,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        if (state.isEraseConfirmationOpen) {
            MiraDialog(
                title = words.eraseDialogTitle,
                message = words.eraseDialogMessage,
                confirmText = words.eraseDialogConfirm,
                onConfirm = { viewModel.confirmEraseAllData() },
                dismissText = words.eraseDialogCancel,
                onDismiss = { viewModel.closeEraseConfirmation() },
                onDismissRequest = { viewModel.closeEraseConfirmation() },
                destructive = true,
            )
        }
    }
}
