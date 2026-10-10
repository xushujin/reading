package com.azhan.qingyue.data.parser

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * TXT 编码探测。
 *
 * 中文电子书绝大多数是 UTF-8 或 GB18030/GBK。做法是：
 * 先看 BOM；没有 BOM 就用「严格模式」尝试 UTF-8 解码，
 * 出现非法字节序列才退回 GB18030 —— 比引入 juniversalchardet 轻得多，
 * 对中文书源的准确率足够。
 */
internal object TextDecoding {

    private val GB18030: Charset? by lazy {
        runCatching { Charset.forName("GB18030") }.getOrNull()
    }

    fun decode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""

        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }

        val strictUtf8 = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)

        return try {
            strictUtf8.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            val gb18030 = GB18030
            if (gb18030 != null) {
                String(bytes, gb18030)
            } else {
                String(bytes, Charsets.ISO_8859_1)
            }
        }
    }
}

/** EPUB 内部 XHTML 转纯文本。 */
internal object HtmlText {

    private val SCRIPT_STYLE = Regex("(?is)<(script|style)\\b[^>]*>.*?</\\1\\s*>")
    private val LINE_BREAK = Regex("(?i)<br\\s*/?\\s*>")
    private val BLOCK_END = Regex(
        "(?i)</(p|div|h[1-6]|li|tr|section|article|blockquote|dd|dt|pre|figure|td)\\s*>"
    )
    private val TAG = Regex("<[^>]*>")
    private val ENTITY = Regex("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);")

    private val NAMED_ENTITIES = mapOf(
        "nbsp" to " ", "ensp" to " ", "emsp" to " ",
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "mdash" to "—", "ndash" to "–", "hellip" to "…", "middot" to "·",
        "ldquo" to "“", "rdquo" to "”", "lsquo" to "‘", "rsquo" to "’",
        "laquo" to "«", "raquo" to "»", "copy" to "©", "reg" to "®",
        "trade" to "™", "times" to "×", "divide" to "÷", "deg" to "°",
        "bull" to "•", "dagger" to "†", "sect" to "§", "para" to "¶",
    )

    fun toPlainText(html: String): String {
        var text = SCRIPT_STYLE.replace(html) { "" }
        text = LINE_BREAK.replace(text) { "\n" }
        text = BLOCK_END.replace(text) { "\n" }
        text = TAG.replace(text) { "" }
        return unescape(text)
    }

    fun unescape(input: String): String = ENTITY.replace(input) { match ->
        val body = match.groupValues[1]
        when {
            body.startsWith("#x", ignoreCase = true) ->
                codePointOrNull(body.substring(2), 16) ?: match.value

            body.startsWith("#") ->
                codePointOrNull(body.substring(1), 10) ?: match.value

            else -> NAMED_ENTITIES[body.lowercase()] ?: match.value
        }
    }

    private fun codePointOrNull(raw: String, radix: Int): String? {
        val code = raw.toIntOrNull(radix) ?: return null
        if (code <= 0 || code > 0x10FFFF) return null
        return runCatching { String(Character.toChars(code)) }.getOrNull()
    }
}

/** 章节标题识别，只用于 TXT —— EPUB 的章节来自 spine，不需要猜。 */
internal object ChapterRules {

    private val CHAPTER_TITLE = Regex(
        "^\\s*(?:" +
            "第\\s*[0-9零一二三四五六七八九十百千万两]{1,8}\\s*[章节回卷篇集幕部折]" +
            "|Chapter\\s+[0-9IVXLCivxlc]+" +
            "|序章|序言|前言|引子|楔子|后记|尾声|终章|尾章|番外|附录|外传|正文" +
            ")\\s*[^\\n]{0,40}$",
        RegexOption.IGNORE_CASE,
    )

    fun isChapterTitle(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.length > 50) return false
        return CHAPTER_TITLE.matches(trimmed)
    }

    fun sniffTitle(text: String, fallback: String): String {
        val head = text.take(300)
        Regex("《([^》]{1,40})》").find(head)?.let { match ->
            val title = match.groupValues[1].trim()
            if (title.isNotEmpty()) return title
        }
        return fallback
    }

    fun sniffAuthor(text: String): String? {
        val head = text.take(300)
        val match = Regex("作\\s*者\\s*[:：]\\s*([^\\s，,。；;\\n]{1,20})").find(head)
            ?: Regex("(?:^|\\n)\\s*([^\\s，,。；;\\n]{1,12})\\s*著").find(head)
        return match?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }
}
