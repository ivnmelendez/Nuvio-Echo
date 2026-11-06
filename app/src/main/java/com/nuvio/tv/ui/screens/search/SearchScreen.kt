package com.nuvio.tv.ui.screens.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.LocalContentFocusRequester
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.components.WatchedMarker
import com.nuvio.tv.ui.newlook.home.Artwork
import com.nuvio.tv.ui.screens.browse.BrowseSkeletonCard
import com.nuvio.tv.ui.screens.browse.rememberSkeletonPulse
import kotlinx.coroutines.launch

// Measured on the reference at 1920x1080 (dp = px / 2).
private val ScreenEdge = 24.dp
private val FieldTop = 12.dp
private val FieldHeight = 56.dp
private val MicSize = 46.dp
private val CloseSize = 42.dp
private val PosterWidth = 148.dp
private val PosterHeight = 222.dp
private val PosterGap = 14.dp
/** From the bottom of the field to the top of the posters: the row title sits in it. */
private val TitleBand = 44.dp
private val TitleTop = 15.dp

private val BackgroundTop = Color(0xFF0D2F3D)
private val FieldText = TextStyle(color = Color.White, fontSize = 18.sp)

/** The page only scrolls where this screen says; focus moving never drags it. */
@OptIn(ExperimentalFoundationApi::class)
private val NoPageScroll = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

private class ResultsGroup(
    val titleRes: Int,
    val items: List<MetaPreview>,
    /** Catalogs of this kind still answering (or fetching their next page). */
    val loading: Boolean,
    val rows: List<CatalogRow>
)

/** How much room stays under a row that is brought into view (the reference leaves 37.5 dp). */
private val RowBottomMargin = 37.5.dp
/** Titles left in a row at which the next page is asked for. */
private const val LOAD_MORE_AHEAD = 6

/**
 * Search as the reference has it: a field with the system keyboard over a teal-to-black page, and the results below
 * in one row of posters per kind, TV Shows then Movies. They fill in as the query is typed.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
fun SearchScreen(
    searchViewModel: SearchViewModel,
    onNavigateToDetail: (itemId: String, itemType: String, addonBaseUrl: String?) -> Unit
) {
    val state by searchViewModel.uiState.collectAsState()
    val watchedMovieIds by searchViewModel.watchedMovieIds.collectAsState()
    val watchedSeriesIds by searchViewModel.watchedSeriesIds.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current

    val query = state.query
    val trimmed = query.trim()
    val groups = remember(state.catalogRows, trimmed) {
        if (trimmed.length < MIN_SEARCH_QUERY_LENGTH) {
            emptyList()
        } else {
            val (series, movies) = state.catalogRows.partition { it.type == ContentType.SERIES || it.type == ContentType.TV }
            listOf(R.string.nav_series to series, R.string.nav_movies to movies).map { (title, rows) ->
                ResultsGroup(title, SearchResultsMerger.merge(rows, trimmed), rows.any { it.isLoading }, rows)
            }
        }
    }
    // A kind with nothing in yet waits (as a row of skeletons) only once another has something to show; until then
    // the page is empty and a spinner turns.
    val anyResults = groups.any { it.items.isNotEmpty() }
    val shown = groups.filter { it.items.isNotEmpty() || (it.loading && anyResults) }
    val waiting = trimmed.length >= MIN_SEARCH_QUERY_LENGTH && !anyResults && (state.isSearching || groups.any { it.loading })

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val marginPx = with(LocalDensity.current) { RowBottomMargin.roundToPx() }
    // Focus on a poster brings its whole row into view, with room under it, rather than only the poster's edge. The
    // page scrolls only here: the default would drag it by each poster's own edge (cutting the row off at the bottom).
    var focusedRow by remember { mutableStateOf(-1) }
    // A new query brings the page back to the top as well, under the field being typed in.
    LaunchedEffect(trimmed) { listState.scrollToItem(0) }
    LaunchedEffect(focusedRow) {
        if (focusedRow < 0) return@LaunchedEffect
        withFrameNanos { }
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == focusedRow } ?: return@LaunchedEffect
        val delta = when {
            item.offset < 0 -> item.offset
            item.offset + item.size > info.viewportEndOffset - marginPx ->
                (item.offset + item.size - (info.viewportEndOffset - marginPx)).coerceAtMost(item.offset)
            else -> 0
        }
        if (delta != 0) listState.animateScrollBy(delta.toFloat(), tween(150, easing = FastOutSlowInEasing))
    }

    fun setQuery(newQuery: String) = searchViewModel.onEvent(SearchEvent.QueryChanged(newQuery))

    val voice = rememberVoiceSearch { recognized ->
        setQuery(recognized)
        searchViewModel.onEvent(SearchEvent.SubmitSearch)
    }

    val fieldFocus = remember { FocusRequester() }
    val firstPoster = remember { FocusRequester() }
    var fieldFocused by remember { mutableStateOf(false) }
    // The keyboard is up as the screen opens, as on the reference.
    LaunchedEffect(Unit) {
        runCatching { fieldFocus.requestFocus() }
        keyboard?.show()
    }
    BackHandler(enabled = query.isNotEmpty() && fieldFocused) { setQuery("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BackgroundTop, Color.Black)))
            .focusRequester(LocalContentFocusRequester.current)
    ) {
        Row(
            modifier = Modifier
                .padding(start = ScreenEdge, end = ScreenEdge, top = FieldTop)
                .height(FieldHeight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundButton(
                shape = CircleShape,
                size = MicSize,
                background = Color.White.copy(alpha = 0.29f),
                icon = Icons.Filled.Mic,
                description = stringResource(R.string.nav_search),
                onClick = { voice.start() }
            )
            Spacer(Modifier.width(12.dp))
            val fieldShape = RoundedCornerShape(12.dp)
            BasicTextField(
                value = query,
                onValueChange = ::setQuery,
                singleLine = true,
                textStyle = FieldText,
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    searchViewModel.onEvent(SearchEvent.SubmitSearch)
                    keyboard?.hide()
                }),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .focusRequester(fieldFocus)
                    .onFocusChanged { fieldFocused = it.isFocused }
                    // Down goes to the first poster, not to whichever is nearest the middle of this wide field.
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                            runCatching { firstPoster.requestFocus() }.getOrDefault(false)
                        } else {
                            false
                        }
                    }
                    .clip(fieldShape)
                    .background(Color.White.copy(alpha = 0.02f))
                    .border(
                        BorderStroke(2.dp, Color.White.copy(alpha = if (fieldFocused) 0.85f else 0.25f)),
                        fieldShape
                    ),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) { inner() }
                }
            )
            Spacer(Modifier.width(8.dp))
            RoundButton(
                shape = RoundedCornerShape(10.dp),
                size = CloseSize,
                background = Color(0xFF2A2A2A),
                icon = Icons.Filled.Close,
                description = stringResource(R.string.action_clear),
                onClick = {
                    setQuery("")
                    runCatching { fieldFocus.requestFocus() }
                }
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (waiting) LoadingIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
            val pulse = rememberSkeletonPulse()
            val rowSpec = LocalBringIntoViewSpec.current
            CompositionLocalProvider(LocalBringIntoViewSpec provides NoPageScroll) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(shown, key = { _, group -> group.titleRes }) { rowIndex, group ->
                    Column {
                        Box(Modifier.height(TitleBand), contentAlignment = Alignment.TopStart) {
                            Text(
                                text = stringResource(group.titleRes),
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                                color = Color.White,
                                modifier = Modifier.padding(start = ScreenEdge, top = TitleTop)
                            )
                        }
                        val rowFirst = remember { FocusRequester() }
                        // A new query starts every row from its first poster: the row kept the place the last one was
                        // scrolled to, so fresh results could open cut off at the left.
                        val rowState = rememberLazyListState()
                        LaunchedEffect(trimmed) { rowState.scrollToItem(0) }
                        CompositionLocalProvider(LocalBringIntoViewSpec provides rowSpec) {
                        LazyRow(
                            state = rowState,
                            // Coming into a row from above or below lands on its first poster (or the one left last).
                            modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenEdge).focusRestorer { rowFirst },
                            horizontalArrangement = Arrangement.spacedBy(PosterGap)
                        ) {
                            itemsIndexed(group.items, key = { _, item -> "${item.apiType}:${item.id}" }) { index, item ->
                                SearchPoster(
                                    item = item,
                                    isWatched = if (item.apiType == "series" || item.apiType == "tv") {
                                        item.id in watchedSeriesIds
                                    } else {
                                        item.id in watchedMovieIds
                                    },
                                    modifier = if (index == 0) {
                                        Modifier.focusRequester(rowFirst).then(if (rowIndex == 0) Modifier.focusRequester(firstPoster) else Modifier)
                                    } else {
                                        Modifier
                                    },
                                    onClick = {
                                        searchViewModel.onEvent(SearchEvent.RememberSearchFromTextInput)
                                        onNavigateToDetail(item.id, item.apiType, item.sourceAddonBaseUrl)
                                    },
                                    onFocused = {
                                        focusedRow = rowIndex
                                        if (index >= group.items.size - LOAD_MORE_AHEAD) {
                                            group.rows.filter { it.hasMore && !it.isLoading }.forEach { row ->
                                                searchViewModel.onEvent(SearchEvent.LoadMoreCatalog(row.catalogId, row.addonId, row.apiType))
                                            }
                                        }
                                    }
                                )
                            }
                            if (group.loading) {
                                items(if (group.items.isEmpty()) 6 else 2) { BrowseSkeletonCard(PosterWidth, pulse) }
                            }
                        }
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun RoundButton(
    shape: androidx.compose.ui.graphics.Shape,
    size: androidx.compose.ui.unit.Dp,
    background: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(size)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(background)
            .then(if (focused) Modifier.border(BorderStroke(2.dp, Color.White.copy(alpha = 0.85f)), shape) else Modifier)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

/**
 * A result poster. Focus zooms the picture a little inside its frame, as in View more, so the card itself never grows
 * past its place in the row (a larger card was cut off by the row's edge).
 */
@Composable
private fun SearchPoster(
    item: MetaPreview,
    isWatched: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onFocused: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val zoom by animateFloatAsState(if (focused) 1.02f else 1f, tween(120, easing = FastOutSlowInEasing), label = "searchZoom")
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .size(PosterWidth, PosterHeight)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clip(shape)
            .background(Color(0xFF141414))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    ) {
        Artwork(
            url = item.poster,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom },
            fallbackTitle = item.name,
            fallbackUrl = item.rawPosterUrl
        )
        if (isWatched) {
            WatchedMarker(modifier = Modifier.align(Alignment.TopEnd).padding(end = 8.dp, top = 8.dp))
        }
        if (focused) Box(Modifier.fillMaxSize().border(2.dp, Color.White, shape))
    }
}
