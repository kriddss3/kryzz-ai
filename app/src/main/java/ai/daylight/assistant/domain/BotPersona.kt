package ai.daylight.assistant.domain

/**
 * C9 Crew: how a bot's persona joins the system prompt in the bot's own conversation.
 * The user's own persona preset stays first, so its tone and safety lines still apply; the
 * bot's role narrows it. The text is stable across turns, so prompt caching still works.
 */
object BotPersona {
    const val NAME_MAX = 24
    const val PERSONA_MAX = 600

    /** One line, no doubled spaces, at most [NAME_MAX] characters. */
    fun cleanName(raw: String): String = raw.replace(Regex("\\s+"), " ").trim().take(NAME_MAX)

    fun cleanPersona(raw: String): String = raw.trim().take(PERSONA_MAX)

    /** [base] followed by the bot's role. A blank name leaves [base] untouched. */
    fun systemPrompt(base: String, name: String, persona: String): String {
        val cleanName = cleanName(name)
        if (cleanName.isEmpty()) return base
        val brief = cleanPersona(persona)
        return buildString {
            append(base.trimEnd())
            append("\n\n")
            append("In this conversation you are \"").append(cleanName).append("\", one of the user's personal bots in Kryzz Crew. ")
            if (brief.isNotEmpty()) append("The user describes your job like this: ").append(brief).append(' ')
            append(
                "Keep to that role in every reply. Some user messages here were sent by one of your " +
                    "routines on a schedule rather than typed live: do the job each one describes and " +
                    "report back briefly in plain words."
            )
        }
    }
}
