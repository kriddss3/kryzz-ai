package ai.daylight.assistant.domain

import ai.daylight.assistant.data.remote.OpenRouterModel

fun OpenRouterModel.availableReasoningEfforts(): List<ReasoningEffort> {
    val metadata = reasoning
    if (metadata == null && "reasoning" !in supportedParameters && "include_reasoning" !in supportedParameters) {
        return listOf(ReasoningEffort.AUTO)
    }
    val supported = metadata?.supportedEfforts
    val selectable = if (supported == null) {
        ReasoningEffort.entries.filter { it !in setOf(ReasoningEffort.AUTO, ReasoningEffort.NONE) }
    } else {
        supported.map(ReasoningEffort::fromApi).filter { it != ReasoningEffort.AUTO }
    }
    return buildList {
        add(ReasoningEffort.AUTO)
        if (metadata?.mandatory != true) add(ReasoningEffort.NONE)
        addAll(selectable.distinct())
    }
}

fun OpenRouterModel.capabilityLabels(): List<String> = buildList {
    architecture?.inputModalities.orEmpty().forEach { modality ->
        add(
            when (modality.lowercase()) {
                "text" -> "Reads text"
                "image" -> "Reads images"
                "video" -> "Reads video"
                "file", "pdf" -> "Reads files"
                "audio" -> "Hears audio"
                else -> "Reads ${modality.lowercase()}"
            }
        )
    }
    architecture?.outputModalities.orEmpty().forEach { modality ->
        add(
            when (modality.lowercase()) {
                "text" -> "Text output"
                "image" -> "Image output"
                "video" -> "Video output"
                "audio" -> "Audio output"
                "file", "pdf" -> "File output"
                else -> "${modality.replaceFirstChar(Char::uppercase)} output"
            }
        )
    }
    if ("tools" in supportedParameters) add("Tool calling")
    if (reasoning != null || "reasoning" in supportedParameters || "include_reasoning" in supportedParameters) add("Reasoning")
    if ("structured_outputs" in supportedParameters || "response_format" in supportedParameters) add("Structured output")
}.distinct()
