package com.nuvio.tv.data.remoteconfig

import com.nuvio.tv.BuildConfig
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.enabledAddons
import com.nuvio.tv.domain.repository.AddonRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Where Home's published rows come from. An installed addon can name its own rows file in its manifest
 * (`behaviorHints.homeConfigUrl`), so with that addon the rows are the ones made for its catalog. Without one, the
 * build's own address is used, which is empty in the Play Store build: Home then shows the addons' catalogs.
 */
@Singleton
class HomeConfigUrlProvider @Inject constructor(
    private val addonRepository: AddonRepository
) {
    suspend fun current(): String {
        val url = pick(addonRepository.getInstalledAddons().first())
        lastKnown = url
        return url
    }

    companion object {
        /** The address in use at the last look, for code that cannot wait for the addon list. */
        @Volatile
        var lastKnown: String = BuildConfig.REMOTE_CONFIG_URL.trim()
            private set

        /** The first enabled addon that names a rows file wins; otherwise the build's address. */
        fun pick(addons: List<Addon>, fallback: String = BuildConfig.REMOTE_CONFIG_URL.trim()): String =
            addons.enabledAddons()
                .firstNotNullOfOrNull { addon ->
                    addon.behaviorHints?.homeConfigUrl?.trim()?.takeIf { it.startsWith("https://") }
                }
                ?: fallback
    }
}
