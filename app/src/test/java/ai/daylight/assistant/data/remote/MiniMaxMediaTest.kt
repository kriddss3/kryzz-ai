package ai.daylight.assistant.data.remote

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

class MiniMaxMediaTest {
    private val appJson = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }

    @Test fun videoRequestKeepsRequiredFieldsEvenWhenTheyEqualKotlinDefaults() {
        val encoded = appJson.encodeToString(
            MiniMaxVideoRequest(
                model = "MiniMax-H3",
                content = listOf(MiniMaxVideoContentItem(type = "text", text = "a cat walks")),
                duration = 5,
                resolution = "2K",
                ratio = "16:9"
            )
        )
        assertThat(encoded).contains("\"model\":\"MiniMax-H3\"")
        assertThat(encoded).contains("\"duration\":5")
        assertThat(encoded).contains("\"resolution\":\"2K\"")
        assertThat(encoded).contains("\"ratio\":\"16:9\"")
        assertThat(encoded).contains("\"type\":\"text\"")
    }

    @Test fun imageRequestAsksForUrlsAndKeepsAspectRatio() {
        val encoded = appJson.encodeToString(
            miniMaxImageRequest(model = "image-01", prompt = "a red fox in snow")
        )
        assertThat(encoded).contains("\"model\":\"image-01\"")
        assertThat(encoded).contains("\"response_format\":\"url\"")
        assertThat(encoded).contains("\"aspect_ratio\":\"16:9\"")
        assertThat(encoded).doesNotContain("base64")
    }

    @Test fun imageResponseAcceptsUrlOnlyPayload() {
        val parsed = appJson.decodeFromString<MiniMaxImageResponse>(
            """{"data":{"image_urls":["https://cdn.example.com/fox.jpg"]},"base_resp":{"status_code":0,"status_msg":"success"}}"""
        )
        val payload = parsed.requireImagePayload()
        assertThat(payload).isInstanceOf(MiniMaxImagePayload.Url::class.java)
        assertThat((payload as MiniMaxImagePayload.Url).url).endsWith("fox.jpg")
    }

    @Test fun nestedBaseRespErrorIsSanitized() {
        val error = parseMiniMaxError(
            appJson,
            """{"base_resp":{"status_code":2013,"status_msg":"unknown model <minimax[image-01]>"}}"""
        )
        assertThat(error).isNotNull()
        assertThat(error!!).doesNotContain("<minimax")
        assertThat(error).doesNotContain("[>")
        assertThat(error.lowercase()).contains("unknown model")
        assertThat(error).contains("2013")
    }

    @Test fun stripsOpenRouterStyleMiniMaxPrefix() {
        assertThat(bareMiniMaxModel("minimax/image-01")).isEqualTo("image-01")
        assertThat(bareMiniMaxModel("MiniMax-H3")).isEqualTo("MiniMax-H3")
        assertThat(isMiniMaxImageModel("minimax/image-01")).isTrue()
        assertThat(isMiniMaxImageModel("image-01-live")).isTrue()
        assertThat(isMiniMaxVideoModel("minimax/MiniMax-H3")).isTrue()
        assertThat(isMiniMaxVideoModel("openai/sora")).isFalse()
    }
}
