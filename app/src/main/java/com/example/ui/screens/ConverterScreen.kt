package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.FilterDrama
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.converter.ChapterSplitMode
import com.example.converter.ConversionOptions
import com.example.converter.ConversionStage
import com.example.converter.EpubFontFamily
import com.example.converter.EpubMargin
import com.example.converter.ImageCompressionLevel
import com.example.converter.TypographyPreset
import com.example.ui.components.ConversionProgressDialog
import com.example.ui.components.ConversionSuccessDialog
import com.example.ui.components.UnlimitedBadge
import com.example.ui.components.formatBytes
import com.example.ui.theme.GreenSuccess
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.RedError
import com.example.ui.theme.TealAccent
import com.example.ui.viewmodel.BatchItem
import com.example.ui.viewmodel.ConverterViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConverterScreen(
    viewModel: ConverterViewModel,
    onNavigateToReader: () -> Unit,
    onShareEpub: (String, String) -> Unit,
    onNavigateToLibrary: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val selectedUri by viewModel.selectedPdfUri.collectAsStateWithLifecycle()
    val metadata by viewModel.pdfMetadata.collectAsStateWithLifecycle()
    val isLoadingMetadata by viewModel.isLoadingMetadata.collectAsStateWithLifecycle()
    val options by viewModel.conversionOptions.collectAsStateWithLifecycle()
    val isConverting by viewModel.isConverting.collectAsStateWithLifecycle()
    val progress by viewModel.conversionProgress.collectAsStateWithLifecycle()
    val lastConvertedBook by viewModel.lastConvertedBook.collectAsStateWithLifecycle()

    val batchQueue by viewModel.batchQueue.collectAsStateWithLifecycle()
    val isBatchRunning by viewModel.isBatchRunning.collectAsStateWithLifecycle()
    val batchCurrentIndex by viewModel.batchCurrentIndex.collectAsStateWithLifecycle()

    var isMultiSelectMode by remember { mutableStateOf(false) }

    val singleFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.onPdfSelected(it) }
    }

    val multiFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addBatchFiles(uris)
            isMultiSelectMode = true
        }
    }

    val scrollState = rememberScrollState()

    // Conversion progress popup
    if (isConverting) {
        ConversionProgressDialog(
            progress = progress,
            onCancel = { viewModel.cancelConversion() }
        )
    }

    // Success popup
    lastConvertedBook?.let { book ->
        ConversionSuccessDialog(
            book = book,
            onOpenReader = {
                viewModel.openBookInReader(book)
                onNavigateToReader()
            },
            onShare = {
                onShareEpub(book.epubFilePath, book.title)
            },
            onDismiss = {
                viewModel.resetConversion()
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // App header with badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "PDF to EPUB",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Universal eBook Studio",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                UnlimitedBadge()
                val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
                IconButton(
                    onClick = { viewModel.toggleDarkMode() },
                    modifier = Modifier.testTag("converter_dark_mode_toggle")
                ) {
                    Icon(
                        imageVector = if (themeMode == com.example.reader.ThemeMode.DARK) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Toggle Dark Mode",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Conversion Mode Selector (Single Document vs Multi-Select Batch)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("converter_mode_selector"),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilterChip(
                selected = !isMultiSelectMode,
                onClick = { isMultiSelectMode = false },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text("Single File") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("mode_chip_single")
            )

            FilterChip(
                selected = isMultiSelectMode,
                onClick = { isMultiSelectMode = true },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Queue,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = {
                    Text(if (batchQueue.isNotEmpty()) "Batch Queue (${batchQueue.size})" else "Multi-Select (Batch)")
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("mode_chip_multi_select")
            )
        }

        // PDF Selector Area
        if (isMultiSelectMode) {
            // MULTI-SELECT MODE
            if (batchQueue.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { multiFilePickerLauncher.launch("application/pdf") }
                        .border(
                            width = 2.dp,
                            brush = Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .testTag("multi_pdf_dropzone_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Queue,
                                contentDescription = "Select Multiple PDFs",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Multi-Select PDF Picker",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Select multiple PDF files to queue for batch conversion. Each book will be parsed and saved to your library.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.Layers,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Long-press files in picker to select multiple",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { multiFilePickerLauncher.launch("application/pdf") },
                            modifier = Modifier.testTag("select_multiple_pdfs_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Queue Multiple PDFs")
                        }
                    }
                }

                // Batch Feature Highlights
                Text(
                    text = "Batch Processing Features",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FeatureMiniCard(
                        icon = Icons.Default.Layers,
                        title = "Multi-Queue",
                        subtitle = "Convert 10+ PDFs",
                        modifier = Modifier.weight(1f)
                    )
                    FeatureMiniCard(
                        icon = Icons.Default.AutoAwesome,
                        title = "Auto-Extract",
                        subtitle = "Chapters per file",
                        modifier = Modifier.weight(1f)
                    )
                    FeatureMiniCard(
                        icon = Icons.Default.Speed,
                        title = "Direct Library",
                        subtitle = "Saved automatically",
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                // Batch Queue Active View
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("batch_queue_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Header row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Queue,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Batch Queue (${batchQueue.size})",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    val pendingCount = batchQueue.count { it.status == "Pending" }
                                    val successCount = batchQueue.count { it.status == "Success" }
                                    Text(
                                        text = if (isBatchRunning) "Converting file ${batchCurrentIndex + 1} of ${batchQueue.size}"
                                        else "$successCount completed • $pendingCount pending",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (!isBatchRunning) {
                                TextButton(
                                    onClick = { viewModel.clearBatch() },
                                    modifier = Modifier.testTag("clear_batch_button")
                                ) {
                                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        // Progress indicator if batch running
                        if (isBatchRunning) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val total = batchQueue.size.toFloat().coerceAtLeast(1f)
                                val currentItem = batchQueue.getOrNull(batchCurrentIndex)
                                val overallProgress = ((batchCurrentIndex + (currentItem?.progress ?: 0f)) / total).coerceIn(0f, 1f)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Converting: ${currentItem?.fileName ?: "Document"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${(overallProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { overallProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                        // Queued Items List
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            batchQueue.forEachIndexed { index, item ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PictureAsPdf,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = item.fileName,
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        if (item.fileSizeBytes > 0L) {
                                                            Text(
                                                                text = formatBytes(item.fileSizeBytes),
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            Text(
                                                                text = "•",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                        Text(
                                                            text = when (item.status) {
                                                                "Success" -> "Converted to EPUB"
                                                                "Converting" -> "Converting ${(item.progress * 100).toInt()}%..."
                                                                "Failed" -> "Error: ${item.error ?: "Failed"}"
                                                                else -> "Queued"
                                                            },
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = when (item.status) {
                                                                "Success" -> GreenSuccess
                                                                "Failed" -> RedError
                                                                "Converting" -> MaterialTheme.colorScheme.primary
                                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                            }
                                                        )
                                                    }
                                                }
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                if (item.status == "Converting") {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(20.dp),
                                                        strokeWidth = 2.dp
                                                    )
                                                } else if (item.status == "Success") {
                                                    Icon(
                                                        Icons.Default.CheckCircle,
                                                        contentDescription = "Completed",
                                                        tint = GreenSuccess,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                } else if (item.status == "Failed") {
                                                    Icon(
                                                        Icons.Default.ErrorOutline,
                                                        contentDescription = "Failed",
                                                        tint = RedError,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }

                                                if (item.status != "Converting" && !isBatchRunning) {
                                                    IconButton(
                                                        onClick = { viewModel.removeBatchItem(item) },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Close,
                                                            contentDescription = "Remove file from queue",
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Per-item progress bar if converting
                                        if (item.status == "Converting") {
                                            LinearProgressIndicator(
                                                progress = { item.progress },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(4.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Success banner if all finished
                        if (batchQueue.isNotEmpty() && batchQueue.all { it.status == "Success" }) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = GreenSuccess.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, GreenSuccess.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = GreenSuccess,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "All books converted!",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = GreenSuccess
                                        )
                                    }
                                    onNavigateToLibrary?.let { navLib ->
                                        TextButton(onClick = navLib) {
                                            Text("Open Library", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // Actions row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { multiFilePickerLauncher.launch("application/pdf") },
                                enabled = !isBatchRunning,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add More")
                            }

                            if (isBatchRunning) {
                                OutlinedButton(
                                    onClick = { viewModel.cancelBatchConversion() },
                                    modifier = Modifier
                                        .weight(1.5f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Cancel Batch")
                                }
                            } else {
                                val pendingFiles = batchQueue.count { it.status != "Success" }
                                Button(
                                    onClick = { viewModel.startBatchConversion() },
                                    enabled = pendingFiles > 0,
                                    modifier = Modifier
                                        .weight(1.5f)
                                        .height(48.dp)
                                        .testTag("start_batch_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Convert All ($pendingFiles)")
                                }
                            }
                        }
                    }
                }
            }
        } else if (selectedUri == null) {
            // SINGLE FILE MODE - Dropzone
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { singleFilePickerLauncher.launch("application/pdf") }
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .testTag("pdf_dropzone_card"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = "Select PDF",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Choose Any PDF Document",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Tap to browse files. No page limits, no file size caps, 100% on-device conversion.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { singleFilePickerLauncher.launch("application/pdf") },
                            modifier = Modifier.testTag("select_pdf_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select PDF")
                        }

                        OutlinedButton(
                            onClick = {
                                isMultiSelectMode = true
                                multiFilePickerLauncher.launch("application/pdf")
                            },
                            modifier = Modifier.testTag("multi_select_quick_button")
                        ) {
                            Icon(Icons.Default.Queue, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Queue Batch")
                        }
                    }
                }
            }

            // Feature Highlights
            Text(
                text = "Why Unlimited Converter?",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FeatureMiniCard(
                    icon = Icons.Default.Speed,
                    title = "Zero Limits",
                    subtitle = "1 or 1,000+ pages",
                    modifier = Modifier.weight(1f)
                )
                FeatureMiniCard(
                    icon = Icons.Default.AutoAwesome,
                    title = "Smart Chapters",
                    subtitle = "Auto headings & TOC",
                    modifier = Modifier.weight(1f)
                )
                FeatureMiniCard(
                    icon = Icons.Default.FilterDrama,
                    title = "100% Offline",
                    subtitle = "Private & secure",
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            // Selected Document Info Card (Single File Mode)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("selected_file_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = metadata?.fileName ?: "Selected Document",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isLoadingMetadata) {
                            Text(
                                text = "Reading PDF metadata & structure...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            metadata?.let { meta ->
                                Text(
                                    text = "${meta.pageCount} Pages • ${formatBytes(meta.fileSizeBytes)}" +
                                            if (meta.outlineItems.isNotEmpty()) " • ${meta.outlineItems.size} Bookmarks" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Queue in Batch Button
                    IconButton(
                        onClick = {
                            selectedUri?.let { uri ->
                                viewModel.addBatchFiles(listOf(uri))
                                viewModel.resetConversion()
                                isMultiSelectMode = true
                            }
                        },
                        modifier = Modifier.testTag("add_to_batch_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Queue,
                            contentDescription = "Queue to Batch",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = { viewModel.resetConversion() },
                        modifier = Modifier.testTag("remove_selected_file_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove file",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Options Configuration
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("conversion_settings_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header with Reset Defaults action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Conversion Settings",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        if (options.isCustomized) {
                            TextButton(
                                onClick = { viewModel.resetConversionSettingsToDefaults() },
                                modifier = Modifier.testTag("reset_defaults_button")
                            ) {
                                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset Defaults", style = MaterialTheme.typography.labelMedium)
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = "Default Settings",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                    // Book Title & Author inputs
                    OutlinedTextField(
                        value = options.title,
                        onValueChange = { newTitle ->
                            viewModel.updateConversionOptions { it.copy(title = newTitle) }
                        },
                        label = { Text("Book Title") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("book_title_input"),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Book, contentDescription = null)
                        }
                    )

                    OutlinedTextField(
                        value = options.author,
                        onValueChange = { newAuthor ->
                            viewModel.updateConversionOptions { it.copy(author = newAuthor) }
                        },
                        label = { Text("Author / Creator") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("book_author_input"),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Description, contentDescription = null)
                        }
                    )

                    // Chapter Splitting Mode
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Chapter Splitting Mode",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ChapterSplitMode.values().forEach { mode ->
                                FilterChip(
                                    selected = options.splitMode == mode,
                                    onClick = {
                                        viewModel.updateConversionOptions { it.copy(splitMode = mode) }
                                    },
                                    label = { Text(mode.displayName) },
                                    leadingIcon = if (options.splitMode == mode) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    modifier = Modifier.testTag("split_mode_${mode.name}")
                                )
                            }
                        }
                        Text(
                            text = options.splitMode.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

                    // Live Typography & Layout Preview Card
                    TypographyPreviewCard(options = options)

                    // 1. Font Family
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Font Family",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = options.fontFamily.displayName + if (options.fontFamily == ConversionOptions.DEFAULT_FONT_FAMILY) " (Default)" else "",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            EpubFontFamily.values().forEach { family ->
                                FilterChip(
                                    selected = options.fontFamily == family,
                                    onClick = {
                                        viewModel.updateConversionOptions { it.copy(fontFamily = family) }
                                    },
                                    label = {
                                        Text(family.displayName)
                                    },
                                    leadingIcon = if (options.fontFamily == family) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    modifier = Modifier.testTag("font_family_${family.name}")
                                )
                            }
                        }
                        Text(
                            text = "Category: ${options.fontFamily.fontCategory} • Embedded into EPUB styles",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

                    // 2. Font Size
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.FormatSize, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = "Font Size",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Text(
                                text = "${options.fontSizePt} pt" + if (options.fontSizePt == ConversionOptions.DEFAULT_FONT_SIZE_PT) " (Default)" else "",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Stepper buttons & Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    val newSize = (options.fontSizePt - 1).coerceAtLeast(ConversionOptions.MIN_FONT_SIZE_PT)
                                    viewModel.updateConversionOptions { it.copy(fontSizePt = newSize) }
                                },
                                enabled = options.fontSizePt > ConversionOptions.MIN_FONT_SIZE_PT,
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("decrease_font_size_button")
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease font size")
                            }

                            Slider(
                                value = options.fontSizePt.toFloat(),
                                onValueChange = { value ->
                                    viewModel.updateConversionOptions { it.copy(fontSizePt = value.toInt()) }
                                },
                                valueRange = ConversionOptions.MIN_FONT_SIZE_PT.toFloat()..ConversionOptions.MAX_FONT_SIZE_PT.toFloat(),
                                steps = (ConversionOptions.MAX_FONT_SIZE_PT - ConversionOptions.MIN_FONT_SIZE_PT) - 1,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("font_size_slider")
                            )

                            IconButton(
                                onClick = {
                                    val newSize = (options.fontSizePt + 1).coerceAtMost(ConversionOptions.MAX_FONT_SIZE_PT)
                                    viewModel.updateConversionOptions { it.copy(fontSizePt = newSize) }
                                },
                                enabled = options.fontSizePt < ConversionOptions.MAX_FONT_SIZE_PT,
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("increase_font_size_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase font size")
                            }
                        }

                        // Quick presets
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(12, 14, 16, 18, 20, 24).forEach { size ->
                                FilterChip(
                                    selected = options.fontSizePt == size,
                                    onClick = {
                                        viewModel.updateConversionOptions { it.copy(fontSizePt = size) }
                                    },
                                    label = { Text("${size}pt") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("font_size_preset_$size")
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

                    // 3. Page Margins
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.AspectRatio, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = "Page Margins",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Text(
                                text = "${options.effectiveHorizontalMargin}% sides • ${options.effectiveVerticalMargin}% top/bottom",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Preset chips
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            EpubMargin.values().forEach { marginPreset ->
                                val isSelected = options.margin == marginPreset && options.customMarginPercent == null
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.updateConversionOptions {
                                            it.copy(margin = marginPreset, customMarginPercent = null)
                                        }
                                    },
                                    label = { Text(marginPreset.displayName) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    modifier = Modifier.testTag("margin_preset_${marginPreset.name}")
                                )
                            }
                        }

                        // Custom margin slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Adjust:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = options.effectiveHorizontalMargin.toFloat(),
                                onValueChange = { value ->
                                    val rounded = value.toInt()
                                    viewModel.updateConversionOptions { it.copy(customMarginPercent = rounded) }
                                },
                                valueRange = ConversionOptions.MIN_MARGIN_PERCENT.toFloat()..ConversionOptions.MAX_MARGIN_PERCENT.toFloat(),
                                steps = (ConversionOptions.MAX_MARGIN_PERCENT - ConversionOptions.MIN_MARGIN_PERCENT) - 1,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("margin_slider")
                            )
                            Text(
                                text = "${options.effectiveHorizontalMargin}%",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.width(36.dp)
                            )
                        }

                        Text(
                            text = options.margin.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

                    // 4. Image Compression Level
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Compress, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = "Image Compression Level",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Text(
                                text = "${options.imageCompression.displayName} (${options.effectiveImageQuality}%)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ImageCompressionLevel.values().forEach { compLevel ->
                                val isSelected = options.imageCompression == compLevel && options.customImageQuality == null
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.updateConversionOptions {
                                            it.copy(imageCompression = compLevel, customImageQuality = null)
                                        }
                                    },
                                    label = { Text(compLevel.displayName) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    modifier = Modifier.testTag("compression_level_${compLevel.name}")
                                )
                            }
                        }

                        // Fine-tune image quality slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Quality:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = options.effectiveImageQuality.toFloat(),
                                onValueChange = { value ->
                                    viewModel.updateConversionOptions { it.copy(customImageQuality = value.toInt()) }
                                },
                                valueRange = ConversionOptions.MIN_IMAGE_QUALITY.toFloat()..ConversionOptions.MAX_IMAGE_QUALITY.toFloat(),
                                steps = 13,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("image_quality_slider")
                            )
                            Text(
                                text = "${options.effectiveImageQuality}%",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.width(42.dp)
                            )
                        }

                        Text(
                            text = options.imageCompression.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

                    // Cover Generation Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text(
                                    text = "Extract Cover Art",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    text = "Renders Page 1 as high-res cover image",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = options.extractCover,
                            onCheckedChange = { checked ->
                                viewModel.updateConversionOptions { it.copy(extractCover = checked) }
                            },
                            modifier = Modifier.testTag("extract_cover_switch")
                        )
                    }

                    // Clean Headers & Footers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text(
                                    text = "Strip Header & Footer Artifacts",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    text = "Removes duplicate page numbers from body",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = options.stripHeadersFooters,
                            onCheckedChange = { checked ->
                                viewModel.updateConversionOptions { it.copy(stripHeadersFooters = checked) }
                            }
                        )
                    }
                }
            }

            // Giant CTA Button
            Button(
                onClick = { viewModel.startConversion() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("convert_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Convert to EPUB (Unlimited)",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                )
            }
        }
    }
}

@Composable
fun FeatureMiniCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
fun TypographyPreviewCard(
    options: ConversionOptions,
    modifier: Modifier = Modifier
) {
    val previewComposeFont = when (options.fontFamily) {
        EpubFontFamily.SANS_SERIF -> FontFamily.SansSerif
        EpubFontFamily.SERIF -> FontFamily.Serif
        EpubFontFamily.LITERARY_GEORGIA -> FontFamily.Serif
        EpubFontFamily.MONOSPACE -> FontFamily.Monospace
        EpubFontFamily.HIGH_LEGIBILITY -> FontFamily.Default
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("typography_live_preview_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Preview,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Live Reader Preview",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "${options.fontFamily.displayName} • ${options.fontSizePt}pt",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Simulated ebook page layout
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = Color.White,
                shadowElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = (options.effectiveHorizontalMargin * 2.2).coerceIn(8.0, 36.0).dp,
                            vertical = (options.effectiveVerticalMargin * 2.0).coerceIn(8.0, 24.0).dp
                        )
                ) {
                    Text(
                        text = "Chapter 1: The Beginning",
                        fontFamily = previewComposeFont,
                        fontSize = (options.fontSizePt * 1.15f).sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A237E),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Text(
                        text = "The quick brown fox jumps over the lazy dog. Every chapter in your converted EPUB will be rendered with this exact typeface, scaling, and comfortable page margins.",
                        fontFamily = previewComposeFont,
                        fontSize = (options.fontSizePt * 0.9f).coerceIn(11f, 22f).sp,
                        lineHeight = ((options.fontSizePt * 0.9f) * 1.55f).sp,
                        color = Color(0xFF212121),
                        textAlign = TextAlign.Justify
                    )
                }
            }
        }
    }
}
