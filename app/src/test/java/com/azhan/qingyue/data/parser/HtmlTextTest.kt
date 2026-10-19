package com.azhan.qingyue.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * HTML 转纯文本的对照测试。
 *
 * 1.3.12 及以前的实现是 5 个正则各扫一遍全文，大书（1100 万字）要复制 4 份
 * 22MB 的中间字符串，是解析阶段 GC 风暴的主力。现在改成单遍状态机。
 *
 * 旧实现原样留在本测试里当**对照基准**：新旧输出必须逐字节一致，
 * 否则就是换实现时悄悄改变了语义（剥标签/换行/实体解码哪一步错了都不行）。
 */
class HtmlTextTest {

    // ------------------------------------------------------------ 旧实现（基准）

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

    private fun legacyToPlainText(html: String): String {
        var text = SCRIPT_STYLE.replace(html) { "" }
        text = LINE_BREAK.replace(text) { "\n" }
        text = BLOCK_END.replace(text) { "\n" }
        text = TAG.replace(text) { "" }
        return ENTITY.replace(text) { match ->
            val body = match.groupValues[1]
            when {
                body.startsWith("#x", ignoreCase = true) ->
                    codePointOrNull(body.substring(2), 16) ?: match.value

                body.startsWith("#") ->
                    codePointOrNull(body.substring(1), 10) ?: match.value

                else -> NAMED_ENTITIES[body.lowercase()] ?: match.value
            }
        }
    }

    private fun codePointOrNull(raw: String, radix: Int): String? {
        val code = raw.toIntOrNull(radix) ?: return null
        if (code <= 0 || code > 0x10FFFF) return null
        return runCatching { String(Character.toChars(code)) }.getOrNull()
    }

    // ------------------------------------------------------------ 用例

    private fun assertEquivalent(html: String) =
        assertEquals(legacyToPlainText(html), HtmlText.toPlainText(html))

    @Test
    fun syntheticDocument() {
        val html = """
            <html><head><title>t</title>
            <style>p { color: red }</style>
            <script type="text/javascript">var a = "<p>not real</p>";</script>
            </head><body>
            <p>第一段 &amp; &lt;转义&gt; &#65; &#x41; &nbsp;tail</p>
            <div>第二段<br>换行<br/>再换行<br / >结束</div>
            <ul><li>条目一</li><li>条目二</li></ul>
            <!-- 注释 <p> 也不算数 -->
            <h2>第二章</h2>
            </body></html>
        """.trimIndent()
        assertEquivalent(html)
    }

    @Test
    fun edgeCases() {
        listOf(
            "",
            "纯文本没有标签",
            "<>",
            "a<b",
            "a < b > c",
            "<p>hello</p>",
            "<BR>大写</BR>",
            "<br/><br /><br  >",
            "<br class=\"x\">带属性的 br 不是换行",
            "<p title=\"a>b\">属性里的 &gt; 不会提前结束标签</p>",
            "<script>没有闭合的脚本 <p>内容</p> 也照常处理",
            "<script>a</script>script 之后的内容保留",
            "<style>p{}</style>style 之后的内容保留",
            "<!-- 注释 -->注释后的文字",
            "&amp;&lt;&gt;&quot;&nbsp;&unknown;&am&#;&x41;&#;&#x;&#X41;&x41;",
            "&#x1F600;&#65;&#0;&#999999999;&#-1;",
            "<div><p>嵌套</p><p>多个块</p></div><section>区块</section>",
            "</p>孤立的闭合标签",
            "<h1>标题一</h1><h12>不是标题级别</h12>",
            "行尾没有换行的最后一段",
        ).forEach { assertEquivalent(it) }
    }

    /** 真实 MOBI 里的整本 HTML（Pride & Prejudice，835KB）。 */
    @Test
    fun realMobiHtml() {
        val file = File("/tmp/pg1342.mobi")
        assumeTrue("未找到测试样本，跳过", file.exists())

        val bytes = file.readBytes()
        val count = readUShort(bytes, 76)
        val offsets = IntArray(count + 1)
        for (i in 0 until count) offsets[i] = readInt(bytes, 78 + i * 8)
        offsets[count] = bytes.size
        val r0 = bytes.copyOfRange(offsets[0], offsets[1])
        val compression = readUShort(r0, 0)
        val textLength = readInt(r0, 4)
        val recordCount = readUShort(r0, 8)
        val recordSize = readUShort(r0, 10)
        val encoding = readInt(r0, 28)

        val text = when (compression) {
            2 -> MobiParser.readPalmDocText(
                records = List(count) { i ->
                    val from = offsets[i].coerceIn(0, bytes.size)
                    val to = offsets[i + 1].coerceIn(from, bytes.size)
                    bytes.copyOfRange(from, to)
                },
                count = recordCount,
                recordSize = recordSize,
                textLength = textLength,
            )
            else -> throw AssertionError("样本压缩方式变了：$compression")
        }
        val charset = if (encoding == 65001) Charsets.UTF_8 else Charsets.ISO_8859_1
        val html = String(text, charset)
        assertTrue("样本正文异常", html.length > 500_000)

        assertEquals(legacyToPlainText(html), HtmlText.toPlainText(html))
    }

    private fun readUShort(b: ByteArray, at: Int): Int =
        ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)

    private fun readInt(b: ByteArray, at: Int): Int =
        ((b[at].toInt() and 0xFF) shl 24) or
            ((b[at + 1].toInt() and 0xFF) shl 16) or
            ((b[at + 2].toInt() and 0xFF) shl 8) or
            (b[at + 3].toInt() and 0xFF)
}
