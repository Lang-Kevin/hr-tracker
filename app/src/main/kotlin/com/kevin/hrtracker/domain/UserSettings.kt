package com.kevin.hrtracker.domain

data class UserSettings(
    val age: Int = 30,
    val manualMaxHr: Int? = null,
    val restingHr: Int? = null
) {
    val maxHrUsed: Int get() = manualMaxHr ?: HrZoneCalculator.tanakaMaxHr(age)
}
