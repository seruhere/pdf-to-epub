package com.example.converter

import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubBuilder {

    fun buildEpub(
        outputFile: File,
        title: String,
        author: String,
        chapters: List<ConvertedChapter>,
        coverImageFile: File?,
        typography: TypographyPreset = TypographyPreset.CLEAN_MODERN,
        fontFamily: EpubFontFamily = EpubFontFamily.SANS_SERIF,
        fontSizePt: Int = ConversionOptions.DEFAULT_FONT_SIZE_PT,
        marginHorizontalPercent: Int = 5,
        marginVerticalPercent: Int = 4
    ): Boolean {
        val zipOut = ZipOutputStream(FileOutputStream(outputFile))
        try {
            val bookId = "urn:uuid:" + UUID.randomUUID().toString()

            // 1. Write uncompressed mimetype entry at offset 0
            val mimetypeBytes = "application/epub+zip".toByteArray(Charsets.US_ASCII)
            val mimetypeEntry = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimetypeBytes.size.toLong()
                compressedSize = mimetypeBytes.size.toLong()
                val crc = CRC32()
                crc.update(mimetypeBytes)
                setCrc(crc.value)
            }
            zipOut.putNextEntry(mimetypeEntry)
            zipOut.write(mimetypeBytes)
            zipOut.closeEntry()

            // Subsequent files are deflated (standard compression)
            zipOut.setMethod(ZipOutputStream.DEFLATED)

            // 2. Write META-INF/container.xml
            zipOut.putNextEntry(ZipEntry("META-INF/container.xml"))
            val containerXml = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>""".trimIndent()
            zipOut.write(containerXml.toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            // 3. Write OEBPS/styles.css
            zipOut.putNextEntry(ZipEntry("OEBPS/styles.css"))
            val selectedFontCss = fontFamily.fontCss
            val css = """
@charset "utf-8";
body {
    $selectedFontCss
    font-size: ${fontSizePt}pt;
    line-height: 1.65;
    margin: ${marginVerticalPercent}% ${marginHorizontalPercent}%;
    color: #212121;
    background-color: #FFFFFF;
}
h1, h2, h3, h4, h5, h6 {
    font-weight: 700;
    line-height: 1.3;
    margin-top: 1.6em;
    margin-bottom: 0.6em;
    page-break-after: avoid;
    color: #1A237E;
}
h1.chapter-title {
    font-size: 1.85em;
    text-align: center;
    margin-top: 2em;
    margin-bottom: 1.2em;
    border-bottom: 2px solid #E0E0E0;
    padding-bottom: 0.5em;
}
p {
    margin: 0 0 1em 0;
    text-align: justify;
    text-justify: inter-word;
    text-indent: 1.2em;
}
p.first-after-heading {
    text-indent: 0;
}
blockquote {
    margin: 1em 2em;
    padding-left: 1em;
    border-left: 3px solid #7986CB;
    font-style: italic;
    color: #424242;
}
.cover-container {
    text-align: center;
    padding: 0;
    margin: 0;
    height: 100vh;
}
.cover-image {
    max-width: 100%;
    max-height: 100%;
    height: auto;
    width: auto;
    object-fit: contain;
    display: block;
    margin: 0 auto;
}
.page-break {
    page-break-before: always;
}
""".trimIndent()
            zipOut.write(css.toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            // 4. If cover image exists, write it to OEBPS/cover.jpg and OEBPS/cover.xhtml
            val hasCover = coverImageFile != null && coverImageFile.exists() && coverImageFile.length() > 0
            if (hasCover) {
                zipOut.putNextEntry(ZipEntry("OEBPS/cover.jpg"))
                coverImageFile.inputStream().use { it.copyTo(zipOut) }
                zipOut.closeEntry()

                zipOut.putNextEntry(ZipEntry("OEBPS/cover.xhtml"))
                val coverXhtml = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="en">
<head>
  <title>Cover</title>
  <link rel="stylesheet" type="text/css" href="styles.css" />
  <style>
    body { margin: 0; padding: 0; text-align: center; background-color: #000000; }
    img { max-width: 100%; max-height: 100vh; height: auto; width: auto; object-fit: contain; }
  </style>
</head>
<body>
  <div class="cover-container">
    <img src="cover.jpg" alt="${escapeXml(title)}" class="cover-image" />
  </div>
</body>
</html>""".trimIndent()
                zipOut.write(coverXhtml.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()
            }

            // 5. Write each chapter XHTML file
            chapters.forEachIndexed { i, chapter ->
                val fileName = "OEBPS/chapter_${i + 1}.xhtml"
                zipOut.putNextEntry(ZipEntry(fileName))

                val safeTitle = escapeXml(chapter.title.ifBlank { "Chapter ${i + 1}" })
                val chapterXhtml = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="en">
<head>
  <meta charset="utf-8" />
  <title>$safeTitle</title>
  <link rel="stylesheet" type="text/css" href="styles.css" />
</head>
<body>
  <section epub:type="chapter">
    <h1 class="chapter-title">$safeTitle</h1>
    <div class="chapter-content">
${chapter.htmlContent}
    </div>
  </section>
</body>
</html>""".trimIndent()
                zipOut.write(chapterXhtml.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()
            }

            // 6. Write EPUB 3 Navigation Document OEBPS/nav.xhtml
            zipOut.putNextEntry(ZipEntry("OEBPS/nav.xhtml"))
            val navXhtml = buildString {
                append("""<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="en">
<head>
  <meta charset="utf-8" />
  <title>Table of Contents</title>
  <link rel="stylesheet" type="text/css" href="styles.css" />
</head>
<body>
  <nav epub:type="toc" id="toc">
    <h1>Table of Contents</h1>
    <ol>
""")
                if (hasCover) {
                    append("""      <li><a href="cover.xhtml">Cover</a></li>""" + "\n")
                }
                chapters.forEachIndexed { index, chapter ->
                    val safeChapterTitle = escapeXml(chapter.title.ifBlank { "Chapter ${index + 1}" })
                    append("""      <li><a href="chapter_${index + 1}.xhtml">$safeChapterTitle</a></li>""" + "\n")
                }
                append("""    </ol>
  </nav>
</body>
</html>""")
            }
            zipOut.write(navXhtml.toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            // 7. Write EPUB 2 NCX file OEBPS/toc.ncx
            zipOut.putNextEntry(ZipEntry("OEBPS/toc.ncx"))
            val ncxXml = buildString {
                append("""<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head>
    <meta name="dtb:uid" content="$bookId"/>
    <meta name="dtb:depth" content="1"/>
    <meta name="dtb:totalPageCount" content="0"/>
    <meta name="dtb:maxPageNumber" content="0"/>
  </head>
  <docTitle>
    <text>${escapeXml(title)}</text>
  </docTitle>
  <docAuthor>
    <text>${escapeXml(author)}</text>
  </docAuthor>
  <navMap>
""")
                var playOrder = 1
                if (hasCover) {
                    append("""    <navPoint id="navPoint-$playOrder" playOrder="$playOrder">
      <navLabel><text>Cover</text></navLabel>
      <content src="cover.xhtml"/>
    </navPoint>
""")
                    playOrder++
                }
                chapters.forEachIndexed { index, chapter ->
                    val safeChapterTitle = escapeXml(chapter.title.ifBlank { "Chapter ${index + 1}" })
                    append("""    <navPoint id="navPoint-$playOrder" playOrder="$playOrder">
      <navLabel><text>$safeChapterTitle</text></navLabel>
      <content src="chapter_${index + 1}.xhtml"/>
    </navPoint>
""")
                    playOrder++
                }
                append("""  </navMap>
</ncx>""")
            }
            zipOut.write(ncxXml.toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            // 8. Write OEBPS/content.opf
            zipOut.putNextEntry(ZipEntry("OEBPS/content.opf"))
            val opfXml = buildString {
                append("""<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookId" version="3.0">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
    <dc:identifier id="BookId">$bookId</dc:identifier>
    <dc:title>${escapeXml(title)}</dc:title>
    <dc:creator id="creator">${escapeXml(author)}</dc:creator>
    <meta refines="#creator" property="role" scheme="marc:relators">aut</meta>
    <dc:language>en</dc:language>
    <meta property="dcterms:modified">${java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())}</meta>
""")
                if (hasCover) {
                    append("""    <meta name="cover" content="cover-image" />""" + "\n")
                }
                append("""  </metadata>
  <manifest>
    <item id="css" href="styles.css" media-type="text/css"/>
    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
""")
                if (hasCover) {
                    append("""    <item id="cover-image" href="cover.jpg" media-type="image/jpeg" properties="cover-image"/>""" + "\n")
                    append("""    <item id="cover-page" href="cover.xhtml" media-type="application/xhtml+xml"/>""" + "\n")
                }
                chapters.forEachIndexed { index, _ ->
                    append("""    <item id="chapter_${index + 1}" href="chapter_${index + 1}.xhtml" media-type="application/xhtml+xml"/>""" + "\n")
                }
                append("""  </manifest>
  <spine toc="ncx">
""")
                if (hasCover) {
                    append("""    <itemref idref="cover-page" />""" + "\n")
                }
                chapters.forEachIndexed { index, _ ->
                    append("""    <itemref idref="chapter_${index + 1}" />""" + "\n")
                }
                append("""  </spine>
  <guide>
""")
                if (hasCover) {
                    append("""    <reference type="cover" title="Cover" href="cover.xhtml"/>""" + "\n")
                }
                if (chapters.isNotEmpty()) {
                    append("""    <reference type="text" title="Beginning" href="chapter_1.xhtml"/>""" + "\n")
                }
                append("""  </guide>
</package>""")
            }
            zipOut.write(opfXml.toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            zipOut.finish()
            return true
        } finally {
            zipOut.close()
        }
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
