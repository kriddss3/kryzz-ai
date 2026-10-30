package ai.daylight.assistant.ui.home

import ai.daylight.assistant.data.CronSchedulePresets
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.daylight.assistant.domain.AgentCapability
import ai.daylight.assistant.ui.DictationSession
import ai.daylight.assistant.ui.HomeViewModel
import ai.daylight.assistant.ui.onboarding.TourScreen
import ai.daylight.assistant.ui.shell.InkLine
import ai.daylight.assistant.ui.make.MakeModel
import ai.daylight.assistant.ui.shell.Arm
import ai.daylight.assistant.ui.shell.InkLines
import ai.daylight.assistant.ui.shell.InkStripEffect
import ai.daylight.assistant.ui.shell.LocalInkStrip
import kotlinx.coroutines.delay
import java.time.ZoneId

/**
 * Home, the arm board (kit `home`): the start screen and the status screen at once. Every
 * caption comes from real state; arms that don't exist yet are drawn unlit.
 */
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onOpenArm: (Arm) -> Unit,
    onAsk: (String) -> Unit,
    onMake: (String, AgentCapability) -> Unit,
    onVoice: () -> Unit,
    onInkwell: () -> Unit
) {
    val threads by vm.threads.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val plan by vm.agentPlan.collectAsStateWithLifecycle()
    val makeRender by vm.makeRender.collectAsStateWithLifecycle()
    val lastMedia by vm.lastMedia.collectAsStateWithLifecycle()
    val lastFindQuery by vm.lastFindQuery.collectAsStateWithLifecycle()
    val crew by vm.crew.collectAsStateWithLifecycle()
    val inkBadge by vm.inkBadge.collectAsStateWithLifecycle()
    val strip = LocalInkStrip.current
    val busy = strip?.busyArms.orEmpty()
    // Minute clock: the date block and "last 09:12" stay right while Home is open.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000L - value % 60_000L)
            value = System.currentTimeMillis()
        }
    }
    val zone = remember { ZoneId.systemDefault() }
    val snapshot = HomeSnapshot(
        threadCount = threads.count,
        lastThreadAt = threads.lastAt,
        busyArms = busy,
        runPlan = plan?.takeIf { strip?.isBusy(it.conversationId) == true },
        quality = settings.agentQuality,
        costCapCents = settings.costCapCents,
        makeKind = makeRender?.takeIf { it.running }?.kind,
        lastMakeKind = lastMedia?.let { MakeModel.kindOf(it.output) },
        lastMakeAt = lastMedia?.createdAt,
        findEnabled = settings.searchEnabled,
        lastFindQuery = lastFindQuery.ifBlank { null },
        botCount = crew.count,
        workingBots = crew.working,
        nextBotRunAt = crew.routines.mapNotNull { CronSchedulePresets.nextRunAt(it, now) }.minOrNull(),
        now = now,
        zone = zone
    )
    InkStripEffect(InkLines.home(busy.size))

    // v2.1: a fresh install sees the tour here once (Tune > Tour shows it again).
    if (settings.tourPending) {
        TourScreen(onDone = vm::dismissTour, modifier = Modifier.padding(start = 16.dp))
        return
    }

    val context = LocalContext.current
    val dictating by vm.dictating.collectAsStateWithLifecycle()
    val dictationLevel by vm.dictationLevel.collectAsStateWithLifecycle()
    val transcribing by vm.dictationTranscribing.collectAsStateWithLifecycle()
    val holdMicPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        strip?.flash(InkLine(if (granted) "HOLD AGAIN TO TALK" else "MIC NOT ALLOWED"))
    }

    var draft by rememberSaveable { mutableStateOf("") }
    fun holdStart() {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            vm.beginDictation(onError = { strip?.flash(InkLine("MIC UNAVAILABLE")) })
        } else {
            holdMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    fun holdEnd(cancelled: Boolean) {
        if (cancelled) {
            vm.cancelDictation()
            return
        }
        vm.finishDictation(
            onText = { words -> draft = DictationSession.append(draft, words) },
            onError = { message -> strip?.flash(InkLine(HomeBoard.dictationProblem(message))) }
        )
    }
    val offer = HomeBoard.makeOffer(draft)
    fun ask() {
        val text = draft.trim()
        if (text.isEmpty()) return
        draft = ""
        onAsk(text)
    }

    Column(Modifier.fillMaxSize().padding(start = 20.dp)) {
        ArmBoard(
            ports = HomeBoard.ports(snapshot),
            dateTitle = HomeBoard.dateTitle(now, zone),
            monthCaption = HomeBoard.monthCaption(now, zone),
            status = HomeBoard.status(snapshot),
            onOpen = onOpenArm,
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
        HomeComposer(
            value = draft,
            onValueChange = { draft = it },
            makeOffer = offer,
            onSend = ::ask,
            onMake = { capability ->
                val text = draft.trim()
                if (text.isNotEmpty()) {
                    draft = ""
                    onMake(text, capability)
                }
            },
            onVoice = onVoice,
            onInkwell = onInkwell,
            modifier = Modifier.navigationBarsPadding().imePadding(),
            inkBadge = inkBadge,
            dictating = dictating,
            dictationLevel = dictationLevel,
            transcribing = transcribing,
            onHoldStart = ::holdStart,
            onHoldEnd = ::holdEnd
        )
    }
}
