package ai.daylight.assistant

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import ai.daylight.assistant.domain.ThemeMode
import ai.daylight.assistant.domain.AssistantMode
import ai.daylight.assistant.domain.AgentCapability
import ai.daylight.assistant.data.local.MessageEntity
import ai.daylight.assistant.ui.ChatScreen
import ai.daylight.assistant.ui.ChatViewModel
import ai.daylight.assistant.ui.SkillsToolsScreen
import ai.daylight.assistant.ui.SkillsToolsViewModel
import ai.daylight.assistant.ui.SettingsHomeScreen
import ai.daylight.assistant.ui.SettingsViewModel
import ai.daylight.assistant.ui.AppearanceSettingsScreen
import ai.daylight.assistant.ui.theme.DaylightTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * The chat screen on its own: Talk (CHAT mode) and Run (AGENT mode) with their reply line,
 * options sheets, AI controls and usage dialog, plus the Tune pages they link to.
 */
class ChatUiTest {
    @get:Rule val compose = createAndroidComposeRule<TestHostActivity>()

    private val container
        get() = (compose.activity.application as DaylightApplication).container

    private fun freshChat(): ChatViewModel {
        val id = runBlocking {
            container.clearAllLocalData()
            container.conversations.createConversation()
        }
        return ChatViewModel(container, id)
    }

    private fun openTalkOptions() {
        compose.onNodeWithContentDescription("Talk options").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Voice chat").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun openAiControls() {
        openTalkOptions()
        compose.onNodeWithText("Model").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("AI controls").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun userCanSendMessage() {
        val viewModel = freshChat()
        compose.setContent {
            DaylightTheme(ThemeMode.SYSTEM) {
                ChatScreen(viewModel, container.conversations, onBack = {}, onModels = {})
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("Hello from UI test")
        compose.onNodeWithContentDescription("Send").performClick()
        compose.waitUntil(10_000) {
            runBlocking { container.database.dao().messages(viewModel.conversationId).any { it.role == "USER" } }
        }
        compose.runOnIdle { assertTrue(viewModel.composer.value.isEmpty()) }
        compose.onAllNodesWithText("Hello from UI test").onFirst().assertExists()
    }

    @Test fun talkSwipeDoesNotTriggerHiddenNavigationOrRun() {
        val viewModel = freshChat()
        var backRequests = 0
        compose.setContent {
            DaylightTheme(ThemeMode.SYSTEM) {
                ChatScreen(viewModel, container.conversations, onBack = { backRequests++ }, onModels = {})
            }
        }
        compose.onNodeWithContentDescription("Talk. Open chat library").assertIsDisplayed()
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        // Navigation is explicit (the rail, the drawer or Android Back); a sideways swipe
        // must not steal input from code blocks and source rows.
        assertTrue(backRequests == 0)
        compose.onNodeWithContentDescription("Talk. Open chat library").assertIsDisplayed()
        compose.onNodeWithContentDescription("Give Kryzz a task").assertDoesNotExist()
    }

    @Test fun chatConversationDoesNotRequestAgentOnSwipe() {
        val viewModel = freshChat()
        var requestedAgentChats = 0
        compose.setContent {
            DaylightTheme(ThemeMode.SYSTEM) {
                ChatScreen(
                    viewModel,
                    container.conversations,
                    onBack = {},
                    onModels = {},
                    onStartAgent = { requestedAgentChats++; true }
                )
            }
        }
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertTrue(requestedAgentChats == 0)
    }

    @Test fun modelRowOpensAiControlsWithADropdownAndCatalogLink() {
        val viewModel = freshChat()
        compose.setContent {
            DaylightTheme(ThemeMode.DARK) {
                ChatScreen(viewModel, container.conversations, onBack = {}, onModels = {})
            }
        }
        openAiControls()
        compose.onNodeWithText("AI controls").assertIsDisplayed()
        compose.onNodeWithTag("llm_dropdown").assertIsDisplayed().performClick()
        compose.onNodeWithText("Search models…").assertIsDisplayed()
        compose.onNodeWithText("Open catalog").assertExists()
    }

    @Test fun talkOptionsSwitchAFreshChatToRunAndItsWorkflows() {
        val viewModel = freshChat()
        compose.setContent {
            DaylightTheme(ThemeMode.SYSTEM) {
                ChatScreen(viewModel, container.conversations, onBack = {}, onModels = {})
            }
        }
        compose.onNodeWithContentDescription("Reply").assertIsDisplayed()
        openTalkOptions()
        compose.onNodeWithText("MODE").assertIsDisplayed()
        compose.onNode(hasText("RUN") and hasClickAction()).performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasContentDescription("Give Kryzz a task")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Reply").assertDoesNotExist()
        compose.onNodeWithContentDescription("Run. Open chat library").assertIsDisplayed()
        compose.onNodeWithContentDescription("Workflow Auto. Change").assertIsDisplayed().performClick()
        compose.onNodeWithText("Choose a workflow").assertIsDisplayed()
        compose.onNodeWithText("Wide search").assertIsDisplayed()
        compose.onNode(hasScrollAction() and hasAnyDescendant(hasText("Wide search")))
            .performScrollToNode(hasText("Video"))
        compose.onNodeWithText("Video").assertIsDisplayed()
    }

    @Test fun modeLocksAfterTheFirstMessageAndOptionsOfferNewChat() {
        val viewModel = freshChat()
        var newChatRequests = 0
        compose.setContent {
            DaylightTheme(ThemeMode.SYSTEM) {
                ChatScreen(
                    viewModel,
                    container.conversations,
                    onBack = {},
                    onModels = {},
                    onNewChat = { newChatRequests++ }
                )
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("Lock me in")
        compose.onNodeWithContentDescription("Send").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Lock me in").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        // The first message locks the chat into Talk: the options sheet drops the switch.
        openTalkOptions()
        compose.onNodeWithText("MODE").assertDoesNotExist()
        compose.onNode(hasText("RUN") and hasClickAction()).assertDoesNotExist()
        compose.onNodeWithText("New chat").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(newChatRequests == 1)
    }

    @Test fun talkOptionsOfferImageCameraAndFileAttachments() {
        val viewModel = freshChat()
        compose.setContent {
            DaylightTheme(ThemeMode.DARK) {
                ChatScreen(viewModel, container.conversations, onBack = {}, onModels = {})
            }
        }
        openTalkOptions()
        compose.onNodeWithText("ATTACH").assertIsDisplayed()
        listOf("IMAGE", "CAMERA", "FILE").forEach { label ->
            compose.onNode(hasText(label) and hasClickAction()).assertIsDisplayed()
        }
    }

    @Test fun aiControlsExposeResearchDepthAndWidth() {
        val viewModel = freshChat()
        compose.setContent {
            DaylightTheme(ThemeMode.DARK) {
                ChatScreen(viewModel, container.conversations, onBack = {}, onModels = {})
            }
        }
        openAiControls()
        compose.onNodeWithText("RESEARCH DEPTH").assertDoesNotExist()
        compose.onNodeWithText("Advanced").performScrollTo().performClick()
        compose.onNodeWithText("RESEARCH DEPTH").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("RESEARCH WIDTH").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("search context", substring = true, ignoreCase = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun skillsPageShowsDeepSearchSkillMakerAndCode() {
        runBlocking { container.clearAllLocalData() }
        val viewModel = SkillsToolsViewModel(container)
        compose.setContent {
            DaylightTheme(ThemeMode.DARK) {
                SkillsToolsScreen(viewModel, onBack = {}, onChats = {}, onLlms = {}, onSettings = {}, onStart = {})
            }
        }
        compose.onNodeWithText("BUILT-IN TOOLS").assertIsDisplayed()
        compose.onNodeWithText("Deep search").assertIsDisplayed()
        val list = compose.onNode(hasScrollAction())
        list.performScrollToNode(hasText("Skill maker"))
        compose.onNodeWithText("Skill maker").assertIsDisplayed()
        list.performScrollToNode(hasText("Full-stack code"))
        compose.onNodeWithText("Full-stack code").assertIsDisplayed()
    }

    @Test fun codeWorkflowCanOpenDirectlyInRun() {
        val viewModel = freshChat()
        compose.setContent {
            DaylightTheme(ThemeMode.DARK) {
                ChatScreen(
                    viewModel,
                    container.conversations,
                    initialMode = AssistantMode.AGENT,
                    initialCapability = AgentCapability.CODE,
                    onBack = {},
                    onModels = {}
                )
            }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasContentDescription("Workflow Code. Change")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Workflow Code. Change").assertIsDisplayed()
        compose.onNodeWithContentDescription("Give Kryzz a task").assertIsDisplayed()
    }

    @Test fun tuneExposesAppearance() {
        compose.setContent {
            DaylightTheme(ThemeMode.DARK) {
                SettingsHomeScreen(onChats = {}, onLlms = {}, onSkills = {}, onOpen = {})
            }
        }
        compose.onNodeWithText("APPEARANCE").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Text and motion").performScrollTo().assertIsDisplayed()
    }

    @Test fun answerActionsShowWholeConversationUsageBreakdown() {
        val conversationId = runBlocking {
            container.clearAllLocalData()
            val id = container.conversations.createConversation()
            container.database.dao().upsertMessage(
                MessageEntity(
                    id = "answer-usage",
                    conversationId = id,
                    role = "ASSISTANT",
                    content = "Measured answer",
                    createdAt = System.currentTimeMillis(),
                    promptTokens = 120,
                    completionTokens = 30,
                    totalTokens = 150,
                    cost = 0.012345,
                    cachedInputTokens = 70,
                    firstTokenLatencyMs = 1_250,
                    totalGenerationTimeMs = 4_800
                )
            )
            id
        }
        val viewModel = ChatViewModel(container, conversationId)
        compose.setContent {
            DaylightTheme(ThemeMode.DARK) {
                ChatScreen(viewModel, container.conversations, onBack = {}, onModels = {})
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Measured answer").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Measured answer").performTouchInput { longClick() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Usage").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Usage").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("TOTAL TOKENS").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("150").assertIsDisplayed()
        compose.onNodeWithText("CACHE HIT").assertIsDisplayed()
        compose.onNodeWithText("70").assertIsDisplayed()
        compose.onNodeWithText("CACHE MISS").assertIsDisplayed()
        compose.onNodeWithText("50").assertIsDisplayed()
        compose.onNodeWithText("\$0.012345").assertIsDisplayed()
        compose.onNodeWithText("FIRST TOKEN").assertIsDisplayed()
        compose.onNodeWithText("1.25 s").assertIsDisplayed()
        compose.onNodeWithText("RESPONSE TIME").assertIsDisplayed()
        compose.onNodeWithText("4.80 s").assertIsDisplayed()
    }

    @Test fun emptyConversationCleanupKeepsConversationsWithMessages() {
        runBlocking {
            container.clearAllLocalData()
            val abandoned = container.conversations.createConversation()
            val kept = container.conversations.createConversation()
            container.database.dao().upsertMessage(
                MessageEntity("abandoned-stream", abandoned, "ASSISTANT", "", System.currentTimeMillis(), status = "STREAMING")
            )
            container.database.dao().upsertMessage(
                MessageEntity("kept-user", kept, "USER", "hello", System.currentTimeMillis())
            )
            container.conversations.deleteEmpty()
            check(container.conversations.conversation(abandoned) == null)
            check(container.conversations.conversation(kept) != null)
        }
    }

    @Test fun repeatedModeSwitchCleanupKeepsOnlyTheActiveEmptyConversation() {
        runBlocking {
            container.clearAllLocalData()
            val abandonedOne = container.conversations.createConversation()
            val active = container.conversations.createConversation()
            val abandonedTwo = container.conversations.createConversation()
            val filled = container.conversations.createConversation()
            container.database.dao().upsertMessage(
                MessageEntity("filled-user", filled, "USER", "keep me", System.currentTimeMillis())
            )

            repeat(4) { container.conversations.deleteEmptyExcept(active) }

            val remaining = container.database.dao().allConversations().map { it.id }.toSet()
            check(abandonedOne !in remaining)
            check(abandonedTwo !in remaining)
            check(active in remaining)
            check(filled in remaining)
            check(!container.conversations.hasContent(active))
            check(container.conversations.hasContent(filled))
        }
    }

    // The redesign keeps one look: theme, chat density, text size and motion. The old
    // background, palette, font and glass options must stay gone.
    @Test fun textAndMotionPageKeepsOnlyTheKitControls() {
        runBlocking { container.clearAllLocalData() }
        val viewModel = SettingsViewModel(container)
        compose.setContent {
            DaylightTheme(ThemeMode.DARK) { AppearanceSettingsScreen(viewModel, onBack = {}) }
        }
        compose.onNodeWithText("THEME").assertIsDisplayed()
        compose.onNodeWithText("CHAT DENSITY").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Text size", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Animations").performScrollTo().assertIsDisplayed()
        listOf(
            "Obsidian Constellation", "Plain grey", "Original animated aurora", "Palette bubbles",
            "Video aurora", "Font style", "Glass panel opacity", "Background blur"
        ).forEach { gone -> compose.onNodeWithText(gone, substring = true).assertDoesNotExist() }
    }

    @Test fun attachmentStoreCopiesAFileIntoPrivateStorage() {
        val source = File(compose.activity.cacheDir, "attachment-test.txt").apply { writeText("private attachment") }
        val attachment = runBlocking { container.attachments.importUris(listOf(Uri.fromFile(source))).single() }
        assertTrue(attachment.localPath?.startsWith(compose.activity.filesDir.absolutePath) == true)
        assertTrue(File(attachment.localPath!!).readText() == "private attachment")
        assertTrue(runBlocking { container.attachments.base64(attachment) }.isNotBlank())
    }
}
