package com.rayluxeo.aida.core.design.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.rayluxeo.aida.core.design.MiraTheme

/**
 * The switch.
 *
 * Material's control in Mira's colours: champagne when it is on, graphite and a hairline when it
 * is off, never Material's own purple.
 *
 * It exists because three screens had each grown a private copy of exactly this — the alarms
 * list, the settings list and the voice setup screen — and one of them carried a comment saying
 * so. A fourth screen would have made it four. A one-off composable living in a screen file is
 * how a design system stops being one (AGENTS.md §11).
 *
 * Deliberately has no label or row of its own: the switch is the control, and what sits beside it
 * is [MiraListRow]'s business.
 */
@Composable
fun MiraSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedBorderColor = MaterialTheme.colorScheme.primary,
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            uncheckedBorderColor = MiraTheme.colors.hairlineStrong,
        ),
    )
}

@Preview
@Composable
private fun MiraSwitchPreview() {
    MiraPreviewSurface {
        var on by remember { mutableStateOf(true) }
        var off by remember { mutableStateOf(false) }
        MiraSwitch(checked = on, onCheckedChange = { on = it })
        MiraSwitch(checked = off, onCheckedChange = { off = it })
        MiraSwitch(checked = true, onCheckedChange = null, enabled = false)
    }
}
