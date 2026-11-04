package com.nuvio.tv.ui.newlook

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.MontserratFamily

/** "Exit <app>?" over a dimmed screen: a glass panel in the middle with Exit (focused first) and Cancel. */
@Composable
internal fun ExitConfirmDialog(onExit: () -> Unit, onCancel: () -> Unit) {
    val exitFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { exitFocus.requestFocus() } }
    BackHandler(onBack = onCancel)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.46f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(340.dp)
                .height(150.dp)
                .background(Color(0xE8121519), RoundedCornerShape(14.dp))
                .border(1.dp, Color(0xFF383B3F), RoundedCornerShape(14.dp))
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.exit_dialog_title, stringResource(R.string.app_name)),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = MontserratFamily,
                letterSpacing = 0.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 30.dp)
            )
            Row(
                modifier = Modifier.padding(top = 36.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ExitDialogButton(stringResource(R.string.exit_dialog_exit), exitFocus, onExit)
                ExitDialogButton(stringResource(R.string.action_cancel), null, onCancel)
            }
        }
    }
}

@Composable
private fun ExitDialogButton(label: String, requester: FocusRequester?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .width(135.dp)
            .height(42.dp)
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .background(if (focused) Color(0xFFF3F4F3) else Color.White.copy(alpha = 0.15f), shape)
            .then(if (focused) Modifier else Modifier.border(BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)), shape))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (focused) Color.Black else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = MontserratFamily,
            letterSpacing = 0.5.sp
        )
    }
}
