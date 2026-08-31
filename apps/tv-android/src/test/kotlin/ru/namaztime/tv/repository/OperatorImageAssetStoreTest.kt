package ru.namaztime.tv.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import ru.namaztime.tv.R
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OperatorImageAssetStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun validLandscapeImageIsNormalizedToStableAppLocalCopy() {
        val store = OperatorImageAssetStore(temporaryFolder.root)

        val result = store.importDocument(
            OperatorImageSlot.BACKGROUND,
            OperatorImageDocument("image/webp", validLandscapeImage()),
        )

        assertEquals(OperatorImageImportResult.Imported, result)
        val stored = store.resolve(OperatorImageSlot.BACKGROUND)
        assertNotNull(stored)
        assertTrue(stored!!.canonicalPath.startsWith(temporaryFolder.root.canonicalPath))
        val bounds = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
        BitmapFactory.decodeFile(stored.path, bounds)
        assertTrue(bounds.outWidth >= 640)
        assertTrue(bounds.outHeight >= 360)
    }

    @Test
    fun jpegPngAndWebpInputsUseTheSameBoundedNormalizationPath() {
        val formats = listOf(
            "image/jpeg" to Bitmap.CompressFormat.JPEG,
            "image/png" to Bitmap.CompressFormat.PNG,
            "image/webp" to Bitmap.CompressFormat.WEBP_LOSSY,
        )

        formats.forEach { (mimeType, format) ->
            val slot = if (mimeType == "image/jpeg") {
                OperatorImageSlot.BACKGROUND
            } else {
                OperatorImageSlot.DONATION
            }
            val result = OperatorImageAssetStore(temporaryFolder.root).importDocument(
                slot,
                OperatorImageDocument(mimeType, encodedLandscapeImage(format)),
            )

            assertEquals(mimeType, OperatorImageImportResult.Imported, result)
        }
    }

    @Test
    fun donationPhotoUsesAnIndependentStableAppLocalSlot() {
        val store = OperatorImageAssetStore(temporaryFolder.root)

        val result = store.importDocument(
            OperatorImageSlot.DONATION,
            OperatorImageDocument("image/webp", validLandscapeImage()),
        )

        assertEquals(OperatorImageImportResult.Imported, result)
        assertNotNull(store.resolve(OperatorImageSlot.DONATION))
        assertNull(store.resolve(OperatorImageSlot.BACKGROUND))
    }

    @Test
    fun declaredNonImageTypeIsRejectedWithoutReplacingLastKnownGood() {
        val store = OperatorImageAssetStore(temporaryFolder.root)
        store.importDocument(
            OperatorImageSlot.BACKGROUND,
            OperatorImageDocument("image/webp", validLandscapeImage()),
        )
        val previous = store.resolve(OperatorImageSlot.BACKGROUND)!!.readBytes()

        val result = store.importDocument(
            OperatorImageSlot.BACKGROUND,
            OperatorImageDocument("text/plain", validLandscapeImage()),
        )

        assertEquals(OperatorImageImportResult.UnsupportedType, result)
        assertTrue(previous.contentEquals(store.resolve(OperatorImageSlot.BACKGROUND)!!.readBytes()))
    }

    @Test
    fun corruptAndUndersizedImagesFailClosed() {
        val store = OperatorImageAssetStore(temporaryFolder.root)

        assertEquals(
            OperatorImageImportResult.DecodeFailed,
            store.importDocument(
                OperatorImageSlot.BACKGROUND,
                OperatorImageDocument("image/png", byteArrayOf(1, 2, 3)),
            ),
        )
        assertEquals(
            OperatorImageImportResult.InvalidDimensions,
            store.importDocument(
                OperatorImageSlot.BACKGROUND,
                OperatorImageDocument("image/png", onePixelPng()),
            ),
        )
        assertNull(store.resolve(OperatorImageSlot.BACKGROUND))
    }

    @Test
    fun oversizedDocumentIsRejectedBeforeDecode() {
        val store = OperatorImageAssetStore(temporaryFolder.root)

        val result = store.importDocument(
            OperatorImageSlot.BACKGROUND,
            OperatorImageDocument(
                "image/png",
                ByteArray(MAX_OPERATOR_IMAGE_BYTES + 1),
            ),
        )

        assertEquals(OperatorImageImportResult.TooLarge, result)
    }

    @Test
    fun persistenceFailureHasAStableResultAndDoesNotLeakAnException() {
        val unusableFilesDir = File(temporaryFolder.root, "not-a-directory").apply {
            writeText("occupied")
        }
        val store = OperatorImageAssetStore(unusableFilesDir)

        val result = store.importDocument(
            OperatorImageSlot.BACKGROUND,
            OperatorImageDocument("image/webp", validLandscapeImage()),
        )

        assertEquals(OperatorImageImportResult.StorageFailed, result)
        assertNull(store.resolve(OperatorImageSlot.BACKGROUND))
    }

    private fun validLandscapeImage(): ByteArray {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return context.resources.openRawResource(R.drawable.tv_background_golden_dusk).use {
            it.readBytes()
        }
    }

    private fun encodedLandscapeImage(format: Bitmap.CompressFormat): ByteArray {
        val bytes = validLandscapeImage()
        val source = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
        )
        return ByteArrayOutputStream().use { output ->
            check(source.compress(format, 90, output))
            source.recycle()
            output.toByteArray()
        }
    }

    private fun onePixelPng(): ByteArray = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
    )
}
