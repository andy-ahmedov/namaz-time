package com.example.namaztime.tv.repository

import android.graphics.BitmapFactory
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.namaztime.tv.R
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

    private fun validLandscapeImage(): ByteArray {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return context.resources.openRawResource(R.drawable.tv_background_golden_dusk).use {
            it.readBytes()
        }
    }

    private fun onePixelPng(): ByteArray = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
    )
}
