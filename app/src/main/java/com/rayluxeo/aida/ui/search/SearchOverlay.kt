package com.rayluxeo.aida.ui.search

import com.rayluxeo.aida.core.design.component.MiraIcons

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraCard
import com.rayluxeo.aida.core.design.component.MiraEmptyState
import com.rayluxeo.aida.core.design.component.MiraIconButton
import com.rayluxeo.aida.core.design.component.MiraLoadingIndicator
import kotlin.math.roundToInt

/** What TalkBack reads for the clear glyph — a fixed English word, like [androidx] layer defaults. */
private const val CLEAR_LABEL = "Clear"

/** How dark the overlay's own scrim goes at full reveal. The shell's blur already carries most of it. */
private const val ScrimMaxAlpha = 0.42f

/** The field's content (text, clear) fades in over the back half of the bloom, not from the first pixel. */
private const val CONTENT_FADE_START = 0.45f

/**
 * Search, as an overlay rather than a page.
 *
 * The circle on Home blooms into a full-width field over a blurred Home instead of pushing a new
 * screen — Abu asked for the field to "come in its full size from that circle". The shell blurs
 * everything behind this (the aurora and the nav bar included, which a per-screen blur could not
 * reach) and drives [progress] 0→1 as it opens, so the blur and the bloom move as one thing; this
 * composable owns only the scrim, the field growing out of [anchor], and the results.
 *
 * [anchor] is the search circle's bounds in root coordinates, so the field starts exactly over the
 * icon and settles into a search bar where that icon sat. The host keeps this mounted while
 * [progress] is non-zero so the field blooms shut again on dismiss rather than vanishing.
 */
@Composable
fun SearchOverlay(
    visible: Boolean,
    progress: Float,
    anchor: Rect,
    onDismiss: () -> Unit,
    onOpenTask: (Long) -> Unit,
    onOpenNote: (Long) -> Unit,
    onOpenClient: (Long) -> Unit,
    onOpenProject: (Long) -> Unit,
    onOpenInvoice: (Long) -> Unit,
    onOpenMemory: () -> Unit,
    onOpenMoney: () -> Unit,
    onOpenFiles: () -> Unit,
    viewModel: UniversalSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val words = universalSearchWording(state.language)
    val spacing = MiraTheme.spacing
    val density = LocalDensity.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // A fresh field every time it opens, and the keyboard rises with the bloom; dropping focus on
    // close lets the keyboard fall with it.
    LaunchedEffect(visible) {
        if (visible) {
            viewModel.updateQuery("")
            runCatching { focusRequester.requestFocus() }
        } else {
            focusManager.clearFocus()
        }
    }

    BackHandler(enabled = visible) { onDismiss() }

    val p = progress.coerceIn(0f, 1f)
    val contentAlpha = ((p - CONTENT_FADE_START) / (1f - CONTENT_FADE_START)).coerceIn(0f, 1f)

    val fieldHeightPx = with(density) { (spacing.xxl + spacing.xs).toPx() }
    val sidePadPx = with(density) { spacing.screenHorizontal.toPx() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rootWidthPx = constraints.maxWidth.toFloat()
        val endLeft = sidePadPx
        val endWidth = rootWidthPx - 2f * sidePadPx
        val endTop = anchor.center.y - fieldHeightPx / 2f

        // Linear interpolation on the already-eased [progress]; no second easing.
        val curLeft = anchor.left + (endLeft - anchor.left) * p
        val curTop = anchor.top + (endTop - anchor.top) * p
        val curWidth = anchor.width + (endWidth - anchor.width) * p
        val curHeight = anchor.height + (fieldHeightPx - anchor.height) * p

        // The scrim deepens the shell's blur and catches a tap anywhere outside the field.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = p }
                .background(MiraTheme.colors.scrim.copy(alpha = ScrimMaxAlpha))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        )

        // Results sit below where the field lands, and fade in with the field's content.
        if (state.query.isNotBlank()) {
            val resultsTop = with(density) { (endTop + fieldHeightPx + spacing.sm.toPx()).toDp() }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = resultsTop)
                    .padding(horizontal = spacing.screenHorizontal)
                    .graphicsLayer { alpha = contentAlpha }
                    .imePadding(),
            ) {
                SearchResultsBody(
                    state = state,
                    words = words,
                    onOpenTask = onOpenTask,
                    onOpenNote = onOpenNote,
                    onOpenClient = onOpenClient,
                    onOpenProject = onOpenProject,
                    onOpenInvoice = onOpenInvoice,
                    onOpenMemory = onOpenMemory,
                    onOpenMoney = onOpenMoney,
                    onOpenFiles = onOpenFiles,
                )
            }
        }

        SearchFieldMorph(
            leftPx = curLeft,
            topPx = curTop,
            widthPx = curWidth,
            heightPx = curHeight,
            query = state.query,
            onQueryChange = viewModel::updateQuery,
            placeholder = words.searchPlaceholder,
            contentAlpha = contentAlpha,
            focusRequester = focusRequester,
            onClear = { viewModel.updateQuery("") },
            onSearchAction = { focusManager.clearFocus() },
        )
    }
}

/**
 * The circle grown wide: a pill whose width, height and position are handed in each frame, so it
 * reads as the search icon stretching into a field. Its content — the glyph warming to the accent,
 * the text, the clear glyph — fades up over the back half of the bloom so the empty circle never
 * shows a caret.
 */
@Composable
private fun SearchFieldMorph(
    leftPx: Float,
    topPx: Float,
    widthPx: Float,
    heightPx: Float,
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    contentAlpha: Float,
    focusRequester: FocusRequester,
    onClear: () -> Unit,
    onSearchAction: () -> Unit,
) {
    val spacing = MiraTheme.spacing
    val density = LocalDensity.current
    val borderColor = lerp(
        MiraTheme.colors.hairlineStrong,
        MaterialTheme.colorScheme.primary,
        contentAlpha,
    )
    val glyphColor = lerp(
        MaterialTheme.colorScheme.onSurfaceVariant,
        MaterialTheme.colorScheme.primary,
        contentAlpha,
    )

    Row(
        modifier = Modifier
            .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
            .width(with(density) { widthPx.toDp() })
            .height(with(density) { heightPx.toDp() })
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(spacing.hairline, borderColor, CircleShape)
            .padding(start = spacing.md, end = spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = MiraIcons.Search,
            contentDescription = null,
            modifier = Modifier.size(spacing.lg),
            tint = glyphColor,
        )
        Spacer(Modifier.width(spacing.sm))
        Box(
            modifier = Modifier
                .weight(1f)
                .graphicsLayer { alpha = contentAlpha },
        ) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { onSearchAction() }),
                singleLine = true,
                maxLines = 1,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            )
        }
        if (query.isNotEmpty()) {
            MiraIconButton(
                icon = MiraIcons.Close,
                contentDescription = CLEAR_LABEL,
                onClick = onClear,
                modifier = Modifier.graphicsLayer { alpha = contentAlpha },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * What the overlay shows under the field: a working line while the search runs, a calm empty line
 * when nothing matched, or the grouped results. There is no "type to search" prompt — a blank field
 * is prompt enough, so the body is drawn only once there is a query.
 */
@Composable
private fun SearchResultsBody(
    state: UniversalSearchUiState,
    words: UniversalSearchWording,
    onOpenTask: (Long) -> Unit,
    onOpenNote: (Long) -> Unit,
    onOpenClient: (Long) -> Unit,
    onOpenProject: (Long) -> Unit,
    onOpenInvoice: (Long) -> Unit,
    onOpenMemory: () -> Unit,
    onOpenMoney: () -> Unit,
    onOpenFiles: () -> Unit,
) {
    val spacing = MiraTheme.spacing
    when {
        state.isSearching -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.xl),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    MiraLoadingIndicator()
                    Text(
                        text = words.searching,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        state.results != null -> {
            val res = state.results!!
            if (res.totalCount == 0) {
                MiraEmptyState(
                    icon = MiraIcons.Search,
                    message = words.noMatches(res.query),
                )
            } else {
                Text(
                    text = words.resultsFound(res.totalCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = spacing.sm),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    if (res.tasks.isNotEmpty()) {
                        item {
                            SearchSectionHeader(
                                words.sectionHeader(SearchResultKind.TASKS, res.tasks.size),
                                MiraIcons.CheckCircle,
                            )
                        }
                        items(res.tasks.size) { idx ->
                            val item = res.tasks[idx]
                            SearchResultCard(
                                title = item.title,
                                subtitle = item.description ?: words.taskFallback,
                                onClick = { onOpenTask(item.id) },
                            )
                        }
                    }

                    if (res.notes.isNotEmpty()) {
                        item {
                            SearchSectionHeader(
                                words.sectionHeader(SearchResultKind.NOTES, res.notes.size),
                                MiraIcons.Description,
                            )
                        }
                        items(res.notes.size) { idx ->
                            val item = res.notes[idx]
                            SearchResultCard(
                                title = item.title ?: words.noteTitleFallback(item.id),
                                subtitle = item.body,
                                onClick = { onOpenNote(item.id) },
                            )
                        }
                    }

                    if (res.clients.isNotEmpty()) {
                        item {
                            SearchSectionHeader(
                                words.sectionHeader(SearchResultKind.CLIENTS, res.clients.size),
                                MiraIcons.Groups,
                            )
                        }
                        items(res.clients.size) { idx ->
                            val item = res.clients[idx]
                            SearchResultCard(
                                title = item.name,
                                subtitle = item.companyName ?: item.email ?: words.clientFallback,
                                onClick = { onOpenClient(item.id) },
                            )
                        }
                    }

                    if (res.projects.isNotEmpty()) {
                        item {
                            SearchSectionHeader(
                                words.sectionHeader(SearchResultKind.PROJECTS, res.projects.size),
                                MiraIcons.WorkOutline,
                            )
                        }
                        items(res.projects.size) { idx ->
                            val item = res.projects[idx]
                            SearchResultCard(
                                title = item.name,
                                subtitle = item.description ?: words.projectFallback,
                                onClick = { onOpenProject(item.id) },
                            )
                        }
                    }

                    if (res.invoices.isNotEmpty()) {
                        item {
                            SearchSectionHeader(
                                words.sectionHeader(SearchResultKind.INVOICES, res.invoices.size),
                                MiraIcons.ReceiptLong,
                            )
                        }
                        items(res.invoices.size) { idx ->
                            val item = res.invoices[idx]
                            SearchResultCard(
                                title = words.invoiceTitle(item.number),
                                subtitle = words.invoiceSubtitle(item.total),
                                onClick = { onOpenInvoice(item.id) },
                            )
                        }
                    }

                    if (res.expenses.isNotEmpty()) {
                        item {
                            SearchSectionHeader(
                                words.sectionHeader(SearchResultKind.EXPENSES, res.expenses.size),
                                MiraIcons.AccountBalanceWallet,
                            )
                        }
                        items(res.expenses.size) { idx ->
                            val item = res.expenses[idx]
                            SearchResultCard(
                                title = item.description,
                                subtitle = words.expenseSubtitle(item.amount, item.category),
                                onClick = onOpenMoney,
                            )
                        }
                    }

                    if (res.memories.isNotEmpty()) {
                        item {
                            SearchSectionHeader(
                                words.sectionHeader(SearchResultKind.MEMORIES, res.memories.size),
                                MiraIcons.Psychology,
                            )
                        }
                        items(res.memories.size) { idx ->
                            val item = res.memories[idx]
                            SearchResultCard(
                                title = item.subject,
                                subtitle = item.fact,
                                onClick = { onOpenMemory() },
                            )
                        }
                    }

                    if (res.files.isNotEmpty()) {
                        item {
                            SearchSectionHeader(
                                words.sectionHeader(SearchResultKind.FILES, res.files.size),
                                MiraIcons.InsertDriveFile,
                            )
                        }
                        items(res.files.size) { idx ->
                            val item = res.files[idx]
                            SearchResultCard(
                                title = item.displayName,
                                subtitle = item.extractedText ?: words.fileFallback,
                                onClick = { onOpenFiles() },
                            )
                        }
                    }

                    if (res.conversations.isNotEmpty()) {
                        item {
                            SearchSectionHeader(
                                words.sectionHeader(SearchResultKind.CONVERSATIONS, res.conversations.size),
                                MiraIcons.Message,
                            )
                        }
                        items(res.conversations.size) { idx ->
                            val item = res.conversations[idx]
                            SearchResultCard(
                                // A matched turn is shown but not tappable — there is no per-turn
                                // destination, so it does not pretend to be a link.
                                title = words.conversationSpeaker(item.role),
                                subtitle = item.text,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
        modifier = Modifier.padding(top = MiraTheme.spacing.xs),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SearchResultCard(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
) {
    MiraCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MiraTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xxs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle.replace("\n", " "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
