package ru.namaztime.tv.data.snapshot

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SnapshotPayload(
    @SerialName("schema_version") val schemaVersion: String,
    @SerialName("snapshot_id") val snapshotId: String,
    @SerialName("data_classification") val dataClassification: String,
    @SerialName("generated_at") val generatedAt: String,
    val mosque: SnapshotMosque,
    val source: SnapshotSource,
    val coverage: SnapshotDateRange,
    @SerialName("prayer_days") val prayerDays: List<SnapshotPrayerDay>,
    @SerialName("iqamah_rules") val iqamahRules: List<SnapshotIqamahRule> = emptyList(),
    @SerialName("iqamah_date_overrides")
    val iqamahDateOverrides: List<SnapshotIqamahOverride> = emptyList(),
    @SerialName("jumuah_sessions") val jumuahSessions: List<SnapshotJumuahSession> = emptyList(),
    val campaigns: List<SnapshotCampaign> = emptyList(),
    val theme: SnapshotTheme? = null,
    val integrity: SnapshotIntegrity,
)

@Serializable
data class SnapshotMosque(
    val id: String,
    val name: String,
    @SerialName("country_code") val countryCode: String? = null,
    val region: String? = null,
    val locality: String? = null,
    val timezone: String,
)

@Serializable
data class SnapshotSource(
    @SerialName("source_id") val sourceId: String,
    val kind: String,
    @SerialName("authority_name") val authorityName: String,
    @SerialName("authority_branch") val authorityBranch: String? = null,
    @SerialName("geographic_scope") val geographicScope: String,
    @SerialName("canonical_url") val canonicalUrl: String? = null,
    @SerialName("retrieved_at") val retrievedAt: String,
    @SerialName("effective_from") val effectiveFrom: String,
    @SerialName("effective_to") val effectiveTo: String,
    @SerialName("raw_sha256") val rawSha256: String,
    @SerialName("parser_version") val parserVersion: String,
    @SerialName("calculation_profile") val calculationProfile: String? = null,
    @SerialName("license_reference") val licenseReference: String? = null,
    val attribution: String? = null,
    val approval: SnapshotApproval? = null,
    val qualification: SnapshotSourceQualification? = null,
)

@Serializable
data class SnapshotApproval(
    val status: String,
    @SerialName("approval_id") val approvalId: String,
    @SerialName("approved_by") val approvedBy: String,
    @SerialName("approved_at") val approvedAt: String,
    @SerialName("approval_scope") val approvalScope: String,
    val note: String? = null,
)

@Serializable
data class SnapshotDateRange(
    val from: String,
    val to: String,
)

@Serializable
data class SnapshotPrayerDay(
    val date: String,
    val fajr: String,
    val sunrise: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String,
    val duha: String? = null,
    @SerialName("middle_of_night") val middleOfNight: String? = null,
    @SerialName("last_third_of_night") val lastThirdOfNight: String? = null,
    val flags: List<String> = emptyList(),
)

@Serializable
data class SnapshotIqamahRule(
    val id: String,
    val prayer: String,
    @SerialName("valid_from") val validFrom: String,
    @SerialName("valid_to") val validTo: String,
    val weekdays: List<Int>,
    val priority: Int,
    val value: SnapshotIqamahValue,
    val reason: String? = null,
)

@Serializable
data class SnapshotIqamahOverride(
    val date: String,
    val prayer: String,
    val value: SnapshotIqamahValue,
    val reason: String? = null,
)

@Serializable
data class SnapshotIqamahValue(
    val mode: String,
    @SerialName("fixed_time") val fixedTime: String? = null,
    @SerialName("offset_minutes") val offsetMinutes: Int? = null,
)

@Serializable
data class SnapshotJumuahSession(
    val id: String,
    val label: String,
    @SerialName("khutbah_time") val khutbahTime: String? = null,
    @SerialName("salah_time") val salahTime: String,
    @SerialName("valid_from") val validFrom: String,
    @SerialName("valid_to") val validTo: String,
)

@Serializable
data class SnapshotCampaign(
    val id: String,
    val kind: String,
    val url: String,
    val title: String,
    val subtitle: String? = null,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String,
    val placement: String,
)

@Serializable
data class SnapshotTheme(
    @SerialName("theme_id") val themeId: String,
    @SerialName("landscape_asset") val landscapeAsset: SnapshotAssetReference? = null,
    @SerialName("portrait_asset") val portraitAsset: SnapshotAssetReference? = null,
    @SerialName("overlay_opacity") val overlayOpacity: Double,
)

@Serializable
data class SnapshotAssetReference(
    @SerialName("asset_id") val assetId: String,
    val sha256: String,
    @SerialName("media_type") val mediaType: String,
    @SerialName("byte_length") val byteLength: Long,
    val width: Int,
    val height: Int,
)

@Serializable
data class SnapshotIntegrity(
    @SerialName("canonical_sha256") val canonicalSha256: String,
    @SerialName("signing_key_id") val signingKeyId: String,
    @SerialName("signature_ed25519_base64") val signatureEd25519Base64: String,
)
