package ai.daylight.assistant.domain

/**
 * Keyword gates for the AUTO agent's media generation tools. The AUTO workspace can call
 * `generate_image` / `generate_video` / `generate_audio` so the user does not have to
 * pre-pick the Image / Video / Music capability, but the tools must only be *offered* when
 * the user actually asked for that kind of media. Without this gate the model would call
 * `generate_video` on a plain text question and hang for minutes on the provider poll.
 *
 * Vocabulary is deliberately conservative (high precision over recall): ambiguous words
 * that are common in non-media contexts ("draw" as in "draw a conclusion", "beat" as in
 * "beat the eggs", "art of" as in "the art of war", "motion" as in "motion to dismiss") are
 * excluded. Each single-word entry matches on word boundaries so "image" never hits
 * "imagine". Matching is case-insensitive.
 */
private val imageKeywords = listOf(
    "image", "images", "picture", "pictures", "photo", "photos", "photograph", "photographs",
    "drawing", "drawings", "illustration", "illustrations", "illustrate",
    "painting", "paintings", "logo", "logos", "artwork", "portrait", "portraits",
    "sketch", "sketches"
)
private val videoKeywords = listOf(
    "video", "videos", "clip", "clips", "movie", "movies", "film", "films", "animation",
    "animations", "animate", "animated", "trailer", "trailers", "cinemagraph", "gif",
    "montage", "footage"
)
private val audioKeywords = listOf(
    "music", "song", "songs", "track", "tracks", "jingle", "jingles", "melody", "melodies",
    "tune", "tunes", "instrumental", "instrumentals", "soundtrack", "soundtracks",
    "audio clip", "sound effect", "sfx", "lo-fi", "lofi", "anthem", "ringtone", "ringtones",
    "dj beat"
)

private fun matchesAnyWord(text: String, keywords: List<String>): Boolean {
    if (text.isBlank()) return false
    val lower = text.lowercase()
    return keywords.any { keyword ->
        val needle = keyword.lowercase()
        if (needle.contains(' ')) {
            // Multi-word phrases: simple substring match is fine and intentional.
            lower.contains(needle)
        } else {
            // Word-boundary match so "image" does not hit "imagine".
            Regex("\\b" + Regex.escape(needle) + "\\b").containsMatchIn(lower)
        }
    }
}

/** True when the user's message asks for a still image / picture / illustration. */
internal fun userWantsImage(text: String): Boolean = matchesAnyWord(text, imageKeywords)

/** True when the user's message asks for a video / clip / animation. */
internal fun userWantsVideo(text: String): Boolean = matchesAnyWord(text, videoKeywords)

/** True when the user's message asks for music / a song / an audio clip. */
internal fun userWantsAudio(text: String): Boolean = matchesAnyWord(text, audioKeywords)
