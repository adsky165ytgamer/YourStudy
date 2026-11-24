package com.yourstudy.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.PowerManager
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MusicUi(
    val queueSize: Int = 0,
    val index: Int = 0,
    val title: String? = null,
    val artist: String? = null,
    val cover: ImageBitmap? = null,
    val playing: Boolean = false,
    val autoPlay: Boolean = true,
    val error: String? = null,
)

private data class Meta(val title: String?, val artist: String?, val cover: ImageBitmap?)

/**
 * User-chosen focus music. A playlist of SAF audio files played with MediaPlayer, scoped to the focus
 * session: it starts with focus, pauses for breaks, and stops when the session ends. It lives in the
 * process kept alive by OverlayService's foreground notification. Main thread only; metadata and cover
 * decoding run on Dispatchers.IO and are discarded if a newer track was requested meanwhile.
 */
object FocusMusic {
    private lateinit var app: Context
    private val _ui = MutableStateFlow(MusicUi())
    val ui: StateFlow<MusicUi> = _ui.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var queue: List<Uri> = emptyList()
    private var index = 0
    private var player: MediaPlayer? = null
    private var prepared = false
    private var wantPlaying = false
    private var resumeAfterBreak = false
    private var generation = 0
    private var errorStreak = 0
    private var noisyRegistered = false
    private var focusRequest: AudioFocusRequest? = null

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()

    fun init(ctx: Context) {
        app = ctx.applicationContext
        queue = Store.musicQueue.map(Uri::parse)
        _ui.value = MusicUi(queueSize = queue.size, autoPlay = Store.musicAutoPlay)
        if (queue.isNotEmpty()) loadMeta(0, generation)
    }

    private fun ensure(ctx: Context) { if (!::app.isInitialized) init(ctx) }

    private fun allowed(): Boolean = RemoteControl.config.value.feature("focus_music").let { it.enabled && !it.locked }

    // ---------------------------------------------------------------- queue

    fun setQueue(ctx: Context, uris: List<Uri>) {
        ensure(ctx)
        val cr = ctx.contentResolver
        val flag = Intent.FLAG_GRANT_READ_URI_PERMISSION
        queue.filter { it !in uris }.forEach { runCatching { cr.releasePersistableUriPermission(it, flag) } }
        uris.forEach { runCatching { cr.takePersistableUriPermission(it, flag) } }
        stop()
        queue = uris
        index = 0
        errorStreak = 0
        Store.musicQueue = uris.map(Uri::toString)
        _ui.update { it.copy(queueSize = uris.size, index = 0, title = null, artist = null, cover = null, playing = false, error = null) }
        if (uris.isNotEmpty()) loadMeta(0, ++generation)
    }

    fun clear(ctx: Context) = setQueue(ctx, emptyList())

    fun setAutoPlay(v: Boolean) { Store.musicAutoPlay = v; _ui.update { it.copy(autoPlay = v) } }

    // ---------------------------------------------------------------- transport

    fun toggle() { if (_ui.value.playing) pause() else play() }

    fun play() {
        if (queue.isEmpty() || !allowed()) return
        wantPlaying = true
        val p = player
        when {
            p == null -> prepare(index)
            prepared -> if (requestFocus()) { p.start(); setPlaying(true); registerNoisy() }
        }
    }

    fun pause() {
        wantPlaying = false
        player?.takeIf { prepared && it.isPlaying }?.pause()
        setPlaying(false)
        abandonFocus(); unregisterNoisy()
    }

    fun next() = prepare(index + 1)
    fun previous() = prepare(index - 1)

    /** Ends playback and releases everything (session over, queue replaced). */
    fun stop() {
        wantPlaying = false; resumeAfterBreak = false
        generation++
        releasePlayer()
        setPlaying(false)
        abandonFocus(); unregisterNoisy()
    }

    // ---------------------------------------------------------------- session hooks (OverlayService)

    /** Called when the timer service starts, including after process death. */
    fun onServiceStart(ctx: Context, phase: Phase) {
        ensure(ctx)
        if (phase == Phase.FOCUSING && Store.musicAutoPlay) play()
    }

    fun onPhase(ctx: Context, from: Phase, to: Phase) {
        ensure(ctx)
        when {
            from == to -> Unit
            to == Phase.BREAK_5M -> { resumeAfterBreak = wantPlaying || _ui.value.playing; pause() }
            from == Phase.BREAK_5M && to == Phase.FOCUSING -> if (resumeAfterBreak) { resumeAfterBreak = false; play() }
            to == Phase.IDLE || to == Phase.COMPLETED -> stop()
        }
    }

    // ---------------------------------------------------------------- player internals

    private fun prepare(i: Int) {
        releasePlayer()
        if (queue.isEmpty()) return
        index = i.mod(queue.size)
        val g = ++generation
        loadMeta(index, g)
        val mp = MediaPlayer()
        player = mp; prepared = false
        mp.setAudioAttributes(attrs)
        mp.setWakeMode(app, PowerManager.PARTIAL_WAKE_LOCK)
        mp.setOnPreparedListener {
            if (g != generation) return@setOnPreparedListener
            prepared = true; errorStreak = 0
            if (wantPlaying && requestFocus()) { it.start(); setPlaying(true); registerNoisy() }
        }
        mp.setOnCompletionListener { if (g == generation) next() }
        mp.setOnErrorListener { _, _, _ -> if (g == generation) onTrackError(); true }
        runCatching { mp.setDataSource(app, queue[index]); mp.prepareAsync() }.onFailure { onTrackError() }
    }

    private fun onTrackError() {
        errorStreak++
        if (errorStreak >= queue.size.coerceAtLeast(1)) {
            stop()
            _ui.update { it.copy(error = "Can't play the selected audio") }
        } else next() // skip an unreadable track
    }

    private fun releasePlayer() {
        player?.let { runCatching { it.setOnPreparedListener(null); it.setOnCompletionListener(null); it.setOnErrorListener(null); it.release() } }
        player = null; prepared = false
    }

    private fun setPlaying(v: Boolean) { if (_ui.value.playing != v) _ui.update { it.copy(playing = v) } }

    // ---------------------------------------------------------------- audio focus & noisy

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        val p = player
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> pause() // another player took over for good
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { if (prepared && p?.isPlaying == true) { p.pause(); setPlaying(false) } }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> p?.setVolume(0.25f, 0.25f)
            AudioManager.AUDIOFOCUS_GAIN -> {
                p?.setVolume(1f, 1f)
                if (wantPlaying && prepared && p?.isPlaying == false) { p.start(); setPlaying(true) }
            }
        }
    }

    private fun requestFocus(): Boolean {
        val am = app.getSystemService(AudioManager::class.java)
        val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs).setOnAudioFocusChangeListener(focusListener).build().also { focusRequest = it }
        return am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        focusRequest?.let { app.getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
    }

    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { pause() } // headphones unplugged
    }

    private fun registerNoisy() {
        if (noisyRegistered) return
        ContextCompat.registerReceiver(app, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        noisyRegistered = true
    }

    private fun unregisterNoisy() {
        if (!noisyRegistered) return
        runCatching { app.unregisterReceiver(noisy) }
        noisyRegistered = false
    }

    // ---------------------------------------------------------------- metadata & cover

    private fun loadMeta(i: Int, g: Int) {
        val uri = queue.getOrNull(i) ?: return
        _ui.update { it.copy(index = i, queueSize = queue.size, error = null) }
        scope.launch {
            val meta = withContext(Dispatchers.IO) { readMeta(uri) }
            if (g == generation) _ui.update { it.copy(title = meta.title, artist = meta.artist, cover = meta.cover) }
        }
    }

    private fun readMeta(uri: Uri): Meta = runCatching {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(app, uri)
            val name = runCatching {
                app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(0)?.substringBeforeLast('.') else null }
            }.getOrNull()
            Meta(
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: name,
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() },
                r.embeddedPicture?.let { decodeSampled(it, 1024) }?.asImageBitmap(),
            )
        } finally { r.release() }
    }.getOrDefault(Meta(null, null, null))

    /** Downsamples before decoding so a 4000 px cover never costs a full-size bitmap. */
    private fun decodeSampled(bytes: ByteArray, max: Int): android.graphics.Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= max && bounds.outHeight / (sample * 2) >= max) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
