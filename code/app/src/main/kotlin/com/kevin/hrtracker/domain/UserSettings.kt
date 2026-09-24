package com.kevin.hrtracker.domain

data class UserSettings(
    val age: Int = 30,
    val manualMaxHr: Int? = null,
    val restingHr: Int? = null,
    val targetZone: Int = 2,
    val customZones: List<ZoneBounds>? = null,
    val zoneModel: ZoneModel = ZoneModel.HR_MAX,
    val chartDynamicScale: Boolean = true,
    val widgetVariant: WidgetVariant = WidgetVariant.STANDARD,
    val weightKg: Int? = null,
    val sex: Sex? = null
) {
    val maxHrUsed: Int get() = manualMaxHr ?: HrZoneCalculator.tanakaMaxHr(age)
    val effectiveZones: List<ZoneBounds>
        get() = customZones ?: HrZoneCalculator.calculateZones(maxHrUsed, restingHr, zoneModel)
}
