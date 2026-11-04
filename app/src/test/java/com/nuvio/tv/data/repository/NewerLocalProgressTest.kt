package com.nuvio.tv.data.repository

import com.nuvio.tv.domain.model.WatchProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class NewerLocalProgressTest {
    private fun progress(contentId: String, season: Int, episode: Int, lastWatched: Long, position: Long) = WatchProgress(
        contentId = contentId, contentType = "series", name = contentId, poster = null, backdrop = null, logo = null,
        videoId = "$contentId:$season:$episode", season = season, episode = episode, episodeTitle = null,
        position = position, duration = 100L, lastWatched = lastWatched
    )

    @Test
    fun `a local episode newer than everything the service has for the title is kept`() {
        val service = progress("tt1", 27, 3, 100L, 40L)
        val local = progress("tt1", 27, 5, 200L, 50L)
        assertEquals(listOf(local, service), withNewerLocalProgress(listOf(service), listOf(local)))
    }

    @Test
    fun `an older local episode, or a title the service does not have, is not added`() {
        val service = progress("tt1", 27, 3, 300L, 40L)
        val older = progress("tt1", 27, 5, 200L, 50L)
        val other = progress("tt2", 1, 1, 999L, 50L)
        assertEquals(listOf(service), withNewerLocalProgress(listOf(service), listOf(older, other)))
    }
}
