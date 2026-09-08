package ru.namaztime.tv.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "snapshots",
    primaryKeys = ["snapshotId"],
    indices = [Index(value = ["generatedAt"])],
)
data class SnapshotEntity(
    val snapshotId: String,
    val schemaVersion: String,
    val dataClassification: String,
    val generatedAt: String,
    val mosqueId: String,
    val mosqueName: String,
    val countryCode: String?,
    val region: String?,
    val locality: String?,
    val timezoneId: String,
    val sourceId: String,
    val sourceKind: String,
    val authorityName: String,
    val authorityBranch: String? = null,
    val geographicScope: String,
    val canonicalUrl: String? = null,
    val retrievedAt: String,
    val sourceEffectiveFrom: String,
    val sourceEffectiveTo: String,
    val rawSha256: String,
    val parserVersion: String,
    val calculationProfile: String? = null,
    val licenseReference: String? = null,
    val attribution: String? = null,
    val approvalId: String?,
    val approvalStatus: String? = "approved",
    val approvedBy: String?,
    val approvedAt: String?,
    val approvalScope: String?,
    val approvalNote: String? = null,
    val qualificationId: String? = null,
    val qualificationSha256: String? = null,
    val qualificationJson: String? = null,
    val coverageFrom: String,
    val coverageTo: String,
    val canonicalSha256: String,
    val signingKeyId: String,
    val signatureEd25519Base64: String,
)

@Entity(
    tableName = "prayer_days",
    primaryKeys = ["snapshotId", "localDate"],
    foreignKeys = [
        ForeignKey(
            entity = SnapshotEntity::class,
            parentColumns = ["snapshotId"],
            childColumns = ["snapshotId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["snapshotId"])],
)
data class PrayerDayEntity(
    val snapshotId: String,
    val localDate: String,
    val fajr: String,
    val sunrise: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String,
    val duha: String?,
    val middleOfNight: String?,
    val lastThirdOfNight: String?,
    @ColumnInfo(defaultValue = "'[]'")
    val flagsJson: String = "[]",
)

@Entity(
    tableName = "iqamah_rules",
    primaryKeys = ["snapshotId", "ruleId"],
    foreignKeys = [
        ForeignKey(
            entity = SnapshotEntity::class,
            parentColumns = ["snapshotId"],
            childColumns = ["snapshotId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["snapshotId"])],
)
data class IqamahRuleEntity(
    val snapshotId: String,
    val ruleId: String,
    val prayer: String,
    val validFrom: String,
    val validTo: String,
    val weekdaysMask: Int,
    val priority: Int,
    val mode: String,
    val fixedTime: String?,
    val offsetMinutes: Int?,
    val reason: String?,
)

@Entity(
    tableName = "iqamah_date_overrides",
    primaryKeys = ["snapshotId", "localDate", "prayer"],
    foreignKeys = [
        ForeignKey(
            entity = SnapshotEntity::class,
            parentColumns = ["snapshotId"],
            childColumns = ["snapshotId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["snapshotId"])],
)
data class IqamahDateOverrideEntity(
    val snapshotId: String,
    val localDate: String,
    val prayer: String,
    val mode: String,
    val fixedTime: String?,
    val offsetMinutes: Int?,
    val reason: String?,
)

@Entity(
    tableName = "jumuah_sessions",
    primaryKeys = ["snapshotId", "sessionId"],
    foreignKeys = [
        ForeignKey(
            entity = SnapshotEntity::class,
            parentColumns = ["snapshotId"],
            childColumns = ["snapshotId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["snapshotId"])],
)
data class JumuahSessionEntity(
    val snapshotId: String,
    val sessionId: String,
    val label: String,
    val khutbahTime: String?,
    val salahTime: String,
    val validFrom: String,
    val validTo: String,
)

@Entity(
    tableName = "campaigns",
    primaryKeys = ["snapshotId", "campaignId"],
    foreignKeys = [
        ForeignKey(
            entity = SnapshotEntity::class,
            parentColumns = ["snapshotId"],
            childColumns = ["snapshotId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["snapshotId"])],
)
data class CampaignEntity(
    val snapshotId: String,
    val campaignId: String,
    val kind: String,
    val httpsUrl: String,
    val title: String,
    val subtitle: String?,
    val startsAt: String?,
    val endsAt: String?,
    val placement: String,
)

@Entity(
    tableName = "themes",
    primaryKeys = ["snapshotId"],
    foreignKeys = [
        ForeignKey(
            entity = SnapshotEntity::class,
            parentColumns = ["snapshotId"],
            childColumns = ["snapshotId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ThemeEntity(
    val snapshotId: String,
    val themeId: String,
    val overlayOpacity: Double,
    val landscapeAssetJson: String? = null,
    val portraitAssetJson: String? = null,
)

@Entity(
    tableName = "snapshot_selection",
    primaryKeys = ["slot"],
    foreignKeys = [
        ForeignKey(
            entity = SnapshotEntity::class,
            parentColumns = ["snapshotId"],
            childColumns = ["activeSnapshotId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = SnapshotEntity::class,
            parentColumns = ["snapshotId"],
            childColumns = ["previousSnapshotId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["activeSnapshotId"]), Index(value = ["previousSnapshotId"])],
)
data class SnapshotSelectionEntity(
    val slot: String = DISPLAY_SELECTION_SLOT,
    val activeSnapshotId: String?,
    val previousSnapshotId: String?,
)

const val DISPLAY_SELECTION_SLOT = "display"
