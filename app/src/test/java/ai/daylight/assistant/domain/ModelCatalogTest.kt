package ai.daylight.assistant.domain

import ai.daylight.assistant.data.remote.ModelArchitecture
import ai.daylight.assistant.data.remote.ModelReasoning
import ai.daylight.assistant.data.remote.OpenRouterModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ModelCatalogTest {
    @Test fun mapsReadOutputAndAgentCapabilities() {
        val model = OpenRouterModel(
            id = "provider/model",
            architecture = ModelArchitecture(inputModalities = listOf("text", "image", "video", "file"), outputModalities = listOf("text", "image")),
            supportedParameters = listOf("tools", "reasoning", "structured_outputs")
        )
        assertThat(model.capabilityLabels()).containsAtLeast(
            "Reads images", "Reads video", "Reads files", "Text output", "Image output", "Tool calling", "Reasoning", "Structured output"
        )
    }

    @Test fun mandatoryReasoningModelCannotBeTurnedOff() {
        val model = OpenRouterModel(
            id = "provider/model",
            reasoning = ModelReasoning(supportedEfforts = listOf("high", "low"), mandatory = true),
            supportedParameters = listOf("reasoning")
        )
        assertThat(model.availableReasoningEfforts()).containsExactly(ReasoningEffort.AUTO, ReasoningEffort.HIGH, ReasoningEffort.LOW).inOrder()
    }

}
