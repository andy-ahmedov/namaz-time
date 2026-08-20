package com.example.namaztime.tv.sync

import com.example.namaztime.tv.data.local.SnapshotDao
import com.example.namaztime.tv.data.local.SnapshotImportException
import com.example.namaztime.tv.data.local.SnapshotImportResult
import com.example.namaztime.tv.data.local.SnapshotImporter
import com.example.namaztime.tv.data.snapshot.SnapshotActivationGate
import com.example.namaztime.tv.data.snapshot.SnapshotAuthenticityException
import com.example.namaztime.tv.data.snapshot.SnapshotAuthenticityVerifier
import com.example.namaztime.tv.data.snapshot.SnapshotValidationException
import java.time.ZoneId

class AuthenticatedRoomSnapshotActivator(
    private val verifier: SnapshotAuthenticityVerifier,
    private val importer: SnapshotImporter,
    private val snapshotDao: SnapshotDao,
    private val expectedMosqueId: String,
    private val expectedMosqueTimezone: String,
) : SnapshotActivator {
    init {
        require(expectedMosqueId.length in 1..128)
        require(ZoneId.getAvailableZoneIds().contains(expectedMosqueTimezone))
    }

    override suspend fun activate(bytes: ByteArray, manifest: DeviceSnapshotManifest): String? {
        val activatable = try {
            SnapshotActivationGate.authenticated(bytes, verifier)
        } catch (error: SnapshotAuthenticityException) {
            throw SnapshotActivationRejectedException("snapshot_${error.code}")
        } catch (error: SnapshotValidationException) {
            throw SnapshotActivationRejectedException("snapshot_schema_${error.code}")
        }
        if (activatable.payload.snapshotId != manifest.snapshotId) {
            throw SnapshotActivationRejectedException("manifest_snapshot_id_mismatch")
        }
        if (activatable.payload.integrity.signingKeyId != manifest.signingKeyId) {
            throw SnapshotActivationRejectedException("manifest_signing_key_mismatch")
        }
        if (activatable.payload.mosque.id != expectedMosqueId ||
            activatable.payload.mosque.timezone != expectedMosqueTimezone
        ) {
            throw SnapshotActivationRejectedException("provisioned_mosque_mismatch")
        }
        return try {
            when (val result = importer.importAndActivate(activatable)) {
                is SnapshotImportResult.Activated -> result.previousSnapshotId
                is SnapshotImportResult.AlreadyActive ->
                    snapshotDao.getSelection()?.previousSnapshotId
            }
        } catch (error: SnapshotImportException) {
            throw SnapshotActivationRejectedException("snapshot_import_${error.code}")
        }
    }
}
