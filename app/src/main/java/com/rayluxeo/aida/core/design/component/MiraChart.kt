package com.rayluxeo.aida.core.design.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rayluxeo.aida.core.design.MiraTheme

/**
 * One slice of a chart.
 *
 * [weight] drives geometry only — a bar's length, a ring's sweep — and is the one place a money
 * amount becomes a float, which is sound because nothing is ever read back off it. The number Abu
 * actually reads is [valueLabel], already formatted by the renderer in his language and currency,
 * so AGENTS.md §8 holds: no total is ever computed or displayed as a float.
 */
data class MiraChartSlice(
    val label: String,
    val weight: Float,
    val valueLabel: String,
    val color: Color,
)

/** The chart colours, drawn from the mascot's sanctioned soft set so a category reads on obsidian. */
fun miraChartColor(index: Int): Color = MASCOT_PALETTE[index % MASCOT_PALETTE.size]

/**
 * A horizontal bar per row: the label, its value, and a track filled in proportion to the largest
 * bar. Horizontal rather than vertical because a category reads left-to-right and a Bengali label
 * needs the width — the same reason the budget card on Money is built this way.
 *
 * Drawn entirely from the palette with no external charting dependency (§2, §10): a track in the
 * raised surface tone and a fill in the slice's own colour.
 */
@Composable
fun MiraBarChart(
    slices: List<MiraChartSlice>,
    modifier: Modifier = Modifier,
    barHeight: Dp = 10.dp,
) {
    val spacing = MiraTheme.spacing
    val max = slices.maxOfOrNull { it.weight }?.takeIf { it > 0f } ?: 1f
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        slices.forEach { slice ->
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = slice.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = spacing.sm),
                    )
                    Text(
                        text = slice.valueLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight)
                        .clip(CircleShape)
                        .background(MiraTheme.colors.hairline),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((slice.weight / max).coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(slice.color),
                    )
                }
            }
        }
    }
}

/**
 * A donut with the total in its eye and a legend beside it. Proportions are drawn as arcs of each
 * slice's colour; the empty centre is where the one number that matters — the total — is read,
 * rather than a pie's solid middle that says nothing.
 */
@Composable
fun MiraDonutChart(
    slices: List<MiraChartSlice>,
    centerValue: String,
    centerCaption: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 128.dp,
    ringWidth: Dp = 18.dp,
) {
    val spacing = MiraTheme.spacing
    val total = slices.fold(0f) { acc, s -> acc + s.weight }.takeIf { it > 0f } ?: 1f
    val track = MiraTheme.colors.hairline
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Box(modifier = Modifier.size(diameter), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = Stroke(width = ringWidth.toPx(), cap = StrokeCap.Butt)
                val inset = ringWidth.toPx() / 2f
                val arcSize = androidx.compose.ui.geometry.Size(
                    size.width - ringWidth.toPx(),
                    size.height - ringWidth.toPx(),
                )
                val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                // A faint full ring underneath, so a single-slice donut still reads as a ring.
                drawArc(
                    color = track,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )
                var start = -90f
                slices.forEach { slice ->
                    val sweep = slice.weight / total * 360f
                    if (sweep > 0f) {
                        drawArc(
                            color = slice.color,
                            startAngle = start,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = stroke,
                        )
                        start += sweep
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = centerValue,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                )
                Text(
                    text = centerCaption,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            slices.forEach { slice ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(spacing.sm)
                            .clip(CircleShape)
                            .background(slice.color),
                    )
                    Spacer(Modifier.width(spacing.sm))
                    Text(
                        text = slice.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(spacing.sm))
                    Text(
                        text = slice.valueLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
