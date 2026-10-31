package com.rayluxeo.aida.ui.home

import com.rayluxeo.aida.core.design.component.MiraIcons

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraAvatar
import com.rayluxeo.aida.core.design.component.MiraAvatarState
import com.rayluxeo.aida.core.design.component.MiraCard
import com.rayluxeo.aida.core.design.component.MiraChip
import com.rayluxeo.aida.core.design.component.MiraDivider
import com.rayluxeo.aida.core.design.component.MiraEmptyState
import com.rayluxeo.aida.core.design.component.MiraInlineNotice
import com.rayluxeo.aida.core.design.component.MiraListRow
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.core.design.component.MiraScaffold
import com.rayluxeo.aida.core.design.component.MiraSectionHeader
import com.rayluxeo.aida.core.design.component.MiraSkeletonBox
import com.rayluxeo.aida.core.design.component.MiraSkeletonLine
import com.rayluxeo.aida.core.design.component.rememberMiraShimmer
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.design.miraRaised
import com.rayluxeo.aida.core.design.rememberMiraHaptics
import com.rayluxeo.aida.domain.attention.AttentionAction
import com.rayluxeo.aida.domain.brief.AttentionSeverity
import com.rayluxeo.aida.domain.brief.AttentionTarget
import com.rayluxeo.aida.ui.mira.speechFailureWording
import com.rayluxeo.aida.ui.navigation.MiraArea
import com.rayluxeo.aida.ui.navigation.shellWording
import com.rayluxeo.aida.voice.MiraVoiceState
import com.rayluxeo.aida.voice.SpeechFailure
import com.rayluxeo.aida.voice.toAvatarState

/**
 * Mira's front page, and the one screen that is hers rather than a list's.
 *
 * It opens the way a person would: the date, then Mira herself as a band of living light, then a
 * word to Abu and a single sentence of where his day stands. Only then does it show work — the one
 * or two things that actually want him, and today's short list — and it never grows a dashboard of
 * equal cards beneath them. A section with nothing to say is left out, because a row that reads
 * "0" every morning is one Abu stops seeing.
 *
 * Two gestures carry the screen. Tapping the wave asks Mira to read the day aloud; the ask, pinned
 * to the foot, is a single round button until he reaches for it. Everything else is quiet: one
 * accent, hairline rules, and air where a lesser screen would put a border.
 */
@Composable
fun HomeScreen(
    onOpenMira: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenSearch: (Rect) -> Unit,
    onOpenSettings: () -> Unit,
    /** Where a focus row or an area tile goes — straight to the area's own screen. */
    onOpenArea: (MiraArea) -> Unit,
    /** Where the Salah card leads. */
    onOpenSalah: () -> Unit,
    /** Where the Quran card leads. */
    onOpenQuran: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // The brief is a snapshot over a dozen tables, so it is re-read when Abu comes back to Home
    // rather than polled. The live half of the screen — the tasks and the counts — needs no help.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }

    HomeContent(
        state = state,
        onCompleteTask = viewModel::onCompleteTask,
        onDismissNotice = viewModel::onDismissNotice,
        onReadBrief = viewModel::onReadBrief,
        onOpenTasks = onOpenTasks,
        onOpenArea = onOpenArea,
        onOpenSalah = onOpenSalah,
        onOpenQuran = onOpenQuran,
        onOpenMira = onOpenMira,
        onOpenSearch = onOpenSearch,
        onOpenSettings = onOpenSettings,
        onAttentionRemindMe = viewModel::onAttentionRemindMe,
        onAttentionMarkDone = viewModel::onAttentionMarkDone,
        onAttentionDismiss = viewModel::onAttentionDismiss,
        modifier = modifier,
    )
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onCompleteTask: (Long) -> Unit,
    onDismissNotice: () -> Unit,
    onReadBrief: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenArea: (MiraArea) -> Unit,
    onOpenSalah: () -> Unit,
    onOpenQuran: () -> Unit,
    onOpenMira: () -> Unit,
    onOpenSearch: (Rect) -> Unit,
    onOpenSettings: () -> Unit,
    onAttentionRemindMe: (HomeAttentionRow) -> Unit,
    onAttentionMarkDone: (HomeAttentionRow) -> Unit,
    onAttentionDismiss: (HomeAttentionRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberMiraHaptics()
    val spacing = MiraTheme.spacing

    LaunchedEffect(state.notice) {
        if (state.notice?.isProblem == true) haptics.error()
    }

    MiraScaffold(
        modifier = modifier,
        // Transparent so the shell's aurora reads through — behind the day AND behind the
        // translucent nav bar. The shell owns the glow now (it replaced the old ice-blue top wash
        // the Column used to carry); this is the presence Abu asked for (16 Oct 2026).
        transparent = true,
    ) { contentPadding ->
        if (state.isLoading) {
            HomeSkeleton(contentPadding = contentPadding)
        } else {
            val wording = homeWording(state.language)
            val direction = LocalLayoutDirection.current
            val start = contentPadding.calculateStartPadding(direction)
            val end = contentPadding.calculateEndPadding(direction)
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                HomeList(
                    wording = wording,
                    state = state,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = start,
                        end = end,
                        top = contentPadding.calculateTopPadding(),
                        bottom = contentPadding.calculateBottomPadding() + spacing.sm,
                    ),
                    onCompleteTask = onCompleteTask,
                    onDismissNotice = onDismissNotice,
                    onReadBrief = onReadBrief,
                    onOpenMira = onOpenMira,
                    onOpenTasks = onOpenTasks,
                    onOpenArea = onOpenArea,
                    onOpenSalah = onOpenSalah,
                    onOpenQuran = onOpenQuran,
                    onOpenSearch = onOpenSearch,
                    onOpenSettings = onOpenSettings,
                    onAttentionRemindMe = onAttentionRemindMe,
                    onAttentionMarkDone = onAttentionMarkDone,
                    onAttentionDismiss = onAttentionDismiss,
                )
            }
        }
    }
}

@Composable
private fun HomeList(
    wording: HomeWording,
    state: HomeUiState,
    contentPadding: PaddingValues,
    onCompleteTask: (Long) -> Unit,
    onDismissNotice: () -> Unit,
    onReadBrief: () -> Unit,
    onOpenMira: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenArea: (MiraArea) -> Unit,
    onOpenSalah: () -> Unit,
    onOpenQuran: () -> Unit,
    onOpenSearch: (Rect) -> Unit,
    onOpenSettings: () -> Unit,
    onAttentionRemindMe: (HomeAttentionRow) -> Unit,
    onAttentionMarkDone: (HomeAttentionRow) -> Unit,
    onAttentionDismiss: (HomeAttentionRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        item {
            HomeTopMinimal(
                dateLine = state.dateLine,
                searchLabel = wording.search,
                settingsLabel = wording.settings,
                onSearch = onOpenSearch,
                onSettings = onOpenSettings,
                modifier = Modifier.padding(top = spacing.md),
            )
        }
        item {
            // Mira herself. Tapping the mark reads the day aloud, but the face is driven by the
            // whole voice turn: it comes alive with the microphone the moment Abu presses the voice
            // button below, and again while she speaks — one presence answering one session.
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                MiraAvatar(
                    state = if (state.isSpeaking) MiraAvatarState.SPEAKING else state.voice.state.toAvatarState(),
                    amplitude = state.voice.amplitude,
                    onClick = onReadBrief,
                    contentDescription = if (state.isSpeaking) wording.stopReading else wording.readBrief,
                    size = spacing.xxl * 2,
                )
            }
        }
        item {
            // While a spoken turn is under way, the glance line gives way to what she is doing with
            // it — heard back as a partial transcript, or named in one word — and takes the accent.
            val voiceLine: String? = when (state.voice.state) {
                MiraVoiceState.LISTENING ->
                    state.voice.partialTranscript?.takeIf { it.isNotBlank() } ?: wording.listening
                MiraVoiceState.PROCESSING, MiraVoiceState.EXECUTING -> wording.working
                MiraVoiceState.ERROR ->
                    speechFailureWording(state.voice.lastFailure ?: SpeechFailure.UNKNOWN, state.language)
                else -> null
            }
            HomeGreeting(
                greeting = state.greeting,
                status = voiceLine
                    ?: wording.homeGlance(state.attention.size, state.summary?.dueToday ?: 0),
                statusAccent = voiceLine != null && state.voice.state != MiraVoiceState.ERROR,
                modifier = Modifier.padding(top = spacing.xs),
            )
        }

        state.notice?.let { notice ->
            item {
                MiraInlineNotice(
                    text = notice.text,
                    modifier = Modifier.padding(top = spacing.lg),
                    tone = if (notice.isProblem) MiraNoticeTone.DANGER else MiraNoticeTone.INFO,
                    dismissContentDescription = wording.dismiss,
                    onDismiss = onDismissNotice,
                    actionText = notice.action?.let { wording.openSettings },
                    onAction = notice.action?.let { onOpenSettings },
                )
            }
        }

        // A withheld calendar is an absence, not an empty day: say so on the face of Home too, not
        // only in the spoken brief, so a clear-looking day that Mira was never allowed to read does
        // not pass for a real one.
        if (state.calendarUnreadable) {
            item {
                MiraInlineNotice(
                    text = wording.calendarUnreadable,
                    modifier = Modifier.padding(top = spacing.sm),
                    tone = MiraNoticeTone.INFO,
                )
            }
        }

        // The items that require Abu's attention.
        if (state.attention.isNotEmpty()) {
            item {
                MiraSectionHeader(
                    text = wording.needsAttention,
                    modifier = Modifier.padding(top = spacing.lg),
                )
            }
            item {
                Column(
                    modifier = Modifier.padding(top = spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    state.attention.forEach { row ->
                        HomeFocusCard(
                            row = row,
                            wording = wording,
                            onOpen = {
                                openAttention(
                                    target = row.target,
                                    onOpenTasks = onOpenTasks,
                                    onOpenArea = onOpenArea,
                                    onOpenMira = onOpenMira,
                                )
                            },
                            onRemindMe = { onAttentionRemindMe(row) },
                            onMarkDone = { onAttentionMarkDone(row) },
                            onDismiss = { onAttentionDismiss(row) },
                        )
                    }
                }
            }
        }


        // The next prayer and today's five. This leads the day's cards — the next prayer is the
        // most time-bound thing on the screen, and it is the one piece Abu wants at a glance before
        // the work. Present only when Abu has set a prayer location, so there is never an empty or
        // fabricated Salah card — it simply is not here until then.
        state.salah?.let { salah ->
            item {
                MiraSectionHeader(
                    text = wording.salah,
                    modifier = Modifier.padding(top = spacing.lg),
                )
            }
            item {
                HomeSalahCard(
                    salah = salah,
                    wording = wording,
                    onOpen = onOpenSalah,
                    modifier = Modifier.padding(top = spacing.xs),
                )
            }
        } ?: state.hadith?.let { hadith ->
            item {
                MiraSectionHeader(
                    text = wording.salah,
                    modifier = Modifier.padding(top = spacing.lg),
                )
            }
            item {
                HomeHadithCard(
                    hadith = hadith,
                    onOpen = onOpenSalah,
                    modifier = Modifier.padding(top = spacing.xs),
                )
            }
        }

        // Quran daily recitation progress & target (at least 2 pages / day).
        state.quran?.let { quran ->
            item {
                HomeQuranCard(
                    quran = quran,
                    onOpen = onOpenQuran,
                    modifier = Modifier.padding(top = spacing.sm),
                )
            }
        }

        // The day in four numbers. Present only when there is a figure worth showing — the view
        // model drops it when all four are nought, so this is never a row of zeroes above an empty
        // list.
        state.summary?.let { summary ->
            item {
                HomeSummaryCard(
                    summary = summary,
                    wording = wording,
                    onOpen = onOpenTasks,
                    modifier = Modifier.padding(top = spacing.lg),
                )
            }
        }

        todaySection(
            wording = wording,
            state = state,
            onCompleteTask = onCompleteTask,
            onOpenTasks = onOpenTasks,
        )

        moneySection(
            wording = wording,
            money = state.money,
            onOpenMoney = { onOpenArea(MiraArea.MONEY) },
        )

        deadlinesSection(wording = wording, deadlines = state.deadlines)

        followUpsSection(wording = wording, followUps = state.followUps)

        // The areas grid is Home's map of the whole app, so it sits at the FOOT, not the top: the
        // bottom nav already carries primary navigation, which lets the glanceable content — prayer,
        // the day, the money — lead, with the full index of areas waiting below for when Abu goes
        // looking for one.
        homeAreasSection(
            wording = wording,
            language = state.language,
            onOpenArea = onOpenArea,
        )

        item { Spacer(Modifier.height(spacing.xl)) }
    }
}

/** Today: the diary's next entry, the short list, and any reminders standing on the day. */
private fun LazyListScope.todaySection(
    wording: HomeWording,
    state: HomeUiState,
    onCompleteTask: (Long) -> Unit,
    onOpenTasks: () -> Unit,
) {
    val hasToday = state.nextEvent != null || state.tasks.isNotEmpty() || state.isEmpty
    if (hasToday) {
        item {
            MiraSectionHeader(
                text = when (state.section) {
                    HomeSection.TODAY -> wording.today
                    HomeSection.OPEN -> wording.open
                },
                modifier = Modifier.padding(top = MiraTheme.spacing.lg),
            )
        }
        state.nextEvent?.let { event ->
            item { HomeEventRow(event = event, onOpen = onOpenTasks) }
        }
        items(state.tasks, key = { "task-${it.id}" }) { task ->
            HomeTaskRow(
                task = task,
                onComplete = { onCompleteTask(task.id) },
                onOpen = onOpenTasks,
            )
        }
        // Home shows only the first few; the rest are a tap away rather than silently dropped.
        if (state.moreTaskCount > 0) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(
                            onClickLabel = wording.moreOpen(state.moreTaskCount),
                            onClick = onOpenTasks,
                        )
                        .padding(
                            horizontal = MiraTheme.spacing.md,
                            vertical = MiraTheme.spacing.sm,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = wording.moreOpen(state.moreTaskCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = MiraIcons.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(MiraTheme.spacing.md),
                    )
                }
            }
        }
        // A quiet day is a real state, not a fault: the message sits centred in the room it owns.
        if (state.isEmpty) {
            item { MiraEmptyState(icon = MiraIcons.TaskAlt, message = wording.nothingOpen) }
        }
    }

    if (state.reminders.isNotEmpty()) {
        item {
            MiraSectionHeader(
                text = wording.reminders,
                modifier = Modifier.padding(top = MiraTheme.spacing.lg),
            )
        }
        items(state.reminders, key = { "reminder-${it.id}" }) { reminder ->
            FactRow(
                title = reminder.title,
                supporting = reminder.whenLabel,
                icon = MiraIcons.Alarm,
            )
        }
    }
}

/**
 * The placeholder Home shows on cold start — shaped like the greeting and the areas grid that are
 * about to land, so the screen settles into place rather than popping in from a spinner. It draws
 * over the shell's aurora the same way the real Home does.
 */
@Composable
private fun HomeSkeleton(contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    val spacing = MiraTheme.spacing
    val brush = rememberMiraShimmer()
    val tileShape = MaterialTheme.shapes.large
    // Mirrors the real order: the date and the greeting, then the day's cards leading, and the areas
    // grid at the foot — so the skeleton settles into the loaded screen rather than jumping shape.
    val cardShape = MaterialTheme.shapes.large
    val cardHeight = spacing.xxl + spacing.xl
    Column(
        modifier = modifier.fillMaxSize().padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(spacing.md))
        MiraSkeletonLine(brush = brush, modifier = Modifier.align(Alignment.Start), widthFraction = 0.42f)
        Spacer(Modifier.height(spacing.xl))
        MiraSkeletonBox(brush = brush, modifier = Modifier.size(spacing.xxl + spacing.lg), shape = CircleShape)
        Spacer(Modifier.height(spacing.lg))
        MiraSkeletonLine(brush = brush, widthFraction = 0.58f, height = spacing.lg)
        Spacer(Modifier.height(spacing.sm))
        MiraSkeletonLine(brush = brush, widthFraction = 0.4f)
        Spacer(Modifier.height(spacing.xl))
        // Two leading cards — the prayer, the day, the money.
        repeat(HOME_SKELETON_CARDS) { card ->
            if (card > 0) Spacer(Modifier.height(spacing.lg))
            MiraSkeletonLine(brush = brush, modifier = Modifier.align(Alignment.Start), widthFraction = 0.22f)
            Spacer(Modifier.height(spacing.sm))
            MiraSkeletonBox(
                brush = brush,
                modifier = Modifier.fillMaxWidth().height(cardHeight),
                shape = cardShape,
            )
        }
        Spacer(Modifier.height(spacing.xl))
        // The areas grid, at the foot where it now lives.
        MiraSkeletonLine(brush = brush, modifier = Modifier.align(Alignment.Start), widthFraction = 0.22f)
        Spacer(Modifier.height(spacing.lg))
        repeat(HOME_SKELETON_GRID_ROWS) { row ->
            if (row > 0) Spacer(Modifier.height(spacing.lg))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                repeat(HOME_AREAS_PER_ROW) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        MiraSkeletonBox(
                            brush = brush,
                            modifier = Modifier.size(spacing.xxl + spacing.md),
                            shape = tileShape,
                        )
                        Spacer(Modifier.height(spacing.xs))
                        MiraSkeletonLine(brush = brush, widthFraction = 0.7f, height = spacing.sm)
                    }
                }
            }
        }
    }
}

private const val HOME_SKELETON_CARDS = 2
private const val HOME_SKELETON_GRID_ROWS = 2
private const val HOME_AREAS_PER_ROW = 4

/** What Abu is owed and what is overdue, the invoices due soon, and the payments just in. */
private fun LazyListScope.moneySection(
    wording: HomeWording,
    money: HomeMoney?,
    onOpenMoney: () -> Unit,
) {
    money ?: return
    item {
        MiraSectionHeader(
            text = wording.money,
            modifier = Modifier.padding(top = MiraTheme.spacing.lg),
        )
    }
    // The headline figures only when there is an amount to headline; a section of only due-soon
    // rows skips the empty card rather than drawing a box with nothing in it.
    if (money.outstanding != null || money.overdue != null) {
        item {
            HomeMoneyCard(
                money = money,
                wording = wording,
                onOpen = onOpenMoney,
                modifier = Modifier.padding(top = MiraTheme.spacing.xs),
            )
        }
    }
    items(money.dueSoon, key = { "invoice-${it.id}" }) { invoice ->
        FactRow(
            title = invoice.title,
            supporting = invoice.detail,
            icon = MiraIcons.ReceiptLong,
            tint = if (invoice.isOverdue) MiraTheme.colors.danger else null,
        )
    }
    items(money.recentPayments, key = { "payment-${it.id}" }) { payment ->
        FactRow(title = payment.title, supporting = payment.detail, icon = MiraIcons.Paid)
    }
}

/** The headline of the money section: what is outstanding, and overdue beneath it when there is any. */
@Composable
private fun HomeMoneyCard(
    money: HomeMoney,
    wording: HomeWording,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    MiraCard(onClick = onOpen, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            money.outstanding?.let { amount ->
                MoneyLine(label = wording.outstanding, amount = amount, detail = money.outstandingDetail)
            }
            money.overdue?.let { amount ->
                MoneyLine(
                    label = wording.overdueMoney,
                    amount = amount,
                    detail = money.overdueDetail,
                    tint = MiraTheme.colors.danger,
                )
            }
        }
    }
}

/** One money figure: its label over the amount, with an optional invoice count on the right. */
@Composable
private fun MoneyLine(
    label: String,
    amount: String,
    detail: String?,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = amount,
                style = MaterialTheme.typography.titleMedium,
                color = tint ?: MaterialTheme.colorScheme.onBackground,
            )
        }
        detail?.let {
            Spacer(Modifier.width(MiraTheme.spacing.sm))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Project deadlines the brief raised, each already worded; a quiet row, not a card. */
private fun LazyListScope.deadlinesSection(
    wording: HomeWording,
    deadlines: List<HomeDeadline>,
) {
    if (deadlines.isEmpty()) return
    item {
        MiraSectionHeader(
            text = wording.deadlines,
            modifier = Modifier.padding(top = MiraTheme.spacing.lg),
        )
    }
    items(deadlines, key = { it.key }) { deadline ->
        FactRow(title = deadline.title, supporting = deadline.detail, icon = MiraIcons.Schedule)
    }
}

/** Clients who have gone quiet long enough to be worth a nudge. */
private fun LazyListScope.followUpsSection(
    wording: HomeWording,
    followUps: List<HomeFollowUp>,
) {
    if (followUps.isEmpty()) return
    item {
        MiraSectionHeader(
            text = wording.followUps,
            modifier = Modifier.padding(top = MiraTheme.spacing.lg),
        )
    }
    items(followUps, key = { "followup-${it.id}" }) { followUp ->
        FactRow(title = followUp.title, supporting = followUp.detail, icon = MiraIcons.Groups)
    }
}

/** The day in four numbers — still open, due today, overdue, done today — a tap from the Tasks screen. */
@Composable
private fun HomeSummaryCard(
    summary: HomeSummary,
    wording: HomeWording,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    MiraCard(onClick = onOpen, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            HomeStat(
                value = wording.number(summary.open),
                label = wording.openCount,
                modifier = Modifier.weight(1f),
            )
            HomeStat(
                value = wording.number(summary.dueToday),
                label = wording.dueTodayCount,
                modifier = Modifier.weight(1f),
            )
            HomeStat(
                value = wording.number(summary.overdue),
                label = wording.overdueCount,
                tint = if (summary.overdue > 0) MiraTheme.colors.danger else null,
                modifier = Modifier.weight(1f),
            )
            HomeStat(
                value = wording.number(summary.completedToday),
                label = wording.doneTodayCount,
                tint = if (summary.completedToday > 0) MiraTheme.colors.positive else null,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** One figure in [HomeSummaryCard]: the number, then its quiet label beneath, centred. */
@Composable
private fun HomeStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = tint ?: MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = MiraTheme.spacing.xxs / 2),
        )
    }
}

/** The band across the very top: the date, and the ways off the page. */
@Composable
private fun HomeTopMinimal(
    dateLine: String,
    searchLabel: String,
    settingsLabel: String,
    onSearch: (Rect) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    // The search circle reports its own bounds in root coordinates, so the overlay's field can bloom
    // out of exactly where the icon sits rather than from an assumed corner.
    var searchBounds by remember { mutableStateOf(Rect.Zero) }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dateLine.isNotEmpty()) {
            Text(
                text = dateLine.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        // Search across everything she holds, then the gear. Two quiet circles, not a row of chrome.
        // The areas each have a tile in Home's own grid below, so a top-bar door to a second hub
        // listing the same areas was redundant and was dropped (Abu, 16 Oct 2026).
        TopCircleButton(
            icon = MiraIcons.Search,
            label = searchLabel,
            onClick = { onSearch(searchBounds) },
            modifier = Modifier.onGloballyPositioned { searchBounds = it.boundsInRoot() },
        )
        Spacer(Modifier.width(spacing.xs))
        TopCircleButton(icon = MiraIcons.Settings, label = settingsLabel, onClick = onSettings)
    }
}

/** The gear, in its own composable only so the top bar reads as date-then-gear. */
@Composable
private fun TopCircleButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    val haptics = rememberMiraHaptics()
    Box(
        modifier = modifier
            .size(spacing.xxl - spacing.xxs)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(spacing.hairline, MiraTheme.colors.hairline, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = label,
            ) {
                haptics.tap()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(spacing.md + spacing.xxs),
        )
    }
}

/** Which areas the Home launcher shows: the built ones, less Settings (the gear already opens it). */
private val HomeLauncherAreas: List<MiraArea> =
    MiraArea.hubOrder.filter { it.isBuilt && it != MiraArea.SETTINGS }

/**
 * A quick-launch grid of the areas, four to a row, the way a Bangladeshi super-app puts its
 * services on its front page — so everything off the four-tab bottom bar is one tap from Home
 * rather than two behind the hub. Each tile opens through the same [onOpenArea] the hub uses, so a
 * tile and a hub row land on the same screen. Area names come from the shell's own phrasebook
 * rather than a second copy on Home (§7.12).
 */
private fun LazyListScope.homeAreasSection(
    wording: HomeWording,
    language: SpokenLanguage,
    onOpenArea: (MiraArea) -> Unit,
) {
    item(key = "areas-grid") {
        val spacing = MiraTheme.spacing
        val words = shellWording(language)
        Column(modifier = Modifier.padding(top = spacing.lg)) {
            MiraSectionHeader(text = wording.areas)
            Spacer(Modifier.height(spacing.sm))
            HomeLauncherAreas.chunked(AREA_GRID_COLUMNS).forEach { rowAreas ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    rowAreas.forEach { area ->
                        AreaTile(
                            label = words.areaTitle(area),
                            iconRes = area.iconRes,
                            onClick = { onOpenArea(area) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Pad a short final row so its tiles keep their column rather than spreading.
                    repeat(AREA_GRID_COLUMNS - rowAreas.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(spacing.sm))
            }
        }
    }
}

/** One area in the Home launcher: its glyph on a raised tile, with the area's name beneath. */
@Composable
private fun AreaTile(
    label: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    val haptics = rememberMiraHaptics()
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .clip(shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = MaterialTheme.colorScheme.primary),
                role = Role.Button,
                onClickLabel = label,
            ) {
                haptics.tap()
                onClick()
            }
            .padding(vertical = spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(spacing.xxl + spacing.sm)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .miraRaised(shape = shape, colors = MiraTheme.colors, fill = MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(spacing.lg),
            )
        }
        Spacer(Modifier.height(spacing.xs))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = spacing.xxs),
        )
    }
}

private const val AREA_GRID_COLUMNS = 4

/**
 * The greeting, centred, with a single line of where the day stands under it.
 *
 * iOS opens a screen with a big title and air; Mira opens hers the same way but centres it over
 * the wave, so the greeting reads as spoken to Abu rather than as a page heading. The status line
 * is the one sentence Mira would say if asked "how does today look" — no more.
 */
@Composable
private fun HomeGreeting(
    greeting: String,
    status: String,
    modifier: Modifier = Modifier,
    statusAccent: Boolean = false,
) {
    val spacing = MiraTheme.spacing
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (greeting.isNotEmpty()) {
            Text(
                text = greeting,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
        }
        if (status.isNotEmpty()) {
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = status,
                style = MaterialTheme.typography.bodyLarge,
                color = if (statusAccent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * One thing Mira thinks Abu should look at first, drawn from his own records and never invented.
 *
 * Severity is carried by the words and the glyph — a row says it is overdue — so the card reads
 * correctly with the colour stripped out; the tint only supports it. There is no empty state:
 * when nothing wants attention the caller leaves the cards out, because a card that says "nothing
 * needs attention" every morning is one Abu learns to scroll past.
 */
@Composable
private fun HomeFocusCard(
    row: HomeAttentionRow,
    wording: HomeWording,
    onOpen: () -> Unit,
    onRemindMe: () -> Unit = {},
    onMarkDone: () -> Unit = {},
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    MiraCard(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.sm),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(spacing.xl)
                        .clip(CircleShape)
                        .background(row.severity.wash()),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = row.severity.icon(),
                        contentDescription = null,
                        tint = row.severity.tint(),
                        modifier = Modifier.size(spacing.md + spacing.xxs),
                    )
                }
                Spacer(Modifier.width(spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.whatHappened,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (row.whyItMatters.isNotBlank()) {
                        Text(
                            text = row.whyItMatters,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = spacing.xxs / 2),
                        )
                    }
                    val recordLabel = listOfNotNull(
                        row.target?.let { wording.recordTypeLabel(it) },
                        row.relatedRecordTitle,
                    ).joinToString(": ").takeIf { it.isNotBlank() }
                    if (recordLabel != null) {
                        Text(
                            text = recordLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = spacing.xxs / 2),
                        )
                    }
                }
                Spacer(Modifier.width(spacing.sm))
                Icon(
                    imageVector = MiraIcons.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MiraTheme.colors.hairlineStrong,
                )
            }

            if (row.availableActions.isNotEmpty()) {
                Spacer(Modifier.height(spacing.xs))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = spacing.xl + spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (AttentionAction.OPEN in row.availableActions) {
                        MiraChip(
                            text = wording.openAction,
                            onClick = onOpen,
                        )
                    }
                    if (AttentionAction.MARK_DONE in row.availableActions) {
                        MiraChip(
                            text = wording.markDoneAction,
                            onClick = onMarkDone,
                        )
                    }
                    if (AttentionAction.REMIND_ME in row.availableActions) {
                        MiraChip(
                            text = wording.remindMeAction,
                            onClick = onRemindMe,
                        )
                    }
                    if (AttentionAction.DISMISS in row.availableActions) {
                        MiraChip(
                            text = wording.dismissAction,
                            onClick = onDismiss,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The next prayer and today's five — Home's one steady daily-rhythm card.
 *
 * The time, the live countdown and the dots all arrive already worded and already decided; the
 * card draws them and nothing more. The whole card opens the Salah screen, and the "View Salah"
 * line with its chevron is the visible affordance for that. There is no empty state: the card is
 * built only when Abu has a prayer location, so it always has a real next prayer to show.
 */
@Composable
private fun HomeSalahCard(
    salah: HomeSalah,
    wording: HomeWording,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    MiraCard(onClick = onOpen, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(spacing.xl)
                        .clip(CircleShape)
                        .background(MiraTheme.colors.accentWash),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = MiraIcons.Salah,
                        contentDescription = null,
                        tint = MiraTheme.colors.accentDim,
                        modifier = Modifier.size(spacing.md + spacing.xxs),
                    )
                }
                Spacer(Modifier.width(spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = salah.nextName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = salah.nextTimeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = spacing.xxs / 2),
                    )
                }
                Spacer(Modifier.width(spacing.sm))
                Text(
                    text = salah.remainingLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(spacing.sm))
            SalahProgressTrack(progress = salah.progress)

            Spacer(Modifier.height(spacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                salah.dots.forEach { dot -> SalahDot(dot = dot) }
            }

            salah.hadith?.let { hadith ->
                Spacer(Modifier.height(spacing.sm))
                MiraDivider()
                Spacer(Modifier.height(spacing.xs))
                HomeWaqtHadithSection(hadith = hadith)
            }

            Spacer(Modifier.height(spacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = wording.viewSalah,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = MiraIcons.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(spacing.md),
                )
            }
        }
    }
}

/**
 * An authentic Hadith section embedded inside the Home Salah card for the active prayer waqt.
 */
@Composable
private fun HomeWaqtHadithSection(
    hadith: HomeHadith,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val displayHadith = remember(hadith, offset) {
        if (offset == 0) hadith
        else {
            val rotated = com.rayluxeo.aida.domain.salah.WaqtHadithProvider.forPrayer(hadith.prayer, offset = offset)
            hadith.copy(
                arabic = rotated.arabic,
                bangla = rotated.bangla,
                reference = rotated.reference,
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { offset++ }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = MiraIcons.Article,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(spacing.md),
                )
                Spacer(Modifier.width(spacing.xs))
                Text(
                    text = "ওয়াক্তের হাদিস · ${displayHadith.prayerName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = displayHadith.reference,
                style = MaterialTheme.typography.labelSmall,
                color = MiraTheme.colors.accentDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(spacing.xs))

        Text(
            text = "« ${displayHadith.arabic} »",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = spacing.xs),
        )

        Spacer(Modifier.height(spacing.xxs))

        Text(
            text = displayHadith.bangla,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Standalone Hadith card displayed on Home when prayer location is not yet set.
 */
@Composable
private fun HomeHadithCard(
    hadith: HomeHadith,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    MiraCard(onClick = onOpen, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HomeWaqtHadithSection(hadith = hadith)
            Spacer(Modifier.height(spacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "নামাজ দেখুন",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = MiraIcons.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(spacing.md),
                )
            }
        }
    }
}

/** The thin determinate bar under the next-prayer line: hairline track, champagne fill. */
@Composable
private fun SalahProgressTrack(progress: Float, modifier: Modifier = Modifier) {
    val spacing = MiraTheme.spacing
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(spacing.xxs)
            .clip(CircleShape)
            .background(MiraTheme.colors.hairline),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(CircleShape)
                .background(MiraTheme.colors.accentDim),
        )
    }
}

/**
 * One of the five dots: a filled champagne mark for a prayer recorded as prayed, a hairline ring
 * otherwise. The name sits beneath. Nothing here reads as a fault — a prayer not yet logged is
 * simply an open ring, never marked missed (Abu's instruction).
 */
@Composable
private fun SalahDot(dot: HomeSalahDot, modifier: Modifier = Modifier) {
    val spacing = MiraTheme.spacing
    val base = Modifier.size(spacing.lg).clip(CircleShape)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = when (dot.state) {
                HomeSalahDotState.PRAYED -> base.background(MiraTheme.colors.accentWash)
                HomeSalahDotState.NOT_YET ->
                    base.border(spacing.hairline * 2, MiraTheme.colors.hairlineStrong, CircleShape)
                HomeSalahDotState.UPCOMING ->
                    base.border(spacing.hairline * 2, MiraTheme.colors.hairline, CircleShape)
            },
            contentAlignment = Alignment.Center,
        ) {
            if (dot.state == HomeSalahDotState.PRAYED) {
                Icon(
                    imageVector = MiraIcons.Check,
                    contentDescription = null,
                    tint = MiraTheme.colors.accentDim,
                    modifier = Modifier.size(spacing.sm + spacing.xxs),
                )
            }
        }
        Spacer(Modifier.height(spacing.xxs))
        Text(
            text = dot.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (dot.state == HomeSalahDotState.PRAYED) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One line of Today's list.
 *
 * The circle is a real control, not decoration: tapping it ticks the task off, with the confirm
 * haptic Abu feels before he sees the list settle. The words open the full list. An overdue task
 * rings its circle in danger so the day's weight is legible before he reads a word.
 */
@Composable
private fun HomeTaskRow(
    task: HomeTask,
    onComplete: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    val haptics = rememberMiraHaptics()
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(spacing.xxl - spacing.xs)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = task.title,
                ) {
                    haptics.confirm()
                    onComplete()
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(spacing.lg)
                    .clip(CircleShape)
                    .border(
                        width = spacing.hairline * 2,
                        color = if (task.isOverdue) MiraTheme.colors.danger else MiraTheme.colors.hairlineStrong,
                        shape = CircleShape,
                    ),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(MaterialTheme.shapes.medium)
                .clickable(role = Role.Button, onClickLabel = task.title) { onOpen() }
                .padding(vertical = spacing.sm),
        ) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            task.supporting?.let { supporting ->
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (task.isOverdue) MiraTheme.colors.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = spacing.xxs / 2),
                )
            }
        }
    }
}

/** The next thing in the diary, held in the same rhythm as the list but never ticked off. */
@Composable
private fun HomeEventRow(
    event: HomeEvent,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClickLabel = event.title) { onOpen() }
            .padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(spacing.xxl - spacing.xs)
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(spacing.lg)
                    .clip(CircleShape)
                    .background(MiraTheme.colors.accentWash),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MiraIcons.Schedule,
                    contentDescription = null,
                    tint = MiraTheme.colors.accentDim,
                    modifier = Modifier.size(spacing.md),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = event.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = spacing.xxs / 2),
            )
        }
    }
}

/**
 * A line Home states and does not offer to open.
 *
 * Deliberately not clickable: a reminder has no screen behind it yet, and a row that ripples and
 * then does nothing is the defect §12 names first. The glyph is muted rather than accent — it says
 * what KIND of line this is, which is not the same as saying it matters.
 */
@Composable
private fun FactRow(
    title: String,
    supporting: String?,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    MiraListRow(
        title = title,
        modifier = modifier,
        supporting = supporting,
        leading = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
private fun AttentionSeverity.icon(): ImageVector = when (this) {
    AttentionSeverity.NOW -> MiraIcons.ErrorOutline
    AttentionSeverity.SOON -> MiraIcons.Schedule
    AttentionSeverity.WATCH -> MiraIcons.RadioButtonUnchecked
}

@Composable
private fun AttentionSeverity.tint(): Color = when (this) {
    AttentionSeverity.NOW -> MiraTheme.colors.danger
    AttentionSeverity.SOON -> MiraTheme.colors.accentDim
    AttentionSeverity.WATCH -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun AttentionSeverity.wash(): Color = when (this) {
    AttentionSeverity.NOW -> MiraTheme.colors.danger.copy(alpha = 0.14f)
    AttentionSeverity.SOON -> MiraTheme.colors.accentWash
    AttentionSeverity.WATCH -> MaterialTheme.colorScheme.surfaceVariant
}

/**
 * Where a focus row goes.
 *
 * Tasks have a screen, so they open it. The rest have an area that is declared and not yet built,
 * and that area's own screen is the honest destination: it names what is coming and offers to hand
 * the question to Mira. An item that pointed at no record at all goes straight to her, the one
 * route that always works. A subscription belongs to the money area, where its renewal will be
 * edited when that screen exists.
 *
 * The `when` is exhaustive on purpose: a new [AttentionTarget] should fail to compile here rather
 * than quietly land Abu in the wrong area.
 */
private fun openAttention(
    target: AttentionTarget?,
    onOpenTasks: () -> Unit,
    onOpenArea: (MiraArea) -> Unit,
    onOpenMira: () -> Unit,
) {
    when (target) {
        null -> onOpenMira()
        AttentionTarget.TASK -> onOpenTasks()
        AttentionTarget.INVOICE -> onOpenArea(MiraArea.INVOICES)
        AttentionTarget.PROJECT -> onOpenArea(MiraArea.PROJECTS)
        AttentionTarget.CLIENT -> onOpenArea(MiraArea.CLIENTS)
        AttentionTarget.SUBSCRIPTION -> onOpenArea(MiraArea.MONEY)
    }
}

/**
 * Daily Quran recitation card on Home tracking the 2-page target.
 */
@Composable
private fun HomeQuranCard(
    quran: HomeQuran,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    MiraCard(onClick = onOpen, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(spacing.xl)
                            .clip(CircleShape)
                            .background(MiraTheme.colors.accentWash),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (quran.targetMet) MiraIcons.Filled.Bookmark else MiraIcons.Bookmark,
                            contentDescription = null,
                            tint = if (quran.targetMet) MiraTheme.colors.positive else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(spacing.md),
                        )
                    }
                    Spacer(Modifier.width(spacing.sm))
                    Column {
                        Text(
                            text = "কুরআন তিলাওয়াত",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        val subtitle = if (quran.lastReadPageToday != null) {
                            "সর্বশেষ পঠিত: পৃষ্ঠা ${quran.lastReadPageToday} · পৃষ্ঠা ${quran.continueFromPage} থেকে পড়ুন"
                        } else {
                            "দৈনিক লক্ষ্য: ${quran.dailyTarget} পৃষ্ঠা · বর্তমান পৃষ্ঠা ${quran.continueFromPage}"
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = spacing.xxs / 2),
                        )
                    }
                }

                Text(
                    text = "${quran.pagesReadToday} / ${quran.dailyTarget} পৃষ্ঠা",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (quran.targetMet) MiraTheme.colors.positive else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(spacing.xs))

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(spacing.xxs)
                    .clip(CircleShape)
                    .background(MiraTheme.colors.hairline),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(quran.progressRatio)
                        .height(spacing.xxs)
                        .clip(CircleShape)
                        .background(if (quran.targetMet) MiraTheme.colors.positive else MaterialTheme.colorScheme.primary),
                )
            }

            Spacer(Modifier.height(spacing.xs))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (quran.targetMet) "আজকের লক্ষ্য পূর্ণ! আরও তিলাওয়াত করুন" else "তিলাওয়াত করুন (পৃষ্ঠা ${quran.currentPage})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = MiraIcons.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(spacing.md),
                )
            }
        }
    }
}
