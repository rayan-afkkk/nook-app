package com.nook.app.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class TimeFormatTest {
    private fun at(day: Int, hour: Int = 12) = Calendar.getInstance().apply {
        set(2026, Calendar.MARCH, day, hour, 0, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test fun `separators say Today and Yesterday`() {
        val now = at(10)
        assertEquals("Today", TimeFormat.separator(at(10, 8), now))
        assertEquals("Yesterday", TimeFormat.separator(at(9, 23), now))
    }

    @Test fun `durations are m_ss or h_mm_ss`() {
        assertEquals("0:07", TimeFormat.duration(7))
        assertEquals("12:05", TimeFormat.duration(725))
        assertEquals("1:00:01", TimeFormat.duration(3601))
    }

    @Test fun `bytes are human readable`() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("2.0 MB", formatBytes(2L * 1024 * 1024))
    }
}
