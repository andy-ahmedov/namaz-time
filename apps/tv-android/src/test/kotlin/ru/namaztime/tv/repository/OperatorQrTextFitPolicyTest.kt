package ru.namaztime.tv.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class OperatorQrTextFitPolicyTest {
    @Test
    fun ownerReportedRussianAndLongEnglishMessagesFitAtReadableTypography() {
        val russian = "Тем же из вас, которые уверовали и расходовали, уготована великая награда."
        val english = "Those who believe and give generously will receive a lasting and generous reward."

        listOf(russian, english).forEach { message ->
            val fit = requireNotNull(OperatorQrTextFitPolicy.fit(message))
            assertTrue(fit.fontSizeSp >= OperatorQrTextFitPolicy.MIN_READABLE_FONT_SIZE_SP)
            assertTrue(fit.lineCount <= OperatorQrTextFitPolicy.MAX_LINES)
        }
    }

    @Test
    fun shortTwoAndThreeLineMessagesUseTheLargestTypographyThatFits() {
        val short = requireNotNull(OperatorQrTextFitPolicy.fit("Помогите школе"))
        val twoLines = requireNotNull(
            OperatorQrTextFitPolicy.fit("Поддержите школу\nдля детей"),
        )
        val threeLines = requireNotNull(
            OperatorQrTextFitPolicy.fit("Вместе\nподдержим\nпроект"),
        )

        assertEquals(OperatorQrTextFitPolicy.PREFERRED_FONT_SIZE_SP, short.fontSizeSp)
        assertEquals(2, twoLines.lineCount)
        assertEquals(3, threeLines.lineCount)
    }

    @Test
    fun boundaryIsAcceptedAndFirstMeasuredOverflowIsRejected() {
        val firstRejectedWordCount = (1..OperatorQrTextFitPolicy.MAX_INPUT_CODE_POINTS).first { count ->
            OperatorQrTextFitPolicy.fit("Ж ".repeat(count).trim()) == null
        }

        assertNotNull(
            OperatorQrTextFitPolicy.fit("Ж ".repeat(firstRejectedWordCount - 1).trim()),
        )
        assertNull(OperatorQrTextFitPolicy.fit("Ж ".repeat(firstRejectedWordCount).trim()))
        assertTrue(firstRejectedWordCount * 2 < OperatorQrTextFitPolicy.MAX_INPUT_CODE_POINTS)
    }

    @Test
    fun controlCharactersAndExplicitExcessLinesAreRejected() {
        assertNull(OperatorQrTextFitPolicy.fit("Допустимо\u0000нет"))
        assertNull(
            OperatorQrTextFitPolicy.fit(
                (1..OperatorQrTextFitPolicy.MAX_LINES + 1).joinToString("\n") { "строка" },
            ),
        )
    }
}
