package ai.daylight.assistant.ui

import ai.daylight.assistant.data.remote.OpenRouterModel
import org.junit.Assert.assertEquals
import org.junit.Test
import com.google.common.truth.Truth.assertThat

class ProviderBrandTest {
    @Test
    fun knownProvidersReceiveClearStableMonograms() {
        assertEquals("OpenAI", providerBrandFor("openai/gpt-4.1").name)
        assertEquals("OA", providerBrandFor("openai/gpt-4.1").monogram)
        assertEquals("AN", providerBrandFor("anthropic/claude-sonnet").monogram)
        assertEquals("DS", providerBrandFor("deepseek/deepseek-r1").monogram)
        assertEquals("AWS", providerBrandFor("amazon-nova/nova-pro").monogram)
    }

    @Test
    fun unknownProviderGetsReadableFallbackIdentity() {
        val brand = providerBrandFor("acme-labs/stellar-1")
        assertEquals("Acme Labs", brand.name)
        assertEquals("AL", brand.monogram)
    }

    @Test fun knownProvidersKeepTheirCompanyName() {
        assertEquals("Tencent", providerBrandFor("tencent/hunyuan-a13b-instruct").name)
        assertEquals("xAI", providerBrandFor("x-ai/grok-4").name)
        assertEquals("Moonshot AI", providerBrandFor("moonshotai/kimi-k2").name)
        assertEquals("Qwen", providerBrandFor("qwen/qwen3-32b").name)
    }

    @Test fun companyFilterKeepsOnlyThatProvider() {
        val models = listOf(
            OpenRouterModel(id = "openai/a", name = "A"),
            OpenRouterModel(id = "anthropic/b", name = "B")
        )
        assertThat(filterCatalog(models, "", "OpenAI").map { it.id }).containsExactly("openai/a")
        assertThat(filterCatalog(models, "", null)).hasSize(2)
    }
}
