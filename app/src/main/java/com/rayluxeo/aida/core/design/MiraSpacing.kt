package com.rayluxeo.aida.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Generous, consistent rhythm. Screens compose from these, never from ad-hoc dp values. */
@Immutable
data class MiraSpacing(
    val hairline: Dp = 1.dp,
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
    val screenHorizontal: Dp = 20.dp,
    val sectionGap: Dp = 24.dp,
    val cardPadding: Dp = 16.dp,
)
