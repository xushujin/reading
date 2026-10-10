package com.azhan.qingyue.data.parser

/**
 * TXT 解析：编码探测 → 归一化换行 → 按章节标题切分。
 *
 * TXT 没有元数据，书名从文件名兜底，作者和书名做一次轻量的首部嗅探
 * （《书名》/ 作者：xxx），失败就用文件名，不硬猜。
 */
object TxtParser {

    fun parse(bytes: ByteArray, fallbackTitle: String): BookContent {
        val text = TextDecoding.decode(bytes)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()

        return BookContent(
            title = ChapterRules.sniffTitle(text, fallbackTitle),
            author = ChapterRules.sniffAuthor(text),
            chapters = splitChapters(text),
            coverBytes = null,
        )
    }

    /** MOBI 解析也复用这套切分逻辑，所以是 internal 而非 private。 */
    internal fun splitChapters(text: String): List<Chapter> {
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
}
