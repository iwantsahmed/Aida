package com.rayluxeo.aida.ui.invoices.delivery

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.model.InvoiceDeliveryDraft
import com.rayluxeo.aida.domain.model.InvoiceDeliveryMethod
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles launching Android platform share intents (Email, WhatsApp, System Chooser).
 *
 * Implements pre-filling of:
 * - Recipient address / number
 * - Subject line
 * - Message body
 * - Attached invoice PDF via secure FileProvider content:// URI
 */
@Singleton
class AndroidInvoiceShareLauncher @Inject constructor() {

    fun launchDelivery(
        context: Context,
        draft: InvoiceDeliveryDraft,
        language: SpokenLanguage = SpokenLanguage.ENGLISH,
    ): AppResult<Unit> {
        return try {
            val words = invoiceDeliveryWording(language)
            val attachmentFile = draft.attachmentPath?.let { File(it) }?.takeIf { it.exists() }
            val attachmentUri: Uri? = attachmentFile?.let { file ->
                val authority = "${context.packageName}.fileprovider"
                FileProvider.getUriForFile(context, authority, file)
            }

            val intent = when (draft.method) {
                InvoiceDeliveryMethod.EMAIL -> createEmailIntent(draft, attachmentUri, words)
                InvoiceDeliveryMethod.WHATSAPP -> createWhatsAppIntent(draft, attachmentUri, context, words)
                InvoiceDeliveryMethod.SYSTEM_SHARE -> createSystemShareIntent(draft, attachmentUri, words)
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            AppResult.Success(Unit)
        } catch (e: Exception) {
            failure(MiraErrorCode.UNKNOWN, "Failed to launch delivery intent: ${e.message}")
        }
    }

    private fun createEmailIntent(
        draft: InvoiceDeliveryDraft,
        attachmentUri: Uri?,
        words: InvoiceDeliveryWording,
    ): Intent {
        // ACTION_SEND with extra email / subject / text / stream
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (attachmentUri != null) "application/pdf" else "message/rfc822"
            if (draft.recipient.isNotBlank() && draft.recipient.contains("@")) {
                putExtra(Intent.EXTRA_EMAIL, arrayOf(draft.recipient))
            }
            putExtra(Intent.EXTRA_SUBJECT, draft.subject)
            putExtra(Intent.EXTRA_TEXT, draft.message)
            if (attachmentUri != null) {
                putExtra(Intent.EXTRA_STREAM, attachmentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        return Intent.createChooser(intent, words.chooserEmailTitle)
    }

    private fun createWhatsAppIntent(
        draft: InvoiceDeliveryDraft,
        attachmentUri: Uri?,
        context: Context,
        words: InvoiceDeliveryWording,
    ): Intent {
        val whatsAppIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (attachmentUri != null) "application/pdf" else "text/plain"
            setPackage("com.whatsapp")
            putExtra(Intent.EXTRA_TEXT, draft.message)
            if (attachmentUri != null) {
                putExtra(Intent.EXTRA_STREAM, attachmentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        // Check if WhatsApp is installed and resolves
        val packageManager = context.packageManager
        val canResolve = whatsAppIntent.resolveActivity(packageManager) != null

        return if (canResolve) {
            whatsAppIntent
        } else {
            // Graceful fallback to system share chooser if WhatsApp is not installed
            createSystemShareIntent(draft, attachmentUri, words)
        }
    }

    private fun createSystemShareIntent(
        draft: InvoiceDeliveryDraft,
        attachmentUri: Uri?,
        words: InvoiceDeliveryWording,
    ): Intent {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (attachmentUri != null) "application/pdf" else "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, draft.subject)
            putExtra(Intent.EXTRA_TEXT, draft.message)
            if (attachmentUri != null) {
                putExtra(Intent.EXTRA_STREAM, attachmentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        return Intent.createChooser(intent, words.chooserShareTitle)
    }
}
