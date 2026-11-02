package com.nuvio.tv.core.addons

import android.content.Context
import com.nuvio.tv.LocaleCache
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.repository.AddonRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * What the installed addons ask of the app itself. For now one thing: a language (`behaviorHints.preferredLanguage`),
 * used for the app and for TMDB's answers while that addon is on. The last answer is kept on the device, so the app
 * opens in that language before the addon list has been read.
 */
@Singleton
class AddonProfile @Inject constructor(
    @ApplicationContext context: Context,
    private val addonRepository: AddonRepository
) {
    private val prefs = context.getSharedPreferences("addon_profile", Context.MODE_PRIVATE)
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when the language an addon asks for changes, so the screen can be rebuilt in it. */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    init {
        LocaleCache.addonTag = prefs.getString(KEY_LANGUAGE, null)?.takeIf { it.isNotBlank() }
    }

    fun start(scope: CoroutineScope) {
        scope.launch {
            addonRepository.getInstalledAddons().collect { addons ->
                // The list starts empty before it is read; that is not "no addon asks for anything".
                if (addons.isEmpty()) return@collect
                val language = pickLanguage(addons)
                if (language != LocaleCache.addonTag) {
                    prefs.edit().putString(KEY_LANGUAGE, language.orEmpty()).apply()
                    LocaleCache.addonTag = language
                    _changes.tryEmit(Unit)
                }
            }
        }
    }

    companion object {
        private const val KEY_LANGUAGE = "language"

        /** The first addon that is on and names a language decides. */
        fun pickLanguage(addons: List<Addon>): String? =
            addons.filter { it.enabled }
                .firstNotNullOfOrNull { it.behaviorHints?.preferredLanguage?.trim()?.takeIf { tag -> tag.isNotEmpty() } }
    }
}
