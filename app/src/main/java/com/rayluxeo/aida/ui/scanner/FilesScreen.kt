package com.rayluxeo.aida.ui.scanner

import com.rayluxeo.aida.core.design.component.MiraIcons

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.fadingHorizontalEdges
import com.rayluxeo.aida.core.design.component.MiraBottomSheet
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraButtonStyle
import com.rayluxeo.aida.core.design.component.MiraCard
import com.rayluxeo.aida.core.design.component.MiraChip
import com.rayluxeo.aida.core.design.component.MiraDialog
import com.rayluxeo.aida.core.design.component.MiraEmptyState
import com.rayluxeo.aida.core.design.component.MiraInlineNotice
import com.rayluxeo.aida.core.design.component.MiraListRow
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.core.design.component.MiraScaffold
import com.rayluxeo.aida.core.design.component.MiraSearchField
import com.rayluxeo.aida.core.design.component.MiraTextField
import com.rayluxeo.aida.core.design.component.MiraTopBar
import com.rayluxeo.aida.domain.model.FileRecord
import com.rayluxeo.aida.domain.model.FileSource

@Composable
fun FilesScreen(
    onNavigateUp: () -> Unit,
    onOpenScanner: () -> Unit,
    onAskMira: (String) -> Unit,
    viewModel: FilesViewModel = hiltViewModel(),
) {
    val files by viewModel.files.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFile by viewModel.selectedFile.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val pendingUndoFileId by viewModel.pendingUndoFileId.collectAsStateWithLifecycle()
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val words = filesWording(language)

    var selectedSourceFilter by remember { mutableStateOf<FileSource?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var pickingClient by remember { mutableStateOf(false) }
    var pickingProject by remember { mutableStateOf(false) }

    val filteredFiles = files.filter { selectedSourceFilter == null || it.source == selectedSourceFilter }

    MiraScaffold(
        topBar = {
            MiraTopBar(
                title = words.screenTitle,
                onBack = onNavigateUp,
                backContentDescription = words.back,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
        ) {
            notice?.let { text ->
                MiraInlineNotice(
                    text = text,
                    tone = if (pendingUndoFileId != null) MiraNoticeTone.CONFIRM else MiraNoticeTone.INFO,
                    dismissContentDescription = words.dismiss,
                    onDismiss = { viewModel.dismissNotice() },
                    actionText = pendingUndoFileId?.let { words.undo },
                    onAction = pendingUndoFileId?.let { id -> { viewModel.restoreFile(id) } },
                )
            }

            MiraSearchField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = words.searchPlaceholder,
                onClear = { viewModel.updateSearchQuery("") },
            )

            // The source filter used to live only in a wide three-pane layout the phone never
            // reached, so in portrait every file was always shown. It is a chip row now.
            val sourceScroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fadingHorizontalEdges(sourceScroll)
                    .horizontalScroll(sourceScroll),
                horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
            ) {
                MiraChip(
                    text = words.allFilter,
                    selected = selectedSourceFilter == null,
                    onClick = { selectedSourceFilter = null },
                )
                FileSource.entries.forEach { source ->
                    MiraChip(
                        text = words.sourceLabel(source),
                        selected = selectedSourceFilter == source,
                        onClick = { selectedSourceFilter = source },
                    )
                }
            }

            MiraButton(
                text = words.openScanner,
                onClick = onOpenScanner,
                leadingIcon = MiraIcons.DocumentScanner,
                modifier = Modifier.fillMaxWidth(),
            )

            FileList(
                files = filteredFiles,
                words = words,
                onSelect = { viewModel.selectFile(it) },
            )
        }

        selectedFile?.let { file ->
            MiraBottomSheet(
                onDismissRequest = { viewModel.selectFile(null) },
                title = file.displayName,
            ) {
                FileDetailContent(
                    file = file,
                    words = words,
                    onAskMira = onAskMira,
                    onRename = { renaming = true },
                    onDelete = { confirmingDelete = true },
                    onLinkClient = { pickingClient = true },
                    onLinkProject = { pickingProject = true },
                )
            }
        }

        val current = selectedFile
        if (renaming && current != null) {
            RenameDialog(
                initial = current.displayName,
                words = words,
                onConfirm = { newName ->
                    viewModel.rename(current.id, newName)
                    renaming = false
                },
                onDismiss = { renaming = false },
            )
        }

        if (confirmingDelete && current != null) {
            MiraDialog(
                title = words.deleteTitle,
                message = words.deleteMessage(current.displayName),
                confirmText = words.delete,
                onConfirm = {
                    viewModel.deleteFile(current.id)
                    confirmingDelete = false
                },
                dismissText = words.cancel,
                onDismiss = { confirmingDelete = false },
                onDismissRequest = { confirmingDelete = false },
                destructive = true,
            )
        }

        if (pickingClient && current != null) {
            LinkPickerSheet(
                title = words.linkToClient,
                words = words,
                options = clients.map { it.id to it.name },
                selectedId = current.clientId,
                onPick = { id ->
                    viewModel.linkClient(current.id, id)
                    pickingClient = false
                },
                onDismiss = { pickingClient = false },
            )
        }

        if (pickingProject && current != null) {
            LinkPickerSheet(
                title = words.linkToProject,
                words = words,
                options = projects.map { it.id to it.name },
                selectedId = current.projectId,
                onPick = { id ->
                    viewModel.linkProject(current.id, id)
                    pickingProject = false
                },
                onDismiss = { pickingProject = false },
            )
        }
    }
}

@Composable
private fun FileList(
    files: List<FileRecord>,
    words: FilesWording,
    onSelect: (FileRecord) -> Unit,
) {
    if (files.isEmpty()) {
        MiraEmptyState(
            icon = MiraIcons.InsertDriveFile,
            message = words.emptyFiles,
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
        ) {
            items(files, key = { it.id }) { file ->
                MiraCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(file) },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MiraTheme.spacing.md),
                        verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
                    ) {
                        Text(
                            text = file.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MiraChip(text = words.sourceLabel(file.source), selected = false)
                            Text(
                                text = words.fileSize(file.sizeBytes),
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
private fun FileDetailContent(
    file: FileRecord,
    words: FilesWording,
    onAskMira: (String) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onLinkClient: () -> Unit,
    onLinkProject: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
    ) {
        Text(
            text = words.fileDetails(file.source, file.sizeBytes, file.mimeType),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!file.extractedText.isNullOrBlank()) {
            Text(
                text = words.extractedTextLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            MiraCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = file.extractedText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(MiraTheme.spacing.sm),
                )
            }
        }

        // Full-width stacked, not two-up: with a leading icon the Bengali labels — especially
        // "ক্লায়েন্ট যুক্ত করুন" / "প্রকল্প যুক্ত করুন" — truncated at half-width.
        Column(verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs)) {
            MiraButton(
                text = words.rename,
                onClick = onRename,
                leadingIcon = MiraIcons.Edit,
                style = MiraButtonStyle.SECONDARY,
                modifier = Modifier.fillMaxWidth(),
            )
            MiraButton(
                text = words.delete,
                onClick = onDelete,
                leadingIcon = MiraIcons.Delete,
                style = MiraButtonStyle.SECONDARY,
                accent = MiraTheme.colors.danger,
                modifier = Modifier.fillMaxWidth(),
            )
            MiraButton(
                text = words.linkClient,
                onClick = onLinkClient,
                leadingIcon = MiraIcons.Link,
                style = MiraButtonStyle.SECONDARY,
                modifier = Modifier.fillMaxWidth(),
            )
            MiraButton(
                text = words.linkProject,
                onClick = onLinkProject,
                leadingIcon = MiraIcons.Link,
                style = MiraButtonStyle.SECONDARY,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        MiraButton(
            text = words.askMiraAboutDocument,
            onClick = { onAskMira(words.askMiraPrompt(file.displayName)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RenameDialog(
    initial: String,
    words: FilesWording,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    MiraDialog(
        title = words.renameTitle,
        confirmText = words.save,
        onConfirm = { onConfirm(name) },
        dismissText = words.cancel,
        onDismiss = onDismiss,
        onDismissRequest = onDismiss,
    ) {
        MiraTextField(
            value = name,
            onValueChange = { name = it },
            label = words.fileNameLabel,
        )
    }
}

@Composable
private fun LinkPickerSheet(
    title: String,
    words: FilesWording,
    options: List<Pair<Long, String>>,
    selectedId: Long?,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    MiraBottomSheet(
        onDismissRequest = onDismiss,
        title = title,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (selectedId != null) {
                MiraListRow(
                    title = words.unlink,
                    onClick = { onPick(null) },
                )
            }
            if (options.isEmpty()) {
                MiraEmptyState(
                    icon = MiraIcons.Link,
                    message = words.nothingToLink,
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                    items(options, key = { it.first }) { (id, label) ->
                        MiraListRow(
                            title = label,
                            onClick = { onPick(id) },
                            trailing = if (id == selectedId) {
                                { Text(words.linked, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}
