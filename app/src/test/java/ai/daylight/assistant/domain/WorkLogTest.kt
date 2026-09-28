package ai.daylight.assistant.domain

import ai.daylight.assistant.data.local.MessageEntity
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WorkLogTest {
    private fun message(id: String, role: String, at: Long, durationMs: Long? = null) =
        MessageEntity(id, "c", role, "text", at, totalGenerationTimeMs = durationMs)

    private fun row(at: Long, vararg titles: String) = WorkLogRow(at, titles.map { WorkStep(WorkStepKind.SEARCH, it) })

    @Test fun groupsToolRowsPerTurnUnderTheFinalAnswer() {
        val visible = listOf(
            message("u1", "USER", 100),
            message("a1", "ASSISTANT", 300, durationMs = 48_000),
            message("u2", "USER", 400),
            message("a2", "ASSISTANT", 500),
            message("u3", "USER", 600),
            message("a3", "ASSISTANT", 700)
        )
        val logs = WorkLogBuilder.build(
            visible,
            listOf(row(150, "q1"), row(200, "q2"), row(320, "review file"), row(450, "q3"))
        )
        assertThat(logs.keys).containsExactly("a1", "a2")
        // Rows written after the answer started (the review pass) still belong to its turn.
        assertThat(logs.getValue("a1").steps.map { it.title }).containsExactly("q1", "q2", "review file").inOrder()
        assertThat(logs.getValue("a1").durationMs).isEqualTo(48_000)
        assertThat(logs.getValue("a2").steps.map { it.title }).containsExactly("q3")
    }

    @Test fun inProgressTurnWithoutAnswerHasNoLog() {
        val logs = WorkLogBuilder.build(listOf(message("u1", "USER", 100)), listOf(row(150, "q")))
        assertThat(logs).isEmpty()
    }

    @Test fun searchRowsListEachQuery() {
        val steps = WorkLogBuilder.steps(
            "parallel_search",
            """{"search_id":"s","queries":["pixel 10 battery","pixel 10 review"],"sources":[{"title":"a","url":"u"},{"title":"b","url":"v"}]}"""
        )
        assertThat(steps).containsExactly(
            WorkStep(WorkStepKind.SEARCH, "pixel 10 battery"),
            WorkStep(WorkStepKind.SEARCH, "pixel 10 review", "2 sources")
        ).inOrder()
        // Rows stored before v5.11 have no queries.
        assertThat(WorkLogBuilder.steps("parallel_search", """{"sources":[]}"""))
            .containsExactly(WorkStep(WorkStepKind.SEARCH, "Web search", "0 sources"))
    }

    @Test fun pageWeatherCalculationAndFileRows() {
        assertThat(WorkLogBuilder.steps("fetch_url", """{"url":"https://www.rtings.com/laptop/x","title":"Best laptops","truncated":false,"text":"..."}"""))
            .containsExactly(WorkStep(WorkStepKind.PAGE, "Best laptops", "rtings.com", "https://www.rtings.com/laptop/x"))
        assertThat(WorkLogBuilder.steps("get_weather", """{"place":"Riga","current":"12°C, cloudy"}""").single().title).isEqualTo("Weather in Riga")
        assertThat(WorkLogBuilder.steps("calculate", """{"expression":"12*7","result":"84","number":84.0}""").single().title).isEqualTo("12*7 = 84")
        assertThat(WorkLogBuilder.steps("create_artifact", """{"status":"created_locally","type":"pdf","title":"Budget","file_name":"budget.pdf"}"""))
            .containsExactly(WorkStep(WorkStepKind.FILE, "Budget", "budget.pdf"))
        assertThat(WorkLogBuilder.steps("update_plan", """{"status":"plan_updated"}""")).isEmpty()
    }

    @Test fun errorRowsArePlainFailedSteps() {
        val step = WorkLogBuilder.steps("fetch_url", """{"status":"error","error":"The page could not be opened.","detail":"example.com/a"}""").single()
        assertThat(step).isEqualTo(WorkStep(WorkStepKind.PAGE, "Page read: example.com/a", "The page could not be opened.", failed = true))
    }

    @Test fun summaryCountsWorkAndTime() {
        val steps = listOf(
            WorkStep(WorkStepKind.SEARCH, "a"), WorkStep(WorkStepKind.SEARCH, "b"), WorkStep(WorkStepKind.SEARCH, "c"),
            WorkStep(WorkStepKind.PAGE, "p1"), WorkStep(WorkStepKind.PAGE, "p2"), WorkStep(WorkStepKind.PAGE, "p3"), WorkStep(WorkStepKind.PAGE, "p4"),
            WorkStep(WorkStepKind.FILE, "f")
        )
        assertThat(WorkLogBuilder.summary(steps, 48_000)).isEqualTo("3 searches · read 4 pages · 1 file · 48s")
        assertThat(WorkLogBuilder.summary(listOf(WorkStep(WorkStepKind.SEARCH, "a"), WorkStep(WorkStepKind.PAGE, "x", failed = true)), null))
            .isEqualTo("1 search · 1 failed")
        assertThat(WorkLogBuilder.summary(listOf(WorkStep(WorkStepKind.WEATHER, "w"), WorkStep(WorkStepKind.TIME, "t")), 500))
            .isEqualTo("2 steps · <1s")
        assertThat(WorkLogBuilder.summary(listOf(WorkStep(WorkStepKind.PAGE, "p"), WorkStep(WorkStepKind.MEMORY, "m")), 125_000))
            .isEqualTo("read 1 page · 1 other step · 2m 5s")
    }
}
