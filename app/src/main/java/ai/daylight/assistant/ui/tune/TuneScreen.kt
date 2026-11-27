package ai.daylight.assistant.ui.tune

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.dp
import ai.daylight.assistant.BuildConfig
import ai.daylight.assistant.R
import ai.daylight.assistant.data.preferences.SettingsState
import ai.daylight.assistant.domain.AgentQuality
import ai.daylight.assistant.domain.ModelPurpose
import ai.daylight.assistant.domain.ThemeMode
import ai.daylight.assistant.ui.Routes
import ai.daylight.assistant.ui.dot.DotRadius
import ai.daylight.assistant.ui.dot.DotSecondaryColor
import ai.daylight.assistant.ui.dot.KryzzGlyph
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.effects.DotMatrixText
import ai.daylight.assistant.ui.dot.effects.LocalDotSplash
import ai.daylight.assistant.ui.home.HomeBoard
import ai.daylight.assistant.ui.shell.Arm
import ai.daylight.assistant.ui.shell.InkLines
import ai.daylight.assistant.ui.shell.InkStripEffect
import ai.daylight.assistant.ui.talk.DotSheet
import ai.daylight.assistant.ui.talk.OptionRow
import ai.daylight.assistant.ui.talk.SheetLabel
import ai.daylight.assistant.voice.TtsProvider
import java.util.Locale

/** Everything Tune shows: the saved settings plus a few facts read elsewhere. */
data class TuneState(
    val settings: SettingsState = SettingsState(),
    val keys: TuneKeys = TuneKeys(),
    val memoryCount: Int = 0,
    val biometricAvailable: Boolean = false,
    /** The last backup result ("Imported 3 conversations."), shown under the Backup row. */
    val backupNotice: String? = null,
)

/** What Tune's controls do. Each saves at once; there is no Save button. */
class TuneActions(
    val onQuality: (AgentQuality) -> Unit = {},
    val onCostCap: (Int) -> Unit = {},
    val onSearch: (Boolean) -> Unit = {},
    val onMemory: (Boolean) -> Unit = {},
    /** Turning location on may first ask for the permission. */
    val onLocation: (Boolean) -> Unit = {},
    val onBiometric: (Boolean) -> Unit = {},
    val onTheme: (ThemeMode) -> Unit = {},
    val onDotBackground: (Boolean) -> Unit = {},
    val onSecondaryColor: (String) -> Unit = {},
    val onExportChats: () -> Unit = {},
    val onImportChats: () -> Unit = {},
    /** v2.1: shows the first-run tour again, on Home. */
    val onTour: () -> Unit = {},
)

/**
 * Tune (kit settings.png): TUNE in dot text, thinking-mode tiles, the per-run cost cap as a
 * 24-dot slider, Models and keys, Privacy toggles, Appearance, then ruled rows to the deeper
 * pages. The accent swatches are left out (yellow is the only accent), and "Keep chats on
 * this phone" is a fact, not a switch, because chats are always local.
 */
@Composable
fun TuneScreen(
    state: TuneState,
    actions: TuneActions,
    onOpen: (String) -> Unit,
    onModelCatalog: () -> Unit,
    onSkills: () -> Unit,
    onLibrary: () -> Unit,
) {
    val c = DotTheme.c
    val s = state.settings
    InkStripEffect(InkLines.tune(s.agentQuality), arm = Arm.TUNE)
    // The slider moves live while dragging and saves once on release.
    var capIndex by remember(s.costCapCents) { mutableIntStateOf(TuneModel.capIndex(s.costCapCents)) }
    var showBackup by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 24.dp)
    ) {
        // The arm title opens the chat library, as Talk's and Run's do.
        Box(
            Modifier
                .heightIn(min = 48.dp)
                .clip(DotRadius.Tile)
                .clickable(onClickLabel = "Open chat library", onClick = onLibrary)
                .clearAndSetSemantics {
                    contentDescription = "Tune. Open chat library"
                    onClick(label = "Open chat library") { onLibrary(); true }
                }
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KryzzGlyph(R.drawable.kz_led_settings, Modifier.padding(end = 10.dp).size(28.dp), tint = c.textPrimary)
                DotMatrixText("TUNE", pitch = 4.2.dp, on = c.textPrimary, off = Color.Transparent)
            }
        }
        Spacer(Modifier.height(20.dp))

        TuneSection("Thinking mode")
        ThinkingModeTiles(s.agentQuality, actions.onQuality)
        TuneCaption(TuneModel.qualityCaption(s.agentQuality), Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(24.dp))

        val capCents = TuneModel.capCents(capIndex)
        val capSpoken = if (capCents <= 0) "Off" else HomeBoard.capLabel(capCents)
        TuneSection("Cost cap per run", mark = R.drawable.kz_led_cost_cap) {
            DotMatrixText(
                TuneModel.capValue(capCents),
                pitch = 3.dp,
                on = if (capCents <= 0) c.textSecondary else c.textPrimary,
                off = Color.Transparent,
                contentDescription = capSpoken
            )
        }
        DotSlider(
            index = capIndex,
            count = TuneModel.CAP_STOPS.size,
            onIndexChange = { capIndex = it },
            onChangeFinished = { actions.onCostCap(TuneModel.capCents(capIndex)) },
            label = "Cost cap per run",
            valueLabel = capSpoken
        )
        TuneCaption(TuneModel.capCaption(s.agentQuality, capCents), Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(28.dp))

        TuneSection("Models and keys")
        TuneRow(
            R.drawable.kz_led_chat_model, "Chat model",
            TuneModel.chatModelCaption(s.defaultModel, TuneModel.runModel(s.agentQuality, s.agentModel, s.maxModel)),
            onClick = { onOpen(Routes.models(ModelPurpose.CHAT)) }
        )
        TuneRow(
            R.drawable.kz_led_api_keys, "API keys", TuneModel.keyCaption(s.chatProvider, state.keys),
            onClick = { onOpen(Routes.API) }
        )
        TuneRow(
            R.drawable.kz_led_image_video, "Image and video model", TuneModel.mediaCaption(s.imageModel, s.videoModel),
            onClick = { onOpen(Routes.models(ModelPurpose.IMAGE)) }
        )
        Spacer(Modifier.height(24.dp))

        TuneSection("Privacy")
        TuneRow(R.drawable.kz_led_chats_on_phone, "Chats stay on this phone", "No cloud copy, no analytics", TuneTrailing.None)
        TuneRow(
            R.drawable.kz_led_web_search, "Allow web search", TuneModel.searchCaption(state.keys.hasParallel),
            TuneTrailing.Switch(s.searchEnabled, actions.onSearch)
        )
        TuneRow(
            R.drawable.kz_led_chat_memory, "Chat memory", TuneModel.memoryCaption(s.memoryEnabled, state.memoryCount),
            TuneTrailing.Switch(s.memoryEnabled, actions.onMemory)
        )
        TuneRow(
            R.drawable.kz_led_location, "Approximate location", TuneModel.locationCaption(s.locationEnabled, s.locationLabel),
            TuneTrailing.Switch(s.locationEnabled, actions.onLocation)
        )
        TuneRow(
            R.drawable.kz_led_lock, "Lock the app", TuneModel.lockCaption(state.biometricAvailable),
            TuneTrailing.Switch(s.biometricLock, actions.onBiometric, enabled = state.biometricAvailable || s.biometricLock)
        )
        Spacer(Modifier.height(24.dp))

        TuneSection("Appearance")
        ThemeSegments(s.themeMode, actions.onTheme, Modifier.padding(bottom = 8.dp))
        TuneRow(
            R.drawable.kz_led_dot_background, "Dot background", "Talk, Run and Home",
            TuneTrailing.Switch(s.dotBackground, actions.onDotBackground)
        )
        SecondaryColorSlider(
            selectedId = s.secondaryColorId,
            onSelect = actions.onSecondaryColor,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
        )
        TuneRow(
            R.drawable.kz_led_text_motion, "Text and motion", "Text size, chat density, animations",
            onClick = { onOpen(Routes.APPEARANCE) }
        )
        Spacer(Modifier.height(24.dp))

        TuneSection("More")
        TuneRow(R.drawable.kz_led_persona, "Persona", TuneModel.personaCaption(s.presetId), onClick = { onOpen(Routes.PERSONA) })
        TuneRow(
            R.drawable.kz_led_search_limits, "Search and run limits",
            "Step budget ${s.stepBudget}, review ${if (s.reviewAnswers) "on" else "off"}",
            onClick = { onOpen(Routes.TOOLS) }
        )
        TuneRow(
            R.drawable.kz_led_voice, "Voice", "Spoken by ${TtsProvider.from(s.ttsProvider).label}",
            onClick = { onOpen(Routes.VOICE) }
        )
        TuneRow(
            R.drawable.kz_led_memories, "Memories",
            if (state.memoryCount == 1) "1 saved" else "${state.memoryCount} saved",
            onClick = { onOpen(Routes.MEMORY) }
        )
        TuneRow(R.drawable.kz_led_skills, "Skills", "Reusable skills and tools", onClick = onSkills)
        // C10: Routines used to open from the chat drawer's Cron button, which is gone.
        TuneRow(R.drawable.kz_led_routines, "Routines", "Prompts that run on a schedule", onClick = { onOpen(Routes.CRON) })
        TuneRow(R.drawable.kz_led_model_catalog, "Model catalog", "Every model your key can use", onClick = onModelCatalog)
        TuneRow(R.drawable.kz_led_data_usage, "Data and usage", "What is stored, clear local data", onClick = { onOpen(Routes.PRIVACY) })
        TuneRow(
            R.drawable.kz_led_backup, "Backup", state.backupNotice ?: "Export or import your chats",
            onClick = { showBackup = true }
        )
        TuneRow(R.drawable.kz_led_tour, "Tour", "How Kryzz works, and where to get keys", onClick = actions.onTour)
        TuneRow(R.drawable.kz_led_about, "About", "Version ${BuildConfig.VERSION_NAME}", onClick = { onOpen(Routes.ABOUT) })
        Spacer(Modifier.height(28.dp))
        TuneCaption(
            "Kryzz AI ${BuildConfig.VERSION_NAME}, created and coded by Kryzz",
            Modifier.fillMaxWidth()
        )
    }

    if (showBackup) {
        DotSheet(onDismiss = { showBackup = false }) {
            SheetLabel("Backup")
            OptionRow(R.drawable.ic_kz_export, "Export chats", value = "JSON file", onClick = {
                showBackup = false
                actions.onExportChats()
            })
            OptionRow(R.drawable.ic_kz_download, "Import chats", value = "Merges in", onClick = {
                showBackup = false
                actions.onImportChats()
            })
            TuneCaption("API keys are never in a backup", Modifier.padding(top = 10.dp))
        }
    }
}

/** Secondary colour slider (Talk dot-grid ripple + Tune preview). */
@Composable
fun SecondaryColorSlider(
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DotTheme.c
    val splash = LocalDotSplash.current
    val colors = DotSecondaryColor.entries
    var colorIndex by remember(selectedId) { mutableIntStateOf(DotSecondaryColor.indexOf(selectedId)) }
    var thumbOrigin by remember { mutableStateOf<Offset?>(null) }
    val chosen = colors[colorIndex]
    val chosenTint = chosen.colorFor(c.dark)
    Column(modifier.fillMaxWidth()) {
        TuneSection("Secondary color") {
            DotMatrixText(
                chosen.label.uppercase(Locale.US),
                pitch = 3.dp,
                on = chosenTint,
                off = Color.Transparent,
                contentDescription = chosen.label
            )
        }
        DotSlider(
            index = colorIndex,
            count = colors.size,
            onIndexChange = { idx ->
                val prev = colors[colorIndex].colorFor(c.dark)
                colorIndex = idx
                val next = colors[idx]
                splash?.trigger(
                    origin = thumbOrigin,
                    targetColor = next.colorFor(c.dark),
                    fromColor = prev,
                    persistTint = false
                )
            },
            onChangeFinished = { onSelect(colors[colorIndex].id) },
            onThumbRootPosition = { thumbOrigin = it },
            activeDotColor = chosenTint,
            label = "Secondary color",
            valueLabel = chosen.label
        )
        TuneCaption("Ripples across background dots in Talk mode", Modifier.padding(top = 2.dp))
    }
}

/** Dark / Light / Auto, shared by Tune and Text and motion. */
@Composable
fun ThemeSegments(mode: ThemeMode, onTheme: (ThemeMode) -> Unit, modifier: Modifier = Modifier) {
    DotSegments(
        options = TuneModel.THEME_ORDER.map { option ->
            SegmentOption(
                TuneModel.themeLabel(option),
                when (option) {
                    ThemeMode.DARK -> R.drawable.kz_led_dark
                    ThemeMode.LIGHT -> R.drawable.kz_led_light
                    ThemeMode.SYSTEM -> R.drawable.kz_led_auto
                }
            )
        },
        selected = TuneModel.THEME_ORDER.indexOf(mode).coerceAtLeast(0),
        onSelect = { onTheme(TuneModel.THEME_ORDER[it]) },
        modifier = modifier
    )
}
