package ru.namaztime.tv.sync

import android.content.res.AssetManager
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import ru.namaztime.tv.data.snapshot.*

internal interface LocalSetupBundleFiles {
    fun paths(): Set<String>
    fun open(path: String): InputStream
}

internal class AssetLocalSetupBundleFiles(private val assets: AssetManager) : LocalSetupBundleFiles {
    override fun paths(): Set<String> {
        val result = mutableSetOf<String>()
        fun visit(relative: String, depth: Int) {
            requireBundle(depth <= 3, "setup_local_inventory_invalid")
            val children = assets.list("public-setup" + if (relative.isEmpty()) "" else "/$relative").orEmpty()
            for (name in children) {
                requireBundle(name.isNotEmpty() && '/' !in name && name !in setOf(".", ".."), "setup_local_inventory_invalid")
                val path = if (relative.isEmpty()) name else "$relative/$name"
                val descendants = assets.list("public-setup/$path").orEmpty()
                if (descendants.isEmpty()) result += path else visit(path, depth + 1)
                requireBundle(result.size <= 1031, "setup_local_inventory_invalid")
            }
        }
        visit("", 0)
        return result
    }

    override fun open(path: String): InputStream = assets.open("public-setup/$path", AssetManager.ACCESS_STREAMING)
}

internal class LocalSetupBundleException(val code: String) : IllegalArgumentException(code)

internal val LOCAL_SETUP_TRUST_ANCHORS = mapOf(
    "trust/production.json" to "2fc9b7a34cbba4bf57ba5242aec6b782d41877ff6863006e83a3d40bc34eb085",
    "trust/previous-production.json" to "55d58bef5426876b8f47be721409d211644cf5bcbad24ddfdc7903ee5549c4a7",
    "trust/test.json" to "82c7e7e943f5796ab689265a2d24862fc1f869f5ea574f7a940d2adaf74c1f77",
    "trust/staging.json" to "db4d936d6894d6ff60bfa422c12a886bfc0f553b47a7e1201fa9e2560b25d0bd",
)

/** Only the debug composition root supplies APK assets. Alternate anchors are test-only. */
internal class LocalSetupBundleLoader(
    private val files: LocalSetupBundleFiles,
    private val cacheDirectory: File,
    private val trustAnchors: Map<String, String> = LOCAL_SETUP_TRUST_ANCHORS,
) {
    fun load(): LocalSetupBundle {
        val paths = files.paths()
        if (paths.isEmpty()) throw LocalSetupBundleException("setup_local_not_configured")
        requireBundle("manifest.json" in paths, "setup_local_inventory_invalid")
        val rawManifest = parseBundleJson(readBounded("manifest.json", 512 * 1024))
        val manifest = bundleJson.decodeFromJsonElement<LocalSetupManifest>(rawManifest)
        val hash = localSetupHash(canonicalJson(JsonObject(rawManifest - setOf("bundle_id", "manifest_sha256"))))
        requireBundle(manifest.schemaVersion == "namaztime-local-setup-bundle/v1" &&
            manifest.manifestSha256 == hash && manifest.bundleId == "local-setup-" + hash.take(32) &&
            manifest.minimumTrustRevision == 3L && manifest.registryState == "active", "setup_local_manifest_invalid")
        val createdAt = canonicalBundleInstant(manifest.createdAt)
        requireBundle(manifest.admission.kind == "persistent_service_verified_local" &&
            manifest.admission.verifiedAt == manifest.createdAt && manifest.admission.actorId.isNotBlank() &&
            manifest.admission.reason.isNotBlank(), "setup_local_admission_invalid")
        manifest.registryRevision.exactBundleKeys(
            setOf("id", "schema_version", "catalog_revision_id", "content_sha256", "created_at", "created_by", "reason"),
            setOf("parent_revision_id"),
        )
        requireBundle(manifest.registryRevision.bundleString("catalog_revision_id") == manifest.catalog.revisionId &&
            canonicalBundleInstant(manifest.registryRevision.bundleString("created_at")) <= createdAt &&
            manifest.registryRevision.getValue("schema_version").jsonPrimitive.int in 1..2 &&
            isBundleHash(manifest.registryRevision.bundleString("content_sha256")) &&
            manifest.catalog.regionCount > 0 && manifest.catalog.cityCount > 0 && manifest.catalog.aliasCount >= 0 &&
            manifest.catalog.searchNameCount >= manifest.catalog.cityCount && isBundleHash(manifest.catalog.contentSha256) &&
            manifest.catalog.license.isNotBlank() && manifest.catalog.licenseUrl.startsWith("https://") &&
            manifest.catalog.attribution.isNotBlank(), "setup_local_catalog_invalid")
        val inventory = manifest.files.associateBy { it.path }
        requireBundle(manifest.files.map { it.path } == inventory.keys.sorted() &&
            inventory.size == manifest.files.size && paths == inventory.keys + "manifest.json" &&
            inventory.keys.containsAll(trustAnchors.keys + setOf("catalog.sqlite", "choices.json")) &&
            trustAnchors.keys == LOCAL_SETUP_TRUST_ANCHORS.keys,
            "setup_local_inventory_invalid")
        var total = 0L
        var snapshotCount = 0
        inventory.values.forEach { item ->
            val limit = when {
                item.path == "catalog.sqlite" -> 128L * 1024 * 1024
                item.path == "choices.json" -> 16L * 1024 * 1024
                item.path in trustAnchors -> 256L * 1024
                item.path.matches(Regex("snapshots/[0-9a-f]{64}\\.json")) -> {
                    snapshotCount++
                    requireBundle(item.path == "snapshots/${item.sha256}.json", "setup_local_inventory_invalid")
                    5L * 1024 * 1024
                }
                else -> throw LocalSetupBundleException("setup_local_inventory_invalid")
            }
            requireBundle(item.byteLength in 1..limit && isBundleHash(item.sha256), "setup_local_size_invalid")
            total += item.byteLength
        }
        requireBundle(snapshotCount <= 1024 && total <= 256L * 1024 * 1024 - 512 * 1024, "setup_local_size_invalid")

        fun readChecked(path: String): ByteArray {
            val reference = inventory[path] ?: throw LocalSetupBundleException("setup_local_reference_invalid")
            val bytes = readBounded(path, reference.byteLength.toInt())
            requireBundle(bytes.size.toLong() == reference.byteLength && localSetupHash(bytes) == reference.sha256,
                "setup_local_hash_mismatch")
            return bytes
        }
        val trust = trustAnchors.mapValues { (path, expectedHash) ->
            readChecked(path).also { requireBundle(localSetupHash(it) == expectedHash, "setup_local_trust_anchor_mismatch") }
        }
        val verifier = SnapshotAuthenticityVerifier(
            trust.getValue("trust/production.json"), minimumTrustBundleRevision = 3,
            previousTrustBundle = trust.getValue("trust/previous-production.json"),
            environmentTrustBundles = listOf(trust.getValue("trust/test.json"), trust.getValue("trust/staging.json")),
        )
        val choices = bundleJson.decodeFromJsonElement<LocalSetupChoices>(parseBundleJson(readChecked("choices.json")))
        requireBundle(choices.schemaVersion == "namaztime-local-setup-choices/v1" &&
            choices.registryRevisionId == manifest.registryRevision.bundleString("id") &&
            choices.policies.map { it.policyId } == choices.policies.map { it.policyId }.distinct().sorted(),
            "setup_local_choices_invalid")
        val snapshots = mutableMapOf<String, ActivatableSnapshot>()
        choices.policies.forEach { policy ->
            val ref = policy.snapshot
            requireBundle(inventory[ref.path] == LocalSetupFile(ref.path, ref.byteLength, ref.sha256) &&
                ref.path == "snapshots/${ref.sha256}.json", "setup_local_reference_invalid")
            val verified = snapshots.getOrPut(ref.path) { SnapshotActivationGate.authenticated(readChecked(ref.path), verifier) }
            val snapshot = verified.payload
            requireBundle(snapshot.dataClassification == "production" && snapshot.snapshotId == ref.snapshotId &&
                snapshot.mosque == ref.displayContext && canonicalBundleInstant(snapshot.generatedAt) <= createdAt,
                "setup_local_snapshot_binding_invalid")
            policy.validateSnapshot(snapshot)
        }
        requireBundle(snapshots.keys == inventory.keys.filter { it.startsWith("snapshots/") }.toSet(),
            "setup_local_reference_invalid")
        val indexFile = copyIndex(inventory.getValue("catalog.sqlite"))
        val index = LocalSetupCityIndex(indexFile, manifest.catalog)
        try {
            val result = LocalSetupBundle(manifest, choices, index, snapshots)
            result.validateBindings()
            return result
        } catch (failure: Throwable) {
            index.close()
            throw failure
        }
    }

    private fun copyIndex(reference: LocalSetupFile): File {
        requireBundle(cacheDirectory.isDirectory || cacheDirectory.mkdirs(), "setup_local_storage_io")
        val destination = File(cacheDirectory, "catalog-${reference.sha256}.sqlite")
        if (destination.isFile) {
            destination.inputStream().use { input ->
                requireBundle(hashBundleStream(input, reference.byteLength) == reference.sha256, "setup_local_cached_hash_mismatch")
            }
            return destination
        }
        val temporary = File.createTempFile("catalog-import-", ".tmp", cacheDirectory)
        try {
            val hash = files.open(reference.path).use { input ->
                FileOutputStream(temporary).use { output ->
                    hashBundleStream(input, reference.byteLength, output).also { output.fd.sync() }
                }
            }
            requireBundle(hash == reference.sha256, "setup_local_hash_mismatch")
            requireBundle(temporary.renameTo(destination), "setup_local_storage_io")
            return destination
        } finally {
            if (temporary.exists()) temporary.delete() // Only this operation's uncommitted temporary file.
        }
    }

    private fun readBounded(path: String, limit: Int): ByteArray = files.open(path).use { input ->
        val output = ByteArrayOutputStream(minOf(limit, 64 * 1024))
        val buffer = ByteArray(16 * 1024)
        var count = 0
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            count += read
            requireBundle(count <= limit, "setup_local_size_invalid")
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    }
}

internal class LocalSetupBundle(
    private val manifest: LocalSetupManifest,
    private val document: LocalSetupChoices,
    private val index: LocalSetupCityIndex,
    private val snapshots: Map<String, ActivatableSnapshot>,
) : Closeable {
    val id: String get() = manifest.bundleId
    private val policies = document.policies.associateBy { it.policyId }
    private val bindings = document.bindings.groupBy { it.cityId }

    fun searchCities(query: String): List<CanonicalCityCandidate> = index.search(normalizeLocalSetupQuery(query))

    fun choiceSet(cityId: String, date: LocalDate): DeviceCityScheduleChoiceSet {
        val city = index.city(cityId) ?: throw LocalSetupBundleException("setup_local_city_unknown")
        val applicable = bindings[cityId].orEmpty().filter { date in it.effective.bundleRange() }
        val options = applicable.map { binding -> policies.getValue(binding.policyId).choiceJson(binding) }
        val reason = when (options.size) {
            0 -> "no_policy"
            1 -> "resolved"
            else -> if (options.map { it.getValue("authorities") }.distinct().size > 1) "multiple_authorities" else "same_tier_ambiguous"
        }
        val response = buildJsonObject {
            put("schema_version", if (manifest.registryRevision.getValue("schema_version").jsonPrimitive.int == 2) "device-city-schedule-choices/v2" else "device-city-schedule-choices/v1")
            put("revision", manifest.registryRevision)
            put("revision_state", "active")
            put("status", if (options.isEmpty()) "unavailable" else "available")
            put("automatic_resolution_status", when (options.size) { 0 -> "unavailable"; 1 -> "resolved"; else -> "ambiguous" })
            put("automatic_resolution_reason", reason)
            put("selection_required", options.size > 1)
            put("date", date.toString())
            put("city", city.candidateJson())
            put("choices", JsonArray(options))
            put("allowed_actions", JsonArray(emptyList()))
        }
        return decodeDeviceScheduleChoiceSet(response.toString().encodeToByteArray(), cityId, date)
            ?: throw LocalSetupBundleException("setup_local_choices_invalid")
    }

    fun snapshotFor(cityId: String, choiceId: String, date: LocalDate): ActivatableSnapshot {
        val binding = bindings[cityId].orEmpty().singleOrNull { it.choiceId == choiceId && date in it.effective.bundleRange() }
            ?: throw LocalSetupBundleException("setup_local_choice_unavailable")
        return snapshots.getValue(policies.getValue(binding.policyId).snapshot.path)
    }

    internal fun validateBindings() {
        val sorted = document.bindings.sortedWith(compareBy({ it.cityId }, { it.choiceId }, { it.effective.from }))
        requireBundle(document.bindings == sorted, "setup_local_bindings_invalid")
        val usedPolicies = mutableSetOf<String>()
        var previous: LocalSetupBinding? = null
        for (binding in document.bindings) {
            val policy = policies[binding.policyId] ?: throw LocalSetupBundleException("setup_local_bindings_invalid")
            val range = binding.effective.bundleRange()
            val snapshot = snapshots.getValue(policy.snapshot.path).payload
            val source = snapshot.source
            val q = source.qualification
            val expectedId = "schedule-choice-" + localSetupHash(
                "namaztime-city-schedule-choice/v1\u0000${binding.cityId}\u0000${binding.policyId}".encodeToByteArray(),
            )
            val city = index.city(binding.cityId) ?: throw LocalSetupBundleException("setup_local_city_unknown")
            requireBundle(binding.choiceId == expectedId && binding.displayLabel.isNotBlank() &&
                range.start >= LocalDate.parse(snapshot.coverage.from) && range.endInclusive <= LocalDate.parse(snapshot.coverage.to) &&
                (q == null || range.endInclusive <= LocalDate.parse(q.freshThrough)) &&
                policy.source["fresh_through"]?.let { range.endInclusive <= LocalDate.parse(it.jsonPrimitive.content) } != false &&
                city.timezone == snapshot.mosque.timezone && index.regionId(binding.cityId) == policy.scope.bundleString("region_id") &&
                (policy.scope.bundleString("kind") != "city" || policy.scope.bundleString("city_id") == binding.cityId) &&
                binding.tier == if (policy.scope.bundleString("kind") == "city") "exact_city_timetable" else "regional_official_timetable",
                "setup_local_scope_binding_invalid")
            if (q != null) {
                val cityScope = q.scope.kind == "city"
                requireBundle(snapshot.mosque.region == city.federalSubjectName &&
                    snapshot.mosque.name == if (cityScope) city.canonicalName else city.federalSubjectName,
                    "setup_local_display_context_invalid")
                requireBundle(snapshot.mosque.locality.orEmpty() == if (cityScope) city.canonicalName else "", "setup_local_display_context_invalid")
            }
            previous?.let { prior ->
                if (prior.cityId == binding.cityId && prior.choiceId == binding.choiceId) {
                    requireBundle(prior.effective.bundleRange().endInclusive < range.start, "setup_local_bindings_overlap")
                }
            }
            previous = binding
            if (usedPolicies.add(binding.policyId)) {
                // Reuse the HTTP wire validator for the full proof and policy projection.
                requireBundle(choiceSet(binding.cityId, range.start).choices.any { it.policyId == binding.policyId }, "setup_local_choices_invalid")
            }
        }
        requireBundle(usedPolicies == policies.keys, "setup_local_unused_policy")
    }

    override fun close() = index.close()
}

@Serializable
internal data class LocalSetupManifest(
    @SerialName("schema_version") val schemaVersion: String,
    @SerialName("bundle_id") val bundleId: String,
    @SerialName("manifest_sha256") val manifestSha256: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("registry_revision") val registryRevision: JsonObject,
    @SerialName("registry_state") val registryState: String,
    val admission: LocalSetupAdmission,
    val catalog: LocalSetupCatalog,
    @SerialName("minimum_trust_revision") val minimumTrustRevision: Long,
    val files: List<LocalSetupFile>,
)

@Serializable
internal data class LocalSetupAdmission(val kind: String, @SerialName("verified_at") val verifiedAt: String,
    @SerialName("actor_id") val actorId: String, val reason: String)

@Serializable
internal data class LocalSetupCatalog(
    @SerialName("revision_id") val revisionId: String,
    @SerialName("content_sha256") val contentSha256: String,
    @SerialName("region_count") val regionCount: Long,
    @SerialName("city_count") val cityCount: Long,
    @SerialName("alias_count") val aliasCount: Long,
    @SerialName("search_name_count") val searchNameCount: Long,
    val license: String,
    @SerialName("license_url") val licenseUrl: String,
    val attribution: String,
)

@Serializable
internal data class LocalSetupFile(val path: String, @SerialName("byte_length") val byteLength: Long, val sha256: String)

@Serializable
internal data class LocalSetupChoices(
    @SerialName("schema_version") val schemaVersion: String,
    @SerialName("registry_revision_id") val registryRevisionId: String,
    val policies: List<LocalSetupPolicy>, val bindings: List<LocalSetupBinding>,
)

@Serializable
internal data class LocalSetupSnapshotReference(
    @SerialName("snapshot_id") val snapshotId: String, val path: String, val sha256: String,
    @SerialName("byte_length") val byteLength: Long,
    @SerialName("display_context") val displayContext: SnapshotMosque,
)

@Serializable
internal data class LocalSetupBinding(
    @SerialName("city_id") val cityId: String, @SerialName("choice_id") val choiceId: String,
    @SerialName("policy_id") val policyId: String, @SerialName("display_label") val displayLabel: String,
    val tier: String, val effective: SnapshotDateRange,
)

@Serializable
internal data class LocalSetupPolicy(
    @SerialName("policy_id") val policyId: String, @SerialName("authority_label") val authorityLabel: String,
    val policy: JsonObject, val scope: JsonObject, val authorities: JsonArray, val source: JsonObject,
    val qualification: JsonObject? = null, val timetable: JsonObject,
    @SerialName("source_overrides") val sourceOverrides: JsonArray,
    val snapshot: LocalSetupSnapshotReference,
) {
    fun validateSnapshot(value: SnapshotPayload) {
        policy.exactBundleKeys(setOf("id", "kind", "geographic_scope_id", "authority_ids", "source_id", "timetable_id", "mosque_ids", "effective"),
            setOf("approval_id", "qualification_id"))
        requireBundle(policy.bundleString("id") == policyId && policy.bundleString("kind") == "timetable" &&
            policy.bundleString("source_id") == source.bundleString("id") &&
            policy.bundleString("geographic_scope_id") == scope.bundleString("id") &&
            policy.getValue("authority_ids") == source.getValue("authority_ids") &&
            policy.bundleString("timetable_id") == timetable.bundleString("id") &&
            timetable.bundleString("published_snapshot_id") == value.snapshotId &&
            timetable.bundleString("mosque_id") == value.mosque.id &&
            source.bundleString("id") == value.source.sourceId && source.bundleString("kind") == value.source.kind &&
            source["canonical_url"]?.jsonPrimitive?.content == value.source.canonicalUrl &&
            value.source.effectiveFrom <= policy.getValue("effective").jsonObject.bundleString("from") &&
            value.source.effectiveTo >= policy.getValue("effective").jsonObject.bundleString("to"),
            "setup_local_policy_binding_invalid")
        val q = value.source.qualification
        if (q != null) {
            requireBundle(qualification != null && SourceQualificationValidation.decode(qualification.toString()) == q &&
                "approval_id" !in policy && policy.bundleString("qualification_id") == q.qualificationId &&
                (policy["mosque_ids"] == JsonNull || policy["mosque_ids"] == JsonArray(emptyList())),
                "setup_local_qualification_binding_invalid")
        } else {
            requireBundle(qualification == null && "qualification_id" !in policy && scope.bundleString("kind") == "city" &&
                snapshot.sha256 == PILOT_LOCAL_SNAPSHOT_SHA256 && value.snapshotId == PILOT_LOCAL_SNAPSHOT_ID &&
                value.mosque.id == "second-cathedral-mosque-ulyanovsk" &&
                scope.bundleString("city_id") == "city-4adcfc15932f3850d5dd5dbaa17e3a4c" &&
                policy.bundleString("approval_id") == value.source.approval?.approvalId &&
                policy.getValue("mosque_ids") == JsonArray(listOf(JsonPrimitive(value.mosque.id))),
                "setup_local_legacy_binding_invalid")
        }
    }

    fun choiceJson(binding: LocalSetupBinding): JsonObject = buildJsonObject {
        put("choice_id", binding.choiceId); put("display_label", binding.displayLabel)
        put("authority_label", authorityLabel); put("tier", binding.tier)
        put("selectable", true); put("executable", true); put("blocked_reason", "eligible")
        put("policy_id", policyId); put("policy_kind", policy.getValue("kind"))
        if (qualification != null) put("qualification", qualification) else put("approval_id", policy.getValue("approval_id"))
        put("effective", policy.getValue("effective")); put("scope", scope); put("authorities", authorities); put("source", source)
        put("timetable_id", policy.getValue("timetable_id")); put("timetable", timetable); put("source_overrides", sourceOverrides)
    }
}

internal val bundleJson = Json { ignoreUnknownKeys = false; isLenient = false; coerceInputValues = false }
internal fun parseBundleJson(bytes: ByteArray): JsonObject {
    val value = bytes.decodeToString(throwOnInvalidSequence = true)
    StrictJsonObjectKeyScanner(value, "setup_local_json_invalid").scan()
    return bundleJson.parseToJsonElement(value).jsonObject
}
internal fun requireBundle(condition: Boolean, code: String) { if (!condition) throw LocalSetupBundleException(code) }
internal fun JsonObject.bundleString(key: String): String = getValue(key).jsonPrimitive.let {
    requireBundle(it.isString && it.content.isNotEmpty(), "setup_local_json_invalid"); it.content
}
internal fun JsonObject.exactBundleKeys(required: Set<String>, optional: Set<String> = emptySet()) =
    requireBundle(keys.containsAll(required) && keys.all { it in required || it in optional }, "setup_local_json_invalid")
internal fun isBundleHash(value: String): Boolean = value.matches(Regex("[0-9a-f]{64}"))
internal fun localSetupHash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).bundleHex()
private fun ByteArray.bundleHex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }
internal fun SnapshotDateRange.bundleRange(): ClosedRange<LocalDate> {
    val start = LocalDate.parse(from); val end = LocalDate.parse(to)
    requireBundle(start.toString() == from && end.toString() == to && start <= end, "setup_local_date_invalid")
    return start..end
}
private fun canonicalBundleInstant(value: String): Instant {
    requireBundle(value.matches(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z")), "setup_local_timestamp_invalid")
    return Instant.parse(value)
}
private fun hashBundleStream(input: InputStream, length: Long, output: FileOutputStream? = null): String {
    val hash = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    var count = 0L
    while (true) {
        val read = input.read(buffer)
        if (read == -1) break
        count += read
        requireBundle(count <= length, "setup_local_size_invalid")
        hash.update(buffer, 0, read); output?.write(buffer, 0, read)
    }
    requireBundle(count == length, "setup_local_size_invalid")
    return hash.digest().bundleHex()
}

internal fun CanonicalCityCandidate.candidateJson(): JsonObject = buildJsonObject {
    put("city_id", id); put("canonical_name", canonicalName); put("aliases", JsonArray(aliases.map(::JsonPrimitive)))
    put("federal_subject_code", federalSubjectCode); put("federal_subject_name", federalSubjectName)
    put("settlement_type", settlementType); put("timezone", timezone); put("latitude", latitude); put("longitude", longitude)
    put("geographic_source_id", geographicSourceId); put("geographic_revision", geographicRevision); put("geographic_license", geographicLicense)
}
