package ru.namaztime.tv.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OperatorMediaImagePagerTest {
    @Test
    fun explicitPagesExposeEveryImageWithoutTopNTruncation() = runTest {
        val all = (1..70).map { index -> image(index, "bucket-${index % 3}") }
        val pager = OperatorMediaImagePager(
            catalog = FakeCatalog(all),
            pageSize = 32,
        )

        val first = pager.loadNext()
        val second = pager.loadNext(first)
        val complete = pager.loadNext(second)

        assertEquals(32, first.items.size)
        assertEquals(64, second.items.size)
        assertEquals(all, complete.items)
        assertNull(complete.nextOffset)
        assertEquals(complete, pager.loadNext(complete))
    }

    @Test
    fun bucketProjectionPreservesCatalogOrderAndDoesNotDropSameNamedImages() = runTest {
        val images = listOf(
            image(1, "mosque", displayName = "photo.jpg"),
            image(2, "mosque", displayName = "photo.jpg"),
            image(3, "donation"),
            image(4, "mosque"),
        )
        val content = OperatorMediaImagePickerContent(items = images, nextOffset = null)

        val grouped = content.groupedByBucket()

        assertEquals(listOf("mosque", "donation"), grouped.map { it.bucketId })
        assertEquals(listOf("1", "2", "4"), grouped.first().images.map { it.id })
        assertEquals(4, grouped.sumOf { it.images.size })
    }

    private fun image(
        id: Int,
        bucket: String,
        displayName: String = "image-$id.jpg",
    ) = OperatorMediaImage(
        id = id.toString(),
        contentUri = "content://media/external/images/media/$id",
        displayName = displayName,
        bucketId = bucket,
        bucketName = bucket,
    )
}

private class FakeCatalog(
    private val images: List<OperatorMediaImage>,
) : OperatorMediaImageCatalog {
    override suspend fun loadPage(offset: Int, limit: Int): OperatorMediaImagePage {
        val items = images.drop(offset).take(limit)
        val nextOffset = (offset + items.size).takeIf { it < images.size }
        return OperatorMediaImagePage(items, nextOffset)
    }
}
