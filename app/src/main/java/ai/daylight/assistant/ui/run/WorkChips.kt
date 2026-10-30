package ai.daylight.assistant.ui.run

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.daylight.assistant.R
import ai.daylight.assistant.domain.AgentExecutor
import ai.daylight.assistant.domain.AgentTurnPolicy
import ai.daylight.assistant.domain.WorkLog
import ai.daylight.assistant.domain.WorkStep
import ai.daylight.assistant.domain.WorkStepKind
import ai.daylight.assistant.ui.dot.DotRadius
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.dot.effects.ThinkingDots
import ai.daylight.assistant.ui.talk.DotSheet
import ai.daylight.assistant.ui.talk.SheetLabel
import ai.daylight.assistant.ui.talk.openUrl
import ai.daylight.assistant.ui.toolDisplayLabel
import java.util.Locale

/** The icon on a chip; mapped to a kit drawable in [chipIcon]. */
enum class ChipGlyph { CHECK, FAILED, SEARCH, WEB, FILE, IMAGE, VIDEO, AUDIO, MEMORY, PLAN, QUESTION, TOOLS }

/** One pill in the work row. The label is shown uppercase. */
data class WorkChip(val label: String, val glyph: ChipGlyph?, val running: Boolean = false, val failed: Boolean = false)

/**
 * Builds the work row under a run (kit agent-run, components.md section 4): finished steps
 * folded by kind ("SEARCH, 3", "READ, 4 PAGES", one chip per file), then a yellow-ringed chip
 * for each thing running right now. Pure, so the folding is unit tested.
 */
object WorkChips {
    /** Activities the run screen shows elsewhere: the whole-run marker and plan updates. */
    val hiddenActivities: Set<String> = setOf(
        toolDisplayLabel(AgentExecutor.TOOL_AGENT_RUNNING),
        toolDisplayLabel(AgentTurnPolicy.UPDATE_PLAN)
    )

    fun done(steps: List<WorkStep>): List<WorkChip> {
        val ok = steps.filterNot { it.failed }
        val out = mutableListOf<WorkChip>()
        val seen = mutableSetOf<WorkStepKind>()
        ok.forEach { step ->
            if (step.kind == WorkStepKind.FILE) {
                out += WorkChip("File, ${step.detail ?: step.title}", ChipGlyph.CHECK)
            } else if (seen.add(step.kind)) {
                out += WorkChip(foldedLabel(step.kind, ok.count { it.kind == step.kind }), ChipGlyph.CHECK)
            }
        }
        val failed = steps.size - ok.size
        if (failed > 0) out += WorkChip("Failed, $failed", ChipGlyph.FAILED, failed = true)
        return out
    }

    /** "SEARCH, 3"; kinds where a count means nothing drop it when there is only one. */
    internal fun foldedLabel(kind: WorkStepKind, count: Int): String {
        fun counted(base: String) = "$base, $count"
        fun single(base: String) = if (count == 1) base else "$base, $count"
        return when (kind) {
            WorkStepKind.SEARCH -> counted("Search")
            WorkStepKind.PAGE -> "Read, $count ${if (count == 1) "page" else "pages"}"
            WorkStepKind.CALCULATION -> counted("Calculate")
            WorkStepKind.WEATHER -> single("Weather")
            WorkStepKind.FILE -> counted("Files")
            WorkStepKind.MEMORY -> counted("Memory")
            WorkStepKind.PAST_CHATS -> counted("Past chats")
            WorkStepKind.TIME -> single("Time")
            WorkStepKind.SCHEDULE -> counted("Schedule")
            WorkStepKind.SKILL -> single("Skill")
            WorkStepKind.QUESTION -> single("Asked you")
            WorkStepKind.OTHER -> counted("Tools")
        }
    }

    /**
     * One running chip per live activity. While the agent is between tools (only the
     * whole-run marker is up) a single THINKING chip says it is still working.
     */
    fun running(activities: List<String>, generating: Boolean): List<WorkChip> {
        if (!generating) return emptyList()
        val live = activities.filterNot { it in hiddenActivities }
            .map { it.trim().trimEnd('…', '.').trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (live.isEmpty()) return listOf(WorkChip("Thinking", null, running = true))
        return live.map { WorkChip(it, glyphFor(it), running = true) }
    }

    /** The newest thing the agent is doing, for the plan card's yellow sub-line. */
    fun latestActivity(activities: List<String>): String? =
        activities.lastOrNull { it !in hiddenActivities }?.trim()?.trimEnd('…', '.')?.trim()?.takeIf { it.isNotEmpty() }

    fun glyphFor(activity: String): ChipGlyph {
        val a = activity.lowercase(Locale.ROOT)
        return when {
            "search" in a -> ChipGlyph.SEARCH
            a.startsWith("reading") -> ChipGlyph.WEB
            "image" in a -> ChipGlyph.IMAGE
            "video" in a -> ChipGlyph.VIDEO
            "audio" in a || "music" in a -> ChipGlyph.AUDIO
            "file" in a || "project" in a || "skill" in a || a.startsWith("creating") -> ChipGlyph.FILE
            "memor" in a || "remember" in a -> ChipGlyph.MEMORY
            "plan" in a -> ChipGlyph.PLAN
            "your answer" in a -> ChipGlyph.QUESTION
            else -> ChipGlyph.TOOLS
        }
    }
}

@DrawableRes
private fun chipIcon(glyph: ChipGlyph): Int = when (glyph) {
    ChipGlyph.CHECK -> R.drawable.ic_kz_check
    ChipGlyph.FAILED -> R.drawable.ic_kz_warning
    ChipGlyph.SEARCH -> R.drawable.ic_kz_search
    ChipGlyph.WEB -> R.drawable.ic_kz_web
    ChipGlyph.FILE -> R.drawable.ic_kz_file
    ChipGlyph.IMAGE -> R.drawable.ic_kz_image
    ChipGlyph.VIDEO -> R.drawable.ic_kz_video
    ChipGlyph.AUDIO -> R.drawable.ic_kz_voice_wave
    ChipGlyph.MEMORY -> R.drawable.ic_kz_ink
    ChipGlyph.PLAN -> R.drawable.ic_kz_plan
    ChipGlyph.QUESTION -> R.drawable.ic_kz_chat
    ChipGlyph.TOOLS -> R.drawable.ic_kz_tools
}

/**
 * The chips, wrapping with a 6dp gap. When there is a log behind them, tapping the row
 * opens it as a sheet with every step.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkChipRow(chips: List<WorkChip>, onOpenLog: (() -> Unit)?, modifier: Modifier = Modifier) {
    if (chips.isEmpty()) return
    val summary = chips.joinToString(", ") { (if (it.running) "running " else "") + it.label }
    FlowRow(
        modifier
            .fillMaxWidth()
            .then(if (onOpenLog != null) Modifier.clip(DotRadius.Tile).clickable(onClickLabel = "Show work log", onClick = onOpenLog) else Modifier)
            .clearAndSetSemantics {
                contentDescription = "Work: $summary"
                if (onOpenLog != null) {
                    role = Role.Button
                    onClick(label = "Show work log") { onOpenLog(); true }
                }
            },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        chips.forEach { WorkChipPill(it) }
    }
}

@Composable
private fun WorkChipPill(chip: WorkChip) {
    val c = DotTheme.c
    Row(
        Modifier
            .heightIn(min = 32.dp)
            .background(c.surfaceRaised, DotRadius.Pill)
            .border(1.dp, if (chip.running) c.accentText else c.border, DotRadius.Pill)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        chip.glyph?.let { glyph ->
            val tint = when {
                chip.failed -> c.error
                chip.running -> c.accentText
                else -> c.textSecondary
            }
            Icon(painterResource(chipIcon(glyph)), null, Modifier.size(if (glyph == ChipGlyph.CHECK) 12.dp else 14.dp), tint = tint)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            chip.label.uppercase(Locale.getDefault()),
            Modifier.widthIn(max = 220.dp),
            style = DotType.Caption,
            color = if (chip.running || chip.failed) c.textPrimary else c.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (chip.running) {
            Spacer(Modifier.width(8.dp))
            ThinkingDots(dot = 3.dp)
        }
    }
}

/** The whole log behind a run: every step, failures in red, pages open in the browser. */
@Composable
fun WorkLogSheet(log: WorkLog, onDismiss: () -> Unit) {
    val c = DotTheme.c
    val context = LocalContext.current
    DotSheet(onDismiss) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(painterResource(R.drawable.ic_kz_log), null, Modifier.size(14.dp), tint = c.textTertiary)
            Spacer(Modifier.width(8.dp))
            SheetLabel("Work log")
        }
        Text(log.summary, style = DotType.BodySmall, color = c.textSecondary)
        Spacer(Modifier.height(12.dp))
        log.steps.forEach { step -> WorkStepRow(step, context) }
    }
}

@Composable
private fun WorkStepRow(step: WorkStep, context: Context) {
    val c = DotTheme.c
    val url = step.url
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = c.surfaceRaised)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .then(if (url != null) Modifier.clickable(onClickLabel = "Open page") { openUrl(context, url) } else Modifier)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(if (step.failed) R.drawable.ic_kz_warning else chipIcon(stepGlyph(step.kind))),
                null,
                Modifier.size(16.dp),
                tint = if (step.failed) c.error else c.textSecondary
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(step.title, style = DotType.Body, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                step.detail?.let { detail ->
                    Text(
                        detail,
                        style = DotType.Code.copy(fontSize = DotType.Caption.fontSize),
                        color = if (step.failed) c.error else c.textTertiary,
                        maxLines = if (step.failed) 3 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (url != null) {
                Spacer(Modifier.width(8.dp))
                Icon(painterResource(R.drawable.ic_kz_external), null, Modifier.size(16.dp), tint = c.textTertiary)
            }
        }
    }
}

private fun stepGlyph(kind: WorkStepKind): ChipGlyph = when (kind) {
    WorkStepKind.SEARCH, WorkStepKind.PAST_CHATS -> ChipGlyph.SEARCH
    WorkStepKind.PAGE, WorkStepKind.WEATHER -> ChipGlyph.WEB
    WorkStepKind.FILE, WorkStepKind.SKILL -> ChipGlyph.FILE
    WorkStepKind.MEMORY -> ChipGlyph.MEMORY
    WorkStepKind.QUESTION -> ChipGlyph.QUESTION
    WorkStepKind.SCHEDULE, WorkStepKind.TIME -> ChipGlyph.PLAN
    WorkStepKind.CALCULATION, WorkStepKind.OTHER -> ChipGlyph.TOOLS
}
