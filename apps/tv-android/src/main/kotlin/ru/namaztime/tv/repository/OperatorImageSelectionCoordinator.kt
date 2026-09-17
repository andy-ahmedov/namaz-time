package ru.namaztime.tv.repository

data class OperatorImageSelectionCapabilities(
    val sdkInt: Int,
    val television: Boolean = false,
    val openDocumentResolvable: Boolean,
    val photoPickerAvailable: Boolean,
    val mediaReadPermissionGranted: Boolean,
)

enum class OperatorImageReadPermission {
    LEGACY_MEDIA_IMAGES,
    MEDIA_IMAGES,
}

sealed interface OperatorImageSelectionDecision {
    data object OpenDocument : OperatorImageSelectionDecision
    data object PhotoPicker : OperatorImageSelectionDecision
    data object MediaStore : OperatorImageSelectionDecision
    data class RequestPermission(
        val permission: OperatorImageReadPermission,
    ) : OperatorImageSelectionDecision
}

enum class OperatorImageSelectionFeedback {
    IMPORTED,
    INACCESSIBLE,
    TOO_LARGE,
    UNSUPPORTED_TYPE,
    INVALID_DIMENSIONS,
    DECODE_FAILED,
    STORAGE_FAILED,
    PERMISSION_DENIED,
}

class OperatorImageSelectionCoordinator {
    fun decide(capabilities: OperatorImageSelectionCapabilities): OperatorImageSelectionDecision =
        when {
            capabilities.television -> if (capabilities.mediaReadPermissionGranted) {
                OperatorImageSelectionDecision.MediaStore
            } else {
                OperatorImageSelectionDecision.RequestPermission(permissionForSdk(capabilities.sdkInt))
            }
            capabilities.openDocumentResolvable -> OperatorImageSelectionDecision.OpenDocument
            capabilities.photoPickerAvailable -> OperatorImageSelectionDecision.PhotoPicker
            capabilities.mediaReadPermissionGranted -> OperatorImageSelectionDecision.MediaStore
            else -> OperatorImageSelectionDecision.RequestPermission(
                permissionForSdk(capabilities.sdkInt),
            )
        }

    fun permissionResultFeedback(granted: Boolean): OperatorImageSelectionFeedback? =
        if (granted) null else OperatorImageSelectionFeedback.PERMISSION_DENIED

    fun importResultFeedback(
        result: OperatorImageImportResult?,
    ): OperatorImageSelectionFeedback? = when (result) {
        null -> null
        OperatorImageImportResult.Imported -> OperatorImageSelectionFeedback.IMPORTED
        OperatorImageImportResult.Inaccessible -> OperatorImageSelectionFeedback.INACCESSIBLE
        OperatorImageImportResult.TooLarge -> OperatorImageSelectionFeedback.TOO_LARGE
        OperatorImageImportResult.UnsupportedType -> OperatorImageSelectionFeedback.UNSUPPORTED_TYPE
        OperatorImageImportResult.InvalidDimensions -> OperatorImageSelectionFeedback.INVALID_DIMENSIONS
        OperatorImageImportResult.DecodeFailed -> OperatorImageSelectionFeedback.DECODE_FAILED
        OperatorImageImportResult.StorageFailed -> OperatorImageSelectionFeedback.STORAGE_FAILED
    }

    private fun permissionForSdk(sdkInt: Int): OperatorImageReadPermission =
        if (sdkInt >= 33) {
            OperatorImageReadPermission.MEDIA_IMAGES
        } else {
            OperatorImageReadPermission.LEGACY_MEDIA_IMAGES
        }
}
