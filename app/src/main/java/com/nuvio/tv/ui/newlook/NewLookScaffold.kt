package com.nuvio.tv.ui.newlook

import kotlin.math.roundToInt
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Settings
import com.nuvio.tv.ui.theme.MontserratFamily
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import com.nuvio.tv.DrawerItem
import com.nuvio.tv.LocalContentFocusRequester
import com.nuvio.tv.LocalSidebarExpanded
import com.nuvio.tv.ui.components.ProfileAvatarCircle
import com.nuvio.tv.ui.navigation.NuvioNavHost
import com.nuvio.tv.ui.navigation.Screen
import com.nuvio.tv.ui.theme.NuvioLayout
import com.nuvio.tv.ui.util.rememberDrawerItemFocusRequesters
import kotlinx.coroutines.launch

/**
 * What the new look's menu tells the screens under it. Home uses it to draw across the sidebar's column, to embed
 * the top tab bar in its page (menu on top), and to know when the hidden menu is showing.
 */
@Stable
class NewLookChrome internal constructor(
    val menuOnTop: Boolean,
    val hideMenu: Boolean,
    /** Start padding the scaffold gives root screens; Home draws across it. */
    val contentInset: Dp,
    private val menuFocusedState: State,
    private val focusTopBarAction: () -> Unit,
    private val topBarContent: @Composable (onDown: () -> Unit, modifier: Modifier) -> Unit,
    private val pageScroll: androidx.compose.runtime.MutableFloatState,
    private val homeReady: androidx.compose.runtime.MutableIntState
) {
    internal class State {
        var pill by mutableStateOf(false)
        var bar by mutableStateOf(false)
        var avatar by mutableStateOf(false)
        val any: Boolean get() = pill || bar || avatar
    }

    val topBarFocused: Boolean get() = menuFocusedState.bar

    /** Home's page has scrolled far enough that the hero's own bar is out of sight (the pinned one takes over). */
    val pastHero: Boolean get() = pageScroll.floatValue >= 100_000f

    /**
     * How far Home's page has scrolled from the top, in px. With the menu on top and showing, the avatar moves up with
     * it, as the tab bar it sits beside is part of the page (it stayed put over the first row's title before).
     */
    fun reportPageScroll(px: Float) {
        pageScroll.floatValue = px
    }

    /**
     * Home has its rows on screen. The pinned bar and its black band wait for it: shown as soon as the route changed
     * back to Home, they sat over the details page still leaving, before any of Home was there. Counted, as two Homes
     * (two tabs) are composed together for a moment while the screen changes.
     */
    fun homeReadyUp() { homeReady.intValue += 1 }
    fun homeReadyDown() { homeReady.intValue = (homeReady.intValue - 1).coerceAtLeast(0) }

    /** Menu on top: moves focus to the current screen's tab (Up from Home's window). */
    fun focusTopBar() = focusTopBarAction()

    /** Menu on top: the glass tab bar, for Home to place in its page. [onDown] is where Down goes from it. */
    @Composable
    fun TopBar(onDown: () -> Unit, modifier: Modifier = Modifier) = topBarContent(onDown, modifier)
}

val LocalNewLookChrome = staticCompositionLocalOf<NewLookChrome?> { null }

private val PillWidth = 56.dp
private val PillPadding = 8.dp
private val IconSlot = 40.dp
private val IconGap = 22.dp
private val IconSize = 22.dp
internal val SidebarColumn = 96.dp
private val AvatarSize = 36.dp
internal val AvatarX = 30.dp
internal val AvatarY = 12.dp
private val BarHeight = 37.dp
private val LensHeight = 25.dp
private val BarTop = 12.dp
private val BarEnd = 32.dp

/**
 * The new look's navigation: a liquid-glass pill of icons on the left (or a glass tab bar on top), the profile
 * avatar at Home's top-left, and the content. Behaves like the classic sidebar: Left from a screen's left edge or
 * Back moves into the menu, Right (or Down from the top bar) goes back, Back again from the menu leaves the app.
 */
@Composable
internal fun NewLookScaffold(
    longPressBackHeld: MutableState<Boolean>,
    navController: NavHostController,
    startDestination: String,
    currentRoute: String?,
    rootRoutes: Set<String>,
    drawerItems: List<DrawerItem>,
    selectedDrawerRoute: String?,
    hideMenu: Boolean,
    menuOnTop: Boolean,
    notificationStyle: com.nuvio.tv.data.local.NotificationStyle,
    activeProfileName: String,
    activeProfileColorHex: String,
    activeProfileAvatarImageUrl: String?,
    showProfileSelector: Boolean,
    onSwitchProfile: () -> Unit,
    onNavigate: (String) -> Unit,
    onExitApp: () -> Unit,
    navigateToRoute: (String) -> Unit,
    drawerIcon: @Composable (item: DrawerItem, tint: Color, modifier: Modifier) -> Unit
) {
    val showMenu = currentRoute in rootRoutes
    // Movies, Series and Watch later are Home's screen with other rows: same avatar, same bar.
    val onHome = currentRoute == Screen.Home.route || currentRoute == Screen.Movies.route ||
        currentRoute == Screen.SeriesTab.route || currentRoute == Screen.Library.route
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val contentFocusRequester = remember { FocusRequester() }
    val itemFocus = rememberDrawerItemFocusRequesters(drawerItems)
    val avatarFocus = remember { FocusRequester() }
    val focusState = remember { NewLookChrome.State() }
    var focusedIndex by remember { mutableIntStateOf(-1) }
    // Non-Home screens with the menu on top: the bar is an overlay that shows only while it has focus.
    var overlayBarShown by remember { mutableStateOf(false) }
    val avatarNotify = remember { AvatarNotifyState() }

    // When the last D-pad move was made, and when the menu was last opened on purpose.
    val trace = remember { BarTrace() }

    val barItems = remember(drawerItems) { topBarOrder(drawerItems) }
    val leftGate = remember { SidebarLeftGate() }
    // Without a hero the page is always past it: the pinned bar and its black band are there from the first frame,
    // not once Home has laid out and said so.
    val pageScroll = remember { androidx.compose.runtime.mutableFloatStateOf(if (com.nuvio.tv.ui.newlook.home.HERO_ENABLED) 0f else 100_000f) }

    fun focusAfterLayout(requester: FocusRequester) {
        scope.launch {
            repeat(2) { withFrameNanos { } }
            runCatching { requester.requestFocus() }
        }
    }

    fun selectedOrFirst(): String? =
        selectedDrawerRoute?.takeIf { route -> drawerItems.any { it.route == route } } ?: drawerItems.firstOrNull()?.route

    fun openMenu() {
        trace.openedAt = android.os.SystemClock.uptimeMillis()
        val route = selectedOrFirst() ?: return
        if (menuOnTop && !onHome) overlayBarShown = true
        focusAfterLayout(itemFocus.getValue(route))
    }

    fun closeMenu() {
        overlayBarShown = false
        runCatching { contentFocusRequester.requestFocus() }
    }

    fun click(route: String) {
        android.util.Log.d("MenuNav", "click $route (current $currentRoute)")
        onNavigate(route)
        navigateToRoute(route)
        overlayBarShown = false
        if (route == currentRoute) closeMenu()
    }

    LaunchedEffect(showMenu) {
        if (!showMenu) overlayBarShown = false
    }
    LaunchedEffect(overlayBarShown, focusState.bar) {
        // The overlay bar goes away once focus has left it.
        if (overlayBarShown && !focusState.bar) {
            repeat(3) { withFrameNanos { } }
            if (!focusState.bar) overlayBarShown = false
        }
    }

    BackHandler(enabled = showMenu && !focusState.any) { openMenu() }
    BackHandler(enabled = showMenu && focusState.any) {
        if (longPressBackHeld.value) return@BackHandler
        onExitApp()
    }

    val topBar: @Composable (() -> Unit, Modifier) -> Unit = { onDown, modifier ->
        GlassTabBar(
            items = barItems,
            selectedRoute = selectedDrawerRoute,
            itemFocus = itemFocus,
            onFocusChanged = { focusState.bar = it },
            onClick = ::click,
            onLeftFromFirst = {
                if (onHome) focusAfterLayout(avatarFocus)
            },
            onDown = onDown,
            drawerIcon = drawerIcon,
            modifier = modifier,
            litAllowed = { trace.recent() }
        )
    }
    // The chrome is remembered, so it reaches the bar and the menu through the latest lambdas (which see the
    // current route), never the ones from when it was made.
    val latestTopBar by rememberUpdatedState(topBar)
    val latestOpenMenu by rememberUpdatedState(::openMenu)
    // Root screens make room for the sidebar only while it stays on screen; hidden or on top, they use the full
    // width (Genres and Search had a wasted column on the left).
    val sidebarInset = if (!menuOnTop && !hideMenu) NuvioLayout.tokens.sidebarContentOffset else 0.dp
    val homeReady = remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val chrome = remember(menuOnTop, hideMenu, barItems, selectedDrawerRoute, onHome) {
        NewLookChrome(
            menuOnTop = menuOnTop,
            hideMenu = hideMenu,
            contentInset = sidebarInset,
            menuFocusedState = focusState,
            focusTopBarAction = { latestOpenMenu() },
            topBarContent = { onDown, modifier -> latestTopBar(onDown, modifier) },
            pageScroll = pageScroll,
            homeReady = homeReady
        )
    }

    // Menu on top, Home scrolled past its hero: the bar and avatar left with it, so they come back pinned to the top.
    val pastHero by remember { androidx.compose.runtime.derivedStateOf { pageScroll.floatValue >= 100_000f } }
    val pinnedBar = menuOnTop && !hideMenu && showMenu && onHome && pastHero && homeReady.intValue > 0
    val pinReveal by animateFloatAsState(
        targetValue = if (pinnedBar) 1f else 0f,
        // Coming in, the bar fades with Home's own entrance (the screen before it fades out first, then Home fades in),
        // so it arrives as part of Home and not ahead of it. Going out it leaves at once.
        animationSpec = if (pinnedBar) {
            androidx.compose.animation.core.tween(
                com.nuvio.tv.ui.navigation.FADE_IN_MS,
                delayMillis = com.nuvio.tv.ui.navigation.FADE_OUT_MS,
                easing = androidx.compose.animation.core.LinearEasing
            )
        } else {
            androidx.compose.animation.core.snap()
        },
        label = "pinnedBarReveal"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionUp || event.key == Key.DirectionDown ||
                        event.key == Key.DirectionLeft || event.key == Key.DirectionRight)
                ) trace.movedAt = android.os.SystemClock.uptimeMillis()
                if (longPressBackHeld.value && event.key == Key.Back) {
                    if (event.type == KeyEventType.KeyUp) longPressBackHeld.value = false
                    return@onPreviewKeyEvent true
                }
                false
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = if (showMenu) sidebarInset else 0.dp)
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.DirectionLeft && event.type == KeyEventType.KeyDown) {
                        leftGate.onLeftDown(event.nativeKeyEvent.eventTime, event.nativeKeyEvent.repeatCount)
                    }
                    // Long-press Back on a root screen goes straight to the menu.
                    if (event.key == Key.Back && event.type == KeyEventType.KeyDown && showMenu &&
                        !focusState.any && event.nativeKeyEvent.isLongPress
                    ) {
                        if (!longPressBackHeld.value) {
                            longPressBackHeld.value = true
                            openMenu()
                        }
                        return@onPreviewKeyEvent true
                    }
                    false
                }
                .onKeyEvent { event ->
                    // Left from a screen's left edge moves into the sidebar.
                    if (!menuOnTop && showMenu && !focusState.any && event.type == KeyEventType.KeyDown &&
                        event.key == Key.DirectionLeft
                    ) {
                        // Only a held Left, or a fresh one after a pause, opens the sidebar (SidebarLeftGate): the
                        // sidebar's onEnter asks the gate when focus search heads into it, and so does this when there
                        // was nothing at all to move to. A quick press that found nothing (a lazy list still
                        // scrolling) tries once more a frame later.
                        if (focusManager.moveFocus(FocusDirection.Left)) {
                            if (!focusState.any) leftGate.onMoved()
                        } else if (leftGate.mayOpen()) {
                            openMenu()
                        } else if (event.nativeKeyEvent.repeatCount == 0) {
                            scope.launch {
                                withFrameNanos { }
                                if (!focusState.any && focusManager.moveFocus(FocusDirection.Left) && !focusState.any) leftGate.onMoved()
                            }
                        }
                        true
                    } else {
                        false
                    }
                }
        ) {
            CompositionLocalProvider(
                LocalSidebarExpanded provides focusState.any,
                LocalContentFocusRequester provides contentFocusRequester,
                LocalNewLookChrome provides chrome
            ) {
                NuvioNavHost(
                    navController = navController,
                    startDestination = startDestination
                )
            }
        }

        if (showMenu && !menuOnTop) {
            val shown = !hideMenu || focusState.any
            val reveal by animateFloatAsState(
                targetValue = if (shown) 1f else 0f,
                animationSpec = androidx.compose.animation.core.snap(),
                label = "menuReveal"
            )
            if (hideMenu && reveal > 0f) {
                // Hidden menu: a dark scrim behind it while it's in.
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(240.dp)
                        .graphicsLayer { alpha = reveal }
                        .background(
                            Brush.horizontalGradient(
                                0f to Color(0xE0060606),
                                0.45f to Color(0x8C060606),
                                1f to Color.Transparent
                            )
                        )
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(SidebarColumn)
                    .graphicsLayer {
                        alpha = reveal
                        translationX = (1f - reveal) * -28.dp.toPx()
                    },
                contentAlignment = Alignment.Center
            ) {
                GlassPill(
                    items = drawerItems,
                    selectedRoute = selectedDrawerRoute,
                    focused = focusState.pill,
                    focusedIndex = focusedIndex,
                    itemFocus = itemFocus,
                    onFocusChanged = { focusState.pill = it },
                    onItemFocused = { focusedIndex = it },
                    onClick = ::click,
                    onUpFromFirst = { if (onHome) focusAfterLayout(avatarFocus) },
                    onRight = ::closeMenu,
                    mayEnterWithLeft = leftGate::mayOpen,
                    drawerIcon = drawerIcon
                )
            }
        }

        if (showMenu && menuOnTop && !onHome && overlayBarShown) {
            // The bar over a non-Home screen, with a scrim from the top.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xE0060606), Color.Transparent)))
            )
            Box(modifier = Modifier.fillMaxWidth().padding(top = BarTop, end = BarEnd), contentAlignment = Alignment.TopEnd) {
                topBar({ closeMenu() }, Modifier)
            }
            LaunchedEffect(Unit) { openMenu() }
        }

        if (pinnedBar || pinReveal > 0f) {
            // Home on a row with the menu on top: the bar stays at the top, over a scrim.
            Box(modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = pinReveal }) {
                // The black band under the bar lives in Home (it comes and goes with the rows). Here it went as soon as the
                // route left Home, while Home was still fading out, and the row hidden behind it showed for a moment.
                Box(modifier = Modifier.fillMaxWidth().padding(top = BarTop, end = BarEnd), contentAlignment = Alignment.TopEnd) {
                    topBar({ closeMenu() }, Modifier)
                }
            }
        }

        // Notifications: drawn under the avatar so style 2 grows out from behind it. Over the player only the
        // player's own ones show, always as the island (the avatar isn't drawn there); the rest wait for it to close.
        if (currentRoute?.startsWith("player") != true) {
            // Auto quality's notices belong to the player alone.
            NotificationHost(
                style = notificationStyle,
                avatar = avatarNotify,
                shows = {
                    it.kind != NewLookNotifications.Kind.QUALITY_UP && it.kind != NewLookNotifications.Kind.QUALITY_DOWN
                }
            )
        } else {
            NotificationHost(
                style = com.nuvio.tv.data.local.NotificationStyle.ISLAND,
                avatar = avatarNotify,
                shows = { it.kind.forPlayer }
            )
        }

        if ((showMenu && onHome) || avatarNotify.visible) {
            val avatarShown = (showMenu && onHome && (!hideMenu || focusState.any)) || avatarNotify.visible
            val avatarReveal by animateFloatAsState(
                targetValue = if (avatarShown) 1f else 0f,
                animationSpec = androidx.compose.animation.core.snap(),
                label = "avatarReveal"
            )
            // Menu on top and showing: the avatar scrolls away with the page and its tab bar.
            val scrollsWithPage = menuOnTop && !hideMenu && showMenu && onHome
            val lift by animateFloatAsState(
                targetValue = if (avatarNotify.lifted) 1f else 0f,
                animationSpec = androidx.compose.animation.core.snap(),
                label = "avatarLift"
            )
            ProfileBubble(
                name = activeProfileName,
                colorHex = activeProfileColorHex,
                avatarImageUrl = activeProfileAvatarImageUrl,
                focusRequester = avatarFocus,
                // The profile picture is hidden for now: invisible and never focused.
                canFocus = false,
                onFocusChanged = { focusState.avatar = it },
                onClick = { if (showProfileSelector) onSwitchProfile() },
                onBackToMenu = {
                    val route = if (menuOnTop) barItems.firstOrNull()?.route else drawerItems.firstOrNull()?.route
                    route?.let { focusAfterLayout(itemFocus.getValue(it)) }
                },
                modifier = Modifier
                    .offset { IntOffset(AvatarX.roundToPx(), AvatarY.roundToPx()) }
                    .graphicsLayer {
                        alpha = 0f
                        val s = (0.82f + 0.18f * avatarReveal) * (1f + 0.08f * lift)
                        scaleX = s
                        scaleY = s
                        translationX = 6.dp.toPx() * lift
                        // A notification brings it back into view wherever the page is.
                        if (scrollsWithPage && !avatarNotify.visible) {
                            // Past the hero it stays pinned beside the bar; before, it leaves with the page.
                            if (!pinnedBar) translationY = -pageScroll.floatValue
                        }
                    }
            )
        }
    }
}

/** Menu on top follows Netflix: Search first, the screens as words, Settings last. */
private fun topBarOrder(items: List<DrawerItem>): List<DrawerItem> {
    val search = items.filter { it.route == Screen.Search.route }
    val settings = items.filter { it.route == Screen.Settings.route }
    return search + items.filter { it !in search && it !in settings } + settings
}

// ---------------------------------------------------------------------------------------------
// The pill
// ---------------------------------------------------------------------------------------------

@Composable
internal fun GlassPill(
    items: List<DrawerItem>,
    selectedRoute: String?,
    focused: Boolean,
    focusedIndex: Int,
    itemFocus: Map<String, FocusRequester>,
    onFocusChanged: (Boolean) -> Unit,
    onItemFocused: (Int) -> Unit,
    onClick: (String) -> Unit,
    onUpFromFirst: () -> Unit,
    onRight: () -> Unit,
    /** Asked when Left from a screen heads into the pill; false keeps focus where it is (see SidebarLeftGate). */
    mayEnterWithLeft: () -> Boolean = { true },
    drawerIcon: @Composable (item: DrawerItem, tint: Color, modifier: Modifier) -> Unit
) {
    val lastSelected = remember { IntArray(1) }
    val selectedFound = items.indexOfFirst { it.route == selectedRoute }
    if (selectedFound >= 0) lastSelected[0] = selectedFound
    val selectedIndex = if (selectedFound >= 0) selectedFound else lastSelected[0].coerceIn(0, (items.size - 1).coerceAtLeast(0))
    val at = if (focused && focusedIndex in items.indices) focusedIndex else selectedIndex
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
    Box(
        modifier = Modifier
            .width(PillWidth)
            .menuFrost(shape)
            .glassSurface(shape, glints = false)
            .onFocusChanged { onFocusChanged(it.hasFocus) }
            .focusProperties {
                onEnter = {
                    if (requestedFocusDirection == FocusDirection.Left) {
                        if (!mayEnterWithLeft()) {
                            cancelFocusChange()
                        } else {
                            items.getOrNull(selectedIndex)?.let { runCatching { itemFocus.getValue(it.route).requestFocus() } }
                        }
                    }
                }
            }
            .focusGroup()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionRight -> {
                        onRight()
                        true
                    }
                    Key.DirectionLeft -> true
                    Key.DirectionUp -> {
                        if (focusedIndex <= 0) onUpFromFirst()
                        else items.getOrNull(focusedIndex - 1)?.let { runCatching { itemFocus.getValue(it.route).requestFocus() } }
                        true
                    }
                    Key.DirectionDown -> {
                        items.getOrNull(focusedIndex + 1)?.let { runCatching { itemFocus.getValue(it.route).requestFocus() } }
                        true
                    }
                    else -> false
                }
            }
            .padding(PillPadding)
    ) {
        PillLens(at = at, focused = focused)
        Column(verticalArrangement = Arrangement.spacedBy(IconGap)) {
            items.forEachIndexed { index, item ->
                key(item.route) {
                    var itemFocused by remember { mutableStateOf(false) }
                    val selected = item.route == selectedRoute
                    val tint by androidx.compose.animation.animateColorAsState(
                        targetValue = when {
                            itemFocused -> Color.White
                            selected -> Color(0xFFE6E6E6)
                            else -> Color(0xFF9E9E9E)
                        },
                        animationSpec = androidx.compose.animation.core.snap(),
                        label = "pillIconTint"
                    )
                    Box(
                        modifier = Modifier
                            .size(IconSlot)
                            .focusRequester(itemFocus.getValue(item.route))
                            .onFocusChanged {
                                itemFocused = it.isFocused
                                if (it.isFocused) onItemFocused(index)
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onClick(item.route) },
                        contentAlignment = Alignment.Center
                    ) {
                        drawerIcon(item, tint, Modifier.size(IconSize))
                    }
                }
            }
        }
    }
}

/**
 * The glass lens behind the icons. At rest it sits faintly on the current screen's icon; with the menu focused it
 * brightens and slides straight to the focused icon.
 */
@Composable
private fun PillLens(at: Int, focused: Boolean) {
    val density = LocalDensity.current
    val step = with(density) { (IconSlot + IconGap).toPx() }
    val y by animateFloatAsState(targetValue = at * step, animationSpec = androidx.compose.animation.core.snap(), label = "lensY")
    val alpha by animateFloatAsState(if (focused) 1f else 0.55f, androidx.compose.animation.core.snap(), label = "lensAlpha")
    Box(
        modifier = Modifier
            // 48 wide in the pill's 40-wide content: requiredSize keeps it a full circle, centred on the icon (size
            // was squeezed to 40, an oval sitting 4 dp left of the icon); only the vertical nudge is needed.
            .offset(y = (-4).dp)
            // Moved by layout, not the layer's translation: a translated layer here was drawn clipped to where it
            // started (the top bar's lens, in the JVM preview), so it showed as a stub off the first item.
            .offset { IntOffset(0, y.roundToInt()) }
            .graphicsLayer { this.alpha = alpha }
            .requiredSize(48.dp)
            .glassLens(androidx.compose.foundation.shape.CircleShape)
    )
}

/** What the top bar needs to tell the person's own focus moves from focus that lands by itself. */
internal class BarTrace {
    var movedAt = 0L
    var openedAt = 0L
    fun recent(): Boolean {
        val now = android.os.SystemClock.uptimeMillis()
        return now - movedAt < 700L || now - openedAt < 700L
    }
}

// ---------------------------------------------------------------------------------------------
// The top bar
// ---------------------------------------------------------------------------------------------

@Composable
internal fun GlassTabBar(
    items: List<DrawerItem>,
    selectedRoute: String?,
    itemFocus: Map<String, FocusRequester>,
    onFocusChanged: (Boolean) -> Unit,
    onClick: (String) -> Unit,
    onLeftFromFirst: () -> Unit,
    onDown: () -> Unit,
    drawerIcon: @Composable (item: DrawerItem, tint: Color, modifier: Modifier) -> Unit,
    modifier: Modifier = Modifier,
    /** Whether focus arriving now was the person's doing (see [lit]); true when the caller can't tell. */
    litAllowed: () -> Boolean = { true }
) {
    var hasFocus by remember { mutableStateOf(false) }
    var focusedIndex by remember { mutableIntStateOf(-1) }
    // The white pill shows when the person drives the bar: focus that came by a D-pad move or by the menu being opened
    // on purpose, or any key pressed in the bar. Focus that lands by itself (Back or a tab click replaces the screen)
    // stays grey, so it doesn't flash white for a moment.
    var lit by remember { mutableStateOf(false) }
    // While the screen changes the route briefly matches no tab; the lens stays on the last tab it knew rather than
    // falling back to the first one (Search) and coming back.
    val lastSelected = remember { IntArray(1) }
    val selectedFound = items.indexOfFirst { it.route == selectedRoute }
    if (selectedFound >= 0) lastSelected[0] = selectedFound
    val selectedIndex = if (selectedFound >= 0) selectedFound else lastSelected[0].coerceIn(0, (items.size - 1).coerceAtLeast(0))
    val at = if (hasFocus && focusedIndex in items.indices) focusedIndex else selectedIndex
    val pillShape = androidx.compose.foundation.shape.RoundedCornerShape(50)
    val body = Color(0xFF1F1F1F)
    val settingsIndex = items.indexOfFirst { it.route == Screen.Settings.route }
    @Composable
    fun BarItem(index: Int, item: DrawerItem) {
        key(item.route) {
            var itemFocused by remember { mutableStateOf(false) }
            val iconOnly = item.route == Screen.Search.route || item.route == Screen.Settings.route
            val isSettings = item.route == Screen.Settings.route
            val isLens = index == at
            val driven = hasFocus && lit
            val color = if (itemFocused && lit) Color.Black else Color.White
            Box(
                modifier = Modifier
                    .height(LensHeight)
                    .then(if (iconOnly) Modifier.width(if (item.route == Screen.Settings.route) LensHeight else 32.5.dp) else Modifier)
                    .let {
                        when {
                            isLens && driven -> it.background(Color.White, pillShape)
                            isLens -> it.background(Color(0xFF5D5D5D), pillShape)
                            isSettings -> it.background(Color(0xFF3A3A3A), pillShape)
                            else -> it
                        }
                    }
                    .focusRequester(itemFocus.getValue(item.route))
                    .onFocusChanged {
                        itemFocused = it.isFocused
                        if (it.isFocused) focusedIndex = index
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onClick(item.route) }
                    .padding(horizontal = if (iconOnly) 0.dp else 10.5.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isSettings) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Filled.Settings,
                        contentDescription = item.label,
                        tint = color,
                        modifier = Modifier.size(13.dp)
                    )
                } else if (iconOnly) {
                    drawerIcon(item, color, Modifier.size(15.dp))
                } else {
                    Text(text = item.label, color = color, fontSize = 9.3.sp, letterSpacing = 0.sp, fontWeight = FontWeight.Medium, fontFamily = MontserratFamily, maxLines = 1)
                }
            }
        }
    }
    val grow by animateFloatAsState(if (hasFocus) 1.08f else 1f, androidx.compose.animation.core.snap(), label = "barGrow")
    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = grow
                scaleY = grow
                transformOrigin = TransformOrigin(1f, 0.5f)
            }
            .onFocusChanged {
                hasFocus = it.hasFocus
                lit = it.hasFocus && (lit || litAllowed())
                // Forget the last item: the bar gets focus a moment before the item does, and the lens followed the
                // old index (the Search button, say) before settling on the one that really took focus.
                if (!it.hasFocus) focusedIndex = -1
                onFocusChanged(it.hasFocus)
            }
            // Focus that falls into the bar without being asked (the screen's own bar is replaced on a tab change,
            // and the system looks for the first focusable) goes to the current tab, not to Search.
            .focusProperties {
                onEnter = {
                    items.getOrNull(selectedIndex)?.let { runCatching { itemFocus.getValue(it.route).requestFocus() } }
                }
            }
            .focusGroup()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                lit = true
                when (event.key) {
                    Key.DirectionDown -> {
                        onDown()
                        true
                    }
                    Key.DirectionUp -> true
                    Key.DirectionLeft -> {
                        if (focusedIndex <= 0) onLeftFromFirst()
                        else items.getOrNull(focusedIndex - 1)?.let { runCatching { itemFocus.getValue(it.route).requestFocus() } }
                        true
                    }
                    Key.DirectionRight -> {
                        items.getOrNull(focusedIndex + 1)?.let { runCatching { itemFocus.getValue(it.route).requestFocus() } }
                        true
                    }
                    else -> false
                }
            },
        horizontalArrangement = Arrangement.spacedBy(15.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .height(BarHeight)
                .background(body, pillShape)
                .padding(horizontal = 6.5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item -> if (index != settingsIndex) BarItem(index, item) }
        }
        if (settingsIndex >= 0) {
            Box(
                modifier = Modifier
                    .size(BarHeight)
                    .background(body, pillShape),
                contentAlignment = Alignment.Center
            ) { BarItem(settingsIndex, items[settingsIndex]) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Profile
// ---------------------------------------------------------------------------------------------

/** The active profile's avatar at Home's top-left, outside the menu. OK opens the profile picker. */
@Composable
internal fun ProfileBubble(
    name: String,
    colorHex: String,
    avatarImageUrl: String?,
    focusRequester: FocusRequester,
    canFocus: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
    onBackToMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, androidx.compose.animation.core.snap(), label = "avatarScale")
    val ring = focusRingColor
    Box(
        modifier = modifier
            .size(AvatarSize)
            .graphicsLayer {
                scaleX *= scale
                scaleY *= scale
            }
            .focusRequester(focusRequester)
            .focusProperties { this.canFocus = canFocus }
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown, Key.DirectionRight -> {
                        onBackToMenu()
                        true
                    }
                    Key.DirectionUp, Key.DirectionLeft -> true
                    else -> false
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        ProfileAvatarCircle(
            name = name,
            colorHex = colorHex,
            size = AvatarSize,
            avatarImageUrl = avatarImageUrl,
            imageCrossfade = false,
            // A 1 px ring (the Appearance colour) a little outside it while focused.
            modifier = Modifier.drawWithContent {
                drawContent()
                if (focused) {
                    drawCircle(
                        color = ring,
                        radius = size.minDimension / 2f + 3.dp.toPx(),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())
                    )
                }
            }
        )
    }
}
