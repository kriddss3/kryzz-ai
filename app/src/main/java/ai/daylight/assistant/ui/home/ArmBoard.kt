package ai.daylight.assistant.ui.home

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ai.daylight.assistant.R
import ai.daylight.assistant.ui.dot.DotMotion
import ai.daylight.assistant.ui.dot.DotPalette
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.dot.MonoFamily
import ai.daylight.assistant.ui.dot.dashedBorder
import ai.daylight.assistant.ui.dot.effects.rememberDotTicker
import ai.daylight.assistant.ui.dot.mascot.SquidPose
import ai.daylight.assistant.ui.dot.mascot.SquidSprite
import ai.daylight.assistant.ui.dot.mascot.SquidWave
import ai.daylight.assistant.ui.dot.mascot.drawSquid
import ai.daylight.assistant.ui.shell.Arm
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Board geometry from kit/screens/src/s_home.py, in dp. The mockup is 372dp wide inside
 * its margins; on a narrower screen everything scales with the squid's LED pitch (7dp at
 * full size). Port rows are 66dp and give up to 10dp each when the screen is short; below
 * that the board scrolls.
 */
internal data class BoardGeometry(val pitch: Float, val rowHeight: Float) {
    val scale: Float get() = pitch / FULL_PITCH
    val squidTop: Float get() = TOP_GAP
    val squidWidth: Float get() = pitch * SquidSprite.COLS
    val squidHeight: Float get() = pitch * SquidSprite.ROWS

    /** Cables start just under the sprite's last row. */
    val cableTop: Float get() = squidTop + squidHeight + 4f * scale
    val portsTop: Float get() = squidTop + squidHeight + 30f * scale

    /** The port node ring, the end of the horizontal cable runs, the port text and the date block. */
    val nodeX: Float get() = squidWidth + 3f * scale
    val cableEndX: Float get() = nodeX - 8f * scale
    val portTextX: Float get() = squidWidth + 17f * scale
    val sideX: Float get() = squidWidth + 25f * scale
    val sideTop: Float get() = squidTop + 10f * scale
    val height: Float get() = portsTop + Arm.entries.size * rowHeight + BOTTOM_GAP

    fun portTop(i: Int): Float = portsTop + i * rowHeight
    fun portCentre(i: Int): Float = portTop(i) + rowHeight / 2f

    /** Port [i]'s arm column: the rightmost arm feeds the top port, so cables never cross. */
    fun cableColumn(i: Int): Int = SquidSprite.armBaseColumns[SquidSprite.armBaseColumns.lastIndex - i]
    fun cableX(i: Int): Float = cableColumn(i) * pitch + pitch / 2f

    /** Cable dots for port [i]: down from the sprite, then across to the node, one per pitch. */
    fun cableDots(i: Int): List<Offset> {
        val x = cableX(i)
        val yc = portCentre(i)
        val dots = ArrayList<Offset>(48)
        var y = cableTop
        while (y < yc) { dots += Offset(x, y); y += pitch }
        var xx = x
        while (xx <= cableEndX) { dots += Offset(xx, yc); xx += pitch }
        return dots
    }

    companion object {
        const val FULL_PITCH = 7f
        const val MIN_PITCH = 5f
        const val MOCK_WIDTH = 372f
        const val ROW = 66f
        const val MIN_ROW = 56f
        const val TOP_GAP = 20f
        const val BOTTOM_GAP = 12f

        /** Geometry for a board [width] x [viewportHeight] dp. */
        fun compute(width: Float, viewportHeight: Float): BoardGeometry {
            val pitch = (width * FULL_PITCH / MOCK_WIDTH).coerceIn(MIN_PITCH, FULL_PITCH)
            val probe = BoardGeometry(pitch, ROW)
            val fit = (viewportHeight - probe.portsTop - BOTTOM_GAP) / Arm.entries.size
            return BoardGeometry(pitch, fit.coerceIn(MIN_ROW, ROW))
        }

        /**
         * Busy cables carry yellow runs: 3 lit dots in every 9. Each step of [phase] moves the
         * runs one dot along, from the squid towards the port.
         */
        fun busyDotLit(index: Int, phase: Int = 0): Boolean = Math.floorMod(index - phase, 9) < 3
    }
}

/** The board's stepped motion (phase D). One clock drives the cables and the squid. */
internal object BoardMotion {
    /** Lit dots move one dot along a busy cable per step (kit DotMotion.dotStep). */
    const val STEP_MS = DotMotion.DotStep

    /** The squid shows a new wave frame every this many cable steps (140ms). */
    const val SQUID_EVERY = SquidWave.FRAME_MS / DotMotion.DotStep

    /** A finished port lights up once for this long. */
    const val FLASH_MS = 280L

    fun squidStep(tick: Int): Int = tick / SQUID_EVERY

    /** Arms whose port was busy in [before] and is idle in [now]: their work just finished. */
    fun finished(before: Map<Arm, PortState>, now: List<ArmPort>): Set<Arm> =
        now.filter { it.state == PortState.IDLE && before[it.arm] == PortState.BUSY }.map { it.arm }.toSet()
}

@DrawableRes
internal fun armIcon(arm: Arm): Int = when (arm) {
    Arm.TALK -> R.drawable.ic_kz_chat
    Arm.MAKE -> R.drawable.ic_kz_image
    Arm.FIND -> R.drawable.ic_kz_search
    Arm.CREW -> R.drawable.ic_kz_crew
    Arm.RUN -> R.drawable.ic_kz_plan
    Arm.VOICE -> R.drawable.ic_kz_voice_wave
    Arm.LINK -> R.drawable.ic_kz_link_future
    Arm.TUNE -> R.drawable.ic_kz_settings
}

/**
 * The arm board: the squid top left with its eight arms running on as dotted cables that
 * step down into eight ports, and the date and a status sentence beside it. One Canvas
 * draws the squid, cables and nodes; the ports are ordinary tappable rows on top.
 *
 * Motion (phase D), one effect on one clock: while anything runs, yellow dots step along
 * the busy cables and the squid waves its arms; when a job finishes its port flashes once.
 */
@Composable
fun ArmBoard(
    ports: List<ArmPort>,
    dateTitle: String,
    monthCaption: String,
    status: String,
    onOpen: (Arm) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = DotTheme.c
    val density = LocalDensity.current
    val motion = LocalKryzzMotionEnabled.current
    val anyBusy = ports.any { it.state == PortState.BUSY }
    val tick = rememberDotTicker(BoardMotion.STEP_MS, running = anyBusy)
    val flashing = remember { mutableStateOf(emptySet<Arm>()) }
    val lastStates = remember { HashMap<Arm, PortState>() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(ports.map { it.state }) {
        val done = BoardMotion.finished(lastStates, ports)
        lastStates.clear()
        ports.forEach { lastStates[it.arm] = it.state }
        if (done.isEmpty() || !motion) return@LaunchedEffect
        flashing.value = flashing.value + done
        // Outside this effect, so a state change during the flash can't leave it lit.
        scope.launch {
            delay(BoardMotion.FLASH_MS)
            flashing.value = flashing.value - done
        }
    }
    BoxWithConstraints(modifier) {
        val boardWidth = maxWidth.value
        // Rows are sized for the tallest viewport seen at this width, so opening the
        // keyboard scrolls the board instead of squeezing its rows.
        val tallest = remember(maxWidth) { FloatArray(1) }
        tallest[0] = maxOf(tallest[0], maxHeight.value)
        val viewport = tallest[0]
        val geo = remember(maxWidth, viewport) { BoardGeometry.compute(maxWidth.value, viewport) }
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(geo.height.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val phase = if (motion) tick.intValue else 0
                    val squid = if (anyBusy && motion) SquidWave.frame(BoardMotion.squidStep(phase)) else SquidPose.STANDBY.grid
                    drawBoard(geo, ports, c, phase, squid, flashing.value)
                }

                // Date and status beside the squid. No greeting: it says what is running.
                Column(
                    Modifier
                        .offset(x = geo.sideX.dp, y = geo.sideTop.dp)
                        .width((boardWidth - geo.sideX).coerceAtLeast(0f).dp)
                ) {
                    val dateSize = with(density) { (44f * geo.scale).dp.toSp() }
                    Text(
                        dateTitle,
                        style = DotType.DotDisplay.copy(fontSize = dateSize, lineHeight = dateSize * 0.95f),
                        color = c.textPrimary,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(monthCaption.uppercase(Locale.ENGLISH), style = DotType.Caption, color = c.textTertiary, maxLines = 1)
                    Spacer(Modifier.height((22f * geo.scale).dp))
                    Text(
                        status,
                        style = DotType.Body.copy(lineHeight = 21.sp),
                        color = c.textSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                ports.forEachIndexed { i, port ->
                    // The tap target starts at the node ring so the whole port is one button.
                    val hitX = geo.nodeX - 14f * geo.scale
                    PortRow(
                        port = port,
                        textInset = (geo.portTextX - hitX).dp,
                        contentTop = portContentTop(geo.rowHeight).dp,
                        onClick = { onOpen(port.arm) },
                        modifier = Modifier
                            .offset(x = hitX.dp, y = geo.portTop(i).dp)
                            .width((boardWidth - hitX).coerceAtLeast(0f).dp)
                            .height(geo.rowHeight.dp)
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawBoard(
    geo: BoardGeometry,
    ports: List<ArmPort>,
    c: DotPalette,
    phase: Int,
    squid: BooleanArray,
    flashing: Set<Arm>,
) {
    val dp = density
    drawSquid(
        origin = Offset(0f, geo.squidTop * dp),
        pitchPx = geo.pitch * dp,
        grid = squid,
        on = c.textPrimary,
        eye = c.accent,
        off = c.dotOff
    )
    val idleR = 1.5f * dp
    val busyR = 1.8f * dp
    ports.forEachIndexed { i, port ->
        val dots = geo.cableDots(i)
        val flash = port.arm in flashing
        when {
            // Just finished: the whole cable lights white once, then goes back to grey.
            flash -> drawPoints(dots.map { it * dp }, PointMode.Points, c.textPrimary, strokeWidth = busyR * 2f, cap = StrokeCap.Round)
            port.state == PortState.BUSY -> {
                val lit = ArrayList<Offset>()
                val dark = ArrayList<Offset>()
                dots.forEachIndexed { j, o -> (if (BoardGeometry.busyDotLit(j, phase)) lit else dark) += o * dp }
                drawPoints(dark, PointMode.Points, c.borderStrong, strokeWidth = idleR * 2f, cap = StrokeCap.Round)
                drawPoints(lit, PointMode.Points, c.accent, strokeWidth = busyR * 2f, cap = StrokeCap.Round)
            }
            port.state == PortState.IDLE -> drawPoints(dots.map { it * dp }, PointMode.Points, c.borderStrong, strokeWidth = idleR * 2f, cap = StrokeCap.Round)
            else -> drawPoints(dots.map { it * dp }, PointMode.Points, c.surfaceRaised, strokeWidth = idleR * 2f, cap = StrokeCap.Round)
        }
        val ring = when (port.state) {
            PortState.BUSY -> c.accent
            PortState.UNLIT -> c.border
            PortState.IDLE -> c.textPrimary
        }
        val stroke = 1.5f * dp
        val node = Offset(geo.nodeX * dp, geo.portCentre(i) * dp)
        if (flash) {
            drawCircle(c.textPrimary, radius = 4f * dp, center = node)
        } else {
            drawCircle(ring, radius = 4f * dp - stroke / 2f, center = node, style = Stroke(stroke))
        }
    }
}

@Composable
private fun PortRow(
    port: ArmPort,
    textInset: Dp,
    contentTop: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = DotTheme.c
    val unlit = port.state == PortState.UNLIT
    val spoken = buildString {
        append(port.arm.label)
        append(", ")
        append(port.caption)
    }
    Box(
        modifier
            // Outer, so it replaces the clickable's own semantics with one clear button.
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                stateDescription = when (port.state) {
                    PortState.UNLIT -> "Not yet"
                    PortState.BUSY -> "Working"
                    PortState.IDLE -> ""
                }
                onClick(label = "Open ${port.arm.label}") { onClick(); true }
            }
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.padding(start = textInset).fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                HorizontalDivider(thickness = 1.dp, color = c.surfaceRaised)
                Column(Modifier.fillMaxWidth().padding(top = contentTop)) {
                    // Only the name line makes room for the icon; the caption runs under
                    // it to the edge, as in the kit (.pi is absolute, top right).
                    Row(
                        Modifier.padding(end = PORT_ICON + 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            port.arm.label,
                            style = DotType.Headline,
                            color = if (unlit) c.textDisabled else c.textPrimary,
                            maxLines = 1
                        )
                        if (port.later) LaterTag()
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        port.caption.uppercase(Locale.ENGLISH),
                        style = DotType.Caption,
                        color = if (port.state == PortState.BUSY) c.accentText else c.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                painterResource(armIcon(port.arm)),
                contentDescription = null,
                tint = if (unlit) c.textDisabled else c.textSecondary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 1.dp + contentTop + 8.dp)
                    .size(PORT_ICON)
            )
        }
    }
}

private val PORT_ICON = 22.dp

/** Space above a port's name: the kit's 12dp at full height, less when rows are tighter. */
internal fun portContentTop(rowHeight: Float): Float = ((rowHeight - 45f) * 0.55f).coerceIn(4f, 11f)

/** The dashed "LATER" tag on the Link port. */
@Composable
private fun LaterTag() {
    val c = DotTheme.c
    Text(
        "LATER",
        style = DotType.Label.copy(fontFamily = MonoFamily, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 0.14.em),
        color = c.textTertiary,
        modifier = Modifier
            .dashedBorder(c.borderStrong, radius = 4.dp)
            .padding(horizontal = 5.dp, vertical = 2.dp)
    )
}
