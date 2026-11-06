package com.nuvio.tv.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nuvio.tv.ui.screens.detail.DetailGlyph
import com.nuvio.tv.ui.screens.detail.DetailGlyphIcon
import com.nuvio.tv.ui.theme.MontserratFamily
import com.nuvio.tv.ui.util.ReadableTextStyle
import com.nuvio.tv.ui.util.contentTextDirection
import com.nuvio.tv.ui.util.localizeEpisodeTitle

/**
 * Shown once playback has been paused for 3 seconds: the title's logo, one line of facts (score, genre, age rating),
 * and for an episode its number, its name and its own synopsis, over a dimmed picture. The controls stay on top.
 * Measured off the reference (1920x1080 at 2x): everything starts 70 dp in, the logo's box 120 dp down.
 */
@Composable
fun PauseOverlay(
    visible: Boolean,
    onClose: () -> Unit,
    title: String,
    logo: String?,
    episodeTitle: String?,
    season: Int?,
    episode: Int?,
    type: String?,
    description: String?,
    score: Float?,
    genre: String?,
    ageRating: String?,
    modifier: Modifier = Modifier
) {
    PlayerOverlayScaffold(
        visible = visible,
        onDismiss = onClose,
        modifier = modifier,
        captureKeys = false,
        dismissOnBackgroundClick = true,
        overlayTint = Color.Black.copy(alpha = 0.16f),
        drawShade = false
    ) {
        val context = LocalContext.current
        val isEpisode = type in listOf("series", "tv") && season != null && episode != null
        // Darkest on the left, behind the text, and clear towards the right; the lower part a little darker too.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.72f),
                        0.5f to Color.Black.copy(alpha = 0.32f),
                        1f to Color.Transparent
                    )
                )
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.6f)
                    )
                )
        )
        Column(modifier = Modifier.align(Alignment.TopStart).padding(start = 70.dp, top = 120.dp)) {
            PauseTitle(title = title, logo = logo)
            Spacer(Modifier.height(6.25.dp))
            val percent = score?.let { Math.round(it * 10f) }
            if (percent != null || genre != null || ageRating != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (percent != null) {
                        val like = com.nuvio.tv.ui.newlook.home.ratingPercentColor(percent)
                        DetailGlyphIcon(DetailGlyph.THUMB_UP, true, like, 16.5.dp)
                        PauseText("$percent%", color = like, size = 10.4.sp)
                    }
                    genre?.let { PauseText(it, color = Color.White.copy(alpha = 0.92f), size = 10.4.sp) }
                    ageRating?.let {
                        PauseText(
                            it, color = Color.White, size = 10.2.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.White.copy(alpha = 0.49f))
                                .padding(horizontal = 4.dp, vertical = 1.5.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(5.7.dp))
            if (isEpisode) {
                PauseText("S${season}:E${episode}", color = Color.White.copy(alpha = 0.72f), size = 13.3.sp, weight = FontWeight.Bold)
                episodeTitle?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(2.2.dp))
                    PauseText(it.localizeEpisodeTitle(context), color = Color.White.copy(alpha = 0.92f), size = 11.sp)
                    Spacer(Modifier.height(5.8.dp))
                }
            }
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = ReadableTextStyle.copy(textDirection = description.contentTextDirection()),
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 11.9.sp,
                    lineHeight = 16.sp,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 480.dp)
                )
            }
        }
    }
}

@Composable
private fun PauseText(
    text: String,
    color: Color,
    size: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Medium
) {
    Text(
        text = text, color = color, fontSize = size, fontWeight = weight,
        fontFamily = MontserratFamily, letterSpacing = 0.sp, style = ReadableTextStyle, maxLines = 1, modifier = modifier
    )
}

/** The logo, as large as 450 x 100 dp allows, anchored at its bottom left; the title in text when there is none. */
@Composable
private fun PauseTitle(title: String, logo: String?) {
    var logoFailed by remember(logo) { mutableStateOf(false) }
    if (!logo.isNullOrBlank() && !logoFailed) {
        val context = LocalContext.current
        AsyncImage(
            // TMDB's w500 logo is stretched to the 900 px this box is at 2x; the original keeps its edges sharp.
            model = remember(logo) {
                val sharp = logo.replace("/t/p/w500/", "/t/p/original/")
                ImageRequest.Builder(context).data(sharp).memoryCacheKey(sharp).crossfade(true).build()
            },
            contentDescription = title,
            contentScale = ContentScale.Fit,
            alignment = Alignment.BottomStart,
            onError = { logoFailed = true },
            modifier = Modifier.height(100.dp).widthIn(max = 450.dp)
        )
    } else {
        Box(Modifier.height(100.dp), contentAlignment = Alignment.BottomStart) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = MontserratFamily,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 450.dp).heightIn(min = 40.dp)
            )
        }
    }
}
