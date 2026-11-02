package com.nuvio.tv.core.build

object AppFeaturePolicy {
    val pluginsEnabled: Boolean = true
    val inAppUpdatesEnabled: Boolean = com.nuvio.tv.BuildConfig.IN_APP_UPDATES_ALLOWED
    val inAppTrailerPlaybackEnabled: Boolean = true
    val externalTrailerPlaybackEnabled: Boolean = true
    val homeTrailersSwitchAvailable: Boolean = true
    val codeActivationEnabled: Boolean = true
    val essentialSettingsOnly: Boolean = false
    /** The app language on a device where none has been chosen yet; empty follows the device. */
    val defaultAppLocaleTag: String = "es-419"
    val defaultAddonUrls: List<String> = emptyList()
    val lightModeSwitchAvailable: Boolean = true
    val supportNuvioEnabled: Boolean = false
    val trailerPlaybackMode: TrailerPlaybackMode = TrailerPlaybackMode.IN_APP
    val imdbRatingLogoEnabled: Boolean = true
}
