package com.kevin.hrtracker.ui.shared

import kotlin.math.ceil
import kotlin.math.floor

/** "Schöne" Schrittweiten der Zeitachse in Sekunden. */
private val NICE_STEPS_SEC = intArrayOf(5, 10, 15, 30, 60, 120, 300, 600, 900, 1800, 3600, 7200)

/**
 * Tick-Positionen (Sekunden, Vielfache der Schrittweite) im Bereich [fromSec], [toSec] (inklusive).
 * Wählt die kleinste "schöne" Schrittweite, bei der höchstens [maxTicks] Ticks entstehen.
 * Reines Kotlin (keine Android/Compose-Abhängigkeiten) -> auf der JVM testbar.
 */
fun timeTicks(fromSec: Float, toSec: Float, maxTicks: Int = 5): List<Float> {
    if (fromSec.isNaN() || toSec.isNaN() || toSec <= fromSec) return emptyList()
    val limit = maxTicks.coerceAtLeast(1)
    val steps = NICE_STEPS_SEC
    var chosen = steps.last()
    for (s in steps) {
        if (countTicks(fromSec, toSec, s) <= limit) { chosen = s; break }
    }
    return ticksFor(fromSec, toSec, chosen).let { if (it.size > limit) it.take(limit) else it }
}

private fun firstMultiple(fromSec: Float, step: Int): Long = ceil(fromSec / step).toLong()
private fun lastMultiple(toSec: Float, step: Int): Long = floor(toSec / step).toLong()

private fun countTicks(fromSec: Float, toSec: Float, step: Int): Int {
    val n = lastMultiple(toSec, step) - firstMultiple(fromSec, step) + 1
    return n.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}

private fun ticksFor(fromSec: Float, toSec: Float, step: Int): List<Float> {
    val a = firstMultiple(fromSec, step)
    val b = lastMultiple(toSec, step)
    if (b < a) return emptyList()
    return (a..b).map { (it * step).toFloat() }
}

/** "m:ss" unter einer Stunde (z.B. "0:30", "12:05"), ab einer Stunde "h:mm:ss". */
fun formatTickLabel(sec: Float): String {
    val total = if (sec.isNaN() || sec < 0f) 0L else Math.round(sec).toLong()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
