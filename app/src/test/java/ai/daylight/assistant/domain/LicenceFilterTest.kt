package ai.daylight.assistant.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** C8: Find's Reusable filter keeps CC0, public domain, CC BY and CC BY-SA, and nothing else. */
class LicenceFilterTest {
    @Test fun commonsNamesAndCodesMapToTheirFamily() {
        assertThat(ImageLicence.family("CC BY-SA 4.0")).isEqualTo(LicenceFamily.CC_BY_SA)
        assertThat(ImageLicence.family("cc-by-sa-4.0")).isEqualTo(LicenceFamily.CC_BY_SA)
        assertThat(ImageLicence.family("CC BY-SA 3.0 DE")).isEqualTo(LicenceFamily.CC_BY_SA)
        assertThat(ImageLicence.family("CC BY 2.0")).isEqualTo(LicenceFamily.CC_BY)
        assertThat(ImageLicence.family("cc-by-3.0")).isEqualTo(LicenceFamily.CC_BY)
        assertThat(ImageLicence.family("CC BY")).isEqualTo(LicenceFamily.CC_BY)
        assertThat(ImageLicence.family("CC0")).isEqualTo(LicenceFamily.CC0)
        assertThat(ImageLicence.family("cc0")).isEqualTo(LicenceFamily.CC0)
        assertThat(ImageLicence.family("CC0 1.0")).isEqualTo(LicenceFamily.CC0)
        assertThat(ImageLicence.family("Public domain")).isEqualTo(LicenceFamily.PUBLIC_DOMAIN)
        assertThat(ImageLicence.family("pd")).isEqualTo(LicenceFamily.PUBLIC_DOMAIN)
        assertThat(ImageLicence.family("PD-US")).isEqualTo(LicenceFamily.PUBLIC_DOMAIN)
    }

    @Test fun otherFreeLicencesAndAnythingRestrictedAreNotReusable() {
        listOf(
            "GFDL", "gfdl", "FAL", "OGL 3", "Attribution", "Copyrighted free use",
            "CC BY-NC-SA 2.0", "CC BY-ND 4.0", "cc-by-nc-4.0", "", "   ", "CCBYSA", "Public"
        ).forEach { name ->
            assertThat(ImageLicence.family(name)).isEqualTo(LicenceFamily.OTHER)
        }
        assertThat(ImageLicence.family(null)).isEqualTo(LicenceFamily.OTHER)
    }

    @Test fun theMachineCodeWinsAndTheNameIsWhatTilesShow() {
        // Commons' short name can be loose; its code is exact.
        val licence = ImageLicence.of(shortName = "Creative Commons Attribution-Share Alike", code = "cc-by-sa-2.5")
        assertThat(licence.family).isEqualTo(LicenceFamily.CC_BY_SA)
        assertThat(licence.shortName).isEqualTo("Creative Commons Attribution-Share Alike")
        // An unknown code falls back to the name.
        assertThat(ImageLicence.of("CC BY 4.0", "some-template").family).isEqualTo(LicenceFamily.CC_BY)
        // No name at all: the code, then a plain fallback.
        assertThat(ImageLicence.of(null, "cc0").shortName).isEqualTo("CC0")
        assertThat(ImageLicence.of("", null).shortName).isEqualTo("Licence not stated")
        assertThat(ImageLicence.of("", null).reusable).isFalse()
    }

    @Test fun attributionFollowsCommonsOrTheFamily() {
        assertThat(ImageLicence.of("CC BY 4.0", "cc-by-4.0").attributionRequired).isTrue()
        assertThat(ImageLicence.of("CC0", "cc0").attributionRequired).isFalse()
        assertThat(ImageLicence.of("CC0", "cc0", attributionRequired = "true").attributionRequired).isTrue()
        assertThat(ImageLicence.of("CC BY 4.0", "cc-by-4.0", attributionRequired = "false").attributionRequired).isFalse()
    }

    @Test fun onlyWebLinksAreKeptAsTheLicenceUrl() {
        assertThat(ImageLicence.of("CC0", "cc0", url = "http://creativecommons.org/publicdomain/zero/1.0/deed.en").url)
            .isEqualTo("http://creativecommons.org/publicdomain/zero/1.0/deed.en")
        assertThat(ImageLicence.of("CC0", "cc0", url = "javascript:alert(1)").url).isNull()
        assertThat(ImageLicence.of("CC0", "cc0", url = " ").url).isNull()
    }

    @Test fun reusableIsTheDefaultAndAnyLicenceLetsEverythingThrough() {
        assertThat(FindFilters().licence).isEqualTo(LicenceFilter.REUSABLE)
        val reusable = listOf("CC0", "Public domain", "CC BY 4.0", "CC BY-SA 4.0").map { ImageLicence.of(it, null) }
        val other = listOf("GFDL", "FAL", "CC BY-NC 4.0").map { ImageLicence.of(it, null) }
        reusable.forEach { assertThat(LicenceFilter.REUSABLE.accepts(it)).isTrue() }
        other.forEach { assertThat(LicenceFilter.REUSABLE.accepts(it)).isFalse() }
        (reusable + other).forEach { assertThat(LicenceFilter.ANY.accepts(it)).isTrue() }
    }
}
