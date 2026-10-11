package com.kevin.hrtracker.domain

import com.kevin.hrtracker.data.entity.HrSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HrvCalculatorTest {

    private fun s(t: Long, rr: String?) = HrSample(sessionId = 1, timestampMs = t, bpm = 60, rrIntervalsMs = rr)

    private fun seq(vararg rr: Int) = rr.mapIndexed { i, v -> s(i * 1000L, v.toString()) }

    @Test
    fun `rmssd over consecutive beats`() {
        // Diffs: +20, -20, +20 → RMSSD 20
        val samples = listOf(s(0, "1000"), s(1_000, "1020"), s(2_000, "1000"), s(3_000, "1020"))
        assertEquals(20, HrvCalculator.rmssd(samples))
    }

    @Test
    fun `multiple rr values per sample are chained`() {
        val samples = listOf(s(0, "1000,1020"), s(1_000, "1000,1020"))
        assertEquals(20, HrvCalculator.rmssd(samples))
    }

    @Test
    fun `no diff across a pause gap`() {
        // Vor der Pause 800 ms, danach 1200 ms: ohne Reset gäbe es einen 400-ms-Sprung
        val samples = listOf(
            s(0, "800"), s(1_000, "810"),
            s(120_000, "1200"), s(121_000, "1210")
        )
        assertEquals(10, HrvCalculator.rmssd(samples))
    }

    @Test
    fun `out of range beat excludes both adjacent diffs`() {
        val r = HrvCalculator.analyze(seq(1000, 1010, 5000, 1000, 1010))!!
        assertEquals(10, r.rmssd)
        assertEquals(4, r.validBeats)
        assertEquals(20.0, r.artefactPct, 1e-9)
        assertNull(HrvCalculator.analyze(seq(1000, 5000, 1010)))
    }

    @Test
    fun `rmssd is rounded not truncated`() {
        assertEquals(11, HrvCalculator.analyze(seq(1000, 1010, 999))!!.rmssd)
    }

    @Test
    fun `double detected beat is flagged and its diffs excluded`() {
        val rr = listOf(800, 820, 800, 820, 800, 400, 420, 800, 820, 800, 820, 800)
        val r = HrvCalculator.analyze(seq(*rr.toIntArray()))!!
        assertEquals(20, r.rmssd)
        assertEquals(10, r.validBeats)
        assertEquals(100.0 * 2 / 12, r.artefactPct, 1e-9)
        val flagged = HrvCalculator.flagArtefacts(rr)
        assertEquals(listOf(5, 6), flagged.indices.filter { flagged[it] })
    }

    @Test
    fun `hrv discard drops first active minute`() {
        val samples = (0 until 300).map { t ->
            val rr = if (t % 2 == 0) 1000 else if (t < 60) 1100 else 1020
            s(t * 1000L, rr.toString())
        }
        val all = HrvCalculator.analyze(samples)!!
        assertEquals(48, all.rmssd)
        assertEquals(299, all.analysedSeconds)
        val r = HrvCalculator.analyze(samples, HrvQuality.STABILISATION_MS)!!
        assertEquals(20, r.rmssd)
        assertEquals(240, r.validBeats)
        assertEquals(0.0, r.artefactPct, 0.0)
        assertEquals(239, r.analysedSeconds)
    }

    @Test
    fun `reading shorter than discard is analysed whole and too short`() {
        val samples = (0 until 30).map { t -> s(t * 1000L, if (t % 2 == 0) "1000" else "1020") }
        val r = HrvCalculator.analyze(samples, HrvQuality.STABILISATION_MS)!!
        assertEquals(20, r.rmssd)
        assertEquals(29, r.analysedSeconds)
        assertEquals(HrvUnreliableReason.TOO_SHORT, HrvQuality.reason(r))
    }

    // Synthetisch (kein echtes Session-Datum): Artefakt-Muster 799,401,280 am Anfang (Doppeldetektion)
    // plus ein großer physiologischer Sprung 842→1104→1085 (Idx 150ff), der NICHT geflaggt werden darf.
    private fun synthRr(i: Int): Int = when {
        i == 0 -> 810; i == 1 -> 825; i == 2 -> 790; i == 3 -> 799; i == 4 -> 401; i == 5 -> 280
        i == 150 -> 842; i == 151 -> 1104; i == 152 -> 1085
        i in 153..160 -> 1090 + ((i * 7) % 5 - 2) * 3
        else -> 805 + (if (i % 2 == 0) 15 else -15) + ((i * 7) % 5 - 2) * 3
    }

    private fun synth() = (0 until 300).map { s(it * 1000L, synthRr(it).toString()) }

    @Test
    fun `synthetic session flags only the double detection not the physiological jump`() {
        val rr = (0 until 300).map { synthRr(it) }
        val flagged = HrvCalculator.flagArtefacts(rr)
        assertEquals(listOf(4, 5), flagged.indices.filter { flagged[it] })
        assertEquals(listOf(401, 280), flagged.indices.filter { flagged[it] }.map { rr[it] })

        val r = HrvCalculator.analyze(synth())!!
        assertEquals(38, r.rmssd)
        assertEquals(298, r.validBeats)
        assertEquals(100.0 * 2 / 300, r.artefactPct, 1e-9)
        assertEquals(299, r.analysedSeconds)

        val d = HrvCalculator.analyze(synth(), HrvQuality.STABILISATION_MS)!!
        assertEquals(40, d.rmssd)
        assertEquals(240, d.validBeats)
        assertEquals(0.0, d.artefactPct, 0.0)
        assertEquals(239, d.analysedSeconds)
    }

    @Test
    fun `quality gates`() {
        assertNull(HrvQuality.reason(180, 180, 5.0))
        assertEquals(HrvUnreliableReason.TOO_SHORT, HrvQuality.reason(179, 500, 0.0))
        assertEquals(HrvUnreliableReason.TOO_FEW_BEATS, HrvQuality.reason(300, 179, 0.0))
        assertEquals(HrvUnreliableReason.TOO_MANY_ARTEFACTS, HrvQuality.reason(300, 300, 5.01))
        assertEquals(HrvUnreliableReason.TOO_SHORT, HrvQuality.reason(100, 100, 50.0))
    }

    @Test
    fun `analysedSeconds helper`() {
        assertEquals(240, HrvQuality.analysedSeconds(300_259, 60_000))
        assertEquals(59, HrvQuality.analysedSeconds(59_000, 60_000))
        assertEquals(180, HrvQuality.analysedSeconds(240_000, 60_000))
    }

    @Test
    fun `no diff across a gap when first sample after gap has no rr`() {
        // Diffs nur 1000→1010 und 1200→1210 (je 10); Sprung 1010→1200 über die Lücke entfällt
        val samples = listOf(
            s(0, "1000"), s(1_000, "1010"),
            s(120_000, null), s(121_000, "1200"), s(122_000, "1210")
        )
        assertEquals(10, HrvCalculator.rmssd(samples))
    }

    @Test
    fun `null without rr data`() {
        assertNull(HrvCalculator.rmssd(listOf(s(0, null), s(1_000, null))))
        assertNull(HrvCalculator.rmssd(listOf(s(0, "1000"))))
    }
}
