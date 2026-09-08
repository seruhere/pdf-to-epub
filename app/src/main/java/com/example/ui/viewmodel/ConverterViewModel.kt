package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.PdfToEpubApp
import com.example.converter.ChapterSplitMode
import com.example.converter.ConversionOptions
import com.example.converter.ConversionProgress
import com.example.converter.ConversionStage
import com.example.converter.PdfMetadata
import com.example.converter.PdfToEpubConverter
import com.example.converter.TypographyPreset
import com.example.data.model.ConvertedBook
import com.example.reader.EpubParser
import com.example.reader.ParsedEpubBook
import com.example.reader.ReaderSettings
import com.example.reader.ReaderTheme
import com.example.reader.ThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class BookSortOrder {
    DATE_DESC,
    TITLE_ASC,
    PAGES_DESC
}

enum class BookFilter {
    ALL,
    FAVORITES,
    READING,
    COMPLETED
}

data class BatchItem(
    val uri: Uri,
    val fileName: String,
    val fileSizeBytes: Long = 0L,
    var status: String = "Pending",
    var progress: Float = 0f,
    var error: String? = null
)

class ConverterViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as PdfToEpubApp).repository
    private val converter = PdfToEpubConverter(application)
    private val epubParser = EpubParser()

    private val prefs = application.getSharedPreferences("reader_app_prefs", Context.MODE_PRIVATE)

    // Dark Mode / Theme Mode state
    private val _themeMode = MutableStateFlow(
        when (prefs.getString("app_theme_mode", "SYSTEM")) {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString("app_theme_mode", mode.name).apply()
    }

    fun toggleDarkMode() {
        val next = when (_themeMode.value) {
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.SYSTEM -> ThemeMode.DARK
        }
        setThemeMode(next)
    }

    // Single conversion state
    private val _selectedPdfUri = MutableStateFlow<Uri?>(null)
    val selectedPdfUri: StateFlow<Uri?> = _selectedPdfUri.asStateFlow()

    private val _pdfMetadata = MutableStateFlow<PdfMetadata?>(null)
    val pdfMetadata: StateFlow<PdfMetadata?> = _pdfMetadata.asStateFlow()

    private val _isLoadingMetadata = MutableStateFlow(false)
    val isLoadingMetadata: StateFlow<Boolean> = _isLoadingMetadata.asStateFlow()

    private val _conversionOptions = MutableStateFlow(
        ConversionOptions(
            title = "",
            author = "",
            splitMode = ChapterSplitMode.AUTO_DETECT,
            pagesPerChapter = 10,
            extractCover = true,
            typography = TypographyPreset.CLEAN_MODERN,
            stripHeadersFooters = true
        )
    )
    val conversionOptions: StateFlow<ConversionOptions> = _conversionOptions.asStateFlow()

    private val _conversionProgress = MutableStateFlow(ConversionProgress())
    val conversionProgress: StateFlow<ConversionProgress> = _conversionProgress.asStateFlow()

    private val _isConverting = MutableStateFlow(false)
    val isConverting: StateFlow<Boolean> = _isConverting.asStateFlow()

    private val _lastConvertedBook = MutableStateFlow<ConvertedBook?>(null)
    val lastConvertedBook: StateFlow<ConvertedBook?> = _lastConvertedBook.asStateFlow()

    private var conversionJob: Job? = null
    private var batchJob: Job? = null

    // Library state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(BookSortOrder.DATE_DESC)
    val sortOrder: StateFlow<BookSortOrder> = _sortOrder.asStateFlow()

    private val _bookFilter = MutableStateFlow(BookFilter.ALL)
    val bookFilter: StateFlow<BookFilter> = _bookFilter.asStateFlow()

    val libraryBooks: StateFlow<List<ConvertedBook>> = combine(
        repository.allBooks,
        _searchQuery,
        _sortOrder,
        _bookFilter
    ) { books, query, sort, filter ->
        var filtered = if (query.isBlank()) {
            books
        } else {
            books.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.author.contains(query, ignoreCase = true) ||
                it.originalFileName.contains(query, ignoreCase = true)
            }
        }

        filtered = when (filter) {
            BookFilter.ALL -> filtered
            BookFilter.FAVORITES -> filtered.filter { it.isFavorite }
            BookFilter.READING -> filtered.filter {
                val total = it.chapterCount.coerceAtLeast(1)
                it.lastReadChapterIndex in 0 until (total - 1)
            }
            BookFilter.COMPLETED -> filtered.filter {
                it.chapterCount > 0 && it.lastReadChapterIndex >= it.chapterCount - 1
            }
        }

        when (sort) {
            BookSortOrder.DATE_DESC -> filtered.sortedByDescending { it.convertedAtMillis }
            BookSortOrder.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
            BookSortOrder.PAGES_DESC -> filtered.sortedByDescending { it.pageCount }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Batch conversion state
    private val _batchQueue = MutableStateFlow<List<BatchItem>>(emptyList())
    val batchQueue: StateFlow<List<BatchItem>> = _batchQueue.asStateFlow()

    private val _isBatchRunning = MutableStateFlow(false)
    val isBatchRunning: StateFlow<Boolean> = _isBatchRunning.asStateFlow()

    private val _batchCurrentIndex = MutableStateFlow(0)
    val batchCurrentIndex: StateFlow<Int> = _batchCurrentIndex.asStateFlow()

    // Reader state
    private val _activeReaderBook = MutableStateFlow<ParsedEpubBook?>(null)
    val activeReaderBook: StateFlow<ParsedEpubBook?> = _activeReaderBook.asStateFlow()

    private val _activeBookRecord = MutableStateFlow<ConvertedBook?>(null)
    val activeBookRecord: StateFlow<ConvertedBook?> = _activeBookRecord.asStateFlow()

    private val _currentChapterIndex = MutableStateFlow(0)
    val currentChapterIndex: StateFlow<Int> = _currentChapterIndex.asStateFlow()

    private val _readerSettings = MutableStateFlow(ReaderSettings())
    val readerSettings: StateFlow<ReaderSettings> = _readerSettings.asStateFlow()

    private val _isLoadingReader = MutableStateFlow(false)
    val isLoadingReader: StateFlow<Boolean> = _isLoadingReader.asStateFlow()

    fun onPdfSelected(uri: Uri) {
        _selectedPdfUri.value = uri
        _pdfMetadata.value = null
        _isLoadingMetadata.value = true

        viewModelScope.launch {
            val result = converter.extractMetadata(uri)
            _isLoadingMetadata.value = false
            result.onSuccess { metadata ->
                _pdfMetadata.value = metadata
                _conversionOptions.value = _conversionOptions.value.copy(
                    title = metadata.title,
                    author = metadata.author,
                    pageStart = 1,
                    pageEnd = metadata.pageCount
                )
            }.onFailure { error ->
                _pdfMetadata.value = PdfMetadata(
                    fileName = "document.pdf",
                    title = "My Document",
                    author = "Author",
                    subject = "",
                    creator = "",
                    pageCount = 1,
                    fileSizeBytes = 0L
                )
                _conversionOptions.value = _conversionOptions.value.copy(
                    title = "My Document",
                    author = "Author",
                    pageStart = 1,
                    pageEnd = 1
                )
            }
        }
    }

    fun updateConversionOptions(updater: (ConversionOptions) -> ConversionOptions) {
        _conversionOptions.value = updater(_conversionOptions.value)
    }

    fun resetConversionSettingsToDefaults() {
        _conversionOptions.value = _conversionOptions.value.copy(
            fontFamily = ConversionOptions.DEFAULT_FONT_FAMILY,
            fontSizePt = ConversionOptions.DEFAULT_FONT_SIZE_PT,
            margin = ConversionOptions.DEFAULT_MARGIN,
            customMarginPercent = null,
            imageCompression = ConversionOptions.DEFAULT_IMAGE_COMPRESSION,
            customImageQuality = null
        )
    }

    fun startConversion() {
        val uri = _selectedPdfUri.value ?: return
        val options = _conversionOptions.value

        _isConverting.value = true
        _conversionProgress.value = ConversionProgress(stage = ConversionStage.INITIALIZING)

        conversionJob = viewModelScope.launch {
            val result = converter.convert(uri, options) { progress ->
                _conversionProgress.value = progress
            }

            result.onSuccess { epubFile ->
                // Check if cover file exists in filesDir/covers/
                val metadata = _pdfMetadata.value
                val coversDir = File(getApplication<Application>().filesDir, "covers")
                val coverFile = coversDir.listFiles()?.maxByOrNull { it.lastModified() }

                val convertedBook = ConvertedBook(
                    title = options.title.ifBlank { "Untitled Book" },
                    author = options.author.ifBlank { "Unknown Author" },
                    originalFileName = metadata?.fileName ?: "document.pdf",
                    epubFilePath = epubFile.absolutePath,
                    coverImagePath = coverFile?.absolutePath,
                    fileSizeBytes = epubFile.length(),
                    pageCount = metadata?.pageCount ?: 1,
                    chapterCount = 1, // updated dynamically when opened
                    convertedAtMillis = System.currentTimeMillis()
                )

                val bookId = repository.saveBook(convertedBook)
                _lastConvertedBook.value = convertedBook.copy(id = bookId)
                _isConverting.value = false
            }.onFailure {
                _isConverting.value = false
            }
        }
    }

    fun cancelConversion() {
        conversionJob?.cancel()
        _isConverting.value = false
        _conversionProgress.value = ConversionProgress(
            stage = ConversionStage.IDLE,
            message = "Conversion cancelled by user"
        )
    }

    fun resetConversion() {
        _selectedPdfUri.value = null
        _pdfMetadata.value = null
        _lastConvertedBook.value = null
        _conversionProgress.value = ConversionProgress()
        _isConverting.value = false
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOrder(order: BookSortOrder) {
        _sortOrder.value = order
    }

    fun deleteBook(book: ConvertedBook) {
        viewModelScope.launch {
            if (_activeBookRecord.value?.id == book.id) {
                _activeReaderBook.value = null
                _activeBookRecord.value = null
            }
            repository.deleteBook(book)
        }
    }

    fun toggleFavorite(book: ConvertedBook) {
        viewModelScope.launch {
            repository.toggleFavorite(book.id, !book.isFavorite)
        }
    }

    fun setBookFilter(filter: BookFilter) {
        _bookFilter.value = filter
    }

    fun updateBookMetadata(book: ConvertedBook, newTitle: String, newAuthor: String) {
        viewModelScope.launch {
            val updated = book.copy(
                title = newTitle.trim().ifBlank { book.title },
                author = newAuthor.trim().ifBlank { book.author }
            )
            repository.updateBook(updated)
            if (_activeBookRecord.value?.id == book.id) {
                _activeBookRecord.value = updated
            }
        }
    }

    fun resetReadingProgress(book: ConvertedBook) {
        viewModelScope.launch {
            repository.updateReadingProgress(book.id, 0, 0)
            if (_activeBookRecord.value?.id == book.id) {
                _currentChapterIndex.value = 0
            }
        }
    }

    fun updateReadingProgress(book: ConvertedBook, chapterIndex: Int) {
        val total = book.chapterCount.coerceAtLeast(1)
        val validChapter = chapterIndex.coerceIn(0, total - 1)
        viewModelScope.launch {
            repository.updateReadingProgress(book.id, validChapter, 0)
            if (_activeBookRecord.value?.id == book.id) {
                _currentChapterIndex.value = validChapter
            }
        }
    }

    // Batch Conversion
    fun addBatchFiles(uris: List<Uri>) {
        val context = getApplication<Application>()
        val items = uris.map { uri ->
            var name = "document.pdf"
            var size = 0L
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1) cursor.getString(nameIndex)?.let { name = it }
                        if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                    }
                }
            } catch (e: Exception) {
                name = uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"
            }
            BatchItem(uri = uri, fileName = name, fileSizeBytes = size)
        }
        _batchQueue.value = _batchQueue.value + items
    }

    fun removeBatchItem(item: BatchItem) {
        if (_isBatchRunning.value) return
        _batchQueue.value = _batchQueue.value.filter { it != item }
    }

    fun removeBatchItemAt(index: Int) {
        if (_isBatchRunning.value) return
        val current = _batchQueue.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _batchQueue.value = current
        }
    }

    fun clearBatch() {
        cancelBatchConversion()
        _batchQueue.value = emptyList()
        _isBatchRunning.value = false
    }

    fun cancelBatchConversion() {
        batchJob?.cancel()
        batchJob = null
        _isBatchRunning.value = false
        val current = _batchQueue.value
        current.forEach { item ->
            if (item.status == "Converting") {
                item.status = "Cancelled"
            }
        }
        _batchQueue.value = ArrayList(current)
    }

    fun startBatchConversion() {
        val queue = _batchQueue.value.ifEmpty { return }
        if (_isBatchRunning.value) return
        _isBatchRunning.value = true

        batchJob = viewModelScope.launch {
            try {
                queue.forEachIndexed { index, item ->
                    // Skip items that are already successfully converted
                    if (item.status == "Success") return@forEachIndexed

                    _batchCurrentIndex.value = index
                    item.status = "Converting"
                    item.progress = 0f
                    item.error = null
                    _batchQueue.value = ArrayList(_batchQueue.value)

                    val metadataResult = converter.extractMetadata(item.uri)
                    val meta = metadataResult.getOrNull()
                    val options = ConversionOptions(
                        title = meta?.title ?: item.fileName.substringBeforeLast("."),
                        author = meta?.author ?: "Unknown",
                        extractCover = true
                    )

                    val convertResult = converter.convert(item.uri, options) { progress ->
                        item.progress = progress.percentage
                        _batchQueue.value = ArrayList(_batchQueue.value)
                    }

                    convertResult.onSuccess { file ->
                        item.status = "Success"
                        item.progress = 1.0f
                        val coversDir = File(getApplication<Application>().filesDir, "covers")
                        val coverFile = coversDir.listFiles()?.maxByOrNull { it.lastModified() }
                        val book = ConvertedBook(
                            title = options.title,
                            author = options.author,
                            originalFileName = item.fileName,
                            epubFilePath = file.absolutePath,
                            coverImagePath = coverFile?.absolutePath,
                            fileSizeBytes = file.length(),
                            pageCount = meta?.pageCount ?: 1
                        )
                        repository.saveBook(book)
                    }.onFailure { err ->
                        item.status = "Failed"
                        item.error = err.localizedMessage
                    }
                    _batchQueue.value = ArrayList(_batchQueue.value)
                }
            } finally {
                _isBatchRunning.value = false
            }
        }
    }

    // Reader functions
    fun openBookInReader(book: ConvertedBook) {
        _isLoadingReader.value = true
        _activeBookRecord.value = book
        _currentChapterIndex.value = book.lastReadChapterIndex

        viewModelScope.launch {
            val file = File(book.epubFilePath)
            val result = epubParser.parse(file)
            _isLoadingReader.value = false
            result.onSuccess { parsed ->
                _activeReaderBook.value = parsed
                if (book.chapterCount != parsed.chapters.size) {
                    repository.saveBook(book.copy(chapterCount = parsed.chapters.size))
                }
            }.onFailure {
                _activeReaderBook.value = null
            }
        }
    }

    fun openEpubFromUri(uri: Uri) {
        _isLoadingReader.value = true
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val contentResolver = context.contentResolver
                val tempDir = File(context.cacheDir, "opened_epubs").apply { mkdirs() }

                var displayName = "Imported Book.epub"
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        cursor.getString(nameIndex)?.let { displayName = it }
                    }
                }

                val destFile = File(tempDir, "${System.currentTimeMillis()}_$displayName")
                contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val parseResult = epubParser.parse(destFile)
                _isLoadingReader.value = false
                parseResult.onSuccess { parsed ->
                    _activeReaderBook.value = parsed
                    _currentChapterIndex.value = 0

                    val coverPath = if (parsed.coverBytes != null) {
                        val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
                        val cFile = File(coversDir, "cover_${System.currentTimeMillis()}.jpg")
                        cFile.writeBytes(parsed.coverBytes)
                        cFile.absolutePath
                    } else null

                    val bookRecord = ConvertedBook(
                        title = parsed.title.ifBlank { displayName.substringBeforeLast(".") },
                        author = parsed.author.ifBlank { "Unknown Author" },
                        originalFileName = displayName,
                        epubFilePath = destFile.absolutePath,
                        coverImagePath = coverPath,
                        fileSizeBytes = destFile.length(),
                        pageCount = parsed.chapters.size,
                        chapterCount = parsed.chapters.size,
                        lastReadChapterIndex = 0
                    )
                    val id = repository.saveBook(bookRecord)
                    _activeBookRecord.value = bookRecord.copy(id = id)
                }.onFailure { err ->
                    _activeReaderBook.value = null
                }
            } catch (e: Exception) {
                _isLoadingReader.value = false
                _activeReaderBook.value = null
            }
        }
    }

    fun setChapter(index: Int) {
        val book = _activeReaderBook.value ?: return
        val validIndex = index.coerceIn(0, (book.chapters.size - 1).coerceAtLeast(0))
        _currentChapterIndex.value = validIndex

        _activeBookRecord.value?.let { record ->
            viewModelScope.launch {
                repository.updateReadingProgress(record.id, validIndex, 0)
            }
        }
    }

    fun updateReadingScrollOffset(offset: Int) {
        val record = _activeBookRecord.value ?: return
        viewModelScope.launch {
            repository.updateReadingProgress(record.id, _currentChapterIndex.value, offset)
        }
    }

    fun nextChapter() {
        val book = _activeReaderBook.value ?: return
        if (_currentChapterIndex.value < book.chapters.size - 1) {
            setChapter(_currentChapterIndex.value + 1)
        }
    }

    fun previousChapter() {
        if (_currentChapterIndex.value > 0) {
            setChapter(_currentChapterIndex.value - 1)
        }
    }

    fun toggleReaderDarkTheme() {
        _readerSettings.value = _readerSettings.value.copy(
            theme = if (_readerSettings.value.theme.isDark) ReaderTheme.LIGHT else ReaderTheme.DARK
        )
    }

    fun updateReaderSettings(updater: (ReaderSettings) -> ReaderSettings) {
        _readerSettings.value = updater(_readerSettings.value)
    }

    fun closeReader() {
        _activeReaderBook.value = null
        _activeBookRecord.value = null
    }
}
