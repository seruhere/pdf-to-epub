package com.example.reader

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * State of the Text-to-Speech reader engine.
 */
data class TtsState(
    val isAvailable: Boolean = false,
    val isInitialized: Boolean = false,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val chapterIndex: Int = 0,
    val chapterTitle: String = "",
    val paragraphIndex: Int = 0,
    val totalParagraphs: Int = 0,
    val currentParagraphSnippet: String = "",
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val isVisible: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Manages native Android Text-to-Speech (TTS) for reading EPUB chapters aloud.
 * Provides paragraph-by-paragraph sequential reading, play/pause/resume,
 * paragraph skipping, speech rate & pitch controls, and automatic chapter advancement.
 */
class ReaderTtsManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val onChapterCompleted: (finishedChapterIndex: Int) -> Unit
) : TextToSpeech.OnInitListener {

    private val tag = "ReaderTtsManager"
    private var tts: TextToSpeech? = null

    private val _state = MutableStateFlow(TtsState())
    val state: StateFlow<TtsState> = _state.asStateFlow()

    // Active reading session cache
    private var currentParagraphs: List<String> = emptyList()
    private var currentUtteranceId: String? = null
    @Volatile
    private var isDisposed = false

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(tag, "Failed to instantiate TextToSpeech", e)
            _state.value = _state.value.copy(
                isAvailable = false,
                isInitialized = false,
                errorMessage = "Text-to-Speech could not be initialized: ${e.message}"
            )
        }
    }

    override fun onInit(status: Int) {
        if (isDisposed) return

        if (status == TextToSpeech.SUCCESS) {
            val engine = tts
            if (engine != null) {
                // Configure default language
                val locale = Locale.getDefault()
                val langResult = engine.isLanguageAvailable(locale)
                if (langResult >= TextToSpeech.LANG_AVAILABLE) {
                    engine.language = locale
                } else {
                    engine.language = Locale.US
                }

                engine.setSpeechRate(_state.value.speechRate)
                engine.setPitch(_state.value.pitch)

                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        if (utteranceId != currentUtteranceId) return
                        _state.value = _state.value.copy(
                            isPlaying = true,
                            isPaused = false
                        )
                    }

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId != currentUtteranceId) return
                        coroutineScope.launch(Dispatchers.Main) {
                            handleUtteranceCompleted()
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId != currentUtteranceId) return
                        Log.w(tag, "TTS utterance error: $utteranceId")
                        coroutineScope.launch(Dispatchers.Main) {
                            handleUtteranceCompleted()
                        }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        if (utteranceId != currentUtteranceId) return
                        Log.w(tag, "TTS utterance error $errorCode: $utteranceId")
                        coroutineScope.launch(Dispatchers.Main) {
                            handleUtteranceCompleted()
                        }
                    }
                })

                _state.value = _state.value.copy(
                    isAvailable = true,
                    isInitialized = true,
                    errorMessage = null
                )
            }
        } else {
            Log.e(tag, "TTS init returned status $status")
            _state.value = _state.value.copy(
                isAvailable = false,
                isInitialized = false,
                errorMessage = "Text-to-Speech engine is not available on this device."
            )
        }
    }

    /**
     * Start reading a chapter aloud from a specific paragraph index.
     */
    fun startChapter(
        chapterIndex: Int,
        chapterTitle: String,
        paragraphs: List<String>,
        startParagraphIndex: Int = 0
    ) {
        val filtered = paragraphs.map { it.trim() }.filter { it.isNotBlank() }
        currentParagraphs = filtered
        val targetIndex = startParagraphIndex.coerceIn(0, (filtered.size - 1).coerceAtLeast(0))

        _state.value = _state.value.copy(
            chapterIndex = chapterIndex,
            chapterTitle = chapterTitle,
            paragraphIndex = targetIndex,
            totalParagraphs = filtered.size,
            isVisible = true,
            errorMessage = null
        )

        if (filtered.isNotEmpty()) {
            speakCurrentParagraph()
        } else {
            _state.value = _state.value.copy(isPlaying = false, isPaused = false)
        }
    }

    /**
     * Speak or restart speaking the current paragraph index.
     */
    private fun speakCurrentParagraph() {
        val engine = tts ?: return
        if (!_state.value.isInitialized) return

        val pIndex = _state.value.paragraphIndex
        if (pIndex !in currentParagraphs.indices) {
            _state.value = _state.value.copy(isPlaying = false, isPaused = false)
            return
        }

        val rawText = currentParagraphs[pIndex]
        val cleanText = cleanTextForSpeech(rawText)

        val snippet = if (cleanText.length > 90) cleanText.take(90) + "..." else cleanText
        _state.value = _state.value.copy(
            paragraphIndex = pIndex,
            currentParagraphSnippet = snippet,
            isPlaying = true,
            isPaused = false
        )

        val utteranceId = "epub_tts_${_state.value.chapterIndex}_${pIndex}_${System.currentTimeMillis()}"
        currentUtteranceId = utteranceId

        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        engine.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    /**
     * Handle when speech for one paragraph finishes naturally.
     */
    private fun handleUtteranceCompleted() {
        val nextIndex = _state.value.paragraphIndex + 1
        if (nextIndex < currentParagraphs.size) {
            _state.value = _state.value.copy(paragraphIndex = nextIndex)
            speakCurrentParagraph()
        } else {
            // Chapter has concluded!
            _state.value = _state.value.copy(
                isPlaying = false,
                isPaused = false
            )
            onChapterCompleted(_state.value.chapterIndex)
        }
    }

    /**
     * Pause reading aloud without losing current paragraph position.
     */
    fun pause() {
        try {
            tts?.stop()
        } catch (_: Exception) {}
        currentUtteranceId = null
        _state.value = _state.value.copy(
            isPlaying = false,
            isPaused = true
        )
    }

    /**
     * Resume reading aloud from the paused paragraph.
     */
    fun resume() {
        if (!_state.value.isInitialized) return
        speakCurrentParagraph()
    }

    /**
     * Toggle play / pause.
     */
    fun togglePlayPause(
        currentChapterIndex: Int,
        currentChapterTitle: String,
        paragraphs: List<String>
    ) {
        when {
            _state.value.isPlaying -> pause()
            _state.value.isPaused -> resume()
            else -> {
                // If not currently loaded or on a different chapter, start fresh
                if (currentParagraphs.isEmpty() || _state.value.chapterIndex != currentChapterIndex) {
                    startChapter(currentChapterIndex, currentChapterTitle, paragraphs, 0)
                } else {
                    speakCurrentParagraph()
                }
            }
        }
    }

    /**
     * Skip to a specific paragraph in the active chapter.
     */
    fun jumpToParagraph(paragraphIndex: Int) {
        if (currentParagraphs.isEmpty()) return
        val target = paragraphIndex.coerceIn(0, currentParagraphs.size - 1)
        _state.value = _state.value.copy(paragraphIndex = target)
        speakCurrentParagraph()
    }

    /**
     * Skip to the next paragraph.
     */
    fun skipNext() {
        if (currentParagraphs.isEmpty()) return
        val next = (_state.value.paragraphIndex + 1).coerceAtMost(currentParagraphs.size - 1)
        if (next != _state.value.paragraphIndex) {
            _state.value = _state.value.copy(paragraphIndex = next)
            speakCurrentParagraph()
        }
    }

    /**
     * Skip to the previous paragraph.
     */
    fun skipPrevious() {
        if (currentParagraphs.isEmpty()) return
        val prev = (_state.value.paragraphIndex - 1).coerceAtLeast(0)
        _state.value = _state.value.copy(paragraphIndex = prev)
        speakCurrentParagraph()
    }

    /**
     * Set the playback rate (e.g. 0.75x, 1.0x, 1.25x, 1.5x, 2.0x).
     */
    fun setSpeechRate(rate: Float) {
        val clamped = rate.coerceIn(0.5f, 2.5f)
        _state.value = _state.value.copy(speechRate = clamped)
        tts?.setSpeechRate(clamped)
        if (_state.value.isPlaying) {
            speakCurrentParagraph()
        }
    }

    /**
     * Set the speech voice pitch.
     */
    fun setPitch(pitch: Float) {
        val clamped = pitch.coerceIn(0.5f, 2.0f)
        _state.value = _state.value.copy(pitch = clamped)
        tts?.setPitch(clamped)
        if (_state.value.isPlaying) {
            speakCurrentParagraph()
        }
    }

    /**
     * Show or hide the floating TTS controls bar.
     */
    fun setVisible(visible: Boolean) {
        _state.value = _state.value.copy(isVisible = visible)
    }

    /**
     * Completely stop reading and reset state.
     */
    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {}
        currentUtteranceId = null
        _state.value = _state.value.copy(
            isPlaying = false,
            isPaused = false
        )
    }

    /**
     * Close player bar and stop audio.
     */
    fun closePlayer() {
        stop()
        _state.value = _state.value.copy(isVisible = false)
    }

    /**
     * Release all TTS engine resources when ViewModel or Reader is destroyed.
     */
    fun release() {
        isDisposed = true
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(tag, "Error shutting down TTS", e)
        }
        tts = null
    }

    companion object {
        /**
         * Preprocesses text by stripping formatting markers (headers, bullet points, HTML leftovers)
         * so that the speech engine pronounces the prose naturally.
         */
        fun cleanTextForSpeech(text: String): String {
            return text
                .replace(Regex("<[^>]*>"), "") // HTML tags
                .replace(Regex("^#{1,6}\\s*"), "") // Markdown headers
                .replace(Regex("^•\\s*"), "") // Bullet symbols
                .replace(Regex("^❝\\s*"), "") // Quote symbols
                .replace(Regex("^>\\s*"), "") // Quotes
                .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1") // Bold
                .replace(Regex("\\*(.*?)\\*"), "$1") // Italics
                .replace("&amp;", "and")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }
}
