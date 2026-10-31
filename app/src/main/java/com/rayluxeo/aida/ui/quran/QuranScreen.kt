package com.rayluxeo.aida.ui.quran

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraButtonStyle
import com.rayluxeo.aida.core.design.component.MiraCard
import com.rayluxeo.aida.core.design.component.MiraDialog
import com.rayluxeo.aida.core.design.component.MiraDivider
import com.rayluxeo.aida.core.design.component.MiraIconButton
import com.rayluxeo.aida.core.design.component.MiraIcons
import com.rayluxeo.aida.core.design.component.MiraInlineNotice
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.core.design.component.MiraScaffold
import com.rayluxeo.aida.core.design.component.MiraTextField
import com.rayluxeo.aida.core.design.component.MiraTopBar
import com.rayluxeo.aida.core.design.rememberMiraHaptics
import com.rayluxeo.aida.domain.quran.QuranAyah
import com.rayluxeo.aida.domain.quran.QuranPage
import com.rayluxeo.aida.domain.quran.QuranPageProvider

@Composable
fun QuranScreen(
    onBack: () -> Unit,
    viewModel: QuranViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = rememberMiraHaptics()
    val spacing = MiraTheme.spacing

    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpPageInput by remember { mutableStateOf("") }

    LaunchedEffect(state.notice) {
        if (state.notice != null) {
            haptics.confirm()
        }
    }

    MiraScaffold(
        topBar = {
            val topSubtitle = buildString {
                append("পৃষ্ঠা ${state.pageNumber} / ${state.totalPages} · পারা ${state.page.juzNumber}")
                if (state.khatamCount > 0) {
                    append(" · খতম: ${state.khatamCount}")
                }
            }
            MiraTopBar(
                title = "আল-কুরআনুল কারীম",
                subtitle = topSubtitle,
                onBack = onBack,
                action = {
                    MiraIconButton(
                        icon = MiraIcons.Bookmark,
                        contentDescription = "পৃষ্ঠা নির্বাচন ও ব্যাকআপ",
                        onClick = { showJumpDialog = true },
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
            )
        },
        bottomBar = {
            QuranBottomControls(
                pageNumber = state.pageNumber,
                totalPages = state.totalPages,
                isPageCompletedToday = state.isPageCompletedToday,
                onPrevious = viewModel::onPreviousPage,
                onNext = viewModel::onNextPage,
                onMarkRead = viewModel::onMarkPageRead,
            )
        },
        horizontalPadding = 0.dp,
        modifier = modifier.fillMaxSize(),
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = spacing.sm,
                end = spacing.sm,
                top = contentPadding.calculateTopPadding() + spacing.xs,
                bottom = contentPadding.calculateBottomPadding() + spacing.sm,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            state.notice?.let { noticeText ->
                item {
                    MiraInlineNotice(
                        text = noticeText,
                        tone = if (state.targetMet) MiraNoticeTone.CONFIRM else MiraNoticeTone.INFO,
                        onDismiss = viewModel::onDismissNotice,
                    )
                }
            }

            item {
                QuranDailyTargetCard(
                    pagesReadToday = state.pagesReadToday,
                    dailyTarget = state.dailyTarget,
                    progressRatio = state.progressRatio,
                    targetMet = state.targetMet,
                    remainingPages = state.remainingPagesToday,
                    lastReadPageToday = state.lastReadPageToday,
                    continueFromPage = state.continueFromPage,
                    currentPageNumber = state.pageNumber,
                    khatamCount = state.khatamCount,
                    onResumeFromToday = viewModel::onResumeFromToday,
                )
            }

            item {
                SurahHeaderCard(page = state.page, isCompletedToday = state.isPageCompletedToday)
            }

            if (state.page.bismillahPrecedes) {
                item {
                    BismillahCard()
                }
            }

            items(state.page.ayahs, key = { "${state.page.pageNumber}_${it.numberInSurah}" }) { ayah ->
                AyahCard(ayah = ayah)
            }
        }
    }

    if (showJumpDialog) {
        MiraDialog(
            title = "পৃষ্ঠা নির্বাচন",
            onDismissRequest = { showJumpDialog = false },
            confirmText = "যান",
            onConfirm = {
                val target = jumpPageInput.toIntOrNull()
                if (target != null) {
                    viewModel.onSelectPage(target)
                }
                showJumpDialog = false
                jumpPageInput = ""
            },
            dismissText = "বাতিল",
            onDismiss = {
                showJumpDialog = false
                jumpPageInput = ""
            },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "কুরআনের যেকোনো পৃষ্ঠায় (১ - ${state.totalPages}) সরাসরি যেতে নম্বর লিখুন:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(spacing.sm))
                MiraTextField(
                    value = jumpPageInput,
                    onValueChange = { jumpPageInput = it.filter { ch -> ch.isDigit() } },
                    label = "পৃষ্ঠা নম্বর",
                    placeholder = "যেমন: ${state.continueFromPage}",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(spacing.sm))
                MiraButton(
                    text = "আজকের পড়ার স্থান: পৃষ্ঠা ${state.continueFromPage}-এ যান",
                    onClick = {
                        viewModel.onResumeFromToday()
                        showJumpDialog = false
                        jumpPageInput = ""
                    },
                    style = MiraButtonStyle.SECONDARY,
                    leadingIcon = MiraIcons.Bookmark,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(spacing.sm))
                MiraDivider()
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = "ডিভাইস ব্যাকআপ (অফলাইন সংরক্ষণ)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(spacing.xxs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    MiraButton(
                        text = "ব্যাকআপ রাখুন",
                        onClick = {
                            viewModel.onBackupProgress()
                            showJumpDialog = false
                        },
                        style = MiraButtonStyle.SECONDARY,
                        modifier = Modifier.weight(1f),
                    )
                    MiraButton(
                        text = "ব্যাকআপ আনুন",
                        onClick = {
                            viewModel.onRestoreProgress()
                            showJumpDialog = false
                        },
                        style = MiraButtonStyle.SECONDARY,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuranDailyTargetCard(
    pagesReadToday: Int,
    dailyTarget: Int,
    progressRatio: Float,
    targetMet: Boolean,
    remainingPages: Int,
    lastReadPageToday: Int?,
    continueFromPage: Int,
    currentPageNumber: Int,
    khatamCount: Int = 0,
    onResumeFromToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    MiraCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.sm),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (targetMet) MiraIcons.Filled.CheckCircle else MiraIcons.Bookmark,
                        contentDescription = null,
                        tint = if (targetMet) MiraTheme.colors.positive else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(spacing.md),
                    )
                    Spacer(Modifier.width(spacing.xs))
                    Text(
                        text = "দৈনিক তিলাওয়াত লক্ষ্য",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                Text(
                    text = "$pagesReadToday / $dailyTarget পৃষ্ঠা",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (targetMet) MiraTheme.colors.positive else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (khatamCount > 0) {
                Spacer(Modifier.height(spacing.xxs))
                Text(
                    text = "খতম সম্পন্ন: $khatamCount বার",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(spacing.xs))

            // Thin progress track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(spacing.xxs)
                    .clip(CircleShape)
                    .background(MiraTheme.colors.hairline),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressRatio)
                        .height(spacing.xxs)
                        .clip(CircleShape)
                        .background(if (targetMet) MiraTheme.colors.positive else MaterialTheme.colorScheme.primary),
                )
            }

            Spacer(Modifier.height(spacing.xxs))

            val message = when {
                targetMet -> "আলহামদুলিল্লাহ! আজকের কমপক্ষে ২ পৃষ্ঠা তিলাওয়াত সম্পন্ন হয়েছে।"
                lastReadPageToday != null -> "আজকে পৃষ্ঠা $lastReadPageToday পর্যন্ত সম্পন্ন। লক্ষ্য পূরণ হতে আর $remainingPages পৃষ্ঠা বাকি।"
                else -> "আজকের লক্ষ্য পূরণ হতে আর $remainingPages পৃষ্ঠা বাকি আছে।"
            }

            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (targetMet) MiraTheme.colors.positive else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (currentPageNumber != continueFromPage) {
                Spacer(Modifier.height(spacing.xs))
                MiraButton(
                    text = "আজকের পড়ার স্থান: পৃষ্ঠা $continueFromPage-এ ফিরে যান →",
                    onClick = onResumeFromToday,
                    style = MiraButtonStyle.SECONDARY,
                    leadingIcon = MiraIcons.Bookmark,
                    contentPadding = PaddingValues(horizontal = spacing.sm, vertical = spacing.xs),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SurahHeaderCard(
    page: QuranPage,
    isCompletedToday: Boolean,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    MiraCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.sm),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = page.surahNameArabic,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                if (isCompletedToday) {
                    Spacer(Modifier.width(spacing.xs))
                    Box(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(MiraTheme.colors.positive.copy(alpha = 0.15f))
                            .padding(horizontal = spacing.xs, vertical = 2.dp),
                    ) {
                        Text(
                            text = "আজকে পঠিত ✓",
                            style = MaterialTheme.typography.labelSmall,
                            color = MiraTheme.colors.positive,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(spacing.xxs))
            val headerSubtitle = buildString {
                append(page.surahNameBangla)
                if (page.ayahRange.isNotEmpty()) {
                    append(" · ").append(page.ayahRange)
                }
                append(" · পৃষ্ঠা ").append(page.pageNumber)
            }
            Text(
                text = headerSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BismillahCard(modifier: Modifier = Modifier) {
    val spacing = MiraTheme.spacing
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MiraTheme.colors.accentWash)
            .padding(vertical = spacing.xs, horizontal = spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = QuranPageProvider.BISMILLAH.arabic,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(spacing.xxs))
            Text(
                text = QuranPageProvider.BISMILLAH.banglaTransliteration,
                style = MaterialTheme.typography.bodySmall,
                color = MiraTheme.colors.accentDim,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AyahCard(ayah: QuranAyah, modifier: Modifier = Modifier) {
    val spacing = MiraTheme.spacing
    MiraCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.sm),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Ayah number badge row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MiraTheme.colors.accentWash)
                        .border(1.dp, MiraTheme.colors.hairline, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${ayah.numberInSurah}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(spacing.xs))

            // 1. Arabic Text
            Text(
                text = ayah.arabic,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 24.sp,
                    lineHeight = 38.sp,
                ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(spacing.xs))
            MiraDivider()
            Spacer(Modifier.height(spacing.xs))

            // 2. Arabic in Bangla (উচ্চারণ)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "উচ্চারণ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MiraTheme.colors.accentDim,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(spacing.xxs / 2))
                Text(
                    text = ayah.banglaTransliteration,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            Spacer(Modifier.height(spacing.xs))

            // 3. Bangla Meaning (বাংলা অর্থ)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "অর্থ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(spacing.xxs / 2))
                Text(
                    text = ayah.banglaMeaning,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun QuranBottomControls(
    pageNumber: Int,
    totalPages: Int,
    isPageCompletedToday: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMarkRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MiraTheme.colors.hairline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = spacing.sm, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            MiraButton(
                text = "পূর্ববর্তী",
                onClick = onPrevious,
                style = MiraButtonStyle.SECONDARY,
                enabled = pageNumber > 1,
                contentPadding = PaddingValues(horizontal = spacing.xs),
                modifier = Modifier.weight(1f),
            )

            MiraButton(
                text = if (isPageCompletedToday) "পড়া হয়েছে ✓" else "পড়া শেষ",
                onClick = onMarkRead,
                style = if (isPageCompletedToday) MiraButtonStyle.SECONDARY else MiraButtonStyle.PRIMARY,
                leadingIcon = if (isPageCompletedToday) MiraIcons.Filled.CheckCircle else MiraIcons.Check,
                contentPadding = PaddingValues(horizontal = spacing.xs),
                modifier = Modifier.weight(1.2f),
            )

            val nextButtonText = if (pageNumber >= totalPages) "পৃষ্ঠা ১ →" else "পরবর্তী"
            MiraButton(
                text = nextButtonText,
                onClick = onNext,
                style = MiraButtonStyle.SECONDARY,
                enabled = true,
                contentPadding = PaddingValues(horizontal = spacing.xs),
                modifier = Modifier.weight(1f),
            )
        }
    }
}
