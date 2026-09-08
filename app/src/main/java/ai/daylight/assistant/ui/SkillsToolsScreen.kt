package ai.daylight.assistant.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.daylight.assistant.data.local.SkillEntity
import ai.daylight.assistant.domain.AgentCapability
import ai.daylight.assistant.domain.StarterSkills
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled

private data class BuiltInTool(val capability: AgentCapability, val description: String, val icon: ImageVector)

private val builtInTools = listOf(
    BuiltInTool(AgentCapability.DEEP_RESEARCH, "Two related search passes with session continuity, source cards, and grounded synthesis.", Icons.AutoMirrored.Outlined.ManageSearch),
    BuiltInTool(AgentCapability.WIDE_SEARCH, "Scan broadly across several query angles and cite every time-sensitive claim.", Icons.Outlined.Public),
    BuiltInTool(AgentCapability.SKILL_MAKER, "Create a reusable local instruction skill and enable it for future agent tasks.", Icons.Outlined.Extension),
    BuiltInTool(AgentCapability.CODE, "Create a complete multi-file full-stack code project as a local ZIP.", Icons.Outlined.Code),
    BuiltInTool(AgentCapability.DOCUMENT, "Generate a polished document and materialize it as DOCX.", Icons.Outlined.Description),
    BuiltInTool(AgentCapability.SPREADSHEET, "Generate structured spreadsheet data and materialize it as XLSX.", Icons.Outlined.TableChart),
    BuiltInTool(AgentCapability.DATABASE, "Design SQL and materialize a usable local SQLite database.", Icons.Outlined.Storage),
    BuiltInTool(AgentCapability.IMAGE, "Generate images with the separately selected OpenRouter image model.", Icons.Outlined.Image),
    BuiltInTool(AgentCapability.VIDEO, "Generate a short video with the separately selected video model.", Icons.Outlined.Movie),
    BuiltInTool(AgentCapability.AUDIO, "Create a downloadable MP3 with the separately selected OpenRouter speech model.", Icons.Outlined.GraphicEq)
)

private data class AutoToolInfo(val name: String, val description: String, val icon: ImageVector)

private val autoTools = listOf(
    AutoToolInfo("get_current_time", "Local date, time, weekday, and timezone on this phone.", Icons.Outlined.Schedule),
    AutoToolInfo("calculate", "Exact arithmetic — no guessing percentages or exponents.", Icons.Outlined.Calculate),
    AutoToolInfo("get_weather", "Live 3-day forecast via Open-Meteo. No extra API key.", Icons.Outlined.Cloud),
    AutoToolInfo("fetch_url", "Read a public page the user pasted as a link.", Icons.Outlined.Link),
    AutoToolInfo("remember_fact / recall_memories", "Save and search durable local memories when Memory is on.", Icons.Outlined.Psychology),
    AutoToolInfo("schedule_task", "Create a daily or weekly reminder that runs in its own chat.", Icons.Outlined.Alarm)
)

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Column { Text("Tools & skills"); Text("Capabilities and local instructions", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("Built-in tools", style = MaterialTheme.typography.titleLarge)
                Text("Tap a tool to open a new Agent conversation with it selected.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            items(builtInTools, key = { it.capability.name }) { tool ->
                BuiltInToolCard(tool) { onStart(tool.capability) }
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Always-on Auto tools", style = MaterialTheme.typography.titleLarge)
                Text("Auto offers these when they help. Time and calculate are always available; the rest appear when the request matches.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            items(autoTools, key = { it.name }) { tool ->
                AutoToolCard(tool)
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Your skills", style = MaterialTheme.typography.titleLarge)
                Text("Starter skills ship enabled and activate automatically on matching Agent requests. Toggle any off. They never contain or receive API keys.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            if (skills.isEmpty()) {
                item {
                    Surface(
                        Modifier.fillMaxWidth().clickable { onStart(AgentCapability.SKILL_MAKER) },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.Extension, null, tint = MaterialTheme.colorScheme.primary)
                            Text("Create your first skill", style = MaterialTheme.typography.titleMedium)
                            Text("Open Skill maker, describe a repeatable workflow, and the agent will save it locally here.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                items(skills, key = { it.id }) { skill ->
                    SkillCard(
                        skill = skill,
                        expanded = expanded == skill.id,
                        onExpand = { expanded = if (expanded == skill.id) null else skill.id },
                        onEnabled = { vm.setEnabled(skill.id, it) },
                        onDelete = { pendingDelete = skill }
                    )
                }
            }
        }
    }

    pendingDelete?.let { skill ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${skill.name}?") },
            text = { Text("This removes the local skill from future Agent requests. Conversations are not changed.") },
            confirmButton = { TextButton(onClick = { vm.delete(skill.id); pendingDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun BuiltInToolCard(tool: BuiltInTool, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f))
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                Icon(tool.icon, null, Modifier.padding(10.dp).size(22.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(tool.capability.title, style = MaterialTheme.typography.titleSmall)
                Text(tool.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.PlayArrow, "Start ${tool.capability.title}", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun AutoToolCard(tool: AutoToolInfo) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f))
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                Icon(tool.icon, null, Modifier.padding(10.dp).size(22.dp), tint = MaterialTheme.colorScheme.secondary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(tool.name, style = MaterialTheme.typography.titleSmall)
                Text(tool.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SkillCard(skill: SkillEntity, expanded: Boolean, onExpand: () -> Unit, onEnabled: (Boolean) -> Unit, onDelete: () -> Unit) {
    val motionEnabled = LocalKryzzMotionEnabled.current
    Card(
        Modifier
            .fillMaxWidth()
            .animateContentSize(if (motionEnabled) tween(150) else snap())
            .clickable(onClick = onExpand),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (expanded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f) else MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (expanded) 0.90f else 0.48f))
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(skill.name, style = MaterialTheme.typography.titleMedium)
                        if (StarterSkills.isStarterId(skill.id)) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ) {
                                Text("Starter", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                    Text(skill.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (expanded) 5 else 2, overflow = TextOverflow.Ellipsis)
                }
                Switch(skill.enabled, onCheckedChange = onEnabled)
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete skill") }
            }
            if (expanded) {
                HorizontalDivider()
                Text("Instructions", style = MaterialTheme.typography.labelLarge)
                Text(skill.instructions, style = MaterialTheme.typography.bodySmall)
                if (skill.examplePrompts.isNotBlank()) {
                    Text("Try asking", style = MaterialTheme.typography.labelLarge)
                    skill.examplePrompts.lineSequence().forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}
