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

/**
 * 相对时间：刚刚 / N 分钟前 / N 小时前 / N 天前，超过一周就退回具体日期。
 *
 * 收藏列表用相对时间更好扫 —— 「3 天前摘的」比「10-11 14:30」更容易
 * 唤起「那阵子在读什么」的记忆。
 *
 * [now] 可注入，便于测试；正常调用用默认值即可。
 */
fun formatRelativeTime(millis: Long, now: Long = System.currentTimeMillis()): String {
    val diff = now - millis
    return when {
        diff < 0L -> formatDateTime(millis)
        diff < 60_000L -> "刚刚"
        diff < 3_600_000L -> "${diff / 60_000L} 分钟前"
        diff < 86_400_000L -> "${diff / 3_600_000L} 小时前"
        diff < 7L * 86_400_000L -> "${diff / 86_400_000L} 天前"
        else -> formatDateTime(millis)
    }
}

fun formatCharCount(chars: Long): String = when {
    chars <= 0L -> "未解析"
    chars < 10_000L -> "$chars 字"
    else -> "${(chars / 10_000f).roundToInt()} 万字"
}
