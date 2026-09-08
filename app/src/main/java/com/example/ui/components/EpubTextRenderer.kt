package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.reader.ReaderChapter
import com.example.reader.ReaderFontFamily
import com.example.reader.ReaderSettings
import com.example.reader.ReaderTextAlign
import com.example.ui.theme.GreenSuccess

/**
 * Dedicated high-craft text rendering component for reading EPUB chapters.
 * Supports text selection, rich chapter headers, drop caps, subheadings,
 * blockquotes, bullet lists, customized font scales, and line spacing.
 */
@Composable
fun EpubTextRenderer(
    chapter: ReaderChapter,
    settings: ReaderSettings,
    bookTitle: String,
    totalChapters: Int,
    searchQuery: String = "",
    modifier: Modifier = Modifier,
    onNextChapter: (() -> Unit)? = null,
    onNavigateToLibrary: (() -> Unit)? = null
) {
    val composeFontFamily = when (settings.fontFamily) {
        ReaderFontFamily.SERIF -> FontFamily.Serif
        ReaderFontFamily.SANS_SERIF -> FontFamily.SansSerif
        ReaderFontFamily.LITERARY_GEORGIA -> FontFamily.Serif
        ReaderFontFamily.MONOSPACE -> FontFamily.Monospace
    }

    val textAlign = when (settings.textAlign) {
        ReaderTextAlign.JUSTIFY -> TextAlign.Justify
        ReaderTextAlign.LEFT -> TextAlign.Start
    }

    val theme = settings.theme
    val textColor = theme.textColor
    val accentColor = theme.accentColor
    val estMinutes = (chapter.wordCount / 200).coerceAtLeast(1)
    val highlightBgColor = accentColor.copy(alpha = 0.35f)
    val highlightTextColor = if (theme.isDark) Color.White else Color.Black

    SelectionContainer {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .testTag("epub_text_renderer")
        ) {
            // Chapter Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp, top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Chapter Pill
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = accentColor.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = "CHAPTER ${chapter.index + 1} OF $totalChapters",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Chapter Title
                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = composeFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = (settings.fontSizeSp * 1.35f).sp,
                        lineHeight = (settings.fontSizeSp * 1.6f).sp
                    ),
                    color = textColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Reading meta stats
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = textColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "~$estMinutes min read • ${chapter.wordCount} words",
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.65f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Decorative ornamental divider
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = accentColor.copy(alpha = 0.3f))
                    Icon(
                        imageVector = Icons.Default.AutoStories,
                        contentDescription = null,
                        tint = accentColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = accentColor.copy(alpha = 0.3f))
                }
            }

            // Paragraphs / Blocks Rendering
            chapter.paragraphs.forEachIndexed { index, rawBlock ->
                val block = rawBlock.trim()
                if (block.isNotBlank()) {
                    when {
                        // Subheading
                        block.startsWith("### ") -> {
                            val headingText = block.removePrefix("### ").trim()
                            Text(
                                text = headingText,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = composeFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = (settings.fontSizeSp * 1.15f).sp
                                ),
                                color = accentColor,
                                modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
                            )
                        }

                        // Blockquote
                        block.startsWith("❝ ") -> {
                            val quoteText = block.removePrefix("❝ ").trim()
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.08f)),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = accentColor.copy(alpha = 0.25f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(28.dp)
                                            .background(accentColor, shape = RoundedCornerShape(2.dp))
                                    )
                                    Column {
                                        Icon(
                                            imageVector = Icons.Default.FormatQuote,
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = quoteText,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontFamily = composeFontFamily,
                                                fontSize = settings.fontSizeSp.sp,
                                                fontStyle = FontStyle.Italic,
                                                lineHeight = (settings.fontSizeSp * settings.lineSpacingMultiplier).sp
                                            ),
                                            color = textColor.copy(alpha = 0.9f),
                                            textAlign = textAlign
                                        )
                                    }
                                }
                            }
                        }

                        // Bullet point
                        block.startsWith("• ") -> {
                            val itemText = block.removePrefix("• ").trim()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = (settings.fontSizeSp * 1.1f).sp
                                    ),
                                    color = accentColor
                                )
                                Text(
                                    text = itemText,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = composeFontFamily,
                                        fontSize = settings.fontSizeSp.sp,
                                        lineHeight = (settings.fontSizeSp * settings.lineSpacingMultiplier).sp
                                    ),
                                    color = textColor,
                                    textAlign = textAlign,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Normal body paragraph (with drop-cap on first paragraph)
                        else -> {
                            if (searchQuery.isNotBlank() && block.contains(searchQuery, ignoreCase = true)) {
                                val highlighted = highlightSearchText(
                                    text = block,
                                    query = searchQuery,
                                    highlightBgColor = highlightBgColor,
                                    highlightTextColor = highlightTextColor
                                )
                                Text(
                                    text = highlighted,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = composeFontFamily,
                                        fontSize = settings.fontSizeSp.sp,
                                        lineHeight = (settings.fontSizeSp * settings.lineSpacingMultiplier).sp
                                    ),
                                    color = textColor,
                                    textAlign = textAlign,
                                    modifier = Modifier.padding(bottom = 14.dp)
                                )
                            } else if (index == 0 && block.length > 20) {
                                // Drop cap for the very first letter of the chapter
                                val firstLetter = block.take(1)
                                val restOfParagraph = block.drop(1)

                                val annotatedString = buildAnnotatedString {
                                    withStyle(
                                        style = SpanStyle(
                                            fontFamily = composeFontFamily,
                                            fontSize = (settings.fontSizeSp * 1.55f).sp,
                                            fontWeight = FontWeight.Bold,
                                            color = accentColor
                                        )
                                    ) {
                                        append(firstLetter)
                                    }
                                    withStyle(
                                        style = SpanStyle(
                                            fontFamily = composeFontFamily,
                                            fontSize = settings.fontSizeSp.sp,
                                            color = textColor
                                        )
                                    ) {
                                        append(restOfParagraph)
                                    }
                                }

                                Text(
                                    text = annotatedString,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        lineHeight = (settings.fontSizeSp * settings.lineSpacingMultiplier).sp
                                    ),
                                    textAlign = textAlign,
                                    modifier = Modifier.padding(bottom = 14.dp)
                                )
                            } else {
                                Text(
                                    text = block,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = composeFontFamily,
                                        fontSize = settings.fontSizeSp.sp,
                                        lineHeight = (settings.fontSizeSp * settings.lineSpacingMultiplier).sp
                                    ),
                                    color = textColor,
                                    textAlign = textAlign,
                                    modifier = Modifier.padding(bottom = 14.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // End of chapter footer card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .testTag("end_of_chapter_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.1f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (chapter.index < totalChapters - 1) {
                        Text(
                            text = "End of Chapter ${chapter.index + 1}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = textColor
                        )
                        Text(
                            text = "You're making great progress in '$bookTitle'.",
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                        if (onNextChapter != null) {
                            Button(
                                onClick = onNextChapter,
                                modifier = Modifier.testTag("next_chapter_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                            ) {
                                Text("Continue to Chapter ${chapter.index + 2}", color = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(GreenSuccess.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = GreenSuccess,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = "Book Completed!",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = textColor
                        )
                        Text(
                            text = "You've finished reading '$bookTitle'. All $totalChapters chapters are completed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                        if (onNavigateToLibrary != null) {
                            Button(
                                onClick = onNavigateToLibrary,
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                            ) {
                                Text("Back to My Books", color = Color.White)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}

private fun highlightSearchText(
    text: String,
    query: String,
    highlightBgColor: Color,
    highlightTextColor: Color
): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    return buildAnnotatedString {
        var startIndex = 0
        while (startIndex < text.length) {
            val matchIndex = text.indexOf(query, startIndex, ignoreCase = true)
            if (matchIndex == -1) {
                append(text.substring(startIndex))
                break
            }
            if (matchIndex > startIndex) {
                append(text.substring(startIndex, matchIndex))
            }
            withStyle(
                SpanStyle(
                    background = highlightBgColor,
                    color = highlightTextColor,
                    fontWeight = FontWeight.Bold
                )
            ) {
                append(text.substring(matchIndex, matchIndex + query.length))
            }
            startIndex = matchIndex + query.length
        }
    }
}
