package com.azhan.qingyue.data.parser

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 大书诊断：用真实的金庸全集（25MB MOBI，解压后 29.5MB）量每个阶段的耗时与内存。
 * 文件不存在时自动跳过。
 */
class MobiLargeFileTest {

    @Test
    fun measureLargeMobiStages() {
        val file = File("/Users/hatim/Downloads/金庸全集.mobi")
        assumeTrue("本地没有样本文件，跳过", file.exists())

        fun mem(): Long = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        var t = System.currentTimeMillis()
        val bytes = file.readBytes()
        println("[1] 读文件            ${System.currentTimeMillis() - t} ms, ${bytes.size / 1024 / 1024} MB")

        t = System.currentTimeMillis()
        val records = MobiParser::class.java.getDeclaredMethod("readPdbRecords", ByteArray::class.java)
        records.isAccessible = true
        val recordList = records.invoke(MobiParser, bytes) as List<ByteArray>
        println("[2] 切 PDB record     ${System.currentTimeMillis() - t} ms, ${recordList.size} 条")

        t = System.currentTimeMillis()
        val text = MobiParser.readPalmDocText(recordList, 7208, 4096, 29_523_959)
        println("[3] PalmDOC 解压      ${System.currentTimeMillis() - t} ms, ${text.size / 1024 / 1024} MB")

        t = System.currentTimeMillis()
        val html = String(text, Charsets.UTF_8)
        println("[4] bytes → String    ${System.currentTimeMillis() - t} ms, ${html.length / 1024 / 1024} M chars")

        t = System.currentTimeMillis()
        val plain = HtmlText.toPlainText(html)
        println("[5] HTML 剥标签       ${System.currentTimeMillis() - t} ms, ${plain.length / 1024 / 1024} M chars")

        t = System.currentTimeMillis()
        val chapters = TxtParser.splitChapters(plain)
        println("[6] 章节切分          ${System.currentTimeMillis() - t} ms, ${chapters.size} 章")

        t = System.currentTimeMillis()
        val document = BookDocumentBuilder.build(chapters.let { BookContent("金庸全集", null, it) })
        println("[7] 构建段落流        ${System.currentTimeMillis() - t} ms, ${document.paragraphCount} 段")

        println("堆使用：${mem() / 1024 / 1024} MB（当前）")
    }
}
