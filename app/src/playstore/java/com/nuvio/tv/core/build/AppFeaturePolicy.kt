package com.nuvio.tv.core.build

object AppFeaturePolicy {
    val pluginsEnabled: Boolean = false
    val inAppUpdatesEnabled: Boolean = false
    val inAppTrailerPlaybackEnabled: Boolean = false
    val externalTrailerPlaybackEnabled: Boolean = false
    val homeTrailersSwitchAvailable: Boolean = false
    val codeActivationEnabled: Boolean = false
    /** Wide auto-advancing rows (highlights, grand rows, smart highlights); off means plain poster rows only. */
    val carouselRowsEnabled: Boolean = false
    /** The collections screen in addon settings (folders of catalogs on Home). */
    val collectionsEnabled: Boolean = false
    /** Trakt and Simkl in Tracking; off leaves MDBList as the only service. */
    val traktAndSimklEnabled: Boolean = false
    val essentialSettingsOnly: Boolean = true
    /** The app language on a device where none has been chosen yet; empty follows the device. */
    val defaultAppLocaleTag: String = ""
    /** Metadata-only addon (no streams) installed once at first launch so catalogs, search and details work. */
    val defaultAddonUrls: List<String> = listOf("https://v3-cinemeta.strem.io")
    val lightModeSwitchAvailable: Boolean = false
    val trailerPlaybackMode: TrailerPlaybackMode = TrailerPlaybackMode.EXTERNAL
    val imdbRatingLogoEnabled: Boolean = false
}
