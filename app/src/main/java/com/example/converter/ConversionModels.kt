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

enum class EpubFontFamily(
    val displayName: String,
    val fontCategory: String,
    val fontCss: String,
    val previewSample: String
) {
    SANS_SERIF(
        displayName = "Modern Sans",
        fontCategory = "Sans-Serif",
        fontCss = "font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;",
        previewSample = "Sphinx of black quartz, judge my vow"
    ),
    SERIF(
        displayName = "Classic Serif",
        fontCategory = "Serif",
        fontCss = "font-family: 'Merriweather', 'Georgia', 'Times New Roman', serif;",
        previewSample = "Sphinx of black quartz, judge my vow"
    ),
    LITERARY_GEORGIA(
        displayName = "Literary Georgia",
        fontCategory = "Editorial Serif",
        fontCss = "font-family: Georgia, Palatino, 'Palatino Linotype', 'Book Antiqua', serif;",
        previewSample = "Sphinx of black quartz, judge my vow"
    ),
    MONOSPACE(
        displayName = "Code Monospace",
        fontCategory = "Fixed-Width",
        fontCss = "font-family: 'JetBrains Mono', 'Fira Code', 'Courier New', Courier, monospace;",
        previewSample = "Sphinx of black quartz, judge my vow"
    ),
    HIGH_LEGIBILITY(
        displayName = "High Legibility",
        fontCategory = "Accessibility",
        fontCss = "font-family: system-ui, -apple-system, sans-serif; letter-spacing: 0.05em; word-spacing: 0.08em; line-height: 1.8;",
        previewSample = "Sphinx of black quartz, judge my vow"
    )
}

enum class EpubMargin(
    val displayName: String,
    val horizontalPercent: Int,
    val verticalPercent: Int,
    val description: String
) {
    COMPACT("Compact (3%)", 3, 2, "Narrow margins — maximized reading area"),
    STANDARD("Standard (5%)", 5, 4, "Optimal reading margins — clean and balanced"),
    WIDE("Spacious (8%)", 8, 6, "Wide margins — focused, relaxed reading"),
    GENEROUS("Novel (12%)", 12, 8, "Generous book margins — classic publication style")
}

enum class ImageCompressionLevel(
    val displayName: String,
    val qualityPercent: Int,
    val maxDimension: Int,
    val description: String
) {
    LOW(
        displayName = "Low Compression",
        qualityPercent = 92,
        maxDimension = 1800,
        description = "Highest visual fidelity (92% quality) • Larger EPUB size"
    ),
    BALANCED(
        displayName = "Balanced (Default)",
        qualityPercent = 80,
        maxDimension = 1400,
        description = "Ideal clarity & file size balance (80% quality) • Recommended"
    ),
    HIGH(
        displayName = "High Compression",
        qualityPercent = 60,
        maxDimension = 1000,
        description = "Compact images (60% quality) • Smaller file size"
    ),
    MAXIMUM(
        displayName = "Maximum Compression",
        qualityPercent = 40,
        maxDimension = 720,
        description = "Aggressive compression (40% quality) • Minimal disk footprint"
    )
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
    val title: String = "",
    val author: String = "",
    val splitMode: ChapterSplitMode = ChapterSplitMode.AUTO_DETECT,
    val pagesPerChapter: Int = 10,
    val extractCover: Boolean = true,
    val pageStart: Int = 1,
    val pageEnd: Int = 0, // 0 = all pages
    val typography: TypographyPreset = TypographyPreset.CLEAN_MODERN,
    val stripHeadersFooters: Boolean = true,
    // Customizable conversion settings with default starting points
    val fontFamily: EpubFontFamily = DEFAULT_FONT_FAMILY,
    val fontSizePt: Int = DEFAULT_FONT_SIZE_PT,
    val margin: EpubMargin = DEFAULT_MARGIN,
    val customMarginPercent: Int? = null,
    val imageCompression: ImageCompressionLevel = DEFAULT_IMAGE_COMPRESSION,
    val customImageQuality: Int? = null
) {
    companion object {
        val DEFAULT_FONT_FAMILY = EpubFontFamily.SANS_SERIF
        const val DEFAULT_FONT_SIZE_PT = 16
        val DEFAULT_MARGIN = EpubMargin.STANDARD
        val DEFAULT_IMAGE_COMPRESSION = ImageCompressionLevel.BALANCED

        const val MIN_FONT_SIZE_PT = 11
        const val MAX_FONT_SIZE_PT = 26

        const val MIN_MARGIN_PERCENT = 2
        const val MAX_MARGIN_PERCENT = 15

        const val MIN_IMAGE_QUALITY = 30
        const val MAX_IMAGE_QUALITY = 100
    }

    val effectiveHorizontalMargin: Int
        get() = customMarginPercent ?: margin.horizontalPercent

    val effectiveVerticalMargin: Int
        get() = customMarginPercent?.let { kotlin.math.round(it * 0.8f).toInt().coerceAtLeast(2) } ?: margin.verticalPercent

    val effectiveImageQuality: Int
        get() = customImageQuality ?: imageCompression.qualityPercent

    val isCustomized: Boolean
        get() = fontFamily != DEFAULT_FONT_FAMILY ||
                fontSizePt != DEFAULT_FONT_SIZE_PT ||
                margin != DEFAULT_MARGIN ||
                customMarginPercent != null ||
                imageCompression != DEFAULT_IMAGE_COMPRESSION ||
                customImageQuality != null
}

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
