package ai.daylight.assistant.ui.crew

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.daylight.assistant.R
import ai.daylight.assistant.data.CronSchedulePresets
import ai.daylight.assistant.data.local.ScheduledTaskEntity
import ai.daylight.assistant.ui.dot.DotIconButton
import ai.daylight.assistant.ui.dot.DotRadius
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.dot.dashedBorder
import ai.daylight.assistant.ui.dot.effects.DotToggle
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.delay

/** The kit's live mark: a yellow ring with a yellow dot inside (`.lv`). */
@Composable
internal fun LiveRing(modifier: Modifier = Modifier, size: Dp = 12.dp) {
    val accent = DotTheme.c.accentText
    Canvas(modifier.size(size).clearAndSetSemantics { }) {
        val stroke = 1.5.dp.toPx()
        val r = this.size.minDimension / 2f
        drawCircle(accent, r - stroke / 2f, style = Stroke(stroke))
        drawCircle(accent, r - stroke - 1.5.dp.toPx())
    }
}

/** A hollow 7dp dot (`.q0`): idle. */
@Composable
private fun HollowDot(modifier: Modifier = Modifier) {
    val ring = DotTheme.c.textDisabled
    Canvas(modifier.size(7.dp).clearAndSetSemantics { }) {
        val stroke = 1.dp.toPx()
        drawCircle(ring, this.size.minDimension / 2f - stroke / 2f, Offset(this.size.width / 2f, this.size.height / 2f), style = Stroke(stroke))
    }
}

/** A bot's status line: live ring and yellow words while it works, else a mark and grey words. */
@Composable
internal fun BotStatusLine(status: BotStatus, modifier: Modifier = Modifier) {
    val c = DotTheme.c
    val colour = when (status.kind) {
        BotStatusKind.RUNNING -> c.accentText
        BotStatusKind.NEXT -> c.textSecondary
        BotStatusKind.IDLE -> c.textTertiary
    }
    Row(
        modifier.semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
            when (status.kind) {
                BotStatusKind.RUNNING -> LiveRing(size = 11.dp)
                BotStatusKind.NEXT -> Icon(painterResource(R.drawable.ic_kz_routine), null, Modifier.size(14.dp), tint = c.textSecondary)
                BotStatusKind.IDLE -> HollowDot()
            }
        }
        Text(
            status.text.uppercase(Locale.US),
            style = DotType.Caption.copy(fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.1.sp),
            color = colour,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** A routine as a 26dp pill: "DAILY 07:00". A paused routine is dashed and says so. */
@Composable
internal fun RoutinePill(task: ScheduledTaskEntity, modifier: Modifier = Modifier) {
    val c = DotTheme.c
    val label = CrewModel.routinePill(task) + if (task.enabled) "" else ", paused"
    val outline = if (task.enabled) Modifier.border(1.dp, c.border, DotRadius.Pill) else Modifier.dashedBorder(c.borderStrong, radius = 13.dp)
    Row(
        modifier
            .height(26.dp)
            .then(outline)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(painterResource(R.drawable.ic_kz_routine), null, Modifier.size(13.dp), tint = c.textTertiary)
        Text(
            label.uppercase(Locale.US),
            style = DotType.Label.copy(fontSize = 10.sp),
            color = if (task.enabled) c.textSecondary else c.textTertiary,
            maxLines = 1
        )
    }
}

/**
 * One routine on the bot's Routines tab: title, schedule, a switch, last and next run,
 * Run now and delete. The same jobs as Tune's Routines page.
 */
@Composable
internal fun BotRoutineRow(
    task: ScheduledTaskEntity,
    zone: ZoneId,
    now: Long,
    onEnabled: (Boolean) -> Unit,
    onRunNow: () -> Unit,
    onDelete: () -> Unit,
    running: Boolean,
) {
    val c = DotTheme.c
    val next = remember(task, now) { if (task.enabled) CronSchedulePresets.nextRunAt(task, now) else null }
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = c.surfaceRaised)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = DotType.Body.copy(fontSize = 15.sp, lineHeight = 19.sp),
                    color = if (task.enabled) c.textPrimary else c.textTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    CrewModel.routinePill(task).uppercase(Locale.US),
                    Modifier.padding(top = 1.dp),
                    style = DotType.Caption.copy(fontSize = 10.sp, lineHeight = 14.sp),
                    color = c.textTertiary
                )
            }
            Box(
                Modifier
                    .size(56.dp, 48.dp)
                    .toggleable(value = task.enabled, role = Role.Switch, onValueChange = onEnabled)
                    .semantics { contentDescription = "Run ${task.title} on schedule" },
                contentAlignment = Alignment.CenterEnd
            ) {
                DotToggle(checked = task.enabled, onChange = null)
            }
        }
        Text(
            task.prompt,
            Modifier.padding(top = 6.dp, end = 8.dp),
            style = DotType.BodySmall,
            color = if (task.enabled) c.textSecondary else c.textTertiary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (running) {
                    BotStatusLine(BotStatus(BotStatusKind.RUNNING, "Running now"))
                } else {
                    Text(
                        (task.lastRunAt?.let { "Last run ${CrewModel.whenLabel(it, now, zone)}" } ?: "Never run").uppercase(Locale.US),
                        style = DotType.Caption.copy(fontSize = 10.sp),
                        color = c.textTertiary
                    )
                    if (next != null) {
                        Text(
                            "Next run ${CrewModel.whenLabel(next, now, zone)}".uppercase(Locale.US),
                            style = DotType.Caption.copy(fontSize = 10.sp),
                            color = c.textPrimary
                        )
                    }
                }
            }
            DotIconButton(R.drawable.ic_kz_play, "Run ${task.title} now", onRunNow, iconSize = 18.dp, enabled = !running)
            DotIconButton(R.drawable.ic_kz_trash, "Delete ${task.title}", onDelete, tint = c.textTertiary, iconSize = 18.dp)
        }
    }
}

/** A thin rule between Crew rows (#1C1C1C snaps to SurfaceRaised). */
@Composable
internal fun CrewRule(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 1.dp, color = DotTheme.c.surfaceRaised)
}

/** The time now, moved on each minute while the screen is shown (for "next run 18:00"). */
@Composable
internal fun rememberMinuteClock(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000L - value % 60_000L)
            value = System.currentTimeMillis()
        }
    }
    return now
}
