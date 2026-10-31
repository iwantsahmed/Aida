package com.rayluxeo.aida.ui.projects

import com.rayluxeo.aida.core.design.component.MiraIcons

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraBottomSheet
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraButtonStyle
import com.rayluxeo.aida.core.design.component.MiraCard
import com.rayluxeo.aida.core.design.component.MiraChip
import com.rayluxeo.aida.core.design.component.MiraDialog
import com.rayluxeo.aida.core.design.component.MiraEmptyState
import com.rayluxeo.aida.core.design.component.MiraInlineNotice
import com.rayluxeo.aida.core.design.component.MiraListSkeleton
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.core.design.component.MiraScaffold
import com.rayluxeo.aida.core.design.component.MiraSearchField
import com.rayluxeo.aida.core.design.component.MiraTextField
import com.rayluxeo.aida.core.design.component.MiraTopBar
import com.rayluxeo.aida.core.design.fadingHorizontalEdges
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.Project
import com.rayluxeo.aida.domain.model.ProjectFilter
import com.rayluxeo.aida.domain.model.ProjectStatus
import com.rayluxeo.aida.ui.common.LocalDateStyle
import com.rayluxeo.aida.ui.common.formatDate
import com.rayluxeo.aida.ui.response.bengaliNumerals
import com.rayluxeo.aida.ui.response.moneyFigure
import com.rayluxeo.aida.ui.tasks.TaskDatePickerDialog
import java.time.LocalDate

/** The order the filter chips sit in: the working set first, the closed sets, then everything. */
private val FilterOrder = listOf(
    ProjectFilter.OPEN,
    ProjectFilter.ACTIVE,
    ProjectFilter.COMPLETED,
    ProjectFilter.CANCELLED,
    ProjectFilter.ALL,
)

@Composable
fun ProjectsScreen(
    onNavigateUp: () -> Unit,
    viewModel: ProjectsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    val words = projectsWording(state.language)
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
                val undoId = state.pendingUndoProjectId
                MiraInlineNotice(
                    text = notice,
                    tone = if (undoId != null) MiraNoticeTone.CONFIRM else MiraNoticeTone.INFO,
                    onDismiss = { viewModel.dismissNotice() },
                    actionText = undoId?.let { words.undo },
                    onAction = undoId?.let { id -> { viewModel.restoreProject(id) } },
                )
            }

            ProjectFilterRow(
                selected = state.filter,
                words = words,
                onSelect = viewModel::selectFilter,
            )

            MiraSearchField(
                value = state.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                placeholder = words.searchPlaceholder,
                onClear = { viewModel.updateSearchQuery("") },
            )

            MiraButton(
                text = words.addProject,
                onClick = { viewModel.openNewSheet() },
                leadingIcon = MiraIcons.Add,
                modifier = Modifier.fillMaxWidth(),
            )

            ProjectList(
                items = projects,
                language = state.language,
                words = words,
                onSelect = { viewModel.selectProject(it) },
            )
        }

        // Tapping a project opens its detail; the sheet is where a project is viewed, its status
        // changed, edited or deleted — mirroring the Clients screen rather than adding a route.
        state.selectedProject?.let { project ->
            val clientName = project.clientId?.let { id -> clients.firstOrNull { it.id == id }?.name }
            ProjectDetailSheet(
                project = project,
                clientName = clientName,
                openTasks = state.selectedProjectOpenTasks,
                language = state.language,
                words = words,
                onDismiss = { viewModel.selectProject(null) },
                onChangeStatus = { status -> viewModel.changeStatus(project.id, status) },
                onEdit = { viewModel.openEditSheet(project) },
                onDelete = { showDeleteConfirm = true },
            )
        }

        if (showDeleteConfirm) {
            state.selectedProject?.let { project ->
                MiraDialog(
                    title = words.deleteTitle,
                    message = words.deleteMessage(project.name),
                    confirmText = words.delete,
                    onConfirm = {
                        viewModel.deleteProject(project.id)
                        showDeleteConfirm = false
                    },
                    dismissText = words.cancel,
                    onDismiss = { showDeleteConfirm = false },
                    onDismissRequest = { showDeleteConfirm = false },
                    destructive = true,
                )
            }
        }

        if (state.isFormOpen) {
            ProjectFormSheet(
                editing = state.editing,
                clients = clients,
                today = viewModel.today(),
                words = words,
                language = state.language,
                onDismiss = { viewModel.closeSheet() },
                onSave = { name, clientId, description, status, startedOn, dueOn, budget ->
                    viewModel.saveProject(name, clientId, description, status, startedOn, dueOn, budget)
                },
            )
        }
    }
}

@Composable
private fun ProjectFilterRow(
    selected: ProjectFilter,
    words: ProjectsWording,
    onSelect: (ProjectFilter) -> Unit,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .fadingHorizontalEdges(scroll)
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
    ) {
        FilterOrder.forEach { filter ->
            MiraChip(
                text = words.filterLabel(filter),
                selected = filter == selected,
                onClick = { onSelect(filter) },
            )
        }
    }
}

@Composable
private fun ProjectList(
    items: List<ProjectListItem>?,
    language: SpokenLanguage,
    words: ProjectsWording,
    onSelect: (Project) -> Unit,
) {
    when {
        items == null -> MiraListSkeleton(modifier = Modifier.fillMaxSize())
        items.isEmpty() -> MiraEmptyState(
            icon = MiraIcons.WorkOutline,
            message = words.noProjects,
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
        ) {
            items(items, key = { it.project.id }) { item ->
                ProjectRow(
                    item = item,
                    language = language,
                    words = words,
                    onClick = { onSelect(item.project) },
                )
            }
        }
    }
}

@Composable
private fun ProjectRow(
    item: ProjectListItem,
    language: SpokenLanguage,
    words: ProjectsWording,
    onClick: () -> Unit,
) {
    val project = item.project
    val spacing = MiraTheme.spacing
    MiraCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Text(
                text = project.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.clientName ?: words.personal,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                // Status takes the single accent — one tag to scan by — not a per-status colour (§10).
                Text(
                    text = words.statusLabel(project.status),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                project.dueOn?.let { due ->
                    Text(
                        text = formatDate(due, LocalDateStyle.current, language),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                project.budget?.let { budget ->
                    Text(
                        text = formatMoney(budget, language),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectDetailSheet(
    project: Project,
    clientName: String?,
    openTasks: Int,
    language: SpokenLanguage,
    words: ProjectsWording,
    onDismiss: () -> Unit,
    onChangeStatus: (ProjectStatus) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val spacing = MiraTheme.spacing
    MiraBottomSheet(onDismissRequest = onDismiss, title = project.name) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = clientName?.let { words.clientDetail(it) } ?: words.personal,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            project.description?.takeIf { it.isNotBlank() }?.let { notes ->
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            project.startedOn?.let { date ->
                Text(
                    text = words.startedDetail(formatDate(date, LocalDateStyle.current, language)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            project.dueOn?.let { date ->
                Text(
                    text = words.dueDetail(formatDate(date, LocalDateStyle.current, language)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            project.budget?.let { budget ->
                Text(
                    text = words.budgetDetail(formatMoney(budget, language)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                MiraChip(text = words.openTasksCount(openTasks), selected = true)
            }

            Text(
                text = words.statusSection,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ProjectStatusRow(selected = project.status, words = words, onSelect = onChangeStatus)

            Spacer(Modifier.height(spacing.xs))

            MiraButton(
                text = words.editProjectAction,
                onClick = onEdit,
                style = MiraButtonStyle.SECONDARY,
                leadingIcon = MiraIcons.Edit,
                modifier = Modifier.fillMaxWidth(),
            )
            MiraButton(
                text = words.deleteProjectAction,
                onClick = onDelete,
                leadingIcon = MiraIcons.Delete,
                accent = MiraTheme.colors.danger,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The five statuses on one scrolling line, the current one filled. Used to change a project's status. */
@Composable
private fun ProjectStatusRow(
    selected: ProjectStatus,
    words: ProjectsWording,
    onSelect: (ProjectStatus) -> Unit,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .fadingHorizontalEdges(scroll)
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
    ) {
        ProjectStatus.entries.forEach { status ->
            MiraChip(
                text = words.statusLabel(status),
                selected = status == selected,
                onClick = { onSelect(status) },
            )
        }
    }
}

@Composable
private fun ProjectFormSheet(
    editing: Project?,
    clients: List<Client>,
    today: LocalDate,
    words: ProjectsWording,
    language: SpokenLanguage,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        clientId: Long?,
        description: String?,
        status: ProjectStatus,
        startedOn: LocalDate?,
        dueOn: LocalDate?,
        budget: Money?,
    ) -> Unit,
) {
    val spacing = MiraTheme.spacing
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var clientId by remember { mutableStateOf(editing?.clientId) }
    var status by remember { mutableStateOf(editing?.status ?: ProjectStatus.ACTIVE) }
    var description by remember { mutableStateOf(editing?.description ?: "") }
    var startedOn by remember { mutableStateOf(editing?.startedOn) }
    var dueOn by remember { mutableStateOf(editing?.dueOn) }
    var budgetText by remember { mutableStateOf(editing?.budget?.amount?.stripTrailingZeros()?.toPlainString() ?: "") }
    var showStartPicker by remember { mutableStateOf(false) }
    var showDuePicker by remember { mutableStateOf(false) }

    MiraBottomSheet(
        onDismissRequest = onDismiss,
        title = if (editing == null) words.newProject else words.editProject,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            MiraTextField(
                value = name,
                onValueChange = { name = it },
                label = words.nameLabel,
                placeholder = words.namePlaceholder,
            )

            FormSectionLabel(words.clientSection)
            ClientPickerRow(
                clients = clients,
                selectedClientId = clientId,
                personalLabel = words.personal,
                onSelect = { clientId = it },
            )

            FormSectionLabel(words.statusSection)
            ProjectStatusRow(selected = status, words = words, onSelect = { status = it })

            MiraTextField(
                value = description,
                onValueChange = { description = it },
                label = words.descriptionFieldLabel,
                singleLine = false,
            )

            DateFieldRow(
                label = words.startedLabel,
                date = startedOn,
                language = language,
                pickText = words.pickDate,
                clearText = words.cancel,
                onPick = { showStartPicker = true },
                onClear = { startedOn = null },
            )
            DateFieldRow(
                label = words.dueLabel,
                date = dueOn,
                language = language,
                pickText = words.pickDate,
                clearText = words.cancel,
                onPick = { showDuePicker = true },
                onClear = { dueOn = null },
            )

            MiraTextField(
                value = budgetText,
                onValueChange = { input -> budgetText = input.filter { it.isDigit() || it == '.' } },
                label = words.budgetLabel,
                placeholder = words.budgetPlaceholder,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )

            Spacer(Modifier.height(spacing.xs))

            MiraButton(
                text = words.saveProject,
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            name,
                            clientId,
                            description.takeIf { it.isNotBlank() },
                            status,
                            startedOn,
                            dueOn,
                            parseBudget(budgetText),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showStartPicker) {
        TaskDatePickerDialog(
            initialDate = startedOn ?: today,
            setText = words.setDate,
            cancelText = words.cancel,
            onPick = { startedOn = it },
            onDismiss = { showStartPicker = false },
        )
    }
    if (showDuePicker) {
        TaskDatePickerDialog(
            initialDate = dueOn ?: today,
            setText = words.setDate,
            cancelText = words.cancel,
            onPick = { dueOn = it },
            onDismiss = { showDuePicker = false },
        )
    }
}

/** The client picker: "Personal" first (no client), then every client, on one scrolling line. */
@Composable
private fun ClientPickerRow(
    clients: List<Client>,
    selectedClientId: Long?,
    personalLabel: String,
    onSelect: (Long?) -> Unit,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .fadingHorizontalEdges(scroll)
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
    ) {
        MiraChip(
            text = personalLabel,
            selected = selectedClientId == null,
            onClick = { onSelect(null) },
        )
        clients.forEach { client ->
            MiraChip(
                text = client.name,
                selected = client.id == selectedClientId,
                onClick = { onSelect(client.id) },
            )
        }
    }
}

/** A labelled date row: the chosen date (or a prompt), set via the picker, cleared via the trailing control. */
@Composable
private fun DateFieldRow(
    label: String,
    date: LocalDate?,
    language: SpokenLanguage,
    pickText: String,
    clearText: String,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (date != null) {
            Text(
                text = formatDate(date, LocalDateStyle.current, language),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            MiraButton(
                text = clearText,
                onClick = onClear,
                style = MiraButtonStyle.TEXT,
                accent = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            MiraButton(
                text = pickText,
                onClick = onPick,
                style = MiraButtonStyle.TEXT,
            )
        }
    }
}

@Composable
private fun FormSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The amount as Abu reads it: the grouped figure (shared, never a `Double` — §8), the taka sign for
 * BDT or the currency code otherwise, and Bengali numerals when Mira is speaking Bengali. The same
 * shape the Money screen uses; a project budget reads exactly like a figure anywhere else.
 */
private fun formatMoney(money: Money, language: SpokenLanguage): String {
    val symbol = if (money.currency.currencyCode == "BDT") "৳" else "${money.currency.currencyCode} "
    val figure = moneyFigure(money)
    val digits = if (language == SpokenLanguage.BENGALI) bengaliNumerals(figure) else figure
    return "$symbol$digits"
}

/** A typed budget to [Money] in BDT, or null when the field is empty or not a number. */
private fun parseBudget(text: String): Money? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    return runCatching { Money.of(trimmed, Money.BDT) }.getOrNull()
}
