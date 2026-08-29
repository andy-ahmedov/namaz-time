package ru.namaztime.tv.presentation

import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenRetentionPolicyTest {
    @Test
    fun advancesThroughBoundedDeterministicCycle() {
        val expected = listOf(
            DpOffset(0.dp, 0.dp),
            DpOffset(2.dp, 0.dp),
            DpOffset(2.dp, 2.dp),
            DpOffset(-2.dp, 2.dp),
            DpOffset(-2.dp, -2.dp),
            DpOffset(2.dp, -2.dp),
        )

        expected.forEachIndexed { index, offset ->
            assertEquals(
                offset,
                screenRetentionOffsetAt(Instant.EPOCH.plusSeconds(index * 600L)),
            )
        }
        assertEquals(expected.first(), screenRetentionOffsetAt(Instant.EPOCH.plusSeconds(3_600L)))
    }

    @Test
    fun remainsStableWithinTenMinuteSlot() {
        val slotStart = Instant.EPOCH.plusSeconds(1_200L)

        assertEquals(
            screenRetentionOffsetAt(slotStart),
            screenRetentionOffsetAt(slotStart.plusSeconds(599L)),
        )
    }

    @Test
    fun neverMovesForegroundMoreThanTwoDpPerAxis() {
        repeat(24) { slot ->
            val offset = screenRetentionOffsetAt(Instant.EPOCH.plusSeconds(slot * 600L))

            assertTrue(kotlin.math.abs(offset.x.value) <= 2f)
            assertTrue(kotlin.math.abs(offset.y.value) <= 2f)
        }
    }
}
