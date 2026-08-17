package ai.daylight.assistant.ui

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
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
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled
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
        fun openLibrary() {
            // Hard gate: never open the panel from a depth destination
            // (settings subpage, models, skills, etc.). The user has to
            // back out to a top-level screen first.
            if (!sidePanelSwipeEnabled) return
            panelOpen = true
        }
        fun openConversationFromLibrary(id: String) {
            panelOpen = false
            // Re-selecting the visible chat should only close the drawer. Replacing the
            // destination would dispose the same empty conversation and delete it.
            if (id == currentConversationId) return
            scope.launch {
                val (mode, capability) = rememberedRoute(id)
                activeConversationId = id
                withContext(Dispatchers.Main.immediate) {
                    openSingleConversation(Routes.chat(id, mode, capability))
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
                activeConversationId = id
                rememberedChatRoutes[id] = mode to capability
                withContext(Dispatchers.Main.immediate) {
                    openSingleConversation(Routes.chat(id, mode, capability, voice))
                }
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
        NavHost(
            navController = nav,
            startDestination = if (onboardingComplete) Routes.BOOT else Routes.ONBOARDING,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
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
                memoryCount = memoryCount
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
