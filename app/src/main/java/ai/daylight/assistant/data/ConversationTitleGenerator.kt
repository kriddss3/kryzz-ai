package ai.daylight.assistant.data

import ai.daylight.assistant.data.remote.ApiMessage
import ai.daylight.assistant.data.remote.ChatRequest
import ai.daylight.assistant.data.remote.OpenRouterClient
import ai.daylight.assistant.data.remote.ProviderPreferences
import ai.daylight.assistant.data.remote.StreamEvent
import ai.daylight.assistant.security.SecureCredentialStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private const val NEW_CONVERSATION_TITLE = "New conversation"

/** Tiny, price-routed model for chat titles; the previous id did not exist on OpenRouter. */
private const val TITLE_MODEL = "meta-llama/llama-3.2-1b-instruct"

/**
 * Gives a first-message conversation an immediate local title, then quietly improves it
 * with a tiny, price-routed OpenRouter request. The application scope keeps this work
 * independent of response streaming, so a title can never delay the first answer.
 */
class ConversationTitleGenerator(
    private val conversations: ConversationRepository,
    private val openRouter: OpenRouterClient,
    private val credentials: SecureCredentialStore,
    private val scope: CoroutineScope
) {
    fun request(conversationId: String, firstUserMessage: String) {
        scope.launch { generateAndApply(conversationId, firstUserMessage) }
    }

    internal suspend fun generateAndApply(conversationId: String, firstUserMessage: String) {
        val fallback = localConversationTitle(firstUserMessage)
        if (!conversations.renameIfCurrent(conversationId, NEW_CONVERSATION_TITLE, fallback)) return

        val key = credentials.openRouterKey()?.takeIf(String::isNotBlank) ?: return
        val generated = runCatching { requestRemoteTitle(key, firstUserMessage) }.getOrNull() ?: return
        conversations.renameIfCurrent(conversationId, fallback, generated)
    }

    private suspend fun requestRemoteTitle(key: String, firstUserMessage: String): String {
        val response = StringBuilder()
        val request = ChatRequest(
            model = TITLE_MODEL,
            messages = listOf(
                ApiMessage(
                    "system",
                    "Write a concise chat title of 3 to 7 words. Return only the title, with no quotes, label, markdown, or ending punctuation."
                ),
                ApiMessage("user", firstUserMessage.take(1_500))
            ),
            stream = false,
            provider = ProviderPreferences(sort = "price"),
            maxTokens = 24
        )
        openRouter.stream(key, request).collect { event ->
            when (event) {
                is StreamEvent.Delta -> response.append(event.text)
                is StreamEvent.Failure -> throw event.error
                is StreamEvent.Done, is StreamEvent.UsageUpdate -> Unit
            }
        }
        return sanitizeGeneratedTitle(response.toString()) ?: error("The title response was empty.")
    }
}

internal fun localConversationTitle(message: String): String {
    val clean = message
        .replace(Regex("https?://\\S+", RegexOption.IGNORE_CASE), "link")
        .replace(Regex("\\s+"), " ")
        .trim()
        .replace(Regex("^(please\\s+)?(?:can|could|would|will)\\s+you\\s+", RegexOption.IGNORE_CASE), "")
        .replace(Regex("^(please\\s+)?help\\s+me(?:\\s+to)?\\s+", RegexOption.IGNORE_CASE), "")
        .replace(Regex("^please\\s+", RegexOption.IGNORE_CASE), "")
    val words = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}'’_-]*")
        .findAll(clean)
        .map { it.value }
        .take(7)
        .toList()
    val title = words.joinToString(" ").take(56).trim()
    return title.ifBlank { NEW_CONVERSATION_TITLE }.replaceFirstChar { first ->
        if (first.isLowerCase()) first.titlecase() else first.toString()
    }
}

internal fun sanitizeGeneratedTitle(value: String): String? {
    val clean = value
        .lineSequence()
        .firstOrNull(String::isNotBlank)
        .orEmpty()
        .trim()
        .trim(' ', '"', '\'', '`', '*', '#')
        .replace(Regex("^title\\s*:\\s*", RegexOption.IGNORE_CASE), "")
        .trimEnd('.', '!', '?', ':', ';', '-', '—')
        .replace(Regex("\\s+"), " ")
    if (clean.length !in 2..80) return null
    return clean.split(' ').filter(String::isNotBlank).take(7).joinToString(" ").take(64).trim().ifBlank { null }
}
