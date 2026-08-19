package com.example.namaztime.tv.data.snapshot

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Base64

internal data class ParsedRfc3339(
    val epochSecond: Long,
    val leapSecond: Boolean,
    val fractionalDigits: String,
) : Comparable<ParsedRfc3339> {
    override fun compareTo(other: ParsedRfc3339): Int {
        val secondsComparison = epochSecond.compareTo(other.epochSecond)
        if (secondsComparison != 0) return secondsComparison
        val leapComparison = leapSecond.compareTo(other.leapSecond)
        if (leapComparison != 0) return leapComparison
        val width = maxOf(fractionalDigits.length, other.fractionalDigits.length)
        return fractionalDigits.padEnd(width, '0').compareTo(
            other.fractionalDigits.padEnd(width, '0'),
        )
    }
}

internal object SnapshotFormatValidation {
    private val rfc3339Pattern = Regex(
        "^(\\d{4})-(\\d{2})-(\\d{2})[Tt](\\d{2}):(\\d{2}):(\\d{2})(?:\\.(\\d+))?(?:([Zz])|([+-])(\\d{2}):(\\d{2}))$",
    )
    private val sha256Pattern = Regex("^[a-f0-9]{64}$")

    fun decodeUtf8(bytes: ByteArray): String = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()

    fun parseRfc3339(value: String): ParsedRfc3339? {
        val match = rfc3339Pattern.matchEntire(value) ?: return null
        val seconds = match.groupValues[6].toInt()
        if (seconds > 60) return null
        val local = try {
            LocalDateTime.of(
                match.groupValues[1].toInt(),
                match.groupValues[2].toInt(),
                match.groupValues[3].toInt(),
                match.groupValues[4].toInt(),
                match.groupValues[5].toInt(),
                minOf(seconds, 59),
            )
        } catch (_: DateTimeException) {
            return null
        }
        val offsetHours = match.groupValues[10].ifEmpty { "0" }.toInt()
        val offsetMinutes = match.groupValues[11].ifEmpty { "0" }.toInt()
        if (offsetHours !in 0..23 || offsetMinutes !in 0..59) return null
        val offsetDirection = if (match.groupValues[9] == "-") -1 else 1
        val offsetSeconds = offsetDirection * (offsetHours * 3600L + offsetMinutes * 60L)
        val baseEpochSecond = local.toEpochSecond(ZoneOffset.UTC) - offsetSeconds
        if (seconds == 60) {
            val utc = Instant.ofEpochSecond(baseEpochSecond).atOffset(ZoneOffset.UTC)
            if (utc.hour != 23 || utc.minute != 59) return null
        }
        return ParsedRfc3339(
            epochSecond = baseEpochSecond,
            leapSecond = seconds == 60,
            fractionalDigits = match.groupValues[7].trimEnd('0'),
        )
    }

    fun isNamedIanaTimezone(value: String): Boolean =
        value != "Local" && value in ZoneId.getAvailableZoneIds()

    fun isSha256(value: String): Boolean = sha256Pattern.matches(value)

    fun codePointLength(value: String): Int = value.codePointCount(0, value.length)

    fun isEd25519SignatureEncoding(value: String): Boolean {
        if (value.length !in 80..120) return false
        return try {
            Base64.getDecoder().decode(value).size == 64
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}
