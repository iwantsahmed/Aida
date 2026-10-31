package com.rayluxeo.aida.ui.invoices.delivery

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import com.rayluxeo.aida.core.design.ContinuousRoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.design.MiraPalette
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraCard
import com.rayluxeo.aida.core.design.component.MiraDivider
import com.rayluxeo.aida.core.design.component.MiraIconButton
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.domain.model.InvoiceDeliveryDraft
import com.rayluxeo.aida.domain.model.InvoiceDeliveryMethod
import java.io.File

/**
 * Confirmation dialog before sending or sharing an invoice.
 *
 * Explicitly displays:
 * - Client
 * - Recipient
 * - Invoice Number
 * - Amount
 * - Subject
 * - Message
 * - Attachment
 * - Delivery Method
 *
 * Requires Abu's explicit confirmation before launching the delivery intent.
 */
@Composable
fun InvoiceDeliveryConfirmationDialog(
    draft: InvoiceDeliveryDraft,
    language: SpokenLanguage,
    onMethodSelect: (InvoiceDeliveryMethod) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val words = invoiceDeliveryWording(language)
    Dialog(onDismissRequest = onDismiss) {
        MiraCard(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Top Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = words.deliverInvoice,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = words.confirmBeforeSending,
                            style = MaterialTheme.typography.bodySmall,
                            color = MiraPalette.Muted,
                        )
                    }
                    MiraIconButton(
                        icon = MiraIcons.Close,
                        contentDescription = words.cancelDelivery,
                        onClick = onDismiss,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Delivery Method Selector
                Text(
                    text = words.deliveryMethod,
                    style = MaterialTheme.typography.labelMedium,
                    color = MiraPalette.Muted,
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MethodOption(
                        title = words.email,
                        selected = draft.method == InvoiceDeliveryMethod.EMAIL,
                        onClick = { onMethodSelect(InvoiceDeliveryMethod.EMAIL) },
                        modifier = Modifier.weight(1f),
                    )
                    MethodOption(
                        title = words.whatsApp,
                        selected = draft.method == InvoiceDeliveryMethod.WHATSAPP,
                        onClick = { onMethodSelect(InvoiceDeliveryMethod.WHATSAPP) },
                        modifier = Modifier.weight(1f),
                    )
                    MethodOption(
                        title = words.share,
                        selected = draft.method == InvoiceDeliveryMethod.SYSTEM_SHARE,
                        onClick = { onMethodSelect(InvoiceDeliveryMethod.SYSTEM_SHARE) },
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                MiraDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // Metadata Rows
                PreviewRow(label = words.client, value = draft.clientName)
                PreviewRow(
                    label = words.recipient,
                    value = draft.recipient.ifBlank { words.recipientNotSpecified },
                    isAccent = draft.recipient.isBlank(),
                )
                PreviewRow(label = words.invoiceNumber, value = draft.invoiceNumber, isBold = true)
                PreviewRow(label = words.amount, value = draft.amountFormatted, isBold = true)

                // Attachment
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = words.attachment,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MiraPalette.Muted,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = draft.attachmentPath?.let { File(it).name } ?: "invoice.pdf",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                MiraDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // Subject
                Text(
                    text = words.subject,
                    style = MaterialTheme.typography.labelMedium,
                    color = MiraPalette.Muted,
                )
                Text(
                    text = draft.subject,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Message Body Preview
                Text(
                    text = words.messageBody,
                    style = MaterialTheme.typography.labelMedium,
                    color = MiraPalette.Muted,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = ContinuousRoundedCornerShape(8.dp),
                        )
                        .padding(12.dp),
                ) {
                    Text(
                        text = draft.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Confirmation Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MiraButton(
                        text = words.cancel,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    MiraButton(
                        text = when (draft.method) {
                            InvoiceDeliveryMethod.EMAIL -> words.sendEmail
                            InvoiceDeliveryMethod.WHATSAPP -> words.sendWhatsApp
                            InvoiceDeliveryMethod.SYSTEM_SHARE -> words.share
                        },
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MethodOption(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) MiraPalette.Accent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val textCol = if (selected) MiraPalette.Accent else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .background(bg, ContinuousRoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            ),
            color = textCol,
        )
    }
}

@Composable
private fun PreviewRow(
    label: String,
    value: String,
    isBold: Boolean = false,
    isAccent: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MiraPalette.Muted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            ),
            color = if (isAccent) MiraPalette.Accent else MaterialTheme.colorScheme.onSurface,
        )
    }
}
