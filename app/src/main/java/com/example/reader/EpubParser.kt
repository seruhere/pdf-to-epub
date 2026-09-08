package com.example.reader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class EpubParser {

    suspend fun parse(epubFile: File): Result<ParsedEpubBook> = withContext(Dispatchers.IO) {
        if (!epubFile.exists() || epubFile.length() == 0L) {
            return@withContext Result.failure(Exception("EPUB file does not exist or is empty"))
        }

        try {
            val zip = ZipFile(epubFile)
            val entries = zip.entries().toList().associateBy { it.name }

            // 1. Locate root opf file from META-INF/container.xml
            var opfPath = "OEBPS/content.opf"
            val containerEntry = entries["META-INF/container.xml"]
            if (containerEntry != null) {
                val containerXml = zip.getInputStream(containerEntry).bufferedReader().use { it.readText() }
                val rootFileRegex = Regex("""full-path=["']([^"']+)["']""")
                rootFileRegex.find(containerXml)?.groupValues?.get(1)?.let {
                    opfPath = it
                }
            }

            val opfEntry = entries[opfPath] ?: entries.values.firstOrNull { it.name.endsWith(".opf") }
                ?: return@withContext Result.failure(Exception("Cannot find content.opf in EPUB archive"))

            val opfBaseDir = if (opfEntry.name.contains("/")) {
                opfEntry.name.substringBeforeLast("/") + "/"
            } else ""

            val opfContent = zip.getInputStream(opfEntry).bufferedReader().use { it.readText() }

            // Extract title
            val titleRegex = Regex("""<dc:title[^>]*>(.*?)</dc:title>""", RegexOption.DOT_MATCHES_ALL)
            val title = titleRegex.find(opfContent)?.groupValues?.get(1)?.trim() ?: epubFile.nameWithoutExtension

            // Extract author
            val authorRegex = Regex("""<dc:creator[^>]*>(.*?)</dc:creator>""", RegexOption.DOT_MATCHES_ALL)
            val author = authorRegex.find(opfContent)?.groupValues?.get(1)?.trim() ?: "Unknown Author"

            // Manifest items: id -> href
            val manifest = mutableMapOf<String, String>()
            val itemRegex = Regex("""<item\s+[^>]*id=["']([^"']+)["'][^>]*href=["']([^"']+)["'][^>]*>""")
            val itemRegex2 = Regex("""<item\s+[^>]*href=["']([^"']+)["'][^>]*id=["']([^"']+)["'][^>]*>""")

            itemRegex.findAll(opfContent).forEach { match ->
                manifest[match.groupValues[1]] = match.groupValues[2]
            }
            itemRegex2.findAll(opfContent).forEach { match ->
                manifest[match.groupValues[2]] = match.groupValues[1]
            }

            // Spine itemrefs
            val spineIds = mutableListOf<String>()
            val itemrefRegex = Regex("""<itemref\s+[^>]*idref=["']([^"']+)["']""")
            itemrefRegex.findAll(opfContent).forEach { match ->
                spineIds.add(match.groupValues[1])
            }

            // Extract TOC labels if toc.ncx is available
            val tocLabels = mutableMapOf<String, String>()
            val ncxEntry = manifest["ncx"]?.let { entries[opfBaseDir + it] ?: entries[it] }
                ?: entries.values.firstOrNull { it.name.endsWith(".ncx") }

            if (ncxEntry != null) {
                val ncxContent = zip.getInputStream(ncxEntry).bufferedReader().use { it.readText() }
                val navPointRegex = Regex("""<navPoint[^>]*>.*?<text>(.*?)</text>.*?<content\s+src=["']([^"']+)["']""", RegexOption.DOT_MATCHES_ALL)
                navPointRegex.findAll(ncxContent).forEach { match ->
                    val label = match.groupValues[1].trim()
                    val src = match.groupValues[2].substringBefore("#")
                    tocLabels[src] = label
                }
            }

            // Parse chapters in order
            val chapters = mutableListOf<ReaderChapter>()
            var chapterIndex = 0

            val hrefsToProcess = if (spineIds.isNotEmpty()) {
                spineIds.mapNotNull { manifest[it] }
            } else {
                entries.keys.filter { it.endsWith(".xhtml") || it.endsWith(".html") }.sorted()
            }

            for (href in hrefsToProcess) {
                val cleanHref = href.substringBefore("#")
                // Skip cover.xhtml in chapter reading stream if it only contains an image
                if (cleanHref.contains("cover", ignoreCase = true)) continue

                val fullPath = if (entries.containsKey(opfBaseDir + cleanHref)) {
                    opfBaseDir + cleanHref
                } else if (entries.containsKey(cleanHref)) {
                    cleanHref
                } else {
                    entries.keys.firstOrNull { it.endsWith(cleanHref) }
                }

                if (fullPath != null) {
                    val entry = entries[fullPath]
                    if (entry != null) {
                        val html = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                        val paragraphs = extractParagraphsFromHtml(html)

                        // Title determination
                        var chapterTitle = tocLabels[cleanHref]
                        if (chapterTitle.isNullOrBlank()) {
                            val h1Regex = Regex("""<h1[^>]*>(.*?)</h1>""", RegexOption.DOT_MATCHES_ALL)
                            chapterTitle = h1Regex.find(html)?.groupValues?.get(1)?.let { stripTags(it) }?.trim()
                        }
                        if (chapterTitle.isNullOrBlank()) {
                            chapterTitle = "Chapter ${chapterIndex + 1}"
                        }

                        if (paragraphs.isNotEmpty()) {
                            chapters.add(
                                ReaderChapter(
                                    id = cleanHref,
                                    index = chapterIndex,
                                    title = chapterTitle,
                                    paragraphs = paragraphs
                                )
                            )
                            chapterIndex++
                        }
                    }
                }
            }

            // Check for cover image bytes
            var coverBytes: ByteArray? = null
            val coverHref = manifest["cover-image"] ?: manifest["cover"]
            val coverEntry = coverHref?.let { entries[opfBaseDir + it] ?: entries[it] }
                ?: entries.values.firstOrNull { it.name.contains("cover") && (it.name.endsWith(".jpg") || it.name.endsWith(".png")) }

            if (coverEntry != null) {
                coverBytes = zip.getInputStream(coverEntry).use { it.readBytes() }
            }

            zip.close()

            Result.success(
                ParsedEpubBook(
                    title = title,
                    author = author,
                    chapters = chapters,
                    coverBytes = coverBytes
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractParagraphsFromHtml(html: String): List<String> {
        val result = mutableListOf<String>()
        val pRegex = Regex("""<p[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
        val matches = pRegex.findAll(html).toList()

        if (matches.isNotEmpty()) {
            for (match in matches) {
                val cleaned = unescapeHtml(stripTags(match.groupValues[1])).trim()
                if (cleaned.isNotBlank()) {
                    result.add(cleaned)
                }
            }
        } else {
            // Fallback: extract all body text split by double newlines or block elements
            val bodyRegex = Regex("""<body[^>]*>(.*?)</body>""", RegexOption.DOT_MATCHES_ALL)
            val bodyContent = bodyRegex.find(html)?.groupValues?.get(1) ?: html
            val textOnly = stripTags(bodyContent)
            textOnly.lines().map { unescapeHtml(it).trim() }.filter { it.isNotBlank() }.forEach {
                result.add(it)
            }
        }
        return result
    }

    private fun stripTags(html: String): String {
        return html.replace(Regex("""<[^>]+>"""), " ")
            .replace(Regex("""\s+"""), " ")
    }

    private fun unescapeHtml(text: String): String {
        return text.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
    }
}
