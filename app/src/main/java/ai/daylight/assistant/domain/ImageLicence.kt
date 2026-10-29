package ai.daylight.assistant.domain

/** The licence families Find tells apart. Everything else on Commons is [OTHER]. */
enum class LicenceFamily { CC0, PUBLIC_DOMAIN, CC_BY, CC_BY_SA, OTHER }

/**
 * An image's licence as Commons states it: [shortName] is what the tiles show ("CC BY-SA
 * 4.0", "Public domain"), [url] the licence text when there is one.
 */
data class ImageLicence(
    val shortName: String,
    val family: LicenceFamily,
    val url: String? = null,
    val attributionRequired: Boolean = family == LicenceFamily.CC_BY || family == LicenceFamily.CC_BY_SA,
) {
    /** CC0, public domain, CC BY and CC BY-SA: free to reuse, with a credit where it asks for one. */
    val reusable: Boolean get() = family != LicenceFamily.OTHER

    companion object {
        private val SEPARATORS = Regex("[-_/]+")
        private val SPACES = Regex("\\s+")
        private val CC_SA = Regex("^CC BY SA( |$)")
        private val CC_BY = Regex("^CC BY( |$)")
        private val CC_ZERO = Regex("(^| )CC0( |$)|^CC ZERO( |$)")
        private val PUBLIC_DOMAIN = Regex("^(PD|PUBLIC DOMAIN)( |$)")
        private val NON_COMMERCIAL_OR_NO_DERIVATIVES = Regex("(^| )(NC|ND)( |$)")

        /**
         * Builds the licence from Commons' fields: [shortName] (LicenseShortName) and [code]
         * (License, e.g. "cc-by-sa-4.0"). The machine code wins when it is one Find knows.
         */
        fun of(shortName: String?, code: String?, url: String? = null, attributionRequired: String? = null): ImageLicence {
            val fromCode = family(code)
            val family = if (fromCode != LicenceFamily.OTHER) fromCode else family(shortName)
            val name = shortName?.trim()?.takeIf { it.isNotEmpty() }
                ?: code?.trim()?.takeIf { it.isNotEmpty() }?.uppercase()
                ?: "Licence not stated"
            val required = attributionRequired?.trim()?.lowercase()?.let { it == "true" }
            return ImageLicence(
                shortName = name,
                family = family,
                url = url?.trim()?.takeIf { it.startsWith("https://") || it.startsWith("http://") },
                attributionRequired = required ?: (family == LicenceFamily.CC_BY || family == LicenceFamily.CC_BY_SA)
            )
        }

        /**
         * The family a licence name or code belongs to. "CC BY-SA 3.0 DE" is CC BY-SA, "pd" and
         * "PD-US" are public domain, and anything non-commercial or no-derivatives is OTHER.
         */
        fun family(raw: String?): LicenceFamily {
            val s = SPACES.replace(SEPARATORS.replace(raw.orEmpty().uppercase(), " "), " ").trim()
            if (s.isEmpty()) return LicenceFamily.OTHER
            if (s.startsWith("CC ") && NON_COMMERCIAL_OR_NO_DERIVATIVES.containsMatchIn(s)) return LicenceFamily.OTHER
            return when {
                CC_ZERO.containsMatchIn(s) -> LicenceFamily.CC0
                PUBLIC_DOMAIN.containsMatchIn(s) -> LicenceFamily.PUBLIC_DOMAIN
                CC_SA.containsMatchIn(s) -> LicenceFamily.CC_BY_SA
                CC_BY.containsMatchIn(s) -> LicenceFamily.CC_BY
                else -> LicenceFamily.OTHER
            }
        }
    }
}

/** Find's licence filter. [REUSABLE] is the default (SCREENS.md: default to reusable licences). */
enum class LicenceFilter(val label: String, val detail: String) {
    REUSABLE("Reusable", "CC0, public domain, CC BY and CC BY-SA"),
    ANY("Any licence", "Everything on Commons, including GFDL and other free licences with longer terms");

    fun accepts(licence: ImageLicence): Boolean = this == ANY || licence.reusable
}

/**
 * The credit that travels with a saved or shared image, in the usual TASL order: title,
 * author, licence (with its link) and source. For example:
 * `"Tayid Lighthouse at Night" by Bien Orellana Bacarra Jr, CC BY-SA 4.0
 * (https://creativecommons.org/licenses/by-sa/4.0), via Wikimedia Commons:
 * https://commons.wikimedia.org/wiki/File:Tayid_Lighthouse_at_Night.jpg`
 */
object CreditLine {
    const val MAX_PART = 160
    private const val VIA = "via Wikimedia Commons"

    /**
     * [author] and [attribution] must be plain text already. [attribution] is the wording
     * the author asked for (Commons' Attribution field); it replaces "by [author]".
     */
    fun build(title: String, author: String?, licence: ImageLicence, sourceUrl: String, attribution: String? = null): String {
        val name = clip(title).ifEmpty { "Untitled" }
        val credit = clip(attribution.orEmpty())
        val by = clip(author.orEmpty())
        val head = when {
            credit.isNotEmpty() -> "\"$name\", $credit"
            by.isNotEmpty() -> "\"$name\" by $by"
            else -> "\"$name\""
        }
        val terms = when (licence.family) {
            LicenceFamily.PUBLIC_DOMAIN -> "public domain"
            else -> licence.shortName
        }
        val link = licence.url?.let { " ($it)" }.orEmpty()
        return "$head, $terms$link, $VIA: ${sourceUrl.trim()}"
    }

    /** One line, at most [MAX_PART] characters, ending in an ellipsis when cut. */
    fun clip(text: String, max: Int = MAX_PART): String {
        val one = text.replace(Regex("\\s+"), " ").trim()
        return if (one.length <= max) one else one.take(max - 1).trimEnd() + "…"
    }
}
