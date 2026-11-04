package com.nuvio.tv.core.build

object AppFeaturePolicy {
    val pluginsEnabled: Boolean = true
    val inAppUpdatesEnabled: Boolean = com.nuvio.tv.BuildConfig.IN_APP_UPDATES_ALLOWED
    val inAppTrailerPlaybackEnabled: Boolean = true
    val externalTrailerPlaybackEnabled: Boolean = true
    val homeTrailersSwitchAvailable: Boolean = true
    val codeActivationEnabled: Boolean = true
    /** Wide auto-advancing rows (highlights, grand rows, smart highlights); off means plain poster rows only. */
    val carouselRowsEnabled: Boolean = true
    /** The collections screen in addon settings (folders of catalogs on Home). */
    val collectionsEnabled: Boolean = true
    /** Trakt and Simkl in Tracking; off leaves MDBList as the only service. */
    val traktAndSimklEnabled: Boolean = true
    /** A notice at first launch: no content is included and the addons are the person's own. */
    val firstLaunchNoticeEnabled: Boolean = false
    val parentalGuideAvailable: Boolean = true
    val homeFromPublishedRowsOnly: Boolean = true
    val playNeedsStreamAddon: Boolean = false
    val essentialSettingsOnly: Boolean = false
    /** The app language on a device where none has been chosen yet; empty follows the device. */
    val defaultAppLocaleTag: String = "es-419"
    val defaultAddonUrls: List<String> = emptyList()
    val lightModeSwitchAvailable: Boolean = true
    val trailerPlaybackMode: TrailerPlaybackMode = TrailerPlaybackMode.IN_APP
    val imdbRatingLogoEnabled: Boolean = true
}
