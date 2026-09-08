package ai.daylight.assistant.data.remote

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class PublicWebClientTest {
    @Test fun privateHostsAreBlocked() {
        assertThat(PublicWebClient.isPrivateHost("localhost")).isTrue()
        assertThat(PublicWebClient.isPrivateHost("127.0.0.1")).isTrue()
        assertThat(PublicWebClient.isPrivateHost("10.0.0.4")).isTrue()
        assertThat(PublicWebClient.isPrivateHost("192.168.1.20")).isTrue()
        assertThat(PublicWebClient.isPrivateHost("172.16.0.2")).isTrue()
        assertThat(PublicWebClient.isPrivateHost("169.254.1.1")).isTrue()
    }

    @Test fun publicLiteralIpIsAllowed() {
        assertThat(PublicWebClient.isPrivateHost("1.1.1.1")).isFalse()
        assertThat(PublicWebClient.isPrivateHost("8.8.8.8")).isFalse()
    }

    @Test fun requireSafeHttpUrlRejectsFileAndPrivate() {
        assertThrows(AssistantApiException::class.java) { PublicWebClient.requireSafeHttpUrl("file:///etc/passwd") }
        assertThrows(AssistantApiException::class.java) { PublicWebClient.requireSafeHttpUrl("http://127.0.0.1/x") }
        val url = PublicWebClient.requireSafeHttpUrl("https://example.com/path")
        assertThat(url.host).isEqualTo("example.com")
    }

    @Test fun htmlToTextStripsTagsAndScripts() {
        val html = "<html><head><title>Hello</title><script>alert(1)</script></head><body><p>One &amp; two</p></body></html>"
        assertThat(PublicWebClient.htmlToText(html)).contains("One & two")
        assertThat(PublicWebClient.htmlToText(html)).doesNotContain("alert")
        assertThat(PublicWebClient.extractTitle(html)).isEqualTo("Hello")
    }

    @Test fun weatherJsonParsesCurrentAndDays() {
        val client = PublicWebClient()
        val body = """
            {
              "latitude": 46.46,
              "longitude": 6.28,
              "timezone": "Europe/Zurich",
              "current": {
                "temperature_2m": 12.4,
                "apparent_temperature": 10.1,
                "relative_humidity_2m": 70,
                "wind_speed_10m": 8.2,
                "weather_code": 61
              },
              "daily": {
                "time": ["2026-08-26", "2026-08-27"],
                "weather_code": [61, 0],
                "temperature_2m_max": [15.0, 18.2],
                "temperature_2m_min": [8.1, 9.0],
                "precipitation_sum": [4.2, 0.0]
              }
            }
        """.trimIndent()
        val report = client.parseWeatherJson(body, "Gilly, Vaud, Switzerland")
        assertThat(report.place).contains("Gilly")
        assertThat(report.current).contains("Rain")
        assertThat(report.current).contains("12.4")
        assertThat(report.days).hasSize(2)
        assertThat(report.days[1]).contains("Clear")
    }

    @Test fun geocodeJsonReadsFirstHit() {
        val client = PublicWebClient()
        val body = """
            {"results":[{"name":"Gilly","latitude":46.46,"longitude":6.28,"admin1":"Vaud","country":"Switzerland"}]}
        """.trimIndent()
        val triple = client.parseGeocodeJson(body, "Gilly")
        assertThat(triple.first).isWithin(0.01).of(46.46)
        assertThat(triple.third).contains("Switzerland")
    }
}
