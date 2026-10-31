package com.rayluxeo.aida.ui.activity

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.fadingHorizontalEdges
import com.rayluxeo.aida.core.design.component.MiraChip
import com.rayluxeo.aida.core.design.component.MiraEmptyState
import com.rayluxeo.aida.core.design.component.MiraIcons
import com.rayluxeo.aida.core.design.component.MiraListRow
import com.rayluxeo.aida.core.design.component.MiraListSkeleton
import com.rayluxeo.aida.core.design.component.MiraScaffold
import com.rayluxeo.aida.core.design.component.MiraTopBar
import com.rayluxeo.aida.domain.model.ActivityCategory

/**
 * A plain record of what Mira has done, newest first, narrowable to one category.
 *
 * Every row is read-only: an entry is evidence, and the thing it was about may since have been
 * deleted, so there is nowhere a tap could lead. An empty list — filtered or not — is a normal
 * state for a private assistant, so it is a calm line rather than a fault.
 */
@Composable
fun ActivityLogScreen(
    onBack: () -> Unit,
    viewModel: ActivityLogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val words = activityLogWording(state.language)
    val spacing = MiraTheme.spacing

    MiraScaffold(
        topBar = {
            MiraTopBar(
                title = words.screenTitle,
                onBack = onBack,
                backContentDescription = words.back,
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            val filterScroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fadingHorizontalEdges(filterScroll)
                    .horizontalScroll(filterScroll)
                    .padding(vertical = spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                MiraChip(
                    text = words.allFilter,
                    selected = state.selectedCategory == null,
                    onClick = { viewModel.selectCategory(null) },
                )
                ActivityCategory.entries.forEach { category ->
                    MiraChip(
                        text = words.categoryLabel(category),
                        selected = state.selectedCategory == category,
                        onClick = { viewModel.selectCategory(category) },
                    )
                }
            }

            when {
                state.isLoading && state.entries.isEmpty() -> MiraListSkeleton(
                    modifier = Modifier.weight(1f),
                )

                state.entries.isEmpty() -> Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    MiraEmptyState(
                        icon = MiraIcons.History,
                        message = words.empty,
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = spacing.xl),
                ) {
                    items(state.entries, key = { it.id }) { item ->
                        MiraListRow(
                            title = item.summary,
                            supporting = item.supporting,
                        )
                    }
                }
            }
        }
    }
}
