package ai.daylight.assistant.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ToolCallXmlTest {
    // Built with concatenation so the test source never contains the raw tag sequence
    // that some tooling tries to parse as a nested call.
    private val toolsOpen = "<" + "tools"
    private val toolsClose = "</" + "tools>"
    private val tcOpen = "<" + "tool_call"
    private val tcClose = "</" + "tool_call>"
    private val toolOpen = "<" + "tool"
    private val toolClose = "</" + "tool>"

    @Test fun scrubRemovesCompleteToolsBlock() {
        val input = "Let me check. " + toolsOpen + ">{\"name\":\"parallel_search\",\"arguments\":{\"searchQueries\":[\"weather\"]}}" + toolsClose
        assertThat(ToolCallXml.scrubToolBlocks(input)).isEqualTo("Let me check.")
    }

    @Test fun scrubHidesOpenToolsBlock() {
        val input = "Let me check. " + toolsOpen + ">{\"name\":\"parallel_search\",\"arguments\":"
        assertThat(ToolCallXml.scrubToolBlocks(input)).isEqualTo("Let me check.")
    }

    @Test fun scrubHandlesToolCallTag() {
        val input = "Prefix " + tcOpen + ">{\"name\":\"x\",\"arguments\":{\"q\":\"a\"}}" + tcClose + " suffix"
        assertThat(ToolCallXml.scrubToolBlocks(input)).isEqualTo("Prefix  suffix")
    }

    @Test fun scrubLeavesPlainTextAlone() {
        val input = "Here is how to use a <table> tag in HTML."
        assertThat(ToolCallXml.scrubToolBlocks(input)).isEqualTo(input)
    }

    @Test fun parseJsonBody() {
        val input = toolsOpen + ">{\"name\":\"parallel_search\",\"arguments\":{\"searchQueries\":[\"weather today\"]}}" + toolsClose
        val calls = ToolCallXml.parseToolCallXml(input)
        assertThat(calls).hasSize(1)
        assertThat(calls[0].name).isEqualTo("parallel_search")
        assertThat(calls[0].arguments).contains("weather today")
    }

    @Test fun parseParametersAlias() {
        val input = tcOpen + ">{\"name\":\"x\",\"parameters\":{\"q\":1}}" + tcClose
        val calls = ToolCallXml.parseToolCallXml(input)
        assertThat(calls).hasSize(1)
        assertThat(calls[0].name).isEqualTo("x")
        assertThat(calls[0].arguments).contains("\"q\":1")
    }

    @Test fun parseXmlAttributeForm() {
        val input = toolOpen + " name=\"parallel_search\">{\"searchQueries\":[\"weather\"]}" + toolClose
        val calls = ToolCallXml.parseToolCallXml(input)
        assertThat(calls).hasSize(1)
        assertThat(calls[0].name).isEqualTo("parallel_search")
        assertThat(calls[0].arguments).contains("weather")
    }

    @Test fun parseXmlBodyForm() {
        val input = toolOpen + "><name>parallel_search</name><arguments>{\"searchQueries\":[\"weather\"]}</arguments>" + toolClose
        val calls = ToolCallXml.parseToolCallXml(input)
        assertThat(calls).hasSize(1)
        assertThat(calls[0].name).isEqualTo("parallel_search")
        assertThat(calls[0].arguments).contains("weather")
    }

    @Test fun parseMultipleInsideWrapper() {
        val inner = tcOpen + ">{\"name\":\"a\",\"arguments\":{}}" + tcClose + tcOpen + ">{\"name\":\"b\",\"arguments\":{}}" + tcClose
        val input = toolsOpen + ">" + inner + toolsClose
        val calls = ToolCallXml.parseToolCallXml(input)
        assertThat(calls).hasSize(2)
        assertThat(calls.map { it.name }).containsExactly("a", "b")
    }

    @Test fun parseIgnoresOpenPartial() {
        val input = toolsOpen + ">{\"name\":\"a\",\"arguments\":"
        assertThat(ToolCallXml.parseToolCallXml(input)).isEmpty()
    }

    @Test fun parseFunctionEqualsForm() {
        val input = "<function=parallel_search>{\"search_queries\":[\"weather geneva\"]}</function>"
        val calls = ToolCallXml.parseToolCallXml(input)
        assertThat(calls).hasSize(1)
        assertThat(calls[0].name).isEqualTo("parallel_search")
        assertThat(calls[0].arguments).contains("weather geneva")
    }

    @Test fun parseQwenFlowerForm() {
        val input = "Sure. ✿FUNCTION✿parallel_search\n✿ARGS✿{\"search_queries\":[\"bitcoin price\"]}"
        val calls = ToolCallXml.parseToolCallXml(input)
        assertThat(calls).hasSize(1)
        assertThat(calls[0].name).isEqualTo("parallel_search")
        assertThat(ToolCallXml.scrubToolBlocks(input)).doesNotContain("✿FUNCTION✿")
    }

    @Test fun parseFencedJsonTool() {
        val input = "Working.\n```tool\n{\"name\":\"generate_image\",\"arguments\":{\"prompt\":\"a red fox\"}}\n```"
        val calls = ToolCallXml.parseToolCallXml(input)
        assertThat(calls).hasSize(1)
        assertThat(calls[0].name).isEqualTo("generate_image")
        assertThat(calls[0].arguments).contains("red fox")
    }

    @Test fun parseBareKnownToolJson() {
        val input = "Calling it now {\"name\":\"parallel_search\",\"arguments\":{\"searchQueries\":[\"weather\"]}} and waiting."
        val calls = ToolCallXml.parseToolCallXml(input)
        assertThat(calls.map { it.name }).contains("parallel_search")
    }
}
