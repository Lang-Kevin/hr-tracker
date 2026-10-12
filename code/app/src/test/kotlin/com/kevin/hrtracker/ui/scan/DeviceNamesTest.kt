package com.kevin.hrtracker.ui.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceNamesTest {
    private val a = "AA:BB:CC:DD:EE:01"

    @Test fun `no address is null`() =
        assertNull(resolveDeviceName(null, mapOf(a to "Saved"), mapOf(a to "Scan")))

    @Test fun `saved name wins`() =
        assertEquals("Saved", resolveDeviceName(a, mapOf(a to "Saved"), mapOf(a to "Scan")))

    @Test fun `falls back to scanned name`() =
        assertEquals("Scan", resolveDeviceName(a, emptyMap(), mapOf(a to "Scan")))

    @Test fun `blank or unknown is null`() {
        assertNull(resolveDeviceName(a, mapOf(a to " "), mapOf(a to null)))
        assertNull(resolveDeviceName(a, emptyMap(), emptyMap()))
    }
}
