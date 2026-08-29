package ru.namaztime.tv.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ru.namaztime.tv.data.local.NamazDatabase
import ru.namaztime.tv.data.local.SnapshotImporter
import ru.namaztime.tv.data.snapshot.SnapshotDecoder
import ru.namaztime.tv.data.snapshot.syntheticSnapshotBytes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class RoomPrayerScheduleRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: NamazDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, NamazDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun activeScheduleIsMappedOnlyFromRoomState() = runTest {
        SnapshotImporter(database).importAndActivate(
            SnapshotDecoder.decode(syntheticSnapshotBytes()),
        )

        val schedule = RoomPrayerScheduleRepository(database.snapshotDao())
            .observeActiveSchedule()
            .first { it != null }

        assertEquals("Синтетическая демонстрационная мечеть", schedule?.mosqueName)
        assertEquals("Europe/Ulyanovsk", schedule?.timezoneId)
        assertEquals("RU", schedule?.countryCode)
        assertEquals("Ульяновская область", schedule?.region)
        assertEquals("synthetic-fixture-v1", schedule?.sourceId)
        assertEquals(
            "Synthetic fixture scoped only to mosque-demo-ulsk",
            schedule?.geographicScope,
        )
        assertEquals("2026-08-19T09:55:00Z", schedule?.retrievedAt)
        assertEquals("Synthetic fixture created for tests", schedule?.licenseReference)
        assertEquals("Not real prayer times; do not display in a mosque", schedule?.attribution)
        assertEquals(listOf("2026-08-19", "2026-08-20", "2026-08-21"), schedule?.days?.map { it.localDate })
        assertEquals(listOf("synthetic"), schedule?.days?.first()?.flags)
        assertEquals("synthetic", schedule?.diagnostics?.dataClassification)
        assertEquals("1".repeat(64), schedule?.diagnostics?.rawSha256)
        assertEquals("synthetic/1", schedule?.diagnostics?.parserVersion)
        assertEquals("approval-synthetic-v1", schedule?.diagnostics?.approvalId)
        assertEquals("test-placeholder-key", schedule?.diagnostics?.signingKeyId)
        assertEquals("2026-08-19T10:00:00Z", schedule?.diagnostics?.generatedAt)
        assertEquals("test-suite", schedule?.diagnostics?.approvedBy)
        assertEquals("2026-08-19T09:59:00Z", schedule?.diagnostics?.approvedAt)
        assertEquals(
            listOf("iqamah-dhuhr-demo", "iqamah-fajr-demo"),
            schedule?.iqamahRules?.map { it.id },
        )
        assertEquals("fixed_time", schedule?.iqamahRules?.first()?.mode)
        assertEquals(127, schedule?.iqamahRules?.first()?.weekdaysMask)
        assertEquals(emptyList<LocalIqamahDateOverride>(), schedule?.iqamahDateOverrides)
        assertEquals(listOf("jumuah-demo-1"), schedule?.jumuahSessions?.map { it.id })
        assertEquals(listOf("campaign-demo"), schedule?.campaigns?.map { it.id })
        assertEquals("https://example.invalid/mosque-demo", schedule?.campaigns?.single()?.httpsUrl)
    }

    @Test
    fun malformedStoredFlagsDoNotSilentlyDisappear() = runTest {
        val snapshot = SnapshotDecoder.decode(syntheticSnapshotBytes())
        SnapshotImporter(database).importAndActivate(snapshot)
        database.openHelper.writableDatabase.execSQL(
            "UPDATE prayer_days SET flagsJson = '{' WHERE snapshotId = ? AND localDate = ?",
            arrayOf(snapshot.snapshotId, "2026-08-19"),
        )

        try {
            RoomPrayerScheduleRepository(database.snapshotDao())
                .observeActiveSchedule()
                .first { it != null }
            fail("expected corrupt local snapshot error")
        } catch (error: CorruptLocalSnapshotException) {
            assertEquals("invalid_prayer_day_flags", error.code)
        }
    }
}
