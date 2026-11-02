package com.nuvio.tv.core.addons

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** The refresh button in addon settings: tells screens holding catalogs to request them again. */
@Singleton
class ManualAddonRefresh @Inject constructor() {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    fun request() {
        _events.tryEmit(Unit)
    }
}
