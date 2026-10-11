package com.kevin.hrtracker.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionRepositoryTest {
    @Test
    fun newSession_persistsPosture() {
        val s = SessionRepository.newSession("HRV RMSSD", 1L, 190, 60, "[]", "SITTING")
        assertEquals("SITTING", s.posture)
        assertNull(SessionRepository.newSession("Run", 1L, 190, 60, "[]", null).posture)
    }
}
