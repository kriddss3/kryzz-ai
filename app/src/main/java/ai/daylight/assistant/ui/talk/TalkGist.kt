package ai.daylight.assistant.ui.talk

import ai.daylight.assistant.ui.shell.InkLine

/**
 * The departure-board line for a finished exchange: a gist of the question, at most 16
 * characters, A-Z, 0-9 and spaces only, so it always fits the 5x7 dot font.
 *
 * It is built locally from the question's own words (no extra model call, no extra cost):
 * filler words are dropped, the remaining keywords are kept in order, and whole words are
 * added until the next one would not fit.
 */
object TalkGist {
    const val MAX_CHARS = 16
    const val FALLBACK = "QUESTION"

    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

    /** Uppercase, accent-free forms, English plus common Latvian filler words. */
    private val STOP_WORDS = setOf(
        // English
        "A", "AN", "THE", "AND", "OR", "BUT", "IF", "THEN", "SO", "OF", "TO", "IN", "ON", "AT", "BY",
        "FOR", "FROM", "WITH", "ABOUT", "AS", "INTO", "OVER", "IS", "ARE", "WAS", "WERE", "BE", "BEEN",
        "BEING", "AM", "DO", "DOES", "DID", "HAVE", "HAS", "HAD", "CAN", "COULD", "WOULD", "SHOULD",
        "WILL", "SHALL", "MAY", "MIGHT", "MUST", "I", "ME", "MY", "WE", "US", "OUR", "YOU", "YOUR",
        "HE", "SHE", "IT", "ITS", "THEY", "THEM", "THEIR", "THIS", "THAT", "THESE", "THOSE", "THERE",
        "HERE", "WHAT", "WHICH", "WHO", "WHOM", "WHOSE", "WHY", "HOW", "WHEN", "WHERE", "ANY", "SOME",
        "ALL", "PLEASE", "PLS", "TELL", "EXPLAIN", "GIVE", "SHOW", "HELP", "NEED", "WANT", "LIKE",
        "JUST", "ALSO", "VERY", "REALLY", "GET", "KNOW", "THINK", "S", "T", "IM", "IVE", "DONT",
        "CANT", "WHATS", "HOWS", "HEY", "HI", "HELLO", "OK", "OKAY", "THANKS", "THANK", "ABOUT",
        // Latvian (diacritics stripped)
        "IR", "UN", "VAI", "KAS", "KA", "ES", "TU", "MAN", "TEV", "MANS", "MANA", "PAR", "UZ", "NO",
        "AR", "PIE", "LAI", "BET", "ARI", "KADS", "KADA", "KADI", "KUR", "KAD", "KAPEC", "CIK",
        "LUDZU", "VARI", "VARETU", "PASTASTI", "PASKAIDRO", "SIS", "SI", "TAS", "TA", "TO", "KO",
        "JA", "NE", "SVEIKI", "LABDIEN", "PALDIES",
    )

    /** The question's words as the dot font can draw them. */
    internal fun words(text: String): List<String> =
        InkLine.sanitize(NON_WORD.replace(text, " "), maxChars = 2_000)
            .split(' ')
            .map { word -> word.filter { it in 'A'..'Z' || it in '0'..'9' } }
            .filter { it.isNotEmpty() }

    fun of(question: String): String {
        val all = words(question)
        if (all.isEmpty()) return FALLBACK
        val keywords = all.filterNot { it in STOP_WORDS }.distinct().ifEmpty { all.distinct() }
        var gist = ""
        for (word in keywords) {
            val next = if (gist.isEmpty()) word else "$gist $word"
            when {
                next.length <= MAX_CHARS -> gist = next
                gist.isEmpty() -> return word.take(MAX_CHARS)
                else -> break
            }
        }
        return gist
    }
}
