package ai.daylight.assistant

import android.Manifest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
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
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import ai.daylight.assistant.data.local.MessageEntity
import ai.daylight.assistant.domain.ThemeMode
import ai.daylight.assistant.ui.AppNavigation
import ai.daylight.assistant.ui.ChatScreen
import ai.daylight.assistant.ui.ChatViewModel
import ai.daylight.assistant.ui.ConversationListViewModel
import ai.daylight.assistant.ui.KryzzLibraryPanel
import ai.daylight.assistant.ui.AppearanceSettingsScreen
import ai.daylight.assistant.ui.SettingsHomeScreen
import ai.daylight.assistant.ui.SettingsViewModel
import ai.daylight.assistant.ui.about.AboutScreen
import ai.daylight.assistant.ui.theme.DaylightTheme
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The whole shell (Home, the arm rail, the chat library drawer) plus the Tune and About
 * pages. Talk is reached the way a person does it: the Talk port on the Home board.
 */
class MidnightSignalUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<TestHostActivity>()

    private val container
        get() = (compose.activity.application as DaylightApplication).container

    @Before
    fun clearState() {
        runBlocking { container.clearAllLocalData() }
    }

    private fun conversationIds(): Set<String> = runBlocking {
        container.database.dao().allConversations().mapTo(mutableSetOf()) { it.id }
    }

    private fun waitForFreshConversation(previous: Set<String>): String {
        var next: String? = null
        compose.waitUntil(5_000) {
            next = (conversationIds() - previous).singleOrNull()
            next != null
        }
        return checkNotNull(next)
    }

    /** Starts the app shell on Home and opens Talk from its port on the board. */
    private fun openTalkFromHome() {
        compose.setContent {
            DaylightTheme(ThemeMode.DARK) {
                AppNavigation(container = container, onboardingComplete = true)
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasContentDescription("Ask, search, or make")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasContentDescription("Talk, ", substring = true)).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasContentDescription("Reply")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun typeDraft(text: String) {
        compose.onNode(hasSetTextAction()).performTextInput(text)
        compose.runOnIdle { compose.activity.currentFocus?.clearFocus() }
        compose.waitForIdle()
    }

    private fun waitForLibrary() {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("library_panel").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun pressBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun railTo(arm: String) {
        compose.onNodeWithContentDescription(arm).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    @Test
    fun talkKeepsItsDraftAcrossArmsAndItsTitleOpensTheLibrary() {
        openTalkFromHome()
        // The shell is the arm rail plus a left library drawer: no bottom tab bar exists.
        compose.onAllNodesWithTag("main_tab_chat").assertCountEquals(0)
        compose.onAllNodesWithTag("main_tab_settings").assertCountEquals(0)

        typeDraft("Keep this draft")

        compose.onNodeWithContentDescription("Talk. Open chat library").assertTouchTarget().performClick()
        waitForLibrary()
        compose.onNodeWithTag("library_new_chat").assertTouchTarget()
        compose.onNodeWithTag("library_search").assertIsDisplayed()
        compose.onNodeWithContentDescription("Backup and restore").assertTouchTarget()
        pressBack()
        compose.onAllNodesWithTag("library_panel").assertCountEquals(0)

        // Over to Tune on the rail and back: Talk returns to the same chat with its draft.
        railTo("Tune")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("THINKING MODE").fetchSemanticsNodes().isNotEmpty() }
        railTo("Talk")
        compose.waitUntil(5_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction()).assertTextContains("Keep this draft")
    }

    @Test
    fun libraryNewChatStartsFreshAndRowsReopenTheirDrafts() {
        runBlocking {
            val saved = container.conversations.createConversation()
            container.database.dao().upsertMessage(
                MessageEntity("saved-question", saved, "USER", "Saved question", System.currentTimeMillis())
            )
            container.conversations.rename(saved, "Saved chat")
        }
        openTalkFromHome()

        compose.onNodeWithContentDescription("Talk. Open chat library").performClick()
        waitForLibrary()
        compose.onNodeWithText("Saved chat").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Saved question").fetchSemanticsNodes().isNotEmpty() }
        typeDraft("Keep the saved draft")

        val previous = conversationIds()
        compose.onNodeWithContentDescription("Talk options").performClick()
        compose.onNodeWithText("New chat").performScrollTo().performClick()
        waitForFreshConversation(previous)
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasContentDescription("Talk. Open chat library")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Keep the saved draft").assertDoesNotExist()

        compose.onNodeWithContentDescription("Talk. Open chat library").performClick()
        waitForLibrary()
        compose.onNodeWithText("Saved chat").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Saved question").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction()).assertTextContains("Keep the saved draft")
    }

    @Test
    fun voiceChatOpensFromTalkOptionsAndCloses() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            compose.activity.packageName,
            Manifest.permission.RECORD_AUDIO
        )
        openTalkFromHome()
        compose.onNodeWithContentDescription("Talk options").performClick()
        compose.onNodeWithText("Voice chat").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("voice_overlay").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("voice_primary_action").assertTouchTarget()
        compose.onNodeWithTag("voice_mute_toggle").assertIsDisplayed()
        compose.onNodeWithTag("voice_close_button").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("voice_overlay").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun emptyTalkShowsItsTitleAndAQuietReplyLine() {
        val conversationId = runBlocking { container.conversations.createConversation() }
        val viewModel = ChatViewModel(container, conversationId)

        compose.setContent {
            DaylightTheme(ThemeMode.DARK) {
                ChatScreen(viewModel, container.conversations, onBack = {}, onModels = {})
            }
        }

        compose.onNodeWithContentDescription("Talk. Open chat library").assertIsDisplayed()
        compose.onNodeWithContentDescription("Reply").assertIsDisplayed()
        compose.onNodeWithText("Research a topic").assertDoesNotExist()
        compose.onNodeWithText("Analyse a file").assertDoesNotExist()
        compose.onNodeWithText("Plan a project").assertDoesNotExist()
        compose.onNodeWithContentDescription("Talk options").assertTouchTarget()
        compose.onNodeWithContentDescription("Dictation").assertTouchTarget()
        // Model and attachments live in the options sheet, not on the screen.
        compose.onNodeWithText("AI controls").assertDoesNotExist()
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

        compose.onNodeWithText("AI controls").assertDoesNotExist()
        compose.onNodeWithContentDescription("Talk options").performClick()
        compose.onNodeWithText("Model").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("AI controls").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("AI controls").assertIsDisplayed()
        compose.onNodeWithText("EVERYDAY MODEL").assertIsDisplayed()
        compose.onNodeWithText("Open catalog").assertIsDisplayed()
        compose.onNodeWithText("Advanced").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("RESEARCH DEPTH").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("RESEARCH WIDTH").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Chat memory").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun sentTalkShowsUpInTheLibrary() {
        openTalkFromHome()
        compose.onAllNodesWithTag("library_panel").assertCountEquals(0)
        compose.onNode(hasSetTextAction()).performTextInput("First message")
        compose.onNodeWithContentDescription("Send").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("First message").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithTag("library_panel").assertCountEquals(0)
        // A chat with a message opens the library from Talk options (the title is gone).
        compose.onNodeWithContentDescription("Talk options").performClick()
        compose.onNodeWithText("Chats").performScrollTo().performClick()
        waitForLibrary()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("library_conversation_row").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithTag("library_conversation_row").onFirst().assertTextContains("First message", substring = true)
    }

    @Test
    fun drawerUsesSearchFoldersBackupAndRows() {
        val viewModel = ConversationListViewModel(container)

        compose.setContent {
            DaylightTheme(ThemeMode.DARK) {
                KryzzLibraryPanel(
                    vm = viewModel,
                    onOpen = {},
                    onNewChat = {}
                )
            }
        }

        compose.onNodeWithContentDescription("Chats").assertIsDisplayed()
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

    // C11: three quick taps on the drawer's CHATS title open the About page (a route now,
    // not the galaxy dialog), and the page keeps the version, the maker and the licences.
    @Test
    fun tripleTappingDrawerLogoRevealsGalacticAppCredits() {
        val viewModel = ConversationListViewModel(container)
        var aboutOpened = 0
        compose.setContent {
            DaylightTheme(ThemeMode.DARK) {
                KryzzLibraryPanel(viewModel, onOpen = {}, onNewChat = {}, onAbout = { aboutOpened++ })
            }
        }

        repeat(3) { compose.onNodeWithContentDescription("Chats").performClick() }
        compose.runOnIdle { assertEquals(1, aboutOpened) }
    }

    @Test
    fun aboutPageShowsVersionMakerAndFontLicences() {
        compose.setContent {
            DaylightTheme(ThemeMode.DARK) {
                AboutScreen(onBack = {})
            }
        }

        compose.onNodeWithText("Version", substring = true, ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("Created and coded by Kryzz").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertTouchTarget()
        compose.onNodeWithTag("about_licences").performScrollTo().assertTouchTarget().performClick()
        compose.onNodeWithText("FONT LICENCES").assertIsDisplayed()
    }

    @Test
    fun tuneUsesScanFriendlySectionsAndOpensTheCatalog() {
        var catalogOpened = 0
        compose.setContent {
            DaylightTheme(ThemeMode.DARK) {
                SettingsHomeScreen(onChats = {}, onLlms = { catalogOpened++ }, onSkills = {}, onOpen = {})
            }
        }

        compose.onNodeWithText("THINKING MODE").assertIsDisplayed()
        compose.onNodeWithText("MODELS AND KEYS").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("PRIVACY").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("APPEARANCE").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("MORE").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Version ${BuildConfig.VERSION_NAME}", substring = true, ignoreCase = true)
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Model catalog").performScrollTo().assertTouchTarget().performClick()
        compose.runOnIdle { assertEquals(1, catalogOpened) }
        // Tune is an arm, not a depth page: it has no Back button.
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    @Test
    fun textAndMotionExposesChatDensityAndMotionControls() {
        val viewModel = SettingsViewModel(container)

        compose.setContent {
            DaylightTheme(ThemeMode.DARK) {
                AppearanceSettingsScreen(viewModel, onBack = {})
            }
        }

        compose.onNodeWithText("CHAT DENSITY").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("chat_density_compact").assertTouchTarget()
        compose.onNodeWithTag("chat_density_comfortable").assertTouchTarget()
        compose.onNodeWithTag("chat_density_spacious").assertTouchTarget()
        compose.onNodeWithText("MOTION AND BACKGROUND").performScrollTo().assertIsDisplayed()
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
