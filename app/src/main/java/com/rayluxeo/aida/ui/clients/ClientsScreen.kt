package com.rayluxeo.aida.ui.clients

import com.rayluxeo.aida.core.design.component.MiraIcons

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraBottomSheet
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraCard
import com.rayluxeo.aida.core.design.component.MiraChip
import com.rayluxeo.aida.core.design.component.MiraDialog
import com.rayluxeo.aida.core.design.component.MiraEmptyState
import com.rayluxeo.aida.core.design.component.MiraInlineNotice
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.core.design.component.MiraScaffold
import com.rayluxeo.aida.core.design.component.MiraSearchField
import com.rayluxeo.aida.core.design.component.MiraTextField
import com.rayluxeo.aida.core.design.component.MiraTopBar
import com.rayluxeo.aida.domain.model.Client

@Composable
fun ClientsScreen(
    onNavigateUp: () -> Unit,
    viewModel: ClientsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    val projects by viewModel.selectedClientProjects.collectAsStateWithLifecycle()
    val invoices by viewModel.selectedClientInvoices.collectAsStateWithLifecycle()
    val words = clientsWording(state.language)
    var showDeleteConfirm by remember { mutableStateOf(false) }

    MiraScaffold(
        topBar = {
            MiraTopBar(
                title = words.title,
                onBack = onNavigateUp,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
        ) {
            state.notice?.let { notice ->
                val undoId = state.pendingUndoClientId
                MiraInlineNotice(
                    text = notice,
                    tone = if (undoId != null) MiraNoticeTone.CONFIRM else MiraNoticeTone.INFO,
                    onDismiss = { viewModel.dismissNotice() },
                    actionText = undoId?.let { words.undo },
                    onAction = undoId?.let { id -> { viewModel.restoreClient(id) } },
                )
            }

            MiraSearchField(
                value = state.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                placeholder = words.searchPlaceholder,
                onClear = { viewModel.updateSearchQuery("") },
            )

            MiraButton(
                text = words.addClient,
                onClick = { viewModel.openAddSheet() },
                leadingIcon = MiraIcons.Add,
                modifier = Modifier.fillMaxWidth(),
            )

            ClientList(
                clients = clients,
                words = words,
                onSelect = { viewModel.selectClient(it) },
            )
        }

        // Tapping a client opens its detail; the sheet is the only place the phone can view or
        // delete one, which the dead wide-window pane never gave it.
        state.selectedClient?.let { client ->
            ClientDetailSheet(
                client = client,
                projectsCount = projects.size,
                invoicesCount = invoices.size,
                words = words,
                onDismiss = { viewModel.selectClient(null) },
                onDelete = { showDeleteConfirm = true },
            )
        }

        if (showDeleteConfirm) {
            state.selectedClient?.let { client ->
                MiraDialog(
                    title = words.deleteTitle,
                    message = words.deleteMessage(client.name),
                    confirmText = words.delete,
                    onConfirm = {
                        viewModel.deleteClient(client.id)
                        showDeleteConfirm = false
                    },
                    dismissText = words.cancel,
                    onDismiss = { showDeleteConfirm = false },
                    onDismissRequest = { showDeleteConfirm = false },
                    destructive = true,
                )
            }
        }

        if (state.isAddSheetOpen) {
            AddClientBottomSheet(
                words = words,
                onDismiss = { viewModel.closeAddSheet() },
                onSave = { name, company, email, phone ->
                    viewModel.createClient(name, company, email, phone)
                },
            )
        }
    }
}

@Composable
private fun ClientList(
    clients: List<Client>,
    words: ClientsWording,
    onSelect: (Client) -> Unit,
) {
    if (clients.isEmpty()) {
        MiraEmptyState(
            icon = MiraIcons.Groups,
            message = words.noClients,
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
        ) {
            items(clients, key = { it.id }) { client ->
                MiraCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(client) },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MiraTheme.spacing.sm),
                    ) {
                        Text(
                            text = client.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                        if (!client.companyName.isNullOrBlank()) {
                            Text(
                                text = client.companyName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientDetailSheet(
    client: Client,
    projectsCount: Int,
    invoicesCount: Int,
    words: ClientsWording,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    MiraBottomSheet(
        onDismissRequest = onDismiss,
        title = client.name,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
        ) {
            if (!client.companyName.isNullOrBlank()) {
                Text(
                    text = words.companyDetail(client.companyName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!client.email.isNullOrBlank()) {
                Text(
                    text = words.emailDetail(client.email),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!client.phone.isNullOrBlank()) {
                Text(
                    text = words.phoneDetail(client.phone),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs)) {
                MiraChip(text = words.projectsCount(projectsCount), selected = true)
                MiraChip(text = words.invoicesCount(invoicesCount), selected = false)
            }

            Spacer(modifier = Modifier.height(MiraTheme.spacing.xs))

            MiraButton(
                text = words.deleteClientAction,
                onClick = onDelete,
                leadingIcon = MiraIcons.Delete,
                accent = MiraTheme.colors.danger,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AddClientBottomSheet(
    words: ClientsWording,
    onDismiss: () -> Unit,
    onSave: (String, String?, String?, String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var companyName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    MiraBottomSheet(
        onDismissRequest = onDismiss,
        title = words.newClient,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
        ) {
            MiraTextField(
                value = name,
                onValueChange = { name = it },
                label = words.nameLabel,
                placeholder = words.namePlaceholder,
            )

            MiraTextField(
                value = companyName,
                onValueChange = { companyName = it },
                label = words.companyNameLabel,
            )

            MiraTextField(
                value = email,
                onValueChange = { email = it },
                label = words.emailLabel,
            )

            MiraTextField(
                value = phone,
                onValueChange = { phone = it },
                label = words.phoneLabel,
            )

            Spacer(modifier = Modifier.height(MiraTheme.spacing.xs))

            MiraButton(
                text = words.saveClient,
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            name,
                            companyName.takeIf { it.isNotBlank() },
                            email.takeIf { it.isNotBlank() },
                            phone.takeIf { it.isNotBlank() },
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
