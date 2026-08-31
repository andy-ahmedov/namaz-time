package ru.namaztime.tv.presentation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import ru.namaztime.tv.R
import ru.namaztime.tv.repository.OperatorImageSelectionFeedback

const val OPERATOR_IMAGE_FEEDBACK_TAG = "operator-image-feedback"

@Composable
internal fun OperatorImageFeedbackBanner(feedback: OperatorImageSelectionFeedback) {
    TvGlassPanel(
        modifier = Modifier.widthIn(max = 620.dp).testTag(OPERATOR_IMAGE_FEEDBACK_TAG),
        radius = 14.dp,
    ) {
        Text(
            text = appString(feedback.messageRes),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            color = if (feedback == OperatorImageSelectionFeedback.IMPORTED) {
                NamazTvTheme.colors.accent
            } else {
                NamazTvTheme.colors.warning
            },
            fontSize = 17.sp,
        )
    }
}

@get:StringRes
private val OperatorImageSelectionFeedback.messageRes: Int
    get() = when (this) {
        OperatorImageSelectionFeedback.IMPORTED -> R.string.image_import_success
        OperatorImageSelectionFeedback.INACCESSIBLE -> R.string.image_import_inaccessible
        OperatorImageSelectionFeedback.TOO_LARGE -> R.string.image_import_too_large
        OperatorImageSelectionFeedback.UNSUPPORTED_TYPE -> R.string.image_import_unsupported
        OperatorImageSelectionFeedback.INVALID_DIMENSIONS -> R.string.image_import_invalid_dimensions
        OperatorImageSelectionFeedback.DECODE_FAILED -> R.string.image_import_decode_failed
        OperatorImageSelectionFeedback.STORAGE_FAILED -> R.string.image_import_storage_failed
        OperatorImageSelectionFeedback.PERMISSION_DENIED -> R.string.image_import_permission_denied
    }
