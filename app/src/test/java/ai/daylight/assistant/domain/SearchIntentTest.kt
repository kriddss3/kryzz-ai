package ai.daylight.assistant.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SearchIntentTest {
    private fun needsSearch(text: String) = messageLikelyNeedsSearch(text, currentYear = 2026)

    @Test fun freshInfoQuestionsTriggerSearch() {
        assertThat(needsSearch("who won the match yesterday")).isTrue()
        assertThat(needsSearch("what is the current price of bitcoin")).isTrue()
        assertThat(needsSearch("any news on the SpaceX launch today")).isTrue()
        assertThat(needsSearch("who is the CEO of OpenAI")).isTrue()
        assertThat(needsSearch("who's the current prime minister of the UK")).isTrue()
        assertThat(needsSearch("latest version of Kotlin")).isTrue()
        assertThat(needsSearch("what are today's headlines")).isTrue()
        assertThat(needsSearch("EUR to CHF exchange rate")).isTrue()
        assertThat(needsSearch("search the web for cheap flights to Riga")).isTrue()
        assertThat(needsSearch("can you look up the opening hours")).isTrue()
    }

    @Test fun recentYearsCountAsFreshRelativeToTheClock() {
        assertThat(needsSearch("best laptops of 2025")).isTrue()
        assertThat(needsSearch("what to expect in 2027")).isTrue()
        // Older years are history the model already knows.
        assertThat(needsSearch("what happened in 1989")).isFalse()
        assertThat(messageLikelyNeedsSearch("best laptops of 2025", currentYear = 2030)).isFalse()
    }

    @Test fun everydayWordsNoLongerForceASearch() {
        // v5.8 regression set: each of these used to force a web search through one
        // everyday word ("game", "search", "still", "result", "manager", "now").
        assertThat(needsSearch("Write me a snake game in Python")).isFalse()
        assertThat(needsSearch("Explain how a binary search works, step by step")).isFalse()
        assertThat(needsSearch("Can you still help me plan my essay?")).isFalse()
        assertThat(needsSearch("Summarise the result of my calculation above")).isFalse()
        assertThat(needsSearch("I need a manager-style email declining a meeting")).isFalse()
        assertThat(needsSearch("what should I cook for dinner now")).isFalse()
        assertThat(needsSearch("update my study plan for this week")).isFalse()
    }

    @Test fun weatherIsLeftToGetWeather() {
        // get_weather answers forecasts directly; forcing a web search first wasted a round.
        assertThat(needsSearch("what's the weather in Lagos right now")).isFalse()
        assertThat(needsSearch("will it rain tomorrow in Riga")).isFalse()
    }

    @Test fun chatAndCreativeDoNotTriggerSearch() {
        assertThat(needsSearch("hi")).isFalse()
        assertThat(needsSearch("thanks, that helped")).isFalse()
        assertThat(needsSearch("write me a poem about cats")).isFalse()
        assertThat(needsSearch("what's your name")).isFalse()
        assertThat(needsSearch("explain how photosynthesis works")).isFalse()
        assertThat(needsSearch("translate this to French for me")).isFalse()
    }

    @Test fun emptyAndBlankNeverMatches() {
        assertThat(needsSearch("")).isFalse()
        assertThat(needsSearch("   ")).isFalse()
    }
}
