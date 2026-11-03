package com.nuvio.tv.data.local

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.domain.model.TmdbSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TmdbSettingsDataStore @Inject constructor(
    private val factory: ProfileDataStoreFactory,
    private val profileManager: ProfileManager
) {
    companion object {
        private const val FEATURE = "tmdb_settings"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun store(profileId: Int = profileManager.activeProfileId.value) =
        factory.get(profileId, FEATURE)

    private val enabledKey = booleanPreferencesKey("tmdb_enabled")
    private val modernHomeEnabledKey = booleanPreferencesKey("tmdb_modern_home_enabled")
    private val enrichContinueWatchingKey = booleanPreferencesKey("tmdb_enrich_continue_watching")
    private val languageKey = stringPreferencesKey("tmdb_language")
    private val useArtworkKey = booleanPreferencesKey("tmdb_use_artwork")
    private val useBasicInfoKey = booleanPreferencesKey("tmdb_use_basic_info")
    private val useDetailsKey = booleanPreferencesKey("tmdb_use_details")
    private val useCreditsKey = booleanPreferencesKey("tmdb_use_credits")
    private val useProductionsKey = booleanPreferencesKey("tmdb_use_productions")
    private val useNetworksKey = booleanPreferencesKey("tmdb_use_networks")
    private val useEpisodesKey = booleanPreferencesKey("tmdb_use_episodes")
    private val useTrailersKey = booleanPreferencesKey("tmdb_use_trailers")
    private val useMoreLikeThisKey = booleanPreferencesKey("tmdb_use_more_like_this")
    private val useCollectionsKey = booleanPreferencesKey("tmdb_use_collections")

    val settings: StateFlow<TmdbSettings> = profileManager.activeProfileId.flatMapLatest { pid ->
        // The language follows the app's, so it is read again whenever that changes.
        kotlinx.coroutines.flow.combine(factory.get(pid, FEATURE).data, com.nuvio.tv.LocaleCache.version) { prefs, _ ->
            TmdbSettings(
                enabled = prefs[enabledKey] ?: true,
                modernHomeEnabled = prefs[modernHomeEnabledKey] ?: true,
                enrichContinueWatching = prefs[enrichContinueWatchingKey] ?: true,
                language = tmdbLanguageForApp(),
                useArtwork = prefs[useArtworkKey] ?: true,
                useBasicInfo = prefs[useBasicInfoKey] ?: true,
                useDetails = prefs[useDetailsKey] ?: true,
                useCredits = prefs[useCreditsKey] ?: true,
                useProductions = prefs[useProductionsKey] ?: true,
                useNetworks = prefs[useNetworksKey] ?: true,
                useEpisodes = prefs[useEpisodesKey] ?: true,
                useTrailers = prefs[useTrailersKey] ?: true,
                useMoreLikeThis = prefs[useMoreLikeThisKey] ?: false,
                useCollections = prefs[useCollectionsKey] ?: false
            )
        }
    }.stateIn(scope, SharingStarted.Eagerly, TmdbSettings(language = tmdbLanguageForApp()))

    suspend fun setEnabled(enabled: Boolean) {
        store().edit { it[enabledKey] = enabled }
    }

    suspend fun setModernHomeEnabled(enabled: Boolean) {
        store().edit { it[modernHomeEnabledKey] = enabled }
    }

    suspend fun setEnrichContinueWatching(enabled: Boolean) {
        store().edit { it[enrichContinueWatchingKey] = enabled }
    }

    suspend fun setLanguage(language: String) {
        store().edit { it[languageKey] = language.ifBlank { "en" } }
    }

    suspend fun setUseArtwork(enabled: Boolean) {
        store().edit { it[useArtworkKey] = enabled }
    }

    suspend fun setUseBasicInfo(enabled: Boolean) {
        store().edit { it[useBasicInfoKey] = enabled }
    }

    suspend fun setUseDetails(enabled: Boolean) {
        store().edit { it[useDetailsKey] = enabled }
    }

    suspend fun setUseCredits(enabled: Boolean) {
        store().edit { it[useCreditsKey] = enabled }
    }

    suspend fun setUseProductions(enabled: Boolean) {
        store().edit { it[useProductionsKey] = enabled }
    }

    suspend fun setUseNetworks(enabled: Boolean) {
        store().edit { it[useNetworksKey] = enabled }
    }

    suspend fun setUseEpisodes(enabled: Boolean) {
        store().edit { it[useEpisodesKey] = enabled }
    }

    suspend fun setUseTrailers(enabled: Boolean) {
        store().edit { it[useTrailersKey] = enabled }
    }

    suspend fun setUseMoreLikeThis(enabled: Boolean) {
        store().edit { it[useMoreLikeThisKey] = enabled }
    }

    suspend fun setUseCollections(enabled: Boolean) {
        store().edit { it[useCollectionsKey] = enabled }
    }
}

/**
 * The language TMDB answers in follows the app's own language: no separate choice. Latin American Spanish has no
 * TMDB code of its own, so it asks for the Mexican one.
 */
internal fun tmdbLanguageForApp(): String {
    val tag = com.nuvio.tv.LocaleCache.localeTag
        .takeIf { it != com.nuvio.tv.LocaleCache.UNSET && it.isNotEmpty() }
    return tmdbLanguageFor(if (tag != null) java.util.Locale.forLanguageTag(tag) else java.util.Locale.getDefault())
}

internal fun tmdbLanguageFor(locale: java.util.Locale): String {
    val language = when (locale.language) {
        "in" -> "id"
        "iw" -> "he"
        "" -> return "en-US"
        else -> locale.language
    }
    return when (language) {
        "en" -> "en-US"
        "es" -> if (locale.country.isEmpty() || locale.country == "ES") "es-ES" else "es-MX"
        "pt" -> if (locale.country == "PT") "pt-PT" else "pt-BR"
        "zh" -> if (locale.country == "TW") "zh-TW" else "zh-CN"
        "sr" -> "sr"
        else -> language
    }
}
