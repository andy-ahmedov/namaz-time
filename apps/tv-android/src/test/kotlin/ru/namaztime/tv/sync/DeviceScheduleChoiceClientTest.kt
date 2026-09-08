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

class DeviceScheduleChoiceClientTest {
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
