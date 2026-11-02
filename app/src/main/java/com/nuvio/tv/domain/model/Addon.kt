package com.nuvio.tv.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Addon(
    val id: String,
    val name: String,
    val displayName: String = name,
    val version: String,
    val description: String?,
    val logo: String?,
    val background: String? = null,
    val baseUrl: String,
    val catalogs: List<CatalogDescriptor>,
    val types: List<ContentType>,
    val rawTypes: List<String> = types.map { it.toApiString() },
    val resources: List<AddonResource>,
    val idPrefixes: List<String> = emptyList(),
    val behaviorHints: AddonBehaviorHints? = null,
    val stremioAddonsConfig: StremioAddonsConfig? = null,
    val manifestLanguage: String? = null,
    val configVersion: Long? = null,
    val timestamp: Long? = null,
    val enabled: Boolean = true
) {
    val isActive: Boolean
        get() = enabled
}

fun List<Addon>.enabledAddons(): List<Addon> {
    val on = filter { it.enabled }
    // Set aside, not removed: it comes back by itself when the addon that replaces it is turned off or deleted.
    return if (on.any { it.behaviorHints?.replacesDefaultAddons == true }) on.filterNot { it.isBuildDefault() } else on
}

private fun Addon.isBuildDefault(): Boolean {
    val own = baseUrl.trim().trimEnd('/')
    return com.nuvio.tv.core.build.AppFeaturePolicy.defaultAddonUrls.any { own.equals(it.trim().trimEnd('/'), ignoreCase = true) }
}

@Immutable
data class CatalogDescriptor(
    val type: ContentType,
    val rawType: String = type.toApiString(),
    val id: String,
    val name: String,
    val extra: List<CatalogExtra> = emptyList(),
    val pageSize: Int? = null,
    val showInHome: Boolean = false,
    val hasExplicitShowInHome: Boolean = false,
    val extraSupported: List<String> = emptyList(),
    val extraRequired: List<String> = emptyList()
) {
    val apiType: String
        get() = rawType.trim().ifBlank { type.toApiString() }
}

@Immutable
data class CatalogExtra(
    val name: String,
    val isRequired: Boolean = false,
    val options: List<String>? = null,
    val defaultValue: String? = null,
    val optionsLimit: Int? = null
)

@Immutable
data class AddonResource(
    val name: String,
    val types: List<String>,
    val idPrefixes: List<String>?
)

@Immutable
data class AddonBehaviorHints(
    val configurable: Boolean? = null,
    val configurationRequired: Boolean? = null,
    val newEpisodeNotifications: Boolean? = null,
    val homeConfigUrl: String? = null,
    /** A language tag (es-419): the app and its metadata use it while this addon is on. */
    val preferredLanguage: String? = null,
    /** The addon brings its own catalogs, so the metadata-only addon the build installs by default is set aside. */
    val replacesDefaultAddons: Boolean? = null
)

@Immutable
data class StremioAddonsConfig(
    val issuer: String? = null,
    val signature: String? = null
)
