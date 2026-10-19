package com.azhan.qingyue.data.repo

import com.azhan.qingyue.data.parser.BlockStyle
import com.azhan.qingyue.data.parser.BookContent
import com.azhan.qingyue.data.parser.BookDocument
import com.azhan.qingyue.data.parser.BookDocumentBuilder
import com.azhan.qingyue.data.parser.Chapter
import com.azhan.qingyue.data.parser.InlineSpan
import com.azhan.qingyue.data.parser.InlineStyle
import com.azhan.qingyue.data.parser.RichBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 解析结果磁盘缓存的往返测试。
 *
 * 这个缓存一旦读出错版内容，用户看到的就是「另一本书」—— 所以序列化必须
 * 无损：文本、段落偏移、章节索引、行内样式区间一个都不能差。
 */
class DocumentDiskCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val stamp = SourceStamp(size = 12345L, lastModified = 67890L)

    private fun sampleDocument(): BookDocument =
        BookDocumentBuilder.build(
            BookContent(
                title = "测试书",
                author = "某作者",
                chapters = listOf(
                    Chapter(
                        title = "第一章",
                        text = "",
                        blocks = listOf(
                            RichBlock(
                                text = "加粗与斜体混排的段落。",
                                blockStyle = BlockStyle.BODY,
                                spans = listOf(
                                    InlineSpan(0, 2, InlineStyle.BOLD),
                                    InlineSpan(3, 5, InlineStyle.ITALIC),
                                ),
                            ),
                            RichBlock("  - 列表项", BlockStyle.LIST_ITEM),
                            RichBlock("", BlockStyle.RULE),
                        ),
                    ),
                    Chapter(title = "第二章", text = "第二段正文。\n第三段正文。"),
                    Chapter(title = "第三章", text = ""),
                ),
            ),
        )

    @Test
    fun roundTripKeepsEverything() {
        val cache = DocumentDiskCache(tmp.newFolder())
        val document = sampleDocument()

        cache.save(1, document, stamp)
        val loaded = cache.load(1, stamp)

        assertNotNull(loaded)
        loaded!!
        assertEquals(document.title, loaded.title)
        assertEquals(document.author, loaded.author)
        assertEquals(document.totalChars, loaded.totalChars)
        assertEquals(document.chapterTitles, loaded.chapterTitles)
        assertEquals(document.chapterStartIndices, loaded.chapterStartIndices)
        assertEquals(document.paragraphs, loaded.paragraphs)
    }

    @Test
    fun stampMismatchInvalidatesCache() {
        val cache = DocumentDiskCache(tmp.newFolder())
        val document = sampleDocument()
        cache.save(1, document, stamp)

        val changed = SourceStamp(size = stamp.size + 1, lastModified = stamp.lastModified)
        assertNull(cache.load(1, changed))
    }

    @Test
    fun builderVersionInvalidatesCache() {
        val document = sampleDocument()
        val shared = tmp.newFolder()
        DocumentDiskCache(shared, builderVersion = 1).save(1, document, stamp)
        assertNull(DocumentDiskCache(shared, builderVersion = 2).load(1, stamp))
        // 同版本、同指纹时仍然可读，排除「版本号检查误伤」的可能
        assertNotNull(DocumentDiskCache(shared, builderVersion = 1).load(1, stamp))
    }

    @Test
    fun removeDeletesCacheFile() {
        val dir = tmp.newFolder()
        val cache = DocumentDiskCache(dir)
        cache.save(1, sampleDocument(), stamp)
        assertTrue(cache.cacheFile(1).exists())

        cache.remove(1)
        assertFalse(cache.cacheFile(1).exists())
        assertNull(cache.load(1, stamp))
    }

    @Test
    fun loadWithoutCacheReturnsNull() {
        val cache = DocumentDiskCache(tmp.newFolder())
        assertNull(cache.load(999, stamp))
    }

    @Test
    fun evictDeletesOldestBeyondLimit() {
        val dir = tmp.newFolder()
        val cache = DocumentDiskCache(dir, maxBytes = 1L) // 任何写入都会触发淘汰
        val keep = sampleDocument()

        // 伪造一个「很旧」的其它缓存文件
        val old = File(dir, "doc_99.bin").apply { writeText("stale") }
        old.setLastModified(1L)

        cache.save(7, keep, stamp)

        assertFalse(old.exists())
        assertTrue(cache.cacheFile(7).exists())
        assertNotNull(cache.load(7, stamp))
    }
}
