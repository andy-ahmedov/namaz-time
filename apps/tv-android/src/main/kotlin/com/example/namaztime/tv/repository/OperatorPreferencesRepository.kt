package com.example.namaztime.tv.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.namaztime.tv.domain.CampaignEngine
import com.example.namaztime.tv.domain.CampaignInput
import com.example.namaztime.tv.domain.CampaignPreview
import com.example.namaztime.tv.domain.QrCodeGenerator
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

data class OperatorPreferences(
    val lastSettingsDestination: String? = null,
    val reducedMotion: Boolean = true,
    val languageTag: String = DEFAULT_LANGUAGE_TAG,
    val screenRetentionShiftEnabled: Boolean = true,
    val backgroundStyleId: String = DEFAULT_BACKGROUND_STYLE_ID,
    val qrConfiguration: OperatorQrConfiguration = OperatorQrConfiguration(),
    val iqamahOffsets: OperatorIqamahOffsets = OperatorIqamahOffsets(),
)

data class OperatorQrConfiguration(
    val httpsUrl: String = "",
    val title: String = "",
    val message: String = "",
) {
    val isEmpty: Boolean
        get() = httpsUrl.isBlank() && title.isBlank() && message.isBlank()
}

data class OperatorIqamahOffsets(
    val fajr: Int? = null,
    val dhuhr: Int? = null,
    val asr: Int? = null,
    val maghrib: Int? = null,
    val isha: Int? = null,
) {
    fun forPrayer(prayerId: String): Int? = when (prayerId) {
        "fajr" -> fajr
        "dhuhr" -> dhuhr
        "asr" -> asr
        "maghrib" -> maghrib
        "isha" -> isha
        else -> null
    }

    fun withPrayer(prayerId: String, offsetMinutes: Int?): OperatorIqamahOffsets = when (prayerId) {
        "fajr" -> copy(fajr = offsetMinutes)
        "dhuhr" -> copy(dhuhr = offsetMinutes)
        "asr" -> copy(asr = offsetMinutes)
        "maghrib" -> copy(maghrib = offsetMinutes)
        "isha" -> copy(isha = offsetMinutes)
        else -> throw IllegalArgumentException("unsupported iqamah prayer")
    }

    val configuredPrayerIds: Set<String>
        get() = OPERATOR_IQAMAH_PRAYER_IDS.filterTo(linkedSetOf()) { forPrayer(it) != null }
}

interface OperatorPreferencesRepository {
    val preferences: Flow<OperatorPreferences>

    suspend fun setLastSettingsDestination(route: String)

    suspend fun setReducedMotion(enabled: Boolean)

    suspend fun setLanguageTag(languageTag: String)

    suspend fun setScreenRetentionShiftEnabled(enabled: Boolean)

    suspend fun setBackgroundStyleId(styleId: String)

    suspend fun setQrConfiguration(configuration: OperatorQrConfiguration)

    suspend fun setIqamahOffset(prayerId: String, offsetMinutes: Int?)

    suspend fun setIqamahOffsets(offsets: OperatorIqamahOffsets)
}

class DataStoreOperatorPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) : OperatorPreferencesRepository {
    override val preferences: Flow<OperatorPreferences> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { values ->
            OperatorPreferences(
                lastSettingsDestination = values[LAST_SETTINGS_DESTINATION],
                reducedMotion = values[REDUCED_MOTION] ?: true,
                languageTag = values[LANGUAGE_TAG]
                    ?.takeIf(SUPPORTED_LANGUAGE_TAGS::contains)
                    ?: DEFAULT_LANGUAGE_TAG,
                screenRetentionShiftEnabled = values[SCREEN_RETENTION_SHIFT_ENABLED] ?: true,
                backgroundStyleId = values[BACKGROUND_STYLE_ID]
                    ?.takeIf(SELECTABLE_BACKGROUND_STYLE_IDS::contains)
                    ?: DEFAULT_BACKGROUND_STYLE_ID,
                qrConfiguration = OperatorQrConfiguration(
                    httpsUrl = values[QR_HTTPS_URL].orEmpty(),
                    title = values[QR_TITLE].orEmpty(),
                    message = values[QR_MESSAGE].orEmpty(),
                ).takeIf(::isValidQrConfiguration) ?: OperatorQrConfiguration(),
                iqamahOffsets = OperatorIqamahOffsets(
                    fajr = values[IQAMAH_OFFSET_FAJR].validIqamahOffsetOrNull(),
                    dhuhr = values[IQAMAH_OFFSET_DHUHR].validIqamahOffsetOrNull(),
                    asr = values[IQAMAH_OFFSET_ASR].validIqamahOffsetOrNull(),
                    maghrib = values[IQAMAH_OFFSET_MAGHRIB].validIqamahOffsetOrNull(),
                    isha = values[IQAMAH_OFFSET_ISHA].validIqamahOffsetOrNull(),
                ),
            )
        }

    override suspend fun setLastSettingsDestination(route: String) {
        dataStore.edit { it[LAST_SETTINGS_DESTINATION] = route }
    }

    override suspend fun setReducedMotion(enabled: Boolean) {
        dataStore.edit { it[REDUCED_MOTION] = enabled }
    }

    override suspend fun setLanguageTag(languageTag: String) {
        require(languageTag in SUPPORTED_LANGUAGE_TAGS) { "unsupported language tag" }
        dataStore.edit { it[LANGUAGE_TAG] = languageTag }
    }

    override suspend fun setScreenRetentionShiftEnabled(enabled: Boolean) {
        dataStore.edit { it[SCREEN_RETENTION_SHIFT_ENABLED] = enabled }
    }

    override suspend fun setBackgroundStyleId(styleId: String) {
        require(styleId in SELECTABLE_BACKGROUND_STYLE_IDS) { "unsupported background style" }
        dataStore.edit { it[BACKGROUND_STYLE_ID] = styleId }
    }

    override suspend fun setQrConfiguration(configuration: OperatorQrConfiguration) {
        require(isValidQrConfiguration(configuration)) { "invalid QR configuration" }
        dataStore.edit { values ->
            values[QR_HTTPS_URL] = configuration.httpsUrl.trim()
            values[QR_TITLE] = configuration.title.trim()
            values[QR_MESSAGE] = configuration.message.trim()
        }
    }

    override suspend fun setIqamahOffset(prayerId: String, offsetMinutes: Int?) {
        require(prayerId in OPERATOR_IQAMAH_PRAYER_IDS) { "unsupported iqamah prayer" }
        require(offsetMinutes == null || offsetMinutes in OPERATOR_IQAMAH_OFFSET_RANGE) {
            "invalid iqamah offset"
        }
        dataStore.edit { values ->
            val key = iqamahOffsetKey(prayerId)
            if (offsetMinutes == null) values.remove(key) else values[key] = offsetMinutes
        }
    }

    override suspend fun setIqamahOffsets(offsets: OperatorIqamahOffsets) {
        require(isValidIqamahOffsets(offsets)) { "invalid iqamah offsets" }
        dataStore.edit { values ->
            OPERATOR_IQAMAH_PRAYER_IDS.forEach { prayerId ->
                val key = iqamahOffsetKey(prayerId)
                val offset = offsets.forPrayer(prayerId)
                if (offset == null) values.remove(key) else values[key] = offset
            }
        }
    }

    private companion object {
        val LAST_SETTINGS_DESTINATION = stringPreferencesKey("last_settings_destination")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val LANGUAGE_TAG = stringPreferencesKey("language_tag")
        val SCREEN_RETENTION_SHIFT_ENABLED = booleanPreferencesKey(
            "screen_retention_shift_enabled",
        )
        val BACKGROUND_STYLE_ID = stringPreferencesKey("background_style_id")
        val QR_HTTPS_URL = stringPreferencesKey("operator_qr_https_url")
        val QR_TITLE = stringPreferencesKey("operator_qr_title")
        val QR_MESSAGE = stringPreferencesKey("operator_qr_message")
        val IQAMAH_OFFSET_FAJR = intPreferencesKey("operator_iqamah_offset_fajr")
        val IQAMAH_OFFSET_DHUHR = intPreferencesKey("operator_iqamah_offset_dhuhr")
        val IQAMAH_OFFSET_ASR = intPreferencesKey("operator_iqamah_offset_asr")
        val IQAMAH_OFFSET_MAGHRIB = intPreferencesKey("operator_iqamah_offset_maghrib")
        val IQAMAH_OFFSET_ISHA = intPreferencesKey("operator_iqamah_offset_isha")

        fun iqamahOffsetKey(prayerId: String) = when (prayerId) {
            "fajr" -> IQAMAH_OFFSET_FAJR
            "dhuhr" -> IQAMAH_OFFSET_DHUHR
            "asr" -> IQAMAH_OFFSET_ASR
            "maghrib" -> IQAMAH_OFFSET_MAGHRIB
            "isha" -> IQAMAH_OFFSET_ISHA
            else -> throw IllegalArgumentException("unsupported iqamah prayer")
        }
    }
}

internal fun OperatorQrConfiguration.toCampaignInput() = CampaignInput(
    id = "operator-local-qr",
    kind = "donation",
    httpsUrl = httpsUrl.trim(),
    title = title.trim(),
    subtitle = message.trim().takeIf(String::isNotEmpty),
    startsAt = "2000-01-01T00:00:00Z",
    endsAt = "9999-12-31T23:59:59Z",
    placement = "with_prayer_times",
)

internal fun isValidQrConfiguration(configuration: OperatorQrConfiguration): Boolean {
    if (configuration.isEmpty) return true
    val campaign = CampaignEngine().preview(configuration.toCampaignInput())
    if (campaign !is CampaignPreview.Valid) return false
    return runCatching { OPERATOR_QR_GENERATOR.generate(campaign.campaign.httpsUrl) }.isSuccess
}

internal fun isValidIqamahOffsets(offsets: OperatorIqamahOffsets): Boolean =
    OPERATOR_IQAMAH_PRAYER_IDS.all { prayerId ->
        offsets.forPrayer(prayerId)?.let(OPERATOR_IQAMAH_OFFSET_RANGE::contains) ?: true
    }

private val OPERATOR_QR_GENERATOR = QrCodeGenerator()

private fun Int?.validIqamahOffsetOrNull(): Int? =
    this?.takeIf(OPERATOR_IQAMAH_OFFSET_RANGE::contains)

const val DEFAULT_LANGUAGE_TAG = "ru"
val SUPPORTED_LANGUAGE_TAGS = setOf(DEFAULT_LANGUAGE_TAG, "en")
const val DEFAULT_BACKGROUND_STYLE_ID = "golden_dusk"
const val BLUE_HOUR_BACKGROUND_STYLE_ID = "blue_hour"
const val CUSTOM_BACKGROUND_STYLE_ID = "custom"
const val NIGHT_MINARET_BACKGROUND_STYLE_ID = "night_minaret"
const val DESERT_DAWN_BACKGROUND_STYLE_ID = "desert_dawn"
const val EMERALD_MOSQUE_BACKGROUND_STYLE_ID = "emerald_mosque"
const val WINTER_TWILIGHT_BACKGROUND_STYLE_ID = "winter_twilight"
const val AUTUMN_COURTYARD_BACKGROUND_STYLE_ID = "autumn_courtyard"
const val CELESTIAL_NAVY_BACKGROUND_STYLE_ID = "celestial_navy"
val BUILT_IN_BACKGROUND_STYLE_IDS = setOf(
    DEFAULT_BACKGROUND_STYLE_ID,
    BLUE_HOUR_BACKGROUND_STYLE_ID,
    NIGHT_MINARET_BACKGROUND_STYLE_ID,
    DESERT_DAWN_BACKGROUND_STYLE_ID,
    EMERALD_MOSQUE_BACKGROUND_STYLE_ID,
    WINTER_TWILIGHT_BACKGROUND_STYLE_ID,
    AUTUMN_COURTYARD_BACKGROUND_STYLE_ID,
    CELESTIAL_NAVY_BACKGROUND_STYLE_ID,
)
val SELECTABLE_BACKGROUND_STYLE_IDS = BUILT_IN_BACKGROUND_STYLE_IDS + CUSTOM_BACKGROUND_STYLE_ID

val OPERATOR_IQAMAH_PRAYER_IDS = listOf("fajr", "dhuhr", "asr", "maghrib", "isha")
val OPERATOR_IQAMAH_OFFSET_RANGE = 0..180
