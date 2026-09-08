package com.example.converter

enum class ChapterSplitMode(val displayName: String, val description: String) {
    AUTO_DETECT("Smart Auto-Detect", "Splits by PDF outline bookmarks or detected chapter headings"),
    PAGE_INTERVAL("Fixed Page Count", "Splits into regular chapters every N pages"),
    PER_PAGE("Single Page Per Chapter", "Creates a separate chapter for each PDF page"),
    SINGLE_FILE("Continuous Flow", "Places entire document in one smooth continuous chapter")
}

enum class TypographyPreset(val displayName: String, val fontCss: String) {
    CLEAN_MODERN("Modern Sans", "font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;"),
    CLASSIC_BOOK("Classic Serif", "font-family: 'Merriweather', Georgia, 'Times New Roman', serif;"),
    HIGH_LEGIBILITY("High Legibility", "font-family: system-ui, sans-serif; letter-spacing: 0.04em; line-height: 1.8;")
}

data class ExtractedOutlineItem(
    val title: String,
    val pageNumber: Int
)

data class PdfMetadata(
    val fileName: String,
    val title: String,
    val author: String,
    val subject: String,
    val creator: String,
    val pageCount: Int,
    val fileSizeBytes: Long,
    val outlineItems: List<ExtractedOutlineItem> = emptyList()
)

data class ConversionOptions(
    val title: String,
    val author: String,
    val splitMode: ChapterSplitMode = ChapterSplitMode.AUTO_DETECT,
    val pagesPerChapter: Int = 10,
    val extractCover: Boolean = true,
    val pageStart: Int = 1,
    val pageEnd: Int = 0, // 0 = all pages
    val typography: TypographyPreset = TypographyPreset.CLEAN_MODERN,
    val stripHeadersFooters: Boolean = true
)

enum class ConversionStage {
    IDLE,
    INITIALIZING,
    ANALYZING_STRUCTURE,
    EXTRACTING_PAGES,
    RENDERING_COVER,
    PACKAGING_EPUB,
    FINALIZING,
    COMPLETED,
    ERROR
}

data class ConversionProgress(
    val stage: ConversionStage = ConversionStage.IDLE,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val currentChapterTitle: String = "",
    val percentage: Float = 0f,
    val message: String = "",
    val errorMessage: String? = null
)

data class ConvertedChapter(
    val index: Int,
    val title: String,
    val htmlContent: String,
    val startPage: Int,
    val endPage: Int
)
