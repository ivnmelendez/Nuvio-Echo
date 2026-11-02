package com.nuvio.tv.data.local

import com.nuvio.tv.domain.model.catalogRowLegacyKey
import com.nuvio.tv.domain.model.catalogTypeKey

internal data class LocalHomeCatalogSettingsState(
    val orderKeys: List<String> = emptyList(),
    val disabledKeys: Set<String> = emptySet(),
    val customTitles: Map<String, String> = emptyMap(),
    val hideUnreleasedContent: Boolean = false
)

internal fun homeCatalogKey(addonId: String, type: String, catalogId: String): String {
    return catalogRowLegacyKey(addonId, type, catalogId)
}

internal fun homeCollectionKey(collectionId: String): String {
    return "collection_${collectionId}"
}

internal fun homeLegacyDisabledCatalogKey(
    addonBaseUrl: String,
    type: String,
    catalogId: String,
    catalogName: String
): String {
    return "${addonBaseUrl}_${catalogTypeKey(type)}_${catalogId}_${catalogName}"
}

internal fun hasLegacyHomeCatalogDisabledKeyFormat(key: String): Boolean {
    return key.contains("://")
}

internal fun isHomeCatalogDisabled(
    disabledKeys: Set<String>,
    addonId: String,
    addonBaseUrl: String,
    type: String,
    catalogId: String,
    catalogName: String
): Boolean {
    return homeCatalogKey(addonId, type, catalogId) in disabledKeys ||
        homeLegacyDisabledCatalogKey(addonBaseUrl, type, catalogId, catalogName) in disabledKeys
}
