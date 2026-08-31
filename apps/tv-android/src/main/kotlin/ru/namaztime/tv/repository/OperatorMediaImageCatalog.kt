package ru.namaztime.tv.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.os.Bundle
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OperatorMediaImage(
    val id: String,
    val contentUri: String,
    val displayName: String,
    val bucketId: String,
    val bucketName: String,
)

data class OperatorMediaImagePage(
    val items: List<OperatorMediaImage>,
    val nextOffset: Int?,
)

data class OperatorMediaImagePickerContent(
    val items: List<OperatorMediaImage> = emptyList(),
    val nextOffset: Int? = 0,
)

data class OperatorMediaImageBucket(
    val bucketId: String,
    val bucketName: String,
    val images: List<OperatorMediaImage>,
)

interface OperatorMediaImageCatalog {
    suspend fun loadPage(offset: Int, limit: Int): OperatorMediaImagePage
}

class OperatorMediaImagePager(
    private val catalog: OperatorMediaImageCatalog,
    private val pageSize: Int = DEFAULT_MEDIA_IMAGE_PAGE_SIZE,
) {
    init {
        require(pageSize in 1..MAX_MEDIA_IMAGE_PAGE_SIZE)
    }

    suspend fun loadNext(
        content: OperatorMediaImagePickerContent = OperatorMediaImagePickerContent(),
    ): OperatorMediaImagePickerContent {
        val offset = content.nextOffset ?: return content
        val page = catalog.loadPage(offset, pageSize)
        return OperatorMediaImagePickerContent(
            items = content.items + page.items,
            nextOffset = page.nextOffset,
        )
    }
}

fun OperatorMediaImagePickerContent.groupedByBucket(): List<OperatorMediaImageBucket> {
    val grouped = linkedMapOf<String, Pair<String, MutableList<OperatorMediaImage>>>()
    items.forEach { image ->
        val group = grouped.getOrPut(image.bucketId) {
            image.bucketName to mutableListOf()
        }
        group.second += image
    }
    return grouped.map { (bucketId, group) ->
        OperatorMediaImageBucket(bucketId, group.first, group.second.toList())
    }
}

class AndroidOperatorMediaImageCatalog(context: Context) : OperatorMediaImageCatalog {
    private val resolver = context.applicationContext.contentResolver

    override suspend fun loadPage(offset: Int, limit: Int): OperatorMediaImagePage =
        withContext(Dispatchers.IO) {
            require(offset >= 0)
            require(limit in 1..MAX_MEDIA_IMAGE_PAGE_SIZE)
            val requested = limit + 1
            val cursor = queryPage(offset, requested)
            cursor.use {
                val items = buildList {
                    while (it.moveToNext() && size < requested) add(it.toOperatorMediaImage())
                }
                OperatorMediaImagePage(
                    items = items.take(limit),
                    nextOffset = (offset + limit).takeIf { items.size > limit },
                )
            }
        }

    private fun queryPage(offset: Int, limit: Int): Cursor {
        val queryArgs = Bundle().apply {
            putStringArray(
                ContentResolver.QUERY_ARG_SORT_COLUMNS,
                arrayOf(MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media._ID),
            )
            putInt(
                ContentResolver.QUERY_ARG_SORT_DIRECTION,
                ContentResolver.QUERY_SORT_DIRECTION_DESCENDING,
            )
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
        }
        return resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            PROJECTION,
            queryArgs,
            null,
        ) ?: error("MediaStore images query returned no cursor")
    }

    private fun Cursor.toOperatorMediaImage(): OperatorMediaImage {
        val id = getLong(getColumnIndexOrThrow(MediaStore.Images.Media._ID))
        val displayName = getString(
            getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME),
        ).orEmpty().ifBlank { "image-$id" }
        val bucketName = getString(
            getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME),
        ).orEmpty().ifBlank { "Images" }
        val bucketId = getString(
            getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID),
        ).orEmpty().ifBlank { "bucket:$bucketName" }
        return OperatorMediaImage(
            id = id.toString(),
            contentUri = ContentUris.withAppendedId(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                id,
            ).toString(),
            displayName = displayName,
            bucketId = bucketId,
            bucketName = bucketName,
        )
    }

    private companion object {
        val PROJECTION = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        )
    }
}

const val DEFAULT_MEDIA_IMAGE_PAGE_SIZE = 32
const val MAX_MEDIA_IMAGE_PAGE_SIZE = 100
