package ai.daylight.assistant.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.daylight.assistant.domain.AgentCapability
import ai.daylight.assistant.ui.agent.BotMood
import ai.daylight.assistant.ui.agent.BotSize
import ai.daylight.assistant.ui.agent.KryzzMascot

private enum class WorkflowGroup(val label: String) {
    THINK("Think & research"),
    CREATE("Create & build"),
    MEDIA("Generate media")
}

private data class WorkflowSpec(
    val capability: AgentCapability,
    val group: WorkflowGroup,
    val description: String,
    val icon: ImageVector
)

private val workflowSpecs = listOf(
    WorkflowSpec(AgentCapability.AUTO, WorkflowGroup.THINK, "Let Kryzz choose the right tools for the task.", Icons.Outlined.AutoAwesome),
    WorkflowSpec(AgentCapability.DEEP_RESEARCH, WorkflowGroup.THINK, "Investigate several angles and return a sourced synthesis.", Icons.Outlined.Search),
    WorkflowSpec(AgentCapability.WIDE_SEARCH, WorkflowGroup.THINK, "Scan broadly, compare sources, and map the landscape.", Icons.Outlined.Public),
    WorkflowSpec(AgentCapability.DOCUMENT, WorkflowGroup.CREATE, "Produce a polished document ready to save or share.", Icons.Outlined.Description),
    WorkflowSpec(AgentCapability.SPREADSHEET, WorkflowGroup.CREATE, "Build structured tables, formulas, and portable data.", Icons.Outlined.TableChart),
    WorkflowSpec(AgentCapability.DATABASE, WorkflowGroup.CREATE, "Design a production-minded SQLite database and queries.", Icons.Outlined.Storage),
    WorkflowSpec(AgentCapability.CODE, WorkflowGroup.CREATE, "Create a complete multi-file project packaged as a ZIP.", Icons.Outlined.Code),
    WorkflowSpec(AgentCapability.SKILL_MAKER, WorkflowGroup.CREATE, "Turn a repeatable workflow into a local Kryzz skill.", Icons.Outlined.Extension),
    WorkflowSpec(AgentCapability.IMAGE, WorkflowGroup.MEDIA, "Generate an image with the configured image model.", Icons.Outlined.Image),
    WorkflowSpec(AgentCapability.VIDEO, WorkflowGroup.MEDIA, "Generate a video with the configured video model.", Icons.Outlined.Movie),
    WorkflowSpec(AgentCapability.AUDIO, WorkflowGroup.MEDIA, "Produce original music tracks with OpenRouter's audio models.", Icons.Outlined.MusicNote)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentCapabilitySheet(
    selected: AgentCapability,
    onSelect: (AgentCapability) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 0.dp
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Choose workflow", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "A focused workflow gives Kryzz the right tools and output format.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                ) {
                    KryzzMascot(
                        mood = BotMood.IDLE,
                        size = BotSize.SMALL,
                        modifier = Modifier.padding(7.dp)
                    )
                }
            }

            LazyColumn(
                Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 14.dp)
            ) {
                WorkflowGroup.entries.forEach { group ->
                    item(key = "workflow_group_${group.name}") {
                        Text(
                            group.label.uppercase(),
                            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(workflowSpecs.filter { it.group == group }, key = { it.capability.name }) { spec ->
                        WorkflowRow(
                            spec = spec,
                            selected = spec.capability == selected,
                            onClick = { onSelect(spec.capability) }
                        )
                    }
                    if (group != WorkflowGroup.MEDIA) {
                        item(key = "workflow_divider_${group.name}") {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkflowRow(spec: WorkflowSpec, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .semantics { this.selected = selected }
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(14.dp),
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f),
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.68f))
        ) {
            Icon(spec.icon, contentDescription = null, modifier = Modifier.padding(11.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                spec.capability.shortLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
            Text(
                spec.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (selected) {
            Icon(
                Icons.Outlined.RadioButtonChecked,
                contentDescription = "Selected",
                modifier = Modifier.size(21.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
