package com.kevin.hrtracker.domain

data class UserSettings(
    val age: Int = 30,
    val manualMaxHr: Int? = null,
    val restingHr: Int? = null,
    val targetZone: Int = 2
) {
    val maxHrUsed: Int get() = manualMaxHr ?: HrZoneCalculator.tanakaMaxHr(age)
}
