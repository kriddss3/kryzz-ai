package ai.daylight.assistant.ui.crew

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.daylight.assistant.R
import ai.daylight.assistant.data.local.BotEntity
import ai.daylight.assistant.data.local.ScheduledTaskEntity
import ai.daylight.assistant.ui.dot.DotAvatar
import ai.daylight.assistant.ui.dot.DotButtonStyle
import ai.daylight.assistant.ui.dot.DotPillButton
import ai.daylight.assistant.ui.dot.DotRadius
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.dot.effects.DotMatrixText
import ai.daylight.assistant.ui.shell.Arm
import ai.daylight.assistant.ui.shell.InkStripEffect
import ai.daylight.assistant.ui.tune.TuneEmpty
import ai.daylight.assistant.ui.tune.TuneSection
import java.time.ZoneId
import java.util.Locale

/**
 * Crew (kit `crew`, C9): personal bots. The bot that is working sits in a framed card at
 * the top; the others are ruled rows with their face, job, a status line from real state
 * (working now, next run, last run) and their routine pills. Routines with no bot (made in
 * Tune or before Crew) are listed under Other routines and open Tune's Routines page.
 */
@Composable
fun CrewScreen(
    vm: CrewViewModel,
    onOpenBot: (String) -> Unit,
    onRoutines: () -> Unit,
    onLibrary: () -> Unit,
) {
    val c = DotTheme.c
    val context = LocalContext.current
    val loaded by vm.bots.collectAsStateWithLifecycle()
    val bots = loaded.orEmpty()
    val routines by vm.routines.collectAsStateWithLifecycle()
    val work by vm.work.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    var showNew by rememberSaveable { mutableStateOf(false) }
    val zone = remember { ZoneId.systemDefault() }
    val now = rememberMinuteClock()

    InkStripEffect(CrewModel.crewLine(bots, work), arm = Arm.CREW)

    // Routine alerts need the notification permission on API 33+; asked only when a bot
    // gets a routine, never on screen entry.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val featured = CrewModel.featured(bots, work)
    val others = bots.filter { it.id != featured?.id }
    val otherRoutines = CrewModel.otherRoutines(routines, bots.mapTo(mutableSetOf()) { it.id })

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Title: opens the chat library, as on the other arms.
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(DotRadius.Tile)
                    .clickable(onClickLabel = "Open chat library", onClick = onLibrary)
                    .clearAndSetSemantics {
                        contentDescription = "Crew, ${CrewModel.countCaption(bots.size).ifEmpty { "no bots" }}. Open chat library"
                        onClick(label = "Open chat library") { onLibrary(); true }
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                DotMatrixText("CREW", pitch = 4.2.dp, on = c.textPrimary, off = Color.Transparent)
                val caption = CrewModel.countCaption(bots.size)
                if (caption.isNotEmpty()) {
                    Spacer(Modifier.width(12.dp))
                    Text(caption.uppercase(Locale.US), style = DotType.Caption, color = c.textTertiary)
                }
            }
            Spacer(Modifier.weight(1f))
            if (bots.isNotEmpty()) {
                DotPillButton(
                    "New bot",
                    { showNew = true },
                    Modifier.padding(end = 12.dp).testTag("crew_new_bot"),
                    style = DotButtonStyle.White,
                    icon = R.drawable.ic_kz_plus
                )
            }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp)
        ) {
            if (loaded != null && bots.isEmpty()) {
                item(key = "empty") {
                    TuneEmpty(
                        dotIcon = R.drawable.ic_kz_crew_dot,
                        words = "No bots yet",
                        body = "A bot is a helper with its own job and voice. Give it a routine and it works on a schedule, then tells you.",
                        modifier = Modifier.padding(top = 48.dp, bottom = 32.dp),
                        action = "New bot",
                        onAction = { showNew = true },
                        actionModifier = Modifier.testTag("crew_new_bot_empty")
                    )
                }
            }
            featured?.let { bot ->
                item(key = "featured-${bot.id}") {
                    FeaturedBotCard(
                        bot = bot,
                        routines = CrewModel.routinesOf(bot.id, routines),
                        status = CrewModel.status(CrewModel.routinesOf(bot.id, routines), work[bot.id], now, zone),
                        onOpen = { onOpenBot(bot.id) }
                    )
                    Spacer(Modifier.height(20.dp))
                }
            }
            items(others, key = { it.id }) { bot ->
                val own = CrewModel.routinesOf(bot.id, routines)
                BotRow(
                    bot = bot,
                    routines = own,
                    status = CrewModel.status(own, work[bot.id], now, zone),
                    live = bot.id in work,
                    onOpen = { onOpenBot(bot.id) }
                )
            }
            if (others.isNotEmpty()) item(key = "rule-end") { CrewRule() }
            if (otherRoutines.isNotEmpty()) {
                item(key = "other-head") {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.weight(1f)) { TuneSection("Other routines") }
                        Text(
                            "MANAGE",
                            Modifier
                                .clip(DotRadius.Pill)
                                .clickable(onClick = onRoutines)
                                .heightIn(min = 40.dp)
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            style = DotType.Label,
                            color = c.textPrimary
                        )
                    }
                }
                items(otherRoutines, key = { "routine-${it.id}" }) { task ->
                    OtherRoutineRow(task, now, zone, onOpen = onRoutines)
                }
                item(key = "other-rule") { CrewRule() }
            }
            if (bots.isNotEmpty()) {
                item(key = "foot") {
                    Row(Modifier.fillMaxWidth().padding(top = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_kz_info), null, Modifier.size(16.dp), tint = c.textTertiary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Bots use your keys and models. Each run is saved in the bot's chat.".uppercase(Locale.US),
                            style = DotType.Caption.copy(fontSize = 10.sp, lineHeight = 15.sp),
                            color = c.textTertiary
                        )
                    }
                }
            }
            notice?.let { text ->
                item(key = "notice") {
                    Text(text, Modifier.padding(top = 12.dp), style = DotType.BodySmall, color = c.error)
                }
            }
        }
    }

    if (showNew) {
        NewBotSheet(
            onDismiss = { showNew = false },
            onSave = { result ->
                if (result.routine != null) ensureNotificationPermission()
                vm.create(result.name, result.persona, result.seed, result.routine) { id ->
                    showNew = false
                    onOpenBot(id)
                }
            }
        )
    }
}

/** The working bot, promoted to a framed card (radius 24, 1dp strong border). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeaturedBotCard(
    bot: BotEntity,
    routines: List<ScheduledTaskEntity>,
    status: BotStatus,
    onOpen: () -> Unit,
) {
    val c = DotTheme.c
    Row(
        Modifier
            .fillMaxWidth()
            .clip(DotRadius.Card)
            .background(c.surface, DotRadius.Card)
            .border(1.dp, c.borderStrong, DotRadius.Card)
            .clickable(onClickLabel = "Open ${bot.name}", onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 18.dp)
            .testTag("crew_featured"),
        verticalAlignment = Alignment.Top
    ) {
        DotAvatar(bot.name, bot.avatarSeed, pitch = 9.dp, modifier = Modifier.padding(4.dp), live = true, animate = true)
        Spacer(Modifier.width(14.dp))
        BotInfo(bot, routines, status, Modifier.weight(1f))
        Icon(painterResource(R.drawable.ic_kz_forward), null, Modifier.size(18.dp), tint = c.textDisabled)
    }
}

/** One bot as a ruled row: face, name, job, status line and routine pills. */
@Composable
private fun BotRow(
    bot: BotEntity,
    routines: List<ScheduledTaskEntity>,
    status: BotStatus,
    live: Boolean,
    onOpen: () -> Unit,
) {
    val c = DotTheme.c
    Column(Modifier.fillMaxWidth()) {
        CrewRule()
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "Open ${bot.name}", onClick = onOpen)
                .padding(vertical = 18.dp),
            verticalAlignment = Alignment.Top
        ) {
            DotAvatar(bot.name, bot.avatarSeed, pitch = 7.dp, modifier = Modifier.padding(4.dp), live = live)
            Spacer(Modifier.width(16.dp))
            BotInfo(bot, routines, status, Modifier.weight(1f))
            Icon(painterResource(R.drawable.ic_kz_forward), null, Modifier.size(18.dp), tint = c.textDisabled)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BotInfo(bot: BotEntity, routines: List<ScheduledTaskEntity>, status: BotStatus, modifier: Modifier = Modifier) {
    val c = DotTheme.c
    Column(modifier) {
        Text(
            bot.name,
            style = DotType.Headline.copy(fontSize = 20.sp, lineHeight = 24.sp),
            color = c.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (bot.persona.isNotBlank()) {
            Text(
                bot.persona,
                Modifier.padding(top = 3.dp),
                style = DotType.Body.copy(fontSize = 14.sp, lineHeight = 19.sp),
                color = c.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        BotStatusLine(status, Modifier.padding(top = 10.dp))
        if (routines.isNotEmpty()) {
            FlowRow(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                routines.forEach { RoutinePill(it) }
            }
        }
    }
}

/** A routine with no bot: its name and schedule; tapping opens Tune's Routines page. */
@Composable
private fun OtherRoutineRow(task: ScheduledTaskEntity, now: Long, zone: ZoneId, onOpen: () -> Unit) {
    val c = DotTheme.c
    val status = CrewModel.status(listOf(task), null, now, zone)
    Column(Modifier.fillMaxWidth()) {
        CrewRule()
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "Open routines", onClick = onOpen)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = DotType.Body,
                    color = if (task.enabled) c.textPrimary else c.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoutinePill(task)
                    Spacer(Modifier.width(10.dp))
                    BotStatusLine(status, Modifier.weight(1f, fill = false))
                }
            }
            Icon(painterResource(R.drawable.ic_kz_forward), null, Modifier.size(18.dp), tint = c.textDisabled)
        }
    }
}
