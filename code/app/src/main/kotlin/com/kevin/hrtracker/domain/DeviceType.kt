package com.kevin.hrtracker.domain

enum class DeviceType { SMARTWATCH, CHEST_STRAP, UNKNOWN }

fun guessDeviceType(name: String?): DeviceType {
    if (name == null) return DeviceType.UNKNOWN
    val n = name.lowercase()
    return when {
        listOf(
            "garmin", "polar", "suunto", "coros", "galaxy watch", "watch",
            "amazfit", "forerunner", "instinct", "vantage", "fenix", "epix", "band"
        ).any { n.contains(it) } -> DeviceType.SMARTWATCH

        listOf("hr", "hrm", "chest", "strap", "tickr", "wahoo", "moofit")
            .any { n.contains(it) } -> DeviceType.CHEST_STRAP

        else -> DeviceType.UNKNOWN
    }
}
