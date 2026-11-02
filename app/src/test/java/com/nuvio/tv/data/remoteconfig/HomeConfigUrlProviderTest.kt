package com.nuvio.tv.data.remoteconfig

import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.AddonBehaviorHints
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeConfigUrlProviderTest {
    private val fallback = ""

    private fun addon(id: String, rows: String? = null, enabled: Boolean = true) = Addon(
        id = id, name = id, version = "1", description = null, logo = null, baseUrl = "https://$id.example",
        catalogs = emptyList(), types = emptyList(), resources = emptyList(),
        behaviorHints = rows?.let { AddonBehaviorHints(homeConfigUrl = it) },
        enabled = enabled
    )

    @Test
    fun `no addon names a rows file, so the build address is used`() {
        assertEquals(fallback, HomeConfigUrlProvider.pick(listOf(addon("cinemeta"), addon("other")), fallback))
    }

    @Test
    fun `an addon that names a rows file wins over the build address`() {
        val url = "https://rows.example/home-config.json"
        assertEquals(url, HomeConfigUrlProvider.pick(listOf(addon("cinemeta"), addon("mine", url)), "https://build.example/c.json"))
    }

    @Test
    fun `a disabled addon is ignored`() {
        assertEquals(fallback, HomeConfigUrlProvider.pick(listOf(addon("mine", "https://rows.example/c.json", enabled = false)), fallback))
    }

    @Test
    fun `only https addresses are accepted`() {
        assertEquals(fallback, HomeConfigUrlProvider.pick(listOf(addon("mine", "http://rows.example/c.json")), fallback))
        assertEquals(fallback, HomeConfigUrlProvider.pick(listOf(addon("mine", "  ")), fallback))
    }

    @Test
    fun `the first addon in order wins when several name one`() {
        assertEquals(
            "https://a.example/c.json",
            HomeConfigUrlProvider.pick(listOf(addon("a", "https://a.example/c.json"), addon("b", "https://b.example/c.json")), fallback)
        )
    }
}
