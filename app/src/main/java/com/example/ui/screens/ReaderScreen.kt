package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.ConvertedBook
import com.example.reader.ReaderFontFamily
import com.example.reader.ReaderTextAlign
import com.example.reader.ReaderTheme
import com.example.ui.components.EpubTextRenderer
import com.example.ui.components.formatBytes
import com.example.ui.viewmodel.ConverterViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ConverterViewModel,
    onNavigateToLibrary: () -> Unit,
    onShareEpub: ((String, String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val activeBook by viewModel.activeReaderBook.collectAsStateWithLifecycle()
    val activeBookRecord by viewModel.activeBookRecord.collectAsStateWithLifecycle()
    val currentChapterIndex by viewModel.currentChapterIndex.collectAsStateWithLifecycle()
    val settings by viewModel.readerSettings.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingReader.collectAsStateWithLifecycle()
    val libraryBooks by viewModel.libraryBooks.collectAsStateWithLifecycle()

    var showTocSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showSwitchBookSheet by remember { mutableStateOf(false) }
    var isSearchVisible by remember { mutableStateOf(false) }
    var chapterSearchQuery by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // File picker to open ANY EPUB file directly
    val epubPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.openEpubFromUri(it) }
    }

    // Reset scroll when chapter changes
    LaunchedEffect(currentChapterIndex) {
        scrollState.scrollTo(0)
    }

    // Periodically sync scroll offset for reading progress persistence
    LaunchedEffect(scrollState.value) {
        viewModel.updateReadingScrollOffset(scrollState.value)
    }

    // Loading State
    if (isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("reader_loading_spinner")
                )
                Text(
                    text = "Opening & Rendering EPUB...",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        return
    }

    // Empty / Shelf State: When no book is currently loaded
    if (activeBook == null || activeBook?.chapters.isNullOrEmpty()) {
        ReaderShelfView(
            convertedBooks = libraryBooks,
            onOpenBook = { book -> viewModel.openBookInReader(book) },
            onPickEpubFile = {
                epubPickerLauncher.launch(
                    arrayOf("application/epub+zip", "application/octet-stream", "*/*")
                )
            },
            onNavigateToLibrary = onNavigateToLibrary,
            modifier = modifier
        )
        return
    }

    val book = activeBook!!
    val chapters = book.chapters
    val currentChapter = chapters.getOrNull(currentChapterIndex) ?: chapters.first()

    // Reading Progress Calculations
    val chapterScrollProgress by remember {
        derivedStateOf {
            if (scrollState.maxValue > 0) {
                (scrollState.value.toFloat() / scrollState.maxValue.toFloat()).coerceIn(0f, 1f)
            } else 0f
        }
    }

    val overallBookProgress by remember {
        derivedStateOf {
            if (chapters.isNotEmpty()) {
                ((currentChapterIndex.toFloat() + chapterScrollProgress) / chapters.size.toFloat()).coerceIn(0f, 1f)
            } else 0f
        }
    }

    // Table of Contents Sheet
    if (showTocSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTocSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Table of Contents",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${chapters.size} Chapters • ${(overallBookProgress * 100).toInt()}% completed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Ch. ${currentChapterIndex + 1}/${chapters.size}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Progress bar inside TOC sheet
                LinearProgressIndicator(
                    progress = { overallBookProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider()

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .padding(vertical = 8.dp)
                ) {
                    itemsIndexed(chapters) { index, ch ->
                        val isSelected = index == currentChapterIndex
                        val isFinished = index < currentChapterIndex

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    viewModel.setChapter(index)
                                    showTocSheet = false
                                },
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    )
                                    Text(
                                        text = ch.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Current chapter",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else if (isFinished) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Read",
                                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Switch Book Bottom Sheet
    if (showSwitchBookSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSwitchBookSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Switch Converted Book",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Select any converted EPUB to read immediately",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                ) {
                    items(libraryBooks) { b ->
                        val isCurrent = b.id == activeBookRecord?.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    viewModel.openBookInReader(b)
                                    showSwitchBookSheet = false
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Book,
                                    contentDescription = null,
                                    tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = b.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${b.author} • ${b.chapterCount} chapters",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isCurrent) {
                                    Text(
                                        text = "Reading",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Appearance & Typography Sheet
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Reader Appearance",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )

                // Page Theme (Light, Sepia, Mint, Charcoal Dark, OLED Black)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Theme & Background",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ReaderTheme.values().forEach { th ->
                            val isSelected = settings.theme == th
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        viewModel.updateReaderSettings { it.copy(theme = th) }
                                    },
                                color = th.backgroundColor,
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray
                                )
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = th.displayName,
                                        color = th.textColor,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Font Family selector
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Font Family",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ReaderFontFamily.values().forEach { fam ->
                            FilterChip(
                                selected = settings.fontFamily == fam,
                                onClick = {
                                    viewModel.updateReaderSettings { it.copy(fontFamily = fam) }
                                },
                                label = { Text(fam.displayName.substringBefore(" ")) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Font Size Stepper & Slider
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Font Size",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "${settings.fontSizeSp.toInt()} sp",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = settings.fontSizeSp,
                        onValueChange = { size ->
                            viewModel.updateReaderSettings { it.copy(fontSizeSp = size) }
                        },
                        valueRange = 13f..28f,
                        steps = 14
                    )
                }

                // Text Alignment (Justify vs Left)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Text Alignment",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = settings.textAlign == ReaderTextAlign.JUSTIFY,
                            onClick = {
                                viewModel.updateReaderSettings { it.copy(textAlign = ReaderTextAlign.JUSTIFY) }
                            },
                            leadingIcon = { Icon(Icons.Default.FormatAlignJustify, contentDescription = null) },
                            label = { Text("Justified") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = settings.textAlign == ReaderTextAlign.LEFT,
                            onClick = {
                                viewModel.updateReaderSettings { it.copy(textAlign = ReaderTextAlign.LEFT) }
                            },
                            leadingIcon = { Icon(Icons.Default.FormatAlignLeft, contentDescription = null) },
                            label = { Text("Left-Aligned") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Line Spacing Multiplier
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Line Spacing",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            1.4f to "Compact",
                            1.65f to "Normal",
                            1.95f to "Relaxed"
                        ).forEach { (multiplier, label) ->
                            FilterChip(
                                selected = kotlin.math.abs(settings.lineSpacingMultiplier - multiplier) < 0.1f,
                                onClick = {
                                    viewModel.updateReaderSettings { it.copy(lineSpacingMultiplier = multiplier) }
                                },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }

    // MAIN DEDICATED READER SCREEN
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(settings.theme.backgroundColor)
            .testTag("reader_screen_main")
    ) {
        // Top App Bar
        Surface(
            color = settings.theme.backgroundColor,
            shadowElevation = 2.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onNavigateToLibrary,
                        modifier = Modifier.testTag("reader_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to shelf",
                            tint = settings.theme.textColor
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = settings.theme.textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${currentChapter.title} • ${(overallBookProgress * 100).toInt()}% read",
                            style = MaterialTheme.typography.bodySmall,
                            color = settings.theme.textColor.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row {
                        // Switch book icon
                        if (libraryBooks.size > 1) {
                            IconButton(
                                onClick = { showSwitchBookSheet = true },
                                modifier = Modifier.testTag("reader_switch_book_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = "Switch book",
                                    tint = settings.theme.textColor
                                )
                            }
                        }

                        // Search in Chapter Toggle
                        IconButton(
                            onClick = {
                                isSearchVisible = !isSearchVisible
                                if (!isSearchVisible) chapterSearchQuery = ""
                            },
                            modifier = Modifier.testTag("reader_search_button")
                        ) {
                            Icon(
                                imageVector = if (isSearchVisible) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = if (isSearchVisible) "Close search" else "Search in chapter",
                                tint = if (isSearchVisible) settings.theme.accentColor else settings.theme.textColor
                            )
                        }

                        // Share EPUB
                        if (activeBookRecord != null && onShareEpub != null) {
                            IconButton(
                                onClick = {
                                    activeBookRecord?.let { book ->
                                        onShareEpub(book.epubFilePath, book.title)
                                    }
                                },
                                modifier = Modifier.testTag("reader_share_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share EPUB",
                                    tint = settings.theme.textColor
                                )
                            }
                        }

                        // Quick Dark Mode Toggle
                        IconButton(
                            onClick = { viewModel.toggleReaderDarkTheme() },
                            modifier = Modifier.testTag("reader_dark_mode_toggle")
                        ) {
                            Icon(
                                imageVector = if (settings.theme.isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = if (settings.theme.isDark) "Switch to Light Mode" else "Switch to Dark Mode",
                                tint = settings.theme.textColor
                            )
                        }

                        // Table of Contents
                        IconButton(
                            onClick = { showTocSheet = true },
                            modifier = Modifier.testTag("reader_toc_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                contentDescription = "Table of contents",
                                tint = settings.theme.textColor
                            )
                        }

                        // Appearance Settings
                        IconButton(
                            onClick = { showSettingsSheet = true },
                            modifier = Modifier.testTag("reader_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatSize,
                                contentDescription = "Reader typography and settings",
                                tint = settings.theme.textColor
                            )
                        }
                    }
                }

                // Inline Chapter Search Bar
                AnimatedVisibility(
                    visible = isSearchVisible,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    val matchCount = remember(currentChapter.paragraphs, chapterSearchQuery) {
                        if (chapterSearchQuery.isBlank()) 0
                        else {
                            currentChapter.paragraphs.sumOf { paragraph ->
                                var count = 0
                                var idx = 0
                                while (idx < paragraph.length) {
                                    val found = paragraph.indexOf(chapterSearchQuery, idx, ignoreCase = true)
                                    if (found == -1) break
                                    count++
                                    idx = found + chapterSearchQuery.length
                                }
                                count
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(settings.theme.backgroundColor)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = chapterSearchQuery,
                            onValueChange = { chapterSearchQuery = it },
                            placeholder = {
                                Text(
                                    "Search in chapter...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = settings.theme.textColor.copy(alpha = 0.5f)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = settings.theme.accentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                if (chapterSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { chapterSearchQuery = "" }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Clear search",
                                            tint = settings.theme.textColor.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = settings.theme.textColor.copy(alpha = 0.05f),
                                unfocusedContainerColor = settings.theme.textColor.copy(alpha = 0.03f),
                                focusedTextColor = settings.theme.textColor,
                                unfocusedTextColor = settings.theme.textColor,
                                focusedIndicatorColor = settings.theme.accentColor,
                                unfocusedIndicatorColor = settings.theme.textColor.copy(alpha = 0.2f)
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("reader_search_input")
                        )

                        if (chapterSearchQuery.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (matchCount > 0) settings.theme.accentColor.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Text(
                                    text = if (matchCount > 0) "$matchCount found" else "No match",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (matchCount > 0) settings.theme.accentColor else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Dedicated Reading Progress Bar (overall book reading progress)
                LinearProgressIndicator(
                    progress = { overallBookProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.5.dp)
                        .testTag("reader_pinned_progress_bar"),
                    color = settings.theme.accentColor,
                    trackColor = settings.theme.textColor.copy(alpha = 0.12f)
                )
            }
        }

        // Dedicated Text-Rendering Component inside Scrollable Canvas
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 22.dp, vertical = 12.dp)
                .testTag("reader_text_scroll_container")
        ) {
            EpubTextRenderer(
                chapter = currentChapter,
                settings = settings,
                bookTitle = book.title,
                totalChapters = chapters.size,
                searchQuery = chapterSearchQuery,
                onNextChapter = {
                    viewModel.nextChapter()
                },
                onNavigateToLibrary = onNavigateToLibrary
            )
        }

        // Bottom Chapter Pager & Progress Status Bar
        Surface(
            color = settings.theme.backgroundColor,
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { viewModel.previousChapter() },
                        enabled = currentChapterIndex > 0,
                        modifier = Modifier.testTag("reader_prev_chapter_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Prev Chapter")
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Ch. ${currentChapterIndex + 1} of ${chapters.size}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = settings.theme.textColor
                        )
                        Text(
                            text = "${(overallBookProgress * 100).toInt()}% of book",
                            style = MaterialTheme.typography.labelSmall,
                            color = settings.theme.accentColor
                        )
                    }

                    TextButton(
                        onClick = { viewModel.nextChapter() },
                        enabled = currentChapterIndex < chapters.size - 1,
                        modifier = Modifier.testTag("reader_next_chapter_btn")
                    ) {
                        Text("Next Chapter")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

/**
 * Dedicated Reader Shelf View shown when no book is currently loaded,
 * allowing users to pick any EPUB file or choose from their converted EPUB books.
 */
@Composable
fun ReaderShelfView(
    convertedBooks: List<ConvertedBook>,
    onOpenBook: (ConvertedBook) -> Unit,
    onPickEpubFile: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Hero Card to Open EPUB file
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("open_epub_file_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Open EPUB File",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Read any converted EPUB or external eBook",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }

                Text(
                    text = "The integrated text-rendering engine parses chapters, headers, and formatting directly inside the app with full dark mode support.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                )

                Button(
                    onClick = onPickEpubFile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pick_epub_file_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select EPUB from Device")
                }
            }
        }

        // Converted Books Shelf Section
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Converted Books (${convertedBooks.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                if (convertedBooks.isNotEmpty()) {
                    TextButton(onClick = onNavigateToLibrary) {
                        Text("View All")
                    }
                }
            }

            if (convertedBooks.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "No Converted Books Yet",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Convert any PDF in the 'Convert' tab or open an existing EPUB above to start reading.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                convertedBooks.forEach { book ->
                    val totalCh = book.chapterCount.coerceAtLeast(1)
                    val readCh = (book.lastReadChapterIndex + 1).coerceAtMost(totalCh)
                    val progressFloat = (readCh.toFloat() / totalCh.toFloat()).coerceIn(0f, 1f)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onOpenBook(book) }
                            .testTag("shelf_book_${book.id}"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Cover Thumbnail or Fallback
                            Box(
                                modifier = Modifier
                                    .width(52.dp)
                                    .height(72.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.secondary
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (book.coverImagePath != null && File(book.coverImagePath).exists()) {
                                    AsyncImage(
                                        model = File(book.coverImagePath),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Book,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Book Info & Reading Progress Bar
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = book.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = book.author,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                // Reading Progress Bar
                                LinearProgressIndicator(
                                    progress = { progressFloat },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .testTag("shelf_book_progress_${book.id}"),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Ch. $readCh of $totalCh",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${(progressFloat * 100).toInt()}% read",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Button(
                                onClick = { onOpenBook(book) },
                                modifier = Modifier.testTag("shelf_read_now_btn_${book.id}"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Read")
                            }
                        }
                    }
                }
            }
        }
    }
}
