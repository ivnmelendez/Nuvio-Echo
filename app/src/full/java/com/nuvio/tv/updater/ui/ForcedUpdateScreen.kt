package com.nuvio.tv.updater.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.updater.UpdateUiState
import com.nuvio.tv.updater.model.AppUpdate

/**
 * Replaces the whole app while a newer release exists: it downloads on its own, installs, and cannot be dismissed.
 * The button is there for when the install was cancelled or the download failed.
 */
@Composable
fun ForcedUpdateScreen(
    state: UpdateUiState,
    update: AppUpdate,
    onDownload: () -> Unit,
    onInstall: () -> Unit
) {
    BackHandler(enabled = true) { }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(update.tag) {
        if (!state.isDownloading && state.downloadedApkPath == null && state.errorMessage == null) onDownload()
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_tivilatino_mark),
            contentDescription = null,
            modifier = Modifier.size(96.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.update_forced_title),
            color = Color.White,
            fontSize = 28.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.update_forced_message, update.tag.removePrefix("v")),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        state.errorMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(text = it, color = Color(0xFFFF8A80), fontSize = 14.sp, textAlign = TextAlign.Center)
        }
        // Which step this is, so a wait never looks like a hang: downloading (with a bar), or ready to install.
        val status = when {
            state.isDownloading -> R.string.update_forced_status_downloading
            state.downloadedApkPath != null && state.errorMessage == null -> R.string.update_forced_status_ready
            else -> null
        }
        status?.let {
            Spacer(Modifier.height(20.dp))
            Text(text = stringResource(it), color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp, textAlign = TextAlign.Center)
        }
        if (state.isDownloading) {
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .width(360.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.18f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((state.downloadProgress ?: 0f).coerceIn(0f, 1f))
                        .background(Color.White)
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = {
                when {
                    state.isDownloading -> Unit
                    state.downloadedApkPath != null -> onInstall()
                    else -> onDownload()
                }
            },
            enabled = !state.isDownloading,
            modifier = Modifier.focusRequester(focus)
        ) {
            val label = when {
                state.isDownloading -> state.downloadProgress
                    ?.let { stringResource(R.string.update_downloading_progress, (it * 100).toInt()) }
                    ?: stringResource(R.string.update_downloading_ellipsis)
                state.errorMessage != null -> stringResource(R.string.update_forced_retry)
                state.downloadedApkPath != null -> stringResource(R.string.update_install)
                else -> stringResource(R.string.update_forced_button)
            }
            Text(label)
        }
    }
}
