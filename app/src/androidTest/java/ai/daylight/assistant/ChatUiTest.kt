package ai.daylight.assistant

import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.swipeLeft
import ai.daylight.assistant.domain.ThemeMode
import ai.daylight.assistant.domain.AssistantMode
import ai.daylight.assistant.domain.AgentCapability
import ai.daylight.assistant.domain.BackgroundStyle
import ai.daylight.assistant.data.local.MessageEntity
import ai.daylight.assistant.ui.ChatScreen
import ai.daylight.assistant.ui.ChatViewModel
import ai.daylight.assistant.ui.SkillsToolsScreen
import ai.daylight.assistant.ui.SkillsToolsViewModel
import ai.daylight.assistant.ui.SettingsHomeScreen
import ai.daylight.assistant.ui.SettingsViewModel
import ai.daylight.assistant.ui.AppearanceSettingsScreen
import ai.daylight.assistant.ui.DaylightBackground
import ai.daylight.assistant.ui.theme.DaylightTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class ChatUiTest {
    @get:Rule val compose = createAndroidComposeRule<TestHostActivity>()

    @Test fun userCanSendMessage() {
        val container = (compose.activity.application as DaylightApplication).container
        val conversationId = runBlocking {
            container.clearAllLocalData()
            container.conversations.createConversation()
        }
        val viewModel = ChatViewModel(container, conversationId)
        compose.setContent {
            DaylightTheme(ThemeMode.SYSTEM) {
                ChatScreen(viewModel, container.conversations, onBack = {}, onModels = {})
            }
        }
        compose.onNodeWithText("Message Kryzz…").performTextInput("Hello from UI test")
        compose.onNodeWithContentDescription("Send").performClick()
        compose.waitUntil(10_000) {
            runBlocking { container.database.dao().messages(conversationId).any { it.role == "USER" } }
        }
        compose.runOnIdle { assertTrue(viewModel.composer.value.isEmpty()) }
        compose.onAllNodesWithText("Hello from UI test").onFirst().assertExists()
    }

    @Test fun chatConversationSwipeDoesNotTriggerHiddenNavigationOrAgentMode() {
        val container = (compose.activity.application as DaylightApplication).container
        val conversationId = runBlocking {
            container.clearAllLocalData()
            container.conversations.createConversation()
        }
        val viewModel = ChatViewModel(container, conversationId)
        var backRequests = 0
        compose.setContent {
            DaylightTheme(ThemeMode.SYSTEM) {
                ChatScreen(
                    viewModel,
                    container.conversations,
                    onBack = { backRequests++ },
                    onModels = {}
                )
            }
        }
        compose.onNodeWithText("How can I help?").assertIsDisplayed()
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        // Navigation is explicit through the library button or Android Back; a
        // horizontal gesture must not steal input from code and source carousels.
        assertTrue(backRequests == 0)
        compose.onNodeWithText("How can I help?").assertIsDisplayed()
        compose.onNodeWithText("Agent workspace").assertDoesNotExist()
    }

    @Test fun chatConversationDoesNotRequestAgentOnSwipe() {
        val container = (compose.activity.application as DaylightApplication).container
        val conversationId = runBlocking {
            container.clearAllLocalData()
            container.conversations.createConversation()
        }
        val viewModel = ChatViewModel(container, conversationId)
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

    @Test fun inlineModelPickerUsesADropdownAndKeepsCatalogLink() {
        val container = (compose.activity.application as DaylightApplication).container
        val conversationId = runBlocking {
            container.clearAllLocalData()
            container.conversations.createConversation()
        }
        val viewModel = ChatViewModel(container, conversationId)
        compose.setContent {
            DaylightTheme(ThemeMode.DARK) {
                ChatScreen(viewModel, container.conversations, onBack = {}, onModels = {})
            }
        }
        compose.onNodeWithContentDescription("AI controls").performClick()
        compose.onNodeWithText("AI controls").assertIsDisplayed()
        compose.onNodeWithTag("llm_dropdown").assertIsDisplayed().performClick()
        compose.onNodeWithText("Search models…").assertIsDisplayed()
        compose.onNodeWithText("Open catalog").assertExists()
    }

    @Test fun chatWorkspaceHasModeSwitcherThatEntersAgentMode() {
        val container = (compose.activity.application as DaylightApplication).container
        val conversationId = runBlocking {
            container.clearAllLocalData()
            container.conversations.createConversation()
        }
        val viewModel = ChatViewModel(container, conversationId)
        compose.setContent {
            DaylightTheme(ThemeMode.SYSTEM) {
                ChatScreen(viewModel, container.conversations, onBack = {}, onModels = {})
            }
        }
        compose.onNodeWithContentDescription("Chat mode").assertExists()
        compose.onNodeWithText("Message Kryzz…").assertIsDisplayed()
        compose.onNodeWithContentDescription("Agent mode").assertExists().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Agent workspace").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Give Kryzz a task…").assertIsDisplayed()
        compose.onNodeWithText("Message Kryzz…").assertDoesNotExist()
        compose.onNodeWithText("Workflow · Auto").assertIsDisplayed().performClick()
        compose.onNodeWithText("Choose workflow").assertIsDisplayed()
        compose.onNodeWithText("Wide search").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Video"))
        compose.onNodeWithText("Video").assertIsDisplayed()
    }

    @Test fun modeLocksAfterFirstMessageAndSwitcherBecomesNewChat() {
        val container = (compose.activity.application as DaylightApplication).container
        val conversationId = runBlocking {
            container.clearAllLocalData()
            container.conversations.createConversation()
        }
        val viewModel = ChatViewModel(container, conversationId)
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
        compose.onNodeWithContentDescription("Chat mode").assertExists()
        compose.onNodeWithContentDescription("Agent mode").assertExists()
        compose.onNodeWithText("Message Kryzz…").performTextInput("Lock me in")
        compose.onNodeWithContentDescription("Send").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Lock me in").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        // The first message locks the conversation into its mode: the switcher is removed.
        compose.onNodeWithContentDescription("Chat mode").assertDoesNotExist()
        compose.onNodeWithContentDescription("Agent mode").assertDoesNotExist()
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("Agent workspace").assertDoesNotExist()
        // The switcher has transformed into a New chat button for this mode.
        compose.onNodeWithContentDescription("New chat").assertExists().performClick()
        compose.waitForIdle()
        assertTrue(newChatRequests == 1)
    }

    @Test fun attachmentPickerIsAvailableInChatMode() {
        val container = (compose.activity.application as DaylightApplication).container
        val conversationId = runBlocking {
            container.clearAllLocalData()
            container.conversations.createConversation()
        }
        val viewModel = ChatViewModel(container, conversationId)
        compose.setContent {
            DaylightTheme(ThemeMode.DARK) {
                ChatScreen(viewModel, container.conversations, onBack = {}, onModels = {})
            }
        }
        compose.onNodeWithContentDescription("Attach images, videos, or files").assertIsDisplayed()
    }

    @Test fun agentChatExposesResearchDepthAndWidthControls() {
        val container = (compose.activity.application as DaylightApplication).container
        val conversationId = runBlocking {
            container.clearAllLocalData()
            container.conversations.createConversation()
        }
        val viewModel = ChatViewModel(container, conversationId)
        compose.setContent {
            DaylightTheme(ThemeMode.DARK) {
                ChatScreen(viewModel, container.conversations, onBack = {}, onModels = {})
            }
        }
        compose.onNodeWithContentDescription("AI controls").performClick()
        compose.onNodeWithText("Advanced").performScrollTo().performClick()
        compose.onNodeWithText("Research depth · 2").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Research width · 3").performScrollTo().assertIsDisplayed()
    }

    @Test fun skillsToolsTabShowsDeepSearchSkillMakerAndCode() {
        val container = (compose.activity.application as DaylightApplication).container
        runBlocking { container.clearAllLocalData() }
        val viewModel = SkillsToolsViewModel(container)
        compose.setContent {
            DaylightTheme(ThemeMode.DARK) {
                SkillsToolsScreen(viewModel, onBack = {}, onChats = {}, onLlms = {}, onSettings = {}, onStart = {})
            }
        }
        compose.onNodeWithText("Built-in tools").assertIsDisplayed()
        compose.onNodeWithText("Deep search").assertIsDisplayed()
        compose.onNodeWithText("Skill maker").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Full-stack code").performScrollTo().assertIsDisplayed()
    }

    @Test fun codeWorkspaceCanOpenDirectlyInAgentMode() {
        val container = (compose.activity.application as DaylightApplication).container
        val conversationId = runBlocking {
            container.clearAllLocalData()
            container.conversations.createConversation()
        }
        val viewModel = ChatViewModel(container, conversationId)
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
        compose.onNodeWithText("Full-stack code").assertIsDisplayed()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Full-stack code").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun settingsExposeAppearance() {
        compose.setContent {
            DaylightTheme(ThemeMode.DARK) {
                SettingsHomeScreen(onChats = {}, onLlms = {}, onSkills = {}, onOpen = {})
            }
        }
        compose.onNodeWithText("Appearance").assertIsDisplayed()
    }

    @Test fun assistantMessageShowsWholeConversationUsageBreakdown() {
        val container = (compose.activity.application as DaylightApplication).container
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
        compose.onNodeWithContentDescription("Conversation usage").performClick()
        compose.onNodeWithText("Total tokens").assertIsDisplayed()
        compose.onNodeWithText("150").assertIsDisplayed()
        compose.onNodeWithText("Cache hit").assertIsDisplayed()
        compose.onNodeWithText("70").assertIsDisplayed()
        compose.onNodeWithText("Cache miss").assertIsDisplayed()
        compose.onNodeWithText("50").assertIsDisplayed()
        compose.onNodeWithText("\$0.012345").assertIsDisplayed()
        compose.onNodeWithText("First token latency").assertIsDisplayed()
        compose.onNodeWithText("1.25 s").assertIsDisplayed()
        compose.onNodeWithText("Total response time").assertIsDisplayed()
        compose.onNodeWithText("4.80 s").assertIsDisplayed()
    }

    @Test fun emptyConversationCleanupKeepsConversationsWithMessages() {
        val container = (compose.activity.application as DaylightApplication).container
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
        val container = (compose.activity.application as DaylightApplication).container
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

    @Test fun appearanceKeepsOnlyTheSignatureConstellationControls() {
        val container = (compose.activity.application as DaylightApplication).container
        runBlocking { container.clearAllLocalData() }
        val viewModel = SettingsViewModel(container)
        compose.setContent {
            DaylightTheme(ThemeMode.DARK) { AppearanceSettingsScreen(viewModel, onBack = {}) }
        }
        compose.onNodeWithText("Obsidian Constellation").assertIsDisplayed()
        compose.onNodeWithText("Plain grey").assertDoesNotExist()
        compose.onNodeWithText("Original animated aurora").assertDoesNotExist()
        compose.onNodeWithText("Palette bubbles").assertDoesNotExist()
        compose.onNodeWithText("Video aurora").assertDoesNotExist()
        compose.onNodeWithText("Font style").assertDoesNotExist()
        compose.onNodeWithText("Glass panel opacity", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Background blur", substring = true).assertDoesNotExist()
    }

    @Test fun suppliedVideoAuroraCanRenderAsALoopingBackground() {
        compose.setContent {
            DaylightTheme(ThemeMode.DARK) {
                DaylightBackground(style = BackgroundStyle.VIDEO_AURORA) { Text("Video background ready") }
            }
        }
        compose.onNodeWithText("Video background ready").assertIsDisplayed()
    }

    @Test fun attachmentStoreCopiesAFileIntoPrivateStorage() {
        val container = (compose.activity.application as DaylightApplication).container
        val source = File(compose.activity.cacheDir, "attachment-test.txt").apply { writeText("private attachment") }
        val attachment = runBlocking { container.attachments.importUris(listOf(Uri.fromFile(source))).single() }
        assertTrue(attachment.localPath?.startsWith(compose.activity.filesDir.absolutePath) == true)
        assertTrue(File(attachment.localPath!!).readText() == "private attachment")
        assertTrue(runBlocking { container.attachments.base64(attachment) }.isNotBlank())
    }
}
