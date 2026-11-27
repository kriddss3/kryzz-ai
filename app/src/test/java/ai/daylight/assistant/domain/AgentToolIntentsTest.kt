package ai.daylight.assistant.domain

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class AgentToolIntentsTest {
    @Test fun fetchMatchesLinksAndReadRequests() {
        assertThat(userWantsFetch("summarise https://example.com/article")).isTrue()
        assertThat(userWantsFetch("read this page for me")).isTrue()
        assertThat(userWantsFetch("what is photosynthesis")).isFalse()
    }

    @Test fun firstHttpUrlStripsTrailingPunctuation() {
        assertThat(firstHttpUrl("see https://example.com/x.")).isEqualTo("https://example.com/x")
    }

    @Test fun scheduleMatchesReminders() {
        assertThat(userWantsSchedule("remind me every morning to review vocab")).isTrue()
        assertThat(userWantsSchedule("schedule a weekly recap")).isTrue()
        assertThat(userWantsSchedule("what is a cron job")).isFalse()
    }

    @Test fun skillAndCodeStayConservative() {
        assertThat(userWantsSkill("save this as a skill")).isTrue()
        assertThat(userWantsSkill("I have a skill issue")).isFalse()
        assertThat(userWantsCodeProject("scaffold an app as a zip")).isTrue()
        assertThat(userWantsCodeProject("explain this function")).isFalse()
    }

    @Test fun artifactCreateGateStaysHighPrecision() {
        // Arms the one-shot create_artifact nudge: only real "make me a file" phrasing.
        assertThat(userWantsArtifactCreated("make an excel file of my expenses")).isTrue()
        assertThat(userWantsArtifactCreated("turn this into a pdf")).isTrue()
        assertThat(userWantsArtifactCreated("make me a word document")).isTrue()
        assertThat(userWantsArtifactCreated("create a spreadsheet of my budget")).isTrue()
        assertThat(userWantsArtifactCreated("create a calendar event for Tuesday at 3")).isTrue()
        assertThat(userWantsArtifactCreated("make an .ics I can import")).isTrue()
        assertThat(userWantsArtifactCreated("save this as a file")).isTrue()
        // Format questions offer the tool but must NOT arm the file-creation nudge.
        assertThat(userWantsArtifactCreated("what is a pdf used for in computing")).isFalse()
        assertThat(userWantsArtifactCreated("what is a calendar")).isFalse()
        assertThat(userWantsArtifactCreated("is excel better than sheets")).isFalse()
        assertThat(userWantsArtifactCreated("write a haiku")).isFalse()
    }

    @Test fun artifactGateCatchesNaturalWording() {
        // v5.13.7: the phrase list missed every wording it did not spell out.
        listOf(
            "make me a pdf about the trip",
            "can you give me this as a PDF?",
            "export the table as json",
            "turn these notes into slides",
            "make a powerpoint about Mars for my class",
            "create a pptx deck for the pitch",
            "code me a landing page for my bakery",
            "write a python script that renames photos and send it as a file",
            "put the contacts in a vcf",
            "put everything into a zip for me",
            "how about you make a pdf of this"
        ).forEach { assertWithMessage(it).that(userWantsArtifactCreated(it)).isTrue() }
        listOf(
            "how do I make a pdf in Word?",
            "what's the difference between docx and pdf",
            "tell me about the history of PowerPoint",
            "write a short poem about rain"
        ).forEach { assertWithMessage(it).that(userWantsArtifactCreated(it)).isFalse() }
    }
}
