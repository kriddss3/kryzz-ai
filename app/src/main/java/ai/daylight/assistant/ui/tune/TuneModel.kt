package ai.daylight.assistant.ui.tune

import ai.daylight.assistant.domain.AgentQuality
import ai.daylight.assistant.domain.AgentQualityPolicy
import ai.daylight.assistant.domain.AssistantPreset
import ai.daylight.assistant.domain.ChatProvider
import ai.daylight.assistant.domain.ThemeMode
import ai.daylight.assistant.ui.home.HomeBoard

/**
 * What Tune knows about the stored API keys. Only the last four characters of a key are
 * ever held here (for "ENDS 4F2A"), never the key itself.
 */
data class TuneKeys(
    val openRouterTail: String? = null,
    val minimaxTail: String? = null,
    val hasParallel: Boolean = false,
    val hasFish: Boolean = false,
)

/** Tune's words and numbers (kit settings.png). Pure, so it is unit tested. */
object TuneModel {

    /**
     * The cost cap slider's 24 dots, in US cents. Dot 0 is Off. The steps are finer at the
     * low end, where most caps sit, and every value the old chips offered is kept.
     */
    val CAP_STOPS: List<Int> = listOf(
        0, 5, 10, 15, 20, 25, 30, 40, 50, 60, 70, 80,
        90, 100, 125, 150, 175, 200, 250, 300, 350, 400, 450, 500
    )

    /** The dot nearest to a stored cap; a tie goes to the lower dot. */
    fun capIndex(cents: Int): Int {
        var best = 0
        for (i in CAP_STOPS.indices) {
            if (kotlin.math.abs(CAP_STOPS[i] - cents) < kotlin.math.abs(CAP_STOPS[best] - cents)) best = i
        }
        return best
    }

    fun capCents(index: Int): Int = CAP_STOPS[index.coerceIn(0, CAP_STOPS.lastIndex)]

    /** The dot-text value beside the label: "$0.50", or "OFF". */
    fun capValue(cents: Int): String = if (cents <= 0) "OFF" else HomeBoard.capLabel(cents)

    /** What the cap does, including Max's raised floor (AgentQualityPolicy). */
    fun capCaption(quality: AgentQuality, cents: Int): String = when {
        cents <= 0 -> "No cap. A run stops when its step budget ends."
        quality == AgentQuality.MAX && cents < AgentQualityPolicy.MAX_COST_CAP_CENTS ->
            "A run stops using tools here and writes its answer. Max raises it to " +
                HomeBoard.capLabel(AgentQualityPolicy.MAX_COST_CAP_CENTS) + "."
        else -> "A run stops using tools here and writes its answer."
    }

    /** One line under the tiles: what the chosen mode does to a run. */
    fun qualityCaption(quality: AgentQuality): String = when (quality) {
        AgentQuality.FAST -> "Fast: low reasoning, up to ${AgentQualityPolicy.FAST_STEP_BUDGET} tool rounds, no review"
        AgentQuality.BALANCED -> "Balanced: your Run model with your own limits"
        AgentQuality.MAX -> "Max: your Max model, high reasoning, costs the most"
    }

    /**
     * The last four characters of a stored key, uppercased. A key too short to be real
     * shows nothing, so a tail never gives away most of a key.
     */
    fun keyTail(key: String?): String? {
        val clean = key?.trim().orEmpty()
        return if (clean.length < 12) null else clean.takeLast(4).uppercase()
    }

    /** "OPENROUTER IN KEYSTORE, ENDS 4F2A" for the active provider's key. */
    fun keyCaption(provider: ChatProvider, keys: TuneKeys): String {
        val tail = when (provider) {
            ChatProvider.OPENROUTER -> keys.openRouterTail
            ChatProvider.MINIMAX -> keys.minimaxTail
        }
        return if (tail == null) "No ${provider.label} key yet" else "${provider.label} in Keystore, ends $tail"
    }

    /** "openai/gpt-4o-mini" -> "gpt-4o-mini". */
    fun modelName(id: String): String = id.trim().substringAfterLast('/')

    /** The model Run uses at this quality (a blank Max slot falls back to the agent model). */
    fun runModel(quality: AgentQuality, agentModel: String, maxModel: String): String =
        if (quality == AgentQuality.MAX) maxModel.trim().ifBlank { agentModel } else agentModel

    /** "TALK GPT-4O-MINI, RUN GLM-4.6". */
    fun chatModelCaption(chatModel: String, runModel: String): String {
        val talk = modelName(chatModel).ifBlank { "not set" }
        val run = modelName(runModel).ifBlank { "not set" }
        return "Talk $talk, Run $run"
    }

    /** "IMAGE-01, MINIMAX-H3", or what is missing. */
    fun mediaCaption(imageModel: String, videoModel: String): String {
        val image = modelName(imageModel)
        val video = modelName(videoModel)
        return when {
            image.isBlank() && video.isBlank() -> "None picked yet"
            image.isBlank() -> "No image model, $video"
            video.isBlank() -> "$image, no video model"
            else -> "$image, $video"
        }
    }

    fun searchCaption(hasParallelKey: Boolean): String =
        if (hasParallelKey) "Talk and Run, through Parallel" else "Needs a Parallel key"

    fun memoryCaption(enabled: Boolean, count: Int): String = when {
        !enabled -> "Paused, nothing saved or used"
        count == 1 -> "1 memory on this phone"
        else -> "$count memories on this phone"
    }

    fun locationCaption(enabled: Boolean, label: String): String = when {
        enabled && label.isNotBlank() -> "On, $label"
        enabled -> "On, approximate only"
        else -> "Off. Helps with local questions"
    }

    fun lockCaption(available: Boolean): String =
        if (available) "Fingerprint or face when Kryzz reopens" else "No fingerprint or face set up on this phone"

    fun personaCaption(presetId: String): String =
        if (presetId == "custom") "Your own instructions"
        else AssistantPreset.all.firstOrNull { it.id == presetId }?.title ?: "Balanced Assistant"

    /** Tune's Appearance segment, in the kit's order: Dark, Light, then Auto (follow the phone). */
    val THEME_ORDER: List<ThemeMode> = listOf(ThemeMode.DARK, ThemeMode.LIGHT, ThemeMode.SYSTEM)

    fun themeLabel(mode: ThemeMode): String = when (mode) {
        ThemeMode.DARK -> "Dark"
        ThemeMode.LIGHT -> "Light"
        ThemeMode.SYSTEM -> "Auto"
    }
}
