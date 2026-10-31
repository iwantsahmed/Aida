package com.rayluxeo.aida.ui.notes

import com.rayluxeo.aida.core.design.component.MiraIcons

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraDivider
import com.rayluxeo.aida.core.design.component.MiraListRow
import com.rayluxeo.aida.core.design.component.MiraPreviewSurface

/**
 * One verb in a sheet that acts on a note: a glyph, a word, and a tap.
 *
 * There is one of these rather than one per sheet. The list's sheet and the editor's sheet offer
 * the same three verbs with the same glyphs, and each had grown a private copy of this row that
 * was byte-for-byte the other — which is how a destructive verb ends up danger-coloured in one
 * place and ivory in the other, with nothing failing to compile. The two sheets still differ in
 * when they close, because that difference is real; how a row looks is not theirs to decide.
 */
@Composable
internal fun NoteSheetActionRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    val tint = if (destructive) {
        MiraTheme.colors.danger
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    MiraListRow(
        title = title,
        modifier = modifier,
        onClick = onClick,
        leading = {
            Icon(imageVector = icon, contentDescription = null, tint = tint)
        },
    )
}

@Preview
@Composable
private fun NoteSheetActionRowPreview() {
    MiraPreviewSurface {
        NoteSheetActionRow(
            title = EnglishNotesWording.pin,
            icon = MiraIcons.PushPin,
            onClick = {},
        )
        MiraDivider()
        NoteSheetActionRow(
            title = EnglishNotesWording.delete,
            icon = MiraIcons.DeleteOutline,
            onClick = {},
            destructive = true,
        )
    }
}
