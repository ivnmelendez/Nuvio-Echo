package com.nuvio.tv.data.remoteconfig

import android.content.Context
import com.nuvio.tv.R
import com.nuvio.tv.data.mdblist.MDBLIST_WATCHLIST_KEY
import com.nuvio.tv.domain.model.LibrarySourceMode
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.repository.LibraryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
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
    @ApplicationContext private val context: Context,
    private val libraryRepository: LibraryRepository,
    private val config: RemoteHomeConfigRepository,
    private val lists: MdbListListRepository,
    private val urlProvider: HomeConfigUrlProvider
) {
    /** The address the rows come from; it changes when an addon that names one is added, removed or turned off. */
    fun configUrl(): kotlinx.coroutines.flow.Flow<String> = urlProvider.urlFlow()

    /** Watch later as one row: the MDBList watchlist when MDBList is the library, else every title saved here. */
    private suspend fun watchlistRows(): List<RemoteRowItems> {
        val fromMdbList = libraryRepository.sourceMode.first() == LibrarySourceMode.MDBLIST
        val items = libraryRepository.libraryItems.first()
            .filter { !fromMdbList || MDBLIST_WATCHLIST_KEY in it.listKeys }
            .sortedByDescending { it.listedAt }
            .map { it.toMetaPreview() }
        if (items.isEmpty()) return emptyList()
        val row = RemoteRow(
            list = "watchlist", title = context.getString(R.string.nav_library), limit = items.size, hideTitle = false
        )
        return listOf(RemoteRowItems(row, items))
    }

    suspend fun cached(tab: String): List<RemoteRowItems> {
        if (tab == RemoteHomeConfig.TAB_WATCHLIST) return watchlistRows()
        val rows = config.cached()?.rowsFor(tab).orEmpty().filter { it.source != RemoteRow.SOURCE_ADDON }
        return rows.map { RemoteRowItems(it, lists.cachedItems(it)) }.filter { it.items.isNotEmpty() }
    }

    suspend fun load(tab: String): List<RemoteRowItems> = coroutineScope {
        if (tab == RemoteHomeConfig.TAB_WATCHLIST) return@coroutineScope watchlistRows()
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
