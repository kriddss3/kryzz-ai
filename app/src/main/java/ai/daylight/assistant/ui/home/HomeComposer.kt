package ai.daylight.assistant.ui.home

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.daylight.assistant.R
import ai.daylight.assistant.domain.AgentCapability
import ai.daylight.assistant.ui.dot.DotRadius
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.talk.RoundIcon

internal const val HOME_PLACEHOLDER = "Ask, search, or make"

/** The pill label for the one-tap switch to the generator. */
internal fun makeOfferLabel(capability: AgentCapability): String =
    if (capability == AgentCapability.VIDEO) "Make a video instead" else "Make an image instead"

/**
 * Home's "Ask, search, or make" bar and the Inkwell button beside it. Sending opens a new
 * Talk thread with the text. When the text reads like a make request, a pill above the bar
 * offers to send it to the generator instead. [inkBadge] counts what is new in the Inkwell.
 */
@Composable
fun HomeComposer(
    value: String,
    onValueChange: (String) -> Unit,
    makeOffer: AgentCapability?,
    onSend: () -> Unit,
    onMake: (AgentCapability) -> Unit,
    onVoice: () -> Unit,
    onInkwell: () -> Unit,
    modifier: Modifier = Modifier,
    inkBadge: String = ""
) {
    val c = DotTheme.c
    val canSend = value.isNotBlank()
    Column(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)) {
        if (makeOffer != null) {
            MakeOfferPill(makeOffer, onClick = { onMake(makeOffer) })
            Spacer(Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .clip(DotRadius.Pill)
                    .background(c.surface)
                    .border(1.dp, c.border, DotRadius.Pill)
                    .padding(start = 18.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(painterResource(R.drawable.ic_kz_squid), null, Modifier.size(20.dp), tint = c.textTertiary)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = HOME_PLACEHOLDER },
                        textStyle = DotType.Body.copy(fontSize = 16.sp, lineHeight = 22.sp, color = c.textPrimary),
                        cursorBrush = SolidColor(c.accentText),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                        decorationBox = { inner ->
                            if (value.isEmpty()) {
                                Text(
                                    HOME_PLACEHOLDER,
                                    style = DotType.Body.copy(fontSize = 16.sp, lineHeight = 22.sp),
                                    color = c.textTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            inner()
                        }
                    )
                }
                if (canSend) {
                    RoundIcon(R.drawable.ic_kz_send, "Send", onSend, filled = true)
                } else {
                    RoundIcon(R.drawable.ic_kz_mic, "Voice", onVoice, filled = false)
                }
            }
            Spacer(Modifier.width(10.dp))
            InkwellButton(badge = inkBadge, onClick = onInkwell)
        }
    }
}

/** A kit pill: 28dp, raised surface, mono caption. */
@Composable
private fun MakeOfferPill(capability: AgentCapability, onClick: () -> Unit) {
    val c = DotTheme.c
    val icon = if (capability == AgentCapability.VIDEO) R.drawable.ic_kz_video else R.drawable.ic_kz_image
    Row(
        Modifier
            .clip(DotRadius.Pill)
            .clickable(onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .height(28.dp)
                .background(c.surfaceRaised, DotRadius.Pill)
                .border(1.dp, c.border, DotRadius.Pill)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painterResource(icon), null, Modifier.size(14.dp), tint = c.textSecondary)
            Spacer(Modifier.width(6.dp))
            Text(makeOfferLabel(capability).uppercase(), style = DotType.Caption, color = c.textSecondary, maxLines = 1)
        }
    }
}

/**
 * The Inkwell (kit `home`): a 56dp white button with the ink drop in black and, when
 * something new was made since the last visit, a yellow count at its top right.
 */
@Composable
private fun InkwellButton(badge: String, onClick: () -> Unit) {
    val c = DotTheme.c
    Box(Modifier.size(56.dp)) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(c.textPrimary)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics {
                    contentDescription = "Inkwell"
                    if (badge.isNotEmpty()) stateDescription = "$badge new"
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(R.drawable.ic_kz_ink), null, Modifier.size(22.dp), tint = c.bg)
        }
        if (badge.isNotEmpty()) {
            // Inside the button's box: the NavHost clips anything past its right edge.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = (-6).dp)
                    .heightIn(min = 22.dp)
                    .widthIn(min = 22.dp)
                    .background(c.accent, CircleShape)
                    .padding(horizontal = 5.dp)
                    .clearAndSetSemantics { },
                contentAlignment = Alignment.Center
            ) {
                Text(badge, style = DotType.Data.copy(fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium), color = c.onAccent)
            }
        }
    }
}
