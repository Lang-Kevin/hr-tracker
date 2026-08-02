package com.kevin.hrtracker.wearable

import com.kevin.hrtracker.ble.ParsedHr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearableHrSource @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _hrSamples = MutableSharedFlow<ParsedHr>(replay = 0, extraBufferCapacity = 64)
    val hrSamples: SharedFlow<ParsedHr> = _hrSamples.asSharedFlow()

    // ponytail: siehe HrBleManager — replay=0 bleibt, letzter Wert separat.
    val lastHr: StateFlow<ParsedHr?> = _hrSamples.stateIn(scope, SharingStarted.Eagerly, null)

    fun tryEmit(parsed: ParsedHr) {
        _hrSamples.tryEmit(parsed)
    }
}
