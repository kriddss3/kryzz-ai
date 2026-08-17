package ai.daylight.assistant.ui

import androidx.compose.ui.unit.dp
import ai.daylight.assistant.domain.ChatDensity
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatDensityLayoutTest {
    @Test
    fun densityMapsToDistinctMessageAndComposerSpacing() {
        assertEquals(8.dp, ChatDensity.COMPACT.messageSpacing())
        assertEquals(12.dp, ChatDensity.COMFORTABLE.messageSpacing())
        assertEquals(16.dp, ChatDensity.SPACIOUS.messageSpacing())

        assertEquals(4.dp, ChatDensity.COMPACT.composerVerticalPadding())
        assertEquals(8.dp, ChatDensity.COMFORTABLE.composerVerticalPadding())
        assertEquals(12.dp, ChatDensity.SPACIOUS.composerVerticalPadding())
    }
}
