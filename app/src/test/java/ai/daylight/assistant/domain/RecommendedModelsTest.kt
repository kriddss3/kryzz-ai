package ai.daylight.assistant.domain

import ai.daylight.assistant.data.remote.ModelArchitecture
import ai.daylight.assistant.data.remote.OpenRouterModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecommendedModelsTest {

    @Test fun picksTheNewestOfEachFamilyInBoardOrder() {
        val picked = RecommendedModels.pick(
            listOf(
                model("deepseek/deepseek-v3-flash", "DeepSeek V3 Flash"),
                model("deepseek/deepseek-v3.2-flash", "DeepSeek V3.2 Flash"),
                model("deepseek/deepseek-v3.2-flash:free", "DeepSeek V3.2 Flash (free)"),
                model("anthropic/claude-3-5-sonnet-20241022", "Claude 3.5 Sonnet"),
                model("anthropic/claude-sonnet-4.5", "Claude Sonnet 4.5"),
                model("anthropic/claude-sonnet-4.5-20250929", "Claude Sonnet 4.5"),
                model("anthropic/claude-sonnet-5", "Claude Sonnet 5"),
                model("anthropic/claude-haiku-3.5", "Claude Haiku 3.5"),
                model("anthropic/claude-haiku-4.5", "Claude Haiku 4.5"),
                model("anthropic/claude-opus-4-1", "Claude Opus 4.1"),
                model("anthropic/claude-opus-4.5", "Claude Opus 4.5"),
                model("xiaomi/mimo-v2-flash", "MiMo V2 Flash"),
                model("xiaomi/mimo-v2.5-flash", "MiMo V2.5 Flash"),
                model("google/gemini-2.5-flash", "Gemini 2.5 Flash"),
                model("google/gemini-2.5-flash-lite", "Gemini 2.5 Flash Lite"),
                model("google/gemini-2.5-flash-image", "Gemini 2.5 Flash Image"),
                model("google/gemini-3-flash", "Gemini 3 Flash"),
                model("google/gemini-3.8-flash", "Gemini 3.8 Flash"),
                model("openai/gpt-4o-mini", "GPT-4o mini"),
            )
        )

        assertThat(picked.map { it.id }).containsExactly(
            "deepseek/deepseek-v3.2-flash",
            "anthropic/claude-sonnet-5",
            "anthropic/claude-haiku-4.5",
            "anthropic/claude-opus-4.5",
            "xiaomi/mimo-v2.5-flash",
            "google/gemini-3.8-flash",
        ).inOrder()
    }

    @Test fun aMissingFamilyIsSkipped() {
        val picked = RecommendedModels.pick(
            listOf(
                model("anthropic/claude-sonnet-5", "Claude Sonnet 5"),
                model("google/gemini-3.8-flash", "Gemini 3.8 Flash"),
            )
        )
        assertThat(picked.map { it.id }).containsExactly(
            "anthropic/claude-sonnet-5",
            "google/gemini-3.8-flash",
        ).inOrder()
    }

    @Test fun sameGenerationPrefersThePlainIdOverAPreview() {
        val picked = RecommendedModels.pick(
            listOf(
                model("google/gemini-2.5-flash-preview", "Gemini 2.5 Flash Preview"),
                model("google/gemini-2.5-flash", "Gemini 2.5 Flash"),
            )
        )
        assertThat(picked.map { it.id }).containsExactly("google/gemini-2.5-flash")
    }

    @Test fun anImageOnlyModelIsNotATextRecommendation() {
        val picked = RecommendedModels.pick(
            listOf(
                model(
                    "google/gemini-3.8-flash-image",
                    "Gemini 3.8 Flash Image",
                    outputs = listOf("image"),
                ),
                model("google/gemini-2.5-flash", "Gemini 2.5 Flash"),
            )
        )
        assertThat(picked.map { it.id }).containsExactly("google/gemini-2.5-flash")
    }

    @Test fun aRecommendedFavouriteIsNotListedTwice() {
        val sonnet = model("anthropic/claude-sonnet-5", "Claude Sonnet 5")
        val mini = model("openai/gpt-4o-mini", "GPT-4o mini")
        val llama = model("meta/llama-4", "Llama 4")
        val board = textModelBoard(
            listOf(llama, mini, sonnet),
            favorites = setOf(sonnet.id, mini.id),
        )
        assertThat(board.recommended.map { it.id }).containsExactly(sonnet.id)
        assertThat(board.saved.map { it.id }).containsExactly(mini.id)
        assertThat(board.rest.map { it.id }).containsExactly(llama.id)
    }

    private fun model(
        id: String,
        name: String,
        outputs: List<String> = emptyList(),
    ) = OpenRouterModel(
        id = id,
        name = name,
        architecture = outputs.takeIf { it.isNotEmpty() }?.let { ModelArchitecture(outputModalities = it) },
    )
}
