package com.example.namaztime.tv.presentation

import com.example.namaztime.tv.repository.LocalPrayerDay
import com.example.namaztime.tv.repository.LocalPrayerSchedule
import com.example.namaztime.tv.repository.LocalSnapshotDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Test

class MainPrayerDisplayStateTest {
    @Test
    fun explicitlySelectedDateControlsDisplayedPrayerRow() {
        val state = schedule().toPrayerDisplayUiState(selectedLocalDate = "2026-08-20")

        assertEquals("20 августа 2026", state.dateLabel)
        assertEquals("03:14", state.rows.first().adhan)
    }

    @Test
    fun sourceStateFailsConservativelyWithoutAuthenticityVerification() {
        val base = schedule()
        val cases = listOf(
            base to "ТЕСТОВЫЕ ДАННЫЕ",
            base.copy(
                sourceKind = "calculation_profile",
                diagnostics = base.diagnostics?.copy(dataClassification = "production"),
            ) to "РАСЧЁТНОЕ — НЕ ОФИЦИАЛЬНО",
            base.copy(
                diagnostics = base.diagnostics?.copy(dataClassification = "production"),
            ) to "ИСТОЧНИК НЕ ПРОВЕРЕН",
            base.copy(
                diagnostics = base.diagnostics?.copy(approvalStatus = "pending"),
            ) to "НЕ ОДОБРЕНО",
        )

        cases.forEach { (schedule, expectedLabel) ->
            assertEquals(
                expectedLabel,
                schedule.toPrayerDisplayUiState("2026-08-20").sourceLabel,
            )
        }
    }

    private fun schedule() = LocalPrayerSchedule(
        snapshotId = "synthetic-ulsk-demo-2026-08-v1",
        mosqueId = "mosque-demo-ulsk",
        mosqueName = "Синтетическая мечеть",
        timezoneId = "Europe/Ulyanovsk",
        sourceKind = "manual_import",
        authorityName = "Synthetic test fixture",
        coverageFrom = "2026-08-19",
        coverageTo = "2026-08-20",
        diagnostics = LocalSnapshotDiagnostics(
            dataClassification = "synthetic",
            rawSha256 = "1".repeat(64),
            parserVersion = "synthetic/1",
            approvalId = "approval-test",
            approvalStatus = "approved",
            signingKeyId = "test-placeholder-key",
        ),
        days = listOf(
            LocalPrayerDay("2026-08-19", "03:12", "05:21", "12:08", "16:47", "18:53", "21:01"),
            LocalPrayerDay("2026-08-20", "03:14", "05:23", "12:08", "16:45", "18:51", "20:58"),
        ),
    )
}
