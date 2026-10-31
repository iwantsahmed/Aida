package com.rayluxeo.aida.ui.mira

import com.rayluxeo.aida.core.design.component.MiraIcons

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.rayluxeo.aida.core.design.MiraPalette
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraBottomSheet
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraButtonStyle
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.design.component.MiraSearchField
import com.rayluxeo.aida.domain.model.Conversation
import com.rayluxeo.aida.domain.model.ConversationSearchHit
import com.rayluxeo.aida.domain.model.ConversationTurn
import com.rayluxeo.aida.domain.model.TurnRole
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Composable
fun ConversationHistorySheet(
    threads: List<Conversation>,
    language: SpokenLanguage,
    onDismiss: () -> Unit,
    onStartNewConversation: () -> Unit,
    onDeleteConversation: (Long) -> Unit,
    onDeleteAllConversations: () -> Unit,
    onSearch: suspend (String) -> List<ConversationSearchHit>,
    onObserveThreadMessages: (Long) -> Flow<List<ConversationTurn>>,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    val words = conversationHistoryWording(language)
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var searchHits by remember { mutableStateOf<List<ConversationSearchHit>?>(null) }
    // Which past thread is open for reading, if any. The list is the resting state; a tap on a
    // thread or a search hit opens it, and the back control returns here.
    var openThread by remember { mutableStateOf<OpenThread?>(null) }

    MiraBottomSheet(
        onDismissRequest = {
            openThread = null
            onDismiss()
        },
        title = if (openThread == null) words.title else null,
        modifier = modifier,
    ) {
        val reading = openThread
        if (reading != null) {
            ThreadDetail(
                thread = reading,
                words = words,
                messages = remember(reading.id) { onObserveThreadMessages(reading.id) }
                    .collectAsStateWithLifecycle(emptyList()).value,
                onBack = { openThread = null },
            )
            return@MiraBottomSheet
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                MiraSearchField(
                    value = searchQuery,
                    onValueChange = { newQuery ->
                        searchQuery = newQuery
                        if (newQuery.isBlank()) {
                            searchHits = null
                        } else {
                            scope.launch {
                                searchHits = onSearch(newQuery)
                            }
                        }
                    },
                    onClear = {
                        searchQuery = ""
                        searchHits = null
                    },
                    placeholder = words.searchPlaceholder,
                    modifier = Modifier.weight(1f),
                )

                MiraButton(
                    text = words.newConversation,
                    onClick = {
                        onStartNewConversation()
                        onDismiss()
                    },
                    leadingIcon = MiraIcons.Add,
                    style = MiraButtonStyle.PRIMARY,
                )
            }

            Spacer(Modifier.height(spacing.md))

            if (searchHits != null) {
                Text(
                    text = words.searchResults(searchHits!!.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = spacing.xs),
                )
                if (searchHits!!.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = spacing.xl),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = words.noMatches,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        verticalArrangement = Arrangement.spacedBy(spacing.xs),
                    ) {
                        items(searchHits!!) { hit ->
                            SearchHitRow(
                                hit = hit,
                                words = words,
                                onOpen = {
                                    openThread = OpenThread(
                                        hit.conversationId,
                                        threads.find { it.id == hit.conversationId }?.title,
                                    )
                                },
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = words.threads(threads.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = spacing.xs),
                )
                if (threads.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = spacing.xl),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = words.noConversations,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        verticalArrangement = Arrangement.spacedBy(spacing.xs),
                    ) {
                        items(threads) { thread ->
                            ConversationThreadRow(
                                thread = thread,
                                words = words,
                                onOpen = { openThread = OpenThread(thread.id, thread.title) },
                                onDelete = { onDeleteConversation(thread.id) },
                            )
                        }
                    }
                }
            }

            if (threads.isNotEmpty()) {
                Spacer(Modifier.height(spacing.md))
                MiraButton(
                    text = words.deleteAll,
                    onClick = {
                        onDeleteAllConversations()
                        onDismiss()
                    },
                    style = MiraButtonStyle.SECONDARY,
                    accent = MiraTheme.colors.danger,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ConversationThreadRow(
    thread: Conversation,
    words: ConversationHistoryWording,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MiraPalette.Graphite)
            .clickable(onClickLabel = words.openConversation, onClick = onOpen)
            .padding(horizontal = spacing.sm, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = MiraIcons.Forum,
                contentDescription = null,
                tint = MiraPalette.Accent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(spacing.sm))
            Column {
                Text(
                    text = thread.title?.takeIf { it.isNotBlank() } ?: words.untitledConversation(thread.id),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = words.messageCount(thread.messageCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        IconButton(onClick = onDelete) {
            Icon(
                imageVector = MiraIcons.DeleteOutline,
                contentDescription = words.delete,
                tint = MiraPalette.Muted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SearchHitRow(
    hit: ConversationSearchHit,
    words: ConversationHistoryWording,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MiraPalette.Graphite)
            .clickable(onClickLabel = words.openConversation, onClick = onOpen)
            .padding(spacing.sm),
    ) {
        Text(
            text = hit.turn.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = words.threadReference(hit.conversationId),
            style = MaterialTheme.typography.labelSmall,
            color = MiraPalette.Accent,
            modifier = Modifier.padding(top = spacing.xxs),
        )
    }
}

/** A past thread opened for reading: just the id, and whatever name it is known by. */
private data class OpenThread(val id: Long, val title: String?)

/**
 * One past thread, read-only. It shows what was said, not a place to say more — resuming a thread
 * as the live conversation would mean scoping the transcript to a thread, which the two-port design
 * deliberately avoids (`ConversationThreadRepository`). Reading it back is the whole feature.
 */
@Composable
private fun ThreadDetail(
    thread: OpenThread,
    words: ConversationHistoryWording,
    messages: List<ConversationTurn>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = MiraIcons.ArrowBack,
                    contentDescription = words.back,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.width(spacing.xs))
            Text(
                text = thread.title?.takeIf { it.isNotBlank() } ?: words.untitledConversation(thread.id),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(spacing.sm))

        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.xl),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = words.emptyThread,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                items(messages, key = { it.id }) { turn ->
                    ThreadMessageRow(turn = turn)
                }
            }
        }
    }
}

/** One message in the read-back view: Abu or Mira, told apart the way the live transcript does. */
@Composable
private fun ThreadMessageRow(
    turn: ConversationTurn,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    val fromAbu = turn.role == TurnRole.ABU
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MiraPalette.Graphite)
            .padding(spacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(spacing.lg)
                .clip(CircleShape)
                .background(
                    if (fromAbu) MaterialTheme.colorScheme.surfaceVariant else MiraTheme.colors.accentWash,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (fromAbu) MiraIcons.Person else MiraIcons.TripOrigin,
                contentDescription = null,
                tint = if (fromAbu) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(spacing.md),
            )
        }
        Spacer(Modifier.width(spacing.sm))
        Text(
            text = turn.text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (fromAbu) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
