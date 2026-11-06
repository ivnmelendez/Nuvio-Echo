package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.ui.theme.NuvioMotion

import com.nuvio.tv.ui.theme.NuvioTheme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.platform.LocalContext
import com.nuvio.tv.ui.components.LoadingIndicator
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R

/**
 * What shows while a stream starts, as on the reference: plain black with a small spinner, never the title's picture
 * and logo. The status line and progress bar still appear under it when the player's loading status is switched on.
 */
@Composable
fun LoadingOverlay(
    visible: Boolean,
    message: String? = null,
    progress: Float? = null,
    /** Off while the player's controls are over it: the reference shows only them on the black. */
    showSpinner: Boolean = true,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(250)),
        exit = fadeOut(animationSpec = tween(200)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (showSpinner) LoadingIndicator(color = Color.White)

                // The status line and, when there is progress, its bar.
                                val showHorizontalBar = progress != null
                if (!message.isNullOrBlank() || showHorizontalBar) {
                    val messageOffset = 60.dp
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = messageOffset)
                            .padding(horizontal = NuvioTheme.spacing.xl)
                    ) {
                        Crossfade(
                            targetState = message?.takeIf { it.isNotBlank() },
                            animationSpec = tween(durationMillis = 260),
                            label = "loadingMessageCrossfade",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                        ) { loadingMessage ->
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (loadingMessage != null) {
                                    Text(
                                        text = loadingMessage,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White.copy(alpha = 0.72f),
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                        if (showHorizontalBar) {
                            Spacer(modifier = Modifier.height(10.dp))
                            val animatedProgress by animateFloatAsState(
                                targetValue = progress.coerceIn(0f, 1f),
                                animationSpec = tween(durationMillis = NuvioMotion.tokens.durations.overlay),
                                label = "loadingProgress"
                            )
                            Box(
                                modifier = Modifier
                                    .width(240.dp)
                                    .height(NuvioTheme.spacing.xs)
                                    .background(
                                        color = Color.White.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(NuvioTheme.radii.xxs)
                                    )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(animatedProgress)
                                        .height(NuvioTheme.spacing.xs)
                                        .background(
                                            color = Color.White.copy(alpha = 0.85f),
                                            shape = RoundedCornerShape(NuvioTheme.radii.xxs)
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
