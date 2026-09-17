package ru.namaztime.tv.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlin.math.roundToInt
import ru.namaztime.tv.R

internal val LocalScheduleBlockTransparency = staticCompositionLocalOf<Int?> { null }

@Composable
internal fun Color.scheduleBlockColor(): Color =
    LocalScheduleBlockTransparency.current?.let { copy(alpha = scheduleBlockAlpha(it)) } ?: this

internal fun scheduleBlockAlpha(percent: Int): Float = 1f - percent.coerceIn(0, 100) / 100f

internal const val SCHEDULE_BLOCK_TRANSPARENCY_TAG = "schedule-block-transparency"

@Composable
internal fun ScheduleBlockTransparencySlider(
    percent: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = NamazTvTheme.colors
    val localValue = remember { mutableIntStateOf(percent.coerceIn(0, 100)) }
    val value = localValue.intValue
    LaunchedEffect(percent, focused) {
        if (!focused) localValue.intValue = percent.coerceIn(0, 100)
    }
    fun update(requested: Int) {
        localValue.intValue = requested.coerceIn(0, 100)
        onChange(localValue.intValue)
    }
    Column(
        modifier.fillMaxWidth()
            .testTag(SCHEDULE_BLOCK_TRANSPARENCY_TAG)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.key != Key.DirectionLeft && event.key != Key.DirectionRight) {
                    false
                } else {
                    if (event.type == KeyEventType.KeyDown) {
                        update(localValue.intValue + if (event.key == Key.DirectionRight) 5 else -5)
                    }
                    true
                }
            }
            .semantics(mergeDescendants = true) {
                progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), 0f..100f, 19)
                setProgress { requested ->
                    if (!requested.isFinite()) false else {
                        update((requested / 5).roundToInt() * 5)
                        true
                    }
                }
            }
            .focusable()
            .border(1.dp, if (focused) colors.accent else Color.Transparent, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            text = appString(R.string.schedule_block_transparency, value),
            color = if (focused) colors.accent else colors.textPrimary,
            fontSize = 15.sp,
        )
        Canvas(Modifier.fillMaxWidth().height(18.dp)) {
            val inset = 6.dp.toPx()
            val start = Offset(inset, size.height / 2)
            val end = Offset(size.width - inset, size.height / 2)
            val thumb = Offset(start.x + (end.x - start.x) * value / 100f, start.y)
            drawLine(colors.surfaceOutline, start, end, 3.dp.toPx(), StrokeCap.Round)
            drawLine(colors.accent, start, thumb, 3.dp.toPx(), StrokeCap.Round)
            drawCircle(colors.accent, 5.dp.toPx(), thumb)
        }
    }
}
