package ru.namaztime.tv.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class SnapshotDaoTest {
    private lateinit var database: NamazDatabase
    private lateinit var dao: SnapshotDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            NamazDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.snapshotDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun prayerDaysAreReadInLocalDateOrder() = runTest {
        dao.insertSnapshot(snapshot())
        dao.insertPrayerDays(
            listOf(
                prayerDay("2026-08-21"),
                prayerDay("2026-08-19"),
                prayerDay("2026-08-20"),
            ),
        )

        assertEquals(
            listOf("2026-08-19", "2026-08-20", "2026-08-21"),
            dao.observePrayerDays(SNAPSHOT_ID).first().map(PrayerDayEntity::localDate),
        )
    }

    @Test
    fun currentDatabaseOpensAtVersionThreeAndProtectsActiveSnapshot() = runTest {
        val sqlite = database.openHelper.writableDatabase
        assertEquals(3, sqlite.version)
        val tableNames = buildSet {
            sqlite.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'",
            ).use { cursor ->
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
        assertEquals(
            setOf(
                "android_metadata",
                "room_master_table",
                "snapshots",
                "prayer_days",
                "iqamah_rules",
                "iqamah_date_overrides",
                "jumuah_sessions",
                "campaigns",
                "themes",
                "snapshot_selection",
            ),
            tableNames,
        )

        dao.insertSnapshot(snapshot())
        dao.setSelection(
            SnapshotSelectionEntity(
                activeSnapshotId = SNAPSHOT_ID,
                previousSnapshotId = null,
            ),
        )
        assertThrows(SQLiteConstraintException::class.java) {
            sqlite.delete("snapshots", "snapshotId = ?", arrayOf(SNAPSHOT_ID))
        }
    }

    private fun snapshot() = SnapshotEntity(
        snapshotId = SNAPSHOT_ID,
        schemaVersion = "1.0",
        dataClassification = "synthetic",
        generatedAt = "2026-08-19T10:00:00Z",
        mosqueId = "mosque-test",
        mosqueName = "Synthetic mosque",
        countryCode = "RU",
        region = "Synthetic region",
        locality = "Synthetic locality",
        timezoneId = "Europe/Ulyanovsk",
        sourceId = "source-test",
        sourceKind = "manual_import",
        authorityName = "Synthetic fixture",
        geographicScope = "Synthetic test only",
        retrievedAt = "2026-08-19T09:55:00Z",
        sourceEffectiveFrom = "2026-08-19",
        sourceEffectiveTo = "2026-08-21",
        rawSha256 = "1".repeat(64),
        parserVersion = "synthetic/1",
        approvalId = "approval-test",
        approvedBy = "test-suite",
        approvedAt = "2026-08-19T09:59:00Z",
        approvalScope = "Synthetic test only",
        coverageFrom = "2026-08-19",
        coverageTo = "2026-08-21",
        canonicalSha256 = "0".repeat(64),
        signingKeyId = "test-placeholder-key",
        signatureEd25519Base64 = "synthetic-signature",
    )

    private fun prayerDay(localDate: String) = PrayerDayEntity(
        snapshotId = SNAPSHOT_ID,
        localDate = localDate,
        fajr = "03:00",
        sunrise = "05:00",
        dhuhr = "12:00",
        asr = "16:00",
        maghrib = "19:00",
        isha = "21:00",
        duha = null,
        middleOfNight = null,
        lastThirdOfNight = null,
    )

    private companion object {
        const val SNAPSHOT_ID = "synthetic-snapshot-test"
    }
}
