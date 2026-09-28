package ai.daylight.assistant.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ToolActivityDetailTest {
    @Test fun searchDetailIsTheFirstQuery() {
        val detail = ToolActivityDetail.of("parallel_search", """{"search_queries":["pixel 10 battery test","pixel 10 review"]}""")
        assertThat(detail).isEqualTo("pixel 10 battery test")
        assertThat(ToolActivityDetail.chipLabel("parallel_search", detail)).isEqualTo("Searching: pixel 10 battery test")
    }

    @Test fun fetchDetailIsHostAndShortPath() {
        assertThat(ToolActivityDetail.of("fetch_url", """{"url":"https://www.rtings.com/laptop/reviews/best"}""")).isEqualTo("rtings.com/laptop/…")
        assertThat(ToolActivityDetail.hostAndPath("https://example.com")).isEqualTo("example.com")
        assertThat(ToolActivityDetail.hostAndPath("https://example.com/news")).isEqualTo("example.com/news")
        assertThat(ToolActivityDetail.hostAndPath("not a url")).isNull()
    }

    @Test fun weatherAndArtifactDetails() {
        assertThat(ToolActivityDetail.of("get_weather", """{"place":"Rīga"}""")).isEqualTo("Rīga")
        assertThat(ToolActivityDetail.of("get_weather", "{}")).isNull()
        assertThat(ToolActivityDetail.chipLabel("get_weather", "Rīga")).isEqualTo("Weather: Rīga")
        assertThat(ToolActivityDetail.of("create_artifact", """{"type":"pdf","title":"Budget 2026","content":"x"}""")).isEqualTo("Budget 2026")
    }

    @Test fun longDetailsAreShortenedAndOtherToolsHaveNone() {
        val detail = ToolActivityDetail.of("parallel_search", """{"search_queries":["${"very long query ".repeat(8)}"]}""")!!
        assertThat(detail.length).isAtMost(ToolActivityDetail.MAX_CHARS)
        assertThat(detail).endsWith("…")
        assertThat(ToolActivityDetail.of("calculate", """{"expression":"1+1"}""")).isNull()
        assertThat(ToolActivityDetail.chipLabel("calculate", "1+1")).isNull()
        assertThat(ToolActivityDetail.of("parallel_search", "broken json")).isNull()
    }

    @Test fun concurrentCallsWithDifferentDetailsKeepSeparateChips() {
        val chips = ActivityChipCounter()
        var shown = emptyList<String>()
        shown = chips.update(shown, "Searching: a", started = true)
        shown = chips.update(shown, "Searching: b", started = true)
        shown = chips.update(shown, "Reading: x.com", started = true)
        assertThat(shown).containsExactly("Searching: a", "Searching: b", "Reading: x.com").inOrder()
        shown = chips.update(shown, "Searching: b", started = false)
        assertThat(shown).containsExactly("Searching: a", "Reading: x.com").inOrder()
        shown = chips.update(shown, "Searching: a", started = false)
        shown = chips.update(shown, "Reading: x.com", started = false)
        assertThat(shown).isEmpty()
    }

    @Test fun sameLabelSharesOneChipUntilTheLastStop() {
        val chips = ActivityChipCounter()
        var shown = chips.update(emptyList(), "Searching the web", started = true)
        shown = chips.update(shown, "Searching the web", started = true)
        assertThat(shown).containsExactly("Searching the web")
        shown = chips.update(shown, "Searching the web", started = false)
        assertThat(shown).containsExactly("Searching the web")
        shown = chips.update(shown, "Searching the web", started = false)
        assertThat(shown).isEmpty()
        // A stray stop never adds a chip.
        assertThat(chips.update(shown, "Searching the web", started = false)).isEmpty()
    }
}
