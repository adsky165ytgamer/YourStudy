package com.yourstudy.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Continuous speech capture. committed = finalized text + manual edits (source of truth),
 * partial = the in-progress hypothesis. Recognition restarts itself after every phrase/silence/recoverable
 * error until stop() is called; a generation counter makes ghost restarts after End impossible.
 */
class VoiceController(context: Context) {
    private val app = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var generation = 0
    private var errorStreak = 0
    private var onDevice = Build.VERSION.SDK_INT >= 33

    var committed by mutableStateOf("")
        private set
    var partial by mutableStateOf("")
        private set
    var keepListening by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
    var languageTag by mutableStateOf(Store.voiceLang)
        private set

    val combined: String get() = join(committed, partial)

    private val restart = Runnable { if (keepListening) begin() }

    fun load(text: String) { committed = text; partial = "" }

    /** The user's edit becomes the truth; stale partials can never overwrite it. */
    fun onManualTextEdited(text: String) {
        committed = text; partial = ""
        // Drop the in-flight utterance so its words are not appended twice.
        if (keepListening) schedule(600)
    }

    fun start() {
        if (keepListening) return
        if (!SpeechRecognizer.isRecognitionAvailable(app)) {
            message = "Speech recognition isn't available on this device."; return
        }
        message = null; errorStreak = 0; keepListening = true
        begin()
    }

    /** End: stop immediately, keep any in-progress words, never restart. */
    fun stop() {
        keepListening = false
        generation++
        handler.removeCallbacks(restart)
        commitPartial()
        release()
    }

    fun destroy() = stop()

    fun setLanguage(tag: String) {
        languageTag = tag; Store.voiceLang = tag
        if (keepListening) { commitPartial(); schedule(100) }
    }

    private fun schedule(delayMs: Long) {
        handler.removeCallbacks(restart)
        generation++ // invalidates callbacks of the session being dropped
        release()
        handler.postDelayed(restart, delayMs)
    }

    private fun begin() {
        release()
        val g = ++generation
        val r = if (onDevice && SpeechRecognizer.isOnDeviceRecognitionAvailable(app))
            SpeechRecognizer.createOnDeviceSpeechRecognizer(app)
        else { onDevice = false; SpeechRecognizer.createSpeechRecognizer(app) }
        recognizer = r
        r.setRecognitionListener(listener(g))
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, app.packageName)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            }
        )
    }

    private fun release() {
        recognizer?.let { r ->
            runCatching { r.setRecognitionListener(null); r.cancel(); r.destroy() }
        }
        recognizer = null
    }

    private fun commitPartial() {
        if (partial.isNotBlank()) committed = join(committed, partial)
        partial = ""
    }

    private fun fatal(msg: String) { stop(); message = msg }

    private fun listener(g: Int) = object : RecognitionListener {
        private fun live() = g == generation && keepListening
        private fun first(b: Bundle?) =
            b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()

        override fun onPartialResults(b: Bundle?) {
            if (!live()) return
            val t = first(b)
            if (t.isNotEmpty()) { partial = t; errorStreak = 0 }
        }

        override fun onResults(b: Bundle?) {
            if (!live()) return
            val t = first(b)
            if (t.isNotEmpty()) { committed = join(committed, t); errorStreak = 0 }
            partial = ""
            schedule(100)
        }

        override fun onError(code: Int) {
            if (!live()) return
            commitPartial()
            when (code) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> fatal("Microphone permission is required.")
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                    if (onDevice) { onDevice = false; schedule(300) }
                    else fatal("This language isn't available for speech recognition on this device.")
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_NO_MATCH -> schedule(150)
                else -> {
                    errorStreak++
                    if (errorStreak >= 6) fatal("Speech recognition keeps failing. Check your connection or language pack.")
                    else schedule(if (code == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 800L else 400L * errorStreak)
                }
            }
        }

        override fun onReadyForSpeech(p: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rms: Float) {}
        override fun onBufferReceived(buf: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(type: Int, p: Bundle?) {}
    }

    private fun join(a: String, b: String): String = when {
        b.isBlank() -> a
        a.isEmpty() -> b
        a.last().isWhitespace() -> a + b
        else -> "$a $b"
    }
}
