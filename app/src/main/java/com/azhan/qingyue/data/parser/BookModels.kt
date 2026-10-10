package com.azhan.qingyue.data.parser

/** 解析后的章节。TXT 里可能只有一个「无标题章节」。 */
data class Chapter(
    val title: String,
    val text: String,
)

/** 解析器的统一输出。 */
class BookContent(
    val title: String,
    val author: String?,
    val chapters: List<Chapter>,
    val coverBytes: ByteArray? = null,
)

/** 渲染单元。段落是虚拟化列表的最小单位，带字符偏移用于跨字号定位。 */
data class Paragraph(
    val text: String,
    val startOffset: Int,
    val chapterIndex: Int,
    val isHeading: Boolean,
)

/**
 * 排版无关的书籍模型。
 *
 * 阅读进度用「正文字符偏移」而不是「第几页 / 第几行」——
 * 字号、行距、屏幕尺寸变化后位置依然准确。
 */
class BookDocument(
    val title: String,
    val author: String?,
    val paragraphs: List<Paragraph>,
    val chapterStartIndices: List<Int>,
    val chapterTitles: List<String>,
    val totalChars: Int,
) {

    val paragraphCount: Int get() = paragraphs.size

    /** 二分查找：给定字符偏移，落在第几个段落。 */
    fun paragraphIndexForOffset(offset: Int): Int {
        if (paragraphs.isEmpty() || offset <= 0) return 0
        var low = 0
        var high = paragraphs.lastIndex
        var result = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (paragraphs[mid].startOffset <= offset) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    fun offsetOfParagraph(index: Int): Int =
        paragraphs.getOrNull(index)?.startOffset ?: totalChars

    fun progressOfOffset(offset: Int): Float =
        if (totalChars <= 0) 0f else (offset.toFloat() / totalChars).coerceIn(0f, 1f)

    fun progressOfParagraph(index: Int): Float = progressOfOffset(offsetOfParagraph(index))

    fun chapterIndexForParagraph(index: Int): Int =
        paragraphs.getOrNull(index)?.chapterIndex ?: 0
}

object BookDocumentBuilder {

    /** 单个段落超过该长度就按标点就近切开，避免超长段落拖累滚动与排版。 */
    private const val MAX_PARAGRAPH_CHARS = 900

    private val BREAK_CHARS = charArrayOf(
        '。', '！', '？', '；', '…', '.', '!', '?', ';',
    )

    fun build(content: BookContent): BookDocument {
        val paragraphs = ArrayList<Paragraph>(1024)
        val chapterStarts = ArrayList<Int>(content.chapters.size)
        val chapterTitles = ArrayList<String>(content.chapters.size)
        var offset = 0

        content.chapters.forEachIndexed { index, chapter ->
            chapterStarts += paragraphs.size
            chapterTitles += chapter.title

            val heading = chapter.title.trim()
            if (heading.isNotEmpty()) {
                paragraphs += Paragraph(heading, offset, index, isHeading = true)
                offset += heading.length + 1
            }
            for (line in splitParagraphs(chapter.text)) {
                paragraphs += Paragraph(line, offset, index, isHeading = false)
                offset += line.length + 1
            }
        }

        if (paragraphs.isEmpty()) {
            paragraphs += Paragraph(EMPTY_HINT, 0, 0, isHeading = false)
            chapterStarts += 0
            chapterTitles += content.title
            offset = EMPTY_HINT.length
        }

        return BookDocument(
            title = content.title,
            author = content.author,
            paragraphs = paragraphs,
            chapterStartIndices = chapterStarts,
            chapterTitles = chapterTitles,
            totalChars = offset.coerceAtLeast(1),
        )
    }

    private const val EMPTY_HINT = "（这本书里没有可显示的正文）"

    private fun splitParagraphs(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val result = ArrayList<String>(text.length / 60 + 8)
        for (raw in text.split('\n')) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.length <= MAX_PARAGRAPH_CHARS) {
                result += line
                continue
            }
            var start = 0
            while (start < line.length) {
                var end = minOf(start + MAX_PARAGRAPH_CHARS, line.length)
                if (end < line.length) {
                    val cut = line.lastIndexOfAny(BREAK_CHARS, end - 1)
                    if (cut > start + MAX_PARAGRAPH_CHARS / 3) end = cut + 1
                }
                result += line.substring(start, end)
                start = end
            }
        }
        return result
    }
}
