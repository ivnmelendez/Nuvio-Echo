package com.nuvio.tv.data.remoteconfig

import com.nuvio.tv.domain.model.MetaPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton

/** One row of a tab with its titles, as the importer published them. */
data class RemoteRowItems(val row: RemoteRow, val items: List<MetaPreview>)

/**
 * The rows of one tab (home, movies or series) from the remote config: [cached] answers from memory or disk with no
 * network, [load] refreshes from GitHub. Rows that came back empty are left out, and addon rows aren't used here
 * (Home already has its own addon catalogs).
 */
@Singleton
class RemoteTabRowsRepository @Inject constructor(
    private val config: RemoteHomeConfigRepository,
    private val lists: MdbListListRepository
) {
    suspend fun cached(tab: String): List<RemoteRowItems> {
        val rows = config.cached()?.rowsFor(tab).orEmpty().filter { it.source != RemoteRow.SOURCE_ADDON }
        return rows.map { RemoteRowItems(it, lists.cachedItems(it)) }.filter { it.items.isNotEmpty() }
    }

    suspend fun load(tab: String): List<RemoteRowItems> = coroutineScope {
        val rows = config.load().rowsFor(tab).filter { it.source != RemoteRow.SOURCE_ADDON }
        // A few at a time, so a low-end device isn't hit with every row's request at once.
        val permits = Semaphore(MAX_PARALLEL_ROWS)
        rows.map { row -> async { RemoteRowItems(row, permits.withPermit { lists.items(row) }) } }
            .awaitAll()
            .filter { it.items.isNotEmpty() }
    }

    private companion object {
        const val MAX_PARALLEL_ROWS = 3
    }
}
