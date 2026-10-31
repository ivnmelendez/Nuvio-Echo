package com.nuvio.tv.ui.newlook

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import com.nuvio.tv.domain.model.AppTheme
import com.nuvio.tv.ui.theme.NuvioTheme

/** The focus hairline of the new look: 1 px near-silver, right on the edge. */
internal val HairlineColor = Color(0xC7E8E8E8)

/** Quiet focus (Appearance → Look & feel): a white edge, a slight lift and a soft shadow instead of a coloured ring. */
val LocalQuietFocus = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Light mode (Appearance → Look & feel): cards without zoom, shadow, depth or picture fade. */
val LocalLightMode = androidx.compose.runtime.staticCompositionLocalOf { true }

/**
 * The new look's focus ring colour: the colour picked in Settings → Appearance. White, the default, keeps the
 * near-silver hairline, as Quiet focus does whatever the colour.
 */
internal val focusRingColor: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalQuietFocus.current || NuvioTheme.currentTheme == AppTheme.WHITE) HairlineColor else NuvioTheme.colors.FocusRing

/**
 * Liquid glass at full transparency: no frosting and no blur, so it's plain layered
 * drawing that costs the same on every TV. A gray body taken from the page gradient, a rim that's brightest on the
 * top and bottom curves and nearly clear down the sides, specular glints on those edges, and a soft drop shadow.
 * Nothing is clipped, so a lens inside can bulge past the sides.
 *
 * The glints are straight lines 1 dp in from the top and bottom, so they only suit a wide shape: on the narrow
 * sidebar the rounded ends curve away from them and they showed as white patches past the edge ([glints] off).
 *
 * [shadow] off for glass that lies flat on the page with text scrolling inside it (a list panel): the shadow shows
 * through the clear body, and on the TV it redrew as lighter blocks wherever the text inside changed.
 */
internal fun Modifier.glassSurface(shape: Shape, glints: Boolean = true, shadow: Boolean = true): Modifier = this
    .drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val stroke = 1.dp.toPx()
        onDrawBehind {
            drawOutline(outline, FlatPanel)
            drawOutline(outline, FlatRim, style = Stroke(stroke))
        }
    }

private val FlatPanel = Color(0xFF141414)
private val FlatRim = Color.White.copy(alpha = 0.10f)

/** How opaque the menu's glass is (Layout → Menu & Navigation): [MENU_GLASS_LOW], [MENU_GLASS_MID] or [MENU_GLASS_HIGH]. */
val LocalMenuGlass = androidx.compose.runtime.staticCompositionLocalOf { MENU_GLASS_LOW }
const val MENU_GLASS_LOW = 0
const val MENU_GLASS_MID = 1
const val MENU_GLASS_HIGH = 2

/**
 * The blurred artwork Home's ambient colour is showing (see [com.nuvio.tv.ui.newlook.home.AmbientPageWash]), for the
 * menu's glass to frost with. Null when none is on screen.
 */
internal object AmbientWashState {
    var image by androidx.compose.runtime.mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    /** The artwork the ambient colour last showed; kept after leaving, so Settings and Who's watching can wear it. */
    @Volatile
    var lastUrl: String? = null
}

/**
 * Frosts the menu's glass at Mid and High: fills it with the part of the ambient wash right behind it (the page's
 * colours, already blurred) plus a light veil, which reads as frosted glass without blurring anything live. Off the
 * wash (another screen, or Ambient colour off) it's a plain gray body instead. Low draws nothing: the clear glass.
 * Goes before [glassSurface], so the glass's rim and glints sit on top.
 */
@Composable
internal fun Modifier.menuFrost(shape: Shape): Modifier = this

/** The lens that sits on the current or focused menu item: a gray glass droplet with a bright inner rim. */
internal fun Modifier.glassLens(shape: Shape): Modifier = this
    .drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        onDrawBehind {
            drawOutline(outline, Color.White.copy(alpha = 0.16f))
            drawOutline(outline, Color.White.copy(alpha = 0.22f), style = Stroke(1.dp.toPx()))
        }
    }

/** The new look's focus ring: a 1 px hairline drawn on the shape's edge. */
internal fun Modifier.hairlineRing(shape: Shape, color: Color = HairlineColor): Modifier = this
    .drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        onDrawWithContent {
            drawContent()
            drawOutline(outline, color, style = Stroke(1.dp.toPx()))
        }
    }
