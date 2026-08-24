package com.example.namaztime.tv.presentation

import androidx.annotation.StringRes
import com.example.namaztime.tv.R

enum class SettingsDestination(
    val route: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
) {
    MOSQUE(
        route = "mosque",
        titleRes = R.string.settings_mosque_title,
        descriptionRes = R.string.settings_mosque_description,
    ),
    SOURCE(
        route = "source",
        titleRes = R.string.settings_source_title,
        descriptionRes = R.string.settings_source_description,
    ),
    IQAMAH(
        route = "iqamah",
        titleRes = R.string.settings_iqamah_title,
        descriptionRes = R.string.settings_iqamah_description,
    ),
    APPEARANCE(
        route = "appearance",
        titleRes = R.string.settings_appearance_title,
        descriptionRes = R.string.settings_appearance_description,
    ),
    CAMPAIGNS(
        route = "campaigns",
        titleRes = R.string.settings_campaigns_title,
        descriptionRes = R.string.settings_campaigns_description,
    ),
    DONATION(
        route = "donation",
        titleRes = R.string.settings_donation_title,
        descriptionRes = R.string.settings_donation_description,
    ),
    LANGUAGE(
        route = "language",
        titleRes = R.string.settings_language_title,
        descriptionRes = R.string.settings_language_description,
    ),
    KIOSK(
        route = "kiosk",
        titleRes = R.string.settings_kiosk_title,
        descriptionRes = R.string.settings_kiosk_description,
    ),
    DIAGNOSTICS(
        route = "diagnostics",
        titleRes = R.string.settings_diagnostics_title,
        descriptionRes = R.string.settings_diagnostics_description,
    ),
    ;

    val navigationTestTag: String
        get() = "settings-navigation-$route"

    val previous: SettingsDestination?
        get() = entries.getOrNull(ordinal - 1)

    val next: SettingsDestination?
        get() = entries.getOrNull(ordinal + 1)

    companion object {
        val initial: SettingsDestination = MOSQUE

        fun fromRoute(route: String?): SettingsDestination =
            entries.firstOrNull { it.route == route } ?: initial
    }
}
