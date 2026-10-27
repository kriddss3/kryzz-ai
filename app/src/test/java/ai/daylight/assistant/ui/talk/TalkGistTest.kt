package ai.daylight.assistant.ui.talk

import ai.daylight.assistant.ui.dot.effects.DotFont5x7
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TalkGistTest {
    @Test fun keepsKeywordsInOrderAndDropsFiller() {
        assertThat(TalkGist.of("Any open pixel fonts that have lowercase?")).isEqualTo("OPEN PIXEL FONTS")
        assertThat(TalkGist.of("What is the 8dp grid?")).isEqualTo("8DP GRID")
        assertThat(TalkGist.of("Is OFL fine for an app?")).isEqualTo("OFL FINE APP")
    }

    @Test fun neverLongerThanSixteenAndOnlyWholeWordsWhenPossible() {
        val gist = TalkGist.of("Compare Doto, Space Mono and Geist for small dot-matrix titles on Android")
        assertThat(gist.length).isAtMost(TalkGist.MAX_CHARS)
        assertThat(gist).isEqualTo("COMPARE DOTO")
    }

    @Test fun aSingleLongWordIsCut() {
        assertThat(TalkGist.of("Supercalifragilisticexpialidocious")).isEqualTo("SUPERCALIFRAGILI")
    }

    @Test fun onlyLettersDigitsAndSpacesSurvive() {
        val questions = listOf(
            "Café “naïve” résumé — what's the deal?!",
            "C++ vs. Rust: 2026 benchmarks (10x?)",
            "email me @ 9:30 & bring $5",
            "Kāds ir laiks Rīgā šodien?",
        )
        questions.forEach { q ->
            val gist = TalkGist.of(q)
            assertThat(gist.length).isAtMost(TalkGist.MAX_CHARS)
            assertThat(gist.all { it in 'A'..'Z' || it in '0'..'9' || it == ' ' }).isTrue()
            assertThat(gist.all { DotFont5x7.supports(it) }).isTrue()
            assertThat(gist).doesNotContain("  ")
            assertThat(gist).isEqualTo(gist.trim())
        }
    }

    @Test fun latvianAccentsAreStrippedAndFillerDropped() {
        // "LAIKS RIGA SODIEN" is 17 characters, so the last word is left off.
        assertThat(TalkGist.of("Kāds ir laiks Rīgā šodien?")).isEqualTo("LAIKS RIGA")
    }

    @Test fun repeatedKeywordsAppearOnce() {
        assertThat(TalkGist.of("fonts fonts fonts pixel")).isEqualTo("FONTS PIXEL")
    }

    @Test fun onlyFillerKeepsTheFirstWords() {
        assertThat(TalkGist.of("What is it?")).isEqualTo("WHAT IS IT")
    }

    @Test fun nothingDrawableFallsBack() {
        assertThat(TalkGist.of("")).isEqualTo(TalkGist.FALLBACK)
        assertThat(TalkGist.of("   ?! …")).isEqualTo(TalkGist.FALLBACK)
        assertThat(TalkGist.of("你好吗")).isEqualTo(TalkGist.FALLBACK)
    }
}
