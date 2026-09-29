package com.kevin.hrtracker.ble

import org.junit.Assert.assertEquals
import org.junit.Test

class ReconnectDelayTest {

    @Test
    fun reconnectDelayMs_attempt0_returns3000() {
        assertEquals(3_000L, HrBleManager.reconnectDelayMs(0))
    }

    @Test
    fun reconnectDelayMs_attempt1_returns5000() {
        assertEquals(5_000L, HrBleManager.reconnectDelayMs(1))
    }

    @Test
    fun reconnectDelayMs_attempt2_returns10000() {
        assertEquals(10_000L, HrBleManager.reconnectDelayMs(2))
    }

    @Test
    fun reconnectDelayMs_attempt3_returns30000() {
        assertEquals(30_000L, HrBleManager.reconnectDelayMs(3))
    }

    @Test
    fun reconnectDelayMs_attempt4_caps30000() {
        assertEquals(30_000L, HrBleManager.reconnectDelayMs(4))
    }

    @Test
    fun reconnectDelayMs_attempt10_caps30000() {
        assertEquals(30_000L, HrBleManager.reconnectDelayMs(10))
    }

    @Test
    fun reconnectDelayMs_attempt1000_caps30000() {
        assertEquals(30_000L, HrBleManager.reconnectDelayMs(1000))
    }
}
