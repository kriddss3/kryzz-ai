package ai.daylight.assistant.ui

import ai.daylight.assistant.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * v2.1: one dictation (mic → transcriber → text) for the chat reply line and Home's composer,
 * started by the mic button or by holding the text field. The mic is the app's one
 * [AppContainer.voiceRecorder], so only one screen dictates at a time.
 */
class DictationSession(private val container: AppContainer, private val scope: CoroutineScope) {
    /** The mic is open. */
    val active = MutableStateFlow(false)

    /** 0..1 loudness for the mic ring, refreshed every 40ms while [active]. */
    val level = MutableStateFlow(0f)

    /** The recording is with the transcriber. */
    val transcribing = MutableStateFlow(false)

    private var meter: Job? = null

    fun begin(): Result<Unit> {
        if (active.value || transcribing.value) return Result.success(Unit)
        level.value = 0f
        container.warmOpenRouter()
        return container.voiceRecorder.start().onSuccess {
            active.value = true
            meter = scope.launch {
                while (active.value) {
                    level.value = container.voiceRecorder.level().toFloat() / 32_767f
                    delay(40)
                }
            }
        }
    }

    /** Drops the recording (a hold slid away, or the screen went). */
    fun cancel() {
        if (!active.value) return
        stopMeter()
        container.voiceRecorder.cancel()
    }

    /** Stops the mic and sends the clip to the transcriber; [onText] gets non-blank words. */
    fun finish(onText: (String) -> Unit, onError: (String) -> Unit) {
        if (!active.value) return
        stopMeter()
        val file = container.voiceRecorder.stop()
        if (file == null || file.length() < MIN_CLIP_BYTES) {
            file?.delete()
            onError(TOO_SHORT)
            return
        }
        transcribing.value = true
        scope.launch {
            try {
                val settings = container.preferences.state.first()
                val text = container.transcribeVoice(file, settings.voiceSttModel).trim()
                if (text.isNotBlank()) onText(text) else onError(NOTHING_HEARD)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                onError(t.message ?: "Dictation failed.")
            } finally {
                file.delete()
                transcribing.value = false
            }
        }
    }

    private fun stopMeter() {
        meter?.cancel()
        meter = null
        active.value = false
        level.value = 0f
    }

    companion object {
        /** About a tenth of a second of 16 kHz audio; anything shorter is a slip of the finger. */
        private const val MIN_CLIP_BYTES = 4_000L
        const val TOO_SHORT = "Hold a little longer and speak, then let go."
        const val NOTHING_HEARD = "Could not hear anything clear. Try dictation again."

        /** Puts dictated [words] after what is already typed. */
        fun append(existing: String, words: String): String = (existing.trimEnd() + " " + words.trim()).trimStart()
    }
}
