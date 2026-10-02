package ai.daylight.assistant.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
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
    private val bytesCaptured = AtomicLong(0)

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
        peak.set(0)
        bytesCaptured.set(0)
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
        // Read in short slices rather than whole hardware buffers: the level poll sees fresher
        // audio, early snapshots are more current, and stop() never waits on a long read.
        val readSize = minOf(minBuf, READ_CHUNK_BYTES)
        captureThread = thread(name = "kryzz-mic", isDaemon = true) {
            captureLoop(instance, file, readSize)
        }
        Unit
    }.onFailure {
        stopInternal()
        output?.delete()
        output = null
    }

    /** Peak amplitude since the last poll, 0..32767. Resets on read, like MediaRecorder.maxAmplitude. */
    fun level(): Int = peak.getAndSet(0)

    /** PCM bytes written to the current recording so far (after the WAV header). */
    fun capturedBytes(): Long = bytesCaptured.get()

    /** The WAV being recorded right now, still growing; null when the microphone is closed. */
    fun currentFile(): File? = output?.takeIf { capturing }

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
        output?.delete()
        output = null
        stopInternal()
    }

    private fun captureLoop(instance: AudioRecord, file: File, readSize: Int) {
        val buf = ByteArray(readSize)
        val silent = ByteArray(readSize) // written while muted so the WAV length still grows
        FileOutputStream(file, true).use { out ->
            while (capturing && instance.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = instance.read(buf, 0, buf.size)
                if (read > 0) {
                    if (muted) {
                        // Keep the WAV aligned (length grows the same as a real recording would)
                        // and reset the peak so the end-of-speech detector stays in its idle
                        // state, but discard the captured audio so the user can't be heard.
                        out.write(silent, 0, read)
                        peak.set(0)
                    } else {
                        out.write(buf, 0, read)
                        val samplePeak = pcmPeakAmplitude(buf, read)
                        peak.accumulateAndGet(samplePeak, ::maxOf)
                    }
                    bytesCaptured.addAndGet(read.toLong())
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
        peak.set(0)
    }

    private companion object {
        /** 20 ms of 16 kHz PCM16 mono. */
        const val READ_CHUNK_BYTES = VOICE_CAPTURE_SAMPLE_RATE / 50 * 2
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
