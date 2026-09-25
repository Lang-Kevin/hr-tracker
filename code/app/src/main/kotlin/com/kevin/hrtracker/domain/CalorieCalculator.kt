package com.kevin.hrtracker.domain

import com.kevin.hrtracker.data.entity.HrSample
import kotlin.math.roundToInt

enum class Sex { MALE, FEMALE }

/**
 * Aktivkalorien: Keytel et al. (2005) liefert den Brutto-Energieumsatz in kJ/min
 * (daher /4.184 für kcal/min). Davon wird der Grundumsatz nach Schofield (WHO/FAO 1985)
 * abgezogen, der nur Gewicht, Alter und Geschlecht braucht.
 *
 * Integration sample-weise: jedes Intervall zwischen zwei Samples zählt mit der BPM
 * des ersten Samples. Intervalle > MAX_GAP_MS (Pause, BLE-Dropout) zählen nicht,
 * damit Pausenzeit nicht mit Trainingspuls hochgerechnet wird (siehe [SampleIntervals]).
 */
object CalorieCalculator {

    fun estimateActiveKcal(
        samples: List<HrSample>,
        weightKg: Int?,
        age: Int,
        sex: Sex?
    ): Int? {
        if (weightKg == null || sex == null) return null
        if (samples.size < 2) return null

        val bmrPerMin = bmrKcalPerDay(weightKg, age, sex) / 1440.0

        val kcal = SampleIntervals.active(samples).sumOf { (a, dtMs) ->
            if (a.bpm <= 0) 0.0
            else {
                // Pro Intervall auf >= 0 clampen: niedrige Pulse ergeben mit Keytel sonst negative Werte.
                val activePerMin = (grossKcalPerMin(a.bpm, weightKg, age, sex) - bmrPerMin).coerceAtLeast(0.0)
                activePerMin * dtMs / 60_000.0
            }
        }
        return kcal.roundToInt()
    }

    internal fun grossKcalPerMin(bpm: Int, weightKg: Int, age: Int, sex: Sex): Double {
        val kJPerMin = when (sex) {
            Sex.MALE   -> -55.0969 + 0.6309 * bpm + 0.1988 * weightKg + 0.2017 * age
            Sex.FEMALE -> -20.4022 + 0.4472 * bpm - 0.1263 * weightKg + 0.0740 * age
        }
        return kJPerMin / 4.184
    }

    /** Schofield (WHO/FAO 1985), kcal/Tag. */
    internal fun bmrKcalPerDay(weightKg: Int, age: Int, sex: Sex): Double {
        val w = weightKg.toDouble()
        return when (sex) {
            Sex.MALE -> when {
                age < 18 -> 17.686 * w + 658.2
                age < 30 -> 15.057 * w + 692.2
                age < 60 -> 11.472 * w + 873.1
                else     -> 11.711 * w + 587.7
            }
            Sex.FEMALE -> when {
                age < 18 -> 13.384 * w + 692.6
                age < 30 -> 14.818 * w + 486.6
                age < 60 -> 8.126 * w + 845.6
                else     -> 9.082 * w + 658.5
            }
        }
    }
}
