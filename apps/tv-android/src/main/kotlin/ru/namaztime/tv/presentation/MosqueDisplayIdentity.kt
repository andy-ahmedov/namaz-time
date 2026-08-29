package ru.namaztime.tv.presentation

import ru.namaztime.tv.repository.LocalPrayerSchedule

internal data class MosqueDisplayIdentity(
    val name: String,
    val locality: String?,
)

internal fun LocalPrayerSchedule.toMosqueDisplayIdentity(): MosqueDisplayIdentity =
    if (mosqueId == SECOND_CATHEDRAL_MOSQUE_ULYANOVSK_ID) {
        MosqueDisplayIdentity(
            name = "Вторая Соборная Мечеть",
            locality = "Ульяновск",
        )
    } else {
        MosqueDisplayIdentity(
            name = mosqueName,
            locality = locality,
        )
    }

private const val SECOND_CATHEDRAL_MOSQUE_ULYANOVSK_ID =
    "second-cathedral-mosque-ulyanovsk"
