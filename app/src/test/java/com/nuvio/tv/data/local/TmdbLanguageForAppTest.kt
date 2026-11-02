package com.nuvio.tv.data.local

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class TmdbLanguageForAppTest {
    @Test
    fun `latin american spanish asks TMDB for the Mexican one`() {
        assertEquals("es-MX", tmdbLanguageFor(Locale.forLanguageTag("es-419")))
        assertEquals("es-MX", tmdbLanguageFor(Locale.forLanguageTag("es-MX")))
    }

    @Test
    fun `plain Spanish is the Spain one`() {
        assertEquals("es-ES", tmdbLanguageFor(Locale.forLanguageTag("es")))
    }

    @Test
    fun `English maps to the US one`() {
        assertEquals("en-US", tmdbLanguageFor(Locale.forLanguageTag("en")))
        assertEquals("en-US", tmdbLanguageFor(Locale.forLanguageTag("en-GB")))
    }

    @Test
    fun `Portuguese and Chinese keep their region`() {
        assertEquals("pt-BR", tmdbLanguageFor(Locale.forLanguageTag("pt-BR")))
        assertEquals("pt-PT", tmdbLanguageFor(Locale.forLanguageTag("pt-PT")))
        assertEquals("zh-TW", tmdbLanguageFor(Locale.forLanguageTag("zh-TW")))
        assertEquals("zh-CN", tmdbLanguageFor(Locale.forLanguageTag("zh-CN")))
    }

    @Test
    fun `legacy Java codes become the ISO ones`() {
        assertEquals("id", tmdbLanguageFor(Locale("in")))
        assertEquals("he", tmdbLanguageFor(Locale("iw")))
    }
}
