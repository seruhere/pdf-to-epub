package com.example.reader

import androidx.compose.ui.graphics.Color

enum class ReaderTheme(
    val displayName: String,
    val backgroundColor: Color,
    val textColor: Color,
    val accentColor: Color
) {
    SEPIA(
        displayName = "Sepia",
        backgroundColor = Color(0xFFF9F3E5),
        textColor = Color(0xFF382B1D),
        accentColor = Color(0xFF8D6E63)
    ),
    LIGHT(
        displayName = "Light",
        backgroundColor = Color(0xFFFFFFFF),
        textColor = Color(0xFF1E2022),
        accentColor = Color(0xFF1976D2)
    ),
    DARK(
        displayName = "Charcoal",
        backgroundColor = Color(0xFF1E212B),
        textColor = Color(0xFFE2E4EE),
        accentColor = Color(0xFF80CBC4)
    ),
    OLED_BLACK(
        displayName = "OLED",
        backgroundColor = Color(0xFF000000),
        textColor = Color(0xFFCCCCCC),
        accentColor = Color(0xFF64B5F6)
    )
}

enum class ReaderFontFamily(val displayName: String) {
    SERIF("Serif (Classic)"),
    SANS_SERIF("Sans-Serif (Modern)"),
    MONOSPACE("Monospace (Clean)")
}

data class ReaderSettings(
    val fontSizeSp: Float = 18f,
    val lineSpacingMultiplier: Float = 1.6f,
    val fontFamily: ReaderFontFamily = ReaderFontFamily.SERIF,
    val theme: ReaderTheme = ReaderTheme.SEPIA
)

data class ReaderChapter(
    val id: String,
    val index: Int,
    val title: String,
    val paragraphs: List<String>
)

data class ParsedEpubBook(
    val title: String,
    val author: String,
    val chapters: List<ReaderChapter>,
    val coverBytes: ByteArray? = null
)
