package com.nuvio.tv.core.streams

data class StreamBadgeSettings(
    val rules: StreamBadgeRules = StreamBadgeRules(),
    val showFileSizeBadges: Boolean = false,
    val showAddonLogo: Boolean = false,
    val badgePlacement: StreamBadgePlacement = StreamBadgePlacement.BOTTOM
)

enum class StreamBadgePlacement {
    TOP,
    BOTTOM
}
