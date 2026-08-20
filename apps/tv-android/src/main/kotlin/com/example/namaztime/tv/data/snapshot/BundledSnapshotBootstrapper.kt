package com.example.namaztime.tv.data.snapshot

import android.content.Context
import com.example.namaztime.tv.data.local.SnapshotImportException
import com.example.namaztime.tv.data.local.SnapshotImporter
import com.example.namaztime.tv.data.local.SnapshotSelectionResolver
import com.example.namaztime.tv.data.local.SnapshotSelectionResolution
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

fun interface SnapshotAssetSource {
    @Throws(IOException::class)
    fun read(): ByteArray
}

class AndroidSnapshotAssetSource(
    context: Context,
    private val assetName: String = BUNDLED_SNAPSHOT_ASSET,
) : SnapshotAssetSource {
    private val assets = context.applicationContext.assets

    override fun read(): ByteArray = assets.open(assetName).use { it.readBytes() }
}

sealed interface SnapshotBootstrapState {
    data object Pending : SnapshotBootstrapState
    data class Ready(
        val snapshotId: String,
        val recoveryCode: String? = null,
    ) : SnapshotBootstrapState
    data class Diagnostic(val supportCode: String) : SnapshotBootstrapState
}

class BundledSnapshotBootstrapper(
    private val selectionGuard: SnapshotSelectionResolver,
    private val importer: SnapshotImporter,
    private val assetSource: SnapshotAssetSource?,
    private val activationGate: (ByteArray) -> ActivatableSnapshot =
        SnapshotActivationGate::bundledSynthetic,
) {
    private val mutableState = MutableStateFlow<SnapshotBootstrapState>(
        SnapshotBootstrapState.Pending,
    )
    val state: StateFlow<SnapshotBootstrapState> = mutableState.asStateFlow()

    suspend fun bootstrapIfNeeded() {
        val selection = try {
            selectionGuard.resolve()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            mutableState.value = SnapshotBootstrapState.Diagnostic("SNAPSHOT_DATABASE_READ_FAILED")
            return
        }
        when (selection) {
            is SnapshotSelectionResolution.Active -> {
                mutableState.value = SnapshotBootstrapState.Ready(selection.snapshotId)
                return
            }
            is SnapshotSelectionResolution.Recovered -> {
                mutableState.value = SnapshotBootstrapState.Ready(
                    selection.snapshotId,
                    recoveryCode = "SNAPSHOT_PREVIOUS_RESTORED",
                )
                return
            }
            SnapshotSelectionResolution.Corrupt -> {
                mutableState.value = SnapshotBootstrapState.Diagnostic("SNAPSHOT_ACTIVE_INVALID")
                return
            }
            SnapshotSelectionResolution.Missing -> Unit
        }

        if (assetSource == null) {
            mutableState.value = SnapshotBootstrapState.Diagnostic("NO_LOCAL_SNAPSHOT")
            return
        }

        mutableState.value = try {
            val snapshot = activationGate(assetSource.read())
            importer.importAndActivate(snapshot)
            SnapshotBootstrapState.Ready(snapshot.payload.snapshotId)
        } catch (error: SnapshotValidationException) {
            SnapshotBootstrapState.Diagnostic("SNAPSHOT_${error.code.uppercase()}")
        } catch (error: IOException) {
            SnapshotBootstrapState.Diagnostic("SNAPSHOT_ASSET_IO")
        } catch (error: SnapshotImportException) {
            SnapshotBootstrapState.Diagnostic("SNAPSHOT_IMPORT_${error.code.uppercase()}")
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            SnapshotBootstrapState.Diagnostic("SNAPSHOT_IMPORT_FAILED")
        }
    }
}

const val BUNDLED_SNAPSHOT_ASSET = "synthetic-prayer-snapshot.json"
