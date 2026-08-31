package ru.namaztime.tv.presentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.namaztime.tv.R
import ru.namaztime.tv.repository.OperatorMediaImage
import ru.namaztime.tv.repository.OperatorMediaImagePickerContent
import ru.namaztime.tv.repository.groupedByBucket

const val MEDIA_IMAGE_PICKER_TAG = "media-image-picker"
const val MEDIA_IMAGE_PICKER_ITEM_TAG_PREFIX = "media-image-picker-item-"
const val MEDIA_IMAGE_PICKER_LOAD_MORE_TAG = "media-image-picker-load-more"
const val MEDIA_IMAGE_PICKER_CANCEL_TAG = "media-image-picker-cancel"

@Composable
internal fun MediaStoreImagePickerScreen(
    content: OperatorMediaImagePickerContent,
    loading: Boolean,
    onSelect: (OperatorMediaImage) -> Unit,
    onLoadMore: () -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    val buckets = remember(content.items) { content.groupedByBucket() }
    val itemRequesters = remember(content.items.map(OperatorMediaImage::id)) {
        content.items.associate { it.id to FocusRequester() }
    }
    val cancelRequester = remember { FocusRequester() }
    val loadMoreRequester = remember { FocusRequester() }
    val firstItemRequester = buckets.firstOrNull()?.images?.firstOrNull()?.let { itemRequesters[it.id] }
    val lastItemRequester = buckets.lastOrNull()?.images?.firstOrNull()?.let { itemRequesters[it.id] }

    LaunchedEffect(firstItemRequester, loading) {
        withFrameNanos { }
        withFrameNanos { }
        runCatching { (firstItemRequester ?: cancelRequester).requestFocus() }
    }

    TvSafeFrame(testTag = MEDIA_IMAGE_PICKER_TAG) {
        TvGlassPanel(modifier = Modifier.fillMaxSize(), radius = 24.dp) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = appString(R.string.media_image_picker_title),
                        color = NamazTvTheme.colors.textPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Button(
                        onClick = onCancel,
                        colors = pickerButtonColors(),
                        modifier = Modifier
                            .testTag(MEDIA_IMAGE_PICKER_CANCEL_TAG)
                            .focusRequester(cancelRequester)
                            .focusProperties {
                                firstItemRequester?.let { down = it }
                                if (content.nextOffset != null) left = loadMoreRequester
                            },
                    ) {
                        Text(appString(R.string.action_cancel))
                    }
                }
                if (content.items.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = appString(
                                if (loading) {
                                    R.string.media_image_picker_loading
                                } else {
                                    R.string.media_image_picker_empty
                                },
                            ),
                            color = NamazTvTheme.colors.textSecondary,
                            fontSize = 20.sp,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(buckets, key = { it.bucketId }) { bucket ->
                            val bucketIndex = buckets.indexOfFirst { it.bucketId == bucket.bucketId }
                            Text(
                                text = bucket.bucketName,
                                color = NamazTvTheme.colors.textSecondary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().height(104.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 3.dp),
                            ) {
                                items(bucket.images, key = OperatorMediaImage::id) { image ->
                                    val itemIndex = bucket.images.indexOfFirst { it.id == image.id }
                                    val previousBucket = buckets.getOrNull(bucketIndex - 1)
                                    val nextBucket = buckets.getOrNull(bucketIndex + 1)
                                    Button(
                                        onClick = { onSelect(image) },
                                        contentPadding = PaddingValues(0.dp),
                                        colors = pickerButtonColors(),
                                        modifier = Modifier
                                            .width(152.dp)
                                            .height(98.dp)
                                            .testTag("$MEDIA_IMAGE_PICKER_ITEM_TAG_PREFIX${image.id}")
                                            .focusRequester(itemRequesters.getValue(image.id))
                                            .focusProperties {
                                                bucket.images.getOrNull(itemIndex - 1)?.let {
                                                    left = itemRequesters.getValue(it.id)
                                                }
                                                bucket.images.getOrNull(itemIndex + 1)?.let {
                                                    right = itemRequesters.getValue(it.id)
                                                }
                                                previousBucket?.images?.getOrNull(
                                                    itemIndex.coerceAtMost(previousBucket.images.lastIndex),
                                                )?.let { up = itemRequesters.getValue(it.id) }
                                                    ?: run { up = cancelRequester }
                                                nextBucket?.images?.getOrNull(
                                                    itemIndex.coerceAtMost(nextBucket.images.lastIndex),
                                                )?.let { down = itemRequesters.getValue(it.id) }
                                                    ?: if (content.nextOffset != null) {
                                                        run { down = loadMoreRequester }
                                                    } else {
                                                        run { down = cancelRequester }
                                                    }
                                            },
                                    ) {
                                        MediaImageTile(image)
                                    }
                                }
                            }
                        }
                    }
                }
                if (content.nextOffset != null && content.items.isNotEmpty()) {
                    Button(
                        onClick = onLoadMore,
                        enabled = !loading,
                        colors = pickerButtonColors(),
                        modifier = Modifier
                            .align(Alignment.End)
                            .testTag(MEDIA_IMAGE_PICKER_LOAD_MORE_TAG)
                            .focusRequester(loadMoreRequester)
                            .focusProperties {
                                lastItemRequester?.let { up = it }
                                right = cancelRequester
                            },
                    ) {
                        Text(
                            appString(
                                if (loading) {
                                    R.string.media_image_picker_loading
                                } else {
                                    R.string.action_load_more
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaImageTile(image: OperatorMediaImage) {
    val context = LocalContext.current
    val thumbnail by produceState<Bitmap?>(initialValue = null, image.contentUri) {
        value = withContext(Dispatchers.IO) {
            loadMediaThumbnail(context, Uri.parse(image.contentUri))
        }
    }
    Box(
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
            .background(NamazTvTheme.colors.surfaceStrong),
    ) {
        thumbnail?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = image.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            text = image.displayName,
            modifier = Modifier.align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(NamazTvTheme.colors.backgroundBottom.copy(alpha = 0.78f))
                .padding(horizontal = 6.dp, vertical = 3.dp),
            color = NamazTvTheme.colors.textPrimary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun loadMediaThumbnail(context: Context, uri: Uri): Bitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= 29) {
        context.contentResolver.loadThumbnail(uri, Size(304, 196), null)
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = thumbnailSampleSize(bounds.outWidth, bounds.outHeight)
        context.contentResolver.openInputStream(uri)?.use { input: InputStream ->
            BitmapFactory.decodeStream(
                input,
                null,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )
        }
    }
}.getOrNull()

private fun thumbnailSampleSize(width: Int, height: Int): Int {
    var sample = 1
    while (width / sample > 608 || height / sample > 392) sample *= 2
    return sample
}

@Composable
private fun pickerButtonColors() = ButtonDefaults.colors(
    containerColor = NamazTvTheme.colors.surfaceStrong.copy(alpha = 0.82f),
    contentColor = NamazTvTheme.colors.textPrimary,
    focusedContainerColor = NamazTvTheme.colors.accent,
    focusedContentColor = NamazTvTheme.colors.backgroundBottom,
)
