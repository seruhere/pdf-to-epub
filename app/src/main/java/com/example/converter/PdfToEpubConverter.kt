package com.example.converter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.regex.Pattern
import kotlin.coroutines.coroutineContext

class PdfToEpubConverter(private val context: Context) {

    private val epubBuilder = EpubBuilder()

    suspend fun extractMetadata(uri: Uri): Result<PdfMetadata> = withContext(Dispatchers.IO) {
        try {
            val fileName = getFileName(uri)
            val fileSizeBytes = getFileSize(uri)

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                PDDocument.load(inputStream).use { document ->
                    val info = document.documentInformation
                    val pageCount = document.numberOfPages

                    val title = info?.title?.trim().takeUnless { it.isNullOrBlank() }
                        ?: fileName.substringBeforeLast(".")

                    val author = info?.author?.trim().takeUnless { it.isNullOrBlank() }
                        ?: "Unknown Author"

                    val subject = info?.subject ?: ""
                    val creator = info?.creator ?: ""

                    // Extract outline/bookmarks if available
                    val outlineItems = mutableListOf<ExtractedOutlineItem>()
                    try {
                        val outline = document.documentCatalog?.documentOutline
                        if (outline != null) {
                            extractOutlineBookmarks(outline, document, outlineItems)
                        }
                    } catch (_: Exception) {}

                    Result.success(
                        PdfMetadata(
                            fileName = fileName,
                            title = title,
                            author = author,
                            subject = subject,
                            creator = creator,
                            pageCount = pageCount,
                            fileSizeBytes = fileSizeBytes,
                            outlineItems = outlineItems
                        )
                    )
                }
            } ?: Result.failure(Exception("Unable to open input stream for selected PDF"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun convert(
        uri: Uri,
        options: ConversionOptions,
        onProgress: (ConversionProgress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        var tempPdfFile: File? = null
        try {
            onProgress(
                ConversionProgress(
                    stage = ConversionStage.INITIALIZING,
                    message = "Opening document and preparing converter...",
                    percentage = 0.05f
                )
            )

            // Cache PDF to temp file for robust access and PdfRenderer cover extraction
            tempPdfFile = File(context.cacheDir, "input_convert_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempPdfFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("Failed to read PDF file"))

            coroutineContext.ensureActive()

            val document = PDDocument.load(tempPdfFile)
            val totalPages = document.numberOfPages
            if (totalPages == 0) {
                document.close()
                return@withContext Result.failure(Exception("The PDF has 0 pages"))
            }

            val startPage = options.pageStart.coerceIn(1, totalPages)
            val endPage = if (options.pageEnd in startPage..totalPages) options.pageEnd else totalPages
            val pagesToConvert = (endPage - startPage) + 1

            onProgress(
                ConversionProgress(
                    stage = ConversionStage.ANALYZING_STRUCTURE,
                    currentPage = 0,
                    totalPages = pagesToConvert,
                    message = "Analyzing document outline & chapters...",
                    percentage = 0.10f
                )
            )

            // Extract outlines for chapter boundaries
            val outlineMap = mutableMapOf<Int, String>()
            if (options.splitMode == ChapterSplitMode.AUTO_DETECT) {
                try {
                    val outline = document.documentCatalog?.documentOutline
                    if (outline != null) {
                        val items = mutableListOf<ExtractedOutlineItem>()
                        extractOutlineBookmarks(outline, document, items)
                        for (item in items) {
                            if (item.pageNumber in startPage..endPage) {
                                outlineMap[item.pageNumber] = item.title
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // Cover Image Generation
            var coverFile: File? = null
            if (options.extractCover) {
                onProgress(
                    ConversionProgress(
                        stage = ConversionStage.RENDERING_COVER,
                        currentPage = 0,
                        totalPages = pagesToConvert,
                        message = "Rendering book cover art...",
                        percentage = 0.15f
                    )
                )
                coverFile = renderCoverImage(tempPdfFile, startPage - 1)
            }

            coroutineContext.ensureActive()

            // Page extraction & chapter building
            val chapters = mutableListOf<ConvertedChapter>()
            var currentChapterTitle = outlineMap[startPage] ?: "Chapter 1"
            val currentChapterLines = mutableListOf<String>()
            var chapterStartPage = startPage
            var chapterIndex = 1

            val stripper = PDFTextStripper().apply {
                sortByPosition = true
            }

            val chapterRegex = Pattern.compile(
                "^(?:Chapter|CHAPTER|Section|SECTION|Part|PART|Book|BOOK)\\s+(?:[0-9]+|[IVXLCDM]+|[A-Za-z]+)?(?:[:\\.\\-\\s].*)?$",
                Pattern.CASE_INSENSITIVE
            )

            for (page in startPage..endPage) {
                coroutineContext.ensureActive()

                val currentProgressPct = 0.20f + (0.65f * ((page - startPage + 1).toFloat() / pagesToConvert))
                onProgress(
                    ConversionProgress(
                        stage = ConversionStage.EXTRACTING_PAGES,
                        currentPage = page - startPage + 1,
                        totalPages = pagesToConvert,
                        currentChapterTitle = currentChapterTitle,
                        percentage = currentProgressPct,
                        message = "Converting page $page of $endPage..."
                    )
                )

                stripper.startPage = page
                stripper.endPage = page
                val rawPageText = stripper.getText(document)

                val cleanedLines = cleanPageText(rawPageText, options.stripHeadersFooters, page, totalPages)

                // Determine if a new chapter should begin on this page
                var shouldSplit = false
                var newChapterTitle = ""

                when (options.splitMode) {
                    ChapterSplitMode.AUTO_DETECT -> {
                        if (outlineMap.containsKey(page) && page != startPage) {
                            shouldSplit = true
                            newChapterTitle = outlineMap[page] ?: "Chapter ${chapterIndex + 1}"
                        } else {
                            // Check if first line or heading matches chapter pattern
                            val potentialHeading = cleanedLines.firstOrNull { it.isNotBlank() }
                            if (potentialHeading != null && chapterRegex.matcher(potentialHeading.trim()).matches() && currentChapterLines.isNotEmpty()) {
                                shouldSplit = true
                                newChapterTitle = potentialHeading.trim()
                            }
                        }
                    }
                    ChapterSplitMode.PAGE_INTERVAL -> {
                        if ((page - startPage) > 0 && (page - startPage) % options.pagesPerChapter == 0) {
                            shouldSplit = true
                            val chapterNum = ((page - startPage) / options.pagesPerChapter) + 1
                            newChapterTitle = "Chapter $chapterNum"
                        }
                    }
                    ChapterSplitMode.PER_PAGE -> {
                        if (page != startPage) {
                            shouldSplit = true
                            newChapterTitle = "Page $page"
                        } else {
                            currentChapterTitle = "Page $startPage"
                        }
                    }
                    ChapterSplitMode.SINGLE_FILE -> {
                        // Keep everything in one continuous chapter
                        shouldSplit = false
                    }
                }

                if (shouldSplit && currentChapterLines.isNotEmpty()) {
                    chapters.add(
                        ConvertedChapter(
                            index = chapterIndex,
                            title = currentChapterTitle,
                            htmlContent = formatToHtml(currentChapterLines),
                            startPage = chapterStartPage,
                            endPage = page - 1
                        )
                    )
                    chapterIndex++
                    chapterStartPage = page
                    currentChapterTitle = if (newChapterTitle.isNotBlank()) newChapterTitle else "Chapter $chapterIndex"
                    currentChapterLines.clear()
                }

                currentChapterLines.addAll(cleanedLines)
            }

            // Flush remaining lines
            if (currentChapterLines.isNotEmpty() || chapters.isEmpty()) {
                chapters.add(
                    ConvertedChapter(
                        index = chapterIndex,
                        title = currentChapterTitle,
                        htmlContent = formatToHtml(currentChapterLines),
                        startPage = chapterStartPage,
                        endPage = endPage
                    )
                )
            }

            document.close()

            // Packaging EPUB
            onProgress(
                ConversionProgress(
                    stage = ConversionStage.PACKAGING_EPUB,
                    currentPage = pagesToConvert,
                    totalPages = pagesToConvert,
                    percentage = 0.90f,
                    message = "Packaging EPUB archive & validating metadata..."
                )
            )

            val epubDir = File(context.filesDir, "epubs").apply { mkdirs() }
            val sanitizedTitle = options.title.replace(Regex("[^a-zA-Z0-9_\\-\\.]"), "_").take(40)
            val outputEpub = File(epubDir, "${sanitizedTitle}_${System.currentTimeMillis()}.epub")

            epubBuilder.buildEpub(
                outputFile = outputEpub,
                title = options.title.ifBlank { "Untitled Book" },
                author = options.author.ifBlank { "Unknown Author" },
                chapters = chapters,
                coverImageFile = coverFile,
                typography = options.typography
            )

            onProgress(
                ConversionProgress(
                    stage = ConversionStage.COMPLETED,
                    currentPage = pagesToConvert,
                    totalPages = pagesToConvert,
                    percentage = 1.0f,
                    message = "Conversion complete! EPUB created successfully."
                )
            )

            Result.success(outputEpub)
        } catch (e: Exception) {
            onProgress(
                ConversionProgress(
                    stage = ConversionStage.ERROR,
                    percentage = 0f,
                    message = "Conversion failed: ${e.localizedMessage}",
                    errorMessage = e.localizedMessage
                )
            )
            Result.failure(e)
        } finally {
            tempPdfFile?.delete()
        }
    }

    private fun cleanPageText(
        rawText: String,
        stripHeadersFooters: Boolean,
        currentPage: Int,
        totalPages: Int
    ): List<String> {
        val lines = rawText.lines()
        if (!stripHeadersFooters) return lines

        val result = mutableListOf<String>()
        val pageNumberRegex = Pattern.compile("^\\s*(?:Page\\s+)?\\d+(?:\\s+of\\s+\\d+)?\\s*$", Pattern.CASE_INSENSITIVE)

        for (i in lines.indices) {
            val line = lines[i]
            val trimmed = line.trim()

            // Skip common standalone header/footer page numbers
            if (trimmed.isNotBlank() && (i == 0 || i == lines.size - 1)) {
                if (pageNumberRegex.matcher(trimmed).matches()) {
                    continue
                }
                if (trimmed == currentPage.toString() || trimmed == "$currentPage / $totalPages") {
                    continue
                }
            }

            result.add(line)
        }
        return result
    }

    private fun formatToHtml(lines: List<String>): String {
        val html = StringBuilder()
        val currentParagraph = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()

            if (trimmed.isEmpty()) {
                if (currentParagraph.isNotEmpty()) {
                    val pContent = escapeHtml(currentParagraph.toString().trim())
                    if (pContent.isNotEmpty()) {
                        html.append("      <p>").append(pContent).append("</p>\n")
                    }
                    currentParagraph.clear()
                }
            } else {
                if (currentParagraph.isNotEmpty()) {
                    // Check if previous ended with hyphen (word split across lines)
                    val lastChar = currentParagraph.last()
                    if (lastChar == '-' && currentParagraph.length > 2 && currentParagraph[currentParagraph.length - 2].isLetter()) {
                        currentParagraph.deleteCharAt(currentParagraph.length - 1)
                        currentParagraph.append(trimmed)
                    } else {
                        currentParagraph.append(" ").append(trimmed)
                    }
                } else {
                    currentParagraph.append(trimmed)
                }
            }
        }

        if (currentParagraph.isNotEmpty()) {
            val pContent = escapeHtml(currentParagraph.toString().trim())
            if (pContent.isNotEmpty()) {
                html.append("      <p>").append(pContent).append("</p>\n")
            }
        }

        return html.toString()
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun renderCoverImage(pdfFile: File, pageIndex: Int): File? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        return try {
            pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            if (renderer.pageCount == 0) return null

            val targetIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
            page = renderer.openPage(targetIndex)

            // High resolution render (2x standard DPI for crisp reading display)
            val renderWidth = (page.width * 2).coerceAtMost(1600)
            val renderHeight = (page.height * 2).coerceAtMost(2400)

            val bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
            val coverFile = File(coversDir, "cover_${System.currentTimeMillis()}.jpg")
            FileOutputStream(coverFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            bitmap.recycle()
            coverFile
        } catch (_: Exception) {
            null
        } finally {
            try { page?.close() } catch (_: Exception) {}
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    private fun extractOutlineBookmarks(
        node: PDOutlineNode,
        document: PDDocument,
        results: MutableList<ExtractedOutlineItem>
    ) {
        var current: PDOutlineItem? = node.firstChild
        while (current != null) {
            try {
                val title = current.title
                val destination = current.destination
                if (destination != null) {
                    val pageNumber = document.pages.indexOf(current.findDestinationPage(document)) + 1
                    if (pageNumber > 0 && !title.isNullOrBlank()) {
                        results.add(ExtractedOutlineItem(title.trim(), pageNumber))
                    }
                }
            } catch (_: Exception) {}
            if (current.hasChildren()) {
                extractOutlineBookmarks(current, document, results)
            }
            current = current.nextSibling
        }
    }

    private fun getFileName(uri: Uri): String {
        var name: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        name = it.getString(index)
                    }
                }
            }
        }
        if (name == null) {
            name = uri.path
            val cut = name?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                name = name?.substring(cut + 1)
            }
        }
        return name ?: "document.pdf"
    }

    private fun getFileSize(uri: Uri): Long {
        var size: Long = 0L
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (index >= 0) {
                        size = it.getLong(index)
                    }
                }
            }
        } catch (_: Exception) {}
        return size
    }
}
