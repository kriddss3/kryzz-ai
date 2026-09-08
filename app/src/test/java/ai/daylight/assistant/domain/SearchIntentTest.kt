package ai.daylight.assistant.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SearchIntentTest {
    @Test fun freshInfoQuestionsTriggerSearch() {
        assertThat(messageLikelyNeedsSearch("what's the weather in Lagos right now")).isTrue()
        assertThat(messageLikelyNeedsSearch("who won the match yesterday")).isTrue()
        assertThat(messageLikelyNeedsSearch("what is the current price of bitcoin")).isTrue()
        assertThat(messageLikelyNeedsSearch("any news on the SpaceX launch today")).isTrue()
        assertThat(messageLikelyNeedsSearch("who is the CEO of OpenAI in 2025")).isTrue()
        assertThat(messageLikelyNeedsSearch("latest version of Kotlin")).isTrue()
        assertThat(messageLikelyNeedsSearch("what are today's headlines")).isTrue()
    }

    @Test fun chatAndCreativeDoNotTriggerSearch() {
        assertThat(messageLikelyNeedsSearch("hi")).isFalse()
        assertThat(messageLikelyNeedsSearch("thanks, that helped")).isFalse()
        assertThat(messageLikelyNeedsSearch("write me a poem about cats")).isFalse()
        assertThat(messageLikelyNeedsSearch("what's your name")).isFalse()
        assertThat(messageLikelyNeedsSearch("explain how photosynthesis works")).isFalse()
        assertThat(messageLikelyNeedsSearch("translate this to French for me")).isFalse()
    }

    @Test fun emptyAndBlankNeverMatches() {
        assertThat(messageLikelyNeedsSearch("")).isFalse()
        assertThat(messageLikelyNeedsSearch("   ")).isFalse()
    }
}
