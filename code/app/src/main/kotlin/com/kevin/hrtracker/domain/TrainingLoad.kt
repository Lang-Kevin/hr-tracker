package com.kevin.hrtracker.domain

import java.time.LocalDate

enum class LoadMetric { TRIMP, SRPE }

enum class AcwrZone { LOW, OPTIMAL, CAUTION, HIGH }

data class LoadDay(
    val date: LocalDate,
    /** Summe der Last der letzten 7 Tage inkl. [date]. */
    val acute: Double,
    /** Ø-Wochenlast der letzten 28 Tage inkl. [date] (Summe / 4). */
    val chronic: Double,
    /** acute / chronic; null, solange weniger als 28 Tage Historie vorliegen oder chronic = 0. */
    val acwr: Double?
)

/**
 * Trainingslast-Verlauf (rollende Summen, gekoppeltes ACWR nach Gabbett).
 * Akut = 7-Tage-Summe, chronisch = 28-Tage-Summe / 4 (gleiche Einheit: Last pro Woche).
 */
object TrainingLoad {

    const val ACUTE_DAYS = 7
    const val CHRONIC_DAYS = 28

    /** Session-RPE nach Foster: RPE (0–10) × aktive Minuten. */
    fun srpe(rpe: Int, activeMs: Long): Double = rpe.coerceIn(0, 10) * activeMs / 60_000.0

    fun zoneOf(acwr: Double): AcwrZone = when {
        acwr < 0.8 -> AcwrZone.LOW
        acwr <= 1.3 -> AcwrZone.OPTIMAL
        acwr <= 1.5 -> AcwrZone.CAUTION
        else -> AcwrZone.HIGH
    }

    /**
     * @param loads Last pro Session mit Datum (mehrere pro Tag erlaubt).
     * @param firstDay Tag der ersten Session überhaupt (auch außerhalb [loads]) — bestimmt,
     *   ab wann genug Historie für ein ACWR vorliegt.
     * @return ein Eintrag pro Tag von today − days + 1 bis today.
     */
    fun series(loads: List<Pair<LocalDate, Double>>, firstDay: LocalDate?, today: LocalDate, days: Int): List<LoadDay> {
        val perDay = loads.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
        fun sumBack(end: LocalDate, n: Int): Double =
            (0 until n).sumOf { perDay[end.minusDays(it.toLong())] ?: 0.0 }

        return (days - 1 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            val acute = sumBack(d, ACUTE_DAYS)
            val chronic = sumBack(d, CHRONIC_DAYS) / (CHRONIC_DAYS / ACUTE_DAYS)
            val enoughHistory = firstDay != null && !firstDay.isAfter(d.minusDays((CHRONIC_DAYS - 1).toLong()))
            LoadDay(
                date = d,
                acute = acute,
                chronic = chronic,
                acwr = if (enoughHistory && chronic > 0.0) acute / chronic else null
            )
        }
    }
}
