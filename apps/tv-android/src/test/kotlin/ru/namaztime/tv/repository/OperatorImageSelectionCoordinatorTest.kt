package ru.namaztime.tv.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OperatorImageSelectionCoordinatorTest {
    private val coordinator = OperatorImageSelectionCoordinator()

    @Test
    fun televisionUsesLocalBrowserEvenWhenFirmwareAdvertisesSystemPickers() {
        listOf(28, 32, 33, 36).forEach { sdk ->
            val available = OperatorImageSelectionCapabilities(
                sdkInt = sdk,
                television = true,
                openDocumentResolvable = true,
                photoPickerAvailable = true,
                mediaReadPermissionGranted = true,
            )
            assertEquals(OperatorImageSelectionDecision.MediaStore, coordinator.decide(available))
            assertEquals(
                OperatorImageSelectionDecision.RequestPermission(
                    if (sdk >= 33) OperatorImageReadPermission.MEDIA_IMAGES
                    else OperatorImageReadPermission.LEGACY_MEDIA_IMAGES,
                ),
                coordinator.decide(available.copy(mediaReadPermissionGranted = false)),
            )
        }
    }

    @Test
    fun openDocumentWinsWhenItsIntentIsActuallyResolvable() {
        val decision = coordinator.decide(
            capabilities(openDocument = true, photoPicker = true, permissionGranted = true),
        )

        assertEquals(OperatorImageSelectionDecision.OpenDocument, decision)
    }

    @Test
    fun photoPickerIsUsedWhenDocumentProviderIsAbsent() {
        val decision = coordinator.decide(
            capabilities(openDocument = false, photoPicker = true, permissionGranted = false),
        )

        assertEquals(OperatorImageSelectionDecision.PhotoPicker, decision)
    }

    @Test
    fun api28To32FallbackRequestsOnlyLegacyImageReadPermission() {
        listOf(28, 32).forEach { sdk ->
            val decision = coordinator.decide(
                capabilities(
                    sdk = sdk,
                    openDocument = false,
                    photoPicker = false,
                    permissionGranted = false,
                ),
            )

            assertEquals(
                OperatorImageSelectionDecision.RequestPermission(
                    OperatorImageReadPermission.LEGACY_MEDIA_IMAGES,
                ),
                decision,
            )
        }
    }

    @Test
    fun api33And35FallbackRequestsOnlyImageSpecificPermission() {
        listOf(33, 35).forEach { sdk ->
            val decision = coordinator.decide(
                capabilities(
                    sdk = sdk,
                    openDocument = false,
                    photoPicker = false,
                    permissionGranted = false,
                ),
            )

            assertEquals(
                OperatorImageSelectionDecision.RequestPermission(
                    OperatorImageReadPermission.MEDIA_IMAGES,
                ),
                decision,
            )
        }
    }

    @Test
    fun mediaStoreOpensOnlyAfterTheRequiredPermissionIsGranted() {
        val decision = coordinator.decide(
            capabilities(
                openDocument = false,
                photoPicker = false,
                permissionGranted = true,
            ),
        )

        assertEquals(OperatorImageSelectionDecision.MediaStore, decision)
    }

    @Test
    fun revokedPermissionReturnsToExplicitRequestInsteadOfQueryingMediaStore() {
        val previouslyGranted = coordinator.decide(
            capabilities(
                openDocument = false,
                photoPicker = false,
                permissionGranted = true,
            ),
        )
        val afterRevocation = coordinator.decide(
            capabilities(
                openDocument = false,
                photoPicker = false,
                permissionGranted = false,
            ),
        )

        assertEquals(OperatorImageSelectionDecision.MediaStore, previouslyGranted)
        assertEquals(
            OperatorImageSelectionDecision.RequestPermission(
                OperatorImageReadPermission.MEDIA_IMAGES,
            ),
            afterRevocation,
        )
    }

    @Test
    fun permissionDenialHasBoundedFeedbackAndCancelHasNone() {
        assertEquals(
            OperatorImageSelectionFeedback.PERMISSION_DENIED,
            coordinator.permissionResultFeedback(granted = false),
        )
        assertNull(coordinator.permissionResultFeedback(granted = true))
        assertNull(coordinator.importResultFeedback(null))
    }

    @Test
    fun everyImporterResultMapsToOneStableLocalizedFeedbackKind() {
        val expected = mapOf(
            OperatorImageImportResult.Imported to OperatorImageSelectionFeedback.IMPORTED,
            OperatorImageImportResult.Inaccessible to OperatorImageSelectionFeedback.INACCESSIBLE,
            OperatorImageImportResult.TooLarge to OperatorImageSelectionFeedback.TOO_LARGE,
            OperatorImageImportResult.UnsupportedType to
                OperatorImageSelectionFeedback.UNSUPPORTED_TYPE,
            OperatorImageImportResult.InvalidDimensions to
                OperatorImageSelectionFeedback.INVALID_DIMENSIONS,
            OperatorImageImportResult.DecodeFailed to OperatorImageSelectionFeedback.DECODE_FAILED,
            OperatorImageImportResult.StorageFailed to
                OperatorImageSelectionFeedback.STORAGE_FAILED,
        )

        expected.forEach { (result, feedback) ->
            assertEquals(feedback, coordinator.importResultFeedback(result))
        }
    }

    private fun capabilities(
        sdk: Int = 35,
        openDocument: Boolean,
        photoPicker: Boolean,
        permissionGranted: Boolean,
    ) = OperatorImageSelectionCapabilities(
        sdkInt = sdk,
        openDocumentResolvable = openDocument,
        photoPickerAvailable = photoPicker,
        mediaReadPermissionGranted = permissionGranted,
    )
}
