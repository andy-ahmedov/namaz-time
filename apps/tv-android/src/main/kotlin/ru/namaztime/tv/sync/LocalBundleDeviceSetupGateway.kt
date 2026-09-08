package ru.namaztime.tv.sync

import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.namaztime.tv.data.local.NamazDatabase
import ru.namaztime.tv.data.local.SnapshotImportException
import ru.namaztime.tv.data.local.SnapshotImportResult
import ru.namaztime.tv.data.local.SnapshotImporter
import ru.namaztime.tv.data.local.toTimeEngineInput
import ru.namaztime.tv.data.snapshot.ActivatableSnapshot
import ru.namaztime.tv.data.snapshot.SnapshotAuthenticityException
import ru.namaztime.tv.data.snapshot.SnapshotValidationException
import ru.namaztime.tv.domain.PrayerTimeEngine
import ru.namaztime.tv.domain.PrayerTimeResolution

internal class LocalBundleDeviceSetupGateway(
    private val loadBundle: () -> LocalSetupBundle,
    private val database: NamazDatabase,
    private val clock: Clock = Clock.systemUTC(),
) : DeviceSetupGateway {
    private val mutex = Mutex()
    private var bundle: LocalSetupBundle? = null
    private var previews = emptyMap<PreviewKey, PreviewSelection>()
    private val importer = SnapshotImporter(database)

    override suspend fun searchCities(query: String): DeviceSetupResult<List<CanonicalCityCandidate>> = operation {
        requireBundle(query.codePointCount(0, query.length) <= 200 && '\u0000' !in query, "setup_request_invalid")
        val result = currentBundle().searchCities(query)
        currentCoroutineContext().ensureActive()
        result
    }

    override suspend fun loadScheduleChoices(cityId: String, date: LocalDate): DeviceSetupResult<DeviceCityScheduleChoiceSet> = operation {
        previews = emptyMap()
        val current = currentBundle()
        val set = current.choiceSet(cityId, date)
        requireBundle(localToday(set.city.timezone) == date, "setup_local_preview_expired")
        val expectedActiveId = database.snapshotDao().getSelection()?.activeSnapshotId
        val pending = mutableMapOf<PreviewKey, PreviewSelection>()
        val result = set.copy(choices = set.choices.map { choice ->
            val input = current.snapshotFor(cityId, choice.id, date)
            val snapshot = input.payload
            val resolution = PrayerTimeEngine().resolve(snapshot.toTimeEngineInput(), clock.instant())
                as? PrayerTimeResolution.Available ?: throw LocalSetupBundleException("setup_local_preview_invalid")
            requireBundle(resolution.localDate == date, "setup_local_preview_expired")
            val rows = DeviceSchedulePreviewPrayer.entries.map { prayer ->
                val onset = resolution.prayers[prayer.name.lowercase(java.util.Locale.ROOT)]
                    ?: throw LocalSetupBundleException("setup_local_preview_invalid")
                DeviceSchedulePreviewRow(prayer, onset.adhan, onset.iqamah)
            }
            val preview = DeviceSchedulePreview(
                date, snapshot.mosque.timezone,
                choice.qualification?.authority?.evidenceLabel ?: choice.authorities.joinToString(" · ") { it.evidenceLabel },
                rows, dataClassification = snapshot.dataClassification,
                provenance = DeviceSchedulePreviewProvenance(snapshot.snapshotId, snapshot.integrity.canonicalSha256,
                    snapshot.source.rawSha256, snapshot.source.parserVersion, snapshot.source.retrievedAt,
                    attribution = snapshot.source.attribution),
            )
            pending[PreviewKey(cityId, choice.id, date)] = PreviewSelection(current.id, expectedActiveId, input)
            choice.copy(localPreview = preview, activationAllowed = true)
        })
        currentCoroutineContext().ensureActive()
        previews = pending
        result
    }

    override suspend fun activateScheduleChoice(cityId: String, choiceId: String, date: LocalDate): DeviceSetupResult<Unit> = operation {
        val selection = previews[PreviewKey(cityId, choiceId, date)]
            ?: throw LocalSetupBundleException("setup_local_preview_required")
        val current = currentBundle()
        requireBundle(current.id == selection.bundleId && localToday(selection.input.payload.mosque.timezone) == date,
            "setup_local_preview_expired")
        requireBundle(current.snapshotFor(cityId, choiceId, date) === selection.input, "setup_local_preview_expired")
        when (importer.activateSelected(selection.input, selection.expectedActiveSnapshotId)) {
            is SnapshotImportResult.Activated, is SnapshotImportResult.AlreadyActive -> Unit
            is SnapshotImportResult.SelectionChanged -> throw LocalSetupBundleException("setup_local_selection_changed")
        }
        previews = emptyMap()
    }

    override suspend fun requestScheduleChoice(cityId: String, choiceId: String, date: LocalDate, interactionId: String): DeviceSetupResult<PendingDeviceScheduleChoiceRequest> =
        DeviceSetupResult.Failure("setup_local_remote_request_unavailable", retryable = false)

    private fun currentBundle(): LocalSetupBundle = bundle ?: loadBundle().also { bundle = it }
    private fun localToday(timezone: String): LocalDate = clock.instant().atZone(ZoneId.of(timezone)).toLocalDate()

    private suspend fun <T> operation(block: suspend () -> T): DeviceSetupResult<T> = withContext(Dispatchers.IO) {
        try {
            mutex.withLock {
                currentCoroutineContext().ensureActive()
                DeviceSetupResult.Success(block())
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: LocalSetupBundleException) {
            DeviceSetupResult.Failure(error.code, retryable = false)
        } catch (_: IOException) {
            DeviceSetupResult.Failure("setup_local_io", retryable = true)
        } catch (_: SnapshotAuthenticityException) {
            DeviceSetupResult.Failure("setup_local_authentication_failed", retryable = false)
        } catch (_: SnapshotValidationException) {
            DeviceSetupResult.Failure("setup_local_snapshot_invalid", retryable = false)
        } catch (error: SnapshotImportException) {
            DeviceSetupResult.Failure("setup_local_${error.code}", retryable = false)
        } catch (_: Exception) {
            DeviceSetupResult.Failure("setup_local_bundle_invalid", retryable = false)
        }
    }

    private data class PreviewKey(val cityId: String, val choiceId: String, val date: LocalDate)
    private data class PreviewSelection(val bundleId: String, val expectedActiveSnapshotId: String?, val input: ActivatableSnapshot)
}
