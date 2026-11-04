@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.newlook.home

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import com.nuvio.tv.domain.model.CardDepthSurface
import com.nuvio.tv.ui.components.nuvioCardDepth
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.nuvio.tv.core.image.customPosterFallback
import coil3.request.crossfade
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.derivedStateOf
import com.nuvio.tv.R
import com.nuvio.tv.ui.components.TrailerPlayer
import com.nuvio.tv.ui.newlook.focusRingColor

internal val CardBackground = Color(0xFF222222)
private val OpenEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
private val LikeGreen = Color(0xFF2ECC71)
private val OpenCardFoot = Brush.verticalGradient(
    0f to Color.Transparent, 0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.88f)
)
internal val NoBackdropShift: () -> Float = { 0f }

/** The one fixed dark veil shared by closed posters and the row below the focused one. */
internal const val ClosedVeilAlpha = 0.28f
private val LikeOrange = Color(0xFFF5A623)
private val LikeRed = Color(0xFFE74C3C)

/** The colour of a rating shown as a percentage: green from 60, orange from 40, red below that. */
internal fun ratingPercentColor(percent: Int): Color = when {
    percent >= 60 -> LikeGreen
    percent >= 40 -> LikeOrange
    else -> LikeRed
}

/**
 * A focusable card with the new look's 1 px hairline (in the Appearance colour) instead of the thick white ring. A null [width] leaves
 * the width to [modifier] (the poster sets it from its open animation in the layout phase).
 */
@Composable
internal fun NewLookCard(
    width: Dp?,
    height: Dp,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    radius: Dp = LocalNewLookCardRadius.current,
    /** Which of Layout's card depth switches covers this card; null draws none. */
    depth: CardDepthSurface? = null,
    /** Whether quiet focus lifts the card (grows it a little) when it takes focus. */
    lift: Boolean = true,
    /** The focus ring's thickness; the highlighted (wide) card draws a thicker, pure white one. */
    ringWidth: Dp = 1.dp,
    ringColor: Color? = null,
    /** A frame drawn while the card is not focused (a row's resting card), or null for none. */
    idleRingColor: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(radius)
    val ring = ringColor ?: focusRingColor
    val quiet = com.nuvio.tv.ui.newlook.LocalQuietFocus.current
    val light = com.nuvio.tv.ui.newlook.LocalLightMode.current
    // Whether the card's artwork is in (see [Artwork]; every card with depth draws its picture through it).
    val artwork = remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .then(if (width != null) Modifier.size(width, height) else Modifier.height(height))
            .onFocusChanged { onFocusChanged(it.isFocused) },
        shape = CardDefaults.shape(shape),
        // Clear, not grey: while a row slides, a card whose artwork is still loading showed as a dark box for a frame
        // or two before its picture came in. Now the artwork just fades in where the card is.
        colors = CardDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent
        ),
        // Focus is the white hairline alone: no growth and no shadow, which cost frames on weak boxes.
        scale = CardDefaults.scale(focusedScale = if (quiet && lift && !light) QUIET_FOCUS_LIFT else 1f, pressedScale = 1f),
        border = CardDefaults.border(
            border = if (idleRingColor != null) Border(BorderStroke(ringWidth, idleRingColor), shape = shape) else Border.None,
            focusedBorder = Border(BorderStroke(ringWidth, ring), shape = shape),
            pressedBorder = Border(BorderStroke(ringWidth, ring), shape = shape)
        ),
        glow = CardDefaults.glow()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            androidx.compose.runtime.CompositionLocalProvider(LocalArtworkShown provides artwork) { content() }
            // The depth's lit edge and sheen wait for the artwork: over a card still loading (a row sliding in new
            // cards) they drew a faint empty box for a frame or two.
            if (!light && depth != null && artwork.value) CardDepthOverlay(shape, depth)
        }
    }
}

/** A focused card no longer grows: the scale animation read as a bounce and cost frames on weak boxes. The ring alone marks focus. */
private const val QUIET_FOCUS_LIFT = 1f

/** A card's "artwork is in" flag, which [Artwork] sets once its picture (or its title, with none) is up. */
internal val LocalArtworkShown = androidx.compose.runtime.compositionLocalOf<androidx.compose.runtime.MutableState<Boolean>?> { null }

/** Layout's corner radius for Home's cards, set once for the page by Home. */
internal val LocalNewLookCardRadius = androidx.compose.runtime.staticCompositionLocalOf { 12.dp }

/** Layout's card depth (edge light and top sheen) over a card's content, when it's on for [surface]. */
@Composable
internal fun BoxScope.CardDepthOverlay(shape: androidx.compose.ui.graphics.Shape, surface: CardDepthSurface) {
    val style = com.nuvio.tv.ui.components.LocalCardDepthStyle.current
    if (style.isEnabledFor(surface)) {
        Box(Modifier.matchParentSize().nuvioCardDepth(shape = shape, surface = surface, style = style))
    }
}

@Composable
internal fun Artwork(
    url: String?,
    modifier: Modifier = Modifier,
    fallbackTitle: String? = null,
    /** The art a custom poster replaced, shown if the custom one fails to load. */
    fallbackUrl: String? = null
) {
    val shown = LocalArtworkShown.current
    if (url.isNullOrBlank()) {
        androidx.compose.runtime.SideEffect { shown?.value = true }
        if (fallbackTitle != null) {
            Box(modifier = modifier.background(CardBackground).padding(10.dp), contentAlignment = Alignment.Center) {
                Text(text = fallbackTitle, color = Color(0xFFB3B3B3), fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 3)
            }
        }
        return
    }
    val context = LocalContext.current
    val light = com.nuvio.tv.ui.newlook.LocalLightMode.current
    // Built once per image, not on every recomposition of the card.
    val request = remember(url, fallbackUrl, light) {
        // Light mode asks TMDB for the size the card is drawn at: a poster card is about 260 px wide and an opened one
        // about 700, so w500 and w1280 were fetched and decoded at nearly twice what the screen shows.
        val sized = if (light) lightSizedUrl(url) else url
        ImageRequest.Builder(context).data(sized).customPosterFallback(sized, fallbackUrl).crossfade(!light).build()
    }
    // Tells the card when its picture is up (the card's depth waits for it). A card showing two pictures (a poster
    // under its backdrop) counts as in once the first is.
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onSuccess = { shown?.value = true },
        onError = { shown?.value = true },
        modifier = modifier
    )
}

/** The TMDB size light mode asks for (see [Artwork]); the prefetch uses it too, so what it warms is what a card reads. */
internal fun lightSizedUrl(url: String): String =
    url.replace("/t/p/w500/", "/t/p/w342/").replace("/t/p/w1280/", "/t/p/w780/").replace("/t/p/original/", "/t/p/w780/")

/**
 * Continue Watching's card is about 630 px wide, and its episode picture often arrives as a TMDB still at w300 (or w185):
 * stretched to the card it looked soft. Asks TMDB for w780 instead (a TMDB url only; any other is left as it is).
 */
internal fun sharperContinueUrl(url: String): String =
    url.replace("/t/p/w300/", "/t/p/w780/").replace("/t/p/w185/", "/t/p/w780/").replace("/t/p/w92/", "/t/p/w780/")
        .replace("/t/p/w500/", "/t/p/w780/")

/** Logo bottom-left, or the title as bold text when there's no logo. */
@Composable
internal fun BoxScope.CardLogo(
    logo: String?,
    title: String,
    maxWidthFraction: Float,
    maxHeightFraction: Float,
    cardWidth: Dp,
    cardHeight: Dp,
    inset: Dp,
    textSize: Float,
    /**
     * The opened poster card's way (measured off the reference): the logo is fitted into a fixed box, 0.475 of the
     * card's width by 0.214 of its height, at the bottom left, and sits centred in it. Wide logos fill the box's width,
     * tall ones its height and come out narrower and more to the middle. The picture is decoded at the box's size.
     */
    fitted: Boolean = false
) {
    var failed by remember(logo) { mutableStateOf(false) }
    if (!logo.isNullOrBlank() && !failed) {
        val context = LocalContext.current
        val light = com.nuvio.tv.ui.newlook.LocalLightMode.current
        val request = remember(logo, light) { ImageRequest.Builder(context).data(logo).crossfade(!light).build() }
        if (fitted) {
            AsyncImage(
                model = request,
                contentDescription = title,
                contentScale = ContentScale.Fit,
                alignment = Alignment.BottomCenter,
                onError = { failed = true },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 11.5.dp, bottom = 7.5.dp)
                    .size(cardWidth * 0.475f, cardHeight * 0.214f)
            )
            return
        }
        AsyncImage(
            model = request,
            contentDescription = title,
            contentScale = ContentScale.Fit,
            alignment = Alignment.BottomStart,
            onError = { failed = true },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = inset, bottom = inset)
                .widthIn(max = cardWidth * maxWidthFraction)
                .heightIn(max = cardHeight * maxHeightFraction)
        )
    } else {
        Text(
            text = title,
            color = Color.White,
            fontSize = textSize.sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = (textSize * 1.1f).sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 10f)),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = inset, bottom = inset - 2.dp)
                .widthIn(max = cardWidth * 0.7f)
        )
    }
}

/**
 * The shade under a card's title: clear for the top 45%, then down to black at 65% at the bottom. Drawn only over
 * the part that isn't clear.
 */
private fun Modifier.bottomShade(): Modifier = drawWithCache {
    val top = size.height * 0.45f
    val brush = Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.65f), startY = top, endY = size.height)
    onDrawBehind { drawRect(brush, topLeft = androidx.compose.ui.geometry.Offset(0f, top), size = androidx.compose.ui.geometry.Size(size.width, size.height - top)) }
}

/**
 * Continue Watching: always wide, with the title (and the episode, for a show) bottom-left and the progress as a
 * bar along the card's bottom edge, rounded off by the card's corners.
 */
@Composable
internal fun ContinueWatchingCard(
    image: String?,
    title: String,
    episode: String?,
    progress: Float?,
    dims: NewLookDimens,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val hasProgress = progress != null && progress > 0f
    NewLookCard(dims.cwWidth, dims.cwHeight, onClick, onLongClick, onFocusChanged, modifier, depth = CardDepthSurface.CONTINUE_WATCHING, lift = false) {
        Artwork(image?.let(::sharperContinueUrl), Modifier.fillMaxSize(), fallbackTitle = title)
        Box(modifier = Modifier.fillMaxSize().bottomShade())
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, end = 14.dp, bottom = if (hasProgress) 14.dp else 12.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.8f), blurRadius = 8f))
            )
            if (!episode.isNullOrBlank()) {
                Text(
                    text = episode,
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.8f), blurRadius = 8f))
                )
            }
        }
        if (hasProgress) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.28f))
            ) {
                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(progress!!.coerceIn(0f, 1f)).background(Color.White))
            }
        }
    }
}

/**
 * A poster that opens out while focused: it widens to the open width over ~280 ms and crossfades to the
 * title's backdrop with its logo, then closes back to a poster when focus leaves.
 */
@Composable
internal fun PosterCard(
    poster: String?,
    /** The art a custom [poster] replaced, shown if the custom one fails to load. */
    posterFallback: String? = null,
    backdrop: String?,
    logo: String?,
    title: String,
    watched: Boolean,
    open: Boolean,
    /** Opens off this card's own focus, in the same frame, instead of waiting for [open] from the screen. */
    openAtOnce: Boolean = false,
    /** Home is going to another screen: losing focus to it must not close the card on its way out. */
    holdOpen: Boolean = false,
    /** The row's resting card: wide while the row has no focus too, with the thin ring, so every row shows one open. */
    restingOpen: Boolean = false,
    dims: NewLookDimens,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** The title's trailer, played in the opened card; null while it's closed or has none. */
    trailerUrl: String? = null,
    trailerAudioUrl: String? = null,
    muted: Boolean = true,
    /** How far in a Continue Watching title is (0 to 1); a bar along the bottom, or nothing when null. */
    progress: Float? = null,
    /** "S3:E3" for a series title in Continue Watching: a small label at the bottom right, open or closed. */
    badge: String? = null,
    /** Px the opened card's backdrop sits off to the side while it settles after a step (read while drawing). */
    backdropShift: () -> Float = NoBackdropShift,
    /** The backdrop of the card the focus just left, drawn [ghostShift] px off: it shows in the gap the push opens. */
    ghostBackdrop: String? = null,
    ghostShift: () -> Float = NoBackdropShift,
    /** The View More card at the end of a cut row: arrows on a dark card, wider (black) when open. */
    viewMore: Boolean = false
) {
    // A regular row stays on its card when the trailer ends: the backdrop comes back.
    var ended by remember(trailerUrl) { mutableStateOf(false) }
    val playing = open && trailerUrl != null && !ended
    // With no
    // delay it follows the card's own focus: the screen's state reaches the card a frame or two later, and a poster
    // pinned to the row's start showed vertical for that long before widening. No animation sits in between either.
    var focusedHere by remember { mutableStateOf(false) }
    val latestHoldOpen by androidx.compose.runtime.rememberUpdatedState(holdOpen)
    val activeOpen = if (openAtOnce) focusedHere else open
    val isOpen = activeOpen || restingOpen
    // As stock Nuvio does: the poster goes the moment the card starts opening, so it's never seen cropped and blown
    // up to the wide shape; the card is black until the backdrop has loaded and faded in.
    val showOpen = isOpen
    NewLookCard(
        null, dims.posterHeight, onClick, onLongClick, { if (it || !latestHoldOpen) focusedHere = it; onFocusChanged(it) },
        // Width, content and focus ring all follow the same state in the same composition, so the frame never shows
        // narrow around a card that has already opened.
        modifier.width(if (isOpen) dims.posterOpenWidth else dims.posterWidth),
        depth = CardDepthSurface.POSTERS,
        // No lift: the card opens at once, and a lift animating on top of it read as a bounce (and overhung its slot).
        lift = false,
        // The opened (wide) card wears a white frame; the closed poster keeps the thin one.
        // A row's open card keeps its frame when it has no focus: the same width, in a solid gray (a see-through one vanished over bright artwork).
        ringWidth = if (isOpen) 2.dp else 1.dp,
        ringColor = if (activeOpen) Color.White else null,
        idleRingColor = if (isOpen) Color(0xFF1F1F1F) else null
    ) {
        if (viewMore) ViewMoreArt(isOpen) else Artwork(
            poster ?: backdrop, Modifier.fillMaxSize().graphicsLayer { alpha = if (isOpen) 0f else 1f },
            fallbackTitle = title, fallbackUrl = posterFallback
        )
        // Closed posters wear one fixed dark veil (lighter than the reference's measured 40%, by eye); only the opened card is undimmed.
        if (!isOpen) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = ClosedVeilAlpha)))
        if (showOpen && !viewMore) {
            Box(modifier = Modifier.fillMaxSize().clipToBounds().background(Color.Black)) {
                // Pushed to the side while it settles, the black behind showing at the edge: the logo and foot stay put.
                if (ghostBackdrop != null) Artwork(
                    ghostBackdrop,
                    // Hidden once the push has settled (a full card width away, it falls outside the clip anyway).
                    Modifier.fillMaxSize().graphicsLayer { translationX = ghostShift() }
                )
                Artwork(backdrop, Modifier.fillMaxSize().graphicsLayer { translationX = backdropShift() })
                HeldTrailer(active = playing, key = trailerUrl) { onFirstFrame ->
                    TrailerPlayer(
                        trailerUrl = trailerUrl,
                        trailerAudioUrl = trailerAudioUrl,
                        isPlaying = true,
                        onEnded = { ended = true },
                        onFirstFrameRendered = onFirstFrame,
                        muted = muted,
                        cropToFill = true,
                        // Burned-in black bars are cropped, so the picture fills the hero or card it plays in.
                        autoCropLetterbox = false,
                        focusable = false,
                        overscanZoom = com.nuvio.tv.ui.screens.home.MODERN_TRAILER_OVERSCAN_ZOOM,
                        modifier = Modifier.fillMaxSize(),
                        surfaceView = com.nuvio.tv.ui.components.LocalTrailerVideoLayer.current
                    )
                }
                // Fixed, never animated or recomputed: a darker foot behind the logo and the corner labels.
                Box(modifier = Modifier.fillMaxSize().background(OpenCardFoot))
                CardLogo(logo, title, 0.4f, 0.28f, dims.posterOpenWidth, dims.posterHeight, 14.dp, 20f, fitted = true)
            }
        }
        if (watched) WatchedMark(Modifier.align(Alignment.TopEnd).padding(8.dp))
        if (badge != null) {
            Text(
                badge,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        if (progress != null && progress > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.28f))
            ) {
                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(Color.White))
            }
        }
    }
}

@Composable
internal fun WatchedMark(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(22.dp).clip(CircleShape).background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
    }
}

/** A highlighted row's card: always wide, the backdrop with the logo; the focused one can play its trailer. */
@Composable
internal fun HighlightCard(
    backdrop: String?,
    logo: String?,
    title: String,
    trailerUrl: String?,
    trailerAudioUrl: String?,
    playing: Boolean,
    muted: Boolean,
    dims: NewLookDimens,
    onTrailerEnded: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    chipLabel: String? = null,
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    NewLookCard(
        dims.highlightWidth, dims.highlightHeight, onClick, onLongClick, onFocusChanged, modifier,
        depth = CardDepthSurface.FEATURED, lift = false, ringWidth = 2.5.dp, ringColor = Color.White
    ) {
        Artwork(backdrop, Modifier.fillMaxSize(), fallbackTitle = title)
        overlay()
        HeldTrailer(active = playing && trailerUrl != null, key = trailerUrl) { onFirstFrame ->
            TrailerPlayer(
                trailerUrl = trailerUrl,
                trailerAudioUrl = trailerAudioUrl,
                isPlaying = true,
                onEnded = onTrailerEnded,
                onFirstFrameRendered = onFirstFrame,
                muted = muted,
                cropToFill = true,
                // Burned-in black bars are cropped, so the picture fills the hero or card it plays in.
                autoCropLetterbox = false,
                focusable = false,
                overscanZoom = com.nuvio.tv.ui.screens.home.MODERN_TRAILER_OVERSCAN_ZOOM,
                modifier = Modifier.fillMaxSize(),
                surfaceView = com.nuvio.tv.ui.components.LocalTrailerVideoLayer.current
            )
        }
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.62f))
        ))
        CardLogo(logo, title, 0.38f, 0.26f, dims.highlightWidth, dims.highlightHeight, 16.dp, 22f)
        if (chipLabel != null) {
            SoundChip(
                text = chipLabel,
                icon = Icons.Filled.PlayArrow,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
            )
        }
    }
}

@Composable
internal fun SoundChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, height: Dp = 22.dp) {
    Row(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(Color(0x99121212))
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFFDDDDDD), modifier = Modifier.size(12.dp))
        Text(text = text, color = Color(0xFFDDDDDD), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/**
 * A ranked row's big number, drawn behind the card. Outline under an opaque fill (so the font's overlapping inner
 * contours never show as stray lines), then a soft gray gradient on top, in the page's grays.
 */
@Composable
internal fun RankNumber(rank: Int, dims: NewLookDimens, focused: Boolean, modifier: Modifier = Modifier) {
    val fontSize = dims.rankFontSize.sp
    val twoDigits = rank >= 10
    val outline by androidx.compose.animation.animateColorAsState(
        if (focused) Color(240, 240, 240, 153) else Color(215, 215, 215, 77),
        androidx.compose.animation.core.snap(),
        label = "rankOutline"
    )
    val base = TextStyle(
        fontSize = fontSize,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = if (twoDigits) (-0.09).em else (-0.06).em,
        lineHeight = fontSize,
        textAlign = TextAlign.End
    )
    // The number is taller than the card it stands behind (1.18 × its height), so it measures unbounded instead of
    // being squeezed into the card's height, and drops by the font's descent so the digits stand on the card's bottom
    // edge (the line box ends that far below the baseline). Squeezed, with a guessed drop, the digits were cut flat
    // and stood off the edge (seen in the JVM preview).
    var descentPx by remember { mutableIntStateOf(0) }
    Box(
        modifier = modifier
            .offset { IntOffset(0, descentPx) }
            .wrapContentSize(Alignment.BottomEnd, unbounded = true)
            .graphicsLayer {
                if (twoDigits) {
                    scaleX = 0.72f
                    transformOrigin = TransformOrigin(1f, 1f)
                }
            }
    ) {
        Text(
            text = rank.toString(),
            style = base.copy(color = outline, drawStyle = Stroke(width = 6.4f)),
            maxLines = 1,
            softWrap = false,
            // Visible: the tight letter spacing puts the digit's edge a little outside its text box, and the default
            // clip cut it flat on the left.
            overflow = TextOverflow.Visible,
            onTextLayout = { descentPx = (it.size.height - it.lastBaseline).toInt() }
        )
        Text(text = rank.toString(), style = base.copy(color = Color(0xFF171717)), maxLines = 1, softWrap = false, overflow = TextOverflow.Visible)
        Text(
            text = rank.toString(),
            style = base.copy(
                brush = Brush.verticalGradient(
                    0f to Color(92, 92, 92, 191),
                    0.55f to Color(44, 44, 44, 115),
                    1f to Color(14, 14, 14, 0)
                )
            ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible
        )
    }
}

/** Still (no animation, so nothing redraws while it waits), lighter at the top like a card catching light. */
private val PlaceholderShade = Brush.verticalGradient(listOf(Color(0xFF222222), Color(0xFF161616)))

/**
 * A loading row's card: a softly pulsing skeleton that can take focus, so moving down onto a row that hasn't
 * loaded (or never will) lands on it and the next Down goes on past it. OK does nothing.
 */
@Composable
internal fun SkeletonCard(
    width: Dp,
    height: Dp,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // Still, not pulsing: an endless animation redraws every frame while the row loads.
    val alpha = androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0.8f) }
    NewLookCard(width, height, onClick = {}, onLongClick = null, onFocusChanged = onFocusChanged, modifier = modifier) {
        // Read while drawing, so the pulse redraws the card without recomposing it.
        Box(modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value }.background(PlaceholderShade))
    }
}

/** "👍 63%  2026  Comedy  17m", under a row's focused card: the rating as a percentage, the year, one genre, the length. */
@Composable
internal fun DetailsLine(
    type: String?,
    genres: List<String>,
    year: String?,
    runtime: String?,
    rating: String?,
    ratingIsImdb: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFCFCFCF),
    dotColor: Color = Color(0xFF777777)
) {
    val percent = rating?.trim()?.takeWhile { it.isDigit() || it == '.' }?.toFloatOrNull()
        ?.let { if (it <= 10f) it * 10f else it }
        ?.takeIf { it > 0f }
        ?.let { Math.round(it) }
    val parts = listOfNotNull(
        year?.takeIf { it.isNotBlank() },
        genres.firstOrNull()?.takeIf { it.isNotBlank() },
        runtime?.takeIf { it.isNotBlank() }
    )
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (percent != null) {
            val ratingColor = ratingPercentColor(percent)
            com.nuvio.tv.ui.screens.detail.DetailGlyphIcon(
                com.nuvio.tv.ui.screens.detail.DetailGlyph.THUMB_UP, true, ratingColor, 17.dp
            )
            Text(
                text = " $percent%",
                color = ratingColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.padding(end = 10.dp)
            )
        }
        parts.forEachIndexed { index, part ->
            Text(
                text = part,
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.padding(end = if (index < parts.lastIndex) 10.dp else 0.dp)
            )
        }
    }
}

internal fun formatRuntime(minutes: Int?): String? {
    if (minutes == null || minutes <= 0) return null
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

/**
 * A collection folder. Highlighted, it takes the highlighted card size with the folder's logo (or title)
 * bottom-left; either way its animation (focus GIF or hero video, muted and looping) plays only while focused.
 */
@Composable
internal fun CollectionTile(
    item: com.nuvio.tv.ui.screens.home.ModernCarouselItem,
    width: Dp,
    height: Dp,
    focused: Boolean,
    onClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false
) {
    val folder = item.payload as? com.nuvio.tv.ui.screens.home.ModernPayload.CollectionFolder
    val gif = folder?.takeIf { it.focusGifEnabled }?.focusGifUrl?.takeIf { it.isNotBlank() }
    val video = folder?.heroVideoUrl?.takeIf { it.isNotBlank() }
    val cover = if (highlighted) item.heroPreview.backdrop ?: item.imageUrl else item.imageUrl ?: item.heroPreview.backdrop
    NewLookCard(
        width, height, onClick, null, onFocusChanged, modifier,
        depth = if (highlighted) CardDepthSurface.FEATURED else CardDepthSurface.POSTERS
    ) {
        Artwork(
            cover, Modifier.fillMaxSize(), fallbackTitle = item.title.ifBlank { null },
            fallbackUrl = item.metaPreview?.rawPosterUrl.takeIf { !highlighted }
        )
        if (focused && gif != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(gif).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (focused && video != null) {
            var loop by remember { mutableStateOf(0) }
            androidx.compose.runtime.key(loop) {
                TrailerPlayer(
                    trailerUrl = video,
                    isPlaying = true,
                    onEnded = { loop++ },
                    muted = true,
                    cropToFill = true,
                    // Burned-in black bars are cropped, so the picture fills the hero or card it plays in.
                    autoCropLetterbox = false,
                    focusable = false,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        if (item.title.isNotBlank() || !folder?.titleLogoUrl.isNullOrBlank()) {
            Box(modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Transparent, 0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))
            ))
            if (highlighted) {
                CardLogo(folder?.titleLogoUrl, item.title, 0.5f, 0.34f, width, height, 16.dp, 22f)
            } else if (item.title.isNotBlank()) {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 8f)),
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                )
            }
        }
        if (highlighted && (gif != null || video != null)) {
            SoundChip(
                text = stringResource(R.string.home_animated_chip),
                icon = Icons.Filled.PlayArrow,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).graphicsLayer { alpha = if (focused) 1f else 0.85f }
            )
        }
    }
}

/**
 * Starts a card's trailer without a flash: the artwork fades to black, the player starts behind that black, and the
 * black lifts once the trailer's first frame is on screen. (Covering the player with a fresh copy of the backdrop
 * flashed white or black for the frame that copy took to load, then showed the backdrop again before the trailer.)
 *
 * The player only runs while the card is still. On the video layer the picture is a separate surface the system
 * places on its own, and one started while its card was sliding into place (a row moving on to its next card, the
 * page scrolling to the row) was left out of line with the card: a smaller, offset copy inside it and a black block
 * beside it. If the card moves while its trailer plays, the black comes back and the trailer starts again once the
 * card has stopped.
 */
@Composable
internal fun BoxScope.HeldTrailer(active: Boolean, key: Any?, player: @Composable (onFirstFrame: () -> Unit) -> Unit) {
    // One value for the black, keyed to the trailer: a new trailer (or the same one again) always starts from the
    // artwork, never from a black still lifting from the last one.
    val dark = remember(key) { androidx.compose.animation.core.Animatable(0f) }
    var started by remember(key) { mutableStateOf(false) }
    var firstFrame by remember(key) { mutableStateOf(false) }
    // Where the card is and how big (a poster card widens as it opens).
    var position by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var still by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(position) {
        still = false
        kotlinx.coroutines.delay(CARD_STILL_MS)
        still = true
    }
    androidx.compose.runtime.LaunchedEffect(active, key) {
        if (active) {
            dark.animateTo(1f, tween(260))
            started = true
            // A trailer that never starts (a dead link) mustn't leave the card black: the artwork comes back.
            kotlinx.coroutines.delay(TRAILER_HOLD_CAP_MS)
            if (!firstFrame) dark.animateTo(0f, tween(320))
        } else {
            started = false
            firstFrame = false
            dark.snapTo(0f)
        }
    }
    androidx.compose.runtime.LaunchedEffect(firstFrame) {
        if (firstFrame) dark.animateTo(0f, tween(320))
    }
    // Moved while playing: back to black until the trailer is up again in the card's new place.
    androidx.compose.runtime.LaunchedEffect(still) {
        if (!still && firstFrame) {
            firstFrame = false
            dark.snapTo(1f)
        }
    }
    Box(Modifier.matchParentSize().onGloballyPositioned {
        position = androidx.compose.ui.geometry.Rect(it.positionInWindow(), it.size.toSize())
    })
    if (active && started && still) player { firstFrame = true }
    if (active) {
        Box(Modifier.matchParentSize().graphicsLayer { alpha = dark.value }.background(Color.Black))
    }
}

/** How long a card must hold its place before its trailer starts (see [HeldTrailer]). */
private const val CARD_STILL_MS = 160L

/** How long [HeldTrailer] holds black for a trailer that hasn't shown a frame yet. */
private const val TRAILER_HOLD_CAP_MS = 8_000L

/**
 * View More's picture, the reference app's own SVGs at the scale measured off its screen: 0.304 dp per unit closed (a
 * 140 dp wide poster-shaped drawing centred on the card), 0.85 dp per unit open (a 382 dp wide one centred on the wide
 * card). The word is part of the English drawings; for other languages the arrows are drawn alone and the word is text.
 */
@Composable
internal fun BoxScope.ViewMoreArt(open: Boolean) {
    val label = stringResource(R.string.view_more)
    val baked = label == "View More"
    val context = androidx.compose.ui.platform.LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val width = if (open) 382.5.dp else 140.dp
    val height = if (open) 212.5.dp else 140.dp * 660f / 460f
    val raw = when {
        open && baked -> R.raw.view_more_wide
        open -> R.raw.view_more_wide_arrows
        baked -> R.raw.view_more_poster
        else -> R.raw.view_more_poster_arrows
    }
    val painter = coil3.compose.rememberAsyncImagePainter(
        model = coil3.request.ImageRequest.Builder(context)
            .data(raw)
            .size(with(density) { width.roundToPx() }, with(density) { height.roundToPx() })
            .build()
    )
    Box(
        modifier = Modifier.fillMaxSize().then(
            if (open) Modifier.background(Color.Black)
            else Modifier.background(Brush.verticalGradient(listOf(Color(0xFF2A2A2A), Color(0xFF0D0D0D))))
        ),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Image(painter = painter, contentDescription = null, modifier = Modifier.size(width, height))
        if (!baked) Text(
            text = if (open) label.uppercase() else label,
            color = Color.White,
            fontSize = if (open) 22.sp else 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = com.nuvio.tv.ui.theme.MontserratFamily,
            letterSpacing = if (open) 2.sp else 0.sp,
            modifier = Modifier.offset(y = if (open) 43.dp else 54.dp)
        )
    }
}
