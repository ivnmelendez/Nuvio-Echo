package com.nuvio.tv.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.bins.BinsRepository
import com.nuvio.tv.core.bins.BinsState
import com.nuvio.tv.core.bins.binKey
import com.nuvio.tv.domain.model.MetaPreview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Watch later for Home's poster options: a held title is saved or taken out with one press. */
@HiltViewModel
class HomePosterBinsViewModel @Inject constructor(
    private val binsRepository: BinsRepository
) : ViewModel() {
    val state: StateFlow<BinsState> = binsRepository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BinsState())

    /** Whether [item] is in Watch later (in any bin). */
    fun isSaved(state: BinsState, item: MetaPreview): Boolean {
        val key = binKey(item.id, item.apiType)
        return state.bins.any { it.contains(key) }
    }

    fun toggle(item: MetaPreview, addonBaseUrl: String?, saved: Boolean) {
        viewModelScope.launch {
            runCatching {
                if (saved) binsRepository.removeFromWatchLater(item.id, item.apiType)
                else binsRepository.addToWatchLater(item.toLibraryEntryInput(addonBaseUrl))
            }
        }
    }
}
