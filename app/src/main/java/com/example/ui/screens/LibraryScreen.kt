package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.ConvertedBook
import com.example.ui.components.formatBytes
import com.example.ui.viewmodel.BookFilter
import com.example.ui.viewmodel.BookSortOrder
import com.example.ui.viewmodel.ConverterViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LibraryScreen(
    viewModel: ConverterViewModel,
    onOpenReader: (ConvertedBook) -> Unit,
    onShareEpub: (String, String) -> Unit,
    onNavigateToConvert: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val books by viewModel.libraryBooks.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val bookFilter by viewModel.bookFilter.collectAsStateWithLifecycle()

    var bookToDelete by remember { mutableStateOf<ConvertedBook?>(null) }
    var bookForDetails by remember { mutableStateOf<ConvertedBook?>(null) }
    var bookForEdit by remember { mutableStateOf<ConvertedBook?>(null) }
    var bookToExportToStorage by remember { mutableStateOf<ConvertedBook?>(null) }

    // SAF Create Document launcher for direct EPUB export to device storage
    val exportDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/epub+zip")
    ) { targetUri ->
        val book = bookToExportToStorage
        if (targetUri != null && book != null) {
            try {
                val sourceFile = File(book.epubFilePath)
                if (sourceFile.exists()) {
                    context.contentResolver.openOutputStream(targetUri)?.use { out ->
                        sourceFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                    Toast.makeText(context, "Saved '${book.title}.epub' to storage!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Source EPUB file not found on disk", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
        bookToExportToStorage = null
    }

    val launchExportToStorage: (ConvertedBook) -> Unit = { book ->
        bookToExportToStorage = book
        val safeName = book.title
            .replace(Regex("[^a-zA-Z0-9._ -]"), "_")
            .trim()
            .take(50)
            .ifBlank { "converted_book" } + ".epub"
        exportDocLauncher.launch(safeName)
    }

    // Delete Confirmation Dialog
    if (bookToDelete != null) {
        AlertDialog(
            onDismissRequest = { bookToDelete = null },
            title = { Text("Delete Converted Book?") },
            text = { Text("This will remove '${bookToDelete?.title}' and delete its generated EPUB file from device storage.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        bookToDelete?.let { viewModel.deleteBook(it) }
                        bookToDelete = null
                    },
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { bookToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Book Details Dialog
    bookForDetails?.let { book ->
        BookDetailsDialog(
            book = book,
            onDismiss = { bookForDetails = null },
            onShare = {
                bookForDetails = null
                onShareEpub(book.epubFilePath, book.title)
            },
            onExportToStorage = {
                bookForDetails = null
                launchExportToStorage(book)
            },
            onRead = {
                bookForDetails = null
                onOpenReader(book)
            }
        )
    }

    // Edit Book Dialog
    bookForEdit?.let { book ->
        EditBookDialog(
            book = book,
            onDismiss = { bookForEdit = null },
            onSave = { newTitle, newAuthor ->
                viewModel.updateBookMetadata(book, newTitle, newAuthor)
                Toast.makeText(context, "Book details updated", Toast.LENGTH_SHORT).show()
                bookForEdit = null
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Title & Theme Toggle
            Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "My Books",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${books.size} converted ${if (books.size == 1) "eBook" else "eBooks"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            IconButton(
                onClick = { viewModel.toggleDarkMode() },
                modifier = Modifier.testTag("library_dark_mode_toggle")
            ) {
                Icon(
                    imageVector = if (themeMode == com.example.reader.ThemeMode.DARK) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Toggle Dark Mode",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text("Search by title, author, or file...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                focusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("library_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Category Filter Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = bookFilter == BookFilter.ALL,
                    onClick = { viewModel.setBookFilter(BookFilter.ALL) },
                    label = { Text("All") },
                    modifier = Modifier.testTag("filter_all")
                )
            }
            item {
                FilterChip(
                    selected = bookFilter == BookFilter.FAVORITES,
                    onClick = { viewModel.setBookFilter(BookFilter.FAVORITES) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (bookFilter == BookFilter.FAVORITES) Color(0xFFE91E63) else MaterialTheme.colorScheme.outline
                        )
                    },
                    label = { Text("Favorites") },
                    modifier = Modifier.testTag("filter_favorites")
                )
            }
            item {
                FilterChip(
                    selected = bookFilter == BookFilter.READING,
                    onClick = { viewModel.setBookFilter(BookFilter.READING) },
                    label = { Text("In Progress") },
                    modifier = Modifier.testTag("filter_reading")
                )
            }
            item {
                FilterChip(
                    selected = bookFilter == BookFilter.COMPLETED,
                    onClick = { viewModel.setBookFilter(BookFilter.COMPLETED) },
                    label = { Text("Completed") },
                    modifier = Modifier.testTag("filter_completed")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Sort Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = sortOrder == BookSortOrder.DATE_DESC,
                onClick = { viewModel.setSortOrder(BookSortOrder.DATE_DESC) },
                label = { Text("Recent") }
            )
            FilterChip(
                selected = sortOrder == BookSortOrder.TITLE_ASC,
                onClick = { viewModel.setSortOrder(BookSortOrder.TITLE_ASC) },
                label = { Text("Title A-Z") }
            )
            FilterChip(
                selected = sortOrder == BookSortOrder.PAGES_DESC,
                onClick = { viewModel.setSortOrder(BookSortOrder.PAGES_DESC) },
                label = { Text("Pages") }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Books List or Empty State
        if (books.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Book,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Text(
                        text = if (searchQuery.isNotBlank()) "No matching eBooks found"
                        else if (bookFilter != BookFilter.ALL) "No eBooks in this filter"
                        else "No Converted Books Yet",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = if (searchQuery.isNotBlank()) "Try searching for a different keyword"
                        else if (bookFilter != BookFilter.ALL) "Try selecting 'All' to view all your books"
                        else "Convert your first PDF to standard EPUB format with unlimited pages!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (searchQuery.isBlank() && bookFilter == BookFilter.ALL) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = onNavigateToConvert,
                            modifier = Modifier.testTag("empty_state_convert_button")
                        ) {
                            Text("Convert a PDF Now")
                        }
                    } else if (bookFilter != BookFilter.ALL) {
                        TextButton(onClick = { viewModel.setBookFilter(BookFilter.ALL) }) {
                            Text("Show All Books")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .testTag("library_books_list"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(books, key = { it.id }) { book ->
                    BookListItemCard(
                        book = book,
                        onOpenReader = { onOpenReader(book) },
                        onShare = { onShareEpub(book.epubFilePath, book.title) },
                        onExportToStorage = { launchExportToStorage(book) },
                        onViewDetails = { bookForDetails = book },
                        onEditDetails = { bookForEdit = book },
                        onResetProgress = {
                            viewModel.resetReadingProgress(book)
                            Toast.makeText(context, "Progress reset to chapter 1", Toast.LENGTH_SHORT).show()
                        },
                        onToggleFavorite = { viewModel.toggleFavorite(book) },
                        onDelete = { bookToDelete = book }
                    )
                }
            }
        }

        // Close Column
        }

        // Add Book Floating Action Button
        FloatingActionButton(
            onClick = onNavigateToConvert,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add New Book"
            )
        }
    }
}

@Composable
fun BookListItemCard(
    book: ConvertedBook,
    onOpenReader: () -> Unit,
    onShare: () -> Unit,
    onExportToStorage: () -> Unit,
    onViewDetails: () -> Unit,
    onEditDetails: () -> Unit,
    onResetProgress: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val dateStr = remember(book.convertedAtMillis) {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(book.convertedAtMillis))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onOpenReader() }
            .testTag("book_card_${book.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Book Cover or Gradient Placeholder
            Box(
                modifier = Modifier
                    .width(68.dp)
                    .height(96.dp)
                    .clip(RoundedCornerShape(8.dp))
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
                        contentDescription = "Cover of ${book.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        modifier = Modifier.padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Book,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "EPUB",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Book Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = "${book.pageCount} pages",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "• ${formatBytes(book.fileSizeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• $dateStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // Reading Progress Bar
                val totalCh = book.chapterCount.coerceAtLeast(1)
                val readCh = (book.lastReadChapterIndex + 1).coerceAtMost(totalCh)
                val progress = (readCh.toFloat() / totalCh.toFloat()).coerceIn(0f, 1f)

                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .testTag("book_item_progress_${book.id}"),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Ch. $readCh of $totalCh",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(progress * 100).toInt()}% read",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Quick Actions: Favorite & Context Menu
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (book.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (book.isFavorite) Color(0xFFE91E63) else MaterialTheme.colorScheme.outline
                    )
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.testTag("book_menu_button_${book.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options for ${book.title}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.testTag("book_dropdown_menu_${book.id}")
                    ) {
                        DropdownMenuItem(
                            text = { Text("Read in App") },
                            leadingIcon = { Icon(Icons.Default.MenuBook, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onOpenReader()
                            },
                            modifier = Modifier.testTag("menu_read_${book.id}")
                        )
                        DropdownMenuItem(
                            text = { Text("Share to Apps") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onShare()
                            },
                            modifier = Modifier.testTag("menu_share_${book.id}")
                        )
                        DropdownMenuItem(
                            text = { Text("Export to Storage (.epub)") },
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onExportToStorage()
                            },
                            modifier = Modifier.testTag("menu_export_storage_${book.id}")
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Book Details & Info") },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onViewDetails()
                            },
                            modifier = Modifier.testTag("menu_details_${book.id}")
                        )
                        DropdownMenuItem(
                            text = { Text("Edit Title & Author") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onEditDetails()
                            },
                            modifier = Modifier.testTag("menu_edit_${book.id}")
                        )
                        DropdownMenuItem(
                            text = { Text("Restart Reading (0%)") },
                            leadingIcon = { Icon(Icons.Default.RestartAlt, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onResetProgress()
                            },
                            modifier = Modifier.testTag("menu_restart_${book.id}")
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete Book", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                            modifier = Modifier.testTag("menu_delete_${book.id}")
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BookDetailsDialog(
    book: ConvertedBook,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onExportToStorage: () -> Unit,
    onRead: () -> Unit
) {
    val dateStr = remember(book.convertedAtMillis) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(book.convertedAtMillis))
    }
    val totalCh = book.chapterCount.coerceAtLeast(1)
    val readCh = (book.lastReadChapterIndex + 1).coerceAtMost(totalCh)
    val progressPct = ((readCh.toFloat() / totalCh.toFloat()) * 100).toInt()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Book Metadata & Details")
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DetailItem(label = "Title", value = book.title)
                DetailItem(label = "Author", value = book.author)
                DetailItem(label = "Format", value = "Standard EPUB 3.0 (Reflowable)")
                DetailItem(label = "File Size", value = formatBytes(book.fileSizeBytes))
                DetailItem(label = "Original PDF", value = book.originalFileName)
                DetailItem(label = "Length", value = "${book.pageCount} Pages • $totalCh Chapters")
                DetailItem(label = "Reading Status", value = "Chapter $readCh of $totalCh ($progressPct% read)")
                DetailItem(label = "Converted On", value = dateStr)

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onShare,
                        modifier = Modifier.weight(1f).testTag("details_share_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share")
                    }
                    Button(
                        onClick = onExportToStorage,
                        modifier = Modifier.weight(1f).testTag("details_export_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onRead, modifier = Modifier.testTag("details_read_button")) {
                Text("Read in App")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun EditBookDialog(
    book: ConvertedBook,
    onDismiss: () -> Unit,
    onSave: (newTitle: String, newAuthor: String) -> Unit
) {
    var title by remember { mutableStateOf(book.title) }
    var author by remember { mutableStateOf(book.author) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Book Info") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Book Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_book_title_field")
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("Author") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_book_author_field")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, author) },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("save_book_details_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DetailItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

