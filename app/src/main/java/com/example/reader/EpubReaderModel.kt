package com.example.reader

import androidx.compose.ui.graphics.Color

enum class ThemeMode(val displayName: String) {
    SYSTEM("System Default"),
    LIGHT("Light Mode"),
    DARK("Dark Mode")
}

enum class ReaderTheme(
    val displayName: String,
    val backgroundColor: Color,
    val textColor: Color,
    val accentColor: Color,
    val isDark: Boolean = false
) {
    LIGHT(
        displayName = "Light",
        backgroundColor = Color(0xFFFFFFFF),
        textColor = Color(0xFF1E2022),
        accentColor = Color(0xFF1976D2),
        isDark = false
    ),
    SEPIA(
        displayName = "Sepia",
        backgroundColor = Color(0xFFF9F3E5),
        textColor = Color(0xFF382B1D),
        accentColor = Color(0xFF8D6E63),
        isDark = false
    ),
    MINT(
        displayName = "Mint",
        backgroundColor = Color(0xFFE8F5E9),
        textColor = Color(0xFF1B5E20),
        accentColor = Color(0xFF2E7D32),
        isDark = false
    ),
    DARK(
        displayName = "Charcoal",
        backgroundColor = Color(0xFF1E212B),
        textColor = Color(0xFFE2E4EE),
        accentColor = Color(0xFF80CBC4),
        isDark = true
    ),
    OLED_BLACK(
        displayName = "OLED",
        backgroundColor = Color(0xFF000000),
        textColor = Color(0xFFCCCCCC),
        accentColor = Color(0xFF64B5F6),
        isDark = true
    )
}

enum class ReaderFontFamily(val displayName: String) {
    SERIF("Serif (Classic)"),
    SANS_SERIF("Sans-Serif (Modern)"),
    LITERARY_GEORGIA("Georgia (Book)"),
    MONOSPACE("Monospace (Clean)")
}

enum class ReaderTextAlign(val displayName: String) {
    JUSTIFY("Justify"),
    LEFT("Left")
}

data class ReaderSettings(
    val fontSizeSp: Float = 18f,
    val lineSpacingMultiplier: Float = 1.65f,
    val fontFamily: ReaderFontFamily = ReaderFontFamily.SERIF,
    val theme: ReaderTheme = ReaderTheme.LIGHT,
    val textAlign: ReaderTextAlign = ReaderTextAlign.JUSTIFY
)

data class ReaderChapter(
    val id: String,
    val index: Int,
    val title: String,
    val paragraphs: List<String>,
    val wordCount: Int = paragraphs.sumOf { p -> p.split(Regex("\\s+")).count { it.isNotBlank() } }
)

data class ParsedEpubBook(
    val title: String,
    val author: String,
    val chapters: List<ReaderChapter>,
    val coverBytes: ByteArray? = null
) {
    val totalWords: Int
        get() = chapters.sumOf { it.wordCount }

    val estimatedReadTimeMinutes: Int
        get() = (totalWords / 200).coerceAtLeast(1)
}
