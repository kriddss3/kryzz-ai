package ai.daylight.assistant.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TalkCommandsTest {
    @Test fun makeGeneratesAnImageAndStripsTheLeadIn() {
        val command = TalkCommands.parse("make a red bicycle")
        assertThat(command).isEqualTo(TalkCommand.Make(MakeKind.IMAGE, "a red bicycle"))
        assertThat(TalkCommands.parse("Make a picture of a lighthouse"))
            .isEqualTo(TalkCommand.Make(MakeKind.IMAGE, "a lighthouse"))
        assertThat(TalkCommands.parse("please make an image of a fox"))
            .isEqualTo(TalkCommand.Make(MakeKind.IMAGE, "a fox"))
    }

    @Test fun makeAVideoUsesTheSameWord() {
        assertThat(TalkCommands.parse("make a video of the ferry at dusk"))
            .isEqualTo(TalkCommand.Make(MakeKind.VIDEO, "the ferry at dusk"))
        assertThat(TalkCommands.parse("can you make a short clip of rain on a window"))
            .isEqualTo(TalkCommand.Make(MakeKind.VIDEO, "rain on a window"))
    }

    @Test fun makeLeavesFilesPlansAndQuestionsAsChat() {
        assertThat(TalkCommands.parse("make me a pdf of my notes")).isNull()
        assertThat(TalkCommands.parse("make a spreadsheet of the costs")).isNull()
        assertThat(TalkCommands.parse("make me a list of boats")).isNull()
        assertThat(TalkCommands.parse("make a plan for dinner")).isNull()
        assertThat(TalkCommands.parse("how do I make a poster")).isNull()
        assertThat(TalkCommands.parse("make")).isNull()
    }

    @Test fun findSearchesPhotosAndLeavesFindOutAlone() {
        assertThat(TalkCommands.parse("find lighthouses"))
            .isEqualTo(TalkCommand.Find("lighthouses"))
        assertThat(TalkCommands.parse("find photos of oslo at night"))
            .isEqualTo(TalkCommand.Find("oslo at night"))
        assertThat(TalkCommands.parse("Find pictures of the eiffel tower"))
            .isEqualTo(TalkCommand.Find("the eiffel tower"))
        assertThat(TalkCommands.parse("find out why the sky is blue")).isNull()
        assertThat(TalkCommands.parse("find what a pdf is")).isNull()
        assertThat(TalkCommands.parse("find photos")).isNull()
    }
}
