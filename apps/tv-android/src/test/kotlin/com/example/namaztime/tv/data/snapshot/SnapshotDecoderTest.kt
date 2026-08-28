package com.example.namaztime.tv.data.snapshot

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SnapshotDecoderTest {
    @Test
    fun validSyntheticSnapshotDecodesEverySupportedChildType() {
        val snapshot = SnapshotDecoder.decode(syntheticFixture())

        assertEquals("synthetic-ulsk-demo-2026-08-v1", snapshot.snapshotId)
        assertEquals("Europe/Ulyanovsk", snapshot.mosque.timezone)
        assertEquals(3, snapshot.prayerDays.size)
        assertEquals(2, snapshot.iqamahRules.size)
        assertEquals(1, snapshot.jumuahSessions.size)
        assertEquals(1, snapshot.campaigns.size)
        assertEquals("builtin-default", snapshot.theme?.themeId)
    }

    @Test
    fun unknownFieldFailsClosed() {
        val corrupt = syntheticFixture().decodeToString().replaceFirst(
            "\"schema_version\": \"1.0\"",
            "\"schema_version\": \"1.0\", \"unexpected\": true",
        )

        val error = assertThrows(SnapshotValidationException::class.java) {
            SnapshotDecoder.decode(corrupt.encodeToByteArray())
        }

        assertEquals("invalid_json", error.code)
    }

    @Test
    fun dateGapAndInvalidTimezoneFailDomainValidation() {
        val fixture = syntheticFixture().decodeToString()
        val cases = listOf(
            fixture.replaceFirst("\"date\": \"2026-08-20\"", "\"date\": \"2026-08-22\"") to
                "date_gap",
            fixture.replaceFirst("Europe/Ulyanovsk", "Mars/Olympus_Mons") to
                "invalid_timezone",
        )

        cases.forEach { (corrupt, expectedCode) ->
            val error = assertThrows(SnapshotValidationException::class.java) {
                SnapshotDecoder.decode(corrupt.encodeToByteArray())
            }
            assertEquals(expectedCode, error.code)
        }
    }

    @Test
    fun fixedOffsetTimezoneIsNotAcceptedAsIanaZone() {
        val corrupt = syntheticFixture().decodeToString()
            .replaceFirst("Europe/Ulyanovsk", "+04:00")

        val error = assertThrows(SnapshotValidationException::class.java) {
            SnapshotDecoder.decode(corrupt.encodeToByteArray())
        }

        assertEquals("invalid_timezone", error.code)
    }

    @Test
    fun malformedUtf8FailsBeforeJsonDeserialization() {
        val corrupt = syntheticFixture().copyOf()
        val marker = "Synthetic test fixture".encodeToByteArray()
        val markerStart = corrupt.indices.first { index ->
            index + marker.size <= corrupt.size &&
                corrupt.copyOfRange(index, index + marker.size).contentEquals(marker)
        }
        corrupt[markerStart] = 0xff.toByte()

        val error = assertThrows(SnapshotValidationException::class.java) {
            SnapshotDecoder.decode(corrupt)
        }

        assertEquals("invalid_json", error.code)
    }

    @Test
    fun dateTimeOutsideContractRfc3339GrammarFailsClosed() {
        val fixture = syntheticFixture().decodeToString()
        val cases = listOf(
            "2026-08-19T10:00:00+04:00:30",
            "+012026-08-19T10:00:00Z",
        )

        cases.forEach { invalidDateTime ->
            val corrupt = fixture.replaceFirst("2026-08-19T10:00:00Z", invalidDateTime)
            val error = assertThrows(SnapshotValidationException::class.java) {
                SnapshotDecoder.decode(corrupt.encodeToByteArray())
            }
            assertEquals("invalid_datetime", error.code)
        }
    }

    @Test
    fun schemaValidRfc3339LowercaseAndLeapSecondsRemainAccepted() {
        val fixture = syntheticFixture().decodeToString()
        val cases = listOf(
            "2026-08-19t10:00:00z",
            "2026-12-31T23:59:60Z",
            "2027-01-01T02:59:60+03:00",
        )

        cases.forEach { validDateTime ->
            SnapshotDecoder.decode(
                fixture.replaceFirst("2026-08-19T10:00:00Z", validDateTime)
                    .encodeToByteArray(),
            )
        }
    }

    @Test
    fun schemaValidRfc3339HighPrecisionAndWideOffsetRemainAccepted() {
        val fixture = syntheticFixture().decodeToString()
        val cases = listOf(
            "2026-08-19T10:00:00.1234567890Z",
            "2026-08-19T10:00:00+23:59",
        )

        cases.forEach { validDateTime ->
            SnapshotDecoder.decode(
                fixture.replaceFirst("2026-08-19T10:00:00Z", validDateTime)
                    .encodeToByteArray(),
            )
        }
    }

    @Test
    fun schemaStringLengthsCountUnicodeCodePoints() {
        val fixture = syntheticFixture().decodeToString().replaceFirst(
            "Синтетическая демонстрационная мечеть",
            "😀".repeat(240),
        )

        val snapshot = SnapshotDecoder.decode(fixture.encodeToByteArray())

        assertEquals(240, snapshot.mosque.name.codePointCount(0, snapshot.mosque.name.length))
    }

    @Test
    fun campaignRangeOrdersLeapSecondBeforeFollowingMidnight() {
        val fixture = syntheticFixture().decodeToString()
        val forward = fixture
            .replaceFirst("2026-08-19T00:00:00Z", "2016-12-31T23:59:60Z")
            .replaceFirst("2026-08-22T00:00:00Z", "2017-01-01T00:00:00Z")
        SnapshotDecoder.decode(forward.encodeToByteArray())

        val reverse = fixture
            .replaceFirst("2026-08-19T00:00:00Z", "2017-01-01T00:00:00Z")
            .replaceFirst("2026-08-22T00:00:00Z", "2016-12-31T23:59:60Z")
        val error = assertThrows(SnapshotValidationException::class.java) {
            SnapshotDecoder.decode(reverse.encodeToByteArray())
        }

        assertEquals("invalid_range", error.code)
    }

    @Test
    fun campaignRangeMustContainAnActivatableInstant() {
        val fixture = syntheticFixture().decodeToString()
            .replaceFirst("2026-08-22T00:00:00Z", "2026-08-19T00:00:00Z")

        val error = assertThrows(SnapshotValidationException::class.java) {
            SnapshotDecoder.decode(fixture.encodeToByteArray())
        }

        assertEquals("invalid_range", error.code)
    }

    @Test
    fun jsonSchemaEnumsBoundsUrisUniquenessAndNullabilityFailClosed() {
        val fixture = syntheticFixture().decodeToString()
        val cases = listOf(
            fixture.replaceFirst(
                "\"snapshot_id\": \"synthetic-ulsk-demo-2026-08-v1\"",
                "\"snapshot_id\": \"short\"",
            ) to "invalid_length",
            fixture.replaceFirst(
                "\"kind\": \"website\"",
                "\"kind\": \"unknown\"",
            ) to "unsupported_value",
            fixture.replaceFirst(
                "\"placement\": \"with_prayer_times\"",
                "\"placement\": \"fullscreen\"",
            ) to "unsupported_value",
            fixture.replaceFirst(
                "\"flags\": [\"synthetic\"]",
                "\"flags\": [\"synthetic\", \"synthetic\"]",
            ) to "duplicate_value",
            fixture.replaceFirst(
                "\"source_id\": \"synthetic-fixture-v1\"",
                "\"source_id\": \"synthetic-fixture-v1\", \"canonical_url\": \"not a uri\"",
            ) to "invalid_uri",
            fixture.replaceFirst(
                "\"region\": \"Ульяновская область\"",
                "\"region\": null",
            ) to "null_not_allowed",
            fixture.replaceFirst(
                "\"authority_name\": \"Synthetic test fixture — not an official authority\"",
                "\"authority_name\": \"${"a".repeat(241)}\"",
            ) to "invalid_length",
            fixture.replaceFirst(
                "https://example.invalid/mosque-demo",
                "https://trusted.example@attacker.example/donate",
            ) to "invalid_https_url",
            fixture.replaceFirst(
                "\"flags\": [\"synthetic\"]",
                "\"flags\": [${List(33) { index -> "\"flag-$index\"" }.joinToString()}]",
            ) to "too_many_items",
        )

        cases.forEach { (corrupt, expectedCode) ->
            val error = assertThrows(SnapshotValidationException::class.java) {
                SnapshotDecoder.decode(corrupt.encodeToByteArray())
            }
            assertEquals(expectedCode, error.code)
        }
    }

    @Test
    fun malformedIqamahValueFailsClosed() {
        val corrupt = syntheticFixture().decodeToString().replaceFirst(
            "\"offset_minutes\": 25",
            "\"offset_minutes\": 25, \"fixed_time\": \"04:00\"",
        )

        val error = assertThrows(SnapshotValidationException::class.java) {
            SnapshotDecoder.decode(corrupt.encodeToByteArray())
        }

        assertEquals("conflicting_value", error.code)
    }

    @Test
    fun schemaValidThemeAssetMetadataIsRetained() {
        val withAsset = syntheticFixture().decodeToString().replaceFirst(
            "\"theme_id\": \"builtin-default\",",
            """
            "theme_id": "builtin-default",
            "landscape_asset": {
              "asset_id": "asset-test",
              "sha256": "${"a".repeat(64)}",
              "media_type": "image/jpeg",
              "byte_length": 1024,
              "width": 1920,
              "height": 1080
            },
            """.trimIndent(),
        )

        val snapshot = SnapshotDecoder.decode(withAsset.encodeToByteArray())

        assertEquals("asset-test", snapshot.theme?.landscapeAsset?.assetId)
    }

    private fun syntheticFixture(): ByteArray = File(
        "../../examples/synthetic-prayer-snapshot.json",
    ).readBytes()
}
