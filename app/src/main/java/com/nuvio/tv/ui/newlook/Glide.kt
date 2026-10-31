package com.nuvio.tv.ui.newlook

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * How the new look's pages and rows move between items, after Nuvio Legacy:
 * a glide that starts quickly and eases into place over about 400 ms, instead of a 220 ms tween that arrived in a
 * handful of frames. Critically damped, so it never overshoots; as a spring it keeps its speed when the next press
 * comes mid-glide, where a tween started again from a standstill and hitched.
 */
object Glide {
    /** Stiff, for weak TV boxes: settles in about 200 ms (280f was the softer glide, about 400 ms). */
    const val STIFFNESS = 900f

    val scroll: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = STIFFNESS)
}
