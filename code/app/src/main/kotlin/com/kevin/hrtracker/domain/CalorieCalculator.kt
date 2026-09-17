package com.kevin.hrtracker.domain

import kotlin.math.roundToInt

enum class Sex { MALE, FEMALE }

/**
 * Keytel et al. (2005): Schätzt den Energieverbrauch aus der mittleren Herzfrequenz.
 * Liefert kJ/min, daher /4.184 für kcal/min.
 */
object CalorieCalculator {

    // ponytail: Keytel nutzt nur die Durchschnitts-HF, nicht den Sample-Verlauf.
    // Sample-weise Integration lohnt erst, wenn Intervall-Sessions sichtbar danebenliegen.
    fun estimateKcal(
        avgBpm: Int,
        durationMs: Long,
        weightKg: Int?,
        age: Int,
        sex: Sex?
    ): Int? {
        if (weightKg == null || sex == null) return null
        if (durationMs <= 0L || avgBpm <= 0) return null

        val hr = avgBpm.toDouble()
        val kg = weightKg.toDouble()
        val yrs = age.toDouble()

        val kJPerMin = when (sex) {
            Sex.MALE   -> -55.0969 + 0.6309 * hr + 0.1988 * kg + 0.2017 * yrs
            Sex.FEMALE -> -20.4022 + 0.4472 * hr - 0.1263 * kg + 0.0740 * yrs
        }

        val minutes = durationMs / 60_000.0
        return (kJPerMin / 4.184 * minutes).roundToInt().coerceAtLeast(0)
    }
}
