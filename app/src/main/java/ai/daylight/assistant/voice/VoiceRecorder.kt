package ai.daylight.assistant.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.NoiseSuppressor
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Records one short voice message from the system microphone.
 *
 * Uses [AudioRecord] rather than [MediaRecorder]: on this device MediaRecorder
 * (`VOICE_RECOGNITION` and often `MIC`) reports `maxAmplitude = 0`, so the
 * session never saw speech and could neither auto-send nor barge-in.
 * Raw PCM16 peaks are reliable, work while Kryzz is talking (duplex), and
 * write a 16 kHz mono WAV that Whisper transcribes well.
 */
class VoiceRecorder(private val context: Context) {
    private var recorder: AudioRecord? = null
    private var output: File? = null
    private var captureThread: Thread? = null
    @Volatile private var capturing = false
    /**
     * When true the capture loop still drains the AudioRecord buffer (so the OS doesn't
     * complain about an un-read mic) but writes zeros instead of the captured samples and
     * never bumps the peak. The downstream STT/TTS path then sees continuous silence, so
     * the session stays open without barge-in or auto-stop firing.
     */
    @Volatile var muted: Boolean = false
    private val peak = AtomicInteger(0)
    private val voicedFrames = AtomicInteger(0)
    private val frames = AtomicInteger(0)
    private val effects = mutableListOf<AudioEffect>()

    /**
     * v2.1: how clearly pitched a 20ms frame must be to count as voice (see
     * [SpeechFrameAnalyzer]). Set from the user's Voice sensitivity before a session starts.
     */
    @Volatile var minPeriodicity: Float = SensitivityProfile.of(VoiceSensitivity.NORMAL).minPeriodicity

    fun start(): Result<Unit> = runCatching {
        stopInternal()
        muted = false
        val file = File(context.cacheDir, "kryzz-voice-${System.currentTimeMillis()}.wav")
        FileOutputStream(file).use { it.write(wavHeader(0)) }
        output = file

        val minBuf = AudioRecord.getMinBufferSize(
            VOICE_CAPTURE_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) error("The microphone is not available.")
        val bufSize = minBuf * 2
        val instance = openRecorder(bufSize)
            ?: error("Could not open the microphone.")
        recorder = instance
        attachEffects(instance.audioSessionId)
        resetCounters()
        capturing = true
        try {
            instance.startRecording()
        } catch (t: Throwable) {
            capturing = false
            runCatching { instance.release() }
            recorder = null
            throw t
        }
        if (instance.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            capturing = false
            runCatching { instance.release() }
            recorder = null
            error("The microphone did not start recording.")
        }
        captureThread = thread(name = "kryzz-mic", isDaemon = true) {
            captureLoop(instance, file, minBuf)
        }
        Unit
    }.onFailure {
        stopInternal()
        output?.delete()
        output = null
    }

    /**
     * Peak amplitude since the last poll, 0..32767, measured after the 200 Hz high-pass so wind
     * rumble does not move the meter. Resets on read, like MediaRecorder.maxAmplitude.
     */
    fun level(): Int {
        voicedFrames.set(0)
        frames.set(0)
        return peak.getAndSet(0)
    }

    /** v2.1: the peak plus how many frames since the last poll sounded like a voice. Resets on read. */
    internal fun poll(): MicReading = MicReading(
        peak = peak.getAndSet(0),
        voicedFrames = voicedFrames.getAndSet(0),
        frames = frames.getAndSet(0)
    )

    /** Closes the recording and returns the finished file (may be null when nothing was recorded). */
    fun stop(): File? {
        val file = output
        stopInternal()
        output = null
        if (file != null && file.isFile) {
            runCatching { patchWavHeader(file) }
        }
        return file
    }

    /** Aborts the recording and deletes the partial file. */
    fun cancel() {
        stopInternal()
        output?.delete()
        output = null
    }

    private fun captureLoop(instance: AudioRecord, file: File, readSize: Int) {
        val buf = ByteArray(readSize)
        val silent = ByteArray(readSize) // written while muted so the WAV length still grows
        val analyzer = SpeechFrameAnalyzer(minPeriodicity = minPeriodicity) { framePeak, voiced ->
            peak.accumulateAndGet(framePeak, ::maxOf)
            frames.incrementAndGet()
            if (voiced) voicedFrames.incrementAndGet()
        }
        FileOutputStream(file, true).use { out ->
            while (capturing && instance.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = instance.read(buf, 0, buf.size)
                if (read > 0) {
                    if (muted) {
                        // Keep the WAV aligned (length grows the same as a real recording would)
                        // and reset the peak so the end-of-speech detector stays in its idle
                        // state, but discard the captured audio so the user can't be heard.
                        out.write(silent, 0, read)
                        resetCounters()
                        analyzer.reset()
                    } else {
                        out.write(buf, 0, read)
                        analyzer.minPeriodicity = minPeriodicity
                        analyzer.accept(buf, read)
                    }
                } else if (read < 0) {
                    break
                }
            }
            out.flush()
        }
    }

    private fun stopInternal() {
        capturing = false
        val thread = captureThread
        captureThread = null
        thread?.join(250)
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        releaseEffects()
        resetCounters()
    }

    private fun resetCounters() {
        peak.set(0)
        voicedFrames.set(0)
        frames.set(0)
    }

    /**
     * v2.1: the phone's own noise suppressor and echo canceller, when it has them. Many phones
     * already run them for VOICE_COMMUNICATION; asking explicitly costs nothing where they do.
     */
    private fun attachEffects(sessionId: Int) {
        releaseEffects()
        if (runCatching { NoiseSuppressor.isAvailable() }.getOrDefault(false)) {
            runCatching { NoiseSuppressor.create(sessionId) }.getOrNull()?.let {
                runCatching { it.enabled = true }
                effects += it
            }
        }
        if (runCatching { AcousticEchoCanceler.isAvailable() }.getOrDefault(false)) {
            runCatching { AcousticEchoCanceler.create(sessionId) }.getOrNull()?.let {
                runCatching { it.enabled = true }
                effects += it
            }
        }
    }

    private fun releaseEffects() {
        effects.forEach { runCatching { it.release() } }
        effects.clear()
    }

    /**
     * Prefer [MediaRecorder.AudioSource.VOICE_COMMUNICATION] so Android's echo canceller
     * can subtract Kryzz's speaker output during barge-in. Fall back to [MIC] if that
     * source cannot be opened.
     */
    private fun openRecorder(bufSize: Int): AudioRecord? {
        val sources = intArrayOf(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC
        )
        for (source in sources) {
            val candidate = runCatching {
                AudioRecord(
                    source,
                    VOICE_CAPTURE_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufSize
                )
            }.getOrNull() ?: continue
            if (candidate.state == AudioRecord.STATE_INITIALIZED) return candidate
            runCatching { candidate.release() }
        }
        return null
    }
}
