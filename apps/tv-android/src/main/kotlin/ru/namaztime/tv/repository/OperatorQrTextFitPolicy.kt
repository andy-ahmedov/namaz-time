package ru.namaztime.tv.repository

import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint

data class QrTextFit(
    val fontSizeSp: Int,
    val lineHeightSp: Int,
    val lineCount: Int,
)

/**
 * The narrowest supported public compact QR subtitle geometry, expressed in
 * density-independent reference pixels. Compose uses the returned typography.
 */
object OperatorQrTextFitPolicy {
    const val PREFERRED_FONT_SIZE_SP = 12
    const val MIN_READABLE_FONT_SIZE_SP = 10
    const val MAX_LINES = 6
    const val MAX_INPUT_CODE_POINTS = 256

    private const val TEXT_WIDTH_DP = 140
    private const val MAX_TEXT_HEIGHT_DP = 78

    private val candidates = listOf(
        QrTypography(PREFERRED_FONT_SIZE_SP, 15),
        QrTypography(11, 14),
        QrTypography(MIN_READABLE_FONT_SIZE_SP, 13),
    )

    fun fit(raw: String): QrTextFit? {
        val text = raw.trim()
        if (text.isEmpty()) return QrTextFit(PREFERRED_FONT_SIZE_SP, 15, 0)
        if (text.codePointCount(0, text.length) > MAX_INPUT_CODE_POINTS) return null
        if (text.any { it.isISOControl() && it != '\n' }) return null
        return candidates.firstNotNullOfOrNull { typography ->
            val lineCount = maxOf(
                measuredLineCount(text, typography),
                conservativeLineCount(text, typography),
            )
            QrTextFit(
                fontSizeSp = typography.fontSizeSp,
                lineHeightSp = typography.lineHeightSp,
                lineCount = lineCount,
            ).takeIf {
                lineCount <= MAX_LINES &&
                    lineCount * typography.lineHeightSp <= MAX_TEXT_HEIGHT_DP
            }
        }
    }

    private fun measuredLineCount(text: String, typography: QrTypography): Int {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = typography.fontSizeSp.toFloat()
        }
        var lines = 0
        text.split('\n').forEach { paragraph ->
            if (paragraph.isEmpty()) {
                lines += 1
                return@forEach
            }
            var start = 0
            while (start < paragraph.length) {
                val fittingCharacters = paint.breakText(
                    paragraph,
                    start,
                    paragraph.length,
                    true,
                    TEXT_WIDTH_DP.toFloat(),
                    null,
                ).coerceAtLeast(1)
                val measuredEnd = (start + fittingCharacters).coerceAtMost(paragraph.length)
                val nextStart = if (measuredEnd == paragraph.length) {
                    measuredEnd
                } else {
                    val whitespace = (measuredEnd - 1 downTo start)
                        .firstOrNull { paragraph[it].isWhitespace() }
                    if (whitespace == null) measuredEnd else whitespace + 1
                }
                lines += 1
                if (lines > MAX_LINES) return lines
                start = nextStart
                while (start < paragraph.length && paragraph[start].isWhitespace()) start += 1
            }
        }
        return lines
    }

    /**
     * Robolectric and some vendor font stacks can report incomplete Paint metrics before a
     * concrete view is attached. Keep a conservative density-independent lower bound so that
     * persistence validation can never become more permissive than the actual Compose layout.
     * The rendered panel additionally verifies [androidx.compose.ui.text.TextLayoutResult].
     */
    private fun conservativeLineCount(text: String, typography: QrTypography): Int {
        var lines = 1
        var usedWidth = 0f
        text.forEach { character ->
            if (character == '\n') {
                lines += 1
                usedWidth = 0f
                return@forEach
            }
            val glyphWidth = typography.fontSizeSp * when {
                character.isWhitespace() -> 0.35f
                character.isUpperCase() -> 0.72f
                character.isDigit() -> 0.60f
                character.isLetter() -> 0.62f
                else -> 0.42f
            }
            if (usedWidth > 0f && usedWidth + glyphWidth > TEXT_WIDTH_DP) {
                lines += 1
                usedWidth = glyphWidth
            } else {
                usedWidth += glyphWidth
            }
        }
        return lines
    }

    private data class QrTypography(val fontSizeSp: Int, val lineHeightSp: Int)
}
