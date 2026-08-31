package ru.namaztime.tv.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NextPrayerWatermarkGeometryTest {
    @Test
    fun archUsesTheApprovedNormalizedCardBounds() {
        val geometry = NEXT_PRAYER_WATERMARK_GEOMETRY

        assertTrue(geometry.archLeft in 0.04f..0.06f)
        assertTrue(geometry.archRight in 0.27f..0.29f)
        assertTrue(geometry.archWidth in 0.22f..0.26f)
        assertTrue(geometry.apexX in 0.15f..0.17f)
        assertTrue(geometry.apexY in 0.18f..0.23f)
        assertTrue(geometry.shoulderY in 0.37f..0.42f)
        assertEquals(1f, geometry.bottomY, 0f)
        assertEquals(
            (geometry.archLeft + geometry.archRight) / 2f,
            geometry.apexX,
            0.0001f,
        )
    }
}
