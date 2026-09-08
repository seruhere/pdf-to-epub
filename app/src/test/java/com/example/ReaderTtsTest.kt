package com.example

import com.example.reader.ReaderTtsManager
import com.example.reader.TtsState
import org.junit.Assert.*
import org.junit.Test

class ReaderTtsTest {

    @Test
    fun ttsState_defaultValues_areCorrect() {
        val state = TtsState()
        assertFalse(state.isPlaying)
        assertFalse(state.isPaused)
        assertFalse(state.isInitialized)
        assertFalse(state.isVisible)
        assertEquals(0, state.chapterIndex)
        assertEquals("", state.chapterTitle)
        assertEquals(0, state.paragraphIndex)
        assertEquals(0, state.totalParagraphs)
        assertEquals(1.0f, state.speechRate, 0.001f)
        assertEquals(1.0f, state.pitch, 0.001f)
        assertNull(state.errorMessage)
        assertEquals("", state.currentParagraphSnippet)
    }

    @Test
    fun cleanTextForSpeech_stripsMarkdownHeadings() {
        val input = "### Chapter 1: The Beginning"
        val cleaned = ReaderTtsManager.cleanTextForSpeech(input)
        assertEquals("Chapter 1: The Beginning", cleaned)
    }

    @Test
    fun cleanTextForSpeech_stripsQuoteMarksAndBullets() {
        val quote = "❝ To be or not to be, that is the question."
        assertEquals("To be or not to be, that is the question.", ReaderTtsManager.cleanTextForSpeech(quote))

        val bullet = "• First key concept"
        assertEquals("First key concept", ReaderTtsManager.cleanTextForSpeech(bullet))
    }

    @Test
    fun cleanTextForSpeech_stripsBoldAndItalics() {
        val markdown = "This is **very important** and *subtle* formatting."
        val cleaned = ReaderTtsManager.cleanTextForSpeech(markdown)
        assertEquals("This is very important and subtle formatting.", cleaned)
    }

    @Test
    fun cleanTextForSpeech_stripsHtmlTags() {
        val html = "<p>This is a paragraph with <span class=\"highlight\">highlighted text</span>.</p>"
        val cleaned = ReaderTtsManager.cleanTextForSpeech(html)
        assertEquals("This is a paragraph with highlighted text.", cleaned)
    }

    @Test
    fun cleanTextForSpeech_normalizesExcessiveWhitespace() {
        val messy = "Hello    world!   \n\n\n  This is a   test.  "
        val cleaned = ReaderTtsManager.cleanTextForSpeech(messy)
        assertEquals("Hello world! This is a test.", cleaned)
    }

    @Test
    fun cleanTextForSpeech_handlesEmptyOrBlank() {
        assertEquals("", ReaderTtsManager.cleanTextForSpeech(""))
        assertEquals("", ReaderTtsManager.cleanTextForSpeech("   \n\t  "))
    }
}
