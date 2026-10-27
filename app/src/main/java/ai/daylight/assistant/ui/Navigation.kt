package ai.daylight.assistant.ui

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavBackStackEntry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ai.daylight.assistant.AppContainer
import ai.daylight.assistant.domain.AssistantMode
import ai.daylight.assistant.domain.AgentCapability
import ai.daylight.assistant.domain.ModelPurpose
import ai.daylight.assistant.domain.BackgroundStyle
import ai.daylight.assistant.domain.GradientPalette
import ai.daylight.assistant.ui.shell.Arm
import ai.daylight.assistant.ui.shell.ArmRail
import ai.daylight.assistant.ui.shell.ArmRailSpec
import ai.daylight.assistant.ui.shell.InkLines
import ai.daylight.assistant.ui.shell.InkStrip
import ai.daylight.assistant.ui.shell.InkStripState
import ai.daylight.assistant.ui.shell.LocalInkStrip
import ai.daylight.assistant.ui.shell.armForMode
import ai.daylight.assistant.ui.shell.armForRoute
import ai.daylight.assistant.ui.shell.defaultInkLine
import ai.daylight.assistant.ui.shell.railVisibleForRoute
import ai.daylight.assistant.ui.shell.shellChromeVisibleForRoute
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Routes {
    const val BOOT = "boot"
    const val ONBOARDING = "onboarding"
    const val LLMS = "llms"
    const val SKILLS = "skills"
    const val CHAT = "chat/{conversationId}?mode={mode}&capability={capability}&voice={voice}"
    const val MODELS = "models/{purpose}"
    const val SETTINGS = "settings"
    const val API = "settings/api"
    const val PERSONA = "settings/persona"
    const val TOOLS = "settings/tools"
    const val PRIVACY = "settings/privacy"
    const val APPEARANCE = "settings/appearance"
    const val VOICE = "settings/voice"
    const val MEMORY = "settings/memory"
    const val CRON = "cron"
    fun chat(id: String, mode: AssistantMode = AssistantMode.CHAT, capability: AgentCapability = AgentCapability.AUTO, voice: Boolean = false) =
        "chat/$id?mode=${mode.name}&capability=${capability.name}&voice=$voice"
    fun models(purpose: ModelPurpose = ModelPurpose.CHAT) = "models/${purpose.name}"
}

/** Chat is the home destination; Settings is the only other top-level surface. */
internal fun topLevelTabForRoute(route: String?): MainTab? = when {
    route == Routes.CHAT || route?.startsWith("chat/") == true -> MainTab.CHAT
    route == Routes.SETTINGS -> MainTab.SETTINGS
    else -> null
}

@Composable
fun AppNavigation(
    container: AppContainer,
    onboardingComplete: Boolean,
    backgroundStyle: BackgroundStyle = BackgroundStyle.CONSTELLATION,
    backgroundBlur: Float = 0f,
    colouredGradient: GradientPalette = GradientPalette.OCEAN
) {
    key(onboardingComplete) {
        val nav = rememberNavController()
        val scope = rememberCoroutineScope()
        val currentEntry = nav.currentBackStackEntryAsState().value
        val currentRoute = currentEntry?.destination?.route
        val currentConversationId = currentEntry?.arguments?.getString("conversationId")
        val currentMode = runCatching {
            AssistantMode.valueOf(currentEntry?.arguments?.getString("mode").orEmpty())
        }.getOrDefault(AssistantMode.CHAT)
        val currentCapability = runCatching {
            AgentCapability.valueOf(currentEntry?.arguments?.getString("capability").orEmpty())
        }.getOrDefault(AgentCapability.AUTO)
        val rememberedChatRoutes = remember {
            mutableMapOf<String, Pair<AssistantMode, AgentCapability>>()
        }
        var activeConversationId by rememberSaveable { mutableStateOf<String?>(null) }
        // Eight Arms shell: the ink strip's line and the rail's busy dots.
        val inkStrip = remember { InkStripState() }
        val currentVoice = currentEntry?.arguments?.getString("voice")?.toBoolean() ?: false
        val activeArm: Arm? = if (railVisibleForRoute(currentRoute)) {
            inkStrip.reportedArm ?: armForRoute(currentRoute, currentMode, currentVoice)
        } else {
            null
        }
        // The conversation each chat arm returns to. One conversation belongs to one arm:
        // switching a fresh chat to Agent moves it from Talk to Run.
        var talkConversationId by rememberSaveable { mutableStateOf<String?>(null) }
        var runConversationId by rememberSaveable { mutableStateOf<String?>(null) }
        fun recordArmConversation(arm: Arm, id: String) {
            when (arm) {
                Arm.TALK -> {
                    talkConversationId = id
                    if (runConversationId == id) runConversationId = null
                }
                Arm.RUN -> {
                    runConversationId = id
                    if (talkConversationId == id) talkConversationId = null
                }
                else -> Unit
            }
        }
        // Chat entries whose generation outlives the screen (covered by Settings, or kept
        // under another arm) report busy here until their back-stack entry is destroyed.
        val busyWatchers = remember { mutableSetOf<String>() }
        LaunchedEffect(currentConversationId, currentMode, currentCapability) {
            if (currentConversationId != null) {
                activeConversationId = currentConversationId
                if (topLevelTabForRoute(currentRoute) == MainTab.CHAT) {
                    rememberedChatRoutes[currentConversationId] = currentMode to currentCapability
                }
            }
        }
        fun openSingleConversation(route: String) {
            nav.navigate(route) {
                popUpTo(Routes.CHAT) { inclusive = true }
                launchSingleTop = true
            }
        }
        suspend fun rememberedRoute(id: String): Pair<AssistantMode, AgentCapability> {
            rememberedChatRoutes[id]?.let { return it }
            val lastUserMessage = container.conversations.latestUserMessage(id)
            val mode = runCatching {
                AssistantMode.valueOf(lastUserMessage?.mode.orEmpty())
            }.getOrDefault(AssistantMode.CHAT)
            val capability = if (mode == AssistantMode.AGENT) {
                runCatching {
                    AgentCapability.valueOf(lastUserMessage?.capability.orEmpty())
                }.getOrDefault(AgentCapability.AUTO)
            } else {
                AgentCapability.AUTO
            }
            return (mode to capability).also { rememberedChatRoutes[id] = it }
        }
        fun openChatLanding() {
            scope.launch {
                val remembered = activeConversationId
                val id = if (remembered != null && container.conversations.conversation(remembered) != null) {
                    remembered
                } else {
                    container.conversations.deleteEmptyExcept(chatDraftConversationIds())
                    container.conversations.createConversation().also { created ->
                        if (remembered != null) transferChatDraft(remembered, created)
                    }
                }
                activeConversationId = id
                val (mode, capability) = rememberedRoute(id)
                withContext(Dispatchers.Main.immediate) {
                    openSingleConversation(Routes.chat(id, mode, capability))
                }
            }
        }
        BackHandler(
            enabled = currentRoute != null && currentRoute !in setOf(Routes.ONBOARDING, Routes.BOOT, Routes.CHAT)
        ) {
            nav.popBackStack()
        }
        val factory = DaylightViewModelFactory(container)
        fun openTopLevel(route: String) {
            val targetTab = topLevelTabForRoute(route)
            if (targetTab != null && targetTab == topLevelTabForRoute(currentRoute)) return
            nav.navigate(route) {
                launchSingleTop = true
            }
        }
        val motionEnabled = LocalKryzzMotionEnabled.current
        val appViewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current)
        // Chat is the home surface. Its left drawer owns conversation switching,
        // new-chat modes and the route into Settings.
        var panelOpen by rememberSaveable { mutableStateOf(false) }
        val libraryVm: ConversationListViewModel = viewModel(
            viewModelStoreOwner = appViewModelStoreOwner,
            factory = factory
        )
        val sidePanelSwipeEnabled = sidePanelOpenAllowedForRoute(currentRoute)
        // previous route can never leak into a subpage.
        LaunchedEffect(sidePanelSwipeEnabled) {
            if (!sidePanelSwipeEnabled && panelOpen) panelOpen = false
        }
        // When the route leaves the chat surface (Settings home, a settings subpage,
        // models, skills, memory, cron, etc.), force the side panel closed so it can
        // never be left dangling over a non-chat surface — regardless of how the route
        // was entered (panel footer, chat header, skills screen, back stack, etc.).
        LaunchedEffect(currentRoute) {
            if (panelOpen && topLevelTabForRoute(currentRoute) != MainTab.CHAT) panelOpen = false
        }
        fun openLibrary() {
            // Hard gate: never open the panel from a depth destination
            // (settings subpage, models, skills, etc.). The user has to
            // back out to a top-level screen first.
            if (!sidePanelSwipeEnabled) return
            panelOpen = true
        }
        fun chatBackStack(): List<NavBackStackEntry> {
            @SuppressLint("RestrictedApi")
            val stack = nav.currentBackStack.value
            return stack.filter { it.destination.route == Routes.CHAT }
        }
        /**
         * Shows a conversation. If it is already on the back stack (for example under
         * Settings, or a run kept alive under another arm), pop back to it so its
         * ViewModel and any running reply survive. Otherwise replace the top chat, unless
         * that chat is still generating: then push on top so the reply keeps running.
         */
        fun showConversation(
            id: String,
            mode: AssistantMode,
            capability: AgentCapability = AgentCapability.AUTO,
            voice: Boolean = false
        ) {
            activeConversationId = id
            if (!voice) rememberedChatRoutes.putIfAbsent(id, mode to capability)
            val chats = chatBackStack()
            val existing = chats.lastOrNull { it.arguments?.getString("conversationId") == id }
            if (existing != null && !voice) {
                while (nav.currentBackStackEntry?.id != existing.id) {
                    if (!nav.popBackStack()) break
                }
                return
            }
            val route = Routes.chat(id, mode, capability, voice)
            val topChatId = chats.lastOrNull()?.arguments?.getString("conversationId")
            if (inkStrip.isBusy(topChatId)) {
                nav.navigate(route) { popUpTo(Routes.CHAT) { inclusive = false } }
            } else {
                openSingleConversation(route)
            }
        }
        fun openConversationFromLibrary(id: String) {
            panelOpen = false
            // Re-selecting the visible chat should only close the drawer. Replacing the
            // destination would dispose the same empty conversation and delete it.
            if (id == currentConversationId) return
            scope.launch {
                val (mode, capability) = rememberedRoute(id)
                withContext(Dispatchers.Main.immediate) {
                    showConversation(id, mode, capability)
                }
            }
        }
        fun deleteConversationFromLibrary(id: String) {
            panelOpen = false
            scope.launch {
                if (id == currentConversationId) {
                    val replacementId = container.conversations.createConversation()
                    activeConversationId = replacementId
                    rememberedChatRoutes[replacementId] = AssistantMode.CHAT to AgentCapability.AUTO
                    withContext(Dispatchers.Main.immediate) {
                        openSingleConversation(Routes.chat(replacementId))
                    }
                }
                container.conversations.delete(id)
                rememberedChatRoutes.remove(id)
                if (activeConversationId == id) activeConversationId = null
            }
        }
        fun newConversationFromLibrary(mode: AssistantMode, voice: Boolean = false, capability: AgentCapability = AgentCapability.AUTO) {
            panelOpen = false
            scope.launch {
                container.conversations.deleteEmptyExcept(chatDraftConversationIds())
                val id = container.conversations.createConversation()
                rememberedChatRoutes[id] = mode to capability
                withContext(Dispatchers.Main.immediate) {
                    showConversation(id, mode, capability, voice)
                }
            }
        }
        /** Talk and Run return to their last conversation, or start a fresh one. */
        fun openArmConversation(arm: Arm) {
            val mode = if (arm == Arm.RUN) AssistantMode.AGENT else AssistantMode.CHAT
            val remembered = if (arm == Arm.RUN) runConversationId else talkConversationId
            scope.launch {
                if (remembered != null && container.conversations.conversation(remembered) != null) {
                    val capability = rememberedChatRoutes[remembered]?.second ?: AgentCapability.AUTO
                    withContext(Dispatchers.Main.immediate) {
                        showConversation(remembered, mode, capability)
                    }
                } else {
                    withContext(Dispatchers.Main.immediate) { newConversationFromLibrary(mode) }
                }
            }
        }
        // Read through State: the compiler memoises `::openArm`, so a plain captured
        // `activeArm` would stay frozen at its first value (Talk taps were ignored).
        val currentArm = rememberUpdatedState(activeArm)
        fun openArm(arm: Arm) {
            if (arm == currentArm.value) return
            panelOpen = false
            when (arm) {
                Arm.TALK, Arm.RUN -> openArmConversation(arm)
                Arm.VOICE -> newConversationFromLibrary(AssistantMode.CHAT, voice = true)
                Arm.TUNE -> openTopLevel(Routes.SETTINGS)
                Arm.MAKE, Arm.FIND, Arm.CREW, Arm.LINK -> inkStrip.flash(InkLines.notYet(arm))
            }
        }
        val rootBackgroundStyle = if (currentRoute == Routes.CHAT || currentRoute?.startsWith("chat/") == true) {
            backgroundStyle
        } else {
            backgroundStyle
        }
        DaylightBackground(
            style = rootBackgroundStyle,
            blurRadius = backgroundBlur,
            colouredGradient = colouredGradient
        ) {
        KryzzSidePanelHost(
            open = panelOpen,
            onClose = { panelOpen = false },
            onOpen = ::openLibrary,
            swipeEnabled = sidePanelSwipeEnabled,
            panel = {
                KryzzLibraryPanel(
                    vm = libraryVm,
                    onOpen = ::openConversationFromLibrary,
                    onNewChat = { mode -> newConversationFromLibrary(mode) },
                    onNewVoice = { newConversationFromLibrary(AssistantMode.CHAT, voice = true) },
                    onCron = {
                        panelOpen = false
                        nav.navigate(Routes.CRON) { launchSingleTop = true }
                    },
                    onSettings = {
                        panelOpen = false
                        openTopLevel(Routes.SETTINGS)
                    },
                    onClose = { panelOpen = false },
                    onDeleteConversation = ::deleteConversationFromLibrary
                )
            }
        ) {
        // Shell: ink strip on top, then the NavHost with the arm rail as its SIBLING on the
        // right. The rail never overlays the NavHost (an overlay broke every button in
        // 5.1.4/5.1.5). The status bar is consumed here once, so screens that pad for it
        // (TopAppBar, statusBarsPadding) don't add it a second time under the strip.
        val chromeVisible = shellChromeVisibleForRoute(currentRoute, onboardingComplete)
        CompositionLocalProvider(LocalInkStrip provides inkStrip) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
        ) {
        if (chromeVisible) {
            InkStrip(
                state = inkStrip,
                fallback = defaultInkLine(currentRoute),
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
        Row(Modifier.fillMaxWidth().weight(1f)) {
        NavHost(
            navController = nav,
            startDestination = if (onboardingComplete) Routes.BOOT else Routes.ONBOARDING,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clipToBounds(),
            enterTransition = {
                if (!motionEnabled) EnterTransition.None
                else slideIntoContainer(pushTowards(initialState.destination.route, targetState.destination.route), tween(240, easing = FastOutSlowInEasing)) + fadeIn(tween(140))
            },
            exitTransition = {
                if (!motionEnabled) ExitTransition.None
                else slideOutOfContainer(pushTowards(initialState.destination.route, targetState.destination.route), tween(220, easing = FastOutSlowInEasing)) + fadeOut(tween(120))
            },
            popEnterTransition = {
                if (!motionEnabled) EnterTransition.None
                else slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(240, easing = FastOutSlowInEasing)) + fadeIn(tween(140))
            },
            popExitTransition = {
                if (!motionEnabled) ExitTransition.None
                else slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(220, easing = FastOutSlowInEasing)) + fadeOut(tween(120))
            }
        ) {
        composable(Routes.ONBOARDING) {
            val vm: OnboardingViewModel = viewModel(factory = factory)
            OnboardingScreen(vm) {
                nav.navigate(Routes.BOOT) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            }
        }
        composable(Routes.BOOT) {
            LaunchedEffect(Unit) {
                container.conversations.deleteEmptyExcept(chatDraftConversationIds())
                val id = container.conversations.createConversation()
                withContext(Dispatchers.Main.immediate) {
                    nav.navigate(Routes.chat(id)) {
                        popUpTo(Routes.BOOT) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }
        composable(Routes.LLMS) {
            val vm: LlmCatalogViewModel = viewModel(factory = factory)
            LlmCatalogScreen(
                vm = vm,
                onChats = nav::popBackStack,
                onSkills = { nav.navigate(Routes.SKILLS) },
                onSettings = { openTopLevel(Routes.SETTINGS) }
            )
        }
        composable(Routes.SKILLS) {
            val vm: SkillsToolsViewModel = viewModel(factory = factory)
            SkillsToolsScreen(
                vm = vm,
                onBack = nav::popBackStack,
                onChats = { openChatLanding() },
                onLlms = { nav.navigate(Routes.LLMS) },
                onSettings = { openTopLevel(Routes.SETTINGS) },
                onStart = { capability ->
                    vm.createConversation { id ->
                        rememberedChatRoutes[id] = AssistantMode.AGENT to capability
                        openSingleConversation(Routes.chat(id, AssistantMode.AGENT, capability))
                    }
                }
            )
        }
        composable(
            Routes.CHAT,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType },
                navArgument("mode") { type = NavType.StringType; defaultValue = AssistantMode.CHAT.name },
                navArgument("capability") { type = NavType.StringType; defaultValue = AgentCapability.AUTO.name },
                navArgument("voice") { type = NavType.StringType; defaultValue = "false" }
            )
        ) { entry ->
            val id = checkNotNull(entry.arguments?.getString("conversationId"))
            val initialMode = runCatching { AssistantMode.valueOf(entry.arguments?.getString("mode").orEmpty()) }.getOrDefault(AssistantMode.CHAT)
            val initialCapability = runCatching { AgentCapability.valueOf(entry.arguments?.getString("capability").orEmpty()) }.getOrDefault(AgentCapability.AUTO)
            val initialVoice = entry.arguments?.getString("voice")?.toBoolean() ?: false
            val vm: ChatViewModel = viewModel(
                key = "chat-$id",
                viewModelStoreOwner = entry,
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(container, id) as T
                }
            )
            // Busy dot for the rail and the strip's live mark. The watcher runs in the
            // back-stack entry's scope, so it keeps reporting while Settings covers this
            // chat and stops (clearing the dot) when the entry is destroyed.
            LaunchedEffect(entry.id) {
                if (busyWatchers.add(entry.id)) {
                    entry.lifecycleScope.launch {
                        try {
                            combine(vm.generating, vm.mode) { generating, mode ->
                                if (generating) armForMode(mode) else null
                            }.collect { inkStrip.setBusy(id, it) }
                        } finally {
                            busyWatchers.remove(entry.id)
                            inkStrip.setBusy(id, null)
                        }
                    }
                }
            }
            DisposableEffect(id) {
                onDispose {
                    // Prune the empty conversation only when this chat really leaves the
                    // back stack. Being covered by Settings must not delete it, otherwise
                    // the retained ChatViewModel would write into a missing conversation.
                    @SuppressLint("RestrictedApi")
                    val stillOnBackStack = nav.currentBackStack.value.any { it.id == entry.id }
                    if (!stillOnBackStack && !hasChatDraft(id)) {
                        scope.launch { container.conversations.deleteIfEmpty(id) }
                    }
                }
            }
            ChatScreen(
                vm,
                container.conversations,
                initialMode = initialMode,
                initialCapability = initialCapability,
                initialVoice = initialVoice,
                onBack = ::openLibrary,
                onOpenLibrary = ::openLibrary,
                onModels = { nav.navigate(Routes.models(it)) },
                onStartAgent = {
                    scope.launch {
                        val exists = container.conversations.conversation(id) != null
                        val hasContent = exists && container.conversations.hasContent(id)
                        val agentConversationId = if (hasContent) {
                            container.conversations.deleteEmptyExcept(chatDraftConversationIds())
                            container.conversations.createConversation()
                        } else {
                            container.conversations.deleteEmptyExcept(chatDraftConversationIds() + id)
                            if (exists) id else container.conversations.createConversation()
                        }
                        rememberedChatRoutes[agentConversationId] = AssistantMode.AGENT to AgentCapability.AUTO
                        openSingleConversation(Routes.chat(agentConversationId, AssistantMode.AGENT))
                    }
                    true
                },
                // From a locked conversation the header button starts a fresh conversation
                // in the same mode (the mode itself stays locked to this conversation).
                onNewChat = { mode ->
                    scope.launch {
                        container.conversations.deleteEmptyExcept(chatDraftConversationIds())
                        val newId = container.conversations.createConversation()
                        activeConversationId = newId
                        rememberedChatRoutes[newId] = mode to AgentCapability.AUTO
                        openSingleConversation(Routes.chat(newId, mode))
                    }
                },
                onComposerFocusChanged = {},
                onArmShown = { arm -> recordArmConversation(arm, id) },
                rootNavVisible = false
            )
        }
        composable(Routes.MODELS, arguments = listOf(navArgument("purpose") { type = NavType.StringType })) { entry ->
            val vm: ModelsViewModel = viewModel(factory = factory)
            val purpose = runCatching { ModelPurpose.valueOf(entry.arguments?.getString("purpose").orEmpty()) }.getOrDefault(ModelPurpose.CHAT)
            ModelsScreen(vm, purpose, nav::popBackStack)
        }
        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = factory)
            val settings by vm.settings.collectAsStateWithLifecycle()
            val memoryCountFlow = remember { container.memories.observe("").map { it.size } }
            val memoryCount by memoryCountFlow.collectAsStateWithLifecycle(initialValue = 0)
            SettingsHomeScreen(
                onChats = { openChatLanding() },
                onLlms = { nav.navigate(Routes.LLMS) },
                onSkills = { nav.navigate(Routes.SKILLS) },
                onOpen = nav::navigate,
                onChat = { openChatLanding() },
                onOpenLibrary = ::openLibrary,
                memoryCount = memoryCount,
                agentQuality = settings.agentQuality
            )
        }
        composable(Routes.API) {
            val vm: SettingsViewModel = viewModel(factory = factory)
            ApiSettingsScreen(vm, nav::popBackStack)
        }
        composable(Routes.PERSONA) {
            val vm: SettingsViewModel = viewModel(factory = factory)
            PersonaSettingsScreen(vm, nav::popBackStack)
        }
        composable(Routes.TOOLS) {
            val vm: SettingsViewModel = viewModel(factory = factory)
            ToolSettingsScreen(vm, nav::popBackStack)
        }
        composable(Routes.PRIVACY) {
            val vm: SettingsViewModel = viewModel(factory = factory)
            PrivacySettingsScreen(vm, nav::popBackStack)
        }
        composable(Routes.MEMORY) {
            val vm: MemoryViewModel = viewModel(factory = factory)
            MemoryScreen(vm, nav::popBackStack)
        }
        composable(Routes.APPEARANCE) {
            val vm: SettingsViewModel = viewModel(factory = factory)
            AppearanceSettingsScreen(vm, nav::popBackStack)
        }
        composable(Routes.VOICE) {
            val vm: SettingsViewModel = viewModel(factory = factory)
            VoiceSettingsScreen(vm, nav::popBackStack)
        }
        composable(Routes.CRON) {
            val vm: CronViewModel = viewModel(factory = factory)
            CronJobsScreen(vm, nav::popBackStack)
        }
        }
        if (chromeVisible) {
            // The rail's column stays on every shell screen so layouts don't jump; the
            // dots are drawn only on arm screens (not on depth pages).
            if (activeArm != null) {
                ArmRail(active = activeArm, busy = inkStrip.busyArms, onOpen = ::openArm)
            } else {
                Spacer(Modifier.width(ArmRailSpec.Width))
            }
        }
        }
        }
        }
        }
        }
    }
}

private fun mainTabOrder(route: String?): Int? = when (topLevelTabForRoute(route)) {
    MainTab.CHAT -> 0
    MainTab.SETTINGS -> 1
    null -> null
}

/**
 * The direction a screen slides when pushed forward. Tab-to-tab movement is
 * order-relative (going to a higher-order tab comes in from the right), while any
 * detail/depth destination (a settings subpage, models, skills, a conversation)
 * always pushes in from the right and pops back out to the right.
 */
private fun pushTowards(initialRoute: String?, targetRoute: String?): AnimatedContentTransitionScope.SlideDirection {
    val i = mainTabOrder(initialRoute)
    val t = mainTabOrder(targetRoute)
    return when {
        i != null && t != null && t < i -> AnimatedContentTransitionScope.SlideDirection.Right
        else -> AnimatedContentTransitionScope.SlideDirection.Left
    }
}
