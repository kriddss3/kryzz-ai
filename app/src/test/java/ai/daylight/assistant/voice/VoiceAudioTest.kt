package ai.daylight.assistant.voice

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class VoiceAudioTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun wavHeaderIsFortyFourBytesLittleEndianPcm16Mono() {
        val header = wavHeader(3_200, sampleRate = 16_000)
        assertThat(header).hasLength(WAV_HEADER_BYTES)
        assertThat(header.copyOfRange(0, 4).toString(Charsets.US_ASCII)).isEqualTo("RIFF")
        assertThat(header.copyOfRange(8, 12).toString(Charsets.US_ASCII)).isEqualTo("WAVE")
        assertThat(header.copyOfRange(12, 16).toString(Charsets.US_ASCII)).isEqualTo("fmt ")
        assertThat(leInt(header, 4)).isEqualTo(36 + 3_200)
        assertThat(leShort(header, 20)).isEqualTo(1)
        assertThat(leShort(header, 22)).isEqualTo(1)
        assertThat(leInt(header, 24)).isEqualTo(16_000)
        assertThat(leInt(header, 28)).isEqualTo(32_000)
        assertThat(leShort(header, 32)).isEqualTo(2)
        assertThat(leShort(header, 34)).isEqualTo(16)
        assertThat(header.copyOfRange(36, 40).toString(Charsets.US_ASCII)).isEqualTo("data")
        assertThat(leInt(header, 40)).isEqualTo(3_200)
    }

    @Test fun patchWavHeaderRewritesSizesToMatchTheFile() {
        val file = tmp.newFile("clip.wav")
        file.writeBytes(wavHeader(0) + ByteArray(640))
        patchWavHeader(file)
        val header = file.readBytes().copyOf(WAV_HEADER_BYTES)
        assertThat(leInt(header, 4)).isEqualTo(36 + 640)
        assertThat(leInt(header, 40)).isEqualTo(640)
    }

    @Test fun pcmPeakUsesTheLargestAbsoluteSampleOnTheMediaRecorderScale() {
        val silent = ByteArray(8)
        assertThat(pcmPeakAmplitude(silent)).isEqualTo(0)

        val peak = ByteArray(4)
        // little-endian PCM16: 3000 and -12000
        peak[0] = (3000 and 0xFF).toByte()
        peak[1] = (3000 shr 8).toByte()
        peak[2] = ((-12000) and 0xFF).toByte()
        peak[3] = ((-12000) shr 8).toByte()
        assertThat(pcmPeakAmplitude(peak)).isEqualTo(12_000)
    }

    @Test fun audioMimeTypeFollowsTheFileExtension() {
        assertThat(audioMimeType(File("clip.wav"))).isEqualTo("audio/wav")
        assertThat(audioMimeType(File("clip.m4a"))).isEqualTo("audio/mp4")
        assertThat(audioMimeType(File("clip.mp3"))).isEqualTo("audio/mpeg")
        assertThat(audioMimeType(File("clip.bin"))).isEqualTo("audio/wav")
    }

    @Test fun trimWavSilenceDropsLeadingAndTrailingPadding() {
        val silent = ByteArray(3_200) // 100 ms of zeros at 16 kHz
        val speech = ByteArray(640)
        // a 10 ms burst of ±8000
        var i = 0
        while (i < speech.size) {
            val sample = if ((i / 2) % 2 == 0) 8_000 else -8_000
            speech[i] = (sample and 0xFF).toByte()
            speech[i + 1] = (sample shr 8).toByte()
            i += 2
        }
        val file = tmp.newFile("padded.wav")
        val pcm = silent + speech + silent
        file.writeBytes(wavHeader(pcm.size) + pcm)
        trimWavSilence(file, threshold = 400, padMs = 20, sampleRate = 16_000)
        val out = file.readBytes()
        val dataBytes = leInt(out, 40)
        assertThat(dataBytes).isLessThan(pcm.size)
        // 20 ms pad each side + 10 ms speech = 50 ms = 1_600 bytes
        assertThat(dataBytes).isAtMost(2_400)
        assertThat(dataBytes).isAtLeast(640)
        assertThat(pcmPeakAmplitude(out.copyOfRange(WAV_HEADER_BYTES, out.size))).isEqualTo(8_000)
    }

    @Test fun trimWavSilenceLeavesAQuietClipAlone() {
        val file = tmp.newFile("quiet.wav")
        val pcm = ByteArray(640)
        file.writeBytes(wavHeader(pcm.size) + pcm)
        val before = file.readBytes()
        trimWavSilence(file, threshold = 400, padMs = 20)
        assertThat(file.readBytes()).isEqualTo(before)
    }

    private fun leInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun leShort(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
}
