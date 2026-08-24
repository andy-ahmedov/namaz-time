package com.example.namaztime.tv.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class OperatorImageSlot(val fileName: String) {
    BACKGROUND("operator_background.jpg"),
    DONATION("operator_donation.jpg"),
}

data class OperatorImageDocument(
    val declaredMimeType: String,
    val bytes: ByteArray,
)

sealed interface OperatorImageImportResult {
    data object Imported : OperatorImageImportResult
    data object Inaccessible : OperatorImageImportResult
    data object TooLarge : OperatorImageImportResult
    data object UnsupportedType : OperatorImageImportResult
    data object InvalidDimensions : OperatorImageImportResult
    data object DecodeFailed : OperatorImageImportResult
    data object StorageFailed : OperatorImageImportResult
}

class OperatorImageAssetStore(private val filesDir: File) {
    private val assetDirectory = File(filesDir, OPERATOR_IMAGE_DIRECTORY)

    fun importDocument(
        slot: OperatorImageSlot,
        document: OperatorImageDocument,
    ): OperatorImageImportResult {
        if (document.bytes.size > MAX_OPERATOR_IMAGE_BYTES) {
            return OperatorImageImportResult.TooLarge
        }
        if (document.declaredMimeType.lowercase() !in ALLOWED_OPERATOR_IMAGE_MIME_TYPES) {
            return OperatorImageImportResult.UnsupportedType
        }
        val bounds = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(document.bytes, 0, document.bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outMimeType == null) {
            return OperatorImageImportResult.DecodeFailed
        }
        if (bounds.outMimeType.lowercase() !in ALLOWED_OPERATOR_IMAGE_MIME_TYPES) {
            return OperatorImageImportResult.UnsupportedType
        }
        if (!validDimensions(bounds.outWidth, bounds.outHeight)) {
            return OperatorImageImportResult.InvalidDimensions
        }
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        val bitmap = BitmapFactory.decodeByteArray(
            document.bytes,
            0,
            document.bytes.size,
            decodeOptions,
        ) ?: return OperatorImageImportResult.DecodeFailed
        return try {
            persist(slot, bitmap)
            OperatorImageImportResult.Imported
        } catch (_: IOException) {
            OperatorImageImportResult.StorageFailed
        } finally {
            bitmap.recycle()
        }
    }

    fun resolve(slot: OperatorImageSlot): File? {
        val candidate = File(assetDirectory, slot.fileName)
        if (!candidate.isFile) return null
        val bounds = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
        BitmapFactory.decodeFile(candidate.path, bounds)
        return candidate.takeIf {
            bounds.outMimeType?.lowercase() in ALLOWED_STORED_IMAGE_MIME_TYPES &&
                validDimensions(bounds.outWidth, bounds.outHeight)
        }
    }

    private fun persist(slot: OperatorImageSlot, bitmap: Bitmap) {
        if (!assetDirectory.exists() && !assetDirectory.mkdirs()) {
            throw IOException("operator image directory unavailable")
        }
        val target = File(assetDirectory, slot.fileName)
        val temporary = File(assetDirectory, ".${slot.fileName}.tmp")
        FileOutputStream(temporary).use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) {
                throw IOException("operator image encode failed")
            }
            output.fd.sync()
        }
        try {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }
}

class AndroidOperatorImageAssetImporter(
    context: Context,
    private val store: OperatorImageAssetStore = OperatorImageAssetStore(context.filesDir),
) {
    private val applicationContext = context.applicationContext

    suspend fun import(slot: OperatorImageSlot, uri: Uri): OperatorImageImportResult =
        withContext(Dispatchers.IO) {
            val resolver = applicationContext.contentResolver
            val declaredType = try {
                resolver.getType(uri)?.lowercase()
            } catch (_: SecurityException) {
                return@withContext OperatorImageImportResult.Inaccessible
            } ?: return@withContext OperatorImageImportResult.UnsupportedType
            if (declaredType !in ALLOWED_OPERATOR_IMAGE_MIME_TYPES) {
                return@withContext OperatorImageImportResult.UnsupportedType
            }
            val knownLength = runCatching {
                resolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
            }.getOrNull()
            if (knownLength != null && knownLength > MAX_OPERATOR_IMAGE_BYTES) {
                return@withContext OperatorImageImportResult.TooLarge
            }
            val boundedRead = try {
                resolver.openInputStream(uri)?.use(::readBounded)
                    ?: return@withContext OperatorImageImportResult.Inaccessible
            } catch (_: IOException) {
                return@withContext OperatorImageImportResult.Inaccessible
            } catch (_: SecurityException) {
                return@withContext OperatorImageImportResult.Inaccessible
            }
            when (boundedRead) {
                BoundedRead.TooLarge -> OperatorImageImportResult.TooLarge
                is BoundedRead.Success -> store.importDocument(
                    slot,
                    OperatorImageDocument(declaredType, boundedRead.bytes),
                )
            }
        }

    fun resolve(slot: OperatorImageSlot): File? = store.resolve(slot)
}

private sealed interface BoundedRead {
    data class Success(val bytes: ByteArray) : BoundedRead
    data object TooLarge : BoundedRead
}

private fun readBounded(input: java.io.InputStream): BoundedRead {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        total += read
        if (total > MAX_OPERATOR_IMAGE_BYTES) return BoundedRead.TooLarge
        output.write(buffer, 0, read)
    }
    return BoundedRead.Success(output.toByteArray())
}

private fun validDimensions(width: Int, height: Int): Boolean {
    if (width < MIN_OPERATOR_IMAGE_WIDTH || height < MIN_OPERATOR_IMAGE_HEIGHT) return false
    if (width > MAX_OPERATOR_IMAGE_DIMENSION || height > MAX_OPERATOR_IMAGE_DIMENSION) return false
    return width.toLong() * height.toLong() <= MAX_OPERATOR_IMAGE_PIXELS
}

private fun sampleSizeFor(width: Int, height: Int): Int {
    var sampleSize = 1
    while (
        (width / sampleSize > NORMALIZED_OPERATOR_IMAGE_MAX_DIMENSION ||
            height / sampleSize > NORMALIZED_OPERATOR_IMAGE_MAX_DIMENSION) &&
        width / (sampleSize * 2) >= MIN_OPERATOR_IMAGE_WIDTH &&
        height / (sampleSize * 2) >= MIN_OPERATOR_IMAGE_HEIGHT
    ) {
        sampleSize *= 2
    }
    return sampleSize
}

const val MAX_OPERATOR_IMAGE_BYTES = 10 * 1024 * 1024
private const val MIN_OPERATOR_IMAGE_WIDTH = 640
private const val MIN_OPERATOR_IMAGE_HEIGHT = 360
private const val MAX_OPERATOR_IMAGE_DIMENSION = 8_192
private const val MAX_OPERATOR_IMAGE_PIXELS = 33_177_600L
private const val NORMALIZED_OPERATOR_IMAGE_MAX_DIMENSION = 3_840
private const val OPERATOR_IMAGE_DIRECTORY = "operator_images"
private val ALLOWED_OPERATOR_IMAGE_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")
private val ALLOWED_STORED_IMAGE_MIME_TYPES = setOf("image/jpeg")
