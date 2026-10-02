package ai.daylight.assistant.voice

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/** Capture rate for the voice-chat microphone. Whisper transcribes 16 kHz WAV cleanly. */
internal const val VOICE_CAPTURE_SAMPLE_RATE = 16_000

internal const val WAV_HEADER_BYTES = 44

/** PCM16 mono bytes per millisecond at [VOICE_CAPTURE_SAMPLE_RATE]. */
internal const val VOICE_BYTES_PER_MS = VOICE_CAPTURE_SAMPLE_RATE * 2 / 1_000

/** MIME type for an OpenRouter /audio/transcriptions upload, inferred from the file extension. */
internal fun audioMimeType(file: File): String = when (file.extension.lowercase()) {
    "wav" -> "audio/wav"
    "mp3" -> "audio/mpeg"
    "m4a", "mp4", "aac" -> "audio/mp4"
    "webm" -> "audio/webm"
    "ogg" -> "audio/ogg"
    else -> "audio/wav"
}

/**
 * Peak absolute sample in a little-endian PCM16 buffer, on the same 0..32767 scale
 * [AdaptiveEndOfSpeechDetector] already uses for MediaRecorder.maxAmplitude.
 */
internal fun pcmPeakAmplitude(data: ByteArray, length: Int = data.size): Int {
    val limit = length.coerceIn(0, data.size)
    if (limit < 2) return 0
    var peak = 0
    var i = 0
    while (i + 1 < limit) {
        val sample = (data[i].toInt() and 0xFF) or (data[i + 1].toInt() shl 8)
        val signed = sample.toShort().toInt()
        peak = maxOf(peak, abs(signed))
        i += 2
    }
    return peak.coerceAtMost(32_767)
}

/** 44-byte PCM16 mono WAV header. [dataBytes] is the PCM payload size, not the file size. */
internal fun wavHeader(dataBytes: Int, sampleRate: Int = VOICE_CAPTURE_SAMPLE_RATE): ByteArray {
    val safeData = dataBytes.coerceAtLeast(0)
    val buffer = ByteBuffer.allocate(WAV_HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
    buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
    buffer.putInt(36 + safeData)
    buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
    buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
    buffer.putInt(16)
    buffer.putShort(1) // PCM
    buffer.putShort(1) // mono
    buffer.putInt(sampleRate)
    buffer.putInt(sampleRate * 2) // byte rate
    buffer.putShort(2) // block align
    buffer.putShort(16) // bits
    buffer.put("data".toByteArray(Charsets.US_ASCII))
    buffer.putInt(safeData)
    return buffer.array()
}

/** Rewrites the header of an already-written WAV so the RIFF/data sizes match the file. */
internal fun patchWavHeader(file: File, sampleRate: Int = VOICE_CAPTURE_SAMPLE_RATE) {
    val dataBytes = (file.length() - WAV_HEADER_BYTES).coerceAtLeast(0L).toInt()
    RandomAccessFile(file, "rw").use { raf ->
        raf.seek(0)
        raf.write(wavHeader(dataBytes, sampleRate))
    }
}

/**
 * Copies the audio captured so far in a WAV that is still being recorded into [dest], with
 * a header that matches, skipping the first [skipPcmBytes] of PCM. Used to transcribe a turn
 * early, while the recorder keeps writing to [source].
 */
internal fun snapshotWav(
    source: File,
    dest: File,
    skipPcmBytes: Long = 0L,
    sampleRate: Int = VOICE_CAPTURE_SAMPLE_RATE
): File {
    writePcmTail(source.readBytes(), dest, skipPcmBytes, sampleRate)
    return dest
}

/**
 * Drops the first [pcmBytes] of audio from a finished 16-bit mono WAV in place. After a
 * barge-in this removes the reply-time audio recorded before the user started talking.
 */
internal fun dropWavPrefix(file: File, pcmBytes: Long, sampleRate: Int = VOICE_CAPTURE_SAMPLE_RATE): File {
    if (pcmBytes <= 0L || !file.isFile) return file
    writePcmTail(file.readBytes(), file, pcmBytes, sampleRate)
    return file
}

/** Writes the PCM of [wav] after [skipPcmBytes] (frame-aligned) to [dest] as a complete WAV. */
private fun writePcmTail(wav: ByteArray, dest: File, skipPcmBytes: Long, sampleRate: Int) {
    val end = WAV_HEADER_BYTES + ((wav.size - WAV_HEADER_BYTES).coerceAtLeast(0) and 1.inv())
    val start = (WAV_HEADER_BYTES + (skipPcmBytes.coerceAtLeast(0L) and 1L.inv()))
        .coerceAtMost(end.toLong()).toInt()
    val pcmLength = end - start
    dest.outputStream().use { out ->
        out.write(wavHeader(pcmLength, sampleRate))
        if (pcmLength > 0) out.write(wav, start, pcmLength)
    }
}

/**
 * Drops leading and trailing near-silence from a 16-bit mono WAV so Whisper
 * does not spend time on the VAD calibration / end-of-speech pad.
 * A short [padMs] is kept so consonants at the edges are not clipped.
 */
internal fun trimWavSilence(
    file: File,
    threshold: Int = 400,
    padMs: Int = 80,
    sampleRate: Int = VOICE_CAPTURE_SAMPLE_RATE
): File {
    val bytes = file.readBytes()
    if (bytes.size <= WAV_HEADER_BYTES + 4) return file
    val pcm = bytes.copyOfRange(WAV_HEADER_BYTES, bytes.size)
    var first = 0
    while (first + 1 < pcm.size) {
        if (kotlin.math.abs(pcmSample(pcm, first)) >= threshold) break
        first += 2
    }
    var last = (pcm.size - 2) and 1.inv()
    while (last >= 0) {
        if (kotlin.math.abs(pcmSample(pcm, last)) >= threshold) break
        last -= 2
    }
    if (first > last) return file
    val padBytes = ((sampleRate * padMs) / 1_000) * 2
    val start = (first - padBytes).coerceAtLeast(0) and 1.inv()
    val endExclusive = (last + 2 + padBytes).coerceAtMost(pcm.size) and 1.inv()
    if (endExclusive - start >= pcm.size - 4) return file
    val sliced = pcm.copyOfRange(start, endExclusive)
    file.writeBytes(wavHeader(sliced.size, sampleRate) + sliced)
    return file
}

private fun pcmSample(data: ByteArray, offset: Int): Int {
    val sample = (data[offset].toInt() and 0xFF) or (data[offset + 1].toInt() shl 8)
    return sample.toShort().toInt()
}

/**
 * Splits a 16-bit mono WAV into fixed-length chunks of [chunkSeconds] seconds each and
 * writes them as standalone WAV files in the same directory. The last chunk may be
 * shorter. Used to feed audio to STT providers (Nemotron) in parallel chunks so the
 * final transcription lands sooner than serialising the whole recording.
 *
 * Returns an empty list when the input has no PCM payload.
 */
internal fun splitWavChunks(file: File, chunkSeconds: Float = 4f, sampleRate: Int = VOICE_CAPTURE_SAMPLE_RATE): List<File> {
    if (!file.isFile) return emptyList()
    val bytes = file.readBytes()
    if (bytes.size <= WAV_HEADER_BYTES) return emptyList()
    val pcm = bytes.copyOfRange(WAV_HEADER_BYTES, bytes.size)
    if (pcm.isEmpty()) return emptyList()
    val samplesPerChunk = (sampleRate * chunkSeconds).toInt().coerceAtLeast(sampleRate / 2)
    val bytesPerChunk = samplesPerChunk * 2 // PCM16 = 2 bytes per sample
    val parent = file.parentFile ?: return emptyList()
    val baseName = file.nameWithoutExtension
    val out = mutableListOf<File>()
    var offset = 0
    var index = 0
    while (offset < pcm.size) {
        val end = (offset + bytesPerChunk).coerceAtMost(pcm.size)
        val slice = pcm.copyOfRange(offset, end)
        val chunk = File(parent, "$baseName-chunk-${index++}.wav")
        chunk.writeBytes(wavHeader(slice.size, sampleRate) + slice)
        out += chunk
        offset = end
    }
    return out
}

/** Convenience for deleting chunk temp files without spelling out the loop at call sites. */
internal fun deleteWavChunks(files: List<File>) {
    for (chunk in files) runCatching { chunk.delete() }
}
