package com.example.namaztime.tv.presentation

enum class SettingsDestination(
    val route: String,
    val title: String,
    val description: String,
) {
    MOSQUE(
        route = "mosque",
        title = "Mosque and location",
        description = "Mosque identity and timezone will be configured from approved local data.",
    ),
    SOURCE(
        route = "source",
        title = "Prayer source",
        description = "Source provenance is read-only on the display device.",
    ),
    IQAMAH(
        route = "iqamah",
        title = "Iqamah and Jumu'ah",
        description = "Adhan and mosque-local iqamah remain separate.",
    ),
    APPEARANCE(
        route = "appearance",
        title = "Appearance",
        description = "Built-in offline presentation settings will appear here.",
    ),
    CAMPAIGNS(
        route = "campaigns",
        title = "QR and announcements",
        description = "Preview locally stored campaign content before its active window.",
    ),
    LANGUAGE(
        route = "language",
        title = "Language",
        description = "Pilot languages are awaiting a product decision.",
    ),
    KIOSK(
        route = "kiosk",
        title = "Autostart and kiosk",
        description = "Consumer and managed-device modes will be configured separately.",
    ),
    DIAGNOSTICS(
        route = "diagnostics",
        title = "Diagnostics",
        description = "No prayer snapshot has been activated yet.",
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
