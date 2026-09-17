package ru.namaztime.tv.presentation

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import ru.namaztime.tv.repository.OperatorMediaImage
import ru.namaztime.tv.repository.OperatorMediaImagePickerContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
class MediaStoreImagePickerScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun bucketsAndEveryLoadedImageAreVisibleAndDpadSelectableWithoutATopNCap() {
        val images = (1..8).map { index ->
            image(index, if (index <= 5) "Mosque" else "Donation")
        }
        var selected: OperatorMediaImage? = null
        compose.setContent {
            NamazTvTheme {
                MediaStoreImagePickerScreen(
                    content = OperatorMediaImagePickerContent(images, nextOffset = null),
                    loading = false,
                    onSelect = { selected = it },
                    onLoadMore = {},
                    onCancel = {},
                )
            }
        }

        compose.onNode(isDialog()).assertIsDisplayed()
        compose.onNodeWithTag(MEDIA_IMAGE_PICKER_TAG).assertIsDisplayed()
        compose.onNodeWithText("Mosque").assertIsDisplayed()
        compose.onNodeWithText("Donation").assertIsDisplayed()
        images.forEach { item ->
            compose.onNodeWithTag("$MEDIA_IMAGE_PICKER_ITEM_TAG_PREFIX${item.id}")
                .assertIsDisplayed()
        }
        compose.onNodeWithTag("${MEDIA_IMAGE_PICKER_ITEM_TAG_PREFIX}1")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("${MEDIA_IMAGE_PICKER_ITEM_TAG_PREFIX}2")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }

        assertEquals("2", selected?.id)
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun explicitLoadMoreAndCancelRemainSeparateActions() {
        var loadMoreCount = 0
        var cancelCount = 0
        compose.setContent {
            NamazTvTheme {
                MediaStoreImagePickerScreen(
                    content = OperatorMediaImagePickerContent(
                        items = listOf(image(1, "Mosque")),
                        nextOffset = 32,
                    ),
                    loading = false,
                    onSelect = {},
                    onLoadMore = { loadMoreCount += 1 },
                    onCancel = { cancelCount += 1 },
                )
            }
        }

        compose.onNodeWithTag("${MEDIA_IMAGE_PICKER_ITEM_TAG_PREFIX}1")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag(MEDIA_IMAGE_PICKER_LOAD_MORE_TAG)
            .assertIsFocused()
            .assertIsDisplayed()
            .performKeyInput {
                pressKey(Key.Enter)
                pressKey(Key.DirectionRight)
            }
        compose.onNodeWithTag(MEDIA_IMAGE_PICKER_CANCEL_TAG)
            .assertIsFocused()
            .assertIsDisplayed()
            .performKeyInput { pressKey(Key.Enter) }

        assertEquals(1, loadMoreCount)
        assertEquals(1, cancelCount)
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun longBucketKeepsDpadFocusAcrossOffscreenImages() {
        val images = (1..12).map { image(it, "Mosque") }
        compose.setContent {
            NamazTvTheme {
                MediaStoreImagePickerScreen(
                    content = OperatorMediaImagePickerContent(images, nextOffset = 32),
                    loading = false,
                    onSelect = {},
                    onLoadMore = {},
                    onCancel = {},
                )
            }
        }

        compose.onNodeWithTag("${MEDIA_IMAGE_PICKER_ITEM_TAG_PREFIX}1")
            .assertIsFocused()
            .performKeyInput { repeat(11) { pressKey(Key.DirectionRight) } }
        compose.onNodeWithTag("${MEDIA_IMAGE_PICKER_ITEM_TAG_PREFIX}12")
            .assertIsDisplayed()
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag(MEDIA_IMAGE_PICKER_LOAD_MORE_TAG)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }
        assertTrue(images.any { image ->
            runCatching {
                compose.onNodeWithTag("$MEDIA_IMAGE_PICKER_ITEM_TAG_PREFIX${image.id}")
                    .assertIsFocused()
            }.isSuccess
        })
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun manyBucketsKeepDpadFocusAcrossOffscreenRows() {
        val images = (1..8).map { image(it, "Bucket $it") }
        compose.setContent {
            NamazTvTheme {
                MediaStoreImagePickerScreen(
                    content = OperatorMediaImagePickerContent(images, nextOffset = null),
                    loading = false,
                    onSelect = {},
                    onLoadMore = {},
                    onCancel = {},
                )
            }
        }

        compose.onNodeWithTag("${MEDIA_IMAGE_PICKER_ITEM_TAG_PREFIX}1")
            .assertIsFocused()
            .performKeyInput { repeat(7) { pressKey(Key.DirectionDown) } }
        compose.onNodeWithTag("${MEDIA_IMAGE_PICKER_ITEM_TAG_PREFIX}8")
            .assertIsDisplayed()
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag(MEDIA_IMAGE_PICKER_CANCEL_TAG)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        assertTrue(images.any { image ->
            runCatching {
                compose.onNodeWithTag("$MEDIA_IMAGE_PICKER_ITEM_TAG_PREFIX${image.id}")
                    .assertIsFocused()
            }.isSuccess
        })
    }

    private fun image(id: Int, bucket: String) = OperatorMediaImage(
        id = id.toString(),
        contentUri = "content://synthetic/images/$id",
        displayName = "image-$id.jpg",
        bucketId = bucket.lowercase(),
        bucketName = bucket,
    )
}
