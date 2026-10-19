package com.azhan.qingyue.ui.reader

import com.azhan.qingyue.data.prefs.ReaderSettings
import com.azhan.qingyue.data.parser.BookDocument

/**
 * 预分页结果缓存。
 *
 * 翻页模式的分页成本和书的大小成正比 —— 一本 1100 万字的书在手机上要几十秒。
 * 而阅读页里有两个高频操作会反复触发它：
 *
 * 1. **工具栏收起 / 弹出**：可用高度变了，`produceState` 的 key 跟着变，整本书重排
 * 2. **退出再进入**：进程里 document 还在（磁盘缓存秒级恢复），但分页结果没了
 *
 * 所以按 (bookId, 宽, 高, 排版设置) 缓存分页结果，进程级单例。
 * 高度有「带工具栏 / 不带 / 带锁定条」几种，LRU 保留最近几份；
 * 总字符量超预算就从最旧开始淘汰，防止大书把缓存撑爆。
 */
internal object PaginateCache {

    private const val MAX_CHARS = 30_000_000

    private val map = LinkedHashMap<PaginateKey, List<BookPage>>()

    @Synchronized
    fun get(key: PaginateKey): List<BookPage>? {
        val hit = map.remove(key) ?: return null
        map[key] = hit
        return hit
    }

    @Synchronized
    fun put(key: PaginateKey, pages: List<BookPage>) {
        map[key] = pages
        var total = map.entries.sumOf { charCountOf(it.value) }

        val entries = map.entries.iterator()
        while (total > MAX_CHARS && entries.hasNext() && map.size > 1) {
            val entry = entries.next()
            if (entry.key == key) continue // 刚写入的这份不淘汰
            total -= charCountOf(entry.value)
            entries.remove()
        }
    }

    @Synchronized
    fun clear() {
        map.clear()
    }

    private fun charCountOf(pages: List<BookPage>): Long =
        pages.sumOf { page -> page.slices.sumOf { it.text.length.toLong() } }
}

/** 分页缓存的键。宽高变化（工具栏显隐 / 锁定条）和排版设置变化都会产生新键。 */
internal data class PaginateKey(
    val bookId: Long,
    val document: BookDocument,
    val widthPx: Int,
    val heightPx: Int,
    val settings: ReaderSettings,
)
