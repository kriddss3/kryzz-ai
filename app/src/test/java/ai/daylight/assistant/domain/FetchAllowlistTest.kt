package ai.daylight.assistant.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FetchAllowlistTest {

    @Test fun searchResultsAndSharedLinksAreReadable() {
        val allowlist = FetchAllowlist(
            knownUrls = listOf("https://www.rtings.com/laptop/reviews/best", "https://example.org/a?id=7"),
            userTexts = listOf("compare the best laptops")
        )
        assertThat(allowlist.hasKnownUrls).isTrue()
        assertThat(allowlist.permits("https://www.rtings.com/laptop/reviews/best")).isTrue()
        assertThat(allowlist.permits("https://example.org/a?id=7")).isTrue()
    }

    @Test fun matchingIgnoresSchemeWwwFragmentAndTrailingSlash() {
        val allowlist = FetchAllowlist(listOf("https://www.rtings.com/laptop/reviews/best"), emptyList())
        assertThat(allowlist.permits("http://rtings.com/laptop/reviews/best/")).isTrue()
        assertThat(allowlist.permits("https://RTINGS.com/laptop/reviews/best#battery")).isTrue()
        assertThat(allowlist.permits("https://rtings.com:443/laptop/reviews/best")).isTrue()
    }

    @Test fun composedUrlsAreRefused() {
        // The exfiltration shape: a known site, but a path or query the model built itself.
        val allowlist = FetchAllowlist(listOf("https://news.example.com/story/1"), listOf("what's new today"))
        assertThat(allowlist.permits("https://news.example.com/story/1?leak=my+address")).isFalse()
        assertThat(allowlist.permits("https://news.example.com/other")).isFalse()
        assertThat(allowlist.permits("https://evil.example.net/?q=secrets")).isFalse()
    }

    @Test fun sitesTheUserNamedAreReadable() {
        val allowlist = FetchAllowlist(emptyList(), listOf("Read the review on rtings.com and summarise it"))
        assertThat(allowlist.hasKnownUrls).isFalse()
        assertThat(allowlist.permits("https://www.rtings.com/laptop/reviews/dell-xps-13")).isTrue()
        // A host that merely ends with the named one is a different site.
        assertThat(allowlist.permits("https://notrtings.com/page")).isFalse()
        assertThat(allowlist.permits("https://example.com/page")).isFalse()
    }

    @Test fun onlyHttpUrlsCount() {
        val allowlist = FetchAllowlist(listOf("ftp://files.example.com/a", "not a url"), emptyList())
        assertThat(allowlist.hasKnownUrls).isFalse()
        assertThat(allowlist.permits("file:///etc/hosts")).isFalse()
    }

    @Test fun lenientParsingKeepsUrlsJavaNetUriRejects() {
        val odd = "https://example.com/search|results/über page"
        val allowlist = FetchAllowlist(listOf(odd), emptyList())
        assertThat(allowlist.hasKnownUrls).isTrue()
        assertThat(allowlist.permits("https://example.com/search|results/über")).isTrue()
    }

    @Test fun urlsInExtractsLinksFromUserText() {
        assertThat(FetchAllowlist.urlsIn("see https://a.example.com/x. and (http://b.example.org/y)"))
            .containsExactly("https://a.example.com/x", "http://b.example.org/y")
        assertThat(FetchAllowlist.urlsIn("no links here")).isEmpty()
    }
}
