package ai.daylight.assistant.ui.link

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ai.daylight.assistant.R
import ai.daylight.assistant.ui.dot.DotGrey
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.dot.dashedBorder
import ai.daylight.assistant.ui.dot.effects.DotMatrixText
import ai.daylight.assistant.ui.shell.Arm
import ai.daylight.assistant.ui.shell.InkLines
import ai.daylight.assistant.ui.shell.InkStripEffect

/** One step of the imagined Essential flow. */
internal data class LinkStep(@DrawableRes val icon: Int, val title: String, val detail: String, val owner: String)

internal val LINK_FLOW = listOf(
    LinkStep(R.drawable.ic_kz_capture_dot, "Capture", "Key press saves a screenshot, photo or voice note on the phone", "Phone"),
    LinkStep(R.drawable.ic_kz_folder_dot, "Essential Space", "Captures are kept and organised there", "Nothing app"),
    LinkStep(R.drawable.ic_kz_ink_dot, "Kryzz ink", "Summarise it, turn it into a task, or hand it to a bot", "Kryzz")
)

/**
 * Link (kit `essential`): a future project, stamped COMING LATER. It is a static page with
 * no network or Intent code and nothing that looks like it works: the arm title is drawn
 * unlit, every border is dashed, and the toggle is disabled with the reason beside it.
 */
@Composable
fun LinkScreen() {
    val c = DotTheme.c
    InkStripEffect(InkLines.LINK, arm = Arm.LINK)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 24.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(84.dp)) {
            DotMatrixText(
                "LINK",
                pitch = 4.2.dp,
                on = c.textDisabled,
                off = Color.Transparent,
                contentDescription = "Link"
            )
            ComingLaterStamp(Modifier.align(Alignment.TopEnd).padding(top = 6.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "A bridge from Essential Space into Kryzz",
            style = DotType.Headline.copy(fontSize = 26.sp, lineHeight = 31.sp, letterSpacing = (-0.02).em),
            color = c.textPrimary
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "The idea: what you capture with the Essential Key lands in Kryzz as ink you can act on. None of this is connected today.",
            style = DotType.Body,
            color = c.textSecondary
        )
        Spacer(Modifier.height(24.dp))
        LINK_FLOW.forEachIndexed { i, step ->
            FlowNode(step)
            if (i < LINK_FLOW.lastIndex) FlowConnector()
        }
        Spacer(Modifier.height(28.dp))
        Text("PREVIEW, EXAMPLE ONLY", style = DotType.Caption, color = c.textDisabled)
        Spacer(Modifier.height(8.dp))
        PreviewRow()
        Spacer(Modifier.height(24.dp))
        HorizontalDivider(thickness = 1.dp, color = c.surfaceRaised)
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Connect Essential Space",
                    style = DotType.Title.copy(fontSize = 16.sp),
                    color = c.textTertiary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "NEEDS AN OFFICIAL WAY FOR APPS TO READ CAPTURES",
                    style = DotType.Caption,
                    color = c.textDisabled
                )
            }
            Spacer(Modifier.width(16.dp))
            DisabledDashedToggle()
        }
    }
}

/** The rotated, dashed yellow stamp. */
@Composable
private fun ComingLaterStamp(modifier: Modifier = Modifier) {
    val c = DotTheme.c
    Column(
        modifier
            .rotate(-4f)
            .dashedBorder(c.accent, width = 1.5.dp, radius = 8.dp, dash = 4.dp, gap = 3.dp)
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp)
            .semantics(mergeDescendants = true) {}
    ) {
        DotMatrixText("COMING LATER", pitch = 2.6.dp, on = c.accent, off = Color.Transparent, contentDescription = "Coming later")
        Spacer(Modifier.height(6.dp))
        Text(
            "FUTURE PROJECT, NOT BUILT YET",
            style = DotType.Caption.copy(fontSize = 9.sp, letterSpacing = 0.14.em),
            color = c.accentText
        )
    }
}

@Composable
private fun FlowNode(step: LinkStep) {
    val c = DotTheme.c
    Row(
        Modifier
            .fillMaxWidth()
            .dashedBorder(c.border, radius = 16.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier.size(44.dp).background(c.surface, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(step.icon), null, Modifier.size(22.dp), tint = c.textDisabled)
        }
        Column(Modifier.weight(1f)) {
            Text(step.title, style = DotType.Title.copy(fontSize = 16.sp, lineHeight = 20.sp), color = DotGrey.G600)
            Spacer(Modifier.height(3.dp))
            Text(step.detail, style = DotType.BodySmall, color = c.textDisabled)
        }
        Text(step.owner.uppercase(), style = DotType.Caption.copy(fontSize = 9.sp), color = c.textDisabled)
    }
}

/** Five 3dp dots between flow nodes, under the icon tiles. */
@Composable
private fun FlowConnector() {
    val c = DotTheme.c
    Column(
        Modifier.padding(start = 37.dp, top = 6.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(5) { Box(Modifier.size(3.dp).background(c.borderStrong, CircleShape)) }
    }
}

@Composable
private fun PreviewRow() {
    val c = DotTheme.c
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.surface, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(36.dp).dashedBorder(c.borderStrong, radius = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(R.drawable.ic_kz_voice_wave), null, Modifier.size(18.dp), tint = c.textDisabled)
        }
        Column(Modifier.weight(1f)) {
            Text("Voice note, 0:42", style = DotType.Title.copy(fontSize = 16.sp, lineHeight = 20.sp), color = DotGrey.G600)
            Spacer(Modifier.height(3.dp))
            Text("would become: 3 to-dos for a Crew bot", style = DotType.BodySmall, color = c.textDisabled)
        }
        Icon(painterResource(R.drawable.ic_kz_arrow_right), null, Modifier.size(18.dp), tint = c.borderStrong)
    }
}

/** A switch that is plainly off and unavailable: dashed track, dim knob, no click. */
@Composable
private fun DisabledDashedToggle() {
    val c = DotTheme.c
    Box(
        Modifier
            .size(width = 44.dp, height = 26.dp)
            .dashedBorder(c.borderStrong, radius = 13.dp)
            .clearAndSetSemantics {
                contentDescription = "Connect Essential Space"
                role = Role.Switch
                stateDescription = "Off, not available yet"
                disabled()
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(c.surfaceHigh, radius = 7.dp.toPx(), center = Offset(13.dp.toPx(), size.height / 2f))
        }
    }
}
