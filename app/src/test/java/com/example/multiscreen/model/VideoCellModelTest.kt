package com.example.multiscreen.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoCellModelTest {

    @Test
    fun testFormatTimeMs_zero() {
        assertEquals("00:00", formatTimeMs(0L))
    }

    @Test
    fun testFormatTimeMs_secondsAndMinutes() {
        assertEquals("01:05", formatTimeMs(65_000L))
        assertEquals("12:34", formatTimeMs((12 * 60 + 34) * 1000L))
    }

    @Test
    fun testFormatTimeMs_hours() {
        assertEquals("01:02:03", formatTimeMs(((1 * 3600) + (2 * 60) + 3) * 1000L))
    }

    @Test
    fun testFormatTimeMs_negative() {
        assertEquals("00:00", formatTimeMs(-5000L))
    }
}
