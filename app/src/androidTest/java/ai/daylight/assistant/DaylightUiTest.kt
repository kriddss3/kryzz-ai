package ai.daylight.assistant

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DaylightUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun resetLocalState() {
        runBlocking { (compose.activity.application as DaylightApplication).container.clearAllLocalData() }
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
    }

    @Test fun onboardingShowsSecureProviderSetup() {
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Your private AI workspace").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Your private AI workspace").assertIsDisplayed()
        compose.onNodeWithText("OpenRouter").assertIsDisplayed()
        compose.onNodeWithText("Parallel Search").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cross-chat memory").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Continue").performScrollTo().assertIsDisplayed()
    }

    @Test fun completedOnboardingShowsUsablePhoneNavigation() {
        val container = (compose.activity.application as DaylightApplication).container
        runBlocking {
            container.preferences.completeOnboarding()
            container.preferences.setAnimationsEnabled(false)
        }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Message Kryzz…").fetchSemanticsNodes().isNotEmpty()
        }

        // The shell is a left chat menu now: the chat header opens it and the
        // panel surfaces the conversation list plus the settings entry.
        compose.onNodeWithContentDescription("Open menu").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("library_panel").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("library_panel").assertIsDisplayed()
        compose.onNodeWithText("Search chats").assertIsDisplayed()
        compose.onNodeWithTag("library_new_chat").assertIsDisplayed()
        compose.onNodeWithTag("library_settings").assertIsDisplayed()
    }
}
