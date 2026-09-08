package ai.daylight.assistant.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MediaIntentTest {
    @Test fun imageKeywordsMatchPictureRequests() {
        assertThat(userWantsImage("generate a picture of a fox")).isTrue()
        assertThat(userWantsImage("make an image of a sunset")).isTrue()
        assertThat(userWantsImage("design a logo for my cafe")).isTrue()
        assertThat(userWantsImage("do a quick sketch of my dog")).isTrue()
    }

    @Test fun imageWordBoundaryDoesNotMatchImagine() {
        // "imagine" must not trigger the image tool — word boundary guard.
        assertThat(userWantsImage("imagine a world where cats rule")).isFalse()
        assertThat(userWantsImage("what is the capital of France")).isFalse()
        // "draw" alone was deliberately excluded: "draw a conclusion" is not an image request.
        assertThat(userWantsImage("help me draw a conclusion from this data")).isFalse()
    }

    @Test fun videoKeywordsMatchClipRequests() {
        assertThat(userWantsVideo("make a 5 second video of waves")).isTrue()
        assertThat(userWantsVideo("generate an animation of a bouncing ball")).isTrue()
        assertThat(userWantsVideo("create a clip for my reel")).isTrue()
    }

    @Test fun videoKeywordsDoNotMatchPlainText() {
        assertThat(userWantsVideo("explain how photosynthesis works")).isFalse()
        assertThat(userWantsVideo("what is the capital of France")).isFalse()
    }

    @Test fun audioKeywordsMatchMusicRequests() {
        assertThat(userWantsAudio("make me a lo-fi study track")).isTrue()
        assertThat(userWantsAudio("generate a happy birthday song")).isTrue()
        assertThat(userWantsAudio("produce a chill instrumental")).isTrue()
    }

    @Test fun audioKeywordsDoNotMatchPlainText() {
        assertThat(userWantsAudio("what is the capital of France")).isFalse()
        assertThat(userWantsAudio("summarise this article")).isFalse()
        // "beat" alone was deliberately excluded: "beat the eggs" is not a music request.
        assertThat(userWantsAudio("how long do I beat the eggs for")).isFalse()
    }

    @Test fun emptyAndBlankTextNeverMatches() {
        assertThat(userWantsImage("")).isFalse()
        assertThat(userWantsVideo("   ")).isFalse()
        assertThat(userWantsAudio("")).isFalse()
    }
}
