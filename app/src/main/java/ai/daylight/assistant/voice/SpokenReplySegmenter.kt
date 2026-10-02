package ai.daylight.assistant.voice

/**
 * Cuts a voice reply into speakable segments while the model is still streaming it, so
 * text-to-speech starts on the first sentence instead of waiting for the whole answer.
 *
 * The first complete sentence is released on its own. Later complete sentences are held
 * until they add up to [batchChars], which keeps TTS requests few and prosody natural;
 * playback of the first sentence covers that wait. [finish] releases whatever is left.
 *
 * Text arrives per agent round. The agent discards a tool round's text, so a new round id
 * restarts from the beginning of the new text, and its first sentence is again released on
 * its own. Anything already released stays spoken and is never repeated.
 */
internal class SpokenReplySegmenter(private val batchChars: Int = DEFAULT_BATCH_CHARS) {
    private var round: String? = null
    private var text = ""
    private var released = 0
    private var releasedInRound = false

    /** Feeds the full visible text of [round] so far and returns the segments ready to speak. */
    fun onText(round: String, value: String): List<String> {
        if (round != this.round) {
            this.round = round
            text = ""
            released = 0
            releasedInRound = false
        }
        if (released > 0 && !value.startsWith(text.substring(0, released))) {
            // Markup scrubbed behind the spoken part shifted the text. Keep the offset so
            // nothing is read twice; at worst a word at the seam is skipped.
            released = released.coerceAtMost(value.length)
        }
        text = value
        val ends = sentenceEnds(text, released)
        if (ends.isEmpty()) return emptyList()
        val out = mutableListOf<String>()
        if (!releasedInRound) release(ends.first())?.let(out::add)
        val last = ends.last()
        if (last > released && text.substring(released, last).trim().length >= batchChars) {
            release(last)?.let(out::add)
        }
        return out
    }

    /** Releases the rest of the current round once the reply is complete. */
    fun finish(): List<String> = listOfNotNull(release(text.length))

    private fun release(end: Int): String? {
        val piece = text.substring(released, end).trim()
        released = end
        if (piece.isEmpty()) return null
        releasedInRound = true
        return piece
    }

    companion object {
        const val DEFAULT_BATCH_CHARS = 160

        private const val TERMINATORS = ".!?…"
        private const val CLOSERS = "\"'”’)]*_"
        private val ABBREVIATIONS = setOf("mr", "mrs", "ms", "dr", "st", "vs", "e.g", "i.e", "approx")

        /**
         * Exclusive end offsets of complete sentences in [text] after [from]. A sentence ends at
         * terminal punctuation (optionally followed by a closing quote, bracket or emphasis mark)
         * that is followed by whitespace, or at a line break. Ends inside an open ``` fence,
         * after a common abbreviation or a single-letter initial, or with no letter or digit
         * since the previous end, are skipped.
         */
        internal fun sentenceEnds(text: String, from: Int): List<Int> {
            val ends = mutableListOf<Int>()
            var segmentStart = from
            var index = from.coerceAtLeast(1)
            while (index < text.length) {
                if (text[index].isWhitespace() && isSentenceEnd(text, index) &&
                    text.substring(segmentStart, index).any { it.isLetterOrDigit() } &&
                    countFences(text, index) % 2 == 0
                ) {
                    ends += index
                    segmentStart = index
                }
                index++
            }
            return ends
        }

        /** Whether the whitespace at [index] closes a sentence. */
        private fun isSentenceEnd(text: String, index: Int): Boolean {
            if (text[index] == '\n') return true
            var end = index - 1
            while (end >= 0 && text[end] in CLOSERS) end--
            if (end < 0 || text[end] !in TERMINATORS) return false
            if (text[end] != '.') return true
            var wordStart = end
            while (wordStart > 0 && (text[wordStart - 1].isLetter() || text[wordStart - 1] == '.')) wordStart--
            val word = text.substring(wordStart, end).lowercase()
            return word.length != 1 && word !in ABBREVIATIONS
        }

        private fun countFences(text: String, end: Int): Int {
            var count = 0
            var at = text.indexOf("```")
            while (at in 0 until end) {
                count++
                at = text.indexOf("```", at + 3)
            }
            return count
        }
    }
}
