package ru.namaztime.tv.data.snapshot

import android.content.Context
import ru.namaztime.tv.data.local.SnapshotImportException
import ru.namaztime.tv.data.local.SnapshotImportResult
import ru.namaztime.tv.data.local.SnapshotImporter
import ru.namaztime.tv.data.local.SnapshotReplacementPolicy
import ru.namaztime.tv.data.local.SnapshotSelectionResolver
import ru.namaztime.tv.data.local.SnapshotSelectionResolution
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
    private val replaceableActiveSnapshotIds: Set<String> = emptySet(),
    private val replacementPolicy: SnapshotReplacementPolicy? = null,
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
        val replaceableSelectionId = when (selection) {
            is SnapshotSelectionResolution.Active -> {
                if (!shouldReadBundledAsset(selection.snapshotId)) {
                    mutableState.value = SnapshotBootstrapState.Ready(selection.snapshotId)
                    return
                }
                selection.snapshotId
            }
            is SnapshotSelectionResolution.Recovered -> {
                if (!shouldReadBundledAsset(selection.snapshotId)) {
                    mutableState.value = SnapshotBootstrapState.Ready(
                        selection.snapshotId,
                        recoveryCode = "SNAPSHOT_PREVIOUS_RESTORED",
                    )
                    return
                }
                selection.snapshotId
            }
            SnapshotSelectionResolution.Corrupt -> {
                mutableState.value = SnapshotBootstrapState.Diagnostic("SNAPSHOT_ACTIVE_INVALID")
                return
            }
            SnapshotSelectionResolution.Missing -> null
        }

        if (assetSource == null) {
            mutableState.value = SnapshotBootstrapState.Diagnostic("NO_LOCAL_SNAPSHOT")
            return
        }

        mutableState.value = try {
            val snapshot = activationGate(assetSource.read())
            val result = if (replaceableSelectionId == null) {
                importer.importAndActivate(snapshot)
            } else if (replacementPolicy != null) {
                importer.replaceAndActivate(snapshot, replacementPolicy)
            } else {
                importer.replaceAndActivate(snapshot, replaceableActiveSnapshotIds)
            }
            when (result) {
                is SnapshotImportResult.SelectionChanged ->
                    SnapshotBootstrapState.Diagnostic("SNAPSHOT_SELECTION_CHANGED")
                else -> SnapshotBootstrapState.Ready(snapshot.payload.snapshotId)
            }
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

    private fun shouldReadBundledAsset(activeSnapshotId: String): Boolean =
        replacementPolicy?.shouldReadBundledAsset(activeSnapshotId)
            ?: (activeSnapshotId in replaceableActiveSnapshotIds)
}

const val BUNDLED_SNAPSHOT_ASSET = "synthetic-prayer-snapshot.json"
