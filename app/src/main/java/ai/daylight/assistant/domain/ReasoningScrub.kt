package ai.daylight.assistant.domain

/**
 * Strips inline chain-of-thought emitted by models that publish their reasoning inside the
 * content stream as `<think>…</think>` blocks (DeepSeek, Qwen, Granite, and a few others).
 * OpenRouter's `reasoning.exclude` flag keeps reasoning out of the stream when the provider
 * honours it, but some models ignore that and inline the tags directly in the content delta.
 *
 * This scrubber is designed to run on the *accumulated* text every delta, so it must handle a
 * still-open tag (the closing `</think>` has not arrived yet): the partial block is hidden from
 * the live bubble and only the text before the opening `<think>` is shown. The persisted message
 * stores the scrubbed text only.
 */
internal fun String.scrubThinkTags(): String {
    if (isEmpty()) return this
    if (!contains("<think>")) return this
    val builder = StringBuilder(length)
    var cursor = 0
    while (true) {
        val openStart = indexOf("<think>", cursor)
        if (openStart < 0) {
            builder.append(substring(cursor))
            break
        }
        builder.append(substring(cursor, openStart))
        val openEnd = openStart + "<think>".length
        val closeStart = indexOf("</think>", openEnd)
        if (closeStart < 0) {
            // In-progress think block: drop the rest of the accumulated text from the bubble.
            // Once the closing tag arrives in a later delta, the whole block is dropped.
            break
        }
        cursor = closeStart + "</think>".length
    }
    return builder.toString()
        .replace(Regex("\\r?\\n\\s*\\r?\\n\\s*\\r?\\n+"), "\n\n")
        .trim()
}
