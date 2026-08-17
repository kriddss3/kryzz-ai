package ai.daylight.assistant.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationMappingTest {
    @Test
    fun focusedConversationRoutesSelectChatDestination() {
        assertEquals(MainTab.CHAT, topLevelTabForRoute(Routes.CHAT))
        assertEquals(MainTab.CHAT, topLevelTabForRoute("chat/abc?mode=CHAT&capability=AUTO"))
        assertNull(topLevelTabForRoute("unknown/abc"))
    }

    @Test
    fun chatIsHomeAndStandaloneLibraryIsNotMapped() {
        assertNull(topLevelTabForRoute("conversations"))
        assertEquals(MainTab.SETTINGS, topLevelTabForRoute(Routes.SETTINGS))
    }

    @Test
    fun contextualDestinationsDoNotMasqueradeAsTopLevelTabs() {
        assertNull(topLevelTabForRoute(Routes.LLMS))
        assertNull(topLevelTabForRoute(Routes.SKILLS))
        assertNull(topLevelTabForRoute(Routes.API))
        assertNull(topLevelTabForRoute(null))
    }
}
