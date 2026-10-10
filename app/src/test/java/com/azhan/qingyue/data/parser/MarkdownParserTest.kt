package com.azhan.qingyue.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    private val sample = """
        # 项目说明

        这是一段**粗体**、*斜体*、`行内代码`、~~删除线~~的正文。

        ## 快速开始

        - 第一项
        - 第二项有 **强调**

        1. 第一步
        2. 第二步

        > 这是一段引用

        ```kotlin
        val x = 1
        fun f() = x + 1
        ```

        ---

        更多内容见[官网](https://example.com)。

        # 附录

        附录正文。
    """.trimIndent()

    @Test
    fun parsesBlocksAndInlineStyles() {
        val doc = BookDocumentBuilder.build(MarkdownParser.parse(sample, "fallback"))
        val blocks = doc.paragraphs

        // 章节：H1 切出「项目说明」和「附录」两章
        assertEquals(listOf("项目说明", "附录"), doc.chapterTitles)

        fun find(text: String) = blocks.first { it.text.contains(text) }

        assertEquals(BlockStyle.BODY, find("这是一段").blockStyle)
        assertEquals(BlockStyle.H2, find("快速开始").blockStyle)
        assertEquals(BlockStyle.LIST_ITEM, find("第一项").blockStyle)
        assertEquals(BlockStyle.ORDERED_ITEM, find("第一步").blockStyle)
        assertEquals(BlockStyle.QUOTE, find("这是一段引用").blockStyle)
        assertEquals(BlockStyle.CODE, find("val x = 1").blockStyle)
        assertTrue("应有水平线块", blocks.any { it.blockStyle == BlockStyle.RULE })

        // 行内：标记字符应被吃掉，只留样式区间
        val body = find("这是一段")
        assertTrue("不应残留 ** 标记", !body.text.contains("**"))
        assertTrue("不应残留 ` 标记", !body.text.contains("`"))
        assertTrue("不应残留 ~~ 标记", !body.text.contains("~~"))
        assertEquals(
            setOf(InlineStyle.BOLD, InlineStyle.ITALIC, InlineStyle.CODE, InlineStyle.STRIKE),
            body.spans.map { it.style }.toSet(),
        )

        // 样式区间必须落在文本范围内，否则渲染会崩
        for (p in blocks) {
            for (s in p.spans) {
                assertTrue("span 越界: ${s} / len=${p.text.length}", s.start in 0..p.text.length)
                assertTrue("span 越界: ${s}", s.end in s.start..p.text.length)
            }
        }

        // 链接只保留文字
        val link = find("更多内容")
        assertTrue("链接文字应保留", link.text.contains("官网"))
        assertTrue("URL 应被丢弃", !link.text.contains("https://"))

        println("章节 = ${doc.chapterTitles}")
        println("块数 = ${blocks.size}")
        blocks.take(12).forEach { println("  [${it.blockStyle}] ${it.text.take(40)}") }
    }
}
