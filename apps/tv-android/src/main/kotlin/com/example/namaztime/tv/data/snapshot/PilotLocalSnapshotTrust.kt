package com.example.namaztime.tv.data.snapshot

import android.content.Context

object PilotLocalSnapshotTrust {
    fun verifier(context: Context): SnapshotAuthenticityVerifier {
        val assets = context.applicationContext.assets
        fun read(name: String): ByteArray = assets.open(name).use { it.readBytes() }

        return SnapshotAuthenticityVerifier(
            trustBundle = read(PILOT_LOCAL_PRODUCTION_TRUST_ASSET),
            minimumTrustBundleRevision = 1,
            environmentTrustBundles = listOf(
                read(PILOT_LOCAL_TEST_TRUST_ASSET),
                read(PILOT_LOCAL_STAGING_TRUST_ASSET),
            ),
        )
    }
}

const val PILOT_LOCAL_SNAPSHOT_ASSET = "pilot-local-ulyanovsk-2026-snapshot.json"
const val PILOT_LOCAL_SNAPSHOT_ID = "ulyanovsk-second-cathedral-2026-pilot-local-v1"
const val LEGACY_SYNTHETIC_SNAPSHOT_ID = "synthetic-ulsk-demo-2026-08-v1"
const val PILOT_LOCAL_PRODUCTION_TRUST_ASSET = "pilot-local-production-trust-bundle.json"
const val PILOT_LOCAL_STAGING_TRUST_ASSET = "pilot-local-staging-trust-bundle.json"
const val PILOT_LOCAL_TEST_TRUST_ASSET = "pilot-local-test-trust-bundle.json"
const val PILOT_LOCAL_SNAPSHOT_SHA256 =
    "92ad801095cdb4e300c56b3e9dc48993179a891a228754a83c5421010edfe3e4"
