package ai.daylight.assistant.ui.run

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.daylight.assistant.R
import ai.daylight.assistant.domain.InlineQuestion
import ai.daylight.assistant.ui.dot.DotRadius
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.dot.MonoFamily
import ai.daylight.assistant.ui.talk.RoundIcon
import androidx.compose.ui.text.font.FontWeight

/**
 * "Kryzz asks": the chat-mode inline question and the agent's ask_user card. The question,
 * lettered options as ruled rows (A, B, C …) and a field for an answer of your own. Waiting
 * on you is the one live thing here, so only the label is yellow.
 */
@Composable
internal fun QuestionCard(
    question: InlineQuestion,
    onAnswer: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    answeredWith: String? = null
) {
    val c = DotTheme.c
    var freeText by rememberSaveable { mutableStateOf("") }
    val waiting = enabled && answeredWith == null
    Column(
        modifier
            .clip(DotRadius.Card)
            .background(c.surface)
            .border(1.dp, c.border, DotRadius.Card)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (answeredWith != null) "ANSWERED" else "KRYZZ ASKS",
                Modifier.weight(1f),
                style = DotType.Label,
                color = if (waiting) c.accent else c.textTertiary
            )
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss)
                    .semantics { contentDescription = "Dismiss question"; role = Role.Button },
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(R.drawable.ic_kz_close), null, Modifier.size(16.dp), tint = c.textSecondary)
            }
        }
        Text(
            question.question,
            Modifier.padding(end = 12.dp),
            style = DotType.Title,
            color = c.textPrimary
        )
        if (question.options.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Column(Modifier.padding(end = 12.dp)) {
                question.options.forEachIndexed { index, option ->
                    val chosen = answeredWith == option
                    HorizontalDivider(thickness = 1.dp, color = c.surfaceRaised)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clickable(enabled = enabled) { onAnswer(option) }
                            .semantics { selected = chosen },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(26.dp)
                                .background(if (chosen) c.textPrimary else c.surfaceHigh, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                ('A' + index).toString(),
                                style = DotType.Label.copy(fontFamily = MonoFamily, fontWeight = FontWeight.Bold),
                                color = if (chosen) c.bg else c.textSecondary
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            option,
                            Modifier.weight(1f).padding(vertical = 8.dp),
                            style = DotType.Body,
                            color = if (enabled || chosen) c.textPrimary else c.textTertiary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (chosen) {
                            Icon(painterResource(R.drawable.ic_kz_check), "Chosen", Modifier.size(16.dp), tint = c.textPrimary)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val hint = if (answeredWith != null) "Answered: $answeredWith" else "Type your own answer"
            BasicTextField(
                value = freeText,
                onValueChange = { freeText = it },
                enabled = enabled,
                singleLine = true,
                textStyle = DotType.Body.copy(color = c.textPrimary),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (freeText.isNotBlank()) onAnswer(freeText.trim()) }),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = hint },
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .background(c.surfaceRaised, DotRadius.Pill)
                            .border(1.dp, c.border, DotRadius.Pill)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (freeText.isEmpty()) {
                            Text(hint, style = DotType.Body, color = c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        inner()
                    }
                }
            )
            if (freeText.isNotBlank() && enabled) {
                RoundIcon(R.drawable.ic_kz_send, "Send answer", { onAnswer(freeText.trim()) }, filled = true)
            }
        }
    }
}
