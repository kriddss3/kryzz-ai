package ai.daylight.assistant

import android.Manifest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import ai.daylight.assistant.domain.BackgroundStyle
import ai.daylight.assistant.domain.ThemeMode
import ai.daylight.assistant.ui.AppNavigation
import ai.daylight.assistant.ui.ChatScreen
import ai.daylight.assistant.ui.ChatViewModel
import ai.daylight.assistant.ui.ConversationListViewModel
import ai.daylight.assistant.ui.KryzzLibraryPanel
import ai.daylight.assistant.ui.AppearanceSettingsScreen
import ai.daylight.assistant.ui.SettingsHomeScreen
import ai.daylight.assistant.ui.SettingsViewModel
import ai.daylight.assistant.ui.theme.DaylightTheme
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MidnightSignalUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<TestHostActivity>()

    private val container
        get() = (compose.activity.application as DaylightApplication).container

    @Before
    fun clearState() {
        runBlocking { container.clearAllLocalData() }
    }

    @Test
    fun libraryPanelOpensFromChatAndKeepsDraftAfterSettingsRoundTrip() {
        compose.setContent {
            DaylightTheme(ThemeMode.DARK) {
                AppNavigation(
                    container = container,
                    onboardingComplete = true,
                    backgroundStyle = BackgroundStyle.PLAIN_GREY,
                    backgroundBlur = 0f
                )
            }
        }

        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Message Kryzz…").fetchSemanticsNodes().isNotEmpty()
        }
        // The shell is a left library panel: no bottom tab bar exists.
        compose.onAllNodesWithTag("main_tab_chat").assertCountEquals(0)
        compose.onAllNodesWithTag("main_tab_library").assertCountEquals(0)
        compose.onAllNodesWithTag("main_tab_settings").assertCountEquals(0)

        compose.onNodeWithText("Message Kryzz…").performTextInput("Keep this draft")
        compose.runOnIdle {
            compose.activity.currentFocus?.clearFocus()
        }
        compose.waitForIdle()

        // The chat header opens the menu, which lists conversations and settings.
        compose.onNodeWithContentDescription("Open menu").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("library_panel").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("library_new_chat").assertTouchTarget()
        compose.onNodeWithTag("library_new_agent").assertTouchTarget()
        compose.onNodeWithTag("library_new_voice").assertTouchTarget()
        compose.onNodeWithContentDescription("Close menu").assertTouchTarget()
        compose.onAllNodesWithTag("library_conversation_row").onFirst().assertTouchTarget()

        // Settings lives at the bottom of the panel; opening it keeps the draft,
        // and returning to the same conversation restores it.
        compose.onNodeWithTag("library_settings").assertTouchTarget().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Appearance").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Open menu").assertTouchTarget().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("library_panel").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("New conversation").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasSetTextAction()).assertTextContains("Keep this draft")
        compose.onNodeWithText("LLM's").assertDoesNotExist()
        compose.onNodeWithText("Skills & tools").assertDoesNotExist()
    }

    @Test
    fun panelModeActionsCreateFreshConversationsDirectly() {
        compose.setContent {
            DaylightTheme(ThemeMode.DARK) {
                AppNavigation(
                    container = container,
                    onboardingComplete = true,
                    backgroundStyle = BackgroundStyle.PLAIN_GREY,
                    backgroundBlur = 0f
                )
            }
        }

        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Message Kryzz…").fetchSemanticsNodes().isNotEmpty()
        }
        fun conversationIds(): Set<String> = runBlocking {
            container.database.dao().allConversations().mapTo(mutableSetOf()) { it.id }
        }
        fun waitForFreshConversation(previous: Set<String>): String {
            var next: String? = null
            compose.waitUntil(5_000) {
                next = (conversationIds() - previous).singleOrNull()
                next != null
            }
            return checkNotNull(next)
        }

        val initial = checkNotNull(conversationIds().singleOrNull())
        compose.onNode(hasSetTextAction()).performTextInput("Keep the first draft")
        compose.runOnIdle { compose.activity.currentFocus?.clearFocus() }
        compose.waitForIdle()

        var previous = conversationIds()
        compose.onNodeWithContentDescription("Open menu").performClick()
        compose.onNodeWithTag("library_new_chat").performClick()
        waitForFreshConversation(previous)
        compose.onNodeWithContentDescription("Chat mode").assertIsSelected()
        assertFalse(runBlocking { container.conversations.conversation(initial) } == null)

        previous = conversationIds()
        compose.onNodeWithContentDescription("Open menu").performClick()
        compose.onNodeWithTag("library_new_agent").performClick()
        val agent = waitForFreshConversation(previous)
        compose.onNodeWithContentDescription("Agent mode").assertIsSelected()
        compose.onNode(hasSetTextAction()).performTextInput("Keep the agent draft")
        compose.runOnIdle { compose.activity.currentFocus?.clearFocus() }
        compose.waitForIdle()
        runBlocking { container.conversations.rename(agent, "Agent draft") }

        previous = conversationIds()
        compose.onNodeWithContentDescription("Open menu").performClick()
        compose.onNodeWithTag("library_new_chat").performClick()
        waitForFreshConversation(previous)
        compose.onNodeWithContentDescription("Chat mode").assertIsSelected()
        assertFalse(runBlocking { container.conversations.conversation(agent) } == null)

        compose.onNodeWithContentDescription("Open menu").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Agent draft").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Agent draft").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Agent mode").assertIsSelected()
        compose.onNode(hasSetTextAction()).assertTextContains("Keep the agent draft")

        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            compose.activity.packageName,
            Manifest.permission.RECORD_AUDIO
        )
        previous = conversationIds()
        compose.onNodeWithContentDescription("Open menu").performClick()
        compose.onNodeWithTag("library_new_voice").performClick()
        waitForFreshConversation(previous)
        compose.onNodeWithContentDescription("Chat mode").assertIsSelected()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("voice_overlay").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("voice_primary_action").assertTouchTarget()
        compose.onNodeWithText("VOICE SESSION").assertIsDisplayed()
        compose.onNodeWithText("End voice chat").assertTouchTarget().performClick()
    }

    @Test
    fun drawerKeepsDraftConversationRestorableFromChatHome() {
        compose.setContent {
            DaylightTheme(ThemeMode.DARK) {
                AppNavigation(
                    container = container,
                    onboardingComplete = true,
                    backgroundStyle = BackgroundStyle.PLAIN_GREY,
                    backgroundBlur = 0f
                )
            }
        }

        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Message Kryzz…").fetchSemanticsNodes().isNotEmpty()
        }
        val conversationId = checkNotNull(runBlocking {
            container.database.dao().allConversations().singleOrNull()?.id
        })
        compose.onNodeWithText("Message Kryzz…").performTextInput("Keep after Back")
        compose.runOnIdle { compose.activity.currentFocus?.clearFocus() }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Open menu").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("library_conversation_row").fetchSemanticsNodes().isNotEmpty()
        }
        assertFalse(runBlocking { container.conversations.conversation(conversationId) } == null)
        compose.onAllNodesWithTag("library_conversation_row").onFirst().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasSetTextAction()).assertTextContains("Keep after Back")
    }

    @Test
    fun emptyChatUsesACleanGreetingAndCompactComposer() {
        val conversationId = runBlocking { container.conversations.createConversation() }
        val viewModel = ChatViewModel(container, conversationId)

        compose.setContent {
            DaylightTheme(ThemeMode.DARK) {
                ChatScreen(viewModel, container.conversations, onBack = {}, onModels = {})
            }
        }

        compose.onNodeWithText("How can I help?").assertIsDisplayed()
        compose.onNodeWithText("Ask anything. Your conversations stay on this device.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Chat mode").assertIsSelected()
        compose.onNodeWithText("Research a topic").assertDoesNotExist()
        compose.onNodeWithText("Analyse a file").assertDoesNotExist()
        compose.onNodeWithText("Message Kryzz…").assertIsDisplayed()
        compose.onNodeWithText("Plan a project").assertDoesNotExist()
        compose.onNodeWithContentDescription("Attach images, videos, or files").assertTouchTarget()
        compose.onNodeWithContentDescription("AI controls").assertTouchTarget()
        compose.onNodeWithContentDescription("Voice chat").assertTouchTarget()
    }

    @Test
    fun aiControlsAreHiddenUntilRequested() {
        val conversationId = runBlocking { container.conversations.createConversation() }
        val viewModel = ChatViewModel(container, conversationId)

        compose.setContent {
            DaylightTheme(ThemeMode.DARK) {
                ChatScreen(viewModel, container.conversations, onBack = {}, onModels = {})
            }
        }

        compose.onNodeWithText("Provider").assertDoesNotExist()
        compose.onNodeWithContentDescription("AI controls").performClick()
        compose.onNodeWithText("AI controls").assertIsDisplayed()
        compose.onNodeWithText("Model").assertIsDisplayed()
        compose.onNodeWithText("Provider").assertIsDisplayed()
        compose.onNodeWithText("Open catalog").assertIsDisplayed()
        compose.onNodeWithText("Advanced").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Research depth", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Research width", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Chat memory").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun rootMenuHidesWhileTypingAndAfterTheFirstMessage() {
        compose.setContent {
            DaylightTheme(ThemeMode.DARK) {
                AppNavigation(
                    container = container,
                    onboardingComplete = true,
                    backgroundStyle = BackgroundStyle.PLAIN_GREY,
                    backgroundBlur = 0f
                )
            }
        }

        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Message Kryzz…").fetchSemanticsNodes().isNotEmpty()
        }
        // No persistent bottom bar: the root menu is the on-demand library panel.
        compose.onAllNodesWithTag("library_panel").assertCountEquals(0)
        compose.onNodeWithText("Message Kryzz…").performClick().performTextInput("First message")
        compose.waitForIdle()
        compose.onAllNodesWithTag("library_panel").assertCountEquals(0)
        compose.onNodeWithContentDescription("Send").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("First message").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithTag("library_panel").assertCountEquals(0)
        // The sent conversation appears in the panel's chat list.
        compose.onNodeWithContentDescription("Open menu").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("library_panel").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithTag("library_conversation_row").onFirst().assertTextContains("First message")
    }

    @Test
    fun drawerUsesChatFiltersFoldersAndRows() {
        val viewModel = ConversationListViewModel(container)

        compose.setContent {
            DaylightTheme(ThemeMode.DARK) {
                KryzzLibraryPanel(
                    vm = viewModel,
                    onOpen = {},
                    onNewChat = {},
                    onNewVoice = {},
                    onSettings = {}
                )
            }
        }

        compose.onNodeWithText("KRYZZ AI").assertIsDisplayed()
        compose.onNodeWithText("Search chats").assertIsDisplayed()
        compose.onNodeWithTag("library_new_chat").assertTouchTarget()
        compose.onNodeWithText("Archived").assertDoesNotExist()
        compose.onNodeWithContentDescription("Create folder").assertTouchTarget()
        compose.onNodeWithContentDescription("Backup and restore").assertTouchTarget().performClick()
        compose.onNodeWithText("Export chat backup").assertIsDisplayed()
        compose.onNodeWithText("Import chat backup").assertIsDisplayed()
        compose.onNodeWithText("Files").assertDoesNotExist()
        compose.onNodeWithText("Saved").assertDoesNotExist()
    }

    @Test
    fun tripleTappingDrawerLogoRevealsGalacticAppCredits() {
        val viewModel = ConversationListViewModel(container)
        compose.setContent {
            DaylightTheme(ThemeMode.DARK) {
                KryzzLibraryPanel(viewModel, onOpen = {}, onNewChat = {}, onNewVoice = {}, onSettings = {})
            }
        }

        repeat(3) { compose.onNodeWithContentDescription("Kryzz AI logo").performClick() }
        compose.onNodeWithText("OBSIDIAN CONSTELLATION").assertIsDisplayed()
        compose.onNodeWithText("Version", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Created and coded by Kryzz").assertIsDisplayed()
        compose.onNodeWithText("Return to the stars").assertTouchTarget()
    }

    @Test
    fun settingsHomeUsesScanFriendlySectionsAndHidesTechnicalOptions() {
        var openedRoute: String? = null
        compose.setContent {
            DaylightTheme(ThemeMode.DARK) {
                SettingsHomeScreen(onChats = {}, onLlms = {}, onSkills = {}, onOpen = { openedRoute = it })
            }
        }

        compose.onNodeWithText("Appearance").assertIsDisplayed()
        compose.onNodeWithText("AI and models").assertIsDisplayed()
        compose.onNodeWithText("Tools and connections").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Data and privacy").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Advanced").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Version", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Model catalog").performScrollTo().assertTouchTarget().performClick()
        compose.runOnIdle { assertEquals(ai.daylight.assistant.ui.Routes.LLMS, openedRoute) }
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    @Test
    fun appearanceExposesChatDensityAndMotionControls() {
        val viewModel = SettingsViewModel(container)

        compose.setContent {
            DaylightTheme(ThemeMode.DARK) {
                AppearanceSettingsScreen(viewModel, onBack = {})
            }
        }

        compose.onNodeWithText("Chat density").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("chat_density_compact").assertTouchTarget()
        compose.onNodeWithTag("chat_density_comfortable").assertTouchTarget()
        compose.onNodeWithTag("chat_density_spacious").assertTouchTarget()
        compose.onNodeWithText("Motion").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("animations_toggle").assertTouchTarget()
    }

    @Test
    fun themeDisablesMotionWhenUserTurnsAnimationsOff() {
        var motionEnabled = true

        compose.setContent {
            DaylightTheme(mode = ThemeMode.DARK, animationsEnabled = false) {
                motionEnabled = LocalKryzzMotionEnabled.current
            }
        }

        compose.waitForIdle()
        assertFalse(motionEnabled)
    }
}

private fun SemanticsNodeInteraction.assertTouchTarget(): SemanticsNodeInteraction =
    assertHasClickAction().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
