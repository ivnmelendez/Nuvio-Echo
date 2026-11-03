package com.nuvio.tv.core.build

object AppFeaturePolicy {
    val pluginsEnabled: Boolean = false
    val inAppUpdatesEnabled: Boolean = false
    val inAppTrailerPlaybackEnabled: Boolean = false
    val externalTrailerPlaybackEnabled: Boolean = true
    val homeTrailersSwitchAvailable: Boolean = false
    val codeActivationEnabled: Boolean = false
    /** Wide auto-advancing rows (highlights, grand rows, smart highlights); off means plain poster rows only. */
    val carouselRowsEnabled: Boolean = false
    /** The collections screen in addon settings (folders of catalogs on Home). */
    val collectionsEnabled: Boolean = false
    /** Trakt and Simkl in Tracking; off leaves MDBList as the only service. */
    val traktAndSimklEnabled: Boolean = false
    /** A notice at first launch: no content is included and the addons are the person's own. */
    val firstLaunchNoticeEnabled: Boolean = true
    /** The parental guide overlay needs a third-party server; the Play build never asks it. */
    val parentalGuideAvailable: Boolean = false
    /**
     * True: Home shows only the published rows and never the addons' catalogs. Off in the Play build, where Home
     * starts on the installed addons' catalogs (Cinemeta's) until an addon names its own rows file.
     */
    val homeFromPublishedRowsOnly: Boolean = false
    /** Without an installed addon that serves streams, a title's page has no Play button (trailer and info only). */
    val playNeedsStreamAddon: Boolean = true
    val essentialSettingsOnly: Boolean = true
    /** The app language on a device where none has been chosen yet; empty follows the device. */
    val defaultAppLocaleTag: String = ""
    /** Metadata-only addon (no streams) installed once at first launch so catalogs, search and details work. */
    val defaultAddonUrls: List<String> = listOf("https://v3-cinemeta.strem.io")
    val lightModeSwitchAvailable: Boolean = false
    val trailerPlaybackMode: TrailerPlaybackMode = TrailerPlaybackMode.EXTERNAL
    val imdbRatingLogoEnabled: Boolean = false
}
