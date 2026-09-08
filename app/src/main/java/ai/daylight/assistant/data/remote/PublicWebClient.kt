package ai.daylight.assistant.data.remote

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

data class FetchedPage(val url: String, val title: String, val text: String, val truncated: Boolean)

data class WeatherReport(
    val place: String,
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val current: String,
    val days: List<String>
)

/**
 * Key-free public web helpers for Auto: Open-Meteo weather and a bounded page fetch.
 * SSRF-hardened (public http/https only) and unit-testable without a network for parse/safety.
 */
class PublicWebClient(
    private val http: OkHttpClient = defaultClient(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) {
    fun weather(place: String?, latitude: Double?, longitude: Double?): WeatherReport {
        val coords = when {
            !place.isNullOrBlank() -> geocode(place.trim())
            latitude != null && longitude != null && (latitude != 0.0 || longitude != 0.0) ->
                Triple(latitude, longitude, "%.2f, %.2f".format(latitude, longitude))
            else -> throw AssistantApiException(
                ErrorKind.VALIDATION,
                "Pass a place name or enable location in Settings."
            )
        }
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("api.open-meteo.com")
            .addPathSegments("v1/forecast")
            .addQueryParameter("latitude", coords.first.toString())
            .addQueryParameter("longitude", coords.second.toString())
            .addQueryParameter(
                "current",
                "temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,apparent_temperature"
            )
            .addQueryParameter("daily", "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum")
            .addQueryParameter("forecast_days", "3")
            .addQueryParameter("timezone", "auto")
            .build()
        val body = getText(url.toString())
        return parseWeatherJson(body, coords.third)
    }

    fun fetchPage(rawUrl: String): FetchedPage {
        var current = requireSafeHttpUrl(rawUrl)
        repeat(5) {
            val request = Request.Builder().url(current).header("User-Agent", USER_AGENT).get().build()
            http.newCall(request).execute().use { response ->
                val code = response.code
                val location = response.header("Location")
                if (code in 300..399 && !location.isNullOrBlank()) {
                    current = requireSafeHttpUrl(location, current)
                    return@repeat
                }
                if (!response.isSuccessful) {
                    throw AssistantApiException(ErrorKind.NETWORK, "Page fetch failed (HTTP $code).")
                }
                val media = response.body?.contentType()?.toString().orEmpty()
                val raw = response.body?.string().orEmpty()
                if (raw.length > MAX_RAW_CHARS) {
                    throw AssistantApiException(ErrorKind.VALIDATION, "Page is larger than ${MAX_RAW_CHARS} characters.")
                }
                val text = if (media.contains("html", ignoreCase = true) || raw.contains("<html", ignoreCase = true)) {
                    htmlToText(raw)
                } else {
                    raw.replace(Regex("\\s+"), " ").trim()
                }
                val clipped = text.take(MAX_TEXT_CHARS)
                return FetchedPage(
                    url = current.toString(),
                    title = extractTitle(raw).ifBlank { current.host },
                    text = clipped,
                    truncated = text.length > MAX_TEXT_CHARS
                )
            }
        }
        throw AssistantApiException(ErrorKind.NETWORK, "Too many redirects.")
    }

    private fun geocode(place: String): Triple<Double, Double, String> {
        if (place.length !in 2..80) {
            throw AssistantApiException(ErrorKind.VALIDATION, "Place name must be 2–80 characters.")
        }
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("geocoding-api.open-meteo.com")
            .addPathSegments("v1/search")
            .addQueryParameter("name", place)
            .addQueryParameter("count", "1")
            .addQueryParameter("language", "en")
            .addQueryParameter("format", "json")
            .build()
        val body = getText(url.toString())
        return parseGeocodeJson(body, place)
    }

    private fun getText(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw AssistantApiException(ErrorKind.NETWORK, "Request failed (HTTP ${response.code}).")
            }
            return response.body?.string().orEmpty()
        }
    }

    fun parseWeatherJson(body: String, placeLabel: String): WeatherReport {
        val root = json.parseToJsonElement(body).jsonObject
        val current = root["current"]?.jsonObject
        val daily = root["daily"]?.jsonObject
        val lat = root["latitude"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val lon = root["longitude"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val zone = root["timezone"]?.jsonPrimitive?.contentOrNull ?: "auto"
        val code = current?.get("weather_code")?.jsonPrimitive?.intOrNull
        val temp = current?.get("temperature_2m")?.jsonPrimitive?.contentOrNull
        val feels = current?.get("apparent_temperature")?.jsonPrimitive?.contentOrNull
        val humidity = current?.get("relative_humidity_2m")?.jsonPrimitive?.contentOrNull
        val wind = current?.get("wind_speed_10m")?.jsonPrimitive?.contentOrNull
        val currentLine = buildString {
            append(describeWeather(code))
            if (!temp.isNullOrBlank()) append(", ${temp}°C")
            if (!feels.isNullOrBlank()) append(" (feels ${feels}°C)")
            if (!humidity.isNullOrBlank()) append(", humidity ${humidity}%")
            if (!wind.isNullOrBlank()) append(", wind ${wind} km/h")
        }.ifBlank { "Current conditions unavailable." }
        val dates = stringList(daily?.get("time"))
        val maxes = stringList(daily?.get("temperature_2m_max"))
        val mins = stringList(daily?.get("temperature_2m_min"))
        val rain = stringList(daily?.get("precipitation_sum"))
        val codes = intList(daily?.get("weather_code"))
        val days = dates.indices.map { i ->
            val label = dates.getOrNull(i) ?: "day ${i + 1}"
            val hi = maxes.getOrNull(i)
            val lo = mins.getOrNull(i)
            val precip = rain.getOrNull(i)
            val sky = describeWeather(codes.getOrNull(i))
            buildString {
                append(label)
                append(": ")
                append(sky)
                if (!hi.isNullOrBlank() || !lo.isNullOrBlank()) append(", ${lo ?: "?"}–${hi ?: "?"}°C")
                if (!precip.isNullOrBlank()) append(", precip ${precip} mm")
            }
        }
        return WeatherReport(placeLabel, lat, lon, zone, currentLine, days)
    }

    fun parseGeocodeJson(body: String, fallbackName: String): Triple<Double, Double, String> {
        val results = json.parseToJsonElement(body).jsonObject["results"] as? JsonArray
            ?: throw AssistantApiException(ErrorKind.VALIDATION, "No location matched \"$fallbackName\".")
        val first = results.firstOrNull()?.jsonObject
            ?: throw AssistantApiException(ErrorKind.VALIDATION, "No location matched \"$fallbackName\".")
        val lat = first["latitude"]?.jsonPrimitive?.doubleOrNull
        val lon = first["longitude"]?.jsonPrimitive?.doubleOrNull
        if (lat == null || lon == null) {
            throw AssistantApiException(ErrorKind.VALIDATION, "No location matched \"$fallbackName\".")
        }
        val name = first["name"]?.jsonPrimitive?.contentOrNull ?: fallbackName
        val admin = first["admin1"]?.jsonPrimitive?.contentOrNull
        val country = first["country"]?.jsonPrimitive?.contentOrNull
        val label = listOfNotNull(name, admin, country).joinToString(", ")
        return Triple(lat, lon, label)
    }

    companion object {
        const val MAX_TEXT_CHARS = 12_000
        const val MAX_RAW_CHARS = 80_000
        private const val USER_AGENT = "KryzzAI/5.6.11 (agent fetch)"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        fun requireSafeHttpUrl(raw: String, base: HttpUrl? = null): HttpUrl {
            val trimmed = raw.trim()
            val parsed = when {
                base != null && trimmed.startsWith("/") -> base.resolve(trimmed)
                else -> trimmed.toHttpUrlOrNull()
            } ?: throw AssistantApiException(ErrorKind.VALIDATION, "That is not a valid http(s) URL.")
            if (parsed.scheme != "http" && parsed.scheme != "https") {
                throw AssistantApiException(ErrorKind.VALIDATION, "Only http and https URLs are allowed.")
            }
            if (isPrivateHost(parsed.host)) {
                throw AssistantApiException(ErrorKind.VALIDATION, "Private or local addresses are blocked.")
            }
            return parsed
        }

        fun isPrivateHost(host: String): Boolean {
            val lower = host.lowercase().trim().removePrefix("[").removeSuffix("]")
            if (lower.isEmpty()) return true
            if (lower == "localhost" || lower.endsWith(".localhost") || lower == "0.0.0.0" ||
                lower == "::1" || lower == "0:0:0:0:0:0:0:1"
            ) return true
            if (lower.endsWith(".local") || lower.endsWith(".internal")) return true
            // Literal IPv4 only — no DNS lookup so unit tests stay offline.
            val parts = lower.split('.')
            if (parts.size == 4 && parts.all { it.toIntOrNull() != null }) {
                val a = parts[0].toInt()
                val b = parts[1].toInt()
                if (a == 10) return true
                if (a == 127) return true
                if (a == 192 && b == 168) return true
                if (a == 172 && b in 16..31) return true
                if (a == 169 && b == 254) return true
            }
            return false
        }

        fun htmlToText(html: String): String {
            var s = html
            s = Regex("(?is)<script[^>]*>.*?</script>").replace(s, " ")
            s = Regex("(?is)<style[^>]*>.*?</style>").replace(s, " ")
            s = Regex("(?is)<noscript[^>]*>.*?</noscript>").replace(s, " ")
            s = Regex("(?is)<[^>]+>").replace(s, " ")
            s = s.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
            return Regex("\\s+").replace(s, " ").trim()
        }

        fun extractTitle(html: String): String {
            val match = Regex("(?is)<title[^>]*>(.*?)</title>").find(html) ?: return ""
            return htmlToText(match.groupValues[1]).take(120)
        }

        private fun describeWeather(code: Int?): String = when (code) {
            null -> "Unknown"
            0 -> "Clear"
            1, 2, 3 -> "Partly cloudy"
            45, 48 -> "Fog"
            51, 53, 55, 56, 57 -> "Drizzle"
            61, 63, 65, 66, 67 -> "Rain"
            71, 73, 75, 77 -> "Snow"
            80, 81, 82 -> "Rain showers"
            85, 86 -> "Snow showers"
            95, 96, 99 -> "Thunderstorm"
            else -> "Code $code"
        }

        private fun stringList(element: kotlinx.serialization.json.JsonElement?): List<String> =
            (element as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

        private fun intList(element: kotlinx.serialization.json.JsonElement?): List<Int> =
            (element as? JsonArray)?.mapNotNull { it.jsonPrimitive.intOrNull } ?: emptyList()
    }
}
