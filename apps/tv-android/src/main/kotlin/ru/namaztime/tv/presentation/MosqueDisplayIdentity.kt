package ru.namaztime.tv.presentation

import ru.namaztime.tv.repository.LocalPrayerSchedule
import ru.namaztime.tv.repository.OperatorMosquePresentationIdentity

internal data class MosqueDisplayIdentity(
    val name: String,
    val locality: String?,
)

internal fun LocalPrayerSchedule.toMosqueDisplayIdentity(
    localIdentity: OperatorMosquePresentationIdentity = OperatorMosquePresentationIdentity(),
): MosqueDisplayIdentity {
    val fallback = if (mosqueId == SECOND_CATHEDRAL_MOSQUE_ULYANOVSK_ID) {
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
    return MosqueDisplayIdentity(
        name = localIdentity.displayName.trim().ifEmpty { fallback.name },
        locality = localIdentity.displayAddress.trim().takeIf(String::isNotEmpty)
            ?: fallback.locality,
    )
}

private const val SECOND_CATHEDRAL_MOSQUE_ULYANOVSK_ID =
    "second-cathedral-mosque-ulyanovsk"
