package com.rayluxeo.aida.ui.memory

import com.rayluxeo.aida.core.design.component.MiraIcons

import androidx.compose.foundation.horizontalScroll
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.fadingHorizontalEdges
import com.rayluxeo.aida.core.design.component.MiraBottomSheet
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraCard
import com.rayluxeo.aida.core.design.component.MiraChip
import com.rayluxeo.aida.core.design.component.MiraEmptyState
import com.rayluxeo.aida.core.design.component.MiraIconButton
import com.rayluxeo.aida.core.design.component.MiraInlineNotice
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.core.design.component.MiraScaffold
import com.rayluxeo.aida.core.design.component.MiraTextField
import com.rayluxeo.aida.core.design.component.MiraTopBar
import com.rayluxeo.aida.domain.model.MemoryCategory
import com.rayluxeo.aida.domain.model.MemoryFact

@Composable
fun MemoryScreen(
    onNavigateUp: () -> Unit,
    viewModel: MemoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val words = memoryWording(state.language)

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
            verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.md),
        ) {
            state.notice?.let { notice ->
                MiraInlineNotice(
                    text = words.noticeText(notice),
                    tone = MiraNoticeTone.INFO,
                    onDismiss = { viewModel.dismissNotice() },
                )
            }

            MiraTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = words.searchPlaceholder,
                leadingIcon = MiraIcons.Search,
            )

            // Category filter chips. More categories than fit on a line, so the row scrolls and its
            // edges fade wherever there is more to reach.
            val categoryScroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fadingHorizontalEdges(categoryScroll)
                    .horizontalScroll(categoryScroll),
                horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
            ) {
                MiraChip(
                    text = words.allCategories,
                    selected = state.selectedCategory == null,
                    onClick = { viewModel.selectCategory(null) },
                )
                MemoryCategory.entries.forEach { category ->
                    MiraChip(
                        text = words.categoryName(category),
                        selected = state.selectedCategory == category,
                        onClick = { viewModel.selectCategory(category) },
                    )
                }
            }

            MiraButton(
                text = words.rememberFact,
                onClick = { viewModel.openAddDialog() },
                leadingIcon = MiraIcons.Add,
                modifier = Modifier.fillMaxWidth(),
            )

            if (memories.isEmpty()) {
                MiraEmptyState(
                    icon = MiraIcons.Psychology,
                    message = if (state.searchQuery.isBlank()) words.emptyNoMemories else words.emptyNoMatch(state.searchQuery),
                    actionText = words.addFact,
                    onAction = { viewModel.openAddDialog() },
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
                ) {
                    items(memories, key = { it.id }) { fact ->
                        MemoryItemCard(
                            fact = fact,
                            words = words,
                            onTogglePin = { viewModel.togglePin(fact) },
                            onEdit = { viewModel.setEditingMemory(fact) },
                            onForget = { viewModel.forget(fact.id) },
                        )
                    }
                }
            }
        }

        if (state.isAddDialogOpen) {
            AddEditMemoryBottomSheet(
                editingMemory = state.editingMemory,
                words = words,
                onDismiss = { viewModel.closeAddDialog() },
                onSave = { subject, factText, category ->
                    if (state.editingMemory != null) {
                        viewModel.updateFact(
                            state.editingMemory!!.copy(
                                subject = subject,
                                fact = factText,
                                category = category,
                            ),
                        )
                    } else {
                        viewModel.rememberFact(subject, factText, category)
                    }
                },
            )
        }
    }
}

@Composable
private fun MemoryItemCard(
    fact: MemoryFact,
    words: MemoryWording,
    onTogglePin: () -> Unit,
    onEdit: () -> Unit,
    onForget: () -> Unit,
) {
    MiraCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MiraTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = fact.subject,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    MiraIconButton(
                        icon = MiraIcons.PushPin,
                        contentDescription = words.pinMemory,
                        onClick = onTogglePin,
                        tint = if (fact.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MiraIconButton(
                        icon = MiraIcons.Edit,
                        contentDescription = words.editMemory,
                        onClick = onEdit,
                    )
                    MiraIconButton(
                        icon = MiraIcons.Delete,
                        contentDescription = words.forgetMemory,
                        onClick = onForget,
                    )
                }
            }

            Text(
                text = fact.fact,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(MiraTheme.spacing.xxs))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MiraChip(
                    text = words.categoryName(fact.category),
                    selected = true,
                )

                Text(
                    text = words.recallSummary(fact.source, fact.recallCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AddEditMemoryBottomSheet(
    editingMemory: MemoryFact?,
    words: MemoryWording,
    onDismiss: () -> Unit,
    onSave: (String, String, MemoryCategory) -> Unit,
) {
    var subject by remember { mutableStateOf(editingMemory?.subject ?: "") }
    var factText by remember { mutableStateOf(editingMemory?.fact ?: "") }
    var selectedCategory by remember { mutableStateOf(editingMemory?.category ?: MemoryCategory.GENERAL_FACT) }

    MiraBottomSheet(
        onDismissRequest = onDismiss,
        title = if (editingMemory == null) words.newFactTitle else words.editFactTitle,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
        ) {
            MiraTextField(
                value = subject,
                onValueChange = { subject = it },
                label = words.subjectLabel,
                placeholder = words.subjectPlaceholder,
            )

            MiraTextField(
                value = factText,
                onValueChange = { factText = it },
                label = words.factLabel,
                placeholder = words.factPlaceholder,
                singleLine = false,
                maxLines = 4,
            )

            Text(
                text = words.categoryLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val pickerScroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fadingHorizontalEdges(pickerScroll)
                    .horizontalScroll(pickerScroll),
                horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
            ) {
                MemoryCategory.entries.forEach { category ->
                    MiraChip(
                        text = words.categoryName(category),
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                    )
                }
            }

            Spacer(modifier = Modifier.height(MiraTheme.spacing.xs))

            MiraButton(
                text = if (editingMemory == null) words.storeMemory else words.saveChanges,
                onClick = {
                    if (subject.isNotBlank() && factText.isNotBlank()) {
                        onSave(subject, factText, selectedCategory)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
