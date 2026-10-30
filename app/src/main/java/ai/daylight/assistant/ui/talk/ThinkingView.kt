package ai.daylight.assistant.ui.talk

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.daylight.assistant.R
import ai.daylight.assistant.data.local.MessageEntity
import ai.daylight.assistant.domain.MessageStatus
import ai.daylight.assistant.domain.ThinkingTrace
import ai.daylight.assistant.domain.ThoughtKind
import ai.daylight.assistant.domain.ThoughtStep
import ai.daylight.assistant.domain.ToolActivityDetail
import ai.daylight.assistant.ui.dot.DotRadius
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.dot.effects.rememberDotBlink
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled
import ai.daylight.assistant.ui.toolDisplayLabel
import kotlinx.coroutines.delay

/** What the thinking row says. Pure, so the words are unit tested. */
internal object ThinkingRowModel {
    /** The tool step's words: "Searching: pixel 10 battery", or the plain tool label. */
    fun toolLabel(step: ThoughtStep): String =
        ToolActivityDetail.chipLabel(step.text, step.detail) ?: toolDisplayLabel(step.text)

    /**
     * The row's title. While nothing is written yet it is THINKING with the time so far; while
     * the answer streams, WRITING; afterwards, how long it took and how many steps it had.
     */
    fun title(steps: List<ThoughtStep>, live: Boolean, writing: Boolean, elapsedMs: Long?): String {
        val time = elapsedMs?.let(ThinkingTrace::durationLabel)
        val count = ThinkingTrace.stepCount(steps)
        val stepsPart = when (count) {
            0 -> null
            1 -> "1 STEP"
            else -> "$count STEPS"
        }
        return when {
            live && !writing -> listOfNotNull("THINKING", time).joinToString(" · ")
            live -> listOfNotNull("WRITING", stepsPart).joinToString(" · ")
            time != null -> listOfNotNull("THOUGHT FOR $time", stepsPart).joinToString(" · ")
            else -> listOfNotNull("THOUGHT", stepsPart).joinToString(" · ")
        }
    }

    /** The row is drawn while the answer is being worked out, and afterwards only if it had thinking. */
    fun shows(steps: List<ThoughtStep>, live: Boolean): Boolean = live || steps.isNotEmpty()
}

/**
 * v2.1: the answer's thinking, above the answer in Talk and Run. [working] says the chat is
 * running a turn right now. While the answer is being
 * worked out it says THINKING with a blinking dot, the seconds so far and the newest thought
 * under it; a tap opens every step: the reasoning, what it said before going to work, and
 * each tool it called. A finished answer keeps a quiet "THOUGHT FOR 12S · 3 STEPS" row.
 */
@Composable
internal fun ThinkingBlock(message: MessageEntity, working: Boolean, modifier: Modifier = Modifier) {
    val c = DotTheme.c
    val motion = LocalKryzzMotionEnabled.current
    val steps = remember(message.id, message.thinkingJson) { ThinkingTrace.decode(message.thinkingJson) }
    // A row left "streaming" by an app that was closed mid-answer is not live any more.
    val live = message.status == MessageStatus.STREAMING.name && working
    if (!ThinkingRowModel.shows(steps, live)) return
    val writing = live && message.content.isNotBlank()
    var open by rememberSaveable(message.id) { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(live) {
        while (live) {
            now = System.currentTimeMillis()
            delay(1_000L - now % 1_000L)
        }
    }
    val elapsed = if (live) (now - message.createdAt).coerceAtLeast(0L) else message.totalGenerationTimeMs
    val title = ThinkingRowModel.title(steps, live, writing, elapsed)
    val canOpen = steps.isNotEmpty()
    val latest = if (live && !open) ThinkingTrace.latestLine(steps, ThinkingRowModel::toolLabel) else null
    val blinkOn by rememberDotBlink(running = live && !writing)

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .heightIn(min = 40.dp)
                .clip(DotRadius.Pill)
                .then(if (canOpen) Modifier.clickable { open = !open } else Modifier)
                .clearAndSetSemantics {
                    contentDescription = title.lowercase().replaceFirstChar { it.uppercase() }
                    if (canOpen) {
                        role = Role.Button
                        stateDescription = if (open) "Open" else "Closed"
                        onClick(label = if (open) "Hide thinking" else "Show thinking") { open = !open; true }
                    }
                }
                .padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val dot = when {
                live && !writing -> if (blinkOn) c.accentText else c.dotOff
                live -> c.accentText
                else -> c.textTertiary
            }
            Box(Modifier.size(7.dp).background(dot, CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(title, style = DotType.Label, color = if (live) c.textSecondary else c.textTertiary, maxLines = 1)
            if (canOpen) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    painterResource(if (open) R.drawable.ic_kz_chevron_up else R.drawable.ic_kz_chevron_down),
                    null,
                    Modifier.size(14.dp),
                    tint = c.textTertiary
                )
            }
        }
        if (latest != null) {
            Text(
                latest,
                Modifier.padding(start = 17.dp, bottom = 4.dp),
                style = DotType.BodySmall,
                color = c.textTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        AnimatedVisibility(
            visible = open && canOpen,
            enter = if (motion) unrollEnter else EnterTransition.None,
            exit = if (motion) unrollExit else ExitTransition.None
        ) {
            ThinkingSteps(steps, live = live && !writing)
        }
    }
}

/** Every step, along a hairline: reasoning in grey, what was said in white, tools as mono lines. */
@Composable
private fun ThinkingSteps(steps: List<ThoughtStep>, live: Boolean) {
    val c = DotTheme.c
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(start = 3.dp, top = 4.dp, bottom = 6.dp)) {
        Box(Modifier.width(1.dp).fillMaxHeight().background(c.border))
        Column(Modifier.padding(start = 13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            steps.forEachIndexed { index, step ->
                val newest = live && index == steps.lastIndex
                when (step.kind) {
                    ThoughtKind.THINK -> Text(
                        step.text,
                        style = DotType.BodySmall,
                        color = if (newest) c.textSecondary else c.textTertiary
                    )
                    ThoughtKind.SAY -> Text(step.text, style = DotType.Body, color = c.textSecondary)
                    ThoughtKind.TOOL -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(5.dp).background(if (newest) c.accentText else c.textTertiary, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            ThinkingRowModel.toolLabel(step).uppercase(),
                            style = DotType.Caption,
                            color = c.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
