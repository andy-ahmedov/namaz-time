package ru.namaztime.tv.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class NamazDatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun deleteDatabase() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun migrationOneToThreePreservesActiveSnapshotAndDefaultsFlags() = runTest {
        createVersionDatabase(1).use { helper ->
            val database = helper.writableDatabase
            database.apply {
                execSQL(snapshotInsertSql("migration-snapshot"))
                insertPrayerDays(this, "migration-snapshot", includeFlags = false)
                execSQL(
                    """
                    INSERT INTO snapshot_selection (slot, activeSnapshotId, previousSnapshotId)
                    VALUES ('display', 'migration-snapshot', NULL)
                    """.trimIndent(),
                )
            }
        }

        val migrated = Room.databaseBuilder(context, NamazDatabase::class.java, DATABASE_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

        assertEquals("migration-snapshot", migrated.snapshotDao().getSelection()?.activeSnapshotId)
        assertEquals("[]", migrated.snapshotDao().getPrayerDayFlags("migration-snapshot", "2026-08-19"))
        assertEquals("approved", migrated.snapshotDao().getSnapshot("migration-snapshot")?.approvalStatus)
        assertEquals(null, migrated.snapshotDao().getTheme("migration-snapshot"))
        assertEquals(
            SnapshotSelectionResolution.Active("migration-snapshot"),
            SnapshotSelectionGuard(migrated).resolve(),
        )
        migrated.close()
    }

    @Test
    fun migrationTwoToThreePreservesPopulatedChildrenFlagsAndPointers() = runTest {
        createVersionDatabase(2).use { helper ->
            helper.writableDatabase.apply {
                execSQL(snapshotInsertSql("migration-previous"))
                execSQL(snapshotInsertSql("migration-active"))
                insertPrayerDays(this, "migration-previous", includeFlags = true)
                insertPrayerDays(this, "migration-active", includeFlags = true)
                execSQL(
                    """
                    INSERT INTO iqamah_rules (
                        snapshotId, ruleId, prayer, validFrom, validTo, weekdaysMask,
                        priority, mode, fixedTime, offsetMinutes, reason
                    ) VALUES (
                        'migration-active', 'rule-fajr', 'fajr', '2026-08-19',
                        '2026-08-21', 127, 10, 'fixed_time', '04:00', NULL, 'migration'
                    )
                    """.trimIndent(),
                )
                execSQL(
                    """
                    INSERT INTO themes (snapshotId, themeId, overlayOpacity)
                    VALUES ('migration-active', 'builtin-default', 0.5)
                    """.trimIndent(),
                )
                execSQL(
                    """
                    INSERT INTO snapshot_selection (slot, activeSnapshotId, previousSnapshotId)
                    VALUES ('display', 'migration-active', 'migration-previous')
                    """.trimIndent(),
                )
            }
        }

        val migrated = Room.databaseBuilder(context, NamazDatabase::class.java, DATABASE_NAME)
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

        val selection = migrated.snapshotDao().getSelection()
        assertEquals("migration-active", selection?.activeSnapshotId)
        assertEquals("migration-previous", selection?.previousSnapshotId)
        assertEquals(
            "[\"migrated\"]",
            migrated.snapshotDao().getPrayerDayFlags("migration-active", "2026-08-19"),
        )
        assertEquals(1, migrated.snapshotDao().countIqamahRules("migration-active"))
        assertEquals("builtin-default", migrated.snapshotDao().getTheme("migration-active")?.themeId)
        assertEquals(null, migrated.snapshotDao().getTheme("migration-active")?.landscapeAssetJson)
        assertEquals(
            SnapshotSelectionResolution.Active("migration-active"),
            SnapshotSelectionGuard(migrated).resolve(),
        )
        migrated.close()
    }

    @Test
    fun migrationThreeToFourPreservesAllLegacyChildrenApprovalAndForeignKeys() = runTest {
        createVersionDatabase(3).use { helper ->
            helper.writableDatabase.apply {
                execSQL(snapshotInsertSql("migration-previous"))
                execSQL(snapshotInsertSql("migration-active"))
                execSQL("UPDATE snapshots SET authorityBranch='Legacy branch', canonicalUrl='https://authority.example/calendar', approvalNote='Retained note', attribution='Retained attribution'")
                insertPrayerDays(this, "migration-previous", includeFlags = true)
                insertPrayerDays(this, "migration-active", includeFlags = true)
                execSQL("INSERT INTO iqamah_rules VALUES ('migration-active', 'rule-fajr', 'fajr', '2026-08-19', '2026-08-21', 127, 10, 'fixed_time', '04:00', NULL, 'migration')")
                execSQL("INSERT INTO iqamah_date_overrides VALUES ('migration-active', '2026-08-20', 'asr', 'fixed_time', '17:00', NULL, 'migration override')")
                execSQL("INSERT INTO jumuah_sessions VALUES ('migration-active', 'friday', 'Friday session', '12:00', '12:30', '2026-08-19', '2026-08-21')")
                execSQL("INSERT INTO campaigns VALUES ('migration-active', 'campaign', 'donation', 'https://authority.example/give', 'Retained title', 'Retained subtitle', NULL, NULL, 'main')")
                execSQL("INSERT INTO themes VALUES ('migration-active', 'builtin-default', 0.5, '{\"retained\":true}', NULL)")
                execSQL("INSERT INTO snapshot_selection VALUES ('display', 'migration-active', 'migration-previous')")
            }
        }
        val migrated = Room.databaseBuilder(context, NamazDatabase::class.java, DATABASE_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .allowMainThreadQueries().build()
        assertEquals(4, migrated.openHelper.writableDatabase.version)
        val dao = migrated.snapshotDao()
        val active = dao.getSnapshot("migration-active")!!
        assertEquals("approval-test", active.approvalId)
        assertEquals("approved", active.approvalStatus)
        assertEquals("test-suite", active.approvedBy)
        assertEquals("Retained note", active.approvalNote)
        assertEquals("Legacy branch", active.authorityBranch)
        assertEquals("Retained attribution", active.attribution)
        assertEquals("migration-active", dao.getSelection()?.activeSnapshotId)
        assertEquals("migration-previous", dao.getSelection()?.previousSnapshotId)
        assertEquals(3, dao.countPrayerDays("migration-active"))
        assertEquals(3, dao.countPrayerDays("migration-previous"))
        assertEquals("[\"migrated\"]", dao.getPrayerDayFlags("migration-active", "2026-08-19"))
        assertEquals(1, dao.countIqamahRules("migration-active"))
        assertEquals(1, dao.countIqamahOverrides("migration-active"))
        assertEquals(1, dao.countJumuahSessions("migration-active"))
        assertEquals(1, dao.countCampaigns("migration-active"))
        assertEquals("{\"retained\":true}", dao.getTheme("migration-active")?.landscapeAssetJson)
        migrated.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        assertEquals(SnapshotSelectionResolution.Active("migration-active"), SnapshotSelectionGuard(migrated).resolve())
        migrated.close()
    }

    private fun createVersionDatabase(version: Int): SupportSQLiteOpenHelper {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(DATABASE_NAME)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        schemaQueries(version).forEach(db::execSQL)
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                },
            )
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration)
    }

    private fun schemaQueries(version: Int): List<String> {
        val schema = File(
            "schemas/ru.namaztime.tv.data.local.NamazDatabase/$version.json",
        )
        val database = Json.parseToJsonElement(schema.readText()).jsonObject
            .getValue("database").jsonObject
        val entityQueries = database.getValue("entities").jsonArray.flatMap { element ->
            val entity = element.jsonObject
            val tableName = entity.getValue("tableName").jsonPrimitive.content
            val table = entity.getValue("createSql").jsonPrimitive.content
                .replace("${'$'}{TABLE_NAME}", tableName)
            val indices = entity["indices"]?.jsonArray?.map { index ->
                index.jsonObject.getValue("createSql").jsonPrimitive.content
                    .replace("${'$'}{TABLE_NAME}", tableName)
            }.orEmpty()
            listOf(table) + indices
        }
        val setupQueries = database.getValue("setupQueries").jsonArray.map {
            it.jsonPrimitive.content
        }
        return entityQueries + setupQueries
    }

    private fun insertPrayerDays(
        database: SupportSQLiteDatabase,
        snapshotId: String,
        includeFlags: Boolean,
    ) {
        listOf("2026-08-19", "2026-08-20", "2026-08-21").forEachIndexed { index, date ->
            val flagsColumn = if (includeFlags) ", flagsJson" else ""
            val flagsValue = if (includeFlags) ", '[\"migrated\"]'" else ""
            database.execSQL(
                """
                INSERT INTO prayer_days (
                    snapshotId, localDate, fajr, sunrise, dhuhr, asr, maghrib, isha,
                    duha, middleOfNight, lastThirdOfNight$flagsColumn
                ) VALUES (
                    '$snapshotId', '$date', '03:1${index + 2}', '05:2${index + 1}', '12:08',
                    '16:47', '18:53', '21:01', NULL, NULL, NULL$flagsValue
                )
                """.trimIndent(),
            )
        }
    }

    private fun snapshotInsertSql(snapshotId: String) = """
        INSERT INTO snapshots (
            snapshotId, schemaVersion, dataClassification, generatedAt, mosqueId, mosqueName,
            countryCode, region, locality, timezoneId, sourceId, sourceKind, authorityName,
            geographicScope, retrievedAt, sourceEffectiveFrom, sourceEffectiveTo, rawSha256,
            parserVersion, approvalId, approvedBy, approvedAt, approvalScope, coverageFrom,
            coverageTo, canonicalSha256, signingKeyId, signatureEd25519Base64
        ) VALUES (
            '$snapshotId', '1.0', 'synthetic', '2026-08-19T10:00:00Z',
            'mosque-test', 'Synthetic mosque', 'RU', 'region', 'locality', 'Europe/Ulyanovsk',
            'source-test', 'manual_import', 'Synthetic authority', 'Synthetic scope',
            '2026-08-19T09:55:00Z', '2026-08-19', '2026-08-21', '${"1".repeat(64)}',
            'synthetic/1', 'approval-test', 'test-suite', '2026-08-19T09:59:00Z',
            'Synthetic only', '2026-08-19', '2026-08-21', '${"0".repeat(64)}',
            'test-placeholder-key', '${"A".repeat(86)}=='
        )
    """.trimIndent()

    private companion object {
        const val DATABASE_NAME = "namaz-migration-test.db"
    }
}
