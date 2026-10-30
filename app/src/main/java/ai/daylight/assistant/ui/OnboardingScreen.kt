package ai.daylight.assistant.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.daylight.assistant.R
import ai.daylight.assistant.ui.dot.DotButtonStyle
import ai.daylight.assistant.ui.dot.DotField
import ai.daylight.assistant.ui.dot.DotIconButton
import ai.daylight.assistant.ui.dot.DotNotice
import ai.daylight.assistant.ui.dot.DotPillButton
import ai.daylight.assistant.ui.dot.DotTag
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.dot.effects.DotDissolve
import ai.daylight.assistant.ui.dot.effects.DotMatrixText
import ai.daylight.assistant.ui.dot.effects.DotReveal
import ai.daylight.assistant.ui.dot.effects.DotRipple
import ai.daylight.assistant.ui.dot.mascot.DotSquid
import ai.daylight.assistant.ui.dot.mascot.squidPitchFor
import ai.daylight.assistant.ui.onboarding.OnboardingFlow
import ai.daylight.assistant.ui.onboarding.OnboardingStep
import ai.daylight.assistant.ui.shell.ScreenBackHandler
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled
import ai.daylight.assistant.ui.tune.TuneDetail
import ai.daylight.assistant.ui.tune.TuneRow
import ai.daylight.assistant.ui.tune.TuneTrailing
import java.util.Locale
import kotlinx.coroutines.delay

/** How long READY stays up before Home opens (the ripple and the reveal take 700ms). */
private const val READY_HOLD_MS = 1_100L

/**
 * Onboarding (components.md section 9): four steps, each with the step counter, a 96dp dot
 * icon that dissolves in, four progress dots, a dot-face title and a short body, then the
 * step's own controls. Continue and Skip sit at the bottom. Finishing ripples out from the
 * button into READY, then the choices are saved and Home opens.
 */
@Composable
fun OnboardingScreen(vm: OnboardingViewModel, onDone: () -> Unit) {
    val openKey by vm.openRouterKey.collectAsStateWithLifecycle()
    val parallelKey by vm.parallelKey.collectAsStateWithLifecycle()
    val fishKey by vm.fishKey.collectAsStateWithLifecycle()
    val memoryEnabled by vm.memoryEnabled.collectAsStateWithLifecycle()
    val locationEnabled by vm.locationEnabled.collectAsStateWithLifecycle()
    val openCheck by vm.openRouterCheck.collectAsStateWithLifecycle()
    val parallelCheck by vm.parallelCheck.collectAsStateWithLifecycle()
    val fishCheck by vm.fishCheck.collectAsStateWithLifecycle()
    val motion = LocalKryzzMotionEnabled.current
    val background = MaterialTheme.colorScheme.background
    val focus = LocalFocusManager.current
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
        onResult = vm::onLocationPermissionResult
    )

    var index by rememberSaveable { mutableIntStateOf(0) }
    var finishing by rememberSaveable { mutableStateOf(false) }
    var boxOrigin by remember { mutableStateOf(Offset.Zero) }
    var buttonCentre by remember { mutableStateOf<Offset?>(null) }
    val step = OnboardingFlow.step(index)
    val verified = openCheck is CheckState.Success

    ScreenBackHandler(enabled = !finishing && OnboardingFlow.back(index) != null) {
        OnboardingFlow.back(index)?.let { index = it }
    }

    fun finish() {
        focus.clearFocus()
        finishing = true
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(background)
            .onGloballyPositioned { boxOrigin = it.positionInRoot() }
    ) {
        if (finishing) {
            OnboardingReady(Modifier.fillMaxSize())
            // completeOnboarding() rebuilds the whole NavHost, so READY has to be shown first.
            LaunchedEffect(Unit) {
                if (motion) delay(READY_HOLD_MS)
                vm.finish(onDone)
            }
        } else {
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                OnboardingHeader(index, onBack = { OnboardingFlow.back(index)?.let { index = it } })
                val scroll = rememberScrollState()
                LaunchedEffect(index) { scroll.scrollTo(0) }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scroll)
                        .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp)
                ) {
                    key(step) { StepHero(step, cover = background) }
                    Spacer(Modifier.height(24.dp))
                    StepDots(index)
                    Spacer(Modifier.height(28.dp))
                    Text(
                        step.title,
                        Modifier.clearAndSetSemantics {
                            heading()
                            contentDescription = step.title.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }
                        },
                        style = DotType.DotDisplay,
                        color = DotTheme.c.textPrimary
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(step.body, style = DotType.Body, color = DotTheme.c.textSecondary)
                    Spacer(Modifier.height(28.dp))
                    when (step) {
                        OnboardingStep.LOCAL -> LocalFacts()
                        OnboardingStep.KEYS -> KeysStep(
                            openKey = openKey,
                            onOpenKey = { vm.openRouterKey.value = it },
                            openCheck = openCheck,
                            onTestOpen = vm::testOpenRouter,
                            parallelKey = parallelKey,
                            onParallelKey = { vm.parallelKey.value = it },
                            parallelCheck = parallelCheck,
                            onTestParallel = vm::testParallel,
                            fishKey = fishKey,
                            onFishKey = { vm.fishKey.value = it },
                            fishCheck = fishCheck,
                            onTestFish = vm::testFish
                        )
                        OnboardingStep.MEMORY -> {
                            TuneRow(
                                R.drawable.ic_kz_memory,
                                "Cross-chat memory",
                                if (memoryEnabled) "On, kept on this phone" else "Off",
                                TuneTrailing.Switch(memoryEnabled, { vm.memoryEnabled.value = it }),
                                modifier = Modifier.testTag("onboarding_memory")
                            )
                            TuneDetail("You can see, pin or delete every memory later in Tune.", Modifier.padding(top = 8.dp))
                        }
                        OnboardingStep.PRIVACY -> {
                            TuneRow(
                                R.drawable.ic_kz_location,
                                "Approximate location",
                                if (locationEnabled) "On, rough area only" else "Off",
                                TuneTrailing.Switch(locationEnabled, { wanted ->
                                    if (wanted) {
                                        locationPermission.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                                    } else {
                                        vm.locationEnabled.value = false
                                    }
                                }),
                                modifier = Modifier.testTag("onboarding_location")
                            )
                            Spacer(Modifier.height(24.dp))
                            DotNotice(
                                "Chat requests go to your chosen provider (OpenRouter or MiniMax) and the model's own provider. Web search goes separately to Parallel. Voice chat always uses OpenRouter. Check each provider's terms.",
                                icon = R.drawable.ic_kz_shield
                            )
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (OnboardingFlow.showsSkip(index)) {
                        DotPillButton(
                            "Skip",
                            ::finish,
                            Modifier.testTag("onboarding_skip"),
                            style = DotButtonStyle.Ghost,
                            enabled = OnboardingFlow.canSkip(index, openKey, verified)
                        )
                    }
                    DotPillButton(
                        OnboardingFlow.primaryLabel(index),
                        onClick = {
                            if (OnboardingFlow.isLast(index)) finish() else {
                                focus.clearFocus()
                                index = OnboardingFlow.next(index)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("onboarding_continue")
                            .onGloballyPositioned { buttonCentre = it.boundsInRoot().center },
                        enabled = OnboardingFlow.canContinue(index, openKey, verified)
                    )
                }
            }
        }
        DotRipple(
            trigger = if (finishing && motion) 1 else 0,
            modifier = Modifier.matchParentSize(),
            origin = buttonCentre?.minus(boxOrigin)
        )
    }
}

/** Back (from the second step on) and the "( 1 / 4 )" counter. */
@Composable
private fun OnboardingHeader(index: Int, onBack: () -> Unit) {
    val hasBack = OnboardingFlow.back(index) != null
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = if (hasBack) 8.dp else 24.dp, end = 24.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (hasBack) {
            DotIconButton(R.drawable.ic_kz_back, "Back", onBack)
            Spacer(Modifier.width(4.dp))
        }
        Text(
            OnboardingFlow.counter(index),
            Modifier.clearAndSetSemantics { contentDescription = "Step ${index + 1} of ${OnboardingFlow.count}" },
            style = DotType.Label,
            color = DotTheme.c.textTertiary
        )
    }
}

/** The step's 96dp dot icon; it dissolves in each time the step changes. */
@Composable
private fun StepHero(step: OnboardingStep, cover: Color) {
    val motion = LocalKryzzMotionEnabled.current
    var shown by remember { mutableStateOf(!motion) }
    LaunchedEffect(Unit) { shown = true }
    DotDissolve(visible = shown, cover = cover) {
        Icon(painterResource(step.icon), null, Modifier.size(96.dp), tint = DotTheme.c.textPrimary)
    }
}

/** Four dots under the hero: the current step is an 8dp yellow dot, the rest 6dp unlit. */
@Composable
private fun StepDots(index: Int) {
    val c = DotTheme.c
    Row(
        Modifier.clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(OnboardingFlow.count) { i ->
            val current = i == index
            Box(Modifier.size(if (current) 8.dp else 6.dp).background(if (current) c.accentText else c.dotOff, CircleShape))
        }
    }
}

/** Step 1: what "local first" means, as Tune's ruled fact rows. */
@Composable
private fun LocalFacts() {
    TuneRow(R.drawable.ic_kz_phone_local, "Chats stay on this phone", "No cloud copy", TuneTrailing.None)
    TuneRow(R.drawable.ic_kz_shield, "No analytics or telemetry", "Nothing is tracked", TuneTrailing.None)
    TuneRow(R.drawable.ic_kz_key, "Your own keys", "Encrypted with Android Keystore", TuneTrailing.None)
}

/** Step 2: the OpenRouter key in full, then Parallel and Fish Audio as rows that unfold. */
@Composable
private fun KeysStep(
    openKey: String,
    onOpenKey: (String) -> Unit,
    openCheck: CheckState,
    onTestOpen: () -> Unit,
    parallelKey: String,
    onParallelKey: (String) -> Unit,
    parallelCheck: CheckState,
    onTestParallel: () -> Unit,
    fishKey: String,
    onFishKey: (String) -> Unit,
    fishCheck: CheckState,
    onTestFish: () -> Unit,
) {
    KeyField(
        name = "OpenRouter",
        purpose = "Talk, Run, Voice and every model.",
        value = openKey,
        onValue = onOpenKey,
        check = openCheck,
        onTest = onTestOpen,
        tag = "openrouter_key",
        required = true
    )
    Spacer(Modifier.height(24.dp))
    OptionalKey(
        name = "Parallel",
        purpose = "Web search and citations in Talk and Run.",
        value = parallelKey,
        onValue = onParallelKey,
        check = parallelCheck,
        onTest = onTestParallel,
        tag = "parallel_key"
    )
    OptionalKey(
        name = "Fish Audio",
        purpose = "Spoken replies in Voice.",
        value = fishKey,
        onValue = onFishKey,
        check = fishCheck,
        onTest = onTestFish,
        tag = "fish_key"
    )
    Spacer(Modifier.height(24.dp))
    DotNotice(
        "Keys are encrypted with Android Keystore, left out of backups and never sent to another provider. You can change them later in Tune.",
        icon = R.drawable.ic_kz_lock
    )
}

/** An optional key: a ruled row with its status that unfolds into the field. */
@Composable
private fun OptionalKey(
    name: String,
    purpose: String,
    value: String,
    onValue: (String) -> Unit,
    check: CheckState,
    onTest: () -> Unit,
    tag: String,
) {
    var expanded by rememberSaveable(name) { mutableStateOf(value.isNotBlank()) }
    val caption = when {
        check is CheckState.Success -> "Optional, tested and saved"
        value.isNotBlank() -> "Optional, saved when you continue"
        else -> "Optional"
    }
    TuneRow(
        R.drawable.ic_kz_key,
        name,
        caption,
        TuneTrailing.Expand(expanded),
        onClick = { expanded = !expanded },
        detail = purpose
    )
    if (expanded) {
        KeyField(
            name = name,
            purpose = purpose,
            value = value,
            onValue = onValue,
            check = check,
            onTest = onTest,
            tag = tag,
            required = false,
            showHeader = false,
            modifier = Modifier.padding(start = 34.dp, bottom = 16.dp)
        )
    }
}

/**
 * A key field: the provider and its REQUIRED/OPTIONAL tag, what it is for, the masked field
 * with an eye toggle, then the test result and a Test pill. Done on the keyboard tests too.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeyField(
    name: String,
    purpose: String,
    value: String,
    onValue: (String) -> Unit,
    check: CheckState,
    onTest: () -> Unit,
    tag: String,
    required: Boolean,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
) {
    val c = DotTheme.c
    val keyboard = LocalSoftwareKeyboardController.current
    var reveal by rememberSaveable(name) { mutableStateOf(false) }
    val checking = check is CheckState.Checking
    // While the field is focused, keep the whole block (field, result, Test) above the
    // keyboard. Focus alone scrolls too early, before the keyboard has shrunk the page.
    val keepInView = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, imeBottom) { if (focused) keepInView.bringIntoView() }
    Column(modifier.fillMaxWidth().bringIntoViewRequester(keepInView)) {
        if (showHeader) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name.uppercase(Locale.US),
                    Modifier.weight(1f).semantics { heading() },
                    style = DotType.Label,
                    color = c.textSecondary
                )
                DotTag(OnboardingFlow.requirement(required), color = if (required) c.textPrimary else c.textTertiary)
            }
            Text(purpose, Modifier.padding(top = 6.dp, bottom = 12.dp), style = DotType.BodySmall, color = c.textTertiary)
        }
        DotField(
            value = value,
            onValueChange = onValue,
            placeholder = "Paste your $name key",
            visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done
            ),
            // Done hides the keyboard but keeps focus here: clearing focus on a hardware Enter
            // moved key focus to Back, and the key-up then stepped back a page.
            keyboardActions = KeyboardActions(onDone = {
                keyboard?.hide()
                if (value.isNotBlank() && !checking) onTest()
            }),
            trailing = {
                DotIconButton(
                    if (reveal) R.drawable.ic_kz_eye_off else R.drawable.ic_kz_eye,
                    if (reveal) "Hide $name key" else "Show $name key",
                    { reveal = !reveal },
                    tint = c.textSecondary,
                    iconSize = 20.dp
                )
            },
            fieldModifier = Modifier
                .testTag(tag)
                .onFocusChanged { focused = it.isFocused }
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KeyStatus(check, value, Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            DotPillButton(
                "Test",
                onTest,
                Modifier.testTag("${tag}_test"),
                style = DotButtonStyle.Ghost,
                enabled = value.isNotBlank(),
                busy = checking
            )
        }
    }
}

/** The key's test result: green with a check when it worked, red with a warning when not. */
@Composable
private fun KeyStatus(check: CheckState, value: String, modifier: Modifier = Modifier) {
    val c = DotTheme.c
    val (text, colour, icon) = when (check) {
        CheckState.Idle -> Triple(OnboardingFlow.keyIdle(value), c.textTertiary, null)
        CheckState.Checking -> Triple("Testing the key…", c.textTertiary, null)
        is CheckState.Success -> Triple(check.message, c.success, R.drawable.ic_kz_check)
        is CheckState.Error -> Triple(check.message, c.error, R.drawable.ic_kz_warning)
    }
    Row(modifier, verticalAlignment = Alignment.Top) {
        if (icon != null) {
            Icon(painterResource(icon), null, Modifier.padding(top = 1.dp, end = 8.dp).size(16.dp), tint = colour)
        }
        Text(text, style = DotType.BodySmall, color = colour)
    }
}

/** The finish: the squid and READY revealed in dots, while the ripple runs out. */
@Composable
private fun OnboardingReady(modifier: Modifier = Modifier) {
    val c = DotTheme.c
    val motion = LocalKryzzMotionEnabled.current
    Column(
        modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        DotSquid(pitch = squidPitchFor(120.dp))
        Spacer(Modifier.height(32.dp))
        DotMatrixText(
            "READY",
            pitch = 6.dp,
            on = c.textPrimary,
            reveal = if (motion) DotReveal.Random else DotReveal.None,
            contentDescription = "Ready"
        )
    }
}
