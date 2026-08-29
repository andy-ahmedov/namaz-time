package ru.namaztime.tv.data.snapshot

import android.content.Context

object PilotLocalSnapshotTrust {
    fun verifier(context: Context): SnapshotAuthenticityVerifier {
        val assets = context.applicationContext.assets
        fun read(name: String): ByteArray = assets.open(name).use { it.readBytes() }

        return SnapshotAuthenticityVerifier(
            trustBundle = read(PILOT_LOCAL_PRODUCTION_TRUST_ASSET),
            minimumTrustBundleRevision = 3,
            previousTrustBundle = read(PILOT_LOCAL_PREVIOUS_PRODUCTION_TRUST_ASSET),
            environmentTrustBundles = listOf(
                read(PILOT_LOCAL_TEST_TRUST_ASSET),
                read(PILOT_LOCAL_STAGING_TRUST_ASSET),
            ),
        )
    }
}

const val PILOT_LOCAL_SNAPSHOT_ASSET = "pilot-local-ulyanovsk-2026-snapshot.json"
const val PILOT_LOCAL_INITIAL_SNAPSHOT_ID = "ulyanovsk-second-cathedral-2026-pilot-local-v1"
const val PILOT_LOCAL_SNAPSHOT_ID = "ulyanovsk-second-cathedral-2026-pilot-local-v2"
const val PILOT_LOCAL_SNAPSHOT_ID_PREFIX = "ulyanovsk-second-cathedral-pilot-local-"
const val LEGACY_SYNTHETIC_SNAPSHOT_ID = "synthetic-ulsk-demo-2026-08-v1"
val PILOT_LOCAL_PREDECESSOR_SNAPSHOT_IDS = setOf(
    LEGACY_SYNTHETIC_SNAPSHOT_ID,
    PILOT_LOCAL_INITIAL_SNAPSHOT_ID,
)
const val PILOT_LOCAL_PRODUCTION_TRUST_ASSET = "pilot-local-production-trust-bundle.json"
const val PILOT_LOCAL_PREVIOUS_PRODUCTION_TRUST_ASSET =
    "pilot-local-production-trust-bundle-previous.json"
const val PILOT_LOCAL_STAGING_TRUST_ASSET = "pilot-local-staging-trust-bundle.json"
const val PILOT_LOCAL_TEST_TRUST_ASSET = "pilot-local-test-trust-bundle.json"
const val PILOT_LOCAL_SNAPSHOT_SHA256 =
    "78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b"
