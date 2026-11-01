package com.nuvio.tv.ui.screens.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Text
import com.nuvio.tv.LocalContentFocusRequester
import com.nuvio.tv.R
import com.nuvio.tv.core.bins.Bin
import com.nuvio.tv.core.bins.BinSort
import com.nuvio.tv.core.cloud.CloudLibraryPlaybackInfo
import com.nuvio.tv.ui.components.EmptyScreenState
import com.nuvio.tv.ui.components.GridContentCard
import com.nuvio.tv.ui.components.PosterCardDefaults
import com.nuvio.tv.ui.screens.home.HeroBackdropState
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.delay

private sealed interface LibraryOverlay {
    data class TitleOptions(val title: BinTitle, val anchor: Rect) : LibraryOverlay
}

/** Stable focus requesters by key ("title:<key>"). */
private class FocusKeys {
    private val map = HashMap<String, FocusRequester>()
    operator fun get(key: String): FocusRequester = map.getOrPut(key) { FocusRequester() }
}

private val ScreenPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 28.dp, bottom = 48.dp)

/** The Library: Watch later, the titles the viewer saved, the most recently added first. */
@Composable
fun LibraryScreen(
    onNavigateToDetail: (itemId: String, itemType: String, addonBaseUrl: String?) -> Unit,
    binsViewModel: BinsViewModel = hiltViewModel()
) {
    val state by binsViewModel.uiState.collectAsState()
    val message by binsViewModel.message.collectAsState()
    var overlay by remember { mutableStateOf<LibraryOverlay?>(null) }
    var rootOffset by remember { mutableStateOf(Offset.Zero) }
    var lastFocused by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingFocus by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusKeys() }
    val gridState = rememberLazyGridState()
    val contentFocus = LocalContentFocusRequester.current

    fun local(rect: Rect): Rect = rect.translate(-rootOffset)
    fun firstTitleKey(): String? = state.titles.firstOrNull()?.let { "title:${it.entry.key}" }

    // First focus once the titles are in: where it was (coming back from a title), else the first one.
    LaunchedEffect(state.loaded) {
        if (!state.loaded || pendingFocus != null) return@LaunchedEffect
        pendingFocus = lastFocused?.takeIf { key -> state.titles.any { "title:${it.entry.key}" == key } } ?: firstTitleKey()
    }

    // Moves focus to [pendingFocus], scrolling its row into view first when it's off screen.
    LaunchedEffect(pendingFocus, state.titles) {
        val key = pendingFocus ?: return@LaunchedEffect
        val index = state.titles.indexOfFirst { "title:${it.entry.key}" == key }
        if (index >= 0 && gridState.layoutInfo.visibleItemsInfo.none { it.index == index + 1 }) {
            runCatching { gridState.scrollToItem(index + 1) }
        }
        repeat(8) {
            if (runCatching { focus[key].requestFocus() }.getOrDefault(false)) {
                pendingFocus = null
                return@LaunchedEffect
            }
            delay(32)
        }
        pendingFocus = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Background)
            .onGloballyPositioned { rootOffset = it.positionInRoot() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(contentFocus)
                // Entering the screen (from the menu, or one of our own requests) lands on what's asked for, else where
                // focus last was, else the first title. Read when focus enters, not when composed.
                .focusProperties {
                    onEnter = {
                        val key = pendingFocus
                            ?: lastFocused?.takeIf { key -> state.titles.any { "title:${it.entry.key}" == key } }
                            ?: firstTitleKey()
                        key?.let { runCatching { focus[it].requestFocus() } }
                    }
                }
                .focusGroup()
        ) {
            TitlesGrid(
                state = state,
                gridState = gridState,
                focus = focus,
                onFocused = { lastFocused = it },
                onClick = { title ->
                    binsViewModel.onTitleOpened(title)
                    HeroBackdropState.update(binsViewModel.cachedBackdrop(title))
                    onNavigateToDetail(title.entry.id, title.entry.type, title.entry.addonBaseUrl)
                },
                onLongClick = { title, anchor -> overlay = LibraryOverlay.TitleOptions(title, local(anchor)) },
                onPrefetch = { binsViewModel.prefetch(it.entry) }
            )
        }

        when (val current = overlay) {
            is LibraryOverlay.TitleOptions -> BinPillMenu(
                anchor = current.anchor,
                title = current.title.entry.name,
                items = listOf(
                    BinMenuItem(stringResource(R.string.bins_title_open)) {
                        overlay = null
                        binsViewModel.onTitleOpened(current.title)
                        onNavigateToDetail(current.title.entry.id, current.title.entry.type, current.title.entry.addonBaseUrl)
                    },
                    BinMenuItem(stringResource(R.string.watch_later_remove)) {
                        val titles = state.titles
                        val at = titles.indexOfFirst { it.entry.key == current.title.entry.key }
                        val next = titles.getOrNull(at + 1) ?: titles.getOrNull(at - 1)
                        binsViewModel.removeTitle(current.title)
                        overlay = null
                        pendingFocus = next?.let { "title:${it.entry.key}" }
                    }
                ),
                initialIndex = 0,
                openedByHold = true,
                onDismiss = {
                    overlay = null
                    pendingFocus = "title:${current.title.entry.key}"
                }
            )
            null -> Unit
        }

        BinsToast(message, Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp))
    }
}

@Composable
private fun TitlesGrid(
    state: BinsUiState,
    gridState: LazyGridState,
    focus: FocusKeys,
    onFocused: (String) -> Unit,
    onClick: (BinTitle) -> Unit,
    onLongClick: (BinTitle, Rect) -> Unit,
    onPrefetch: (BinTitle) -> Unit
) {
    val style = PosterCardDefaults.Style.copy(
        width = state.posterWidthDp.dp,
        height = (state.posterWidthDp * 3 / 2).dp,
        cornerRadius = state.posterCornerDp.dp
    )
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = style.width),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = ScreenPadding,
        horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.lg)
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Text(stringResource(R.string.library_title), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                if (state.titles.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        pluralStringResource(R.plurals.bins_title_count, state.titles.size, state.titles.size),
                        color = BinsMuted, fontSize = 13.sp
                    )
                }
            }
        }
        if (state.loaded && state.titles.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                EmptyScreenState(
                    title = stringResource(R.string.watch_later_empty_title),
                    subtitle = stringResource(R.string.watch_later_empty_subtitle),
                    icon = Icons.Outlined.Inventory2,
                    height = 260.dp
                )
            }
        }
        items(state.titles, key = { it.entry.key }) { title ->
            val key = title.entry.key
            var bounds by remember { mutableStateOf(Rect.Zero) }
            val series = title.entry.type.equals("series", true) || title.entry.type.equals("tv", true)
            Box(
                Modifier
                    .onGloballyPositioned { bounds = it.boundsInRoot() }
                    .animateItem(
                        fadeInSpec = null, fadeOutSpec = null,
                        placementSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)
                    )
            ) {
                GridContentCard(
                    item = title.preview,
                    posterCardStyle = style,
                    isWatched = if (series) title.entry.id in state.watchedSeriesIds else title.entry.id in state.watchedMovieIds,
                    focusRequester = focus["title:$key"],
                    showLabel = true,
                    onFocused = {
                        onFocused("title:$key")
                        onPrefetch(title)
                    },
                    onClick = { onClick(title) },
                    onLongPress = { onLongClick(title, bounds) }
                )
            }
        }
    }
}
