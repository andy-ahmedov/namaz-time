package ru.namaztime.tv.data.snapshot

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Versioned NamazTime public-source proof, deliberately separate from approval.
// Empty defaults mirror Go's omitempty fields for lossless fingerprint checks.
@Serializable
data class SnapshotSourceQualification(
    @SerialName("schema_version") val schemaVersion: String,
    @SerialName("qualification_id") val qualificationId: String,
    val state: String,
    @SerialName("decision_system") val decisionSystem: String,
    @SerialName("qualified_at") val qualifiedAt: String,
    val authority: SnapshotQualifiedAuthority,
    @SerialName("source_id") val sourceId: String,
    @SerialName("source_kind") val sourceKind: String,
    @SerialName("canonical_url") val canonicalUrl: String,
    val scope: SnapshotQualifiedScope,
    @SerialName("catalog_revision") val catalogRevision: String,
    val timezone: String,
    val coverage: SnapshotDateRange,
    @SerialName("fresh_through") val freshThrough: String,
    val artifact: SnapshotQualifiedArtifact,
    val retrieval: SnapshotQualifiedRetrieval,
    @SerialName("parser_version") val parserVersion: String,
    @SerialName("candidate_id") val candidateId: String,
    @SerialName("normalized_sha256") val normalizedSha256: String,
    @SerialName("onset_sha256") val onsetSha256: String,
    @SerialName("transcription_sha256") val transcriptionSha256: String,
    @SerialName("diff_sha256") val diffSha256: String,
    @SerialName("validation_sha256") val validationSha256: String,
    @SerialName("validated_days") val validatedDays: Int,
    @SerialName("terms_assessment") val termsAssessment: String,
    val evidence: List<SnapshotSourceEvidence>,
    val comparisons: List<SnapshotSourceComparison>,
    @SerialName("warning_resolutions") val warningResolutions: List<SnapshotWarningResolution> = emptyList(),
    val unknowns: List<String> = emptyList(),
    val sha256: String,
)

@Serializable
data class SnapshotQualifiedAuthority(
    val id: String,
    val name: String,
    val branch: String = "",
    val website: String = "",
    @SerialName("evidence_label") val evidenceLabel: String,
)

@Serializable
data class SnapshotQualifiedScope(
    val id: String,
    val kind: String,
    @SerialName("city_id") val cityId: String = "",
    @SerialName("region_id") val regionId: String,
    val description: String,
)

@Serializable
data class SnapshotQualifiedArtifact(
    val filename: String,
    @SerialName("content_type") val contentType: String,
    @SerialName("captured_at") val capturedAt: String,
    @SerialName("byte_length") val byteLength: Long,
    val sha256: String,
)

@Serializable
data class SnapshotQualifiedRetrieval(
    val url: String,
    @SerialName("http_status") val httpStatus: Int,
    @SerialName("content_type") val contentType: String,
    val etag: String = "",
    @SerialName("last_modified") val lastModified: String = "",
)

@Serializable
data class SnapshotSourceEvidence(
    val id: String,
    val purpose: String,
    val label: String,
    val url: String,
    @SerialName("retrieved_at") val retrievedAt: String,
    val sha256: String,
    val claim: String,
)

@Serializable
data class SnapshotSourceComparison(
    @SerialName("evidence_id") val evidenceId: String,
    val day: SnapshotPrayerDay,
)

@Serializable
data class SnapshotWarningResolution(
    val code: String,
    @SerialName("evidence_id") val evidenceId: String,
    val reason: String,
)
