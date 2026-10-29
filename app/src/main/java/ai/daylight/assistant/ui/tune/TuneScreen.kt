package ai.daylight.assistant.ui.tune

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import ai.daylight.assistant.ui.GalacticAboutDialog
import ai.daylight.assistant.ui.Routes
import ai.daylight.assistant.ui.dot.DotRadius
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.effects.DotMatrixText
import ai.daylight.assistant.ui.home.HomeBoard
import ai.daylight.assistant.ui.shell.Arm
import ai.daylight.assistant.ui.shell.InkLines
import ai.daylight.assistant.ui.shell.InkStripEffect
import ai.daylight.assistant.ui.talk.DotSheet
import ai.daylight.assistant.ui.talk.OptionRow
import ai.daylight.assistant.ui.talk.SheetLabel
import ai.daylight.assistant.voice.TtsProvider

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
    val onExportChats: () -> Unit = {},
    val onImportChats: () -> Unit = {},
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
    var showAbout by rememberSaveable { mutableStateOf(false) }

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
            DotMatrixText("TUNE", pitch = 4.2.dp, on = c.textPrimary, off = Color.Transparent)
        }
        Spacer(Modifier.height(20.dp))

        TuneSection("Thinking mode")
        ThinkingModeTiles(s.agentQuality, actions.onQuality)
        TuneCaption(TuneModel.qualityCaption(s.agentQuality), Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(24.dp))

        val capCents = TuneModel.capCents(capIndex)
        val capSpoken = if (capCents <= 0) "Off" else HomeBoard.capLabel(capCents)
        TuneSection("Cost cap per run") {
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
            R.drawable.ic_kz_model, "Chat model",
            TuneModel.chatModelCaption(s.defaultModel, TuneModel.runModel(s.agentQuality, s.agentModel, s.maxModel)),
            onClick = { onOpen(Routes.models(ModelPurpose.CHAT)) }
        )
        TuneRow(
            R.drawable.ic_kz_key, "API keys", TuneModel.keyCaption(s.chatProvider, state.keys),
            onClick = { onOpen(Routes.API) }
        )
        TuneRow(
            R.drawable.ic_kz_image, "Image and video model", TuneModel.mediaCaption(s.imageModel, s.videoModel),
            onClick = { onOpen(Routes.models(ModelPurpose.IMAGE)) }
        )
        Spacer(Modifier.height(24.dp))

        TuneSection("Privacy")
        TuneRow(R.drawable.ic_kz_phone_local, "Chats stay on this phone", "No cloud copy, no analytics", TuneTrailing.None)
        TuneRow(
            R.drawable.ic_kz_web, "Allow web search", TuneModel.searchCaption(state.keys.hasParallel),
            TuneTrailing.Switch(s.searchEnabled, actions.onSearch)
        )
        TuneRow(
            R.drawable.ic_kz_memory, "Chat memory", TuneModel.memoryCaption(s.memoryEnabled, state.memoryCount),
            TuneTrailing.Switch(s.memoryEnabled, actions.onMemory)
        )
        TuneRow(
            R.drawable.ic_kz_location, "Approximate location", TuneModel.locationCaption(s.locationEnabled, s.locationLabel),
            TuneTrailing.Switch(s.locationEnabled, actions.onLocation)
        )
        TuneRow(
            R.drawable.ic_kz_lock, "Lock the app", TuneModel.lockCaption(state.biometricAvailable),
            TuneTrailing.Switch(s.biometricLock, actions.onBiometric, enabled = state.biometricAvailable)
        )
        Spacer(Modifier.height(24.dp))

        TuneSection("Appearance")
        ThemeSegments(s.themeMode, actions.onTheme, Modifier.padding(bottom = 8.dp))
        TuneRow(
            R.drawable.ic_kz_grid, "Dot background", "Talk, Run and Home",
            TuneTrailing.Switch(s.dotBackground, actions.onDotBackground)
        )
        TuneRow(
            R.drawable.ic_kz_sliders, "Text and motion", "Text size, chat density, animations",
            onClick = { onOpen(Routes.APPEARANCE) }
        )
        Spacer(Modifier.height(24.dp))

        TuneSection("More")
        TuneRow(R.drawable.ic_kz_user, "Persona", TuneModel.personaCaption(s.presetId), onClick = { onOpen(Routes.PERSONA) })
        TuneRow(
            R.drawable.ic_kz_tools, "Search and run limits",
            "Step budget ${s.stepBudget}, review ${if (s.reviewAnswers) "on" else "off"}",
            onClick = { onOpen(Routes.TOOLS) }
        )
        TuneRow(
            R.drawable.ic_kz_mic, "Voice", "Spoken by ${TtsProvider.from(s.ttsProvider).label}",
            onClick = { onOpen(Routes.VOICE) }
        )
        TuneRow(
            R.drawable.ic_kz_memory, "Memories",
            if (state.memoryCount == 1) "1 saved" else "${state.memoryCount} saved",
            onClick = { onOpen(Routes.MEMORY) }
        )
        TuneRow(R.drawable.ic_kz_skill, "Skills", "Reusable skills and tools", onClick = onSkills)
        // C10: Routines used to open from the chat drawer's Cron button, which is gone.
        TuneRow(R.drawable.ic_kz_routine, "Routines", "Prompts that run on a schedule", onClick = { onOpen(Routes.CRON) })
        TuneRow(R.drawable.ic_kz_log, "Model catalog", "Every model your key can use", onClick = onModelCatalog)
        TuneRow(R.drawable.ic_kz_shield, "Data and usage", "What is stored, clear local data", onClick = { onOpen(Routes.PRIVACY) })
        TuneRow(
            R.drawable.ic_kz_export, "Backup", state.backupNotice ?: "Export or import your chats",
            onClick = { showBackup = true }
        )
        TuneRow(R.drawable.ic_kz_info, "About", "Version ${BuildConfig.VERSION_NAME}", onClick = { showAbout = true })
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
    if (showAbout) GalacticAboutDialog(onDismiss = { showAbout = false })
}

/** Dark / Light / Auto, shared by Tune and Text and motion. */
@Composable
fun ThemeSegments(mode: ThemeMode, onTheme: (ThemeMode) -> Unit, modifier: Modifier = Modifier) {
    DotSegments(
        options = TuneModel.THEME_ORDER.map { option ->
            SegmentOption(
                TuneModel.themeLabel(option),
                when (option) {
                    ThemeMode.DARK -> R.drawable.ic_kz_moon
                    ThemeMode.LIGHT -> R.drawable.ic_kz_sun
                    ThemeMode.SYSTEM -> null
                }
            )
        },
        selected = TuneModel.THEME_ORDER.indexOf(mode).coerceAtLeast(0),
        onSelect = { onTheme(TuneModel.THEME_ORDER[it]) },
        modifier = modifier
    )
}
