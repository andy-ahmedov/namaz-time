package ru.namaztime.tv.presentation

import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import java.time.Instant

private const val RETENTION_SLOT_SECONDS = 10L * 60L
internal val SCREEN_RETENTION_SHIFT_BUDGET = 2.dp

private val retentionOffsets = listOf(
    DpOffset(0.dp, 0.dp),
    DpOffset(SCREEN_RETENTION_SHIFT_BUDGET, 0.dp),
    DpOffset(SCREEN_RETENTION_SHIFT_BUDGET, SCREEN_RETENTION_SHIFT_BUDGET),
    DpOffset(-SCREEN_RETENTION_SHIFT_BUDGET, SCREEN_RETENTION_SHIFT_BUDGET),
    DpOffset(-SCREEN_RETENTION_SHIFT_BUDGET, -SCREEN_RETENTION_SHIFT_BUDGET),
    DpOffset(SCREEN_RETENTION_SHIFT_BUDGET, -SCREEN_RETENTION_SHIFT_BUDGET),
)

internal fun screenRetentionOffsetAt(instant: Instant): DpOffset {
    val slot = Math.floorDiv(instant.epochSecond, RETENTION_SLOT_SECONDS)
    return retentionOffsets[Math.floorMod(slot, retentionOffsets.size.toLong()).toInt()]
}
