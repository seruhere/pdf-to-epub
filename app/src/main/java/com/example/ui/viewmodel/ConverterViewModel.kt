package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
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

data class BatchItem(
    val uri: Uri,
    val fileName: String,
    var status: String = "Pending",
    var progress: Float = 0f,
    var error: String? = null
)

class ConverterViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as PdfToEpubApp).repository
    private val converter = PdfToEpubConverter(application)
    private val epubParser = EpubParser()

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

    // Library state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(BookSortOrder.DATE_DESC)
    val sortOrder: StateFlow<BookSortOrder> = _sortOrder.asStateFlow()

    val libraryBooks: StateFlow<List<ConvertedBook>> = combine(
        repository.allBooks,
        _searchQuery,
        _sortOrder
    ) { books, query, sort ->
        val filtered = if (query.isBlank()) {
            books
        } else {
            books.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.author.contains(query, ignoreCase = true) ||
                it.originalFileName.contains(query, ignoreCase = true)
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

    // Batch Conversion
    fun addBatchFiles(uris: List<Uri>) {
        val items = uris.map { uri ->
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"
            BatchItem(uri = uri, fileName = name)
        }
        _batchQueue.value = _batchQueue.value + items
    }

    fun clearBatch() {
        _batchQueue.value = emptyList()
        _isBatchRunning.value = false
    }

    fun startBatchConversion() {
        val queue = _batchQueue.value.ifEmpty { return }
        _isBatchRunning.value = true

        viewModelScope.launch {
            queue.forEachIndexed { index, item ->
                _batchCurrentIndex.value = index
                item.status = "Converting"
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
            _isBatchRunning.value = false
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

    fun updateReaderSettings(updater: (ReaderSettings) -> ReaderSettings) {
        _readerSettings.value = updater(_readerSettings.value)
    }

    fun closeReader() {
        _activeReaderBook.value = null
        _activeBookRecord.value = null
    }
}
