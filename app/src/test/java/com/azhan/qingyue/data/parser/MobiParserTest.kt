package com.azhan.qingyue.data.parser

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
}
