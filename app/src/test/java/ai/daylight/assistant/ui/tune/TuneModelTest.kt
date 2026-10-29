package ai.daylight.assistant.ui.tune

import ai.daylight.assistant.domain.AgentQuality
import ai.daylight.assistant.domain.AgentTurnPolicy
import ai.daylight.assistant.domain.ChatProvider
import ai.daylight.assistant.domain.ThemeMode
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TuneModelTest {
    @Test fun theCapSliderHas24DotsFromOffToFiveDollars() {
        val stops = TuneModel.CAP_STOPS
        assertThat(stops).hasSize(24)
        assertThat(stops.first()).isEqualTo(0)
        assertThat(stops.last()).isEqualTo(500)
        assertThat(stops).isInStrictOrder()
        // Every value the old cap chips offered, and the default, stays reachable.
        assertThat(stops).containsAtLeast(0, 10, 25, 50, 100, 200)
        assertThat(stops).contains(AgentTurnPolicy.DEFAULT_COST_CAP_CENTS)
    }

    @Test fun aStoredCapLandsOnTheNearestDot() {
        assertThat(TuneModel.capIndex(0)).isEqualTo(0)
        assertThat(TuneModel.capCents(TuneModel.capIndex(25))).isEqualTo(25)
        assertThat(TuneModel.capCents(TuneModel.capIndex(500))).isEqualTo(500)
        // 33 sits between 30 and 40: nearer 30.
        assertThat(TuneModel.capCents(TuneModel.capIndex(33))).isEqualTo(30)
        // 35 is a tie between 30 and 40: the lower dot wins.
        assertThat(TuneModel.capCents(TuneModel.capIndex(35))).isEqualTo(30)
        assertThat(TuneModel.capCents(TuneModel.capIndex(9_999))).isEqualTo(500)
        assertThat(TuneModel.capCents(TuneModel.capIndex(-5))).isEqualTo(0)
    }

    @Test fun capCentsClampsOutOfRangeDots() {
        assertThat(TuneModel.capCents(-1)).isEqualTo(0)
        assertThat(TuneModel.capCents(99)).isEqualTo(500)
    }

    @Test fun theCapValueIsDotTextOrOff() {
        assertThat(TuneModel.capValue(0)).isEqualTo("OFF")
        assertThat(TuneModel.capValue(50)).isEqualTo("$0.50")
        assertThat(TuneModel.capValue(500)).isEqualTo("$5.00")
        // Everything shown in dot text must be drawable by the 5x7 font.
        TuneModel.CAP_STOPS.forEach { cents ->
            TuneModel.capValue(cents).forEach { ch ->
                assertThat(ai.daylight.assistant.ui.dot.effects.DotFont5x7.supports(ch)).isTrue()
            }
        }
    }

    @Test fun theCapCaptionSaysWhatReallyHappens() {
        assertThat(TuneModel.capCaption(AgentQuality.BALANCED, 0)).contains("No cap")
        assertThat(TuneModel.capCaption(AgentQuality.BALANCED, 50))
            .isEqualTo("A run stops using tools here and writes its answer.")
        // Max raises a lower cap to $1.00 (AgentQualityPolicy), and Tune says so.
        assertThat(TuneModel.capCaption(AgentQuality.MAX, 50)).endsWith("Max raises it to $1.00.")
        assertThat(TuneModel.capCaption(AgentQuality.MAX, 200)).doesNotContain("raises")
        assertThat(TuneModel.capCaption(AgentQuality.MAX, 0)).contains("No cap")
    }

    @Test fun eachThinkingModeHasItsOwnCaption() {
        val captions = AgentQuality.entries.map(TuneModel::qualityCaption)
        assertThat(captions.toSet()).hasSize(3)
        assertThat(TuneModel.qualityCaption(AgentQuality.FAST)).contains("8 tool rounds")
        assertThat(TuneModel.qualityCaption(AgentQuality.MAX)).contains("costs the most")
    }

    @Test fun onlyTheLastFourCharactersOfAKeyAreShown() {
        assertThat(TuneModel.keyTail("sk-or-v1-0123456789abcdef4f2a")).isEqualTo("4F2A")
        assertThat(TuneModel.keyTail("  sk-or-v1-0123456789ab  ")).isEqualTo("89AB")
        // Too short to be a real key: show nothing rather than most of it.
        assertThat(TuneModel.keyTail("short-key")).isNull()
        assertThat(TuneModel.keyTail("")).isNull()
        assertThat(TuneModel.keyTail(null)).isNull()
    }

    @Test fun theKeyCaptionNamesTheActiveProvider() {
        val keys = TuneKeys(openRouterTail = "4F2A", minimaxTail = null)
        assertThat(TuneModel.keyCaption(ChatProvider.OPENROUTER, keys)).isEqualTo("OpenRouter in Keystore, ends 4F2A")
        assertThat(TuneModel.keyCaption(ChatProvider.MINIMAX, keys)).isEqualTo("No MiniMax key yet")
    }

    @Test fun modelCaptionsUseShortNamesAndRunFollowsTheMode() {
        assertThat(TuneModel.modelName("openai/gpt-4o-mini")).isEqualTo("gpt-4o-mini")
        assertThat(TuneModel.modelName("MiniMax-M3")).isEqualTo("MiniMax-M3")
        assertThat(TuneModel.runModel(AgentQuality.BALANCED, "z-ai/glm-4.6", "anthropic/opus")).isEqualTo("z-ai/glm-4.6")
        assertThat(TuneModel.runModel(AgentQuality.MAX, "z-ai/glm-4.6", "anthropic/opus")).isEqualTo("anthropic/opus")
        // A blank Max slot falls back to the agent model, as the run itself does.
        assertThat(TuneModel.runModel(AgentQuality.MAX, "z-ai/glm-4.6", " ")).isEqualTo("z-ai/glm-4.6")
        assertThat(TuneModel.chatModelCaption("openai/gpt-4o-mini", "z-ai/glm-4.6")).isEqualTo("Talk gpt-4o-mini, Run glm-4.6")
        assertThat(TuneModel.chatModelCaption("", "")).isEqualTo("Talk not set, Run not set")
    }

    @Test fun theMediaCaptionSaysWhatIsMissing() {
        assertThat(TuneModel.mediaCaption("", "")).isEqualTo("None picked yet")
        assertThat(TuneModel.mediaCaption("image-01", "")).isEqualTo("image-01, no video model")
        assertThat(TuneModel.mediaCaption("", "MiniMax-H3")).isEqualTo("No image model, MiniMax-H3")
        assertThat(TuneModel.mediaCaption("image-01", "MiniMax-H3")).isEqualTo("image-01, MiniMax-H3")
    }

    @Test fun privacyCaptionsFollowTheRealState() {
        assertThat(TuneModel.searchCaption(true)).contains("Parallel")
        // C8: the same switch gates Find, which needs no key.
        assertThat(TuneModel.searchCaption(true)).contains("Find")
        assertThat(TuneModel.searchCaption(false)).isEqualTo("Find only; Talk needs a Parallel key")
        assertThat(TuneModel.memoryCaption(false, 12)).startsWith("Paused")
        assertThat(TuneModel.memoryCaption(true, 1)).isEqualTo("1 memory on this phone")
        assertThat(TuneModel.memoryCaption(true, 12)).isEqualTo("12 memories on this phone")
        assertThat(TuneModel.locationCaption(true, "Riga, LV")).isEqualTo("On, Riga, LV")
        assertThat(TuneModel.locationCaption(true, "")).isEqualTo("On, approximate only")
        assertThat(TuneModel.locationCaption(false, "Riga, LV")).startsWith("Off")
        assertThat(TuneModel.lockCaption(false)).startsWith("No fingerprint")
    }

    @Test fun personaCaptionNamesThePreset() {
        assertThat(TuneModel.personaCaption("concise")).isEqualTo("Concise")
        assertThat(TuneModel.personaCaption("custom")).isEqualTo("Your own instructions")
        assertThat(TuneModel.personaCaption("gone")).isEqualTo("Balanced Assistant")
    }

    @Test fun theAppearanceSegmentCoversEveryThemeInKitOrder() {
        assertThat(TuneModel.THEME_ORDER).containsExactly(ThemeMode.DARK, ThemeMode.LIGHT, ThemeMode.SYSTEM).inOrder()
        assertThat(TuneModel.THEME_ORDER.map(TuneModel::themeLabel)).containsExactly("Dark", "Light", "Auto").inOrder()
    }
}
