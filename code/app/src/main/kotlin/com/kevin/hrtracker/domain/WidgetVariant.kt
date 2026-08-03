package com.kevin.hrtracker.domain

/**
 * Anzeige-Preset für PiP-Fenster und Foreground-Notification.
 * MINIMAL = nur BPM, STANDARD = heutiges Verhalten, ZONE = Zone dominant, TIMER = Dauer dominant.
 */
enum class WidgetVariant { MINIMAL, STANDARD, ZONE, TIMER }

private const val SEPARATOR = "  •  "

/**
 * Baut den Notification-Text zur gewählten Variante.
 * Rein und Android-frei, damit unit-testbar.
 */
fun widgetNotificationText(
    variant: WidgetVariant,
    bpm: Int?,
    zone: Int?,
    elapsed: String
): String {
    val bpmText = "${bpm ?: "--"} BPM"
    val zoneText = zone?.let { "Zone $it" }
    val parts = when (variant) {
        WidgetVariant.MINIMAL -> listOf(bpmText)
        WidgetVariant.STANDARD -> listOfNotNull(bpmText, zoneText, elapsed)
        WidgetVariant.ZONE -> listOfNotNull(zoneText, bpmText)
        WidgetVariant.TIMER -> listOf(elapsed, bpmText)
    }
    return parts.joinToString(SEPARATOR)
}
