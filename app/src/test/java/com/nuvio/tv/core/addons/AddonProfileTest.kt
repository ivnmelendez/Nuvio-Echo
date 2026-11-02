package com.nuvio.tv.core.addons

import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.AddonBehaviorHints
import com.nuvio.tv.domain.model.enabledAddons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddonProfileTest {
    private fun addon(
        id: String,
        baseUrl: String = "https://$id.example",
        language: String? = null,
        replaces: Boolean? = null,
        enabled: Boolean = true
    ) = Addon(
        id = id, name = id, version = "1", description = null, logo = null, baseUrl = baseUrl,
        catalogs = emptyList(), types = emptyList(), resources = emptyList(),
        behaviorHints = if (language != null || replaces != null) {
            AddonBehaviorHints(preferredLanguage = language, replacesDefaultAddons = replaces)
        } else null,
        enabled = enabled
    )

    @Test
    fun `no addon asks for a language`() {
        assertNull(AddonProfile.pickLanguage(listOf(addon("a"), addon("b"))))
    }

    @Test
    fun `the first addon that is on and names a language decides`() {
        val addons = listOf(addon("a"), addon("b", language = "es-419"), addon("c", language = "en"))
        assertEquals("es-419", AddonProfile.pickLanguage(addons))
    }

    @Test
    fun `an addon that is off asks for nothing`() {
        assertNull(AddonProfile.pickLanguage(listOf(addon("b", language = "es-419", enabled = false))))
    }

    @Test
    fun `without a replacing addon every enabled addon stays`() {
        val list = listOf(addon("a"), addon("b", enabled = false), addon("c"))
        assertEquals(listOf("a", "c"), list.enabledAddons().map { it.id })
    }

    @Test
    fun `a replacing addon that is off sets nothing aside`() {
        val list = listOf(addon("a"), addon("mine", replaces = true, enabled = false))
        assertEquals(listOf("a"), list.enabledAddons().map { it.id })
    }
}
