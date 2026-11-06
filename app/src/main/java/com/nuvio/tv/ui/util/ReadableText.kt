package com.nuvio.tv.ui.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle

/** A soft drop shadow for text set straight over artwork (the details page, the pause screen), so a bright frame can't wash it out. */
val ReadableTextShadow = Shadow(color = Color.Black.copy(alpha = 0.6f), offset = Offset(0f, 2f), blurRadius = 6f)

/** [ReadableTextShadow] as a text style, for the Text calls that set their own colour and size. */
val ReadableTextStyle = TextStyle(shadow = ReadableTextShadow)
