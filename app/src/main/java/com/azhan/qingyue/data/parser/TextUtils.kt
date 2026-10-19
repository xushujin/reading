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

    private val NAMED_ENTITIES = mapOf(
        "nbsp" to " ", "ensp" to " ", "emsp" to " ",
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "mdash" to "—", "ndash" to "–", "hellip" to "…", "middot" to "·",
        "ldquo" to "“", "rdquo" to "”", "lsquo" to "‘", "rsquo" to "’",
        "laquo" to "«", "raquo" to "»", "copy" to "©", "reg" to "®",
        "trade" to "™", "times" to "×", "divide" to "÷", "deg" to "°",
        "bull" to "•", "dagger" to "†", "sect" to "§", "para" to "¶",
    )

    private val BLOCK_END_NAMES = setOf(
        "p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "li", "tr",
        "section", "article", "blockquote", "dd", "dt", "pre", "figure", "td",
    )

    /**
     * HTML 转纯文本，单遍扫描。
     *
     * 1.3.12 及以前是 5 个正则各过一遍全文 —— 对 1100 万字的书，等于把
     * 22MB 的字符串复制 4 次，再跑 5 遍正则，手机堆里 GC 风暴，转圈的时间
     * 大半耗在这里。改成一遍状态机：跳过 script/style、`<br>` 与块级闭合标签
     * 转 `\n`、其余标签丢弃、实体就地解码。语义与原来的正则链一致
     * （`HtmlTextTest` 里留着旧实现做逐字节对照）。
     */
    fun toPlainText(html: String): String {
        val n = html.length
        val out = StringBuilder(minOf(n, 8 * 1024 * 1024) + 16)
        var i = 0

        while (i < n) {
            val c = html[i]

            if (c == '<') {
                val close = html.indexOf('>', i)
                if (close == -1) {
                    // TAG = <[^>]*>，没有 '>' 就不是标签，按字面文本保留
                    out.append(c)
                    i++
                    continue
                }

                val name = tagName(html, i, close)
                when {
                    name == "script" || name == "style" -> {
                        // 内容连同闭合标签整体丢弃；找不到闭合则只丢开标签本身
                        // （原正则要求必须存在 </script>，否则整体不匹配）
                        val end = findClosingTagEnd(html, name, close + 1)
                        i = if (end != -1) end else close + 1
                    }

                    name == "br" && isLineBreak(html, i, close) -> {
                        out.append('\n')
                        i = close + 1
                    }

                    name.length > 1 && name[0] == '/' &&
                        name.substring(1) in BLOCK_END_NAMES &&
                        isBlockEnd(html, i, close) -> {
                        out.append('\n')
                        i = close + 1
                    }

                    else -> i = close + 1 // 其余标签一律丢弃
                }
                continue
            }

            if (c == '&') {
                val decoded = decodeEntityAt(html, i)
                if (decoded != null) {
                    out.append(decoded.first)
                    i = decoded.second
                    continue
                }
            }

            out.append(c)
            i++
        }

        return out.toString()
    }

    /** 标签名（小写）；闭合标签带 "/" 前缀，如 "/p"。 */
    private fun tagName(html: String, start: Int, close: Int): String {
        var p = start + 1
        var closing = false
        if (p < close && html[p] == '/') {
            p++
            closing = true
        }
        val nameStart = p
        while (p < close && (html[p].isLetterOrDigit())) p++
        val name = html.substring(nameStart, p).lowercase()
        return if (closing) "/$name" else name
    }

    /** `<br ...>` 的属性区必须只剩空白和至多一个斜杠（与原 LINE_BREAK 一致，不允许带其它属性）。 */
    private fun isLineBreak(html: String, start: Int, close: Int): Boolean {
        val inner = html.substring(start + 3, close) // "br" 之后、'>' 之前
        var p = 0
        while (p < inner.length && inner[p].isWhitespace()) p++
        if (p < inner.length && inner[p] == '/') p++
        while (p < inner.length && inner[p].isWhitespace()) p++
        return p == inner.length
    }

    /** `</p ...>` 的名字后必须只剩空白（与原 BLOCK_END 一致，`</p class=x>` 不算）。 */
    private fun isBlockEnd(html: String, start: Int, close: Int): Boolean {
        var p = start + 2 // 跳过 "</"
        while (p < close && html[p].isLetterOrDigit()) p++
        while (p < close && html[p].isWhitespace()) p++
        return p == close
    }

    /** 从 [from] 起找 `</name ...>`，返回 '>' 之后的位置；找不到返回 -1。 */
    private fun findClosingTagEnd(html: String, name: String, from: Int): Int {
        val closeTag = "</$name"
        var idx = indexOfIgnoreCase(html, closeTag, from)
        while (idx != -1) {
            var p = idx + closeTag.length
            while (p < html.length && html[p].isWhitespace()) p++
            if (p < html.length && html[p] == '>') return p + 1
            idx = indexOfIgnoreCase(html, closeTag, idx + 1)
        }
        return -1
    }

    private fun indexOfIgnoreCase(html: String, needle: String, from: Int): Int {
        val n = html.length
        val m = needle.length
        if (m == 0) return from
        var i = from
        while (i <= n - m) {
            if (html.regionMatches(i, needle, 0, m, ignoreCase = true)) return i
            i++
        }
        return -1
    }

    /**
     * 在 [amp] 处尝试解码一个实体（`&nbsp;` / `&#65;` / `&#x1F600;`）。
     * 返回 (替换文本, 下一个下标)；不是合法实体返回 null，调用方按字面输出 '&'。
     */
    private fun decodeEntityAt(html: String, amp: Int): Pair<String, Int>? {
        val limit = minOf(html.length, amp + 34)
        var p = amp + 1
        while (p < limit) {
            val ch = html[p]
            if (ch == ';') {
                val body = html.substring(amp + 1, p)
                val replacement = decodeEntityBody(body) ?: return null
                return replacement to (p + 1)
            }
            if (!(ch == '#' || ch.isLetterOrDigit())) return null
            p++
        }
        return null
    }

    private fun decodeEntityBody(body: String): String? = when {
        // 注意保持与旧正则一致：这里 #x 是小写敏感的（正则没有 IGNORE_CASE）
        body.startsWith("#x") ->
            codePointOrNull(body.substring(2), 16)

        body.startsWith("#") ->
            codePointOrNull(body.substring(1), 10)

        else -> NAMED_ENTITIES[body.lowercase()]
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
