package com.nuvio.tv.ui.newlook

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.nuvio.tv.ui.screens.detail.ignoreHeldSelect
import com.nuvio.tv.ui.screens.detail.ndClickable
import com.nuvio.tv.ui.theme.MontserratFamily
import kotlinx.coroutines.delay

/**
 * What holding a Continue Watching card opens, as in the reference (1920x1080 at 2x, measured): a 300 x 176 dp near-black
 * panel with a thin light edge in the middle of the screen, three rows of 52 dp with centred words, the focused one
 * in a white frame the panel's width. No picture, no title, nothing dimmed around it.
 */
@Composable
internal fun ReferenceOptionsMenu(options: List<NewLookOption>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Nothing dimmed behind the panel: the page stays as it is.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .background(Color(0xE0000000), PanelShape)
                    .border(1.dp, Color.White.copy(alpha = 0.42f), PanelShape)
                    .ignoreHeldSelect()
                    .focusGroup()
                    .padding(vertical = 10.dp)
            ) {
                val requesters = remember(options.size) { List(options.size) { FocusRequester() } }
                LaunchedEffect(options.size) {
                    val first = requesters.firstOrNull() ?: return@LaunchedEffect
                    // The dialog's window needs a moment before anything in it can take focus.
                    repeat(8) {
                        if (runCatching { first.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
                        delay(40)
                    }
                }
                options.forEachIndexed { i, option -> ReferenceOptionRow(option, requesters[i]) }
            }
        }
    }
}

@Composable
private fun ReferenceOptionRow(option: NewLookOption, requester: FocusRequester) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .then(if (focused) Modifier.border(1.5.dp, Color(0xFFE3E5E4), RowShape) else Modifier)
            .ndClickable(focusRequester = requester, onFocusChanged = { focused = it }, onClick = { if (option.enabled) option.onClick() }),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = option.label,
            color = if (focused) Color.White else Color.White.copy(alpha = 0.78f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = MontserratFamily,
            letterSpacing = 0.5.sp,
            maxLines = 1
        )
    }
}

private val PanelShape = RoundedCornerShape(14.dp)
private val RowShape = RoundedCornerShape(8.dp)
