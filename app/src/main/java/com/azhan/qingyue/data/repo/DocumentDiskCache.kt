package com.azhan.qingyue.data.repo

import com.azhan.qingyue.data.parser.BlockStyle
import com.azhan.qingyue.data.parser.BookDocument
import com.azhan.qingyue.data.parser.InlineSpan
import com.azhan.qingyue.data.parser.InlineStyle
import com.azhan.qingyue.data.parser.Paragraph
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/** 源文件的指纹：大小 + 修改时间。任一对不上就视为文件变过，缓存作废。 */
data class SourceStamp(val size: Long, val lastModified: Long)

/**
 * 解析结果的磁盘缓存：一本一个 `doc_<bookId>.bin`。
 *
 * 为什么需要它 —— 内存 LRU 只留 3 本，应用重启后必然全没；而重新打开一本书
 * 要走「SAF 读整个文件 + 完整解析」，其中 MOBI 的 PalmDOC/HUFF 解压是纯 CPU 活，
 * 几 MB 的书要好几秒。解析一遍的结果序列化下来，二次打开只剩一次反序列化
 * （毫秒到一两百毫秒），也不用再走 SAF 读原文。
 *
 * 正确性靠 [SourceStamp]：打开前查一次源文件的 size / lastModified（一次单行查询，
 * 比读几 MB 便宜得多），和缓存头部记录的对不上就作废重解析。文件被替换、被
 * 「重新添加」都会体现为指纹变化。
 *
 * 格式是带版本号的手写二进制：段落文本用 `writeUTF`（段落被 [BookDocumentBuilder]
 * 切到 900 字以内，远小于 UTF 的 64KB 上限）。写入先落临时文件再改名，避免留半截缓存。
 * 任何一步失败都只当「没有缓存」处理，绝不影响正常打开。
 *
 * 目录有总量上限，超出后按 lastModified 淘汰最旧的，防止缓存无限膨胀。
 */
class DocumentDiskCache(
    private val dir: File,
    /** 跟着解析行为走：BookDocumentBuilder / 章节切分规则变了就 +1，旧缓存整体作废。 */
    private val builderVersion: Int = 1,
    /** 缓存目录总量上限，超出后按 lastModified 淘汰最旧的。 */
    private val maxBytes: Long = 160L * 1024 * 1024,
) {

    fun cacheFile(bookId: Long): File = File(dir, "doc_$bookId.bin")

    fun load(bookId: Long, stamp: SourceStamp): BookDocument? = runCatching {
        val file = cacheFile(bookId)
        if (!file.exists()) return null

        DataInputStream(file.inputStream().buffered()).use { input ->
            if (input.readInt() != MAGIC) return null
            if (input.readInt() != FORMAT_VERSION) return null
            if (input.readInt() != builderVersion) return null
            if (input.readLong() != stamp.size || input.readLong() != stamp.lastModified) {
                return null
            }

            val title = input.readUTF()
            val author = if (input.readBoolean()) input.readUTF() else null
            val totalChars = input.readInt()

            val chapterTitles = List(input.readInt()) { input.readUTF() }
            val chapterStarts = List(input.readInt()) { input.readInt() }

            val paragraphCount = input.readInt()
            val paragraphs = ArrayList<Paragraph>(paragraphCount.coerceAtLeast(0))
            repeat(paragraphCount) {
                val text = input.readUTF()
                val startOffset = input.readInt()
                val chapterIndex = input.readInt()
                val blockStyle = BlockStyle.valueOf(input.readUTF())
                val isHeading = input.readBoolean()
                val spans = List(input.readInt()) {
                    InlineSpan(
                        start = input.readInt(),
                        end = input.readInt(),
                        style = InlineStyle.valueOf(input.readUTF()),
                    )
                }
                paragraphs += Paragraph(
                    text = text,
                    startOffset = startOffset,
                    chapterIndex = chapterIndex,
                    isHeading = isHeading,
                    blockStyle = blockStyle,
                    spans = spans,
                )
            }

            BookDocument(
                title = title,
                author = author,
                paragraphs = paragraphs,
                chapterStartIndices = chapterStarts,
                chapterTitles = chapterTitles,
                totalChars = totalChars,
            )
        }
    }.getOrNull()

    /** stamp 为 null（查不到源文件指纹）时不缓存 —— 没法验证就宁可每次重解析。 */
    fun save(bookId: Long, document: BookDocument, stamp: SourceStamp) {
        val tmp = File(dir, "doc_${bookId}.tmp")
        runCatching {
            DataOutputStream(tmp.outputStream().buffered()).use { out ->
                out.writeInt(MAGIC)
                out.writeInt(FORMAT_VERSION)
                out.writeInt(builderVersion)
                out.writeLong(stamp.size)
                out.writeLong(stamp.lastModified)

                out.writeUTF(document.title)
                out.writeBoolean(document.author != null)
                document.author?.let { out.writeUTF(it) }
                out.writeInt(document.totalChars)

                out.writeInt(document.chapterTitles.size)
                document.chapterTitles.forEach { out.writeUTF(it) }

                out.writeInt(document.chapterStartIndices.size)
                document.chapterStartIndices.forEach { out.writeInt(it) }

                out.writeInt(document.paragraphs.size)
                document.paragraphs.forEach { p ->
                    out.writeUTF(p.text)
                    out.writeInt(p.startOffset)
                    out.writeInt(p.chapterIndex)
                    out.writeUTF(p.blockStyle.name)
                    out.writeBoolean(p.isHeading)
                    out.writeInt(p.spans.size)
                    p.spans.forEach { span ->
                        out.writeInt(span.start)
                        out.writeInt(span.end)
                        out.writeUTF(span.style.name)
                    }
                }
            }

            val target = cacheFile(bookId)
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }

            evict(keepBookId = bookId)
        }.onFailure {
            runCatching { tmp.delete() }
        }
    }

    fun remove(bookId: Long) {
        runCatching { cacheFile(bookId).delete() }
    }

    /** 缓存目录总量超上限时，按最后修改时间从旧到新删，刚写入的这本书除外。 */
    private fun evict(keepBookId: Long) {
        val files = dir.listFiles()?.filter { it.isFile && it.extension == "bin" } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return

        files.asSequence()
            .filter { it.nameWithoutExtension != "doc_$keepBookId" }
            .sortedBy { it.lastModified() }
            .forEach { file ->
                if (total <= maxBytes) return
                val size = file.length()
                if (file.delete()) total -= size
            }
    }

    private companion object {
        const val MAGIC = 0x5159_4443 // "QYDC"
        const val FORMAT_VERSION = 1
    }
}
