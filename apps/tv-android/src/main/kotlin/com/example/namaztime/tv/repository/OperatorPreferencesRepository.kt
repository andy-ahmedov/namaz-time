package com.example.namaztime.tv.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
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
    val iqamahTimes: OperatorIqamahTimes = OperatorIqamahTimes(),
)

data class OperatorQrConfiguration(
    val httpsUrl: String = "",
    val title: String = "",
    val message: String = "",
) {
    val isEmpty: Boolean
        get() = httpsUrl.isBlank() && title.isBlank() && message.isBlank()
}

data class OperatorIqamahTimes(
    val fajr: String = "",
    val dhuhr: String = "",
    val asr: String = "",
    val maghrib: String = "",
    val isha: String = "",
) {
    fun forPrayer(prayerId: String): String? = when (prayerId) {
        "fajr" -> fajr
        "dhuhr" -> dhuhr
        "asr" -> asr
        "maghrib" -> maghrib
        "isha" -> isha
        else -> null
    }?.takeIf(String::isNotBlank)

    fun withPrayer(prayerId: String, time: String): OperatorIqamahTimes = when (prayerId) {
        "fajr" -> copy(fajr = time)
        "dhuhr" -> copy(dhuhr = time)
        "asr" -> copy(asr = time)
        "maghrib" -> copy(maghrib = time)
        "isha" -> copy(isha = time)
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

    suspend fun setIqamahTime(prayerId: String, time: String)

    suspend fun setIqamahTimes(times: OperatorIqamahTimes)
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
                    ?.takeIf(BUILT_IN_BACKGROUND_STYLE_IDS::contains)
                    ?: DEFAULT_BACKGROUND_STYLE_ID,
                qrConfiguration = OperatorQrConfiguration(
                    httpsUrl = values[QR_HTTPS_URL].orEmpty(),
                    title = values[QR_TITLE].orEmpty(),
                    message = values[QR_MESSAGE].orEmpty(),
                ).takeIf(::isValidQrConfiguration) ?: OperatorQrConfiguration(),
                iqamahTimes = OperatorIqamahTimes(
                    fajr = values[IQAMAH_FAJR].validIqamahTimeOrEmpty(),
                    dhuhr = values[IQAMAH_DHUHR].validIqamahTimeOrEmpty(),
                    asr = values[IQAMAH_ASR].validIqamahTimeOrEmpty(),
                    maghrib = values[IQAMAH_MAGHRIB].validIqamahTimeOrEmpty(),
                    isha = values[IQAMAH_ISHA].validIqamahTimeOrEmpty(),
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
        require(styleId in BUILT_IN_BACKGROUND_STYLE_IDS) { "unsupported background style" }
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

    override suspend fun setIqamahTime(prayerId: String, time: String) {
        require(prayerId in OPERATOR_IQAMAH_PRAYER_IDS) { "unsupported iqamah prayer" }
        val normalized = time.trim()
        require(normalized.isEmpty() || IQAMAH_TIME_PATTERN.matches(normalized)) {
            "invalid iqamah time"
        }
        dataStore.edit { values -> values[iqamahKey(prayerId)] = normalized }
    }

    override suspend fun setIqamahTimes(times: OperatorIqamahTimes) {
        val normalized = OPERATOR_IQAMAH_PRAYER_IDS.associateWith { prayerId ->
            times.forPrayer(prayerId).orEmpty().trim().also { value ->
                require(value.isEmpty() || IQAMAH_TIME_PATTERN.matches(value)) {
                    "invalid iqamah time"
                }
            }
        }
        dataStore.edit { values ->
            normalized.forEach { (prayerId, value) -> values[iqamahKey(prayerId)] = value }
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
        val IQAMAH_FAJR = stringPreferencesKey("operator_iqamah_fajr")
        val IQAMAH_DHUHR = stringPreferencesKey("operator_iqamah_dhuhr")
        val IQAMAH_ASR = stringPreferencesKey("operator_iqamah_asr")
        val IQAMAH_MAGHRIB = stringPreferencesKey("operator_iqamah_maghrib")
        val IQAMAH_ISHA = stringPreferencesKey("operator_iqamah_isha")

        fun iqamahKey(prayerId: String) = when (prayerId) {
            "fajr" -> IQAMAH_FAJR
            "dhuhr" -> IQAMAH_DHUHR
            "asr" -> IQAMAH_ASR
            "maghrib" -> IQAMAH_MAGHRIB
            "isha" -> IQAMAH_ISHA
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

internal fun isValidIqamahTimes(times: OperatorIqamahTimes): Boolean =
    OPERATOR_IQAMAH_PRAYER_IDS.all { prayerId ->
        times.forPrayer(prayerId)?.let(IQAMAH_TIME_PATTERN::matches) ?: true
    }

private val IQAMAH_TIME_PATTERN = Regex("(?:[01]\\d|2[0-3]):[0-5]\\d")
private val OPERATOR_QR_GENERATOR = QrCodeGenerator()

private fun String?.validIqamahTimeOrEmpty(): String =
    this?.takeIf(IQAMAH_TIME_PATTERN::matches).orEmpty()

const val DEFAULT_LANGUAGE_TAG = "ru"
val SUPPORTED_LANGUAGE_TAGS = setOf(DEFAULT_LANGUAGE_TAG, "en")
const val DEFAULT_BACKGROUND_STYLE_ID = "golden_dusk"
const val BLUE_HOUR_BACKGROUND_STYLE_ID = "blue_hour"
val BUILT_IN_BACKGROUND_STYLE_IDS = setOf(
    DEFAULT_BACKGROUND_STYLE_ID,
    BLUE_HOUR_BACKGROUND_STYLE_ID,
)

val OPERATOR_IQAMAH_PRAYER_IDS = listOf("fajr", "dhuhr", "asr", "maghrib", "isha")
