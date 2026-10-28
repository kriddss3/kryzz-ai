package ai.daylight.assistant.ui.voice

import ai.daylight.assistant.R
import ai.daylight.assistant.ui.dot.DotRadius
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.dot.effects.DotMatrixText
import ai.daylight.assistant.ui.dot.effects.ThinkingDots
import ai.daylight.assistant.ui.dot.mascot.DotSquid
import ai.daylight.assistant.voice.VoicePhase
import android.os.SystemClock
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Kit voice: the line before, Geist 18/25 in the disabled grey. */
private val PreviousLine = DotType.Answer.copy(lineHeight = 25.sp)

/** Kit voice: the current line, Geist 500 30/36. */
private val CurrentLarge = DotType.Question.copy(fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.02).em)

private fun currentStyle(size: VoiceTextSize): TextStyle = when (size) {
    VoiceTextSize.LARGE -> CurrentLarge
    VoiceTextSize.MEDIUM -> DotType.Goal
    VoiceTextSize.SMALL -> DotType.Answer
}

/**
 * The squid, VOICE in dot text, and the status line: a red dot while the mic is open, the
 * phase and how long it has lasted. Thinking adds the kit's stepping dots.
 */
@Composable
internal fun VoiceHeader(
    phase: VoicePhase,
    searching: Boolean,
    muted: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val c = DotTheme.c
    var elapsed by remember(phase) { mutableLongStateOf(0L) }
    LaunchedEffect(phase) {
        val start = SystemClock.elapsedRealtime()
        while (true) {
            val ms = SystemClock.elapsedRealtime() - start
            elapsed = ms / 1_000
            delay(1_000 - ms % 1_000)
        }
    }
    Row(modifier, verticalAlignment = Alignment.Top) {
        DotSquid(pose = voiceSquidPose(phase), pitch = if (compact) 2.4.dp else 3.4.dp)
        Spacer(Modifier.width(if (compact) 12.dp else 8.dp))
        Column(Modifier.padding(top = if (compact) 2.dp else 6.dp)) {
            DotMatrixText("VOICE", pitch = if (compact) 3.dp else 4.2.dp, contentDescription = "Voice chat")
            Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
            Row(
                Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(if (voiceMicLive(phase, muted)) c.recording else c.textDisabled, CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    voiceStatusCaption(phase, searching, muted, elapsed),
                    style = DotType.Caption,
                    color = c.textTertiary,
                    maxLines = 1
                )
                if (phase == VoicePhase.PROCESSING) {
                    Spacer(Modifier.width(10.dp))
                    ThinkingDots(dot = 4.dp)
                }
            }
        }
    }
}

/**
 * The previous line dim, the current line large. A long reply steps down a size; when
 * [maxCurrentLines] is set (short screens) it is cut with an ellipsis.
 */
@Composable
internal fun VoiceTranscript(lines: VoiceLines, modifier: Modifier = Modifier, maxCurrentLines: Int = Int.MAX_VALUE) {
    val c = DotTheme.c
    Column(modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        lines.previous?.let {
            Text(it, style = PreviousLine, color = c.textDisabled, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        val main = lines.current ?: lines.placeholder
        if (main != null) {
            if (lines.previous != null) Spacer(Modifier.height(14.dp))
            Text(
                main,
                style = currentStyle(voiceTextSize(main.length)),
                color = if (lines.current != null) c.textPrimary else c.textTertiary,
                maxLines = maxCurrentLines,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Kit `.ib.f`: a raised circle with a hairline border and a white outline icon. */
@Composable
internal fun VoiceSideButton(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp
) {
    val c = DotTheme.c
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(c.surfaceRaised)
            .border(1.dp, c.border, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description; role = Role.Button },
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(icon), null, Modifier.size(22.dp), tint = c.textPrimary)
    }
}

/**
 * Mutes the mic without leaving the session. Not in the kit mockup; it sits under the core
 * button so it stays in thumb reach. Muted, the pill turns white and offers Unmute.
 */
@Composable
internal fun VoiceMuteToggle(muted: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val c = DotTheme.c
    val fg = if (muted) c.bg else c.textPrimary
    Row(
        modifier
            .height(44.dp)
            .clip(DotRadius.Pill)
            .then(
                if (muted) Modifier.background(c.textPrimary, DotRadius.Pill)
                else Modifier.border(1.dp, c.borderStrong, DotRadius.Pill)
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 18.dp)
            .semantics {
                contentDescription = if (muted) "Unmute microphone" else "Mute microphone"
                stateDescription = if (muted) "Muted" else "On"
                role = Role.Button
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            painterResource(if (muted) R.drawable.ic_kz_mic else R.drawable.ic_kz_mic_off),
            null,
            Modifier.size(16.dp),
            tint = fg
        )
        Spacer(Modifier.width(8.dp))
        Text(if (muted) "UNMUTE" else "MUTE MIC", style = DotType.Label, color = fg)
    }
}
