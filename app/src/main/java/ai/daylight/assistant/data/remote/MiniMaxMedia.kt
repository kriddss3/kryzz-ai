package ai.daylight.assistant.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

internal fun miniMaxImageRequest(model: String, prompt: String): MiniMaxImageRequest =
    MiniMaxImageRequest(
        model = bareMiniMaxModel(model),
        prompt = prompt,
        aspectRatio = "16:9",
        responseFormat = "url",
        n = 1
    )

internal sealed class MiniMaxImagePayload {
    data class Url(val url: String) : MiniMaxImagePayload()
    data class Base64(val value: String) : MiniMaxImagePayload()
}

internal fun bareMiniMaxModel(id: String): String {
    val trimmed = id.trim()
    return if (trimmed.startsWith("minimax/", ignoreCase = true)) trimmed.substringAfter('/') else trimmed
}

internal fun isMiniMaxImageModel(id: String): Boolean =
    bareMiniMaxModel(id).startsWith("image-", ignoreCase = true)

internal fun isMiniMaxVideoModel(id: String): Boolean {
    val bare = bareMiniMaxModel(id)
    return bare.startsWith("MiniMax-H", ignoreCase = true) ||
        bare.startsWith("Hailuo", ignoreCase = true) ||
        bare.startsWith("T2V-", ignoreCase = true) ||
        bare.startsWith("I2V-", ignoreCase = true) ||
        bare.startsWith("S2V-", ignoreCase = true)
}

internal fun MiniMaxBaseResp?.throwIfFailed(fallback: String = "MiniMax could not complete this request.") {
    val code = this?.statusCode ?: return
    if (code == 0) return
    throw AssistantApiException(
        kind = if (code == 1004) ErrorKind.INVALID_KEY else ErrorKind.VALIDATION,
        message = friendlyMiniMaxMessage(statusMsg, code, fallback)
    )
}

internal fun MiniMaxImageResponse.requireImagePayload(): MiniMaxImagePayload {
    baseResp.throwIfFailed("The MiniMax image model rejected this request.")
    val base64 = data?.imageBase64?.firstOrNull()?.trim().orEmpty()
    if (base64.isNotEmpty()) return MiniMaxImagePayload.Base64(base64)
    val url = data?.imageUrls?.firstOrNull()?.trim().orEmpty()
    if (url.isNotEmpty()) return MiniMaxImagePayload.Url(url)
    throw AssistantApiException(ErrorKind.UNKNOWN, "The MiniMax image model returned no image.")
}

internal fun parseMiniMaxError(json: Json, text: String): String? {
    val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
    val base = root["base_resp"] as? JsonObject ?: root
    val msg = (base["status_msg"] as? JsonPrimitive)?.contentOrNull
    val code = (base["status_code"] as? JsonPrimitive)?.intOrNull
        ?: (base["status_code"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
    val openAi = ((root["error"] as? JsonObject)?.get("message") as? JsonPrimitive)?.contentOrNull
    val raw = when {
        !msg.isNullOrBlank() && code != null && code != 0 -> "$msg ($code)"
        !msg.isNullOrBlank() -> msg
        code != null && code != 0 -> "MiniMax error $code"
        !openAi.isNullOrBlank() -> openAi
        else -> return null
    }
    return friendlyMiniMaxMessage(raw, code)
}

internal fun friendlyMiniMaxMessage(raw: String?, code: Int? = null, fallback: String = "MiniMax could not complete this request."): String {
    val stripped = raw.orEmpty()
        .replace(Regex("<[^>]*>"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    val body = stripped.ifBlank { fallback }
    return if (code != null && code != 0 && !body.contains("($code)")) "$body ($code)" else body
}
