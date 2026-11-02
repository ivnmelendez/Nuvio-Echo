package com.nuvio.tv.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SettingsCatalogTest {

    private fun visible(debug: Boolean = false, essential: Boolean = false) =
        visibleSettingsCategories(isPrimaryProfile = true, isEssentialMode = essential, isDebugBuild = debug)

    @Test
    fun `settings shows playback, addons, tracking, language and about in that order`() {
        assertEquals(
            listOf(
                SettingsCategory.PLAYBACK,
                SettingsCategory.ADDONS,
                SettingsCategory.TRACKING,
                SettingsCategory.LANGUAGE,
                SettingsCategory.ABOUT
            ),
            visible()
        )
    }

    @Test
    fun `the list is the same in debug builds and in essential mode`() {
        assertEquals(visible(), visible(debug = true))
        assertEquals(visible(), visible(essential = true))
    }

    @Test
    fun `debug and experience never appear in the rail`() {
        assertFalse(SettingsCategory.DEBUG in visible(debug = true))
        assertFalse(SettingsCategory.EXPERIENCE in visible(debug = true))
    }

    @Test
    fun `each group is contiguous`() {
        val groups = visible().map { it.group }
        assertEquals(groups.distinct(), groups.fold(emptyList<SettingsRailGroup>()) { acc, g ->
            if (acc.lastOrNull() == g) acc else acc + g
        })
    }

    @Test
    fun `dividers are placed only where the group changes`() {
        val categories = visible()
        val dividerBefore = categories.indices.filter { categories.startsNewGroup(it) }.map { categories[it] }
        assertEquals(
            listOf(SettingsCategory.TRACKING, SettingsCategory.LANGUAGE),
            dividerBefore
        )
    }
}
