package ai.daylight.assistant.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.daylight.assistant.R
import ai.daylight.assistant.domain.AgentCapability
import ai.daylight.assistant.ui.dot.DotRadius
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.talk.DotSheet
import ai.daylight.assistant.ui.talk.SheetLabel

private enum class WorkflowGroup(val label: String) {
    THINK("Think & research"),
    CREATE("Create & build"),
    MEDIA("Generate media")
}

private data class WorkflowSpec(
    val capability: AgentCapability,
    val group: WorkflowGroup,
    val description: String,
    @DrawableRes val icon: Int
)

private val workflowSpecs = listOf(
    WorkflowSpec(AgentCapability.AUTO, WorkflowGroup.THINK, "Let Kryzz choose the right tools for the task.", R.drawable.ic_kz_agent),
    WorkflowSpec(AgentCapability.DEEP_RESEARCH, WorkflowGroup.THINK, "Investigate several angles and return a sourced synthesis.", R.drawable.ic_kz_search),
    WorkflowSpec(AgentCapability.WIDE_SEARCH, WorkflowGroup.THINK, "Scan broadly, compare sources, and map the landscape.", R.drawable.ic_kz_web),
    WorkflowSpec(AgentCapability.DOCUMENT, WorkflowGroup.CREATE, "Produce a polished document ready to save or share.", R.drawable.ic_kz_file_docx),
    WorkflowSpec(AgentCapability.SPREADSHEET, WorkflowGroup.CREATE, "Build structured tables, formulas, and portable data.", R.drawable.ic_kz_file_xlsx),
    WorkflowSpec(AgentCapability.DATABASE, WorkflowGroup.CREATE, "Design a production-minded SQLite database and queries.", R.drawable.ic_kz_grid),
    WorkflowSpec(AgentCapability.CODE, WorkflowGroup.CREATE, "Create a complete multi-file project packaged as a ZIP.", R.drawable.ic_kz_file_code),
    WorkflowSpec(AgentCapability.SKILL_MAKER, WorkflowGroup.CREATE, "Turn a repeatable workflow into a local Kryzz skill.", R.drawable.ic_kz_skill),
    WorkflowSpec(AgentCapability.IMAGE, WorkflowGroup.MEDIA, "Generate an image with the configured image model.", R.drawable.ic_kz_image),
    WorkflowSpec(AgentCapability.VIDEO, WorkflowGroup.MEDIA, "Generate a video with the configured video model.", R.drawable.ic_kz_video),
    WorkflowSpec(AgentCapability.AUDIO, WorkflowGroup.MEDIA, "Produce original music tracks with OpenRouter's audio models.", R.drawable.ic_kz_voice_wave)
)

/**
 * The workflow picker as a kit sheet: grouped 64dp rows with an icon tile, the name and a
 * line of what it does. The chosen row has a white outline and a filled radio.
 */
@Composable
fun AgentCapabilitySheet(
    selected: AgentCapability,
    onSelect: (AgentCapability) -> Unit,
    onDismiss: () -> Unit
) {
    val c = DotTheme.c
    DotSheet(onDismiss) {
        Text("Choose a workflow", style = DotType.Headline, color = c.textPrimary)
        Text(
            "A focused workflow gives Kryzz the right tools and output format.",
            Modifier.padding(top = 4.dp),
            style = DotType.BodySmall,
            color = c.textSecondary
        )
        WorkflowGroup.entries.forEach { group ->
            Spacer(Modifier.height(20.dp))
            SheetLabel(group.label)
            workflowSpecs.filter { it.group == group }.forEach { spec ->
                WorkflowRow(spec, selected = spec.capability == selected) { onSelect(spec.capability) }
            }
        }
    }
}

@Composable
private fun WorkflowRow(spec: WorkflowSpec, selected: Boolean, onClick: () -> Unit) {
    val c = DotTheme.c
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .heightIn(min = 64.dp)
            .clip(DotRadius.Tile)
            .border(1.dp, if (selected) c.textPrimary else c.surface, DotRadius.Tile)
            .semantics { this.selected = selected }
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).background(c.surfaceHigh, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(spec.icon), null, Modifier.size(22.dp), tint = c.textPrimary)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(spec.capability.shortLabel, style = DotType.Body.copy(fontSize = 16.sp), color = c.textPrimary)
            Text(spec.description, Modifier.padding(top = 2.dp), style = DotType.BodySmall, color = c.textSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier.size(20.dp).border(1.5.dp, if (selected) c.textPrimary else c.borderStrong, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Box(Modifier.size(10.dp).background(c.textPrimary, CircleShape))
        }
    }
}
