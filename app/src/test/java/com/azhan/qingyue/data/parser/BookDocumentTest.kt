package com.azhan.qingyue.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 章节坐标的回归测试。
 *
 * 1.3.5 及以前的目录跳转是坏的：`chapterStartIndices` 存的是**段落号**，
 * 却被直接当成**字符偏移**传给了 `paragraphIndexForOffset`。段落号比偏移量小得多，
 * 于是每次跳转都落在书首附近，而且不报错、不崩溃 —— 只能靠肉眼发现。
 *
 * 这里断言的是「按章节取到的偏移，反查回来必须还是那一章」。
 */
class BookDocumentTest {

    private fun document(): BookDocument = BookDocumentBuilder.build(
        BookContent(
            title = "测试书",
            author = null,
            chapters = listOf(
                Chapter(title = "第一章", text = "第一段正文。\n第二段正文。"),
                Chapter(title = "第二章", text = "第三段正文。"),
                Chapter(title = "第三章", text = "第四段正文。\n第五段正文。"),
            ),
        ),
    )

    @Test
    fun chapterOffsetMapsBackToSameChapter() {
        val document = document()

        document.chapterTitles.indices.forEach { chapter ->
            val offset = document.offsetOfChapter(chapter)
            assertNotNull("第 $chapter 章应该有偏移", offset)

            val paragraph = document.paragraphIndexForOffset(offset!!)
            assertEquals(
                "第 $chapter 章的偏移反查后应落回该章首段",
                document.chapterStartIndices[chapter],
                paragraph,
            )
        }
    }

    /**
     * 直接拿段落号当偏移用会跳到书首 —— 这条断言把那个错误行为钉死：
     * 第三章的段落号（7）远小于它的真实字符偏移。
     */
    @Test
    fun paragraphIndexIsNotACharacterOffset() {
        val document = document()
        val chapter = 2
        val paragraphIndex = document.chapterStartIndices[chapter]
        val offset = document.offsetOfChapter(chapter)!!

        assertTrue("正文偏移必须大于段落号", offset > paragraphIndex)
        assertTrue(
            "把段落号当偏移会落到前面的段落（这正是旧 bug）",
            document.paragraphIndexForOffset(paragraphIndex) < paragraphIndex,
        )
    }

    @Test
    fun chapterOffsetIsNullWhenChapterDoesNotExist() {
        assertEquals(null, document().offsetOfChapter(99))
        assertEquals(null, document().offsetOfChapter(-1))
    }

    @Test
    fun chapterOffsetsAreStrictlyIncreasing() {
        val document = document()
        val offsets = document.chapterTitles.indices.mapNotNull { document.offsetOfChapter(it) }

        assertEquals(document.chapterTitles.size, offsets.size)
        offsets.zipWithNext().forEach { (previous, next) ->
            assertTrue("章节偏移必须递增：$previous -> $next", next > previous)
        }
    }
}
