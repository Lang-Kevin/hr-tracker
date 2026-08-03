package com.kevin.hrtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetVariantTest {

    @Test
    fun `minimal zeigt nur bpm`() {
        assertEquals(
            "142 BPM",
            widgetNotificationText(WidgetVariant.MINIMAL, 142, 3, "00:12:04")
        )
    }

    @Test
    fun `standard zeigt bpm zone und dauer`() {
        assertEquals(
            "142 BPM  •  Zone 3  •  00:12:04",
            widgetNotificationText(WidgetVariant.STANDARD, 142, 3, "00:12:04")
        )
    }

    @Test
    fun `zone stellt die zone voran`() {
        assertEquals(
            "Zone 3  •  142 BPM",
            widgetNotificationText(WidgetVariant.ZONE, 142, 3, "00:12:04")
        )
    }

    @Test
    fun `timer stellt die dauer voran`() {
        assertEquals(
            "00:12:04  •  142 BPM",
            widgetNotificationText(WidgetVariant.TIMER, 142, 3, "00:12:04")
        )
    }

    @Test
    fun `ohne bpm steht ein platzhalter`() {
        assertEquals(
            "-- BPM",
            widgetNotificationText(WidgetVariant.MINIMAL, null, null, "00:00:00")
        )
    }

    @Test
    fun `ohne zone entfaellt das zonen-segment`() {
        assertEquals(
            "142 BPM  •  00:12:04",
            widgetNotificationText(WidgetVariant.STANDARD, 142, null, "00:12:04")
        )
        assertEquals(
            "142 BPM",
            widgetNotificationText(WidgetVariant.ZONE, 142, null, "00:12:04")
        )
    }
}
