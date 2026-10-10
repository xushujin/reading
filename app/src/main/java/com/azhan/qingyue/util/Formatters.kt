package com.azhan.qingyue.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

private val DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault())

fun formatPercent(progress: Float): String =
    "${(progress.coerceIn(0f, 1f) * 100).roundToInt()}%"

fun formatClock(millis: Long): String =
    TIME_FORMATTER.format(Instant.ofEpochMilli(millis))

fun formatDateTime(millis: Long): String =
    DATE_FORMATTER.format(Instant.ofEpochMilli(millis))

fun formatCharCount(chars: Long): String = when {
    chars <= 0L -> "未解析"
    chars < 10_000L -> "$chars 字"
    else -> "${(chars / 10_000f).roundToInt()} 万字"
}
