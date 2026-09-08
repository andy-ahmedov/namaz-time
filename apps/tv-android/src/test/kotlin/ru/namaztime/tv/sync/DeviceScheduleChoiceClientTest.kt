package ru.namaztime.tv.sync

import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlinx.serialization.json.*
import ru.namaztime.tv.data.snapshot.qualifiedSnapshotDocument
import ru.namaztime.tv.data.snapshot.withQualificationHash

class DeviceScheduleChoiceClientTest {
    @Test
    fun canonicalAliasesRetainCatalogWhitespaceAndDoNotDisappearDuringChoiceValidation() = runTest {
        val alias = "\u00a0Alias\t Town\u0085"
        val response = Json.parseToJsonElement(choiceSetResponse(1)).jsonObject.let { root ->
            JsonObject(root + ("city" to JsonObject(root.getValue("city").jsonObject +
                ("aliases" to JsonArray(listOf(JsonPrimitive(alias)))))))
        }
        val result = client(ChoiceRecordingTransport(SyncHttpResponse(200, emptyMap(), response.toString().encodeToByteArray())))
            .loadScheduleChoices(CITY_ID, SETUP_DATE)
        assertTrue("raw catalog alias rejected: $result", result is DeviceSetupResult.Success<*>)
        assertEquals(listOf(alias), (result as DeviceSetupResult.Success).value.city.aliases)
    }

    @Test
    fun qualifiedV2KeepsEveryIndependentExecutableChoiceWithoutHumanApprovalOrRemoteActivation() = runTest {
        val response = qualifiedChoiceSetResponse()
        val result = client(ChoiceRecordingTransport(SyncHttpResponse(200, emptyMap(), response.encodeToByteArray())))
            .loadScheduleChoices(CITY_ID, LocalDate.parse("2026-09-08"))
        assertTrue("qualified response rejected: $result", result is DeviceSetupResult.Success<*>)
        val set = (result as DeviceSetupResult.Success).value
        assertEquals(2, set.choices.size)
        assertTrue(set.selectionRequired)
        assertFalse(set.requestAllowed)
        assertTrue(set.choices.all { it.executable && !it.requestable && !it.activationAllowed })
        assertTrue(set.choices.all { it.approvalId == null })
    }

    @Test
    fun mixedActiveV2PreservesExecutableLegacyAndPublicChoicesWithoutAutoSelection() = runTest {
        val transport = ChoiceRecordingTransport(SyncHttpResponse(200, emptyMap(), mixedActiveChoiceSetResponse().toString().encodeToByteArray()))
        val result = client(transport).loadScheduleChoices(CITY_ID, LocalDate.parse("2026-09-08"))
        assertTrue("mixed admitted response rejected: $result", result is DeviceSetupResult.Success<*>)
        val set = (result as DeviceSetupResult.Success).value
        assertEquals(listOf(choiceId(0), choiceId(1)), set.choices.map { it.id })
        assertTrue(set.selectionRequired)
        assertFalse(set.requestAllowed)
        assertTrue(set.choices.all { it.selectable && it.executable && !it.requestable && !it.activationAllowed && it.localPreview == null })
        assertEquals("synthetic-approval-1", set.choices[1].approvalId)
        assertEquals(null, set.choices[1].qualification)
        assertEquals(null, set.choices[0].approvalId)
        assertTrue(set.choices[0].qualification != null)
        assertEquals(listOf("GET"), transport.requests.map { it.method })
    }

    @Test
    fun mixedV2KeepsLegacyProofStrictAndRejectsIneligibleActiveOrExecutableStagedChoices() = runTest {
        val original = mixedActiveChoiceSetResponse()
        val legacy = original.getValue("choices").jsonArray[1].jsonObject
        val malformedLegacy = listOf(
            JsonObject(legacy - "approval_id"),
            JsonObject(legacy + ("approval_id" to JsonNull)),
            JsonObject(legacy + ("approval_id" to JsonPrimitive(""))),
            JsonObject(legacy + ("qualification" to JsonNull)),
            JsonObject(legacy + ("scope" to JsonObject(legacy.getValue("scope").jsonObject +
                ("city_id" to JsonPrimitive("another-city"))))),
            JsonObject(legacy + ("source" to JsonObject(legacy.getValue("source").jsonObject +
                ("geographic_scope_id" to JsonPrimitive("another-scope"))))),
            JsonObject(legacy + ("source" to JsonObject(legacy.getValue("source").jsonObject +
                ("fresh_through" to JsonPrimitive("2026-09-07"))))),
            JsonObject(legacy + ("timetable" to JsonObject(legacy.getValue("timetable").jsonObject +
                ("source_id" to JsonPrimitive("another-source"))))),
            JsonObject(legacy + ("executable" to JsonPrimitive(false))),
        )
        val invalid = malformedLegacy.map { replacement ->
            JsonObject(original + ("choices" to JsonArray(listOf(original.getValue("choices").jsonArray[0], replacement))))
        } + JsonObject(original + mapOf(
            "revision_state" to JsonPrimitive("staged"),
            "allowed_actions" to JsonArray(listOf(JsonPrimitive("request_selection"))),
        ))
        invalid.forEachIndexed { index, body ->
            val result = client(ChoiceRecordingTransport(SyncHttpResponse(200, emptyMap(), body.toString().encodeToByteArray())))
                .loadScheduleChoices(CITY_ID, LocalDate.parse("2026-09-08"))
            assertEquals("mixed malformed variant $index", DeviceSetupResult.Failure("setup_response_invalid", false), result)
        }
    }

    @Test
    fun mixedStagedV2RemainsNonExecutableAndRequiresExplicitReviewRequest() = runTest {
        val active = mixedActiveChoiceSetResponse()
        val staged = JsonObject(active + mapOf(
            "revision_state" to JsonPrimitive("staged"),
            "allowed_actions" to JsonArray(listOf(JsonPrimitive("request_selection"))),
            "choices" to JsonArray(active.getValue("choices").jsonArray.map {
                JsonObject(it.jsonObject + ("executable" to JsonPrimitive(false)))
            }),
        ))
        val result = client(ChoiceRecordingTransport(SyncHttpResponse(200, emptyMap(), staged.toString().encodeToByteArray())))
            .loadScheduleChoices(CITY_ID, LocalDate.parse("2026-09-08"))
        assertTrue("mixed staged response rejected: $result", result is DeviceSetupResult.Success<*>)
        val set = (result as DeviceSetupResult.Success).value
        assertTrue(set.selectionRequired && set.requestAllowed)
        assertTrue(set.choices.all { !it.executable && it.requestable && !it.activationAllowed })
    }

    private fun mixedActiveChoiceSetResponse(): JsonObject {
        val root = Json.parseToJsonElement(qualifiedChoiceSetResponse()).jsonObject
        val legacy = JsonObject(Json.parseToJsonElement(choiceDocument(1, false)).jsonObject +
            ("executable" to JsonPrimitive(true)))
        return JsonObject(root + ("choices" to JsonArray(listOf(root.getValue("choices").jsonArray[0], legacy))))
    }

    @Test
    fun qualifiedChoiceWireRejectsBranchConfusionProofTamperAndCatalogScopeSourceMismatch() = runTest {
        val original = qualifiedChoiceSetResponse()
        val invalid = listOf(
            original.replace("device-city-schedule-choices/v2", "device-city-schedule-choices/v1"),
            original.replace("\"qualified\"", "\"approved\""),
            original.replace("\"qualification\":{", "\"approval_id\":\"fake-approval\",\"qualification\":{"),
            original.replace("\"qualification\":{", "\"approval_id\":null,\"qualification\":{"),
            original.replace("\"qualification\":{", "\"approval_id\":\"\",\"qualification\":{"),
            original.replace("\"qualified_at\":", "\"qualified_at\":\"2026-09-08T10:00:00Z\",\"qualified_at\":"),
            original.replace("Synthetic test authority", "Unbound authority"),
            original.replace("\"catalog_revision_id\":\"catalog-synthetic-v1\"", "\"catalog_revision_id\":\"different-catalog\""),
            original.replace("\"source_kind\":\"official_html\"", "\"source_kind\":\"manual_import\""),
            original.replace("\"timezone\":\"Europe/Moscow\"", "\"timezone\":\"Europe/Kirov\""),
        )
        invalid.forEachIndexed { index, value ->
            val result = client(ChoiceRecordingTransport(SyncHttpResponse(200, emptyMap(), value.encodeToByteArray())))
                .loadScheduleChoices(CITY_ID, LocalDate.parse("2026-09-08"))
            assertEquals("malformed variant $index", DeviceSetupResult.Failure("setup_response_invalid", false), result)
        }
    }

    private fun qualifiedChoiceSetResponse(): String {
        val root = Json.parseToJsonElement(choiceSetResponse(2)).jsonObject.toMutableMap()
        root["schema_version"] = JsonPrimitive("device-city-schedule-choices/v2")
        root["revision"] = JsonObject(root.getValue("revision").jsonObject + mapOf(
            "schema_version" to JsonPrimitive(2), "catalog_revision_id" to JsonPrimitive("catalog-synthetic-v1"),
        ))
        root["revision_state"] = JsonPrimitive("active")
        root["date"] = JsonPrimitive("2026-09-08")
        root["allowed_actions"] = JsonArray(emptyList())
        root["city"] = JsonObject(root.getValue("city").jsonObject + ("timezone" to JsonPrimitive("Europe/Moscow")))
        root["choices"] = JsonArray((0..1).map { index ->
            val choice = Json.parseToJsonElement(choiceDocument(index, false)).jsonObject.toMutableMap()
            val authority = qualifiedSnapshotDocument().getValue("source").jsonObject.getValue("qualification").jsonObject
                .getValue("authority").jsonObject
            val q = JsonObject(qualifiedSnapshotDocument().getValue("source").jsonObject.getValue("qualification").jsonObject + mapOf(
                "source_id" to JsonPrimitive("synthetic-source-$index"),
                "authority" to JsonObject(authority + ("id" to JsonPrimitive("synthetic-authority-$index"))),
                "scope" to choice.getValue("scope"),
            )).withQualificationHash()
            choice.remove("approval_id")
            choice["qualification"] = q
            choice["executable"] = JsonPrimitive(true)
            choice["effective"] = q.getValue("coverage")
            choice["authorities"] = JsonArray(listOf(q.getValue("authority")))
            choice["source"] = JsonObject(choice.getValue("source").jsonObject + mapOf(
                "kind" to q.getValue("source_kind"), "canonical_url" to q.getValue("canonical_url"),
                "status" to JsonPrimitive("qualified"), "qualification_id" to q.getValue("qualification_id"),
                "fresh_through" to q.getValue("fresh_through"),
            ))
            choice["timetable"] = JsonObject(choice.getValue("timetable").jsonObject + mapOf(
                "effective" to q.getValue("coverage"),
                "mosque_id" to JsonPrimitive("public-scope-" + ru.namaztime.tv.data.snapshot.fixtureHash(
                    q.getValue("scope").jsonObject.getValue("id").jsonPrimitive.content.encodeToByteArray(),
                ).take(32)),
            ))
            JsonObject(choice)
        })
        return JsonObject(root).toString()
    }

    @Test
    fun keepsIndependentRegionalChoiceAlongsideAnotherCityAuthority() = runTest {
        val original = choiceDocument(1, duplicateLabels = false)
        val regional = original
            .replace("\"tier\":\"exact_city_timetable\"", "\"tier\":\"regional_official_timetable\"")
            .replace("\"kind\":\"city\",\"city_id\":\"$CITY_ID\"", "\"kind\":\"region\"")
        val response = choiceSetResponse(2)
            .replace(original, regional)
            .replace("same_tier_ambiguous", "multiple_authorities")
        val result = client(
            ChoiceRecordingTransport(SyncHttpResponse(200, emptyMap(), response.encodeToByteArray())),
        ).loadScheduleChoices(CITY_ID, SETUP_DATE)

        assertTrue("independent mixed-scope choices rejected: $result", result is DeviceSetupResult.Success<*>)
        val choices = (result as DeviceSetupResult.Success).value
        assertEquals(listOf("exact_city_timetable", "regional_official_timetable"), choices.choices.map { it.tier })
        assertTrue(choices.selectionRequired)
        assertTrue(choices.choices.none { it.executable })
    }

    @Test
    fun exposesCompleteZeroOneTwoFiveAndEightChoiceSetsWithoutImplicitSelection() = runTest {
        listOf(0, 1, 2, 5, 8).forEach { count ->
            val transport = ChoiceRecordingTransport(
                response = SyncHttpResponse(
                    statusCode = 200,
                    headers = emptyMap(),
                    body = choiceSetResponse(count).encodeToByteArray(),
                ),
            )
            val client = client(transport)

            val result = client.loadScheduleChoices(CITY_ID, SETUP_DATE)

            assertTrue("count=$count result=$result", result is DeviceSetupResult.Success<*>)
            @Suppress("UNCHECKED_CAST")
            val set = (result as DeviceSetupResult.Success<DeviceCityScheduleChoiceSet>).value
            assertEquals(count, set.choices.size)
            assertEquals(count > 1, set.selectionRequired)
            assertEquals(count > 0, set.requestAllowed)
            assertTrue(set.choices.all { it.selectable && !it.executable && it.requestable == (count > 0) })
            assertEquals((0 until count).map(::choiceId), set.choices.map { it.id })
            assertEquals(1, transport.requests.size)
            val request = transport.requests.single()
            assertEquals("GET", request.method)
            assertTrue(request.url.contains("city_id=$CITY_ID"))
            assertTrue(request.url.contains("date=$SETUP_DATE"))
            assertFalse(request.url.contains("mosque"))
            assertFalse(request.url.contains("revision"))
        }
    }

    @Test
    fun duplicateAuthorityLabelsRemainDistinctAndServerOrderDoesNotSelectOne() = runTest {
        val transport = ChoiceRecordingTransport(
            response = SyncHttpResponse(
                200,
                emptyMap(),
                choiceSetResponse(2, duplicateLabels = true).encodeToByteArray(),
            ),
        )

        val result = client(transport).loadScheduleChoices(CITY_ID, SETUP_DATE)

        val set = (result as DeviceSetupResult.Success).value
        assertEquals(listOf("Синтетическая организация", "Синтетическая организация"),
            set.choices.map { it.authorityLabel })
        assertEquals(2, set.choices.map { it.id }.distinct().size)
        assertEquals(2, set.choices.map { it.policyId }.distinct().size)
        assertTrue(set.selectionRequired)
        assertTrue(set.choices.none { it.executable })
    }

    @Test
    fun activeSingleChoiceIsExecutableButNeverCreatesAnotherSelectionRequest() = runTest {
        val response = choiceSetResponse(1)
            .replace("\"revision_state\":\"staged\"", "\"revision_state\":\"active\"")
            .replace("\"executable\":false", "\"executable\":true")
            .replace("\"allowed_actions\":[\"request_selection\"]", "\"allowed_actions\":[]")
            .replace(
                "synthetic-snapshot-0",
                "ulyanovsk-second-cathedral-2026-pilot-local-v2",
            )
        val result = client(
            ChoiceRecordingTransport(SyncHttpResponse(200, emptyMap(), response.encodeToByteArray())),
        ).loadScheduleChoices(CITY_ID, SETUP_DATE)

        val set = (result as DeviceSetupResult.Success).value
        assertFalse(set.requestAllowed)
        assertFalse(set.selectionRequired)
        assertTrue(set.choices.single().executable)
        assertFalse(set.choices.single().requestable)
        assertEquals(
            "ulyanovsk-second-cathedral-2026-pilot-local-v2",
            set.choices.single().publishedSnapshotId,
        )
    }

    @Test
    fun rejectsUnknownNestedFieldsStaleSelectableChoicesAndOversizedBodies() = runTest {
        val unknown = choiceSetResponse(1).replace(
            "\"evidence_label\":\"PROPOSAL\"",
            "\"evidence_label\":\"PROPOSAL\",\"unexpected\":true",
        )
        val stale = choiceSetResponse(1).replace(
            "\"status\":\"approved\",\"fresh_through\"",
            "\"status\":\"stale\",\"fresh_through\"",
        )
        listOf(unknown.encodeToByteArray(), stale.encodeToByteArray(), ByteArray(512 * 1024 + 1)).forEach { body ->
            val result = client(
                ChoiceRecordingTransport(SyncHttpResponse(200, emptyMap(), body)),
            ).loadScheduleChoices(CITY_ID, SETUP_DATE)
            assertEquals(DeviceSetupResult.Failure("setup_response_invalid", retryable = false), result)
        }
    }

    @Test
    fun submitsOnlyBoundedExplicitChoiceAndReturnsPendingReview() = runTest {
        val transport = ChoiceRecordingTransport(
            response = SyncHttpResponse(
                201,
                emptyMap(),
                pendingResponse().encodeToByteArray(),
            ),
        )
        val client = client(transport)

        val result = client.requestScheduleChoice(
            cityId = CITY_ID,
            choiceId = choiceId(0),
            date = SETUP_DATE,
            interactionId = INTERACTION_ID,
        )

        val pending = (result as DeviceSetupResult.Success).value
        assertEquals("pending_review", pending.status)
        assertEquals(choiceId(0), pending.choiceId)
        assertEquals(INTERACTION_ID, pending.interactionId)
        val request = transport.requests.single()
        assertEquals("POST", request.method)
        assertEquals("application/json", request.contentType)
        val body = request.body!!.decodeToString()
        assertTrue(body.contains("\"city_id\":\"$CITY_ID\""))
        assertTrue(body.contains("\"choice_id\":\"${choiceId(0)}\""))
        assertTrue(body.contains("\"interaction_id\":\"$INTERACTION_ID\""))
        assertFalse(body.contains("mosque_id"))
        assertFalse(body.contains("revision_id"))
        assertFalse(body.contains("admin"))
    }

    @Test
    fun mapsUnauthorizedConflictRetryAndPropagatesCancellationForBothOperations() = runTest {
        val unauthorized = client(ChoiceRecordingTransport(SyncHttpResponse(401, emptyMap(), byteArrayOf())))
        assertEquals(DeviceSetupResult.Unauthorized, unauthorized.loadScheduleChoices(CITY_ID, SETUP_DATE))
        assertEquals(
            DeviceSetupResult.Unauthorized,
            unauthorized.requestScheduleChoice(CITY_ID, choiceId(0), SETUP_DATE, INTERACTION_ID),
        )

        val conflict = client(ChoiceRecordingTransport(SyncHttpResponse(409, emptyMap(), byteArrayOf())))
        assertEquals(
            DeviceSetupResult.Failure("setup_choice_not_requestable", retryable = false),
            conflict.requestScheduleChoice(CITY_ID, choiceId(0), SETUP_DATE, INTERACTION_ID),
        )

        val retry = client(ChoiceRecordingTransport(error = IOException("offline")))
        assertEquals(
            DeviceSetupResult.Failure("setup_io", retryable = true),
            retry.loadScheduleChoices(CITY_ID, SETUP_DATE),
        )

        val cancelled = client(ChoiceRecordingTransport(error = CancellationException("cancel")))
        try {
            cancelled.loadScheduleChoices(CITY_ID, SETUP_DATE)
            fail("choice load cancellation was swallowed")
        } catch (_: CancellationException) {
            // Expected.
        }
        try {
            cancelled.requestScheduleChoice(CITY_ID, choiceId(0), SETUP_DATE, INTERACTION_ID)
            fail("choice request cancellation was swallowed")
        } catch (_: CancellationException) {
            // Expected.
        }
    }

    @Test
    fun rejectsNonHttpsOriginInvalidIdentifiersAndMismatchedPendingPrincipalEcho() = runTest {
        val transport = ChoiceRecordingTransport(
            SyncHttpResponse(200, emptyMap(), choiceSetResponse(1).encodeToByteArray()),
        )
        val insecure = DeviceSetupClient(
            transport,
            ChoiceProvisioningStore(
                provisioning().copy(
                    credentials = provisioning().credentials.copy(
                        manifestUrl = "http://api.example.invalid/v1/devices/$DEVICE_ID/manifest",
                    ),
                ),
            ),
        )
        assertEquals(
            DeviceSetupResult.Failure("setup_request_invalid", retryable = false),
            insecure.loadScheduleChoices(CITY_ID, SETUP_DATE),
        )
        assertEquals(
            DeviceSetupResult.Failure("setup_request_invalid", retryable = false),
            client(transport).requestScheduleChoice(" bad ", choiceId(0), SETUP_DATE, INTERACTION_ID),
        )
        assertTrue(transport.requests.isEmpty())

        val mismatched = pendingResponse().replace(
            "\"device_id\":\"$DEVICE_ID\"",
            "\"device_id\":\"different-device-0001\"",
        )
        val result = client(
            ChoiceRecordingTransport(SyncHttpResponse(201, emptyMap(), mismatched.encodeToByteArray())),
        ).requestScheduleChoice(CITY_ID, choiceId(0), SETUP_DATE, INTERACTION_ID)
        assertEquals(DeviceSetupResult.Failure("setup_response_invalid", retryable = false), result)
    }

    private fun client(transport: DeviceSyncTransport) = DeviceSetupClient(
        transport = transport,
        provisioningStore = ChoiceProvisioningStore(provisioning()),
    )

    private fun provisioning() = DeviceProvisioning(
        credentials = DeviceSyncCredentials(
            deviceId = DEVICE_ID,
            token = "fixture-device-token-not-production",
            manifestUrl = "https://api.example.invalid/v1/devices/$DEVICE_ID/manifest",
            mosqueId = MOSQUE_ID,
            mosqueTimezone = "Europe/Moscow",
        ),
        mosqueId = MOSQUE_ID,
        mosqueName = "Synthetic setup mosque",
        mosqueTimezone = "Europe/Moscow",
    )

    private fun choiceSetResponse(count: Int, duplicateLabels: Boolean = false): String {
        val status = if (count == 0) "unavailable" else "available"
        val automaticStatus = when {
            count == 0 -> "unavailable"
            count == 1 -> "resolved"
            else -> "ambiguous"
        }
        val reason = when {
            count == 0 -> "no_policy"
            count == 1 -> "resolved"
            else -> "same_tier_ambiguous"
        }
        val choices = (0 until count).joinToString(",") { index ->
            choiceDocument(index, duplicateLabels)
        }
        val actions = if (count == 0) "[]" else "[\"request_selection\"]"
        return """
            {
              "schema_version":"device-city-schedule-choices/v1",
              "revision":{
                "id":"synthetic-revision-0001","schema_version":1,
                "catalog_revision_id":"synthetic-catalog-0001","content_sha256":"${"a".repeat(64)}",
                "created_at":"2026-08-30T08:00:00Z","created_by":"synthetic-test",
                "reason":"synthetic Android choice fixture"
              },
              "revision_state":"staged","status":"$status",
              "automatic_resolution_status":"$automaticStatus",
              "automatic_resolution_reason":"$reason",
              "selection_required":${count > 1},"date":"$SETUP_DATE",
              "city":${cityDocument()},"choices":[$choices],"allowed_actions":$actions
            }
        """.trimIndent()
    }

    private fun choiceDocument(index: Int, duplicateLabels: Boolean): String {
        val authorityLabel = if (duplicateLabels) "Синтетическая организация" else "Синтетическая организация $index"
        val authorityId = "synthetic-authority-$index"
        val scopeId = "synthetic-city-scope-$index"
        val sourceId = "synthetic-source-$index"
        val timetableId = "synthetic-timetable-$index"
        return """
            {
              "choice_id":"${choiceId(index)}","display_label":"Киров ($authorityLabel)",
              "authority_label":"$authorityLabel","tier":"exact_city_timetable",
              "selectable":true,"executable":false,"blocked_reason":"eligible",
              "policy_id":"synthetic-policy-$index","policy_kind":"timetable",
              "approval_id":"synthetic-approval-$index",
              "effective":{"from":"2026-01-01","to":"2026-12-31"},
              "scope":{"id":"$scopeId","kind":"city","city_id":"$CITY_ID",
                "region_id":"synthetic-region","description":"Synthetic city scope $index"},
              "authorities":[{"id":"$authorityId","name":"$authorityLabel",
                "evidence_label":"PROPOSAL"}],
              "source":{"id":"$sourceId","kind":"official_file",
                "authority_ids":["$authorityId"],"geographic_scope_id":"$scopeId",
                "canonical_url":"https://example.invalid/synthetic/$index",
                "status":"approved","fresh_through":"2026-12-31"},
              "timetable_id":"$timetableId",
              "timetable":{"id":"$timetableId","source_id":"$sourceId",
                "geographic_scope_id":"$scopeId","mosque_id":"$MOSQUE_ID",
                "timezone":"Europe/Moscow","effective":{"from":"2026-01-01","to":"2026-12-31"},
                "published_snapshot_id":"synthetic-snapshot-$index","source_override_ids":[]},
              "source_overrides":[]
            }
        """.trimIndent()
    }

    private fun cityDocument() = """
        {
          "city_id":"$CITY_ID","canonical_name":"Киров","aliases":["Kirov"],
          "federal_subject_code":"RU-KIR","federal_subject_name":"Кировская область",
          "settlement_type":"PPLA","timezone":"Europe/Kirov","latitude":58.6036,
          "longitude":49.6679,"geographic_source_id":"geonames:548408",
          "geographic_revision":"2026-08-29","geographic_license":"CC BY 4.0"
        }
    """.trimIndent()

    private fun pendingResponse() = """
        {
          "schema_version":"device-schedule-choice-request/v1",
          "request":{
            "id":"device-binding-request-${"b".repeat(64)}",
            "revision_id":"synthetic-revision-0001","city_id":"$CITY_ID",
            "policy_id":"synthetic-policy-0","choice_id":"${choiceId(0)}",
            "mosque_id":"$MOSQUE_ID","device_id":"$DEVICE_ID","date":"$SETUP_DATE",
            "resolution_tier":"exact_city_timetable","status":"pending_review",
            "selection_sha256":"${"c".repeat(64)}","origin":"local_tv_operator",
            "interaction_id":"$INTERACTION_ID","requested_at":"2026-08-30T09:00:00Z"
          }
        }
    """.trimIndent()

    private fun choiceId(index: Int): String =
        "schedule-choice-" + index.toString(16).padStart(64, '0')

    private companion object {
        const val DEVICE_ID = "device-setup-0001"
        const val MOSQUE_ID = "mosque-setup-0001"
        const val CITY_ID = "city-kirov-0001"
        val SETUP_DATE: LocalDate = LocalDate.parse("2026-08-30")
        const val INTERACTION_ID = "interaction-setup-0001"
    }
}

private class ChoiceProvisioningStore(
    private val provisioning: DeviceProvisioning?,
) : DeviceProvisioningStore {
    override fun load(): DeviceProvisioning? = provisioning
    override fun save(provisioning: DeviceProvisioning) = Unit
    override fun clear() = Unit
}

private class ChoiceRecordingTransport(
    private val response: SyncHttpResponse = SyncHttpResponse(200, emptyMap(), byteArrayOf()),
    private val error: Exception? = null,
) : DeviceSyncTransport {
    val requests = mutableListOf<SyncHttpRequest>()

    override suspend fun execute(request: SyncHttpRequest): SyncHttpResponse {
        requests += request
        error?.let { throw it }
        return response
    }
}
