package ai.daylight.assistant.ui.run

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.daylight.assistant.R
import ai.daylight.assistant.domain.AgentPlan
import ai.daylight.assistant.domain.PlanStepStatus
import ai.daylight.assistant.domain.SubagentState
import ai.daylight.assistant.domain.SwarmPhase
import ai.daylight.assistant.domain.SwarmStatus
import ai.daylight.assistant.ui.dot.DotBar
import ai.daylight.assistant.ui.dot.DotMotion
import ai.daylight.assistant.ui.dot.DotRadius
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.dot.DottedConnector
import ai.daylight.assistant.ui.dot.effects.PlanStepMark
import ai.daylight.assistant.ui.dot.effects.StepState
import java.util.Locale

/** The numbers on the plan and swarm cards. Pure, so they are unit tested. */
object PlanCardModel {
    const val PROGRESS_DOTS = 24

    /** Plans longer than this fold to the current step and its neighbours. */
    const val FOLD_ABOVE = 5

    /** A step still marked in progress after the run ended is shown as not done. */
    fun stepState(status: PlanStepStatus, live: Boolean): StepState = when (status) {
        PlanStepStatus.DONE -> StepState.Done
        PlanStepStatus.IN_PROGRESS -> if (live) StepState.Active else StepState.Pending
        PlanStepStatus.PENDING -> StepState.Pending
    }

    /** "[ 3/5 ]": the step being worked on while live (like "RUN STEP 3 OF 5"), the done count after. */
    fun counter(plan: AgentPlan, live: Boolean): String {
        val total = plan.steps.size
        val shown = if (live && plan.doneCount < total) plan.doneCount + 1 else plan.doneCount
        return "[ $shown/$total ]"
    }

    /** Lit dots: done steps, plus half a step for the one in progress while live. */
    fun litDots(plan: AgentPlan, live: Boolean, count: Int = PROGRESS_DOTS): Int {
        val total = plan.steps.size
        if (total == 0) return 0
        val half = if (live && plan.steps.any { it.status == PlanStepStatus.IN_PROGRESS }) 0.5 else 0.0
        return kotlin.math.round((plan.doneCount + half) / total * count).toInt().coerceIn(0, count)
    }

    /**
     * The steps on show. Short plans show all; a long one shows the current step with one
     * either side until it is unfolded.
     */
    fun visibleSteps(statuses: List<PlanStepStatus>, unfolded: Boolean): IntRange {
        val total = statuses.size
        if (unfolded || total <= FOLD_ABOVE) return 0 until total
        val current = statuses.indexOfFirst { it != PlanStepStatus.DONE }.takeIf { it >= 0 } ?: (total - 1)
        val start = (current - 1).coerceIn(0, total - 3)
        return start..(start + 2)
    }

    fun subagentState(state: SubagentState): StepState = when (state) {
        SubagentState.PENDING -> StepState.Pending
        SubagentState.RUNNING -> StepState.Active
        SubagentState.DONE -> StepState.Done
        SubagentState.FAILED -> StepState.Failed
    }

    fun swarmCaption(status: SwarmStatus): String {
        val running = status.subagents.count { it.state == SubagentState.RUNNING }
        return when (status.phase) {
            SwarmPhase.PLANNING -> "Planning"
            SwarmPhase.RUNNING -> if (running > 0) "$running working" else "Queued"
            SwarmPhase.SYNTHESIZING -> "Merging"
            SwarmPhase.DONE -> "Done"
            SwarmPhase.FAILED -> "Stopped"
        }
    }

    fun swarmActive(status: SwarmStatus): Boolean =
        status.phase == SwarmPhase.PLANNING || status.phase == SwarmPhase.RUNNING || status.phase == SwarmPhase.SYNTHESIZING
}

/**
 * The kit plan card (agent-run): PLAN with a [ 3/5 ] counter, 24 progress dots, and the
 * steps joined by a dotted connector. The step in progress is white with a yellow mono
 * line of what the agent is doing; done steps are grey, waiting ones dim.
 */
@Composable
fun PlanCard(plan: AgentPlan, live: Boolean, activity: String?, modifier: Modifier = Modifier) {
    val c = DotTheme.c
    var unfolded by remember { mutableStateOf(false) }
    val visible = PlanCardModel.visibleSteps(plan.steps.map { it.status }, unfolded)
    RunCard(modifier) {
        CardHeader(
            icon = R.drawable.ic_kz_plan,
            title = "Plan",
            trailing = PlanCardModel.counter(plan, live),
            trailingLive = live,
            description = "Plan, ${plan.doneCount} of ${plan.steps.size} steps done"
        )
        Spacer(Modifier.height(14.dp))
        DotBar(
            count = PlanCardModel.PROGRESS_DOTS,
            lit = PlanCardModel.litDots(plan, live),
            on = if (live) c.accent else c.textPrimary,
            off = c.dotOff,
            dot = 6.dp,
            gap = 5.dp,
            stepMs = DotMotion.DotStep
        )
        Spacer(Modifier.height(14.dp))
        visible.forEach { index ->
            val step = plan.steps[index]
            val state = PlanCardModel.stepState(step.status, live)
            StepRow(
                title = step.title,
                state = state,
                detail = activity.takeIf { state == StepState.Active },
                connector = index != visible.last
            )
        }
        val hidden = plan.steps.size - (visible.last - visible.first + 1)
        if (hidden > 0) {
            Text(
                "( + $hidden MORE )",
                Modifier
                    .padding(start = 32.dp)
                    .clip(DotRadius.Pill)
                    .clickable(onClickLabel = "Show all steps") { unfolded = true }
                    .heightIn(min = 40.dp)
                    .padding(vertical = 12.dp),
                style = DotType.Label,
                color = c.textSecondary
            )
        }
    }
}

/** The swarm's subagents, drawn like plan steps under a SWARM header with its phase. */
@Composable
fun SwarmCard(status: SwarmStatus, modifier: Modifier = Modifier) {
    val active = PlanCardModel.swarmActive(status)
    RunCard(modifier) {
        CardHeader(
            icon = R.drawable.ic_kz_crew,
            title = "Swarm",
            trailing = PlanCardModel.swarmCaption(status).uppercase(Locale.US),
            trailingLive = active,
            description = "Swarm, ${PlanCardModel.swarmCaption(status)}"
        )
        if (status.subagents.isNotEmpty()) Spacer(Modifier.height(14.dp))
        status.subagents.forEachIndexed { index, sub ->
            StepRow(
                title = sub.title,
                state = PlanCardModel.subagentState(sub.state),
                detail = null,
                connector = index != status.subagents.lastIndex
            )
        }
        if (status.phase == SwarmPhase.FAILED) {
            Text("SWARM STOPPED", Modifier.padding(top = 4.dp), style = DotType.Label, color = DotTheme.c.error)
        }
    }
}

@Composable
private fun RunCard(modifier: Modifier, content: @Composable () -> Unit) {
    val c = DotTheme.c
    Column(
        modifier
            .fillMaxWidth()
            .clip(DotRadius.Card)
            .background(c.surface)
            .border(1.dp, c.border, DotRadius.Card)
            .padding(16.dp)
    ) { content() }
}

@Composable
private fun CardHeader(@DrawableRes icon: Int, title: String, trailing: String, trailingLive: Boolean, description: String) {
    val c = DotTheme.c
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(icon), null, Modifier.size(16.dp), tint = c.textPrimary)
        Spacer(Modifier.width(10.dp))
        Text(title.uppercase(Locale.US), Modifier.weight(1f), style = DotType.Label, color = c.textPrimary)
        Text(trailing, style = DotType.Caption, color = if (trailingLive) c.accent else c.textSecondary, maxLines = 1)
    }
}

@Composable
private fun StepRow(title: String, state: StepState, detail: String?, connector: Boolean) {
    val c = DotTheme.c
    val textColor = when (state) {
        StepState.Active -> c.textPrimary
        StepState.Done -> c.textSecondary
        StepState.Failed -> c.textPrimary
        StepState.Pending, StepState.Skipped -> c.textDisabled
    }
    val stateLabel = when (state) {
        StepState.Active -> "in progress"
        StepState.Done -> "done"
        StepState.Failed -> "failed"
        StepState.Pending -> "waiting"
        StepState.Skipped -> "skipped"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .semantics(mergeDescendants = true) { stateDescription = stateLabel }
    ) {
        Column(Modifier.width(20.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            PlanStepMark(state, Modifier.padding(top = 1.dp), size = 20.dp)
            if (connector) {
                DottedConnector(c.borderStrong, Modifier.weight(1f).padding(vertical = 3.dp).heightIn(min = 18.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(top = 1.dp, bottom = if (connector) 14.dp else 4.dp)) {
            Text(title, style = DotType.Body.copy(lineHeight = DotType.Title.lineHeight), color = textColor)
            if (detail != null) {
                Text(
                    detail.uppercase(Locale.getDefault()),
                    Modifier.padding(top = 4.dp),
                    style = DotType.Caption,
                    color = c.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (state == StepState.Failed) {
                Text("FAILED", Modifier.padding(top = 4.dp), style = DotType.Caption, color = c.error)
            }
        }
    }
}
