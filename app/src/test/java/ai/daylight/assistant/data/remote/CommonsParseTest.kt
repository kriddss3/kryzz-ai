package ai.daylight.assistant.data.remote

import ai.daylight.assistant.domain.FindFilters
import ai.daylight.assistant.domain.FindKind
import ai.daylight.assistant.domain.FindSize
import ai.daylight.assistant.domain.LicenceFamily
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

/** C8: Find reads Commons' API answers (fixture trimmed from real responses, 2026-10-29). */
class CommonsParseTest {
    private val fixture = """
        {"batchcomplete":true,"continue":{"gsroffset":24,"continue":"gsroffset||"},"query":{"pages":[
          {"pageid":27536037,"ns":6,"title":"File:La Martre lighthouse - La Martre, QC, CA - 16-06-2013.jpg","index":2,
           "imageinfo":[{"size":6290811,"width":4672,"height":3095,
             "thumburl":"https://thumb.wikimedia.org/wikipedia/commons/thumb/9/9c/La_Martre_lighthouse_-_La_Martre%2C_QC%2C_CA_-_16-06-2013.jpg/500px-La_Martre_lighthouse_-_La_Martre%2C_QC%2C_CA_-_16-06-2013.jpg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=thumbnail",
             "thumbwidth":500,"thumbheight":331,
             "url":"https://upload.wikimedia.org/wikipedia/commons/9/9c/La_Martre_lighthouse_-_La_Martre%2C_QC%2C_CA_-_16-06-2013.jpg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=original",
             "descriptionurl":"https://commons.wikimedia.org/wiki/File:La_Martre_lighthouse_-_La_Martre,_QC,_CA_-_16-06-2013.jpg",
             "extmetadata":{"ObjectName":{"value":"La Martre lighthouse - La Martre, QC, CA - 16-06-2013","source":"mediawiki-metadata"},
               "Artist":{"value":"<a href=\"//commons.wikimedia.org/wiki/User:Letartean\" title=\"User:Letartean\">Letartean</a>","source":"commons-desc-page"},
               "LicenseShortName":{"value":"CC BY 3.0","source":"commons-desc-page","hidden":""},
               "AttributionRequired":{"value":"true","source":"commons-desc-page","hidden":""},
               "LicenseUrl":{"value":"https://creativecommons.org/licenses/by/3.0","source":"commons-desc-page"},
               "License":{"value":"cc-by-3.0","source":"commons-templates","hidden":""}},
             "mime":"image/jpeg"}]},
          {"pageid":130158199,"ns":6,"title":"File:Pixel lighthouse icon.svg","index":1,
           "imageinfo":[{"size":231,"width":512,"height":512,
             "thumburl":"https://thumb.wikimedia.org/wikipedia/commons/thumb/0/0c/Pixel_lighthouse_icon.svg/500px-Pixel_lighthouse_icon.svg.png?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=thumbnail",
             "thumbwidth":500,"thumbheight":500,
             "url":"https://upload.wikimedia.org/wikipedia/commons/0/0c/Pixel_lighthouse_icon.svg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=original",
             "descriptionurl":"https://commons.wikimedia.org/wiki/File:Pixel_lighthouse_icon.svg",
             "extmetadata":{"ObjectName":{"value":"Pixel lighthouse icon"},
               "Artist":{"value":"<div style=\"display:inline-block;padding:0 3px;background-color:#BDC\"><a href=\"//commons.wikimedia.org/wiki/File:Pixel_lighthouse_icon.png\"><b>Original: </b></a></div> <div style=\"display:inline-block;\"><a href=\"//commons.wikimedia.org/wiki/User:StuckInLagToad\">StuckInLagToad</a></div>"},
               "LicenseShortName":{"value":"CC BY-SA 4.0"},
               "LicenseUrl":{"value":"https://creativecommons.org/licenses/by-sa/4.0"},
               "License":{"value":"cc-by-sa-4.0"}},
             "mime":"image/svg+xml"}]},
          {"pageid":152398201,"ns":6,"title":"File:Coat of Arms of Teriberka (Murmansk oblast).svg","index":5,
           "imageinfo":[{"size":57863,"width":1117,"height":1483,
             "thumburl":"https://thumb.wikimedia.org/wikipedia/commons/thumb/d/d6/Coat_of_Arms_of_Teriberka_%28Murmansk_oblast%29.svg/500px-Coat_of_Arms_of_Teriberka_%28Murmansk_oblast%29.svg.png",
             "thumbwidth":500,"thumbheight":664,
             "url":"https://upload.wikimedia.org/wikipedia/commons/d/d6/Coat_of_Arms_of_Teriberka_%28Murmansk_oblast%29.svg",
             "descriptionurl":"https://commons.wikimedia.org/wiki/File:Coat_of_Arms_of_Teriberka_(Murmansk_oblast).svg",
             "extmetadata":{"Artist":{"value":"<p>Идея герба: Михаил Ригерт (Кострома).\n</p><p>Художник &amp; дизайн: Анна Гарсия<br>(Москва)</p>"},
               "LicenseShortName":{"value":"Public domain"},
               "AttributionRequired":{"value":"false"},
               "License":{"value":"pd"}},
             "mime":"image/svg+xml"}]},
          {"pageid":4001,"ns":6,"title":"File:Fort_Lauderdale South USGS Map 1983.tiff","index":3,
           "imageinfo":[{"size":40888200,"width":6900,"height":8700,
             "thumburl":"https://thumb.wikimedia.org/wikipedia/commons/thumb/7/77/Fort_Lauderdale_South_USGS_Map_1983.tiff/lossy-page1-500px-Fort_Lauderdale_South_USGS_Map_1983.tiff.jpg",
             "thumbwidth":500,"thumbheight":630,
             "url":"https://upload.wikimedia.org/wikipedia/commons/7/77/Fort_Lauderdale_South_USGS_Map_1983.tiff",
             "descriptionurl":"https://evil.example.com/wiki/File:Fort",
             "extmetadata":{"LicenseShortName":{"value":"GFDL"},"License":{"value":"gfdl"},
               "Attribution":{"value":"Map &copy; USGS &ndash; please credit"}},
             "mime":"image/tiff"}]},
          {"pageid":4002,"ns":6,"title":"File:Elsewhere.jpg","index":4,
           "imageinfo":[{"size":1000,"width":800,"height":600,
             "thumburl":"https://example.com/thumb/500px-Elsewhere.jpg",
             "url":"https://example.com/Elsewhere.jpg","mime":"image/jpeg"}]},
          {"pageid":4003,"ns":6,"title":"File:Plain http.jpg","index":6,
           "imageinfo":[{"size":1000,"width":800,"height":600,
             "url":"http://upload.wikimedia.org/wikipedia/commons/a/ab/Plain_http.jpg","mime":"image/jpeg"}]},
          {"pageid":4004,"ns":6,"title":"File:Deleted.jpg","index":7,"missing":true}
        ]}}
    """.trimIndent()

    @Test fun resultsKeepCommonsRankingAndDropAnythingOffWikimedia() {
        val page = CommonsImageClient.parseSearch(fixture)
        assertThat(page.nextOffset).isEqualTo(24)
        // Ranked by "index"; the example.com, plain-http and missing pages are dropped.
        assertThat(page.images.map { it.id }).containsExactly(130158199L, 27536037L, 4001L, 152398201L).inOrder()
    }

    @Test fun aPhotoCarriesItsTitleAuthorLicenceAndSource() {
        val photo = CommonsImageClient.parseSearch(fixture).images.single { it.id == 27536037L }
        assertThat(photo.title).isEqualTo("La Martre lighthouse - La Martre, QC, CA - 16-06-2013")
        assertThat(photo.author).isEqualTo("Letartean")
        assertThat(photo.licence.shortName).isEqualTo("CC BY 3.0")
        assertThat(photo.licence.family).isEqualTo(LicenceFamily.CC_BY)
        assertThat(photo.licence.url).isEqualTo("https://creativecommons.org/licenses/by/3.0")
        assertThat(photo.licence.attributionRequired).isTrue()
        assertThat(photo.pageUrl).isEqualTo("https://commons.wikimedia.org/wiki/File:La_Martre_lighthouse_-_La_Martre,_QC,_CA_-_16-06-2013.jpg")
        assertThat(photo.domain).isEqualTo("commons.wikimedia.org")
        assertThat(photo.thumbWidth).isEqualTo(500)
        assertThat(photo.aspect).isWithin(0.01f).of(500f / 331f)
        // Wider than 1280: the large view is the 1280 rendition; the 6 MB original is saved as it is.
        assertThat(photo.viewUrl).contains("/1280px-La_Martre_lighthouse")
        assertThat(photo.viewUrl).startsWith("https://thumb.wikimedia.org/")
        assertThat(photo.saveUrl).startsWith("https://upload.wikimedia.org/wikipedia/commons/9/9c/")
    }

    @Test fun aDrawingIsViewedAndSavedAsAPngRendition() {
        val icon = CommonsImageClient.parseSearch(fixture).images.single { it.id == 130158199L }
        assertThat(icon.mime).isEqualTo("image/svg+xml")
        assertThat(icon.author).isEqualTo("Original: StuckInLagToad")
        assertThat(icon.licence.family).isEqualTo(LicenceFamily.CC_BY_SA)
        assertThat(icon.viewUrl).contains("/1280px-Pixel_lighthouse_icon.svg.png")
        assertThat(icon.saveUrl).contains("/1920px-Pixel_lighthouse_icon.svg.png")
    }

    @Test fun htmlFieldsBecomePlainWordsAndMissingTitlesComeFromTheFileName() {
        val arms = CommonsImageClient.parseSearch(fixture).images.single { it.id == 152398201L }
        assertThat(arms.title).isEqualTo("Coat of Arms of Teriberka (Murmansk oblast)")
        assertThat(arms.author).isEqualTo("Идея герба: Михаил Ригерт (Кострома). Художник & дизайн: Анна Гарсия (Москва)")
        assertThat(arms.licence.family).isEqualTo(LicenceFamily.PUBLIC_DOMAIN)
        assertThat(arms.licence.attributionRequired).isFalse()
    }

    @Test fun aHugeTiffIsSavedAsTheLargestStandardRenditionAndAForeignSourceIsReplaced() {
        val map = CommonsImageClient.parseSearch(fixture).images.single { it.id == 4001L }
        assertThat(map.title).isEqualTo("Fort Lauderdale South USGS Map 1983")
        assertThat(map.licence.family).isEqualTo(LicenceFamily.OTHER)
        assertThat(map.licence.reusable).isFalse()
        assertThat(map.attribution).isEqualTo("Map (c) USGS - please credit")
        // A description page off Commons is not trusted: it is rebuilt from the file name.
        assertThat(map.pageUrl).isEqualTo("https://commons.wikimedia.org/wiki/File:Fort_Lauderdale_South_USGS_Map_1983.tiff")
        // TIFF thumbnails carry a "lossy-page1-" prefix; the width swap still finds the number.
        assertThat(map.saveUrl).endsWith("/lossy-page1-3840px-Fort_Lauderdale_South_USGS_Map_1983.tiff.jpg")
        assertThat(map.viewUrl).endsWith("/lossy-page1-1280px-Fort_Lauderdale_South_USGS_Map_1983.tiff.jpg")
    }

    @Test fun anEmptySearchHasNoResultsAndNoNextPage() {
        val page = CommonsImageClient.parseSearch("""{"batchcomplete":true}""")
        assertThat(page.images).isEmpty()
        assertThat(page.nextOffset).isNull()
    }

    @Test fun anApiErrorIsReportedInPlainWords() {
        val error = assertThrows(AssistantApiException::class.java) {
            CommonsImageClient.parseSearch("""{"error":{"code":"badvalue","info":"Unrecognized value for parameter \"gsrsort\"."}}""")
        }
        assertThat(error.message).contains("Unrecognized value")
    }

    @Test fun theSearchCarriesTypeAndSizeKeywords() {
        assertThat(CommonsImageClient.searchText("  lighthouses   at night ", FindFilters()))
            .isEqualTo("lighthouses at night filetype:bitmap")
        assertThat(CommonsImageClient.searchText("map", FindFilters(kind = FindKind.DRAWINGS, size = FindSize.LARGE)))
            .isEqualTo("map filetype:drawing fileres:>2000")
        assertThat(CommonsImageClient.searchText("map", FindFilters(size = FindSize.MEDIUM)))
            .isEqualTo("map filetype:bitmap fileres:1000,2000")
    }

    @Test fun theRequestAsksTheFileNamespaceForThumbnailsAndCredits() {
        val url = CommonsImageClient.searchUrl("lighthouses", FindFilters(), offset = 24)
        assertThat(url.scheme).isEqualTo("https")
        assertThat(url.host).isEqualTo("commons.wikimedia.org")
        assertThat(url.encodedPath).isEqualTo("/w/api.php")
        assertThat(url.queryParameter("generator")).isEqualTo("search")
        assertThat(url.queryParameter("gsrnamespace")).isEqualTo("6")
        assertThat(url.queryParameter("gsrsearch")).isEqualTo("lighthouses filetype:bitmap")
        assertThat(url.queryParameter("gsroffset")).isEqualTo("24")
        assertThat(url.queryParameter("iiurlwidth")).isEqualTo("500")
        assertThat(url.queryParameter("formatversion")).isEqualTo("2")
        assertThat(url.queryParameter("iiextmetadatafilter")).contains("LicenseShortName")
        assertThat(url.queryParameter("iiextmetadatafilter")).contains("Artist")
        // The first page sends no offset at all.
        assertThat(CommonsImageClient.searchUrl("x", FindFilters()).queryParameter("gsroffset")).isNull()
    }

    @Test fun onlyStandardWidthsAreAskedFor() {
        val thumb = "https://thumb.wikimedia.org/wikipedia/commons/thumb/9/9c/A_500px-name.jpg/500px-A_500px-name.jpg?utm_source=x"
        assertThat(CommonsImageClient.widthUrl(thumb, 1280))
            .isEqualTo("https://thumb.wikimedia.org/wikipedia/commons/thumb/9/9c/A_500px-name.jpg/1280px-A_500px-name.jpg?utm_source=x")
        assertThat(CommonsImageClient.widthUrl("https://upload.wikimedia.org/wikipedia/commons/9/9c/A.jpg", 1280)).isNull()
        // A photo narrower than 1280 is viewed as its original; a small TIFF falls back to the thumbnail.
        assertThat(CommonsImageClient.viewUrl(thumb, "https://upload.wikimedia.org/o.jpg", "image/jpeg", 900))
            .isEqualTo("https://upload.wikimedia.org/o.jpg")
        assertThat(CommonsImageClient.viewUrl(thumb, "https://upload.wikimedia.org/o.tif", "image/tiff", 900)).isEqualTo(thumb)
        // A 30 MB JPEG 5000px wide is saved as the 3840 rendition; a TIFF 1500px wide as the 1280 one.
        assertThat(CommonsImageClient.saveUrl(thumb, "o", "image/jpeg", 5000, 30L * 1024 * 1024)).contains("/3840px-")
        assertThat(CommonsImageClient.saveUrl(thumb, "o", "image/tiff", 1500, 9_000_000)).contains("/1280px-")
        assertThat(CommonsImageClient.STANDARD_WIDTHS).containsExactly(500, 960, 1280, 1920, 3840).inOrder()
    }

    @Test fun mediaUrlsMustBeHttpsOnWikimedia() {
        assertThat(CommonsImageClient.isMediaUrl("https://upload.wikimedia.org/a.jpg")).isTrue()
        assertThat(CommonsImageClient.isMediaUrl("https://thumb.wikimedia.org/a.jpg")).isTrue()
        assertThat(CommonsImageClient.isMediaUrl("http://upload.wikimedia.org/a.jpg")).isFalse()
        assertThat(CommonsImageClient.isMediaUrl("https://wikimedia.org.evil.example/a.jpg")).isFalse()
        assertThat(CommonsImageClient.isMediaUrl("https://evilwikimedia.org/a.jpg")).isFalse()
        assertThat(CommonsImageClient.isMediaUrl("not a url")).isFalse()
    }

    @Test fun plainTextDecodesEntitiesAndBreaks() {
        assertThat(CommonsImageClient.plainText("Tom &amp; Jerry&nbsp;&#8211; &#x41;<br/>next")).isEqualTo("Tom & Jerry – A next")
        assertThat(CommonsImageClient.plainText(null)).isEmpty()
        assertThat(CommonsImageClient.titleFromFile("File:Tayid_Lighthouse at Night.jpeg")).isEqualTo("Tayid Lighthouse at Night")
        assertThat(CommonsImageClient.commonsPage("File:Tayid Lighthouse.jpg"))
            .isEqualTo("https://commons.wikimedia.org/wiki/File:Tayid_Lighthouse.jpg")
    }

    @Test fun theUserAgentNamesTheAppAndHowToReachIt() {
        // Wikimedia answers a bare "okhttp" user agent with 403.
        assertThat(CommonsImageClient.USER_AGENT).startsWith("KryzzAI/")
        assertThat(CommonsImageClient.USER_AGENT).contains("https://github.com/kriddss3/kryzz-ai")
    }
}
