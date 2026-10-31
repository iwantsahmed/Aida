package com.rayluxeo.aida.ui.salah

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraButtonStyle
import com.rayluxeo.aida.core.design.component.MiraBottomSheet
import com.rayluxeo.aida.core.design.component.MiraCard
import com.rayluxeo.aida.core.design.component.MiraDivider
import com.rayluxeo.aida.core.design.component.MiraIconButton
import com.rayluxeo.aida.core.design.component.MiraIcons
import com.rayluxeo.aida.core.design.component.MiraInlineNotice
import com.rayluxeo.aida.core.design.component.MiraListRow
import com.rayluxeo.aida.core.design.component.MiraListSkeleton
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.core.design.component.MiraScaffold
import com.rayluxeo.aida.core.design.component.MiraTopBar
import com.rayluxeo.aida.core.design.rememberMiraHaptics
import com.rayluxeo.aida.domain.salah.BuiltInCities
import com.rayluxeo.aida.domain.salah.Prayer
import com.rayluxeo.aida.domain.salah.PrayerCity
import com.rayluxeo.aida.domain.salah.PrayerStatus
import com.rayluxeo.aida.domain.salah.TahajjudStatus

/** The statuses the correction sheet offers, in the order it shows them. */
private val PRAYER_STATUS_CHOICES = listOf(
    PrayerStatus.PRAYED,
    PrayerStatus.PRAYED_LATE,
    PrayerStatus.NOT_YET,
    PrayerStatus.MISSED,
    PrayerStatus.NOT_RECORDED,
)

private val TAHAJJUD_STATUS_CHOICES = listOf(
    TahajjudStatus.PRAYED,
    TahajjudStatus.NOT_PRAYED,
    TahajjudStatus.NOT_RECORDED,
)

/**
 * Mira's Salah main screen: the day's prayer times, a live countdown to the next prayer, the five
 * prayer rows with one-tap tracking, and the Tahajjud window kept apart from the obligatory five.
 *
 * [onOpenMonth] opens the monthly tracker, [onOpenSettings] the Salah settings (also where a
 * Tahajjud reminder is set, and where a first-time location is added). The lead wires these routes.
 */
@Composable
fun SalahScreen(
    onBack: () -> Unit,
    onOpenMonth: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenQuran: (() -> Unit)? = null,
    viewModel: SalahViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val words = salahWording(state.language)
    val spacing = MiraTheme.spacing

    var statusSheetFor by remember { mutableStateOf<PrayerRowUi?>(null) }
    var tahajjudSheet by remember { mutableStateOf(false) }

    MiraScaffold(
        topBar = {
            MiraTopBar(
                title = words.screenTitle,
                onBack = onBack,
                action = {
                    MiraIconButton(
                        icon = MiraIcons.Settings,
                        contentDescription = words.setUpTitle,
                        onClick = onOpenSettings,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> MiraListSkeleton(modifier = Modifier.padding(padding))

            !state.isConfigured -> SetupPrompt(
                words = words,
                language = state.language,
                modifier = Modifier.fillMaxSize().padding(padding),
                onSelectCity = viewModel::selectCity,
                onSetUp = onOpenSettings,
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                state.notice?.let { notice ->
                    item {
                        MiraInlineNotice(
                            text = notice,
                            tone = MiraNoticeTone.DANGER,
                            onDismiss = viewModel::onDismissNotice,
                        )
                    }
                }

                item { DateHeader(state = state) }

                state.hero?.let { hero ->
                    item { HeroCountdown(hero = hero) }
                }

                item {
                    PrayersCard(
                        prayers = state.prayers,
                        words = words,
                        onMarkPrayed = viewModel::onMarkPrayed,
                        onOpenStatus = { statusSheetFor = it },
                    )
                }

                state.hadith?.let { hadith ->
                    item {
                        WaqtHadithCard(hadith = hadith)
                    }
                }

                state.tahajjud?.let { tahajjud ->
                    item {
                        TahajjudCard(
                            tahajjud = tahajjud,
                            words = words,
                            onMarkPrayed = viewModel::onMarkTahajjud,
                            onOpenStatus = { tahajjudSheet = true },
                            onSetReminder = onOpenSettings,
                        )
                    }
                }

                item {
                    MiraCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenMonth) {
                        MiraListRow(
                            title = words.monthView,
                            leading = {
                                Icon(
                                    imageVector = MiraIcons.CalendarMonth,
                                    contentDescription = null,
                                    tint = MiraTheme.colors.accentDim,
                                )
                            },
                            trailing = {
                                Icon(
                                    imageVector = MiraIcons.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    }
                }

                onOpenQuran?.let { openQuran ->
                    item {
                        MiraCard(modifier = Modifier.fillMaxWidth(), onClick = openQuran) {
                            MiraListRow(
                                title = "আল-কুরআনুল কারীম",
                                supporting = "দৈনিক তিলাওয়াত লক্ষ্য: ২ পৃষ্ঠা",
                                leading = {
                                    Icon(
                                        imageVector = MiraIcons.Bookmark,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                trailing = {
                                    Icon(
                                        imageVector = MiraIcons.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(spacing.lg)) }
            }
        }
    }

    statusSheetFor?.let { row ->
        PrayerStatusSheet(
            row = row,
            words = words,
            onSelect = { status ->
                viewModel.onSetStatus(row.prayer, status)
                statusSheetFor = null
            },
            onDismiss = { statusSheetFor = null },
        )
    }

    if (tahajjudSheet) {
        val current = state.tahajjud?.status ?: TahajjudStatus.NOT_RECORDED
        TahajjudStatusSheet(
            current = current,
            words = words,
            onSelect = { status ->
                viewModel.onSetTahajjud(status)
                tahajjudSheet = false
            },
            onDismiss = { tahajjudSheet = false },
        )
    }
}

@Composable
private fun SetupPrompt(
    words: SalahWording,
    language: SpokenLanguage,
    modifier: Modifier = Modifier,
    onSelectCity: (PrayerCity) -> Unit,
    onSetUp: () -> Unit,
) {
    val spacing = MiraTheme.spacing
    val isBengali = language == SpokenLanguage.BENGALI

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.screenHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(spacing.xl))
        Icon(
            imageVector = MiraIcons.Place,
            contentDescription = null,
            modifier = Modifier.size(spacing.xl),
            tint = MiraTheme.colors.accentDim,
        )
        Spacer(Modifier.height(spacing.md))
        Text(
            text = words.setUpTitle,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(spacing.xs))
        Text(
            text = words.setUpBody,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = spacing.md),
        )
        Spacer(Modifier.height(spacing.lg))

        Text(
            text = if (isBengali) "দ্রুত শহর নির্বাচন করুন (জিপিএস প্রয়োজন নেই):" else "Quick select city (No GPS needed):",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.sm))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs, Alignment.CenterHorizontally),
        ) {
            listOf("dhaka", "chattogram", "sylhet", "rajshahi", "khulna", "makkah").forEach { id ->
                val city = BuiltInCities.findById(id) ?: return@forEach
                MiraButton(
                    text = if (isBengali) city.bengaliName else city.englishName,
                    onClick = { onSelectCity(city) },
                    style = if (id == "dhaka") MiraButtonStyle.PRIMARY else MiraButtonStyle.SECONDARY,
                )
            }
        }

        Spacer(Modifier.height(spacing.lg))
        MiraButton(
            text = if (isBengali) "সকল জেলা ও শহর..." else "All Cities / Settings...",
            onClick = onSetUp,
            style = MiraButtonStyle.SECONDARY,
            leadingIcon = MiraIcons.Place,
        )
        Spacer(Modifier.height(spacing.xl))
    }
}

@Composable
private fun DateHeader(state: SalahUiState) {
    val spacing = MiraTheme.spacing
    Column {
        Text(
            text = state.gregorianDate,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (state.hijriDate.isNotEmpty() || state.locationLabel.isNotEmpty()) {
            Spacer(Modifier.height(spacing.xxs))
            val detail = listOf(state.hijriDate, state.locationLabel)
                .filter { it.isNotEmpty() }
                .joinToString(" · ")
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeroCountdown(hero: SalahHeroUi) {
    val spacing = MiraTheme.spacing
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = hero.untilText,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.xxs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = hero.nextPrayerName,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.width(spacing.sm))
            Text(
                text = hero.nextPrayerTime,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(spacing.xs))
        Text(
            text = hero.countdown,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun PrayersCard(
    prayers: List<PrayerRowUi>,
    words: SalahWording,
    onMarkPrayed: (Prayer) -> Unit,
    onOpenStatus: (PrayerRowUi) -> Unit,
) {
    MiraCard(modifier = Modifier.fillMaxWidth()) {
        prayers.forEachIndexed { index, row ->
            if (index > 0) MiraDivider()
            PrayerRow(
                row = row,
                words = words,
                onMarkPrayed = { onMarkPrayed(row.prayer) },
                onOpenStatus = { onOpenStatus(row) },
            )
        }
    }
}

@Composable
private fun PrayerRow(
    row: PrayerRowUi,
    words: SalahWording,
    onMarkPrayed: () -> Unit,
    onOpenStatus: () -> Unit,
) {
    val visual = prayerStatusVisual(row.displayStatus)
    MiraListRow(
        title = row.name,
        supporting = "${row.time}  ·  ${row.statusLabel}",
        leading = {
            TrackCircle(
                icon = visual.icon,
                tint = visual.tint,
                contentDescription = "${row.name}, ${row.statusLabel}",
                onTap = onMarkPrayed,
                onLongPress = onOpenStatus,
            )
        },
        trailing = {
            MiraIconButton(
                icon = MiraIcons.MoreVert,
                contentDescription = words.chooseStatus,
                onClick = onOpenStatus,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
private fun TahajjudCard(
    tahajjud: TahajjudCardUi,
    words: SalahWording,
    onMarkPrayed: () -> Unit,
    onOpenStatus: () -> Unit,
    onSetReminder: () -> Unit,
) {
    val spacing = MiraTheme.spacing
    val visual = tahajjudStatusVisual(tahajjud.status)
    MiraCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = MiraIcons.Bedtime,
                        contentDescription = null,
                        tint = MiraTheme.colors.accentDim,
                    )
                    Spacer(Modifier.width(spacing.xs))
                    Text(
                        text = words.tahajjudTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(spacing.xxs))
                Text(
                    text = "${words.lastThird}  ·  ${tahajjud.windowLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = tahajjud.statusLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (tahajjud.status == TahajjudStatus.PRAYED) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Spacer(Modifier.width(spacing.sm))
            TrackCircle(
                icon = visual.icon,
                tint = visual.tint,
                contentDescription = "${words.tahajjudTitle}, ${tahajjud.statusLabel}",
                onTap = onMarkPrayed,
                onLongPress = onOpenStatus,
            )
        }
        Spacer(Modifier.height(spacing.xs))
        MiraButton(
            text = words.setReminder,
            onClick = onSetReminder,
            style = MiraButtonStyle.TEXT,
            leadingIcon = MiraIcons.NotificationsNone,
        )
    }
}

/**
 * A tracking control: one tap records the prayer, a long press opens the correction sheet. The
 * long press is paired with a visible control everywhere it is offered (the trailing glyph on a
 * prayer row), because a long press is invisible to anyone who has not been told about it.
 */
@Composable
private fun TrackCircle(
    icon: ImageVector,
    tint: Color,
    contentDescription: String,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val haptics = rememberMiraHaptics()
    Box(
        modifier = Modifier
            .size(MiraTheme.spacing.xxl)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = {
                    haptics.confirm()
                    onTap()
                },
                onLongClick = {
                    haptics.tap()
                    onLongPress()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = tint)
    }
}

@Composable
private fun PrayerStatusSheet(
    row: PrayerRowUi,
    words: SalahWording,
    onSelect: (PrayerStatus) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = MiraTheme.spacing
    MiraBottomSheet(onDismissRequest = onDismiss, title = row.name) {
        PRAYER_STATUS_CHOICES.forEach { status ->
            val visual = prayerStatusVisual(status)
            MiraListRow(
                title = words.statusLabel(status),
                onClick = { onSelect(status) },
                leading = {
                    Icon(imageVector = visual.icon, contentDescription = null, tint = visual.tint)
                },
                trailing = if (row.storedStatus == status) {
                    {
                        Icon(
                            imageVector = MiraIcons.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    null
                },
            )
        }
        Spacer(Modifier.height(spacing.md))
    }
}

@Composable
private fun TahajjudStatusSheet(
    current: TahajjudStatus,
    words: SalahWording,
    onSelect: (TahajjudStatus) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = MiraTheme.spacing
    MiraBottomSheet(onDismissRequest = onDismiss, title = words.tahajjudTitle) {
        TAHAJJUD_STATUS_CHOICES.forEach { status ->
            val visual = tahajjudStatusVisual(status)
            MiraListRow(
                title = words.tahajjudStatusLabel(status),
                onClick = { onSelect(status) },
                leading = {
                    Icon(imageVector = visual.icon, contentDescription = null, tint = visual.tint)
                },
                trailing = if (current == status) {
                    {
                        Icon(
                            imageVector = MiraIcons.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    null
                },
            )
        }
        Spacer(Modifier.height(spacing.md))
    }
}

/** The glyph and tint a status shows — one decision, so the row and the sheet never disagree. */
private data class StatusVisual(val icon: ImageVector, val tint: Color)

@Composable
private fun prayerStatusVisual(status: PrayerStatus): StatusVisual = when (status) {
    PrayerStatus.PRAYED -> StatusVisual(MiraIcons.Filled.CheckCircle, MaterialTheme.colorScheme.primary)
    PrayerStatus.PRAYED_LATE -> StatusVisual(MiraIcons.CheckCircle, MiraTheme.colors.accentDim)
    PrayerStatus.MISSED -> StatusVisual(MiraIcons.ErrorOutline, MiraTheme.colors.danger)
    PrayerStatus.NOT_YET -> StatusVisual(MiraIcons.RadioButtonUnchecked, MaterialTheme.colorScheme.onSurfaceVariant)
    PrayerStatus.UPCOMING -> StatusVisual(MiraIcons.RadioButtonUnchecked, MiraTheme.colors.hairlineStrong)
    PrayerStatus.NOT_RECORDED -> StatusVisual(MiraIcons.RadioButtonUnchecked, MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun tahajjudStatusVisual(status: TahajjudStatus): StatusVisual = when (status) {
    TahajjudStatus.PRAYED -> StatusVisual(MiraIcons.Filled.CheckCircle, MaterialTheme.colorScheme.primary)
    TahajjudStatus.NOT_PRAYED -> StatusVisual(MiraIcons.RadioButtonUnchecked, MaterialTheme.colorScheme.onSurfaceVariant)
    TahajjudStatus.NOT_RECORDED -> StatusVisual(MiraIcons.RadioButtonUnchecked, MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * An authentic Hadith card for the active prayer waqt.
 *
 * Shows original Arabic text with tashkeel, accurate Bengali meaning,
 * and canonical Hadith reference.
 */
@Composable
private fun WaqtHadithCard(
    hadith: WaqtHadithUi,
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

    MiraCard(
        onClick = { offset++ },
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(spacing.md)) {
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
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(spacing.sm))

            Text(
                text = "« ${displayHadith.arabic} »",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.xs),
            )

            Spacer(Modifier.height(spacing.xs))

            Text(
                text = displayHadith.bangla,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
