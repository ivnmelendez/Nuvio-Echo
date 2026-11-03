package com.nuvio.tv.ui.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.Subtitle
import kotlinx.coroutines.delay

/**
 * The player's side panels, drawn like the reference app's: a dark panel on the right with a small title, plain rows,
 * the focused row a rounded light box, and a check on the one in use. Nothing here animates.
 */
private val PanelRowShape = RoundedCornerShape(10.dp)

@Composable
private fun ReferencePanel(
    width: Dp,
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    BackHandler(onBack = onDismiss)
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(width)
                .fillMaxHeight()
                .background(Color(0xE60B0710))
                .padding(start = 14.dp, end = 14.dp, top = 14.dp)
        ) {
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 15.sp,
                modifier = Modifier.padding(start = 6.dp, bottom = 12.dp)
            )
            content()
        }
    }
}

@Composable
private fun ReferenceRow(
    selected: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 36.dp,
    content: @Composable (focused: Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(PanelRowShape)
            .then(
                if (focused) Modifier
                    .background(Color.White.copy(alpha = 0.2f))
                    .border(1.dp, Color.White.copy(alpha = 0.45f), PanelRowShape)
                else Modifier
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) { content(focused) }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.padding(start = 8.dp).width(16.dp).height(16.dp))
        }
    }
}

/** Puts the focus on the row in use (or the first) when a panel opens. */
@Composable
private fun rememberRowFocus(visible: Boolean, count: Int, initial: Int): List<FocusRequester> {
    val requesters = remember(count) { List(count) { FocusRequester() } }
    LaunchedEffect(visible, count) {
        if (!visible || count == 0) return@LaunchedEffect
        repeat(5) {
            delay(40)
            if (runCatching { requesters[initial.coerceIn(0, count - 1)].requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
    }
    return requesters
}

@Composable
internal fun ReferenceAudioPanel(
    visible: Boolean,
    tracks: List<TrackInfo>,
    selectedIndex: Int,
    onTrackSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    val fallback = stringResource(R.string.player_btn_audio_quality)
    val initial = tracks.indexOfFirst { it.index == selectedIndex || (selectedIndex < 0 && it.isSelected) }.coerceAtLeast(0)
    val requesters = rememberRowFocus(visible, tracks.size, initial)
    ReferencePanel(width = 400.dp, title = stringResource(R.string.player_panel_audio), onDismiss = onDismiss, modifier = modifier) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(tracks, key = { i, t -> "${t.index}:$i" }) { i, track ->
                ReferenceRow(
                    selected = track.index == selectedIndex || (selectedIndex < 0 && track.isSelected),
                    focusRequester = requesters[i],
                    onClick = { onTrackSelected(track.index) }
                ) { focused ->
                    Text(
                        text = shortAudioLabel(track, fallback),
                        color = Color.White.copy(alpha = if (focused) 1f else 0.82f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
internal fun ReferenceSubtitlePanel(
    visible: Boolean,
    internalTracks: List<TrackInfo>,
    selectedInternalIndex: Int,
    addonSubtitles: List<Subtitle>,
    selectedAddonSubtitle: Subtitle?,
    onInternalSelected: (Int) -> Unit,
    onAddonSelected: (Subtitle) -> Unit,
    onDisable: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    val none = stringResource(R.string.player_panel_subtitles_none)
    val offSelected = selectedAddonSubtitle == null && selectedInternalIndex < 0
    val count = 1 + internalTracks.size + addonSubtitles.size
    val initial = when {
        offSelected -> 0
        selectedAddonSubtitle != null -> 1 + internalTracks.size + addonSubtitles.indexOfFirst { it.id == selectedAddonSubtitle.id }.coerceAtLeast(0)
        else -> 1 + internalTracks.indexOfFirst { it.index == selectedInternalIndex }.coerceAtLeast(0)
    }
    val requesters = rememberRowFocus(visible, count, initial)
    ReferencePanel(width = 400.dp, title = stringResource(R.string.player_panel_subtitles), onDismiss = onDismiss, modifier = modifier) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            item(key = "none") {
                ReferenceRow(selected = offSelected, focusRequester = requesters[0], onClick = onDisable) { focused ->
                    Text(none, color = Color.White.copy(alpha = if (focused) 1f else 0.82f), fontSize = 13.sp)
                }
            }
            itemsIndexed(internalTracks, key = { i, t -> "in:${t.index}:$i" }) { i, track ->
                ReferenceRow(
                    selected = selectedAddonSubtitle == null && track.index == selectedInternalIndex,
                    focusRequester = requesters[1 + i],
                    onClick = { onInternalSelected(track.index) }
                ) { focused ->
                    SubtitleRowText(
                        name = track.name.ifBlank { track.language.orEmpty() },
                        tag = track.codec?.substringAfterLast('/')?.uppercase()?.takeIf { it.length in 2..5 },
                        focused = focused
                    )
                }
            }
            itemsIndexed(addonSubtitles, key = { i, s -> "ad:${s.id}:$i" }) { i, subtitle ->
                ReferenceRow(
                    selected = selectedAddonSubtitle?.id == subtitle.id,
                    focusRequester = requesters[1 + internalTracks.size + i],
                    onClick = { onAddonSelected(subtitle) }
                ) { focused ->
                    SubtitleRowText(
                        name = subtitle.getDisplayLanguage(),
                        tag = subtitle.url.substringBefore('?').substringAfterLast('.', "").uppercase().takeIf { it.length in 2..4 },
                        focused = focused
                    )
                }
            }
        }
    }
}

@Composable
private fun SubtitleRowText(name: String, tag: String?, focused: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = name,
            color = Color.White.copy(alpha = if (focused) 1f else 0.82f),
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (tag != null) Text(text = tag, color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
    }
}

/** The links the addons offer for this title: a big quality, the file name, then size and source. */
@Composable
internal fun ReferenceLinksPanel(
    streams: List<Stream>,
    currentUrl: String?,
    loading: Boolean,
    error: String?,
    onSelected: (Stream) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val initial = streams.indexOfFirst { it.getStreamUrl() != null && it.getStreamUrl() == currentUrl }.coerceAtLeast(0)
    val requesters = rememberRowFocus(true, streams.size, initial)
    ReferencePanel(width = 640.dp, title = stringResource(R.string.player_panel_links), onDismiss = onDismiss, modifier = modifier) {
        if (streams.isEmpty()) {
            Text(
                text = error ?: stringResource(if (loading) R.string.player_panel_links_loading else R.string.player_panel_links_none),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 6.dp)
            )
            return@ReferencePanel
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(streams, key = { i, s -> s.stableKey(i) }) { i, stream ->
                val isCurrent = stream.getStreamUrl() != null && stream.getStreamUrl() == currentUrl
                ReferenceRow(
                    selected = false,
                    focusRequester = requesters[i],
                    onClick = { onSelected(stream) },
                    height = 52.dp
                ) { focused ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.width(26.dp)) {
                            if (isCurrent) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.width(16.dp).height(16.dp))
                        }
                        Text(
                            text = streamQualityLabel(stream),
                            color = Color.White.copy(alpha = if (focused) 1f else 0.82f),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.width(78.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = streamFileName(stream),
                                color = Color.White.copy(alpha = if (focused) 1f else 0.82f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                stream.behaviorHints?.videoSize?.takeIf { it > 0L }?.let {
                                    Text(formatStreamSize(it), color = Color(0xFFC8B26A), fontSize = 10.sp)
                                }
                                Text(stream.addonName, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun streamQualityLabel(stream: Stream): String {
    val height = stream.qualityValue.takeIf { it > 0 } ?: Regex("(\\d{3,4})p", RegexOption.IGNORE_CASE)
        .find(stream.quality.orEmpty() + " " + stream.name.orEmpty() + " " + stream.title.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
        ?: if (stream.quality.orEmpty().contains("4k", true) || stream.name.orEmpty().contains("4k", true)) 2160 else 0
    return when {
        height >= 2160 -> "4K"
        height >= 1440 -> "QHD"
        height >= 1080 -> "FHD"
        height >= 720 -> "HD"
        height > 0 -> "SD"
        else -> stream.quality?.takeIf { it.isNotBlank() } ?: "--"
    }
}

private fun streamFileName(stream: Stream): String =
    stream.behaviorHints?.filename?.takeIf { it.isNotBlank() }
        ?: stream.title?.lineSequence()?.firstOrNull { it.isNotBlank() }
        ?: stream.description?.lineSequence()?.firstOrNull { it.isNotBlank() }
        ?: stream.name.orEmpty()

private fun formatStreamSize(bytes: Long): String {
    val gb = bytes / 1_073_741_824.0
    return if (gb >= 1.0) String.format(java.util.Locale.US, "%.1f GB", gb)
    else String.format(java.util.Locale.US, "%.1f MB", bytes / 1_048_576.0)
}
