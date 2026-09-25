package com.kevin.hrtracker.domain

import com.kevin.hrtracker.data.entity.HrSample
import kotlin.math.sqrt

object HrvCalculator {

    private val VALID_RR_MS = 300..2000

    /**
     * RMSSD aus den RR-Intervallen. Aufeinanderfolgende Samples sind aufeinanderfolgende
     * Schläge; über eine Sample-Lücke > MAX_GAP_MS (Pause, Dropout) hinweg wird nicht
     * differenziert, sonst entstünden künstliche Sprünge. 300–2000 ms filtert Ektopien/Rauschen.
     */
    fun rmssd(samples: List<HrSample>): Int? {
        var prevRr: Int? = null
        var prevTs: Long? = null
        var sumSq = 0.0
        var n = 0
        for (sample in samples.sortedBy { it.timestampMs }) {
            if (prevTs != null && sample.timestampMs - prevTs > SampleIntervals.MAX_GAP_MS) prevRr = null
            prevTs = sample.timestampMs
            val rrs = sample.rrIntervalsMs
                ?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.filter { it in VALID_RR_MS }
                ?: continue
            for (rr in rrs) {
                prevRr?.let { p ->
                    val d = (rr - p).toDouble()
                    sumSq += d * d
                    n++
                }
                prevRr = rr
            }
        }
        return if (n == 0) null else sqrt(sumSq / n).toInt()
    }
}
