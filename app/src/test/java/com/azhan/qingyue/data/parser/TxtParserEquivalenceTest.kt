package com.azhan.qingyue.data.parser

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 章节切分的对照测试。
 *
 * 切分实现从 `text.split('\n')` 换成了逐行扫描（大书省一份几十万行字符串的
 * 中间分配），旧实现留在本测试里当基准：两种输入的输出必须完全一致，
 * 包括「结尾没有换行」「结尾多余换行」「空行」「孤立标题」这些边角。
 */
class TxtParserEquivalenceTest {

    private fun legacySplitChapters(text: String): List<Chapter> {
        if (text.isBlank()) return listOf(Chapter("", ""))

        val chapters = ArrayList<Chapter>(64)
        var currentTitle = ""
        val buffer = StringBuilder()

        fun flush() {
            val body = buffer.toString().trim()
            if (body.isNotEmpty() || currentTitle.isNotEmpty()) {
                chapters += Chapter(currentTitle, body)
            }
            buffer.setLength(0)
        }

        for (line in text.split('\n')) {
            if (ChapterRules.isChapterTitle(line)) {
                if (buffer.isNotBlank() || currentTitle.isNotEmpty()) flush()
                currentTitle = line.trim()
            } else {
                buffer.append(line).append('\n')
            }
        }
        flush()

        val withBody = chapters.filter { it.text.isNotBlank() }
        return withBody.ifEmpty { listOf(Chapter("", text)) }
    }

    private fun assertEquivalent(text: String) =
        assertEquals(legacySplitChapters(text), TxtParser.splitChapters(text))

    @Test
    fun emptyAndBlank() {
        assertEquivalent("")
        assertEquivalent("\n")
        assertEquivalent("\n\n   \n")
        assertEquivalent("   ")
    }

    @Test
    fun plainBody() {
        assertEquivalent("正文没有章节标记")
        assertEquivalent("第一行\n第二行\n第三行")
        assertEquivalent("第一行\n第二行\n") // 结尾多余换行
    }

    @Test
    fun chapters() {
        assertEquivalent("第一章 开端\n正文甲\n正文乙\n第二章 转折\n正文丙")
        assertEquivalent("第一章\n\n正文\n\n第二章\n")
        assertEquivalent("第一章\n第二章\n第三章\n只有第三章有正文")
        assertEquivalent("Chapter 1\nbody\nChapter 2\nbody")
        assertEquivalent("第一章 开端\n正文甲") // 结尾无换行
    }

    @Test
    fun trailingNewlineVariants() {
        assertEquivalent("第一章\n正文\n") // 标题行 + 正文 + 换行
        assertEquivalent("第一章\n") // 只有标题 + 换行（无正文的章节会被过滤）
        assertEquivalent("第一章\n正文\n\n\n") // 多个结尾换行
    }

    @Test
    fun crlfIsHandledUpstream() {
        // CRLF 归一化在 TxtParser.parse 里做，splitChapters 只认 \n；
        // 这里验证行为与旧实现一致即可（\r 会被当作行内容的一部分）。
        assertEquivalent("第一章\r\n正文\r\n")
    }
}
