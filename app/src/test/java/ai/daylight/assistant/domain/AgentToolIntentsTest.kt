package ai.daylight.assistant.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AgentToolIntentsTest {
    @Test fun weatherMatchesForecastRequests() {
        assertThat(userWantsWeather("what's the weather in Gilly")).isTrue()
        assertThat(userWantsWeather("3 day forecast for Geneva")).isTrue()
        assertThat(userWantsWeather("write me a poem")).isFalse()
    }

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

    @Test fun artifactSkillAndCodeStayConservative() {
        assertThat(userWantsArtifact("write a report on the IA")).isTrue()
        assertThat(userWantsArtifact("write a haiku")).isFalse()
        assertThat(userWantsSkill("save this as a skill")).isTrue()
        assertThat(userWantsSkill("I have a skill issue")).isFalse()
        assertThat(userWantsCodeProject("scaffold an app as a zip")).isTrue()
        assertThat(userWantsCodeProject("explain this function")).isFalse()
    }

    @Test fun artifactGateCatchesFileFormatRequests() {
        // v5.7.1: PDF was missing entirely and bare "excel" only matched two phrases,
        // so create_artifact was never even offered for these requests.
        assertThat(userWantsArtifact("make an excel file of my expenses")).isTrue()
        assertThat(userWantsArtifact("turn this into a pdf")).isTrue()
        assertThat(userWantsArtifact("can I get a PDF file of the summary")).isTrue()
        assertThat(userWantsArtifact("make me a word document")).isTrue()
        assertThat(userWantsArtifact("build a workout workbook")).isTrue()
        assertThat(userWantsArtifact("write a haiku")).isFalse()
    }

    @Test fun artifactCreateGateStaysHighPrecision() {
        // Arms the one-shot create_artifact nudge: only real "make me a file" phrasing.
        assertThat(userWantsArtifactCreated("make an excel file of my expenses")).isTrue()
        assertThat(userWantsArtifactCreated("turn this into a pdf")).isTrue()
        assertThat(userWantsArtifactCreated("make me a word document")).isTrue()
        assertThat(userWantsArtifactCreated("create a spreadsheet of my budget")).isTrue()
        // Format questions offer the tool but must NOT arm the file-creation nudge.
        assertThat(userWantsArtifactCreated("what is a pdf used for in computing")).isFalse()
        assertThat(userWantsArtifactCreated("is excel better than sheets")).isFalse()
        assertThat(userWantsArtifactCreated("write a haiku")).isFalse()
    }
}
