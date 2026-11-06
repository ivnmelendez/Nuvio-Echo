package com.nuvio.tv.ui.newlook.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.tv.R
import com.nuvio.tv.ui.screens.home.ModernCarouselItem
import com.nuvio.tv.ui.screens.home.ModernPayload
import com.nuvio.tv.ui.theme.MontserratFamily

/**
 * What View More opens: every title of the row in a grid of five posters across, the way the reference app shows it.
 * Measured off its screen (1920x1080, dp = px / 2): posters 168 dp wide at 2:3, 8.5 dp apart, the first column 68 dp
 * from the left (the back arrow stands in the space before it), the grid starting 56 dp from the top under the title.
 * The focused poster wears a white frame and its picture grows about 2% inside it. Moving to a row scrolls the grid
 * so that row's top stands 145 dp under the top of the grid (the first row stays at the top): the row before it
 * shows half under the title and the next one peeks in at the bottom, 200 ms. Measured off its screen recording.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ViewMoreGridScreen(
    rowKey: String,
    onNavigateToDetail: (String, String, String) -> Unit,
    onBackPress: () -> Unit
) {
    BackHandler(onBack = onBackPress)
    val entry = remember(rowKey) { ViewMoreStore.get(rowKey) }
    val items = remember(entry) {
        entry?.items.orEmpty().filter { it.payload is ModernPayload.Catalog && !it.isViewMore }
    }
    val columns = 5
    val gap = 8.5.dp
    val gridState = rememberLazyGridState()
    var focusedKey by rememberSaveable(rowKey) { mutableStateOf<String?>(null) }
    var targetRow by remember { mutableIntStateOf(0) }
    val startFocus = remember { FocusRequester() }
    // One requester per poster: Up and Down are moved by hand (see below), since the row to move to may not be composed yet.
    val requesters = remember { HashMap<Int, FocusRequester>() }
    var focusedIndex by remember { mutableIntStateOf(0) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val density = LocalDensity.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val cardWidth = (maxWidth - 68.5.dp - 16.dp - gap * (columns - 1)) / columns
        val rowPitchPx = with(density) { (cardWidth * 1.5f + gap).toPx() }
        // Where a focused row's top stands, counted from the top of the grid; the first row sits at its natural place.
        val anchorPx = with(density) { 145.dp.toPx() }
        fun scrollFor(row: Int) = if (row <= 0) 0f else (row * rowPitchPx - anchorPx).coerceAtLeast(0f)
        val restoreIndex = remember(items) { items.indexOfFirst { it.key == focusedKey }.coerceAtLeast(0) }

        LaunchedEffect(items) {
            if (items.isEmpty()) return@LaunchedEffect
            val row = restoreIndex / columns
            if (row > 0) {
                val scroll = scrollFor(row)
                val whole = (scroll / rowPitchPx).toInt()
                gridState.scrollToItem(whole * columns, (scroll - whole * rowPitchPx).toInt())
                targetRow = row
            }
            repeat(3) { androidx.compose.runtime.withFrameNanos { } }
            runCatching { startFocus.requestFocus() }
        }

        // The focused row goes to its place under the title; it starts from wherever the grid is, so keys in a row compose.
        LaunchedEffect(targetRow) {
            val now = gridState.firstVisibleItemIndex / columns * rowPitchPx + gridState.firstVisibleItemScrollOffset
            val delta = scrollFor(targetRow) - now
            if (kotlin.math.abs(delta) > 1f) gridState.animateScrollBy(delta, tween(200, easing = FastOutSlowInEasing))
        }

        Text(
            text = entry?.title.orEmpty(),
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = MontserratFamily,
            maxLines = 1,
            modifier = Modifier.offset(x = 24.5.dp, y = 13.dp)
        )
        // The way back: the reference app's own arrow, at its measured place (24 dp wide, centred at 298 dp).
        Image(
            painter = coil3.compose.rememberAsyncImagePainter(
                model = coil3.request.ImageRequest.Builder(LocalContext.current)
                    .data(R.raw.view_more_back)
                    .size(with(density) { 24.dp.roundToPx() }, with(density) { 41.5.dp.roundToPx() })
                    .build()
            ),
            contentDescription = null,
            modifier = Modifier.offset(x = 14.dp, y = 277.dp).size(24.dp, 41.5.dp)
        )

        CompositionLocalProvider(LocalBringIntoViewSpec provides NoScroll) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(columns),
                userScrollEnabled = false,
                contentPadding = PaddingValues(start = 68.5.dp, end = 16.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(gap),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 56.dp)
                    .clipToBounds()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val down = event.key == Key.DirectionDown
                        if (!down && event.key != Key.DirectionUp) return@onPreviewKeyEvent false
                        val target = if (down) {
                            // A last row with fewer posters: Down lands on its last one.
                            val next = focusedIndex + columns
                            if (next < items.size) next else if (focusedIndex / columns < (items.size - 1) / columns) items.size - 1 else -1
                        } else focusedIndex - columns
                        if (target in items.indices) {
                            targetRow = target / columns
                            scope.launch {
                                // The scroll brings the row in; focus it as soon as it is composed.
                                repeat(30) {
                                    if (runCatching { requesters.getOrPut(target) { FocusRequester() }.requestFocus() }.getOrDefault(false)) return@launch
                                    androidx.compose.runtime.withFrameNanos { }
                                }
                            }
                        }
                        true
                    }
            ) {
                itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
                    GridPoster(
                        item = item,
                        modifier = Modifier
                            .focusRequester(requesters.getOrPut(index) { FocusRequester() })
                            .then(if (index == restoreIndex) Modifier.focusRequester(startFocus) else Modifier),
                        onFocused = {
                            focusedKey = item.key
                            focusedIndex = index
                            targetRow = index / columns
                        },
                        onClick = {
                            (item.payload as? ModernPayload.Catalog)?.let {
                                onNavigateToDetail(it.itemId, it.itemType, it.addonBaseUrl)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GridPoster(
    item: ModernCarouselItem,
    modifier: Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val zoom by animateFloatAsState(if (focused) 1.02f else 1f, tween(120, easing = FastOutSlowInEasing), label = "gridZoom")
    val shape = RoundedCornerShape(6.dp)
    val url = item.heroPreview.poster ?: item.metaPreview?.poster ?: item.imageUrl
    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .clip(shape)
            .background(Color(0xFF141414))
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    ) {
        Artwork(url, Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom }, fallbackTitle = item.title)
        if (focused) Box(Modifier.fillMaxSize().border(2.dp, Color.White, shape))
    }
}

private val NoScroll = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}
