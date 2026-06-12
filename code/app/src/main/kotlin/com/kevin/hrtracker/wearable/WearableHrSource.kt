package com.kevin.hrtracker.wearable

import com.kevin.hrtracker.ble.ParsedHr
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearableHrSource @Inject constructor() {
    private val _hrSamples = MutableSharedFlow<ParsedHr>(replay = 0, extraBufferCapacity = 64)
    val hrSamples: SharedFlow<ParsedHr> = _hrSamples.asSharedFlow()

    fun tryEmit(parsed: ParsedHr) {
        _hrSamples.tryEmit(parsed)
    }
}
