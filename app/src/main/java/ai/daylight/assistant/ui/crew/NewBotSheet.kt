package ai.daylight.assistant.ui.crew

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ai.daylight.assistant.R
import ai.daylight.assistant.data.CronRecurrence
import ai.daylight.assistant.data.CronSchedulePresets
import ai.daylight.assistant.data.RoutineDraft
import ai.daylight.assistant.domain.BotPersona
import ai.daylight.assistant.ui.WeeklyDaysMenu
import ai.daylight.assistant.ui.dot.DotAvatar
import ai.daylight.assistant.ui.dot.DotButtonStyle
import ai.daylight.assistant.ui.dot.DotDialog
import ai.daylight.assistant.ui.dot.DotDialogTitle
import ai.daylight.assistant.ui.dot.DotField
import ai.daylight.assistant.ui.dot.DotPill
import ai.daylight.assistant.ui.dot.DotPillButton
import ai.daylight.assistant.ui.dot.DotRadius
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.kitTimePickerColors
import ai.daylight.assistant.ui.tune.TuneSection
import java.util.Locale
import kotlin.random.Random

/** What the bot sheet hands back: the bot's words, its face and (new bots only) a routine. */
data class BotSheetResult(val name: String, val persona: String, val seed: Int, val routine: RoutineDraft?)

/** The routine choice in the sheet: none, or one of the existing schedule presets. */
private enum class RoutineChoice(val label: String) { NONE("None"), DAILY("Daily"), WEEKLY("Weekly") }

/**
 * New bot (kit Crew "New bot"): the face (tap to shuffle), a name, what it does and how it
 * talks, and an optional first routine on the existing schedule presets. With [editing]
 * it edits the name, job and face of an existing bot; its routines live on its page.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun NewBotSheet(
    onDismiss: () -> Unit,
    onSave: (BotSheetResult) -> Unit,
    editing: Boolean = false,
    initialName: String = "",
    initialPersona: String = "",
    initialSeed: Int = remember { Random.nextInt(0, 100) },
) {
    val c = DotTheme.c
    var name by rememberSaveable { mutableStateOf(initialName) }
    var persona by rememberSaveable { mutableStateOf(initialPersona) }
    var seed by rememberSaveable { mutableIntStateOf(initialSeed) }
    var choiceName by rememberSaveable { mutableStateOf(RoutineChoice.NONE.name) }
    val choice = RoutineChoice.valueOf(choiceName)
    var routinePrompt by rememberSaveable { mutableStateOf("") }
    var hour by rememberSaveable { mutableIntStateOf(7) }
    var minute by rememberSaveable { mutableIntStateOf(0) }
    var daysCsv by rememberSaveable { mutableStateOf("1") }
    var showDays by rememberSaveable { mutableStateOf(false) }
    var showTime by rememberSaveable { mutableStateOf(false) }
    val days = daysCsv.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.toSet().ifEmpty { setOf(1) }

    val routine: RoutineDraft? = when (choice) {
        RoutineChoice.NONE -> null
        RoutineChoice.DAILY, RoutineChoice.WEEKLY -> RoutineDraft(
            title = CrewModel.routineTitle(routinePrompt),
            prompt = routinePrompt,
            recurrence = if (choice == RoutineChoice.WEEKLY) CronRecurrence.WEEKLY else CronRecurrence.DAILY,
            hour = hour,
            minute = minute,
            days = if (choice == RoutineChoice.WEEKLY) days else emptySet()
        )
    }
    val valid = BotPersona.cleanName(name).isNotEmpty() && (routine == null || routine.valid)

    DotDialog(onDismiss = onDismiss, padded = false) {
        DotDialogTitle(if (editing) "Edit bot" else "New bot", Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp))
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            // The face: drawn from the name and a seed; tapping deals a new one.
            Row(verticalAlignment = Alignment.CenterVertically) {
                DotAvatar(
                    name = BotPersona.cleanName(name).ifEmpty { "Bot" },
                    seed = seed,
                    pitch = 8.dp,
                    modifier = Modifier
                        .clip(DotRadius.Tile)
                        .clickable(role = Role.Button, onClickLabel = "Shuffle the face") { seed = Random.nextInt(0, 100) }
                        .semantics { contentDescription = "Bot face. Tap to shuffle" }
                        .padding(4.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Its face comes from its name.", style = DotType.BodySmall, color = c.textSecondary)
                    DotPillButton(
                        "Shuffle",
                        { seed = Random.nextInt(0, 100) },
                        Modifier.padding(top = 6.dp).testTag("bot_shuffle"),
                        style = DotButtonStyle.Ghost,
                        icon = R.drawable.ic_kz_retry
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            DotField(
                value = name,
                onValueChange = { name = it.take(BotPersona.NAME_MAX) },
                label = "Name",
                placeholder = "Scout",
                fieldModifier = Modifier.testTag("bot_name")
            )
            Spacer(Modifier.height(16.dp))
            DotField(
                value = persona,
                onValueChange = { persona = it.take(BotPersona.PERSONA_MAX) },
                label = "What it does",
                placeholder = "Reads my three feeds and writes a five-line brief. Plain words, always links the source.",
                singleLine = false,
                minLines = 3,
                maxLines = 6,
                supportingEnd = "${persona.length}/${BotPersona.PERSONA_MAX}",
                fieldModifier = Modifier.testTag("bot_persona")
            )
            if (!editing) {
                Spacer(Modifier.height(20.dp))
                TuneSection("Routine")
                FlowRow(
                    Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RoutineChoice.entries.forEach { option ->
                        DotPill(
                            option.label,
                            selected = choice == option,
                            onClick = {
                                choiceName = option.name
                                if (option == RoutineChoice.WEEKLY) showDays = true
                            },
                            modifier = Modifier.testTag("bot_routine_${option.name.lowercase(Locale.US)}")
                        )
                    }
                }
                if (choice == RoutineChoice.NONE) {
                    Text(
                        "A chat-only bot. You can add routines on its page later.",
                        Modifier.padding(top = 6.dp),
                        style = DotType.BodySmall,
                        color = c.textTertiary
                    )
                } else {
                    Spacer(Modifier.height(12.dp))
                    DotField(
                        value = routinePrompt,
                        onValueChange = { routinePrompt = it },
                        label = "Each run, it does",
                        placeholder = "Read my feeds and write today's brief.",
                        singleLine = false,
                        minLines = 2,
                        maxLines = 5,
                        fieldModifier = Modifier.testTag("bot_routine_prompt")
                    )
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DotPillButton(
                            String.format(Locale.US, "At %02d:%02d", hour, minute),
                            { showTime = true },
                            Modifier.testTag("bot_routine_time"),
                            style = DotButtonStyle.Ghost,
                            icon = R.drawable.ic_kz_timer
                        )
                        if (choice == RoutineChoice.WEEKLY) {
                            DotPillButton(
                                CronSchedulePresets.weekdayLabel(days),
                                { showDays = true },
                                Modifier.testTag("bot_routine_days"),
                                style = DotButtonStyle.Ghost,
                                icon = R.drawable.ic_kz_calendar
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = c.border)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
        ) {
            DotPillButton("Cancel", onDismiss, style = DotButtonStyle.Ghost)
            DotPillButton(
                if (editing) "Save" else "Create",
                { onSave(BotSheetResult(name, persona, seed, routine)) },
                Modifier.testTag("bot_save"),
                enabled = valid
            )
        }
    }

    if (showDays) {
        WeeklyDaysMenu(
            selectedDays = days,
            onToggle = { day ->
                val next = days.toMutableSet()
                if (day in next) { if (next.size > 1) next.remove(day) } else next.add(day)
                daysCsv = next.sorted().joinToString(",")
            },
            onDone = { showDays = false }
        )
    }
    if (showTime) {
        val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        DotDialog(onDismiss = { showTime = false }) {
            DotDialogTitle("Time of day")
            Spacer(Modifier.height(12.dp))
            TimePicker(state = state, modifier = Modifier.align(Alignment.CenterHorizontally), colors = kitTimePickerColors())
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                DotPillButton("Cancel", { showTime = false }, style = DotButtonStyle.Ghost)
                DotPillButton(
                    "Set",
                    {
                        hour = state.hour
                        minute = state.minute
                        showTime = false
                    },
                    Modifier.testTag("bot_time_set")
                )
            }
        }
    }
}
