package com.example.namaztime.tv.data.snapshot

import java.net.URI
import java.nio.charset.CharacterCodingException
import java.time.DateTimeException
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json

class SnapshotValidationException(
    val path: String,
    val code: String,
    cause: Throwable? = null,
) : IllegalArgumentException("$path: $code", cause)

object SnapshotDecoder {
    private const val MAX_PRAYER_DAYS = 400
    private const val MAX_IQAMAH_RULES = 512
    private const val MAX_IQAMAH_OVERRIDES = 2_000
    private const val MAX_JUMUAH_SESSIONS = 64
    private const val MAX_CAMPAIGNS = 128
    private const val MAX_PRAYER_DAY_FLAGS = 32
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
    }
    private val datePattern = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val timePattern = Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$")
    private val providerKinds = setOf(
        "official_api",
        "official_file",
        "official_html",
        "mosque_calendar",
        "calculation_profile",
        "manual_import",
    )
    private val prayers = setOf("fajr", "dhuhr", "asr", "maghrib", "isha")

    fun decode(bytes: ByteArray): SnapshotPayload {
        val document = try {
            SnapshotFormatValidation.decodeUtf8(bytes)
        } catch (error: CharacterCodingException) {
            fail("$", "invalid_json", error)
        }
        val element = try {
            json.parseToJsonElement(document)
        } catch (error: SerializationException) {
            fail("$", "invalid_json", error)
        } catch (error: IllegalArgumentException) {
            fail("$", "invalid_json", error)
        }
        rejectExplicitNulls(element, "$")
        val snapshot = try {
            json.decodeFromJsonElement(SnapshotPayload.serializer(), element)
        } catch (error: SerializationException) {
            fail("$", "invalid_json", error)
        }
        validate(snapshot)
        return snapshot
    }

    private fun validate(snapshot: SnapshotPayload) {
        requireValue(snapshot.schemaVersion == "1.0", "schema_version", "unsupported_value")
        text(snapshot.snapshotId, "snapshot_id", 8, 128)
        requireValue(
            snapshot.dataClassification in setOf("production", "synthetic"),
            "data_classification",
            "unsupported_value",
        )
        instant(snapshot.generatedAt, "generated_at")

        text(snapshot.mosque.id, "mosque.id", 1, 128)
        text(snapshot.mosque.name, "mosque.name", 1, 240)
        snapshot.mosque.countryCode?.let {
            requireValue(Regex("^[A-Z]{2}$").matches(it), "mosque.country_code", "invalid_country_code")
        }
        maxLength(snapshot.mosque.region, "mosque.region", 240)
        maxLength(snapshot.mosque.locality, "mosque.locality", 240)
        text(snapshot.mosque.timezone, "mosque.timezone", 3, 64)
        requireValue(
            SnapshotFormatValidation.isNamedIanaTimezone(snapshot.mosque.timezone),
            "mosque.timezone",
            "invalid_timezone",
        )

        validateSource(snapshot.source)
        val coverageFrom = date(snapshot.coverage.from, "coverage.from")
        val coverageTo = date(snapshot.coverage.to, "coverage.to")
        requireValue(!coverageTo.isBefore(coverageFrom), "coverage", "invalid_range")
        val sourceFrom = date(snapshot.source.effectiveFrom, "source.effective_from")
        val sourceTo = date(snapshot.source.effectiveTo, "source.effective_to")
        requireValue(
            !coverageFrom.isBefore(sourceFrom) && !coverageTo.isAfter(sourceTo),
            "coverage",
            "outside_source_effective_range",
        )
        validatePrayerDays(snapshot.prayerDays, coverageFrom, coverageTo)
        requireValue(snapshot.iqamahRules.size <= MAX_IQAMAH_RULES, "iqamah_rules", "too_many_items")
        snapshot.iqamahRules.forEachIndexed(::validateIqamahRule)
        requireValue(
            snapshot.iqamahDateOverrides.size <= MAX_IQAMAH_OVERRIDES,
            "iqamah_date_overrides",
            "too_many_items",
        )
        snapshot.iqamahDateOverrides.forEachIndexed(::validateIqamahOverride)
        requireValue(snapshot.jumuahSessions.size <= MAX_JUMUAH_SESSIONS, "jumuah_sessions", "too_many_items")
        snapshot.jumuahSessions.forEachIndexed(::validateJumuah)
        requireValue(snapshot.campaigns.size <= MAX_CAMPAIGNS, "campaigns", "too_many_items")
        snapshot.campaigns.forEachIndexed(::validateCampaign)
        snapshot.theme?.let {
            text(it.themeId, "theme.theme_id", 1, 128)
            requireValue(it.overlayOpacity in 0.0..1.0, "theme.overlay_opacity", "out_of_range")
            it.landscapeAsset?.let { asset -> validateAsset(asset, "theme.landscape_asset") }
            it.portraitAsset?.let { asset -> validateAsset(asset, "theme.portrait_asset") }
        }
        sha256(snapshot.integrity.canonicalSha256, "integrity.canonical_sha256")
        text(snapshot.integrity.signingKeyId, "integrity.signing_key_id", 1, 128)
        requireValue(
            SnapshotFormatValidation.isEd25519SignatureEncoding(
                snapshot.integrity.signatureEd25519Base64,
            ),
            "integrity.signature_ed25519_base64",
            "invalid_signature_encoding",
        )
    }

    private fun validateSource(source: SnapshotSource) {
        text(source.sourceId, "source.source_id", 1, 128)
        requireValue(source.kind in providerKinds, "source.kind", "unsupported_value")
        if (source.kind == "calculation_profile") {
            text(source.calculationProfile.orEmpty(), "source.calculation_profile", 1, 240)
        }
        text(source.authorityName, "source.authority_name", 1, 240)
        maxLength(source.authorityBranch, "source.authority_branch", 240)
        text(source.geographicScope, "source.geographic_scope", 1, 1000)
        source.canonicalUrl?.let { uri(it, "source.canonical_url") }
        instant(source.retrievedAt, "source.retrieved_at")
        val from = date(source.effectiveFrom, "source.effective_from")
        val to = date(source.effectiveTo, "source.effective_to")
        requireValue(!to.isBefore(from), "source", "invalid_range")
        sha256(source.rawSha256, "source.raw_sha256")
        text(source.parserVersion, "source.parser_version", 1, 128)
        source.calculationProfile?.let {
            text(it, "source.calculation_profile", 1, 240)
        }
        maxLength(source.licenseReference, "source.license_reference", 1000)
        maxLength(source.attribution, "source.attribution", 1000)
        requireValue(source.approval.status == "approved", "source.approval.status", "unsupported_value")
        text(source.approval.approvalId, "source.approval.approval_id", 1, 128)
        text(source.approval.approvedBy, "source.approval.approved_by", 1, 240)
        instant(source.approval.approvedAt, "source.approval.approved_at")
        text(source.approval.approvalScope, "source.approval.approval_scope", 1, 1000)
        maxLength(source.approval.note, "source.approval.note", 2000)
    }

    private fun validatePrayerDays(
        days: List<SnapshotPrayerDay>,
        coverageFrom: LocalDate,
        coverageTo: LocalDate,
    ) {
        requireValue(days.isNotEmpty(), "prayer_days", "required")
        requireValue(days.size <= MAX_PRAYER_DAYS, "prayer_days", "too_many_items")
        val expectedCount = ChronoUnit.DAYS.between(coverageFrom, coverageTo) + 1
        requireValue(days.size.toLong() == expectedCount, "prayer_days", "coverage_mismatch")
        val seen = mutableSetOf<String>()
        days.forEachIndexed { index, day ->
            val parsed = date(day.date, "prayer_days[$index].date")
            requireValue(seen.add(day.date), "prayer_days[$index].date", "duplicate_date")
            requireValue(
                parsed == coverageFrom.plusDays(index.toLong()),
                "prayer_days[$index].date",
                "date_gap",
            )
            listOf(
                "fajr" to day.fajr,
                "sunrise" to day.sunrise,
                "dhuhr" to day.dhuhr,
                "asr" to day.asr,
                "maghrib" to day.maghrib,
                "isha" to day.isha,
            ).forEach { (name, value) -> localTime(value, "prayer_days[$index].$name") }
            day.duha?.let { localTime(it, "prayer_days[$index].duha") }
            day.middleOfNight?.let { localTime(it, "prayer_days[$index].middle_of_night") }
            day.lastThirdOfNight?.let { localTime(it, "prayer_days[$index].last_third_of_night") }
            requireValue(
                day.flags.distinct().size == day.flags.size,
                "prayer_days[$index].flags",
                "duplicate_value",
            )
            requireValue(
                day.flags.size <= MAX_PRAYER_DAY_FLAGS,
                "prayer_days[$index].flags",
                "too_many_items",
            )
            day.flags.forEachIndexed { flagIndex, flag ->
                maxLength(flag, "prayer_days[$index].flags[$flagIndex]", 128)
            }
        }
    }

    private fun validateIqamahRule(index: Int, rule: SnapshotIqamahRule) {
        text(rule.id, "iqamah_rules[$index].id", 1, 128)
        prayer(rule.prayer, "iqamah_rules[$index].prayer")
        val from = date(rule.validFrom, "iqamah_rules[$index].valid_from")
        val to = date(rule.validTo, "iqamah_rules[$index].valid_to")
        requireValue(!to.isBefore(from), "iqamah_rules[$index]", "invalid_range")
        requireValue(rule.weekdays.isNotEmpty(), "iqamah_rules[$index].weekdays", "required")
        requireValue(
            rule.weekdays.distinct().size == rule.weekdays.size && rule.weekdays.all { it in 1..7 },
            "iqamah_rules[$index].weekdays",
            "invalid_weekdays",
        )
        requireValue(rule.priority in 0..100_000, "iqamah_rules[$index].priority", "out_of_range")
        validateIqamahValue(rule.value, "iqamah_rules[$index].value")
        maxLength(rule.reason, "iqamah_rules[$index].reason", 1000)
    }

    private fun validateIqamahOverride(index: Int, override: SnapshotIqamahOverride) {
        date(override.date, "iqamah_date_overrides[$index].date")
        prayer(override.prayer, "iqamah_date_overrides[$index].prayer")
        validateIqamahValue(override.value, "iqamah_date_overrides[$index].value")
        maxLength(override.reason, "iqamah_date_overrides[$index].reason", 1000)
    }

    private fun validateIqamahValue(value: SnapshotIqamahValue, path: String) {
        when (value.mode) {
            "fixed_time" -> {
                requireValue(value.offsetMinutes == null, path, "conflicting_value")
                localTime(value.fixedTime.orEmpty(), "$path.fixed_time")
            }
            "offset_after_adhan" -> {
                requireValue(value.fixedTime == null, path, "conflicting_value")
                requireValue(value.offsetMinutes != null, "$path.offset_minutes", "required")
                requireValue(value.offsetMinutes in 0..240, "$path.offset_minutes", "out_of_range")
            }
            else -> fail("$path.mode", "unsupported_value")
        }
    }

    private fun validateJumuah(index: Int, session: SnapshotJumuahSession) {
        text(session.id, "jumuah_sessions[$index].id", 1, 128)
        text(session.label, "jumuah_sessions[$index].label", 1, 120)
        session.khutbahTime?.let { localTime(it, "jumuah_sessions[$index].khutbah_time") }
        localTime(session.salahTime, "jumuah_sessions[$index].salah_time")
        val from = date(session.validFrom, "jumuah_sessions[$index].valid_from")
        val to = date(session.validTo, "jumuah_sessions[$index].valid_to")
        requireValue(!to.isBefore(from), "jumuah_sessions[$index]", "invalid_range")
    }

    private fun validateCampaign(index: Int, campaign: SnapshotCampaign) {
        text(campaign.id, "campaigns[$index].id", 1, 128)
        requireValue(
            campaign.kind in setOf("donation", "website", "telegram", "schedule", "contacts", "custom"),
            "campaigns[$index].kind",
            "unsupported_value",
        )
        text(campaign.title, "campaigns[$index].title", 1, 160)
        maxLength(campaign.subtitle, "campaigns[$index].subtitle", 500)
        val uri = try {
            URI(campaign.url)
        } catch (error: Exception) {
            fail("campaigns[$index].url", "invalid_https_url", error)
        }
        requireValue(
            uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null &&
                SnapshotFormatValidation.codePointLength(campaign.url) <= 2_048,
            "campaigns[$index].url",
            "invalid_https_url",
        )
        val startsAt = instant(campaign.startsAt, "campaigns[$index].starts_at")
        val endsAt = instant(campaign.endsAt, "campaigns[$index].ends_at")
        requireValue(endsAt > startsAt, "campaigns[$index]", "invalid_range")
        requireValue(
            campaign.placement in setOf("always", "with_prayer_times", "rotation"),
            "campaigns[$index].placement",
            "unsupported_value",
        )
    }

    private fun validateAsset(asset: SnapshotAssetReference, path: String) {
        text(asset.assetId, "$path.asset_id", 1, 128)
        sha256(asset.sha256, "$path.sha256")
        requireValue(
            asset.mediaType in setOf("image/jpeg", "image/png", "image/webp"),
            "$path.media_type",
            "unsupported_value",
        )
        requireValue(asset.byteLength in 1..20_971_520, "$path.byte_length", "out_of_range")
        requireValue(asset.width in 320..7680, "$path.width", "out_of_range")
        requireValue(asset.height in 180..7680, "$path.height", "out_of_range")
    }

    private fun required(value: String, path: String) {
        requireValue(value.isNotBlank(), path, "required")
    }

    private fun text(value: String, path: String, minimum: Int, maximum: Int) {
        required(value, path)
        requireValue(
            SnapshotFormatValidation.codePointLength(value) in minimum..maximum,
            path,
            "invalid_length",
        )
    }

    private fun maxLength(value: String?, path: String, maximum: Int) {
        if (value != null) {
            requireValue(
                SnapshotFormatValidation.codePointLength(value) <= maximum,
                path,
                "invalid_length",
            )
        }
    }

    private fun uri(value: String, path: String) {
        val parsed = try {
            URI(value)
        } catch (error: Exception) {
            fail(path, "invalid_uri", error)
        }
        requireValue(parsed.isAbsolute, path, "invalid_uri")
    }

    private fun rejectExplicitNulls(element: JsonElement, path: String) {
        when (element) {
            JsonNull -> fail(path, "null_not_allowed")
            is JsonObject -> element.forEach { (name, value) ->
                rejectExplicitNulls(value, "$path.$name")
            }
            is JsonArray -> element.forEachIndexed { index, value ->
                rejectExplicitNulls(value, "$path[$index]")
            }
            else -> Unit
        }
    }

    private fun date(value: String, path: String): LocalDate = try {
        requireValue(datePattern.matches(value), path, "invalid_date")
        LocalDate.parse(value)
    } catch (error: DateTimeException) {
        fail(path, "invalid_date", error)
    }

    private fun localTime(value: String, path: String) {
        requireValue(timePattern.matches(value), path, if (value.isEmpty()) "required" else "invalid_time")
    }

    private fun instant(value: String, path: String): ParsedRfc3339 =
        SnapshotFormatValidation.parseRfc3339(value)
            ?: fail(path, if (value.isBlank()) "required" else "invalid_datetime")

    private fun sha256(value: String, path: String) {
        requireValue(
            SnapshotFormatValidation.isSha256(value),
            path,
            if (value.isEmpty()) "required" else "invalid_sha256",
        )
    }

    private fun prayer(value: String, path: String) {
        requireValue(value in prayers, path, "unsupported_value")
    }

    private fun requireValue(condition: Boolean, path: String, code: String) {
        if (!condition) fail(path, code)
    }

    private fun fail(path: String, code: String, cause: Throwable? = null): Nothing {
        throw SnapshotValidationException(path, code, cause)
    }
}
