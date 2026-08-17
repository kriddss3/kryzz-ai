package ai.daylight.assistant.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.os.SystemClock
import java.io.File
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Plays a generated MP3 reply once and reports completion on the main thread.
 *
 * While playing, [level] reports the live playback loudness (0..1) captured from the
 * audio session, so the voice bubble can react to the TTS exactly like the mic level
 * reacts to the user's speech.
 */
class VoicePlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    private var track: AudioTrack? = null
    private var visualizer: Visualizer? = null
    private var completion: ((Throwable?) -> Unit)? = null
    @Volatile private var streamStopped = false

    @Volatile
    private var currentLevel = 0f

    fun play(
        file: File,
        onStarted: () -> Unit = {},
        onComplete: (Throwable?) -> Unit
    ) {
        stop()
        completion = onComplete
        val instance = MediaPlayer()
        player = instance
        try {
            instance.apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { finish(null) }
                setOnErrorListener { _, what, extra ->
                    finish(IllegalStateException("Audio playback failed ($what/$extra)."))
                    true
                }
                setOnPreparedListener { prepared ->
                    if (player === prepared) {
                        try {
                            attachLevelCapture(prepared.audioSessionId)
                            prepared.start()
                            onStarted()
                        } catch (t: Throwable) {
                            finish(t)
                        }
                    }
                }
            }
            // Local MP3 preparation is usually quick, but doing it asynchronously prevents a
            // noticeable main-thread hitch exactly when the first spoken segment becomes ready.
            instance.prepareAsync()
        } catch (t: Throwable) {
            finish(t)
        }
    }

    /**
     * Plays a PCM16 mono stream as chunks arrive. Used for Fish HTTP streaming so the
     * first audio starts before the whole clip is downloaded.
     *
     * The preroll is written into the track *before* [AudioTrack.play] — starting an
     * empty track underruns immediately and was cutting Fish replies after ~100 ms.
     */
    suspend fun playPcm(
        chunks: Flow<ByteArray>,
        sampleRate: Int = VoiceConfig.FISH_PCM_SAMPLE_RATE,
        onStarted: () -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        stop()
        streamStopped = false
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(sampleRate * 2) // ~1 s of PCM16 mono so a short Fish burst cannot empty the track
        val instance = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBuf)
            .build()
        track = instance
        var started = false
        var framesWritten = 0L
        val preroll = ArrayList<ByteArray>()
        var prerollBytes = 0
        val prerollTarget = (sampleRate / 16) * 2 // ~60 ms of PCM16 mono before play()
        var leftover: Byte? = null
        try {
            suspend fun writeFully(raw: ByteArray) {
                val aligned = joinOddByte(leftover, raw)
                leftover = aligned.second
                val data = aligned.first
                var offset = 0
                while (offset < data.size && !streamStopped) {
                    if (started && instance.playState != AudioTrack.PLAYSTATE_PLAYING) {
                        runCatching { instance.play() }
                    }
                    val written = instance.write(data, offset, data.size - offset)
                    if (written < 0) throw IllegalStateException("Audio playback failed ($written).")
                    framesWritten += written / 2L
                    offset += written
                }
            }
            chunks.collect { chunk ->
                if (streamStopped) return@collect
                if (chunk.isEmpty()) return@collect
                currentLevel = pcmLevel(chunk)
                if (!started) {
                    preroll += chunk
                    prerollBytes += chunk.size
                    if (prerollBytes < prerollTarget) return@collect
                    for (buffered in preroll) writeFully(buffered)
                    preroll.clear()
                    instance.play()
                    started = true
                    attachLevelCapture(instance.audioSessionId)
                    onStarted()
                } else {
                    writeFully(chunk)
                }
            }
            if (!started && prerollBytes > 0 && !streamStopped) {
                for (buffered in preroll) writeFully(buffered)
                preroll.clear()
                instance.play()
                started = true
                attachLevelCapture(instance.audioSessionId)
                onStarted()
            }
            leftover?.let { _ -> leftover = null }
            if (!streamStopped) {
                // Drain the hardware buffer so the last spoken words are not cut off:
                // keep playing until the playback head reaches the frames we wrote.
                val drainStartedAt = SystemClock.elapsedRealtime()
                while (!streamStopped && SystemClock.elapsedRealtime() - drainStartedAt < TAIL_DRAIN_TIMEOUT_MS &&
                    instance.playState == AudioTrack.PLAYSTATE_PLAYING &&
                    instance.playbackHeadPosition < framesWritten
                ) {
                    delay(10)
                }
                runCatching { instance.stop() }
            }
        } finally {
            currentLevel = 0f
            releaseLevelCapture()
            runCatching { instance.release() }
            if (track === instance) track = null
        }
    }

    /** Live playback loudness, 0..1. Safe to poll often; 0 while nothing plays. */
    fun level(): Float {
        if (player?.isPlaying == true || track?.playState == AudioTrack.PLAYSTATE_PLAYING) return currentLevel
        return 0f
    }

    fun isPlaying(): Boolean = player?.isPlaying == true || track?.playState == AudioTrack.PLAYSTATE_PLAYING

    /** Playback volume, 0..1 — used to fade the reply out when the user talks over it. */
    fun setVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        runCatching { player?.setVolume(v, v) }
        runCatching { track?.setVolume(v) }
    }

    fun stop() {
        completion = null
        streamStopped = true
        releaseLevelCapture()
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        runCatching { track?.pause() }
        runCatching { track?.flush() }
        runCatching { track?.stop() }
        runCatching { track?.release() }
        track = null
    }

    private fun finish(error: Throwable?) {
        val callback = completion
        completion = null
        releaseLevelCapture()
        runCatching { player?.release() }
        player = null
        callback?.invoke(error)
    }

    private fun attachLevelCapture(sessionId: Int) {
        releaseLevelCapture()
        runCatching {
            visualizer = Visualizer(sessionId).apply {
                setCaptureSize(Visualizer.getCaptureSizeRange()[1])
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(visualizer: Visualizer, waveform: ByteArray, samplingRate: Int) {
                            var sum = 0L
                            for (sample in waveform) sum += (sample * sample).toLong()
                            val rms = sqrt(sum.toDouble() / waveform.size.coerceAtLeast(1))
                            currentLevel = (rms / 96.0).toFloat().coerceIn(0f, 1f)
                        }

                        override fun onFftDataCapture(visualizer: Visualizer, fft: ByteArray, samplingRate: Int) = Unit
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    true,
                    false
                )
                enabled = true
            }
        }
    }

    private fun releaseLevelCapture() {
        currentLevel = 0f
        runCatching { visualizer?.enabled = false }
        runCatching { visualizer?.release() }
        visualizer = null
    }

    private fun pcmLevel(chunk: ByteArray): Float {
        if (chunk.size < 2) return 0f
        var sum = 0.0
        var samples = 0
        var i = 0
        while (i + 1 < chunk.size) {
            val sample = (chunk[i].toInt() and 0xFF) or (chunk[i + 1].toInt() shl 8)
            val signed = sample.toShort().toInt()
            sum += signed.toDouble() * signed
            samples++
            i += 2
        }
        if (samples == 0) return 0f
        return (sqrt(sum / samples) / 8_000.0).toFloat().coerceIn(0f, 1f)
    }

    private companion object {
        /** Upper bound on waiting for the PCM tail to drain before stopping the track. */
        const val TAIL_DRAIN_TIMEOUT_MS = 4_000L

        /** Prepend a leftover odd byte from the previous chunk so PCM16 frames stay aligned. */
        fun joinOddByte(leftover: Byte?, raw: ByteArray): Pair<ByteArray, Byte?> {
            if (leftover == null && raw.size % 2 == 0) return raw to null
            val prefix = if (leftover != null) 1 else 0
            val total = prefix + raw.size
            val even = total and 1.inv()
            val out = ByteArray(even)
            var offset = 0
            if (leftover != null) {
                out[0] = leftover
                offset = 1
            }
            val copy = (even - offset).coerceAtMost(raw.size)
            System.arraycopy(raw, 0, out, offset, copy)
            val nextLeftover = if (copy < raw.size) raw[copy] else null
            return out to nextLeftover
        }
    }
}
