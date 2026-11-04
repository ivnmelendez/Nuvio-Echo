package com.nuvio.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nuvio.tv.R
import com.nuvio.tv.ui.newlook.NewLookOption
import com.nuvio.tv.ui.newlook.NewLookOptionsMenu
import com.nuvio.tv.ui.screens.home.ContinueWatchingItem

/** URLs that failed to load — skip them immediately on next recomposition. */
internal val brokenImageUrls = java.util.Collections.synchronizedSet(mutableSetOf<String>())

/** Holding a Continue Watching card: the new look's glass menu of what to do with it. */
@Composable
fun ContinueWatchingOptionsDialog(
    item: ContinueWatchingItem,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
    onDetails: () -> Unit,
    onStartFromBeginning: () -> Unit = {},
    showPlayManually: Boolean = false,
    onPlayManually: () -> Unit = {}
) {
    val isPlayEnabled = LocalPlaybackAvailability.current.canStream(item)
    val title = when (item) {
        is ContinueWatchingItem.InProgress -> item.progress.name
        is ContinueWatchingItem.NextUp -> item.info.name
    }
    val image = when (item) {
        is ContinueWatchingItem.InProgress -> item.progress.backdrop ?: item.progress.poster
        is ContinueWatchingItem.NextUp -> item.info.thumbnail ?: item.info.backdrop ?: item.info.poster
    }
    // As in the reference: View Details, Remove, Close.
    com.nuvio.tv.ui.newlook.ReferenceOptionsMenu(
        options = listOf(
            NewLookOption(stringResource(R.string.cw_view_details), onClick = onDetails),
            NewLookOption(stringResource(R.string.cw_action_remove), onClick = onRemove),
            NewLookOption(stringResource(R.string.action_close), onClick = onDismiss)
        ),
        onDismiss = onDismiss
    )
}
