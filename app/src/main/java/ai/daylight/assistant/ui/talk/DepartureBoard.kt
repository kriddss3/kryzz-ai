package ai.daylight.assistant.ui.talk

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import ai.daylight.assistant.data.local.MessageEntity
import ai.daylight.assistant.domain.MessageStatus
import ai.daylight.assistant.ui.dot.DotGrey
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.dot.effects.DotMatrixText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** One question and the answer(s) that followed it, up to the next question. */
data class TalkExchange(val question: MessageEntity?, val answers: List<MessageEntity>) {
    val id: String get() = question?.id ?: answers.firstOrNull()?.id.orEmpty()
    val createdAt: Long get() = question?.createdAt ?: answers.firstOrNull()?.createdAt ?: 0L
    val streaming: Boolean get() = answers.any { it.status == MessageStatus.STREAMING.name }
    val gist: String get() = question?.let { TalkGist.of(it.content) } ?: TalkGist.FALLBACK
}

/**
 * Groups a conversation into exchanges: every USER message opens one, and the ASSISTANT
 * messages after it (until the next USER message) are its answers. Answers that arrive
 * before any question form an exchange of their own.
 */
fun talkExchanges(messages: List<MessageEntity>): List<TalkExchange> {
    val out = mutableListOf<TalkExchange>()
    var question: MessageEntity? = null
    val answers = mutableListOf<MessageEntity>()
    fun flush() {
        if (question != null || answers.isNotEmpty()) out += TalkExchange(question, answers.toList())
        question = null
        answers.clear()
    }
    messages.forEach { m ->
        when (m.role) {
            "USER" -> { flush(); question = m }
            "ASSISTANT" -> answers += m
            else -> Unit
        }
    }
    flush()
    return out
}

/**
 * The time on a board row: "09:12" for today, "26 OCT" earlier this year, "OCT 2025" before
 * that. Uppercase, so it reads like the rest of the board.
 */
fun boardTimeLabel(
    createdAt: Long,
    now: Long,
    zone: TimeZone = TimeZone.getDefault(),
    locale: Locale = Locale.getDefault(),
    is24Hour: Boolean = true,
): String {
    val then = Calendar.getInstance(zone).apply { timeInMillis = createdAt }
    val today = Calendar.getInstance(zone).apply { timeInMillis = now }
    val sameYear = then.get(Calendar.YEAR) == today.get(Calendar.YEAR)
    val sameDay = sameYear && then.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    val pattern = when {
        sameDay -> if (is24Hour) "HH:mm" else "h:mm a"
        sameYear -> "d MMM"
        else -> "MMM yyyy"
    }
    return SimpleDateFormat(pattern, locale).apply { timeZone = zone }.format(Date(createdAt))
        .uppercase(locale).replace(".", "")
}

/** Board row geometry (SCREENS.md section 2). */
private object BoardSpec {
    val RowHeight = 44.dp
    val Pitch = 2.6.dp
}

/**
 * A departure-board row: the exchange's gist in dot text with its time on the right.
 * The newest row is white, older rows grey. Tap to unfold the full exchange.
 */
@Composable
fun DepartureRow(
    gist: String,
    time: String,
    question: String,
    newest: Boolean,
    expanded: Boolean,
    showRule: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DotTheme.c
    // Older rows dim: grey-600 on black (7.7:1). On light that grey is 2.5:1, so they use the
    // tertiary grey there instead (4.5:1).
    val dotColor = when {
        newest -> c.textPrimary
        c.dark -> DotGrey.G600
        else -> c.textTertiary
    }
    val timeColor = if (newest) c.textPrimary else c.textTertiary
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(BoardSpec.RowHeight)
                .clickable(onClick = onToggle)
                .clearAndSetSemantics {
                    contentDescription = "$question, $time"
                    stateDescription = if (expanded) "Unfolded" else "Folded"
                    onClick(label = if (expanded) "Fold" else "Unfold") { onToggle(); true }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) {
                DotMatrixText(gist, pitch = BoardSpec.Pitch, on = dotColor, off = Color.Transparent)
            }
            Spacer(Modifier.width(12.dp))
            Text(time, style = DotType.Data, color = timeColor, maxLines = 1)
        }
        if (showRule) HorizontalDivider(thickness = 1.dp, color = c.surfaceRaised)
    }
}
