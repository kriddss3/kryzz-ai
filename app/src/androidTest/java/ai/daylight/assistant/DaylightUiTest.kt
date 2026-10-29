package ai.daylight.assistant

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

    // C11: onboarding is four kit steps. The keys step holds Continue until an OpenRouter
    // key is in, and Start saves the key and lands on Home.
    @Test fun onboardingShowsSecureProviderSetup() {
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Step 1 of 4").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Local first").assertIsDisplayed()
        compose.onNodeWithText("Chats stay on this phone").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("onboarding_continue").assertIsEnabled().performClick()

        compose.onNodeWithContentDescription("Step 2 of 4").assertIsDisplayed()
        compose.onNodeWithContentDescription("Your keys").assertIsDisplayed()
        compose.onNodeWithTag("onboarding_continue").assertIsNotEnabled()
        compose.onNodeWithText("Parallel").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Fish Audio").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("openrouter_key").performScrollTo().performTextInput("sk-or-ui-test")
        compose.onNodeWithTag("onboarding_continue").assertIsEnabled().performClick()

        compose.onNodeWithContentDescription("Step 3 of 4").assertIsDisplayed()
        compose.onNodeWithText("Cross-chat memory").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("onboarding_continue").performClick()

        compose.onNodeWithContentDescription("Step 4 of 4").assertIsDisplayed()
        compose.onNodeWithText("Approximate location").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Start").assertIsDisplayed()
        compose.onNodeWithTag("onboarding_continue").performClick()

        val container = (compose.activity.application as DaylightApplication).container
        compose.waitUntil(10_000) { runBlocking { container.preferences.state.first().onboardingComplete } }
        assertEquals("sk-or-ui-test", container.credentials.openRouterKey())
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
