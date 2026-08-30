package ru.namaztime.tv.sync

import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed interface DeviceSetupResult<out T> {
    data class Success<T>(val value: T) : DeviceSetupResult<T>
    data object NotProvisioned : DeviceSetupResult<Nothing>
    data object Unauthorized : DeviceSetupResult<Nothing>
    data class Failure(val code: String, val retryable: Boolean) : DeviceSetupResult<Nothing>
}

data class CanonicalCityCandidate(
    val id: String,
    val canonicalName: String,
    val aliases: List<String>,
    val federalSubjectCode: String,
    val federalSubjectName: String,
    val settlementType: String,
    val timezone: String,
    val latitude: Double,
    val longitude: Double,
    val geographicSourceId: String,
    val geographicRevision: String,
    val geographicLicense: String,
)

data class DeviceScheduleAuthority(
    val id: String,
    val name: String,
    val evidenceLabel: String,
)

data class DeviceScheduleSource(
    val id: String,
    val kind: String,
    val status: String,
    val canonicalUrl: String?,
    val freshThrough: LocalDate?,
)

data class DeviceScheduleChoice(
    val id: String,
    val displayLabel: String,
    val authorityLabel: String,
    val tier: String,
    val selectable: Boolean,
    val executable: Boolean,
    val requestable: Boolean,
    val policyId: String,
    val policyKind: String,
    val approvalId: String,
    val effectiveFrom: LocalDate,
    val effectiveTo: LocalDate,
    val scopeId: String,
    val scopeKind: String,
    val scopeDescription: String,
    val authorities: List<DeviceScheduleAuthority>,
    val source: DeviceScheduleSource,
    val scheduleId: String,
    val scheduleKind: String,
    val scheduleTimezone: String?,
    val publishedSnapshotId: String?,
)

data class DeviceCityScheduleChoiceSet(
    val revisionId: String,
    val revisionState: String,
    val status: String,
    val automaticResolutionStatus: String,
    val automaticResolutionReason: String,
    val selectionRequired: Boolean,
    val date: LocalDate,
    val city: CanonicalCityCandidate,
    val choices: List<DeviceScheduleChoice>,
    val requestAllowed: Boolean,
)

data class PendingDeviceScheduleChoiceRequest(
    val id: String,
    val revisionId: String,
    val cityId: String,
    val policyId: String,
    val choiceId: String,
    val mosqueId: String,
    val deviceId: String,
    val date: LocalDate,
    val tier: String,
    val status: String,
    val selectionSha256: String,
    val origin: String,
    val interactionId: String,
    val requestedAt: Instant,
)

fun interface DeviceCitySearchGateway {
    suspend fun searchCities(query: String): DeviceSetupResult<List<CanonicalCityCandidate>>
}

interface DeviceSetupGateway : DeviceCitySearchGateway {
    suspend fun loadScheduleChoices(
        cityId: String,
        date: LocalDate,
    ): DeviceSetupResult<DeviceCityScheduleChoiceSet>

    suspend fun requestScheduleChoice(
        cityId: String,
        choiceId: String,
        date: LocalDate,
        interactionId: String,
    ): DeviceSetupResult<PendingDeviceScheduleChoiceRequest>
}

class DeviceSetupClient(
    private val transport: DeviceSyncTransport,
    private val provisioningStore: DeviceProvisioningStore,
) : DeviceSetupGateway {
    override suspend fun searchCities(
        query: String,
    ): DeviceSetupResult<List<CanonicalCityCandidate>> {
        if (!validSetupQuery(query)) {
            return DeviceSetupResult.Failure("setup_request_invalid", retryable = false)
        }
        val provisioning = when (val loaded = loadProvisioning()) {
            is ProvisioningLoad.Available -> loaded.value
            ProvisioningLoad.Missing -> return DeviceSetupResult.NotProvisioned
            ProvisioningLoad.Failed -> {
                return DeviceSetupResult.Failure("provisioning_store_io", retryable = true)
            }
        }
        val url = deviceSetupUrl(
            provisioning = provisioning,
            suffix = "cities",
            query = "q=" + URLEncoder.encode(query, StandardCharsets.UTF_8.name()),
        ) ?: return DeviceSetupResult.Failure("setup_request_invalid", retryable = false)
        val response = try {
            transport.execute(
                SyncHttpRequest(
                    url = url,
                    bearerToken = provisioning.credentials.token,
                    maximumBodyBytes = MAX_DEVICE_SETUP_RESPONSE_BYTES,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            return DeviceSetupResult.Failure("setup_io", retryable = true)
        }
        if (response.body.size > MAX_DEVICE_SETUP_RESPONSE_BYTES) {
            return DeviceSetupResult.Failure("setup_response_invalid", retryable = false)
        }
        when (response.statusCode) {
            401, 403 -> return DeviceSetupResult.Unauthorized
            in 500..599 -> return DeviceSetupResult.Failure("setup_server_error", retryable = true)
            200 -> Unit
            else -> return DeviceSetupResult.Failure("setup_unavailable", retryable = false)
        }
        val document = try {
            deviceSetupJson.decodeFromString<DeviceCitySearchDocument>(
                response.body.decodeToString(throwOnInvalidSequence = true),
            )
        } catch (_: Exception) {
            return DeviceSetupResult.Failure("setup_response_invalid", retryable = false)
        }
        if (document.schemaVersion != DEVICE_CITY_SEARCH_SCHEMA || document.query != query) {
            return DeviceSetupResult.Failure("setup_response_invalid", retryable = false)
        }
        val candidates = document.candidates.map(DeviceCityCandidateDocument::toCandidate)
        if (candidates.any { !it.isValid() } || candidates.map { it.id }.distinct().size != candidates.size) {
            return DeviceSetupResult.Failure("setup_response_invalid", retryable = false)
        }
        return DeviceSetupResult.Success(candidates)
    }

    override suspend fun loadScheduleChoices(
        cityId: String,
        date: LocalDate,
    ): DeviceSetupResult<DeviceCityScheduleChoiceSet> {
        if (!validSetupIdentifier(cityId)) {
            return DeviceSetupResult.Failure("setup_request_invalid", retryable = false)
        }
        val provisioning = when (val loaded = loadProvisioning()) {
            is ProvisioningLoad.Available -> loaded.value
            ProvisioningLoad.Missing -> return DeviceSetupResult.NotProvisioned
            ProvisioningLoad.Failed -> {
                return DeviceSetupResult.Failure("provisioning_store_io", retryable = true)
            }
        }
        val query = buildString {
            append("city_id=")
            append(URLEncoder.encode(cityId, StandardCharsets.UTF_8.name()))
            append("&date=")
            append(date)
        }
        val url = deviceSetupUrl(provisioning, "schedule-choices", query)
            ?: return DeviceSetupResult.Failure("setup_request_invalid", retryable = false)
        val response = executeSetupRequest(
            request = SyncHttpRequest(
                url = url,
                bearerToken = provisioning.credentials.token,
                maximumBodyBytes = MAX_DEVICE_SETUP_RESPONSE_BYTES,
            ),
        ) ?: return DeviceSetupResult.Failure("setup_io", retryable = true)
        when (response.statusCode) {
            401, 403 -> return DeviceSetupResult.Unauthorized
            in 500..599 -> return DeviceSetupResult.Failure("setup_server_error", retryable = true)
            200 -> Unit
            else -> return DeviceSetupResult.Failure("setup_unavailable", retryable = false)
        }
        if (response.body.size > MAX_DEVICE_SETUP_RESPONSE_BYTES) {
            return DeviceSetupResult.Failure("setup_response_invalid", retryable = false)
        }
        val document = decodeSetupDocument<DeviceCityScheduleChoicesDocument>(response.body)
            ?: return DeviceSetupResult.Failure("setup_response_invalid", retryable = false)
        val projected = document.toChoiceSet(cityId, date)
            ?: return DeviceSetupResult.Failure("setup_response_invalid", retryable = false)
        return DeviceSetupResult.Success(projected)
    }

    override suspend fun requestScheduleChoice(
        cityId: String,
        choiceId: String,
        date: LocalDate,
        interactionId: String,
    ): DeviceSetupResult<PendingDeviceScheduleChoiceRequest> {
        if (!validSetupIdentifier(cityId) || !CHOICE_ID_PATTERN.matches(choiceId) ||
            !interactionId.isBoundedText(8, 128)
        ) {
            return DeviceSetupResult.Failure("setup_request_invalid", retryable = false)
        }
        val provisioning = when (val loaded = loadProvisioning()) {
            is ProvisioningLoad.Available -> loaded.value
            ProvisioningLoad.Missing -> return DeviceSetupResult.NotProvisioned
            ProvisioningLoad.Failed -> {
                return DeviceSetupResult.Failure("provisioning_store_io", retryable = true)
            }
        }
        val url = deviceSetupUrl(provisioning, "schedule-choice-requests", query = null)
            ?: return DeviceSetupResult.Failure("setup_request_invalid", retryable = false)
        val input = DeviceScheduleChoiceRequestInput(cityId, choiceId, date.toString(), interactionId)
        val body = deviceSetupJson.encodeToString(input).encodeToByteArray()
        if (body.size > MAX_DEVICE_SETUP_REQUEST_BYTES) {
            return DeviceSetupResult.Failure("setup_request_invalid", retryable = false)
        }
        val response = executeSetupRequest(
            request = SyncHttpRequest(
                url = url,
                bearerToken = provisioning.credentials.token,
                maximumBodyBytes = MAX_DEVICE_SETUP_RESPONSE_BYTES,
                method = "POST",
                contentType = "application/json",
                body = body,
            ),
        ) ?: return DeviceSetupResult.Failure("setup_io", retryable = true)
        when (response.statusCode) {
            401, 403 -> return DeviceSetupResult.Unauthorized
            409 -> return DeviceSetupResult.Failure("setup_choice_not_requestable", retryable = false)
            in 500..599 -> return DeviceSetupResult.Failure("setup_server_error", retryable = true)
            201 -> Unit
            else -> return DeviceSetupResult.Failure("setup_unavailable", retryable = false)
        }
        if (response.body.size > MAX_DEVICE_SETUP_RESPONSE_BYTES) {
            return DeviceSetupResult.Failure("setup_response_invalid", retryable = false)
        }
        val document = decodeSetupDocument<DeviceScheduleChoiceRequestDocument>(response.body)
            ?: return DeviceSetupResult.Failure("setup_response_invalid", retryable = false)
        val pending = document.toPendingRequest(
            provisioning = provisioning,
            expectedCityId = cityId,
            expectedChoiceId = choiceId,
            expectedDate = date,
            expectedInteractionId = interactionId,
        ) ?: return DeviceSetupResult.Failure("setup_response_invalid", retryable = false)
        return DeviceSetupResult.Success(pending)
    }

    private suspend fun executeSetupRequest(
        request: SyncHttpRequest,
    ): SyncHttpResponse? = try {
        transport.execute(request)
    } catch (error: CancellationException) {
        throw error
    } catch (_: IOException) {
        null
    }

    private suspend fun loadProvisioning(): ProvisioningLoad = try {
        withContext(Dispatchers.IO) {
            provisioningStore.load()?.let(ProvisioningLoad::Available) ?: ProvisioningLoad.Missing
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: IOException) {
        ProvisioningLoad.Failed
    }
}

private sealed interface ProvisioningLoad {
    data class Available(val value: DeviceProvisioning) : ProvisioningLoad
    data object Missing : ProvisioningLoad
    data object Failed : ProvisioningLoad
}

@Serializable
private data class DeviceCitySearchDocument(
    @SerialName("schema_version") val schemaVersion: String,
    val query: String,
    val candidates: List<DeviceCityCandidateDocument>,
)

@Serializable
private data class DeviceCityCandidateDocument(
    @SerialName("city_id") val cityId: String,
    @SerialName("canonical_name") val canonicalName: String,
    val aliases: List<String>,
    @SerialName("federal_subject_code") val federalSubjectCode: String,
    @SerialName("federal_subject_name") val federalSubjectName: String,
    @SerialName("settlement_type") val settlementType: String,
    val timezone: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("geographic_source_id") val geographicSourceId: String,
    @SerialName("geographic_revision") val geographicRevision: String,
    @SerialName("geographic_license") val geographicLicense: String,
) {
    fun toCandidate() = CanonicalCityCandidate(
        id = cityId,
        canonicalName = canonicalName,
        aliases = aliases.toList(),
        federalSubjectCode = federalSubjectCode,
        federalSubjectName = federalSubjectName,
        settlementType = settlementType,
        timezone = timezone,
        latitude = latitude,
        longitude = longitude,
        geographicSourceId = geographicSourceId,
        geographicRevision = geographicRevision,
        geographicLicense = geographicLicense,
    )
}

@Serializable
private data class DeviceCityScheduleChoicesDocument(
    @SerialName("schema_version") val schemaVersion: String,
    val revision: RegistryRevisionDocument,
    @SerialName("revision_state") val revisionState: String,
    val status: String,
    @SerialName("automatic_resolution_status") val automaticResolutionStatus: String,
    @SerialName("automatic_resolution_reason") val automaticResolutionReason: String,
    @SerialName("selection_required") val selectionRequired: Boolean,
    val date: String,
    val city: DeviceCityCandidateDocument,
    val choices: List<DeviceScheduleChoiceDocument>,
    @SerialName("allowed_actions") val allowedActions: List<String>,
)

@Serializable
private data class RegistryRevisionDocument(
    val id: String,
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("parent_revision_id") val parentRevisionId: String? = null,
    @SerialName("catalog_revision_id") val catalogRevisionId: String,
    @SerialName("content_sha256") val contentSha256: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("created_by") val createdBy: String,
    val reason: String,
)

@Serializable
private data class DateRangeDocument(
    val from: String,
    val to: String,
)

@Serializable
private data class GeographicScopeDocument(
    val id: String,
    val kind: String,
    @SerialName("city_id") val cityId: String? = null,
    @SerialName("region_id") val regionId: String,
    val description: String,
)

@Serializable
private data class PrayerAuthorityDocument(
    val id: String,
    val name: String,
    val branch: String? = null,
    val website: String? = null,
    @SerialName("evidence_label") val evidenceLabel: String,
)

@Serializable
private data class PrayerSourceDocument(
    val id: String,
    val kind: String,
    @SerialName("authority_ids") val authorityIds: List<String>,
    @SerialName("geographic_scope_id") val geographicScopeId: String,
    @SerialName("canonical_url") val canonicalUrl: String? = null,
    val status: String,
    @SerialName("fresh_through") val freshThrough: String? = null,
)

@Serializable
private data class TimeTableDocument(
    val id: String,
    @SerialName("source_id") val sourceId: String,
    @SerialName("geographic_scope_id") val geographicScopeId: String,
    @SerialName("mosque_id") val mosqueId: String,
    val timezone: String,
    val effective: DateRangeDocument,
    @SerialName("published_snapshot_id") val publishedSnapshotId: String,
    @SerialName("source_override_ids") val sourceOverrideIds: List<String> = emptyList(),
)

@Serializable
private data class CalculationProfileDocument(
    val id: String,
    @SerialName("source_id") val sourceId: String,
    @SerialName("geographic_scope_id") val geographicScopeId: String,
    val version: String,
    val effective: DateRangeDocument,
    @SerialName("approval_id") val approvalId: String,
)

@Serializable
private data class SourceOverrideDocument(
    val id: String,
    @SerialName("base_source_id") val baseSourceId: String,
    @SerialName("override_source_id") val overrideSourceId: String,
    val effective: DateRangeDocument,
    @SerialName("applied_fields") val appliedFields: List<String>,
    @SerialName("approval_id") val approvalId: String,
)

@Serializable
private data class DeviceScheduleChoiceDocument(
    @SerialName("choice_id") val choiceId: String,
    @SerialName("display_label") val displayLabel: String,
    @SerialName("authority_label") val authorityLabel: String,
    val tier: String,
    val selectable: Boolean,
    val executable: Boolean,
    @SerialName("blocked_reason") val blockedReason: String,
    @SerialName("policy_id") val policyId: String,
    @SerialName("policy_kind") val policyKind: String,
    @SerialName("approval_id") val approvalId: String,
    val effective: DateRangeDocument,
    val scope: GeographicScopeDocument,
    val authorities: List<PrayerAuthorityDocument>,
    val source: PrayerSourceDocument,
    @SerialName("timetable_id") val timetableId: String? = null,
    @SerialName("calculation_profile_id") val calculationProfileId: String? = null,
    val timetable: TimeTableDocument? = null,
    @SerialName("calculation_profile") val calculationProfile: CalculationProfileDocument? = null,
    @SerialName("source_overrides") val sourceOverrides: List<SourceOverrideDocument>,
)

@Serializable
private data class DeviceScheduleChoiceRequestInput(
    @SerialName("city_id") val cityId: String,
    @SerialName("choice_id") val choiceId: String,
    val date: String,
    @SerialName("interaction_id") val interactionId: String,
)

@Serializable
private data class DeviceScheduleChoiceRequestDocument(
    @SerialName("schema_version") val schemaVersion: String,
    val request: DeviceRegistryBindingRequestDocument,
)

@Serializable
private data class DeviceRegistryBindingRequestDocument(
    val id: String,
    @SerialName("revision_id") val revisionId: String,
    @SerialName("city_id") val cityId: String,
    @SerialName("policy_id") val policyId: String,
    @SerialName("choice_id") val choiceId: String,
    @SerialName("mosque_id") val mosqueId: String,
    @SerialName("device_id") val deviceId: String,
    val date: String,
    @SerialName("resolution_tier") val resolutionTier: String,
    val status: String,
    @SerialName("selection_sha256") val selectionSha256: String,
    val origin: String,
    @SerialName("interaction_id") val interactionId: String,
    @SerialName("requested_at") val requestedAt: String,
)

private inline fun <reified T> decodeSetupDocument(body: ByteArray): T? = try {
    deviceSetupJson.decodeFromString<T>(body.decodeToString(throwOnInvalidSequence = true))
} catch (_: Exception) {
    null
}

private fun DeviceCityScheduleChoicesDocument.toChoiceSet(
    expectedCityId: String,
    expectedDate: LocalDate,
): DeviceCityScheduleChoiceSet? {
    if (schemaVersion != DEVICE_CITY_SCHEDULE_CHOICES_SCHEMA ||
        date.toLocalDateOrNull() != expectedDate || city.cityId != expectedCityId ||
        !revision.isValid() || revisionState !in REVISION_STATES ||
        status !in CHOICE_SET_STATUSES || automaticResolutionStatus !in ASSESSMENT_STATUSES ||
        automaticResolutionReason !in ASSESSMENT_REASONS || !city.toCandidate().isValid() ||
        allowedActions.size != allowedActions.distinct().size ||
        allowedActions.any { it != REQUEST_SELECTION_ACTION }
    ) {
        return null
    }
    val requestAllowed = REQUEST_SELECTION_ACTION in allowedActions
    if ((revisionState == "staged" && choices.isNotEmpty()) != requestAllowed ||
        (status == "available") != choices.isNotEmpty() || selectionRequired != (choices.size > 1)
    ) {
        return null
    }
    val projected = choices.map { choice ->
        choice.toChoice(
            expectedCityId = expectedCityId,
            expectedDate = expectedDate,
            requestable = requestAllowed,
        ) ?: return null
    }
    if (projected.map { it.id }.distinct().size != projected.size ||
        projected.map { it.policyId }.distinct().size != projected.size ||
        projected.map { it.tier }.distinct().size > 1 ||
        (revisionState == "staged" && projected.any { it.executable })
    ) {
        return null
    }
    val executableCount = projected.count { it.executable }
    if (executableCount > 1 ||
        (revisionState == "active" && automaticResolutionStatus == "resolved" &&
            (projected.size != 1 || executableCount != 1)) ||
        (automaticResolutionStatus == "ambiguous" && executableCount != 0)
    ) {
        return null
    }
    when (projected.size) {
        0 -> if (automaticResolutionStatus !in setOf("stale", "unavailable")) return null
        1 -> if (automaticResolutionStatus != "resolved" || automaticResolutionReason != "resolved") return null
        else -> if (automaticResolutionStatus != "ambiguous" ||
            automaticResolutionReason != "same_tier_ambiguous"
        ) {
            return null
        }
    }
    return DeviceCityScheduleChoiceSet(
        revisionId = revision.id,
        revisionState = revisionState,
        status = status,
        automaticResolutionStatus = automaticResolutionStatus,
        automaticResolutionReason = automaticResolutionReason,
        selectionRequired = selectionRequired,
        date = expectedDate,
        city = city.toCandidate(),
        choices = projected,
        requestAllowed = requestAllowed,
    )
}

private fun DeviceScheduleChoiceDocument.toChoice(
    expectedCityId: String,
    expectedDate: LocalDate,
    requestable: Boolean,
): DeviceScheduleChoice? {
    val effectiveRange = effective.toRangeOrNull() ?: return null
    if (!CHOICE_ID_PATTERN.matches(choiceId) || !displayLabel.isBoundedText(1, 500) ||
        !authorityLabel.isBoundedText(1, 500) || tier !in RESOLUTION_TIERS ||
        !selectable || blockedReason != "eligible" || !validSetupIdentifier(policyId) ||
        policyKind !in POLICY_KINDS || !validSetupIdentifier(approvalId) ||
        expectedDate !in effectiveRange || !scope.isValidFor(expectedCityId) ||
        authorities.isEmpty() || authorities.map { it.id }.distinct().size != authorities.size ||
        authorities.any { !it.isValid() } || !source.isValid(expectedDate) ||
        source.geographicScopeId != scope.id ||
        source.authorityIds.toSet() != authorities.map { it.id }.toSet() ||
        sourceOverrides.map { it.id }.distinct().size != sourceOverrides.size ||
        sourceOverrides.any { !it.isValid(expectedDate) }
    ) {
        return null
    }
    val scheduleId: String
    val scheduleKind: String
    val scheduleTimezone: String?
    val publishedSnapshotId: String?
    when (policyKind) {
        "timetable" -> {
            val schedule = timetable ?: return null
            if (timetableId == null || timetableId != schedule.id ||
                calculationProfileId != null || calculationProfile != null ||
                !schedule.isValid(source.id, scope.id, expectedDate, sourceOverrides.map { it.id })
            ) {
                return null
            }
            scheduleId = schedule.id
            scheduleKind = "timetable"
            scheduleTimezone = schedule.timezone
            publishedSnapshotId = schedule.publishedSnapshotId
        }
        "calculation_profile" -> {
            val schedule = calculationProfile ?: return null
            if (calculationProfileId == null || calculationProfileId != schedule.id ||
                timetableId != null || timetable != null ||
                !schedule.isValid(source.id, scope.id, expectedDate)
            ) {
                return null
            }
            scheduleId = schedule.id
            scheduleKind = "calculation_profile"
            scheduleTimezone = null
            publishedSnapshotId = null
        }
        else -> return null
    }
    return DeviceScheduleChoice(
        id = choiceId,
        displayLabel = displayLabel,
        authorityLabel = authorityLabel,
        tier = tier,
        selectable = selectable,
        executable = executable,
        requestable = requestable && !executable,
        policyId = policyId,
        policyKind = policyKind,
        approvalId = approvalId,
        effectiveFrom = effectiveRange.start,
        effectiveTo = effectiveRange.endInclusive,
        scopeId = scope.id,
        scopeKind = scope.kind,
        scopeDescription = scope.description,
        authorities = authorities.map {
            DeviceScheduleAuthority(it.id, it.name, it.evidenceLabel)
        },
        source = DeviceScheduleSource(
            id = source.id,
            kind = source.kind,
            status = source.status,
            canonicalUrl = source.canonicalUrl,
            freshThrough = source.freshThrough?.toLocalDateOrNull(),
        ),
        scheduleId = scheduleId,
        scheduleKind = scheduleKind,
        scheduleTimezone = scheduleTimezone,
        publishedSnapshotId = publishedSnapshotId,
    )
}

private fun DeviceScheduleChoiceRequestDocument.toPendingRequest(
    provisioning: DeviceProvisioning,
    expectedCityId: String,
    expectedChoiceId: String,
    expectedDate: LocalDate,
    expectedInteractionId: String,
): PendingDeviceScheduleChoiceRequest? {
    val item = request
    val parsedDate = item.date.toLocalDateOrNull()
    val parsedRequestedAt = item.requestedAt.toInstantOrNull()
    if (schemaVersion != DEVICE_SCHEDULE_CHOICE_REQUEST_SCHEMA ||
        !item.id.isBoundedText(8, 128) || !validSetupIdentifier(item.revisionId) ||
        item.cityId != expectedCityId || !validSetupIdentifier(item.policyId) ||
        item.choiceId != expectedChoiceId || item.mosqueId != provisioning.mosqueId ||
        item.deviceId != provisioning.credentials.deviceId || parsedDate != expectedDate ||
        item.resolutionTier !in RESOLUTION_TIERS || item.status != "pending_review" ||
        !SHA256_PATTERN.matches(item.selectionSha256) || item.origin != "local_tv_operator" ||
        item.interactionId != expectedInteractionId || parsedRequestedAt == null
    ) {
        return null
    }
    return PendingDeviceScheduleChoiceRequest(
        id = item.id,
        revisionId = item.revisionId,
        cityId = item.cityId,
        policyId = item.policyId,
        choiceId = item.choiceId,
        mosqueId = item.mosqueId,
        deviceId = item.deviceId,
        date = parsedDate,
        tier = item.resolutionTier,
        status = item.status,
        selectionSha256 = item.selectionSha256,
        origin = item.origin,
        interactionId = item.interactionId,
        requestedAt = parsedRequestedAt,
    )
}

private fun RegistryRevisionDocument.isValid(): Boolean =
    validSetupIdentifier(id) && schemaVersion >= 1 &&
        (parentRevisionId == null || validSetupIdentifier(parentRevisionId)) &&
        validSetupIdentifier(catalogRevisionId) && SHA256_PATTERN.matches(contentSha256) &&
        createdAt.toInstantOrNull() != null && createdBy.isBoundedText(1, 240) &&
        reason.isBoundedText(1, 1_000)

private fun GeographicScopeDocument.isValidFor(expectedCityId: String): Boolean =
    validSetupIdentifier(id) && kind in setOf("city", "region") &&
        validSetupIdentifier(regionId) && description.isBoundedText(1, 500) &&
        when (kind) {
            "city" -> cityId == expectedCityId
            "region" -> cityId == null
            else -> false
        }

private fun PrayerAuthorityDocument.isValid(): Boolean =
    validSetupIdentifier(id) && name.isBoundedText(1, 500) &&
        (branch == null || branch.isBoundedText(1, 500)) &&
        (website == null || website.isSafeReferenceUri()) && evidenceLabel in EVIDENCE_LABELS

private fun PrayerSourceDocument.isValid(expectedDate: LocalDate): Boolean =
    validSetupIdentifier(id) && kind in SOURCE_KINDS && authorityIds.isNotEmpty() &&
        authorityIds.size == authorityIds.distinct().size && authorityIds.all(::validSetupIdentifier) &&
        validSetupIdentifier(geographicScopeId) &&
        (canonicalUrl == null || canonicalUrl.isSafeReferenceUri()) && status == "approved" &&
        (freshThrough == null || (freshThrough.toLocalDateOrNull()?.let { !it.isBefore(expectedDate) } == true))

private fun TimeTableDocument.isValid(
    expectedSourceId: String,
    expectedScopeId: String,
    expectedDate: LocalDate,
    expectedOverrideIds: List<String>,
): Boolean {
    val range = effective.toRangeOrNull() ?: return false
    return validSetupIdentifier(id) && sourceId == expectedSourceId &&
        geographicScopeId == expectedScopeId && validSetupIdentifier(mosqueId) &&
        isNamedSetupZone(timezone) && expectedDate in range &&
        validSetupIdentifier(publishedSnapshotId) &&
        sourceOverrideIds.size == sourceOverrideIds.distinct().size &&
        sourceOverrideIds == expectedOverrideIds
}

private fun CalculationProfileDocument.isValid(
    expectedSourceId: String,
    expectedScopeId: String,
    expectedDate: LocalDate,
): Boolean {
    val range = effective.toRangeOrNull() ?: return false
    return validSetupIdentifier(id) && sourceId == expectedSourceId &&
        geographicScopeId == expectedScopeId && version.isBoundedText(1, 160) &&
        expectedDate in range && validSetupIdentifier(approvalId)
}

private fun SourceOverrideDocument.isValid(expectedDate: LocalDate): Boolean {
    val range = effective.toRangeOrNull() ?: return false
    return validSetupIdentifier(id) && validSetupIdentifier(baseSourceId) &&
        validSetupIdentifier(overrideSourceId) && expectedDate in range &&
        appliedFields.isNotEmpty() && appliedFields.size == appliedFields.distinct().size &&
        appliedFields.all { it.isBoundedText(1, 160) } && validSetupIdentifier(approvalId)
}

private fun DateRangeDocument.toRangeOrNull(): ClosedRange<LocalDate>? {
    val start = from.toLocalDateOrNull() ?: return null
    val end = to.toLocalDateOrNull() ?: return null
    return if (start <= end) start..end else null
}

private fun String.toLocalDateOrNull(): LocalDate? = try {
    LocalDate.parse(this).takeIf { it.toString() == this }
} catch (_: Exception) {
    null
}

private fun String.toInstantOrNull(): Instant? = try {
    Instant.parse(this)
} catch (_: Exception) {
    null
}

private fun String.isSafeReferenceUri(): Boolean = try {
    val parsed = URI(this)
    parsed.isAbsolute && parsed.scheme in setOf("https", "http") && parsed.host != null &&
        parsed.userInfo == null && parsed.fragment == null
} catch (_: Exception) {
    false
}

private fun CanonicalCityCandidate.isValid(): Boolean =
    id.isBoundedText(1, 160) && canonicalName.isBoundedText(1, 240) &&
        aliases.size == aliases.distinct().size && aliases.all { it.isBoundedText(1, 240) } &&
        federalSubjectCode.matches(Regex("^RU-[A-Z]{2,3}$")) &&
        federalSubjectName.isBoundedText(1, 240) && settlementType.isBoundedText(1, 32) &&
        isNamedSetupZone(timezone) && latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
        geographicSourceId.isBoundedText(1, 300) &&
        geographicRevision.isBoundedText(1, 200) && geographicLicense.isBoundedText(1, 300)

private fun String.isBoundedText(minimum: Int, maximum: Int): Boolean =
    length in minimum..maximum && trim() == this && none { it == '\u0000' || it == '\r' || it == '\n' }

private fun validSetupQuery(value: String): Boolean =
    value.isBoundedText(1, 400) && value.codePointCount(0, value.length) <= 200

private fun validSetupIdentifier(value: String): Boolean = value.isBoundedText(1, 160)

private fun isNamedSetupZone(value: String): Boolean = try {
    ZoneId.getAvailableZoneIds().contains(value) && ZoneId.of(value).id == value
} catch (_: Exception) {
    false
}

private fun deviceSetupUrl(
    provisioning: DeviceProvisioning,
    suffix: String,
    query: String?,
): String? = try {
    val manifest = URI(provisioning.credentials.manifestUrl)
    if (manifest.scheme != "https" || manifest.host == null || manifest.userInfo != null ||
        manifest.fragment != null || provisioning.credentials.deviceId.length !in 8..128 ||
        suffix !in setOf("cities", "schedule-choices", "schedule-choice-requests")
    ) {
        null
    } else {
        val origin = URI("https", null, manifest.host, manifest.port, null, null, null).toASCIIString()
        val deviceSegment = percentEncodePathSegment(provisioning.credentials.deviceId)
        buildString {
            append(origin)
            append("/v1/devices/")
            append(deviceSegment)
            append("/setup/")
            append(suffix)
            query?.let {
                append('?')
                append(it)
            }
        }
    }
} catch (_: Exception) {
    null
}

private fun percentEncodePathSegment(value: String): String = buildString {
    value.encodeToByteArray().forEach { byte ->
        val unsigned = byte.toInt() and 0xff
        val character = unsigned.toChar()
        if ((character in 'a'..'z') || (character in 'A'..'Z') ||
            (character in '0'..'9') || character in "-._~"
        ) {
            append(character)
        } else {
            append('%')
            append(HEX_DIGITS[unsigned ushr 4])
            append(HEX_DIGITS[unsigned and 0x0f])
        }
    }
}

private val deviceSetupJson = Json {
    ignoreUnknownKeys = false
    isLenient = false
    coerceInputValues = false
    explicitNulls = false
}

private const val DEVICE_CITY_SEARCH_SCHEMA = "device-city-search/v1"
private const val DEVICE_CITY_SCHEDULE_CHOICES_SCHEMA = "device-city-schedule-choices/v1"
private const val DEVICE_SCHEDULE_CHOICE_REQUEST_SCHEMA = "device-schedule-choice-request/v1"
private const val REQUEST_SELECTION_ACTION = "request_selection"
private const val MAX_DEVICE_SETUP_RESPONSE_BYTES = 512 * 1024
private const val MAX_DEVICE_SETUP_REQUEST_BYTES = 16 * 1024
private const val HEX_DIGITS = "0123456789ABCDEF"

private val CHOICE_ID_PATTERN = Regex("^schedule-choice-[0-9a-f]{64}$")
private val SHA256_PATTERN = Regex("^[0-9a-f]{64}$")
private val REVISION_STATES = setOf("staged", "active")
private val CHOICE_SET_STATUSES = setOf("available", "unavailable")
private val ASSESSMENT_STATUSES = setOf("resolved", "ambiguous", "stale", "unavailable")
private val ASSESSMENT_REASONS = setOf(
    "resolved",
    "same_tier_ambiguous",
    "stale_source",
    "source_unavailable",
    "source_not_approved",
    "schedule_unavailable",
    "policy_out_of_range",
    "no_policy",
)
private val RESOLUTION_TIERS = setOf(
    "exact_city_timetable",
    "regional_official_timetable",
    "approved_regional_calculation_profile",
    "explicitly_configured_fallback",
)
private val POLICY_KINDS = setOf("timetable", "calculation_profile")
private val SOURCE_KINDS = setOf(
    "official_api",
    "official_file",
    "official_html",
    "mosque_calendar",
    "calculation_profile",
    "manual_import",
)
private val EVIDENCE_LABELS = setOf(
    "CONFIRMED_PUBLIC",
    "CONFIRMED_STATIC",
    "CONFIRMED_RUNTIME",
    "INFERENCE",
    "PROPOSAL",
    "UNKNOWN",
)
