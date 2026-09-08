package ru.namaztime.tv.data.local

import androidx.room.withTransaction
import ru.namaztime.tv.data.snapshot.SnapshotFormatValidation
import ru.namaztime.tv.data.snapshot.SnapshotSelectionTrust
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

sealed interface SnapshotSelectionResolution {
    data object Missing : SnapshotSelectionResolution
    data class Active(val snapshotId: String) : SnapshotSelectionResolution
    data class Recovered(val snapshotId: String) : SnapshotSelectionResolution
    data object Corrupt : SnapshotSelectionResolution
}

fun interface SnapshotSelectionResolver {
    suspend fun resolve(): SnapshotSelectionResolution
}

class SnapshotSelectionGuard(
    private val database: NamazDatabase,
    private val productionTrust: SnapshotSelectionTrust? = null,
) : SnapshotSelectionResolver {
    override suspend fun resolve(): SnapshotSelectionResolution = database.withTransaction {
        val dao = database.snapshotDao()
        val selection = dao.getSelection() ?: return@withTransaction SnapshotSelectionResolution.Missing
        val activeId = selection.activeSnapshotId
        if (activeId != null && isUsable(dao, activeId)) {
            return@withTransaction SnapshotSelectionResolution.Active(activeId)
        }
        val previousId = selection.previousSnapshotId
        if (previousId != null && isUsable(dao, previousId)) {
            dao.setSelection(
                SnapshotSelectionEntity(
                    activeSnapshotId = previousId,
                    previousSnapshotId = null,
                ),
            )
            return@withTransaction SnapshotSelectionResolution.Recovered(previousId)
        }
        if (activeId == null && previousId == null) {
            return@withTransaction SnapshotSelectionResolution.Missing
        }
        dao.setSelection(SnapshotSelectionEntity(activeSnapshotId = null, previousSnapshotId = null))
        SnapshotSelectionResolution.Corrupt
    }

    private suspend fun isUsable(dao: SnapshotDao, snapshotId: String): Boolean {
        val snapshot = dao.getSnapshot(snapshotId) ?: return false
        if (!validMetadata(snapshot)) return false
        val from = runCatching { LocalDate.parse(snapshot.coverageFrom) }.getOrNull() ?: return false
        val to = runCatching { LocalDate.parse(snapshot.coverageTo) }.getOrNull() ?: return false
        if (to.isBefore(from)) return false
        val days = dao.getPrayerDays(snapshotId)
        val expectedCount = ChronoUnit.DAYS.between(from, to) + 1
        if (days.size.toLong() != expectedCount) return false
        val hasLocalPrayerPolicy = dao.countIqamahRules(snapshotId) != 0 ||
            dao.countIqamahOverrides(snapshotId) != 0 || dao.countJumuahSessions(snapshotId) != 0
        try {
            persistedSourceQualification(snapshot, days, hasLocalPrayerPolicy)
        } catch (_: IllegalArgumentException) {
            return false
        }
        return days.withIndex().all { (index, day) ->
            day.localDate == from.plusDays(index.toLong()).toString() &&
                listOf(day.fajr, day.sunrise, day.dhuhr, day.asr, day.maghrib, day.isha)
                    .all(LOCAL_TIME_PATTERN::matches) &&
                validFlags(day.flagsJson)
        }
    }

    private fun validMetadata(snapshot: SnapshotEntity): Boolean {
        val sourceFrom = runCatching { LocalDate.parse(snapshot.sourceEffectiveFrom) }.getOrNull()
            ?: return false
        val sourceTo = runCatching { LocalDate.parse(snapshot.sourceEffectiveTo) }.getOrNull()
            ?: return false
        val coverageFrom = runCatching { LocalDate.parse(snapshot.coverageFrom) }.getOrNull()
            ?: return false
        val coverageTo = runCatching { LocalDate.parse(snapshot.coverageTo) }.getOrNull()
            ?: return false
        val generatedAt = runCatching { Instant.parse(snapshot.generatedAt) }.getOrNull() ?: return false
        val productionTrustValid = snapshot.dataClassification != "production" ||
            productionTrust?.allows(snapshot.signingKeyId, generatedAt) == true
        val validAdmission = snapshot.schemaVersion == "2.0" ||
            (snapshot.schemaVersion == "1.0" && snapshot.approvalStatus == "approved" &&
                !snapshot.approvalId.isNullOrBlank() && !snapshot.approvedBy.isNullOrBlank() &&
                SnapshotFormatValidation.parseRfc3339(snapshot.approvedAt.orEmpty()) != null && !snapshot.approvalScope.isNullOrBlank())
        return validAdmission &&
            SnapshotFormatValidation.codePointLength(snapshot.snapshotId) in 8..128 &&
            snapshot.dataClassification in setOf("production", "synthetic") &&
            SnapshotFormatValidation.parseRfc3339(snapshot.generatedAt) != null &&
            snapshot.mosqueId.isNotBlank() &&
            snapshot.mosqueName.isNotBlank() &&
            SnapshotFormatValidation.isNamedIanaTimezone(snapshot.timezoneId) &&
            snapshot.sourceId.isNotBlank() &&
            snapshot.sourceKind in PROVIDER_KINDS &&
            snapshot.authorityName.isNotBlank() &&
            snapshot.geographicScope.isNotBlank() &&
            SnapshotFormatValidation.parseRfc3339(snapshot.retrievedAt) != null &&
            !sourceTo.isBefore(sourceFrom) &&
            !coverageTo.isBefore(coverageFrom) &&
            !coverageFrom.isBefore(sourceFrom) &&
            !coverageTo.isAfter(sourceTo) &&
            SnapshotFormatValidation.isSha256(snapshot.rawSha256) &&
            snapshot.parserVersion.isNotBlank() &&
            SnapshotFormatValidation.isSha256(snapshot.canonicalSha256) &&
            snapshot.signingKeyId.isNotBlank() &&
            SnapshotFormatValidation.isEd25519SignatureEncoding(snapshot.signatureEd25519Base64) &&
            productionTrustValid
    }

    private fun validFlags(value: String): Boolean = runCatching {
        val flags = Json.decodeFromString<List<String>>(value)
        flags.distinct().size == flags.size &&
            flags.all { SnapshotFormatValidation.codePointLength(it) <= 128 }
    }.getOrDefault(false)

    private companion object {
        val LOCAL_TIME_PATTERN = Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$")
        val PROVIDER_KINDS = setOf(
            "official_api",
            "official_file",
            "official_html",
            "mosque_calendar",
            "calculation_profile",
            "manual_import",
        )
    }
}
