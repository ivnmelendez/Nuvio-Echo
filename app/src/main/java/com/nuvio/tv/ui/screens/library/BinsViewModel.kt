package com.nuvio.tv.ui.screens.library

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.R
import com.nuvio.tv.core.bins.Bin
import com.nuvio.tv.core.bins.BinEntry
import com.nuvio.tv.core.bins.BinNameResult
import com.nuvio.tv.core.bins.BinSort
import com.nuvio.tv.core.bins.BinsReducer
import com.nuvio.tv.core.bins.BinsRepository
import com.nuvio.tv.core.poster.CustomPosterScreen
import com.nuvio.tv.core.poster.patternForScreen
import com.nuvio.tv.core.poster.withCustomPosterUrls
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.repository.MetaRepository
import com.nuvio.tv.domain.repository.WatchProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A saved title, ready to draw. */
@Immutable
data class BinTitle(val entry: BinEntry, val preview: MetaPreview)

@Immutable
data class BinsUiState(
    val loaded: Boolean = false,
    /** Watch later: every saved title, the most recently added first. */
    val titles: List<BinTitle> = emptyList(),
    val posterWidthDp: Int = 126,
    /** Layout's custom poster URL for the Library (blank when off). */
    val posterPattern: String = "",
    val posterCornerDp: Int = 12,
    val watchedMovieIds: Set<String> = emptySet(),
    val watchedSeriesIds: Set<String> = emptySet()
)

/** The Library's one list, Watch later: the titles of every bin together, newest saved first. */
@HiltViewModel
class BinsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BinsRepository,
    private val layoutPreferences: LayoutPreferenceDataStore,
    private val watchProgressRepository: WatchProgressRepository,
    private val watchedSeriesStateHolder: com.nuvio.tv.data.local.WatchedSeriesStateHolder,
    private val metaRepository: MetaRepository
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private var messageJob: Job? = null

    private val posterLayout = combine(
        layoutPreferences.posterCardWidthDp,
        layoutPreferences.posterCardCornerRadiusDp,
        layoutPreferences.customPosterUrlPattern,
        layoutPreferences.customPosterEnabledScreens
    ) { width, corner, pattern, screens ->
        Triple(width, corner, patternForScreen(pattern, CustomPosterScreen.LIBRARY, screens))
    }

    private val watched = combine(
        watchProgressRepository.observeWatchedMovieIds(),
        watchedSeriesStateHolder.fullyWatchedSeriesIds
    ) { movies, series -> movies to series }

    val uiState: StateFlow<BinsUiState> = combine(repository.state, posterLayout, watched) { state, layout, watched ->
        val (width, corner, pattern) = layout
        val entries = state.bins.flatMap { it.entries }.distinctBy { it.key }.sortedByDescending { it.addedAt }
        val previews = entries.map { it.toPreview() }.withCustomPosterUrls(pattern)
        BinsUiState(
            loaded = true,
            titles = entries.zip(previews) { entry, preview -> BinTitle(entry, preview) },
            posterWidthDp = width,
            posterCornerDp = corner,
            posterPattern = pattern,
            watchedMovieIds = watched.first,
            watchedSeriesIds = watched.second
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BinsUiState())

    fun removeTitle(title: BinTitle) {
        viewModelScope.launch { repository.removeFromWatchLater(title.entry.id, title.entry.type) }
        showMessage(context.getString(R.string.watch_later_removed))
    }

    fun onTitleOpened(title: BinTitle) {
        prefetch(title.entry)
    }

    fun cachedBackdrop(title: BinTitle): String? = metaRepository.getCachedMeta(title.entry.type, title.entry.id)?.backdropUrl

    private var prefetchJob: Job? = null

    /** Warms the details page for the focused title, so opening it is instant. */
    fun prefetch(entry: BinEntry) {
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch {
            delay(150)
            // firstOrNull: a lookup that ends with no result must not crash the screen.
            runCatching {
                metaRepository.getMetaFromAllAddons(type = entry.type, id = entry.id)
                    .firstOrNull { it !is com.nuvio.tv.core.network.NetworkResult.Loading }
            }
        }
    }

    private fun showMessage(text: String) {
        _message.value = text
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            delay(2_400)
            _message.value = null
        }
    }
}

internal fun BinEntry.toPreview(): MetaPreview = MetaPreview(
    id = id,
    type = ContentType.fromString(type),
    rawType = type,
    name = name,
    poster = poster,
    posterShape = PosterShape.POSTER,
    background = background,
    logo = logo,
    description = description,
    releaseInfo = releaseInfo,
    imdbRating = imdbRating,
    genres = genres
)
