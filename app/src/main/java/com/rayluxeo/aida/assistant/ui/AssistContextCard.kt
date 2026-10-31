package com.rayluxeo.aida.assistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.rayluxeo.aida.assistant.OutboundDisclosure
import com.rayluxeo.aida.assistant.outboundWording
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraButtonStyle
import com.rayluxeo.aida.core.design.component.MiraCard
import com.rayluxeo.aida.core.design.component.MiraDivider
import com.rayluxeo.aida.core.design.component.MiraListRow
import com.rayluxeo.aida.core.design.component.MiraPreviewSurface

/**
 * The last thing Abu sees before anything from his screen leaves the phone.
 *
 * It itemises the real payload rather than summarising it: which app the screen came from, whether
 * its text is in what would be sent and exactly how many characters that is, and whether a
 * screenshot is. The numbers come from [OutboundDisclosure], which is built from the payload
 * itself, so this card cannot describe something other than what would be sent.
 *
 * Two deliberate departures from the rest of the app:
 *
 * - The champagne fill is on [onKeepOnDevice], not on the send action. Everywhere else the primary
 *   button is the step the screen exists for; here the safe answer is the one that must be easiest
 *   to reach, and emphasis is the only honest way to say which answer costs nothing.
 * - There is no auto-dismiss and no remembered choice. The host must treat being dismissed by any
 *   other route — back, the session closing, Abu walking away — as [onKeepOnDevice], because the
 *   default is that nothing is sent.
 */
@Composable
fun AssistContextCard(
    disclosure: OutboundDisclosure,
    language: SpokenLanguage,
    onSend: () -> Unit,
    onKeepOnDevice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    val wording = outboundWording(language)

    MiraCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = wording.question,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        MiraListRow(
            title = wording.appLine,
            trailing = { DisclosureValue(disclosure.appLabel ?: wording.unknownApp) },
        )
        MiraListRow(
            title = wording.screenTextLine,
            trailing = {
                DisclosureValue(
                    if (disclosure.includesScreenText) {
                        wording.characters(disclosure.characterCount)
                    } else {
                        wording.notIncluded
                    },
                )
            },
        )
        MiraListRow(
            title = wording.screenshotLine,
            trailing = {
                DisclosureValue(if (disclosure.includesScreenshot) wording.included else wording.notIncluded)
            },
        )

        MiraDivider()
        Spacer(Modifier.height(spacing.sm))
        Text(
            text = wording.perRequest,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(spacing.lg))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            MiraButton(
                text = wording.send,
                onClick = onSend,
                modifier = Modifier.weight(1f),
                style = MiraButtonStyle.SECONDARY,
            )
            MiraButton(
                text = wording.keepOnDevice,
                onClick = onKeepOnDevice,
                modifier = Modifier.weight(1f),
                style = MiraButtonStyle.PRIMARY,
            )
        }
    }
}

/** The answer half of a disclosure line: plain, never dimmed, because it is the part that matters. */
@Composable
private fun DisclosureValue(value: String) {
    Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Preview
@Composable
private fun AssistContextCardPreview() {
    MiraPreviewSurface {
        AssistContextCard(
            disclosure = OutboundDisclosure(
                appLabel = "Chrome",
                includesScreenText = true,
                includesScreenshot = false,
                characterCount = 1240,
            ),
            language = SpokenLanguage.ENGLISH,
            onSend = {},
            onKeepOnDevice = {},
        )
        AssistContextCard(
            disclosure = OutboundDisclosure(
                appLabel = null,
                includesScreenText = false,
                includesScreenshot = false,
                characterCount = 38,
            ),
            language = SpokenLanguage.BENGALI,
            onSend = {},
            onKeepOnDevice = {},
        )
    }
}
