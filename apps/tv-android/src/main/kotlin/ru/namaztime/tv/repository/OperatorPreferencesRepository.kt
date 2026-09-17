package ru.namaztime.tv.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import ru.namaztime.tv.domain.CampaignEngine
import ru.namaztime.tv.domain.CampaignInput
import ru.namaztime.tv.domain.CampaignPreview
import ru.namaztime.tv.domain.QrCodeGenerator
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

data class OperatorPreferences(
    val lastSettingsDestination: String? = null,
    val reducedMotion: Boolean = true,
    val languageTag: String = DEFAULT_LANGUAGE_TAG,
    val screenRetentionShiftEnabled: Boolean = true,
    val showIqamahOnSchedule: Boolean = true,
    val scheduleBlockTransparency: Int = 20,
    val scheduleLayoutMode: ScheduleLayoutMode = ScheduleLayoutMode.STANDARD,
    val mosquePresentationIdentity: OperatorMosquePresentationIdentity =
        OperatorMosquePresentationIdentity(),
    val backgroundStyleId: String = DEFAULT_BACKGROUND_STYLE_ID,
    val qrConfiguration: OperatorQrConfiguration = OperatorQrConfiguration(),
    val iqamahConfiguration: OperatorIqamahConfiguration = OperatorIqamahConfiguration(),
    val donationConfiguration: OperatorDonationConfiguration = OperatorDonationConfiguration(),
    val displayMode: OperatorDisplayMode = OperatorDisplayMode.SCHEDULE,
)

data class OperatorMosquePresentationIdentity(
    val displayName: String = "",
    val displayAddress: String = "",
)

enum class ScheduleLayoutMode(val id: String) {
    STANDARD("standard"),
    RIGHT_SIDE_COMPACT("right_side_compact");

    companion object {
        fun fromId(id: String?): ScheduleLayoutMode = entries.firstOrNull { it.id == id } ?: STANDARD
    }
}

enum class OperatorDisplayMode(val id: String) {
    SCHEDULE("schedule"),
    DONATION("donation"),
    ;

    companion object {
        fun fromId(id: String?): OperatorDisplayMode = entries.firstOrNull { it.id == id } ?: SCHEDULE
    }
}

internal const val DEFAULT_QR_HTTPS_URL =
    "https://qr.nspk.ru/BS1A005J2EMTHO629O5R87V15MNNJLGR?type=01&bank=100000000006&crc=D505"

data class OperatorDonationConfiguration(
    val httpsUrl: String = "",
    val recipient: String = "",
    val bank: String = "",
    val cardNumber: String = "",
    val phone: String = "",
    val gratitudeMessage: String = "",
    val imageStyleId: String = DEFAULT_DONATION_IMAGE_STYLE_ID,
) {
    val isEmpty: Boolean
        get() = httpsUrl.isBlank() && !hasAnyTransferDetail

    val hasAnyTransferDetail: Boolean
        get() = listOf(recipient, bank, cardNumber, phone).any(String::isNotBlank)
}

data class OperatorQrConfiguration(
    val httpsUrl: String = "",
    val title: String = "",
    val message: String = "",
) {
    val isEmpty: Boolean
        get() = httpsUrl.isBlank() && title.isBlank() && message.isBlank()
}

data class OperatorIqamahConfiguration(
    val fajrOffsetMinutes: Int? = null,
    val dhuhrFixedTimeMinutes: Int? = null,
    val asrOffsetMinutes: Int? = null,
    val maghribOffsetMinutes: Int? = null,
    val ishaOffsetMinutes: Int? = null,
) {
    fun offsetForPrayer(prayerId: String): Int? = when (prayerId) {
        "fajr" -> fajrOffsetMinutes
        "asr" -> asrOffsetMinutes
        "maghrib" -> maghribOffsetMinutes
        "isha" -> ishaOffsetMinutes
        else -> null
    }

    fun editorValueForPrayer(prayerId: String): Int? = when (prayerId) {
        "dhuhr" -> dhuhrFixedTimeMinutes
        else -> offsetForPrayer(prayerId)
    }

    fun withEditorValue(prayerId: String, value: Int?): OperatorIqamahConfiguration = when (prayerId) {
        "fajr" -> copy(fajrOffsetMinutes = value)
        "dhuhr" -> copy(dhuhrFixedTimeMinutes = value)
        "asr" -> copy(asrOffsetMinutes = value)
        "maghrib" -> copy(maghribOffsetMinutes = value)
        "isha" -> copy(ishaOffsetMinutes = value)
        else -> throw IllegalArgumentException("unsupported iqamah prayer")
    }

    val configuredPrayerIds: Set<String>
        get() = OPERATOR_IQAMAH_PRAYER_IDS.filterTo(linkedSetOf()) {
            editorValueForPrayer(it) != null
        }
}

interface OperatorPreferencesRepository {
    val preferences: Flow<OperatorPreferences>

    suspend fun setLastSettingsDestination(route: String)

    suspend fun setShowIqamahOnSchedule(enabled: Boolean)

    suspend fun setScheduleBlockTransparency(percent: Int)

    suspend fun setScheduleLayoutMode(mode: ScheduleLayoutMode)

    suspend fun setReducedMotion(enabled: Boolean)

    suspend fun setLanguageTag(languageTag: String)

    suspend fun setScreenRetentionShiftEnabled(enabled: Boolean)

    suspend fun setMosquePresentationIdentity(identity: OperatorMosquePresentationIdentity)

    suspend fun setBackgroundStyleId(styleId: String)

    suspend fun setQrConfiguration(configuration: OperatorQrConfiguration)

    suspend fun setIqamahOffset(prayerId: String, offsetMinutes: Int?)

    suspend fun setIqamahConfiguration(configuration: OperatorIqamahConfiguration)

    suspend fun setDonationConfiguration(configuration: OperatorDonationConfiguration)

    suspend fun setDonationImageStyleId(styleId: String)

    suspend fun setDisplayMode(mode: OperatorDisplayMode)
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
            val donationConfiguration = values.toDonationConfiguration()
                .takeIf(::isValidDonationConfiguration)
                ?: OperatorDonationConfiguration()
            val displayMode = OperatorDisplayMode.fromId(values[DISPLAY_MODE])
                .takeIf { it != OperatorDisplayMode.DONATION || !donationConfiguration.isEmpty }
                ?: OperatorDisplayMode.SCHEDULE
            OperatorPreferences(
                lastSettingsDestination = values[LAST_SETTINGS_DESTINATION],
                reducedMotion = values[REDUCED_MOTION] ?: true,
                showIqamahOnSchedule = values[SHOW_IQAMAH_ON_SCHEDULE] ?: true,
                scheduleBlockTransparency = values[SCHEDULE_BLOCK_TRANSPARENCY]?.takeIf { it in 0..100 } ?: 20,
                scheduleLayoutMode = ScheduleLayoutMode.fromId(values[SCHEDULE_LAYOUT_MODE]),
                languageTag = values[LANGUAGE_TAG]
                    ?.takeIf(SUPPORTED_LANGUAGE_TAGS::contains)
                    ?: DEFAULT_LANGUAGE_TAG,
                screenRetentionShiftEnabled = values[SCREEN_RETENTION_SHIFT_ENABLED] ?: true,
                mosquePresentationIdentity = OperatorMosquePresentationIdentity(
                    displayName = values[MOSQUE_DISPLAY_NAME].orEmpty(),
                    displayAddress = values[MOSQUE_DISPLAY_ADDRESS].orEmpty(),
                ).takeIf(::isValidMosquePresentationIdentity)
                    ?: OperatorMosquePresentationIdentity(),
                backgroundStyleId = values[BACKGROUND_STYLE_ID]
                    ?.takeIf(SELECTABLE_BACKGROUND_STYLE_IDS::contains)
                    ?: DEFAULT_BACKGROUND_STYLE_ID,
                qrConfiguration = safePersistedQrConfiguration(
                    OperatorQrConfiguration(
                        httpsUrl = values[QR_HTTPS_URL].orEmpty(),
                        title = values[QR_TITLE].orEmpty(),
                        message = values[QR_MESSAGE].orEmpty(),
                    ),
                ),
                iqamahConfiguration = OperatorIqamahConfiguration(
                    fajrOffsetMinutes = values[IQAMAH_OFFSET_FAJR].validIqamahOffsetOrNull(),
                    dhuhrFixedTimeMinutes = values[DHUHR_FIXED_TIME_MINUTES]
                        .validDhuhrFixedTimeOrNull(),
                    asrOffsetMinutes = values[IQAMAH_OFFSET_ASR].validIqamahOffsetOrNull(),
                    maghribOffsetMinutes = values[IQAMAH_OFFSET_MAGHRIB]
                        .validIqamahOffsetOrNull(),
                    ishaOffsetMinutes = values[IQAMAH_OFFSET_ISHA].validIqamahOffsetOrNull(),
                ),
                donationConfiguration = donationConfiguration,
                displayMode = displayMode,
            )
        }

    override suspend fun setLastSettingsDestination(route: String) {
        dataStore.edit { it[LAST_SETTINGS_DESTINATION] = route }
    }

    override suspend fun setShowIqamahOnSchedule(enabled: Boolean) {
        dataStore.edit { it[SHOW_IQAMAH_ON_SCHEDULE] = enabled }
    }

    override suspend fun setScheduleBlockTransparency(percent: Int) {
        require(percent in 0..100)
        dataStore.edit { it[SCHEDULE_BLOCK_TRANSPARENCY] = percent }
    }

    override suspend fun setScheduleLayoutMode(mode: ScheduleLayoutMode) {
        dataStore.edit { it[SCHEDULE_LAYOUT_MODE] = mode.id }
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

    override suspend fun setMosquePresentationIdentity(
        identity: OperatorMosquePresentationIdentity,
    ) {
        require(isValidMosquePresentationIdentity(identity)) {
            "invalid mosque presentation identity"
        }
        dataStore.edit { values ->
            val displayName = identity.displayName.trim()
            val displayAddress = identity.displayAddress.trim()
            if (displayName.isEmpty()) values.remove(MOSQUE_DISPLAY_NAME)
            else values[MOSQUE_DISPLAY_NAME] = displayName
            if (displayAddress.isEmpty()) values.remove(MOSQUE_DISPLAY_ADDRESS)
            else values[MOSQUE_DISPLAY_ADDRESS] = displayAddress
        }
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
        require(prayerId in OPERATOR_IQAMAH_OFFSET_PRAYER_IDS) { "unsupported iqamah prayer" }
        require(offsetMinutes == null || offsetMinutes in OPERATOR_IQAMAH_OFFSET_RANGE) {
            "invalid iqamah offset"
        }
        dataStore.edit { values ->
            val key = iqamahOffsetKey(prayerId)
            if (offsetMinutes == null) values.remove(key) else values[key] = offsetMinutes
        }
    }

    override suspend fun setIqamahConfiguration(configuration: OperatorIqamahConfiguration) {
        require(isValidIqamahConfiguration(configuration)) { "invalid iqamah configuration" }
        dataStore.edit { values ->
            OPERATOR_IQAMAH_OFFSET_PRAYER_IDS.forEach { prayerId ->
                val key = iqamahOffsetKey(prayerId)
                val offset = configuration.offsetForPrayer(prayerId)
                if (offset == null) values.remove(key) else values[key] = offset
            }
            configuration.dhuhrFixedTimeMinutes?.let { values[DHUHR_FIXED_TIME_MINUTES] = it }
                ?: values.remove(DHUHR_FIXED_TIME_MINUTES)
            values.remove(LEGACY_IQAMAH_OFFSET_DHUHR)
        }
    }

    override suspend fun setDonationConfiguration(configuration: OperatorDonationConfiguration) {
        require(isValidDonationConfiguration(configuration)) { "invalid donation configuration" }
        dataStore.edit { values ->
            values[DONATION_HTTPS_URL] = configuration.httpsUrl.trim()
            values[DONATION_RECIPIENT] = configuration.recipient.trim()
            values[DONATION_BANK] = configuration.bank.trim()
            values[DONATION_CARD_NUMBER] = configuration.cardNumber.trim()
            values[DONATION_PHONE] = configuration.phone.trim()
            values.remove(TOMBSTONED_DONATION_COLLECTION_URL)
            val gratitudeMessage = configuration.gratitudeMessage.trim()
            if (gratitudeMessage.isEmpty()) values.remove(DONATION_GRATITUDE_MESSAGE)
            else values[DONATION_GRATITUDE_MESSAGE] = gratitudeMessage
            values.remove(LEGACY_DONATION_TRANSFER_DETAILS)
            values.remove(LEGACY_DONATION_MESSAGE)
            values[DONATION_IMAGE_STYLE_ID] = configuration.imageStyleId
            if (configuration.isEmpty) values[DISPLAY_MODE] = OperatorDisplayMode.SCHEDULE.id
        }
    }

    override suspend fun setDonationImageStyleId(styleId: String) {
        require(styleId in SELECTABLE_DONATION_IMAGE_STYLE_IDS) {
            "unsupported donation image style"
        }
        dataStore.edit { values -> values[DONATION_IMAGE_STYLE_ID] = styleId }
    }

    override suspend fun setDisplayMode(mode: OperatorDisplayMode) {
        dataStore.edit { values ->
            if (mode == OperatorDisplayMode.DONATION) {
                val configuration = values.toDonationConfiguration()
                require(isValidDonationConfiguration(configuration) && !configuration.isEmpty) {
                    "donation display is not configured"
                }
            }
            values[DISPLAY_MODE] = mode.id
        }
    }

    private companion object {
        val LAST_SETTINGS_DESTINATION = stringPreferencesKey("last_settings_destination")
        val SHOW_IQAMAH_ON_SCHEDULE = booleanPreferencesKey("show_iqamah_on_schedule")
        val SCHEDULE_BLOCK_TRANSPARENCY = intPreferencesKey("schedule_block_transparency")
        val SCHEDULE_LAYOUT_MODE = stringPreferencesKey("schedule_layout_mode")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val LANGUAGE_TAG = stringPreferencesKey("language_tag")
        val SCREEN_RETENTION_SHIFT_ENABLED = booleanPreferencesKey(
            "screen_retention_shift_enabled",
        )
        val MOSQUE_DISPLAY_NAME = stringPreferencesKey("operator_mosque_display_name")
        val MOSQUE_DISPLAY_ADDRESS = stringPreferencesKey("operator_mosque_display_address")
        val BACKGROUND_STYLE_ID = stringPreferencesKey("background_style_id")
        val QR_HTTPS_URL = stringPreferencesKey("operator_qr_https_url")
        val QR_TITLE = stringPreferencesKey("operator_qr_title")
        val QR_MESSAGE = stringPreferencesKey("operator_qr_message")
        val IQAMAH_OFFSET_FAJR = intPreferencesKey("operator_iqamah_offset_fajr")
        val LEGACY_IQAMAH_OFFSET_DHUHR = intPreferencesKey("operator_iqamah_offset_dhuhr")
        val DHUHR_FIXED_TIME_MINUTES = intPreferencesKey("operator_dhuhr_fixed_time_minutes")
        val IQAMAH_OFFSET_ASR = intPreferencesKey("operator_iqamah_offset_asr")
        val IQAMAH_OFFSET_MAGHRIB = intPreferencesKey("operator_iqamah_offset_maghrib")
        val IQAMAH_OFFSET_ISHA = intPreferencesKey("operator_iqamah_offset_isha")
        val DISPLAY_MODE = stringPreferencesKey("operator_display_mode")
        val DONATION_HTTPS_URL = stringPreferencesKey("operator_donation_https_url")
        val DONATION_RECIPIENT = stringPreferencesKey("operator_donation_recipient")
        val DONATION_BANK = stringPreferencesKey("operator_donation_bank")
        val DONATION_CARD_NUMBER = stringPreferencesKey("operator_donation_card_number")
        val DONATION_PHONE = stringPreferencesKey("operator_donation_phone")
        val TOMBSTONED_DONATION_COLLECTION_URL =
            stringPreferencesKey("operator_donation_collection_url")
        val DONATION_GRATITUDE_MESSAGE =
            stringPreferencesKey("operator_donation_gratitude_message")
        val LEGACY_DONATION_TRANSFER_DETAILS =
            stringPreferencesKey("operator_donation_transfer_details")
        val LEGACY_DONATION_MESSAGE = stringPreferencesKey("operator_donation_message")
        val DONATION_IMAGE_STYLE_ID = stringPreferencesKey("operator_donation_image_style_id")

        fun iqamahOffsetKey(prayerId: String) = when (prayerId) {
            "fajr" -> IQAMAH_OFFSET_FAJR
            "asr" -> IQAMAH_OFFSET_ASR
            "maghrib" -> IQAMAH_OFFSET_MAGHRIB
            "isha" -> IQAMAH_OFFSET_ISHA
            else -> throw IllegalArgumentException("unsupported iqamah prayer")
        }

        fun Preferences.toDonationConfiguration(): OperatorDonationConfiguration {
            val persistedDetails = OperatorDonationDetails(
                recipient = this[DONATION_RECIPIENT].orEmpty(),
                bank = this[DONATION_BANK].orEmpty(),
                cardNumber = this[DONATION_CARD_NUMBER].orEmpty(),
                phone = this[DONATION_PHONE].orEmpty(),
            )
            val details = if (persistedDetails.hasAnyValue) {
                persistedDetails
            } else {
                parseLegacyDonationDetails(this[LEGACY_DONATION_TRANSFER_DETAILS].orEmpty())
            }
            return OperatorDonationConfiguration(
                httpsUrl = this[DONATION_HTTPS_URL].orEmpty(),
                recipient = details.recipient,
                bank = details.bank,
                cardNumber = details.cardNumber,
                phone = details.phone,
                gratitudeMessage = this[DONATION_GRATITUDE_MESSAGE].orEmpty(),
                imageStyleId = this[DONATION_IMAGE_STYLE_ID]
                    ?.takeIf(SELECTABLE_DONATION_IMAGE_STYLE_IDS::contains)
                    ?: DEFAULT_DONATION_IMAGE_STYLE_ID,
            )
        }
    }
}

private data class OperatorDonationDetails(
    val recipient: String = "",
    val bank: String = "",
    val cardNumber: String = "",
    val phone: String = "",
) {
    val hasAnyValue: Boolean
        get() = listOf(recipient, bank, cardNumber, phone).any(String::isNotBlank)
}

private fun parseLegacyDonationDetails(raw: String): OperatorDonationDetails {
    val value = raw.trim()
    if (value.isEmpty()) return OperatorDonationDetails()
    val parsed = mutableMapOf<String, String>()
    var recognizedLabel = false
    value.lineSequence().map(String::trim).filter(String::isNotEmpty).forEach { line ->
        val activeLabel = LEGACY_DONATION_LABELS.firstOrNull { (labels, _) ->
            labels.any { label -> line.startsWith("$label:", ignoreCase = true) }
        }
        if (activeLabel != null) {
            val (labels, field) = activeLabel
            recognizedLabel = true
            val label = labels.first { line.startsWith("$it:", ignoreCase = true) }
            parsed[field] = line.substring(label.length + 1).trim()
        } else if (
            LEGACY_IGNORED_DONATION_LABELS.any { label ->
                line.startsWith("$label:", ignoreCase = true)
            }
        ) {
            recognizedLabel = true
        }
    }
    if (!recognizedLabel) return OperatorDonationDetails(recipient = value)
    return OperatorDonationDetails(
        recipient = parsed["recipient"].orEmpty(),
        bank = parsed["bank"].orEmpty(),
        cardNumber = parsed["cardNumber"].orEmpty(),
        phone = parsed["phone"].orEmpty(),
    )
}

private val LEGACY_DONATION_LABELS = listOf(
    setOf("Получатель", "Recipient") to "recipient",
    setOf("Банк", "Bank") to "bank",
    setOf("Номер карты", "Card number", "Card") to "cardNumber",
    setOf("СБП / Телефон", "СБП/Телефон", "SBP / Phone", "Phone") to "phone",
)

private val LEGACY_IGNORED_DONATION_LABELS =
    setOf("Ссылка на сбор", "Collection link", "Fundraiser link")

internal fun OperatorQrConfiguration.toCampaignInput() = CampaignInput(
    id = "operator-local-qr",
    kind = "donation",
    httpsUrl = httpsUrl.trim().ifEmpty { DEFAULT_QR_HTTPS_URL },
    title = title.trim(),
    subtitle = message.trim().takeIf(String::isNotEmpty),
    startsAt = "2000-01-01T00:00:00Z",
    endsAt = "9999-12-31T23:59:59Z",
    placement = "with_prayer_times",
)

internal fun isValidQrConfiguration(configuration: OperatorQrConfiguration): Boolean {
    if (configuration.isEmpty) return true
    if (configuration.message.isNotBlank() &&
        OperatorQrTextFitPolicy.fit(configuration.message) == null
    ) return false
    val campaign = CampaignEngine().preview(configuration.toCampaignInput())
    if (campaign !is CampaignPreview.Valid) return false
    return runCatching { OPERATOR_QR_GENERATOR.generate(campaign.campaign.httpsUrl) }.isSuccess
}

private fun safePersistedQrConfiguration(
    configuration: OperatorQrConfiguration,
): OperatorQrConfiguration {
    if (isValidQrConfiguration(configuration)) return configuration
    if (configuration.message.isNotBlank() &&
        OperatorQrTextFitPolicy.fit(configuration.message) == null
    ) {
        return configuration.copy(message = "")
            .takeIf(::isValidQrConfiguration)
            ?: OperatorQrConfiguration()
    }
    return OperatorQrConfiguration()
}

internal fun OperatorDonationConfiguration.toDonationCampaignInput() = CampaignInput(
    id = "operator-local-donation-screen",
    kind = "donation",
    httpsUrl = httpsUrl.trim().ifEmpty { DEFAULT_QR_HTTPS_URL },
    title = recipient.trim().ifEmpty { "donation" },
    subtitle = null,
    startsAt = "2000-01-01T00:00:00Z",
    endsAt = "9999-12-31T23:59:59Z",
    placement = "always",
)

internal fun isValidDonationConfiguration(configuration: OperatorDonationConfiguration): Boolean {
    if (configuration.imageStyleId !in SELECTABLE_DONATION_IMAGE_STYLE_IDS) return false
    if (!isValidDonationGratitude(configuration.gratitudeMessage)) return false
    if (configuration.isEmpty) return true
    if (!configuration.hasAnyTransferDetail ||
        configuration.recipient.length > MAX_DONATION_DETAIL_LENGTH ||
        configuration.bank.length > MAX_DONATION_DETAIL_LENGTH ||
        configuration.cardNumber.length > MAX_DONATION_DETAIL_LENGTH ||
        configuration.phone.length > MAX_DONATION_DETAIL_LENGTH
    ) return false
    val campaign = CampaignEngine().preview(configuration.toDonationCampaignInput())
    if (campaign !is CampaignPreview.Valid) return false
    return runCatching { OPERATOR_QR_GENERATOR.generate(campaign.campaign.httpsUrl) }.isSuccess
}

private fun isValidDonationGratitude(value: String): Boolean {
    val trimmed = value.trim()
    return trimmed.codePointCount(0, trimmed.length) <= MAX_DONATION_GRATITUDE_LENGTH &&
        value.none(Char::isISOControl)
}

internal fun isValidIqamahConfiguration(configuration: OperatorIqamahConfiguration): Boolean =
    OPERATOR_IQAMAH_OFFSET_PRAYER_IDS.all { prayerId ->
        configuration.offsetForPrayer(prayerId)
            ?.let(OPERATOR_IQAMAH_OFFSET_RANGE::contains) ?: true
    } && configuration.dhuhrFixedTimeMinutes
        ?.let(OPERATOR_DHUHR_FIXED_TIME_RANGE::contains) != false

internal fun isValidMosquePresentationIdentity(
    identity: OperatorMosquePresentationIdentity,
): Boolean = isValidDisplayIdentityField(identity.displayName, MAX_MOSQUE_DISPLAY_NAME_LENGTH) &&
    isValidDisplayIdentityField(identity.displayAddress, MAX_MOSQUE_DISPLAY_ADDRESS_LENGTH)

private fun isValidDisplayIdentityField(value: String, maximumCodePoints: Int): Boolean {
    val trimmed = value.trim()
    return trimmed.codePointCount(0, trimmed.length) <= maximumCodePoints &&
        value.none(Char::isISOControl)
}

private val OPERATOR_QR_GENERATOR = QrCodeGenerator()

private fun Int?.validIqamahOffsetOrNull(): Int? =
    this?.takeIf(OPERATOR_IQAMAH_OFFSET_RANGE::contains)

private fun Int?.validDhuhrFixedTimeOrNull(): Int? =
    this?.takeIf(OPERATOR_DHUHR_FIXED_TIME_RANGE::contains)

const val DEFAULT_LANGUAGE_TAG = "ru"
val SUPPORTED_LANGUAGE_TAGS = setOf(DEFAULT_LANGUAGE_TAG, "en")
const val DEFAULT_BACKGROUND_STYLE_ID = "golden_dusk"
const val LUMINOUS_DUSK_BACKGROUND_STYLE_ID = "luminous_dusk"
const val BLUE_HOUR_BACKGROUND_STYLE_ID = "blue_hour"
const val CUSTOM_BACKGROUND_STYLE_ID = "custom"
const val NIGHT_MINARET_BACKGROUND_STYLE_ID = "night_minaret"
const val DESERT_DAWN_BACKGROUND_STYLE_ID = "desert_dawn"
const val EMERALD_MOSQUE_BACKGROUND_STYLE_ID = "emerald_mosque"
const val WINTER_TWILIGHT_BACKGROUND_STYLE_ID = "winter_twilight"
const val AUTUMN_COURTYARD_BACKGROUND_STYLE_ID = "autumn_courtyard"
const val CELESTIAL_NAVY_BACKGROUND_STYLE_ID = "celestial_navy"
const val WAL_5_BACKGROUND_STYLE_ID = "wal_5"
val BUILT_IN_BACKGROUND_STYLE_IDS = setOf(
    DEFAULT_BACKGROUND_STYLE_ID,
    BLUE_HOUR_BACKGROUND_STYLE_ID,
    NIGHT_MINARET_BACKGROUND_STYLE_ID,
    DESERT_DAWN_BACKGROUND_STYLE_ID,
    EMERALD_MOSQUE_BACKGROUND_STYLE_ID,
    WINTER_TWILIGHT_BACKGROUND_STYLE_ID,
    AUTUMN_COURTYARD_BACKGROUND_STYLE_ID,
    CELESTIAL_NAVY_BACKGROUND_STYLE_ID,
    LUMINOUS_DUSK_BACKGROUND_STYLE_ID,
    WAL_5_BACKGROUND_STYLE_ID,
)
val SELECTABLE_BACKGROUND_STYLE_IDS = BUILT_IN_BACKGROUND_STYLE_IDS + CUSTOM_BACKGROUND_STYLE_ID

const val DEFAULT_DONATION_IMAGE_STYLE_ID = "donation_mosque"
const val DONATION_IMAGE_COURTYARD_STYLE_ID = "donation_courtyard"
const val DONATION_IMAGE_LANTERN_STYLE_ID = "donation_lantern"
const val DONATION_IMAGE_CRESCENT_STYLE_ID = "donation_crescent"
const val DONATION_IMAGE_COMMUNITY_STYLE_ID = "donation_community"
const val CUSTOM_DONATION_IMAGE_STYLE_ID = "donation_custom"
val BUILT_IN_DONATION_IMAGE_STYLE_IDS = setOf(
    DEFAULT_DONATION_IMAGE_STYLE_ID,
    DONATION_IMAGE_COURTYARD_STYLE_ID,
    DONATION_IMAGE_LANTERN_STYLE_ID,
    DONATION_IMAGE_CRESCENT_STYLE_ID,
    DONATION_IMAGE_COMMUNITY_STYLE_ID,
)
val SELECTABLE_DONATION_IMAGE_STYLE_IDS =
    BUILT_IN_DONATION_IMAGE_STYLE_IDS + CUSTOM_DONATION_IMAGE_STYLE_ID

val OPERATOR_IQAMAH_PRAYER_IDS = listOf("fajr", "dhuhr", "asr", "maghrib", "isha")
val OPERATOR_IQAMAH_OFFSET_PRAYER_IDS = listOf("fajr", "asr", "maghrib", "isha")
val OPERATOR_IQAMAH_OFFSET_RANGE = 0..180
val OPERATOR_DHUHR_FIXED_TIME_RANGE = (12 * 60)..(16 * 60)
const val MAX_MOSQUE_DISPLAY_NAME_LENGTH = 80
const val MAX_MOSQUE_DISPLAY_ADDRESS_LENGTH = 160
const val MAX_DONATION_DETAIL_LENGTH = 160
const val MAX_DONATION_GRATITUDE_LENGTH = 240
