package com.rayluxeo.aida.ui.scanner

import com.rayluxeo.aida.core.design.component.MiraIcons

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.fadingHorizontalEdges
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraButtonStyle
import com.rayluxeo.aida.core.design.component.MiraCard
import com.rayluxeo.aida.core.design.component.MiraChip
import com.rayluxeo.aida.core.design.component.MiraInlineNotice
import com.rayluxeo.aida.core.design.component.MiraLoadingIndicator
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.core.design.component.MiraScaffold
import com.rayluxeo.aida.core.design.component.MiraTextField
import com.rayluxeo.aida.core.design.component.MiraTopBar
import com.rayluxeo.aida.data.scanner.ScanImageStorage
import com.rayluxeo.aida.domain.model.ExpenseCategory
import com.rayluxeo.aida.domain.scanner.ScanType
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ScannerScreen(
    onNavigateUp: () -> Unit,
    onAskMira: (String) -> Unit,
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val words = scannerWording(state.language)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var tempPhotoPath by remember { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success && tempPhotoPath != null) {
            val file = File(tempPhotoPath!!)
            if (file.exists()) {
                viewModel.onImageCaptured(
                    imagePath = file.absolutePath,
                    relativePath = "scans/${file.name}",
                    sizeBytes = file.length(),
                )
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val storage = ScanImageStorage(context)
                    val stored = storage.saveInputStream(inputStream, prefix = "imported")
                    if (stored != null) {
                        viewModel.onImageCaptured(
                            imagePath = stored.absolutePath,
                            relativePath = stored.relativePath,
                            sizeBytes = stored.sizeBytes,
                        )
                    }
                }
            }
        }
    }

    fun launchCamera() {
        val storageDir = File(context.filesDir, "scans")
        if (!storageDir.exists()) storageDir.mkdirs()
        val photoFile = File(storageDir, "scan_${System.currentTimeMillis()}.jpg")
        tempPhotoPath = photoFile.absolutePath
        val authority = "${context.packageName}.fileprovider"
        val uri = runCatching {
            FileProvider.getUriForFile(context, authority, photoFile)
        }.getOrNull()

        if (uri != null) {
            tempPhotoUri = uri
            cameraLauncher.launch(uri)
        }
    }

    MiraScaffold(
        topBar = {
            MiraTopBar(
                title = words.title,
                onBack = onNavigateUp,
                backContentDescription = words.back,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.md),
        ) {
            state.notice?.let { noticeMsg ->
                MiraInlineNotice(
                    text = noticeMsg,
                    tone = if (state.isSavedSuccess) MiraNoticeTone.CONFIRM else MiraNoticeTone.INFO,
                    onDismiss = { viewModel.dismissNotice() },
                )
            }

            // Scan Type Selector Tabs
            Text(
                text = words.scanTypeSection,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val scanTypeScroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fadingHorizontalEdges(scanTypeScroll)
                    .horizontalScroll(scanTypeScroll),
                horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
            ) {
                ScanType.entries.forEach { scanType ->
                    val selected = state.scanType == scanType
                    MiraChip(
                        text = words.scanTypeLabel(scanType),
                        selected = selected,
                        onClick = { viewModel.setScanType(scanType) },
                    )
                }
            }

            // Action Buttons: Camera & Import
            // Stacked full-width rather than side by side: an icon and a label like "Take photo" or
            // "Import image" (and their longer Bengali forms) do not fit in half the row, and a
            // button that ellipsises to "Take ph…" reads as broken.
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
            ) {
                MiraButton(
                    text = words.takePhoto,
                    onClick = { launchCamera() },
                    leadingIcon = MiraIcons.CameraAlt,
                    modifier = Modifier.fillMaxWidth(),
                )

                MiraButton(
                    text = words.importImage,
                    style = MiraButtonStyle.SECONDARY,
                    onClick = { galleryLauncher.launch("image/*") },
                    leadingIcon = MiraIcons.Image,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Image Preview Card
            if (state.capturedImagePath != null) {
                MiraCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MiraTheme.spacing.md),
                        verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
                    ) {
                        Text(
                            text = words.imagePreview,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        val bitmap = remember(state.capturedImagePath) {
                            BitmapFactory.decodeFile(state.capturedImagePath)
                        }

                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = words.scannedImageDescription,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        shape = MaterialTheme.shapes.medium,
                                    ),
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = MiraIcons.FilterBAndW,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(MiraTheme.spacing.xs))
                                Text(
                                    text = words.cleanImage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }

                            Switch(
                                checked = state.isCleanedImage,
                                onCheckedChange = { viewModel.toggleCleanImage() },
                            )
                        }
                    }
                }
            }

            if (state.isProcessingOcr) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MiraTheme.spacing.lg),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
                    ) {
                        MiraLoadingIndicator()
                        Text(
                            text = words.processingOcr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Extracted Information Card
            if (state.ocrResult != null || state.extractedText.isNotBlank()) {
                MiraCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MiraTheme.spacing.md),
                        verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
                    ) {
                        Text(
                            text = words.extractedInformation,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )

                        when (state.scanType) {
                            ScanType.RECEIPT, ScanType.INVOICE -> {
                                ReceiptFormSection(
                                    state = state,
                                    viewModel = viewModel,
                                    words = words,
                                )
                            }
                            ScanType.BUSINESS_CARD -> {
                                BusinessCardFormSection(
                                    state = state,
                                    viewModel = viewModel,
                                    words = words,
                                )
                            }
                            ScanType.DOCUMENT, ScanType.SCREENSHOT -> {
                                DocumentFormSection(
                                    state = state,
                                    viewModel = viewModel,
                                    words = words,
                                    onAskMira = onAskMira,
                                )
                            }
                            ScanType.HANDWRITING -> {
                                HandwritingFormSection(
                                    state = state,
                                    viewModel = viewModel,
                                    words = words,
                                    onAskMira = onAskMira,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(MiraTheme.spacing.xl))
        }
    }
}

@Composable
private fun ReceiptFormSection(
    state: ScannerUiState,
    viewModel: ScannerViewModel,
    words: ScannerWording,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm)) {
        MiraTextField(
            value = state.merchant,
            onValueChange = { viewModel.updateMerchant(it) },
            label = words.merchantLabel,
        )

        MiraTextField(
            value = state.amountText,
            onValueChange = { viewModel.updateAmount(it) },
            label = words.amountLabel,
        )

        Text(
            text = words.expenseCategorySection,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val categoryScroll = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fadingHorizontalEdges(categoryScroll)
                .horizontalScroll(categoryScroll),
            horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
        ) {
            ExpenseCategory.entries.forEach { cat ->
                MiraChip(
                    text = words.expenseCategoryLabel(cat),
                    selected = state.category == cat,
                    onClick = { viewModel.updateCategory(cat) },
                )
            }
        }

        MiraButton(
            text = words.saveExpense,
            onClick = { viewModel.saveExpense() },
            leadingIcon = MiraIcons.Save,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BusinessCardFormSection(
    state: ScannerUiState,
    viewModel: ScannerViewModel,
    words: ScannerWording,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm)) {
        MiraTextField(
            value = state.cardName,
            onValueChange = { viewModel.updateCardName(it) },
            label = words.cardNameLabel,
        )

        MiraTextField(
            value = state.cardCompany,
            onValueChange = { viewModel.updateCardCompany(it) },
            label = words.cardCompanyLabel,
        )

        MiraTextField(
            value = state.cardPhone,
            onValueChange = { viewModel.updateCardPhone(it) },
            label = words.cardPhoneLabel,
        )

        MiraTextField(
            value = state.cardEmail,
            onValueChange = { viewModel.updateCardEmail(it) },
            label = words.cardEmailLabel,
        )

        MiraTextField(
            value = state.cardWebsite,
            onValueChange = { viewModel.updateCardWebsite(it) },
            label = words.cardWebsiteLabel,
        )

        MiraTextField(
            value = state.cardAddress,
            onValueChange = { viewModel.updateCardAddress(it) },
            label = words.cardAddressLabel,
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
        ) {
            MiraButton(
                text = words.createClient,
                onClick = { viewModel.createClientFromCard() },
                leadingIcon = MiraIcons.PersonAdd,
                modifier = Modifier.fillMaxWidth(),
            )

            MiraButton(
                text = words.saveNote,
                style = MiraButtonStyle.SECONDARY,
                onClick = { viewModel.createNoteFromCard() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DocumentFormSection(
    state: ScannerUiState,
    viewModel: ScannerViewModel,
    words: ScannerWording,
    onAskMira: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm)) {
        MiraTextField(
            value = state.documentTitle,
            onValueChange = { viewModel.updateDocumentTitle(it) },
            label = words.documentTitleLabel,
        )

        MiraTextField(
            value = state.extractedText,
            onValueChange = { viewModel.updateExtractedText(it) },
            label = words.extractedTextLabel,
            singleLine = false,
            maxLines = 6,
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
        ) {
            MiraButton(
                text = words.saveDocument,
                onClick = { viewModel.saveDocumentFile() },
                modifier = Modifier.fillMaxWidth(),
            )

            MiraButton(
                text = words.saveAsNote,
                style = MiraButtonStyle.SECONDARY,
                onClick = { viewModel.saveDocumentAsNote() },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        MiraButton(
            text = words.askMiraAboutDocument,
            style = MiraButtonStyle.TEXT,
            onClick = { onAskMira(words.askMiraDocumentPrompt(state.extractedText)) },
            leadingIcon = MiraIcons.AutoAwesome,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun HandwritingFormSection(
    state: ScannerUiState,
    viewModel: ScannerViewModel,
    words: ScannerWording,
    onAskMira: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm)) {
        if (state.parsedHandwriting?.isRecognitionLowConfidence == true) {
            MiraInlineNotice(
                text = words.handwritingLowConfidence,
                tone = MiraNoticeTone.INFO,
            )
        }

        MiraTextField(
            value = state.documentTitle,
            onValueChange = { viewModel.updateDocumentTitle(it) },
            label = words.noteTitleLabel,
        )

        MiraTextField(
            value = state.extractedText,
            onValueChange = { viewModel.updateExtractedText(it) },
            label = words.recognizedTextLabel,
            singleLine = false,
            maxLines = 6,
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
        ) {
            MiraButton(
                text = words.saveAsNote,
                onClick = { viewModel.saveDocumentAsNote() },
                modifier = Modifier.fillMaxWidth(),
            )

            MiraButton(
                text = words.saveDocument,
                style = MiraButtonStyle.SECONDARY,
                onClick = { viewModel.saveDocumentFile() },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        MiraButton(
            text = words.askMira,
            style = MiraButtonStyle.TEXT,
            onClick = { onAskMira(words.askMiraHandwritingPrompt(state.extractedText)) },
            leadingIcon = MiraIcons.AutoAwesome,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
