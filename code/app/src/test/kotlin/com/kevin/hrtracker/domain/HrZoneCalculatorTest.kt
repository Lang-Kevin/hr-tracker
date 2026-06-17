package com.kevin.hrtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class HrZoneCalculatorTest {

    @Test
    fun `valid sequential zones produce no errors`() {
        val zones = listOf("100" to "120", "120" to "140", "140" to "160", "160" to "180", "180" to "200")
        assertEquals(listOf(null, null, null, null, null), HrZoneCalculator.validateZoneTexts(zones))
    }

    @Test
    fun `inverted zone reports error`() {
        val zones = listOf("120" to "100")
        assertEquals(listOf("Min muss kleiner als Max sein"), HrZoneCalculator.validateZoneTexts(zones))
    }

    @Test
    fun `overlapping zone reports error`() {
        val zones = listOf("100" to "120", "110" to "140")
        assertEquals(listOf(null, "Überlappt mit Z1"), HrZoneCalculator.validateZoneTexts(zones))
    }

    @Test
    fun `non numeric input reports error`() {
        val zones = listOf("abc" to "140")
        assertEquals(listOf("Ungültige Zahl"), HrZoneCalculator.validateZoneTexts(zones))
    }
}
