package ai.daylight.assistant.voice

/**
 * v2.1 direct hearing: the voice recording goes straight to a reply model that takes audio
 * (Gemini 2.5 Flash Lite is the default voice brain), instead of a transcriber first and the
 * model second. One request instead of two, and the model hears tone and hesitation too.
 *
 * The model writes what it heard first, inside `<heard>…</heard>`, then its spoken reply.
 * The app keeps the heard words as the user's message (history, titles, memory) and speaks
 * only the reply. An empty `<heard></heard>` means there was no speech for Kryzz in the
 * clip (wind, noise, someone else talking), and the session just keeps listening.
 */
object VoiceHearing {
    /** What the user's row says until the heard words arrive (and if the model never sends them). */
    const val PLACEHOLDER = "Voice message"

    const val OPEN = "<heard>"
    const val CLOSE = "</heard>"

    /** Added to the voice system prompt when the turn carries audio. */
    const val INSTRUCTION =
        "The user's latest turn is a voice recording, attached as audio. Start your reply with exactly what they said, " +
            "word for word in their language, inside <heard></heard> on one line, for example: <heard>What's the weather tomorrow?</heard> " +
            "Then answer them out loud as usual. If the recording holds no clear speech meant for you (silence, wind, traffic, " +
            "rustling, music, or people talking in the background), write <heard></heard> and nothing else."

    /** The text the model sees in place of the audio's words. */
    const val AUDIO_PROMPT = "(Voice message attached. Listen to it.)"

    /**
     * True for reply models that accept audio input on OpenRouter. Gemini's chat models do;
     * its image and speech-output variants do not.
     */
    fun hearsAudio(modelId: String): Boolean {
        val id = modelId.trim().lowercase()
        if (!id.startsWith("google/gemini")) return false
        return listOf("-image", "-tts", "embedding").none { it in id }
    }

    /** The heard words and the reply in a streamed (possibly unfinished) answer. */
    data class Split(
        /** What the user said; null while it is still streaming or when the model sent no tag. */
        val heard: String?,
        /** The spoken reply after the tag (or all of the text when there is no tag). */
        val reply: String,
        /** True once the tag has closed, or once it is clear there will be none. */
        val settled: Boolean,
    ) {
        /** The model heard nothing meant for Kryzz. */
        val noSpeech: Boolean get() = settled && heard != null && heard.isBlank()
    }

    fun split(text: String): Split {
        val lead = text.trimStart()
        if (lead.isEmpty()) return Split(heard = null, reply = "", settled = false)
        if (lead.startsWith("<heard/>") || lead.startsWith("<heard />")) {
            return Split(heard = "", reply = lead.substringAfter('>').trimStart(), settled = true)
        }
        if (!lead.startsWith(OPEN)) {
            // "<he" may still become the tag: hold it back until it can't.
            if (OPEN.startsWith(lead)) return Split(heard = null, reply = "", settled = false)
            return Split(heard = null, reply = stripStrayTags(text), settled = true)
        }
        val body = lead.substring(OPEN.length)
        val close = body.indexOf(CLOSE)
        if (close < 0) return Split(heard = null, reply = "", settled = false)
        val heard = body.substring(0, close).replace(Regex("\\s+"), " ").trim()
        val reply = stripStrayTags(body.substring(close + CLOSE.length)).trimStart()
        return Split(heard = heard, reply = reply, settled = true)
    }

    /** A model that repeats the tag later in the reply must not have it read aloud. */
    private fun stripStrayTags(text: String): String =
        text.replace(Regex("<heard>[\\s\\S]*?</heard>"), "").replace(OPEN, "").replace(CLOSE, "")
}
