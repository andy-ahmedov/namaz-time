package ru.namaztime.tv.sync

import java.security.MessageDigest
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import ru.namaztime.tv.repository.LocalIqamahRule
import ru.namaztime.tv.repository.LocalPrayerDay
import ru.namaztime.tv.repository.LocalPrayerSchedule
import ru.namaztime.tv.repository.LocalSnapshotDiagnostics
import ru.namaztime.tv.repository.PrayerScheduleRepository

internal data class DevelopmentScheduleSelection(
    val cityId: String,
    val choiceId: String,
)

internal interface DevelopmentScheduleSelectionStore {
    val selection: StateFlow<DevelopmentScheduleSelection?>
    suspend fun save(selection: DevelopmentScheduleSelection)
}

internal class InMemoryDevelopmentScheduleSelectionStore(
    initialSelection: DevelopmentScheduleSelection? = null,
) : DevelopmentScheduleSelectionStore {
    private val mutableSelection = MutableStateFlow(initialSelection)
    override val selection: StateFlow<DevelopmentScheduleSelection?> =
        mutableSelection.asStateFlow()

    override suspend fun save(selection: DevelopmentScheduleSelection) {
        mutableSelection.value = selection
    }
}

/**
 * Interactive debug-only fixture for emulator UI work.
 *
 * Its prayer rows are visibly synthetic local preview data, never a real authority claim.
 * Release and pilot variants use the provisioned device-scoped HTTP client instead.
 */
internal class DevelopmentDeviceSetupGateway(
    private val selectionStore: DevelopmentScheduleSelectionStore =
        InMemoryDevelopmentScheduleSelectionStore(),
) : DeviceSetupGateway {
    override suspend fun searchCities(
        query: String,
    ): DeviceSetupResult<List<CanonicalCityCandidate>> {
        val normalized = query.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty()) return DeviceSetupResult.Success(emptyList())
        return DeviceSetupResult.Success(
            developmentCities.filter { city ->
                city.canonicalName.lowercase(Locale.ROOT).contains(normalized) ||
                    city.aliases.any { it.lowercase(Locale.ROOT).contains(normalized) }
            },
        )
    }

    override suspend fun loadScheduleChoices(
        cityId: String,
        date: LocalDate,
    ): DeviceSetupResult<DeviceCityScheduleChoiceSet> {
        val city = developmentCities.singleOrNull { it.id == cityId }
            ?: return DeviceSetupResult.Failure("setup_request_invalid", retryable = false)
        val authorityCount = if (city.canonicalName == "Москва") 2 else 1
        val choices = List(authorityCount) { index -> developmentChoice(city, date, index) }
        return DeviceSetupResult.Success(
            DeviceCityScheduleChoiceSet(
                revisionId = "synthetic-debug-revision-v1",
                revisionState = "staged",
                status = "available",
                automaticResolutionStatus = if (choices.size == 1) "resolved" else "ambiguous",
                automaticResolutionReason = if (choices.size == 1) {
                    "resolved"
                } else {
                    "same_tier_ambiguous"
                },
                selectionRequired = choices.size > 1,
                date = date,
                city = city,
                choices = choices,
                requestAllowed = false,
            ),
        )
    }

    override suspend fun requestScheduleChoice(
        cityId: String,
        choiceId: String,
        date: LocalDate,
        interactionId: String,
    ): DeviceSetupResult<PendingDeviceScheduleChoiceRequest> {
        return DeviceSetupResult.Failure("setup_debug_preview_only", retryable = false)
    }

    override suspend fun activateScheduleChoice(
        cityId: String,
        choiceId: String,
        date: LocalDate,
    ): DeviceSetupResult<Unit> {
        val city = developmentCities.singleOrNull { it.id == cityId }
            ?: return DeviceSetupResult.Failure("setup_request_invalid", retryable = false)
        val choice = List(if (city.canonicalName == "Москва") 2 else 1) { index ->
            developmentChoice(city, date, index)
        }.singleOrNull { it.id == choiceId && it.activationAllowed }
            ?: return DeviceSetupResult.Failure("setup_request_invalid", retryable = false)
        return try {
            selectionStore.save(DevelopmentScheduleSelection(city.id, choice.id))
            DeviceSetupResult.Success(Unit)
        } catch (_: IOException) {
            DeviceSetupResult.Failure("setup_activation_io", retryable = true)
        }
    }
}

internal class DevelopmentPrayerScheduleRepository(
    private val baseRepository: PrayerScheduleRepository,
    private val selectionStore: DevelopmentScheduleSelectionStore,
    private val clock: Clock = Clock.systemUTC(),
) : PrayerScheduleRepository {
    override fun observeActiveSchedule(): Flow<LocalPrayerSchedule?> = combine(
        baseRepository.observeActiveSchedule(),
        selectionStore.selection,
    ) { baseSchedule, selection ->
        selection?.toDevelopmentSchedule(clock) ?: baseSchedule
    }
}

private val developmentCities = listOf(
    developmentCity(
        id = "synthetic-debug-omsk",
        name = "Омск",
        aliases = listOf("Omsk"),
        subjectCode = "RU-OMS",
        subjectName = "Демо-каталог · Омская область",
        timezone = "Asia/Omsk",
    ),
    developmentCity(
        id = "synthetic-debug-moscow",
        name = "Москва",
        aliases = listOf("Moscow"),
        subjectCode = "RU-MOW",
        subjectName = "Демо-каталог · город федерального значения Москва",
        timezone = "Europe/Moscow",
    ),
    developmentCity(
        id = "synthetic-debug-moskovsky",
        name = "Московский",
        aliases = listOf("Moskovsky"),
        subjectCode = "RU-MOW",
        subjectName = "Демо-каталог · город федерального значения Москва",
        timezone = "Europe/Moscow",
    ),
    developmentCity(
        id = "synthetic-debug-ulyanovsk",
        name = "Ульяновск",
        aliases = listOf("Ulyanovsk"),
        subjectCode = "RU-ULY",
        subjectName = "Демо-каталог · Ульяновская область",
        timezone = "Europe/Ulyanovsk",
    ),
    developmentCity(
        id = "synthetic-debug-kirov-kir",
        name = "Киров",
        aliases = listOf("Kirov"),
        subjectCode = "RU-KIR",
        subjectName = "Демо-каталог · Кировская область",
        timezone = "Europe/Kirov",
    ),
    developmentCity(
        id = "synthetic-debug-kirov-klu",
        name = "Киров",
        aliases = listOf("Kirov"),
        subjectCode = "RU-KLU",
        subjectName = "Демо-каталог · Калужская область",
        settlementType = "village",
        timezone = "Europe/Moscow",
    ),
)

private fun developmentCity(
    id: String,
    name: String,
    aliases: List<String>,
    subjectCode: String,
    subjectName: String,
    timezone: String,
    settlementType: String = "city",
) = CanonicalCityCandidate(
    id = id,
    canonicalName = name,
    aliases = aliases,
    federalSubjectCode = subjectCode,
    federalSubjectName = subjectName,
    settlementType = settlementType,
    timezone = timezone,
    latitude = 0.0,
    longitude = 0.0,
    geographicSourceId = "synthetic-debug-only",
    geographicRevision = "fixture-v1",
    geographicLicense = "synthetic-test-only",
)

private fun developmentChoice(
    city: CanonicalCityCandidate,
    date: LocalDate,
    index: Int,
): DeviceScheduleChoice {
    val number = index + 1
    val authority = "Демо-организация ${city.canonicalName} №$number"
    val suffix = sha256("${city.id}\u0000$number")
    return DeviceScheduleChoice(
        id = "schedule-choice-$suffix",
        displayLabel = "${city.canonicalName} — $authority",
        authorityLabel = authority,
        tier = "exact_city_timetable",
        selectable = true,
        executable = false,
        requestable = false,
        policyId = "synthetic-debug-policy-${suffix.take(16)}",
        policyKind = "timetable",
        approvalId = "synthetic-debug-approval-$number",
        effectiveFrom = date.minusYears(1),
        effectiveTo = date.plusYears(1),
        scopeId = "synthetic-debug-scope-${suffix.take(16)}",
        scopeKind = "city",
        scopeDescription = "Демонстрационные данные — не реальное расписание",
        authorities = listOf(
            DeviceScheduleAuthority(
                id = "synthetic-debug-authority-${suffix.take(16)}",
                name = authority,
                evidenceLabel = "PROPOSAL",
            ),
        ),
        source = DeviceScheduleSource(
            id = "synthetic-debug-source-${suffix.take(16)}",
            kind = "manual_import",
            status = "synthetic_debug",
            canonicalUrl = null,
            freshThrough = null,
        ),
        scheduleId = "synthetic-debug-timetable-${suffix.take(16)}",
        scheduleKind = "timetable",
        scheduleTimezone = city.timezone,
        publishedSnapshotId = null,
        localPreview = developmentPreview(date, city.timezone, index),
        activationAllowed = true,
    )
}

private fun developmentPreview(
    date: LocalDate,
    timezone: String,
    index: Int,
): DeviceSchedulePreview {
    val minuteShift = index * 5L
    fun row(
        prayer: DeviceSchedulePreviewPrayer,
        adhan: String,
        iqamah: String?,
    ) = DeviceSchedulePreviewRow(
        prayer = prayer,
        adhan = LocalTime.parse(adhan).plusMinutes(minuteShift),
        iqamah = iqamah?.let(LocalTime::parse)?.plusMinutes(minuteShift),
    )
    return DeviceSchedulePreview(
        date = date,
        timezone = timezone,
        evidenceLabel = "PROPOSAL",
        rows = listOf(
            row(DeviceSchedulePreviewPrayer.FAJR, "04:20", "04:35"),
            row(DeviceSchedulePreviewPrayer.SUNRISE, "06:01", null),
            row(DeviceSchedulePreviewPrayer.DHUHR, "12:28", "13:00"),
            row(DeviceSchedulePreviewPrayer.ASR, "16:24", "16:40"),
            row(DeviceSchedulePreviewPrayer.MAGHRIB, "19:03", "19:13"),
            row(DeviceSchedulePreviewPrayer.ISHA, "20:41", "21:00"),
        ),
    )
}

private fun DevelopmentScheduleSelection.toDevelopmentSchedule(
    clock: Clock,
): LocalPrayerSchedule? {
    val city = developmentCities.singleOrNull { it.id == cityId } ?: return null
    val localDate = LocalDate.now(clock.withZone(ZoneId.of(city.timezone)))
    val authorityCount = if (city.canonicalName == "Москва") 2 else 1
    val choiceWithIndex = List(authorityCount) { index ->
        index to developmentChoice(city, localDate, index)
    }.singleOrNull { (_, candidate) -> candidate.id == choiceId } ?: return null
    val (index, choice) = choiceWithIndex
    val preview = requireNotNull(choice.localPreview)
    val coverageFrom = localDate.minusDays(1)
    val coverageTo = localDate.plusYears(1)
    val days = generateSequence(coverageFrom) { date ->
        date.plusDays(1).takeIf { !it.isAfter(coverageTo) }
    }.map { date ->
        val rows = developmentPreview(date, city.timezone, index).rows
            .associateBy { it.prayer }
        LocalPrayerDay(
            localDate = date.toString(),
            fajr = rows.getValue(DeviceSchedulePreviewPrayer.FAJR).adhan.toString(),
            sunrise = rows.getValue(DeviceSchedulePreviewPrayer.SUNRISE).adhan.toString(),
            dhuhr = rows.getValue(DeviceSchedulePreviewPrayer.DHUHR).adhan.toString(),
            asr = rows.getValue(DeviceSchedulePreviewPrayer.ASR).adhan.toString(),
            maghrib = rows.getValue(DeviceSchedulePreviewPrayer.MAGHRIB).adhan.toString(),
            isha = rows.getValue(DeviceSchedulePreviewPrayer.ISHA).adhan.toString(),
        )
    }.toList()
    val fixedIqamah = preview.rows.filter { it.iqamah != null }.map { row ->
        LocalIqamahRule(
            id = "synthetic-debug-iqamah-${row.prayer.name.lowercase()}",
            prayer = row.prayer.name.lowercase(),
            validFrom = coverageFrom.toString(),
            validTo = coverageTo.toString(),
            weekdaysMask = 127,
            priority = 0,
            mode = "fixed_time",
            fixedTime = requireNotNull(row.iqamah).toString(),
            offsetMinutes = null,
            reason = "synthetic debug fixture",
        )
    }
    val fixtureHash = sha256("${city.id}\u0000${choice.id}\u0000fixture-v1")
    return LocalPrayerSchedule(
        snapshotId = "synthetic-debug-active-${fixtureHash.take(16)}",
        mosqueId = "synthetic-debug-mosque-${fixtureHash.take(16)}",
        mosqueName = choice.authorityLabel,
        locality = city.canonicalName,
        timezoneId = city.timezone,
        sourceKind = "manual_import",
        authorityName = choice.authorityLabel,
        countryCode = "RU",
        region = city.federalSubjectName,
        sourceId = choice.source.id,
        geographicScope = choice.scopeDescription,
        retrievedAt = clock.instant().toString(),
        sourceEffectiveFrom = coverageFrom.toString(),
        sourceEffectiveTo = coverageTo.toString(),
        licenseReference = "synthetic-test-only",
        attribution = "PROPOSAL · локальное демонстрационное расписание",
        coverageFrom = coverageFrom.toString(),
        coverageTo = coverageTo.toString(),
        days = days,
        iqamahRules = fixedIqamah,
        diagnostics = LocalSnapshotDiagnostics(
            dataClassification = "synthetic",
            generatedAt = clock.instant().toString(),
            rawSha256 = fixtureHash,
            parserVersion = "debug-fixture-v1",
            approvalId = choice.approvalId,
            approvalStatus = "proposal",
            approvedBy = "",
            approvedAt = "",
            approvalScope = "not approved; debug only",
            signingKeyId = "not-signed-debug-proposal",
            canonicalSha256 = fixtureHash,
        ),
    )
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.encodeToByteArray())
    .joinToString("") { byte -> "%02x".format(byte) }
