package com.nuvio.tv.core.deeplink

import com.nuvio.tv.domain.deeplink.AppDeepLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkParserTest {
    @Test
    fun parsesMetaQueryDeepLink() {
        assertEquals(
            AppDeepLink.Meta(type = "series", id = "tt0944947"),
            DeepLinkParser.parse("tivistream://meta?type=series&id=tt0944947")
        )
    }

    @Test
    fun parsesAddonInstallDeepLink() {
        assertEquals(
            AppDeepLink.AddonInstall("https://free.nebulapro.xyz/sports/i/free/manifest.json"),
            DeepLinkParser.parse("tivistream://free.nebulapro.xyz/sports/i/free/manifest.json")
        )
    }

    @Test
    fun parsesStremioAddonInstallDeepLink() {
        assertEquals(
            AppDeepLink.AddonInstall("https://free.nebulapro.xyz/sports/i/free/manifest.json"),
            DeepLinkParser.parse("stremio://free.nebulapro.xyz/sports/i/free/manifest.json")
        )
    }

    @Test
    fun parsesDirectImdbDetailDeepLink() {
        assertEquals(
            AppDeepLink.Meta(type = "series", id = "tt0944947"),
            DeepLinkParser.parse("tivistream://series/tt0944947")
        )
    }

    @Test
    fun parsesProviderImdbDetailDeepLink() {
        assertEquals(
            AppDeepLink.Meta(type = "series", id = "tt0944947"),
            DeepLinkParser.parse("tivistream://imdb/series/tt0944947")
        )
    }

    @Test
    fun parsesProviderTmdbDetailDeepLink() {
        assertEquals(
            AppDeepLink.Meta(type = "series", id = "tmdb:1399"),
            DeepLinkParser.parse("tivistream://tmdb/tv/1399")
        )
    }

    @Test
    fun doesNotTreatAuthLinkAsAddonInstall() {
        assertNull(DeepLinkParser.parse("tivistream://auth/trakt?code=abc"))
    }

    @Test
    fun doesNotTreatNonHostStremioLinkAsAddonInstall() {
        assertNull(DeepLinkParser.parse("stremio://detail/series/tt0944947"))
    }
}
