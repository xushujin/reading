package com.azhan.qingyue.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 搜索匹配规则的测试。
 *
 * 大小写归一与空串处理都在 [matchesSearch] 内部完成，所以这里覆盖了
 * 大写关键词、中文、空串、null 作者等容易出错的边界。
 */
class BookSearchTest {

    private fun book(
        title: String,
        fileName: String = "$title.txt",
        author: String? = null,
    ) = BookEntity(
        shelfId = 1L,
        documentUri = "content://test/$fileName",
        fileName = fileName,
        title = title,
        author = author,
        format = "txt",
    )

    @Test
    fun matchesTitleAuthorAndFileName() {
        val b = book("Pride and Prejudice", "pride-sample.txt", "Jane Austen")

        assertTrue("书名应命中", b.matchesSearch("prejudice"))
        assertTrue("作者应命中", b.matchesSearch("austen"))
        assertTrue("文件名应命中", b.matchesSearch("sample"))
    }

    /**
     * 大小写不敏感是最容易改坏的一点：漏了 `lowercase()`，
     * 英文关键词就永远搜不到，而中文场景测不出来。
     */
    @Test
    fun matchingIsCaseInsensitive() {
        val b = book("Pride and Prejudice", author = "Jane Austen")

        assertTrue(b.matchesSearch("pride"))
        assertTrue(b.matchesSearch("PRIDE"))
        assertTrue(b.matchesSearch("jane austen"))
        assertTrue(b.matchesSearch("AUSTEN"))
    }

    @Test
    fun matchesChineseTitleAndAuthor() {
        val b = book("红楼梦", "红楼梦.txt", "曹雪芹")

        assertTrue("中文书名应命中", b.matchesSearch("红楼"))
        assertTrue("中文作者应命中", b.matchesSearch("曹雪芹"))
        assertFalse("无关词不应命中", b.matchesSearch("水浒"))
    }

    @Test
    fun doesNotMatchUnrelatedKeyword() {
        val b = book("Pride and Prejudice", author = "Jane Austen")

        assertFalse(b.matchesSearch("tolkien"))
        assertFalse(b.matchesSearch("zzz"))
    }

    @Test
    fun handlesNullAuthor() {
        val b = book("Untitled Note", author = null)

        assertTrue("书名仍可命中", b.matchesSearch("untitled"))
        assertFalse(b.matchesSearch("nobody"))
    }

    /** 空关键词由函数自己兜住，调用方不必特判 —— 否则会变成「列出所有书」。 */
    @Test
    fun blankKeywordMatchesNothing() {
        val b = book("Pride and Prejudice")

        assertFalse(b.matchesSearch(""))
        assertFalse(b.matchesSearch("   "))
    }

    /** 文件名与书名不同时（比如用户改了标题），两个字段都要能搜到。 */
    @Test
    fun searchesFileNameEvenWhenTitleDiffers() {
        val b = book(
            title = "第一章",
            fileName = "quantum-mechanics-notes.txt",
        )

        assertTrue("应能用文件名搜到", b.matchesSearch("quantum"))
        assertTrue("也应能用书名搜到", b.matchesSearch("第一章"))
    }
}
