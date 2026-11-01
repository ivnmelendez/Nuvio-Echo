package com.nuvio.tv.ui.screens.library

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.screens.detail.detailGlassPanel
import com.nuvio.tv.ui.screens.detail.ignoreHeldSelect
import com.nuvio.tv.ui.screens.detail.ndClickable
import com.nuvio.tv.ui.screens.detail.whiteSelectionSurface
import com.nuvio.tv.ui.screens.detail.trapDpadFocus
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val BinsPillShape = RoundedCornerShape(999.dp)
private val PillIdle = Color(0xFF1E1E1E)
private val PillOn = Color(0xFF2E2E2E)
internal val BinsMuted = Color(0xFF9A9A9A)

/** Reports where this sits in the window, for anchoring a menu to it. */
internal fun Modifier.boundsReporter(onBounds: (Rect) -> Unit): Modifier = onGloballyPositioned { onBounds(it.boundsInRoot()) }

internal data class BinMenuItem(val label: String, val checked: Boolean = false, val onClick: () -> Unit)

/**
 * A pill menu under (or above) [anchor], in the screen's own coordinates. [openedByHold] swallows the held OK that
 * opened it, so the release doesn't pick the first item.
 */
@Composable
internal fun BinPillMenu(
    anchor: Rect,
    title: String?,
    items: List<BinMenuItem>,
    initialIndex: Int,
    openedByHold: Boolean,
    onDismiss: () -> Unit,
    width: Dp = 240.dp
) {
    val requesters = remember(items.size) { List(items.size) { FocusRequester() } }
    BackHandler { onDismiss() }
    LaunchedEffect(Unit) {
        val target = requesters.getOrNull(initialIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))) ?: return@LaunchedEffect
        repeat(6) {
            if (runCatching { target.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            delay(40)
        }
    }
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    val margin = 16.dp.roundToPx()
                    val gap = 8.dp.roundToPx()
                    var y = anchor.bottom.toInt() + gap
                    if (y + p.height > constraints.maxHeight - margin) y = anchor.top.toInt() - gap - p.height
                    y = y.coerceIn(margin, (constraints.maxHeight - margin - p.height).coerceAtLeast(margin))
                    val x = anchor.left.toInt().coerceIn(margin, (constraints.maxWidth - margin - p.width).coerceAtLeast(margin))
                    layout(constraints.maxWidth, constraints.maxHeight) { p.place(x, y) }
                }
                .widthIn(min = width)
                .detailGlassPanel(RoundedCornerShape(20.dp), strength = 1.6f)
                .then(if (openedByHold) Modifier.ignoreHeldSelect() else Modifier)
                .trapDpadFocus()
                .focusGroup()
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (title != null) {
                Text(
                    title, color = BinsMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 4.dp)
                )
            }
            items.forEachIndexed { i, item ->
                var focused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .widthIn(min = width - 12.dp)
                        .height(38.dp)
                        .whiteSelectionSurface(focused, BinsPillShape)
                        .ndClickable(focusRequester = requesters[i], onFocusChanged = { focused = it }, onClick = item.onClick)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        item.label, color = if (focused) Color.Black else Color.White, fontSize = 15.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.checked) {
                        Spacer(Modifier.width(18.dp))
                        Text("✓", color = if (focused) Color.Black else BinsMuted, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

/** A short message in a pill at the bottom of the screen. */
@Composable
internal fun BinsToast(message: String?, modifier: Modifier = Modifier) {
    androidx.compose.animation.AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(220)),
        modifier = modifier
    ) {
        var last by remember { mutableStateOf("") }
        if (message != null) last = message
        Text(
            last,
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier
                .clip(BinsPillShape)
                .background(Color(0xFF1D1D1D))
                .border(1.dp, Color(0xFF333333), BinsPillShape)
                .padding(horizontal = 18.dp, vertical = 10.dp)
        )
    }
}
