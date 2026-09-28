package com.kevin.hrtracker.ui.shared

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs

/**
 * Zoom/Pan-Gesten für X-Achsen-Diagramme.
 * - Zwei+ Finger: [onZoom] (X-Skalierung, Schwerpunkt in px) und [onPan] (dx in px).
 * - Ein Finger: nur wenn [panEnabled]; erst nach horizontalem Touch-Slop wird konsumiert,
 *   vertikale Bewegung wird nicht konsumiert (Eltern-verticalScroll bleibt nutzbar).
 * - Doppeltipp: [onDoubleTap] (x in px).
 */
fun Modifier.chartZoomPan(
    panEnabled: () -> Boolean,
    onZoom: (factor: Float, anchorX: Float) -> Unit,
    onPan: (dxPx: Float) -> Unit,
    onDoubleTap: (x: Float) -> Unit
): Modifier = composed {
    val panEnabledState by rememberUpdatedState(panEnabled)
    val onZoomState by rememberUpdatedState(onZoom)
    val onPanState by rememberUpdatedState(onPan)
    val onDoubleTapState by rememberUpdatedState(onDoubleTap)

    this
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var accX = 0f
                var accY = 0f
                var panning = false
                var wasMulti = false
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.none { it.pressed }) break
                    val pressedCount = event.changes.count { it.pressed }
                    if (pressedCount >= 2) {
                        val zoom = event.calculateZoom()
                        val centroid = event.calculateCentroid()
                        val pan = event.calculatePan()
                        if (zoom != 1f && centroid != Offset.Unspecified) onZoomState(zoom, centroid.x)
                        if (pan.x != 0f) onPanState(pan.x)
                        event.changes.forEach { if (it.positionChange() != Offset.Zero) it.consume() }
                        panning = true
                        wasMulti = true
                    } else if (panEnabledState()) {
                        if (wasMulti) {
                            wasMulti = false
                            accX = 0f
                            accY = 0f
                        }
                        val change = event.changes.firstOrNull { it.pressed } ?: break
                        val delta = change.positionChange()
                        if (panning) {
                            if (delta.x != 0f) onPanState(delta.x)
                            change.consume()
                        } else {
                            accX += delta.x
                            accY += delta.y
                            val slop = viewConfiguration.touchSlop
                            if (abs(accX) > slop && abs(accX) > abs(accY)) {
                                panning = true
                                change.consume()
                            } else if (abs(accY) > slop) {
                                break // vertikal: Geste nicht behandeln
                            }
                        }
                    } else {
                        // Pan nicht erlaubt: Ein-Finger-Geste ignorieren (nicht konsumieren, damit der
                        // Eltern-verticalScroll nutzbar bleibt), auf weiteren Finger warten.
                        if (wasMulti) {
                            wasMulti = false
                            panning = false
                            accX = 0f
                            accY = 0f
                        }
                    }
                }
            }
        }
        .pointerInput(Unit) {
            detectTapGestures(onDoubleTap = { onDoubleTapState(it.x) })
        }
}
