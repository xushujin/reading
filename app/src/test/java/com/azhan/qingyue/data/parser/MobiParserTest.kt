package com.azhan.qingyue.data.parser

import org.junit.Assert.assertArrayEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 用真实 MOBI 文件验证解析链路（PDB → PalmDOC 解压 → HTML 剥标签 → 章节切分）。
 *
 * 测试文件不入库：本地没有时自动跳过，避免 CI 上误报失败。
 * 取一个无 DRM 的样本：
 *   curl -sL -o /tmp/pg1342.mobi https://www.gutenberg.org/ebooks/1342.kindle.noimages
 */
class MobiParserTest {

    @Test
    fun parsesRealMobi() {
        val file = File("/tmp/pg1342.mobi")
        assumeTrue("未找到测试样本，跳过", file.exists())

        val content = MobiParser.parse(file.readBytes(), "fallback-title")

        val totalChars = content.chapters.sumOf { it.text.length }
        println("title      = ${content.title}")
        println("author     = ${content.author}")
        println("chapters   = ${content.chapters.size}")
        println("totalChars = $totalChars")
        println("--- 正文开头 ---")
        println(content.chapters.firstOrNull()?.text?.take(240))

        check(content.chapters.isNotEmpty()) { "没有解析出任何章节" }
        check(totalChars > 500_000) { "正文只有 $totalChars 字，解压可能不完整" }
        check(content.chapters.none { it.text.contains("\u0000") }) { "正文混入了空字节" }
    }

    /**
     * PalmDOC 解压的合成样本测试（不依赖真实文件）。
     *
     * 覆盖全部指令类别：NUL 字节、literal run、普通字面量、空格折叠、LZ77 回抄
     * （且 dist 指向前一个 record 的输出 —— 跨 record 引用是历史上踩过坑的地方）。
     * 1.3.11 及以前这里用 ArrayList<Byte> 装箱，25MB 的书解压时分配千万个小对象，
     * 表现为「一直解析中」；换成原生缓冲后必须逐字节等价。
     */
    @Test
    fun palmDocDecompressionHandlesAllOpcodes() {
        val base = "abcdefgh".repeat(40) // 320 字节字面量

        fun literalRuns(text: String): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            var i = 0
            while (i < text.length) {
                val take = minOf(8, text.length - i)
                out.write(0x08) // 接下来 take 个字节原样输出
                out.write(text.substring(i, i + take).toByteArray(Charsets.ISO_8859_1))
                i += take
            }
            return out.toByteArray()
        }

        // record 1：320 字节字面量
        val record1 = literalRuns(base)

        // record 2：NUL + 空格折叠 + literal run + 回抄
        // 0x81,0x05 → dist = ((0x8105 shr 3) and 0x7FF) = 32，len = (0x05 and 7) + 3 = 8
        val record2 = byteArrayOf(
            0x00.toByte(),
            0xC1.toByte(), // 空格 + 'A'
            0x03.toByte(), 'x'.code.toByte(), 'y'.code.toByte(), 'z'.code.toByte(),
            0x81.toByte(), 0x05.toByte(),
        )

        val nul = "\u0000"
        val expected = base + nul + " A" + "xyz" + base.substring(294, 302)

        val out = MobiParser.readPalmDocText(
            records = listOf(ByteArray(0), record1, record2),
            count = 2,
            recordSize = 4096,
            textLength = expected.length,
        )

        assertArrayEquals(expected.toByteArray(Charsets.ISO_8859_1), out)
    }
}
