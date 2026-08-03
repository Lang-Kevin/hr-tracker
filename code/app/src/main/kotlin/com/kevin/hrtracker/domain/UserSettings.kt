package com.kevin.hrtracker.domain

data class UserSettings(
    val age: Int = 30,
    val manualMaxHr: Int? = null,
    val restingHr: Int? = null,
    val targetZone: Int = 2,
    val hrSource: HrSource = HrSource.BLE,
    val customZones: List<ZoneBounds>? = null,
    val chartDynamicScale: Boolean = true,
    val widgetVariant: WidgetVariant = WidgetVariant.STANDARD
) {
    val maxHrUsed: Int get() = manualMaxHr ?: HrZoneCalculator.tanakaMaxHr(age)
    val effectiveZones: List<ZoneBounds>
        get() = customZones ?: HrZoneCalculator.calculateZones(maxHrUsed, restingHr)
}
