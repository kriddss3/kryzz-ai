package ai.daylight.assistant.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

/**
 * Puts the phone in a voice-call audio path for the whole voice session.
 *
 * Echo cancellation only subtracts audio played on the voice-communication stream.
 * Media playback is invisible to it, so the microphone hears Kryzz and the reply
 * interrupts itself. The route is restored when the session ends.
 */
class VoiceRoute(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var active = false
    private var previousMode = AudioManager.MODE_NORMAL
    private var previousSpeaker = false
    private var previousCallVolume = -1
    private var focus: AudioFocusRequest? = null

    fun enter() {
        if (active) return
        if (audioManager.mode == AudioManager.MODE_IN_CALL) return
        previousMode = audioManager.mode
        @Suppress("DEPRECATION")
        previousSpeaker = audioManager.isSpeakerphoneOn
        previousCallVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
        active = true
        runCatching { audioManager.mode = AudioManager.MODE_IN_COMMUNICATION }
        routeToSpeaker()
        matchCallVolumeToMedia()
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attributes)
            .build()
        focus = request
        runCatching { audioManager.requestAudioFocus(request) }
    }

    fun leave() {
        if (!active) return
        active = false
        focus?.let { request -> runCatching { audioManager.abandonAudioFocusRequest(request) } }
        focus = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { audioManager.clearCommunicationDevice() }
        }
        @Suppress("DEPRECATION")
        runCatching { audioManager.isSpeakerphoneOn = previousSpeaker }
        if (previousCallVolume >= 0) {
            runCatching {
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, previousCallVolume, 0)
            }
            previousCallVolume = -1
        }
        runCatching { audioManager.mode = previousMode }
    }

    /**
     * Voice-communication playback follows the call volume, which is often much quieter
     * than media. Match it to the current media volume for the session, then put it back.
     */
    private fun matchCallVolumeToMedia() {
        val musicMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val callMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL).coerceAtLeast(1)
        val music = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val mapped = (music.toFloat() / musicMax * callMax).toInt().coerceIn(1, callMax)
        runCatching { audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, mapped, 0) }
    }

    private fun routeToSpeaker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val current = audioManager.communicationDevice
            if (current != null && current.type != AudioDeviceInfo.TYPE_BUILTIN_EARPIECE &&
                current.type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            ) {
                return
            }
            val speaker = audioManager.availableCommunicationDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            }
            if (speaker != null) {
                runCatching { audioManager.setCommunicationDevice(speaker) }
                return
            }
        }
        @Suppress("DEPRECATION")
        val headset = audioManager.isBluetoothScoOn || audioManager.isWiredHeadsetOn
        if (!headset) {
            @Suppress("DEPRECATION")
            runCatching { audioManager.isSpeakerphoneOn = true }
        }
    }
}
