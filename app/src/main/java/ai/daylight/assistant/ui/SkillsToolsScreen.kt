package ai.daylight.assistant.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.daylight.assistant.R
import ai.daylight.assistant.data.local.SkillEntity
import ai.daylight.assistant.domain.AgentCapability
import ai.daylight.assistant.domain.StarterSkills
import ai.daylight.assistant.ui.dot.DotConfirmDialog
import ai.daylight.assistant.ui.dot.DotIconButton
import ai.daylight.assistant.ui.dot.DotTag
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.dot.effects.DotToggle
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled
import ai.daylight.assistant.ui.tune.TuneDetail
import ai.daylight.assistant.ui.tune.TunePage
import ai.daylight.assistant.ui.tune.TuneRow
import ai.daylight.assistant.ui.tune.TuneSection
import ai.daylight.assistant.ui.tune.TuneTrailing

private data class BuiltInTool(val capability: AgentCapability, val description: String, @DrawableRes val icon: Int)

private val builtInTools = listOf(
    BuiltInTool(AgentCapability.DEEP_RESEARCH, "Two related search passes with session continuity, source cards, and grounded synthesis.", R.drawable.ic_kz_search),
    BuiltInTool(AgentCapability.WIDE_SEARCH, "Scan broadly across several query angles and cite every time-sensitive claim.", R.drawable.ic_kz_web),
    BuiltInTool(AgentCapability.SKILL_MAKER, "Create a reusable local instruction skill and enable it for future agent tasks.", R.drawable.ic_kz_skill),
    BuiltInTool(AgentCapability.CODE, "Create a complete multi-file full-stack code project as a local ZIP.", R.drawable.ic_kz_code),
    BuiltInTool(AgentCapability.DOCUMENT, "Generate a polished document and materialize it as DOCX.", R.drawable.ic_kz_file_docx),
    BuiltInTool(AgentCapability.SPREADSHEET, "Generate structured spreadsheet data and materialize it as XLSX.", R.drawable.ic_kz_file_xlsx),
    BuiltInTool(AgentCapability.DATABASE, "Design SQL and materialize a usable local SQLite database.", R.drawable.ic_kz_grid),
    BuiltInTool(AgentCapability.IMAGE, "Generate images with the separately selected OpenRouter image model.", R.drawable.ic_kz_image),
    BuiltInTool(AgentCapability.VIDEO, "Generate a short video with the separately selected video model.", R.drawable.ic_kz_video),
    BuiltInTool(AgentCapability.AUDIO, "Create a downloadable MP3 with the separately selected OpenRouter speech model.", R.drawable.ic_kz_voice_wave)
)

private data class AutoToolInfo(val name: String, val description: String, @DrawableRes val icon: Int)

private val autoTools = listOf(
    AutoToolInfo("get_current_time", "Local date, time, weekday, and timezone on this phone.", R.drawable.ic_kz_timer),
    AutoToolInfo("calculate", "Exact arithmetic, no guessing percentages or exponents.", R.drawable.ic_kz_cpu_load),
    AutoToolInfo("get_weather", "Live 3-day forecast via Open-Meteo. No extra API key.", R.drawable.ic_kz_sun),
    AutoToolInfo("fetch_url", "Read a public page the user pasted as a link.", R.drawable.ic_kz_link),
    AutoToolInfo("remember_fact / recall_memories", "Save and search durable local memories when Memory is on.", R.drawable.ic_kz_memory),
    AutoToolInfo("schedule_task", "Create a daily or weekly reminder that runs in its own chat.", R.drawable.ic_kz_routine)
)

/**
 * Skills: the built-in tools (tap one to start a Run with it), the always-on Auto tools,
 * and your own skills with a switch, delete, and the instructions unfolded on tap.
 */
@Composable
fun SkillsToolsScreen(
    vm: SkillsToolsViewModel,
    onBack: () -> Unit,
    onChats: () -> Unit,
    onLlms: () -> Unit,
    onSettings: () -> Unit,
    onStart: (AgentCapability) -> Unit
) {
    val skills by vm.skills.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<SkillEntity?>(null) }

    TunePage("Skills", onBack, scrollable = false) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp)) {
            item {
                TuneSection("Built-in tools")
                TuneDetail("Tap a tool to start a new Run with it selected.", Modifier.padding(bottom = 10.dp))
            }
            items(builtInTools, key = { it.capability.name }) { tool ->
                TuneRow(
                    tool.icon,
                    tool.capability.title,
                    detail = tool.description,
                    onClick = { onStart(tool.capability) },
                    modifier = Modifier.semantics { contentDescription = "Start ${tool.capability.title}" }
                )
            }
            item {
                Spacer(Modifier.height(28.dp))
                TuneSection("Always-on Auto tools")
                TuneDetail(
                    "Auto offers these when they help. Time and calculate are always there; the rest join when a request matches.",
                    Modifier.padding(bottom = 10.dp)
                )
            }
            items(autoTools, key = { it.name }) { tool ->
                TuneRow(tool.icon, tool.name, detail = tool.description, trailing = TuneTrailing.None)
            }
            item {
                Spacer(Modifier.height(28.dp))
                TuneSection("Your skills")
                TuneDetail(
                    "Starter skills ship on and join matching Run requests by themselves. Turn any off. Skills never hold or receive API keys.",
                    Modifier.padding(bottom = 10.dp)
                )
            }
            if (skills.isEmpty()) {
                item {
                    TuneRow(
                        R.drawable.ic_kz_plus,
                        "Create your first skill",
                        detail = "Open Skill maker, describe a repeatable workflow, and Kryzz saves it here.",
                        onClick = { onStart(AgentCapability.SKILL_MAKER) }
                    )
                }
            } else {
                items(skills, key = { it.id }) { skill ->
                    SkillRow(
                        skill = skill,
                        expanded = expanded == skill.id,
                        onExpand = { expanded = if (expanded == skill.id) null else skill.id },
                        onEnabled = { vm.setEnabled(skill.id, it) },
                        onDelete = { pendingDelete = skill }
                    )
                }
            }
            item { HorizontalDivider(thickness = 1.dp, color = DotTheme.c.surfaceRaised) }
        }
    }

    pendingDelete?.let { skill ->
        DotConfirmDialog(
            title = "Delete ${skill.name}?",
            body = "Future Run requests stop using this skill. Your conversations stay as they are.",
            confirm = "Delete",
            destructive = true,
            onConfirm = {
                vm.delete(skill.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

/** A skill: name (and a STARTER tag), its description, a switch and delete; tap to unfold. */
@Composable
private fun SkillRow(skill: SkillEntity, expanded: Boolean, onExpand: () -> Unit, onEnabled: (Boolean) -> Unit, onDelete: () -> Unit) {
    val c = DotTheme.c
    val motionEnabled = LocalKryzzMotionEnabled.current
    Column(Modifier.fillMaxWidth().animateContentSize(if (motionEnabled) tween(150) else snap())) {
        HorizontalDivider(thickness = 1.dp, color = c.surfaceRaised)
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onExpand)
                .semantics { stateDescription = if (expanded) "Instructions shown" else "Instructions hidden" }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        skill.name,
                        Modifier.weight(1f, fill = false),
                        style = DotType.Body.copy(fontSize = 15.sp, lineHeight = 19.sp),
                        color = if (skill.enabled) c.textPrimary else c.textTertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (StarterSkills.isStarterId(skill.id)) DotTag("Starter")
                }
                Text(
                    skill.description,
                    Modifier.padding(top = 3.dp),
                    style = DotType.BodySmall,
                    color = c.textTertiary,
                    maxLines = if (expanded) 5 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                Modifier
                    .size(56.dp, 48.dp)
                    .toggleable(value = skill.enabled, role = Role.Switch, onValueChange = onEnabled)
                    .semantics { contentDescription = "Use ${skill.name}" },
                contentAlignment = Alignment.Center
            ) {
                DotToggle(checked = skill.enabled, onChange = null)
            }
            DotIconButton(R.drawable.ic_kz_trash, "Delete skill", onDelete, tint = c.textTertiary, iconSize = 18.dp)
        }
        if (expanded) {
            Column(Modifier.fillMaxWidth().padding(bottom = 14.dp, end = 8.dp)) {
                Text("INSTRUCTIONS", style = DotType.Label, color = c.textSecondary)
                Text(skill.instructions, Modifier.padding(top = 6.dp), style = DotType.BodySmall, color = c.textPrimary)
                if (skill.examplePrompts.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text("TRY ASKING", style = DotType.Label, color = c.textSecondary)
                    skill.examplePrompts.lineSequence().filter { it.isNotBlank() }.forEach {
                        Row(Modifier.padding(top = 6.dp)) {
                            Text("→", style = DotType.Data, color = c.textTertiary)
                            Spacer(Modifier.width(8.dp))
                            Text(it, style = DotType.BodySmall, color = c.textSecondary)
                        }
                    }
                }
            }
        }
    }
}
