package ai.daylight.assistant.domain

import java.util.concurrent.CancellationException

/**
 * Words Talk acts on itself. A message that starts with "make" generates an image, or a
 * video when the rest asks for one. A message that starts with "find" searches photos.
 * A file, a plan, or "find out" stays a normal reply.
 */
internal sealed interface TalkCommand {
    data class Make(val kind: MakeKind, val prompt: String) : TalkCommand
    data class Find(val query: String) : TalkCommand
}

/** Heard during a voice turn: leave the reply and run [text] as a Talk command instead. */
internal class TalkCommandInterrupt(val text: String) : CancellationException("talk command")

internal object TalkCommands {
    private const val POLITE = """(?:please\s+)?(?:can you\s+|could you\s+|would you\s+)?"""
    private val MAKE = Regex("""(?i)^${POLITE}make\b[\s:,.-]+(.+)$""")
    private val FIND = Regex("""(?i)^${POLITE}find\b[\s:,.-]+(.+)$""")
    private val FIND_OUT = Regex("""(?i)^out\b""")
    private val QUESTION = Regex("""(?i)^(?:how|what|why|which|where|when|who)\b""")
    private val MEDIA_LEAD = Regex(
        """(?i)^(?:me\s+)?(?:a|an|the|some)?\s*(?:short\s+|long\s+|quick\s+)?""" +
            """(?:videos?|clips?|movies?|films?|animations?|images?|pictures?|photos?|photographs?|drawings?|illustrations?|paintings?|logos?|portraits?|sketches?)""" +
            """(?:\s+of)?\s*"""
    )
    private val PHOTO_LEAD = Regex(
        """(?i)^(?:me\s+)?(?:a|an|the|some)?\s*(?:photos?|pictures?|images?|photographs?)(?:\s+of)?\s*"""
    )
    private val TASK = Regex(
        """(?i)^(?:me\s+)?(?:a|an|the|some)?\s*(?:list|plan|summary|email|letter|recipe|essay|story|poem|outline|notes?|schedule|reminder|budget|itinerary|message|reply|paragraph)\b"""
    )

    fun parse(text: String): TalkCommand? {
        val clean = text.trim()
        if (clean.isEmpty()) return null
        MAKE.matchEntire(clean)?.let { match ->
            val rest = match.groupValues[1].trim()
            if (rest.length < 2) return null
            if (userWantsArtifactCreated(clean) || userWantsSkill(clean) || userWantsCodeProject(clean)) return null
            val visual = userWantsImage(rest) || userWantsVideo(rest)
            if (!visual && (QUESTION.containsMatchIn(rest) || TASK.containsMatchIn(rest))) return null
            val kind = if (userWantsVideo(rest)) MakeKind.VIDEO else MakeKind.IMAGE
            val prompt = MEDIA_LEAD.replace(rest, "").trim()
            if (prompt.length < 2) return null
            return TalkCommand.Make(kind, prompt.take(2_000))
        }
        FIND.matchEntire(clean)?.let { match ->
            val rest = match.groupValues[1].trim()
            if (rest.length < 2 || FIND_OUT.containsMatchIn(rest) || QUESTION.containsMatchIn(rest)) return null
            val query = PHOTO_LEAD.replace(rest, "").trim()
            if (query.length < 2) return null
            return TalkCommand.Find(query.take(FindSession.MAX_QUERY))
        }
        return null
    }
}
