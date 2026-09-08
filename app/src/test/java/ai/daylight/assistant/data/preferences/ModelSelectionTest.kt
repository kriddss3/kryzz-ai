package ai.daylight.assistant.data.preferences

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ModelSelectionTest {
    @Test
    fun keepsTheCurrentModelWhenTheProviderStillOffersIt() {
        assertThat(
            chooseAvailableModel(
                current = "provider/current",
                available = setOf("provider/other", "provider/current"),
                preferred = "provider/preferred"
            )
        ).isEqualTo("provider/current")
    }

    @Test
    fun replacesAStaleModelWithTheProviderDefaultWhenAvailable() {
        assertThat(
            chooseAvailableModel(
                current = "old-provider/model",
                available = setOf("provider/other", "provider/default"),
                preferred = "provider/default"
            )
        ).isEqualTo("provider/default")
    }

    @Test
    fun fallsBackDeterministicallyWhenThePreferredModelIsUnavailable() {
        assertThat(
            chooseAvailableModel(
                current = "old-provider/model",
                available = setOf("provider/z", "provider/a"),
                preferred = "provider/missing"
            )
        ).isEqualTo("provider/a")
    }
}
