package com.azhan.qingyue.data.parser

/**
 * Markdown 解析。
 *
 * 产出的是**带样式的块列表**（[RichBlock]），不是纯文本 ——
 * 这样标题、引用、代码块、列表在阅读页里是真渲染出来的，而不是把 `#`、`**`
 * 原样显示给用户看。
 *
 * 覆盖范围（够读技术文档和笔记，但不追求 CommonMark 全量）：
 *   块级：H1–H6 / 段落 / 无序列表 / 有序列表 / 引用 / 围栏代码块 / 水平线
 *   行内：粗体 / 斜体 / 行内代码 / 删除线 / 链接（只显示文字，丢弃 URL）
 *
 * 章节切分：遇到 H1 或 H2 就开新章，标题取自该行。没有标题的文档整篇作一章。
 */
object MarkdownParser {

    private const val MAX_BLOCK_CHARS = 900

    fun parse(text: String, fallbackTitle: String): BookContent {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')

        // 切章层级自适应：只有一个 H1（或没有）说明它只是文档标题，
        // 这时按 H2 切章；有多个 H1 才按 H1 切。
        // 兼顾「单篇技术笔记」（1 个 H1 + 多个 H2）和「多章文档」两种结构。
        val h1Count = lines.count { H1.matches(it.trim()) }
        val splitLevel = if (h1Count <= 1) 2 else 1

        val chapters = ArrayList<Chapter>(32)
        var currentTitle = ""
        var blocks = ArrayList<RichBlock>(64)

        fun flush() {
            if (blocks.isEmpty() && currentTitle.isEmpty()) return
            chapters += Chapter(title = currentTitle, text = "", blocks = blocks)
            blocks = ArrayList(64)
        }

        var i = 0
        while (i < lines.size) {
            val raw = lines[i]
            val line = raw.trimEnd()

            // 围栏代码块：整段原样收进来
            if (line.trimStart().startsWith("```")) {
                val fence = line.trimStart().takeWhile { it == '`' }
                val body = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith(fence)) {
                    if (body.isNotEmpty()) body.append('\n')
                    body.append(lines[i])
                    i++
                }
                i++ // 跳过结束围栏
                if (body.isNotEmpty()) {
                    blocks += RichBlock(body.toString(), BlockStyle.CODE)
                }
                continue
            }

            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> Unit // 空行只作分隔，不产出块

                HEADING.matches(trimmed) -> {
                    val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 6)
                    val content = trimmed.drop(level).trim()
                    if (content.isNotEmpty()) {
                        if (level <= splitLevel) {
                            // 到达切章层级：开新章
                            flush()
                            currentTitle = content
                        } else {
                            blocks += RichBlock(
                                text = content,
                                blockStyle = headingStyleOf(level),
                                spans = parseInline(content).second,
                            )
                        }
                    }
                }

                RULE.matches(trimmed) -> blocks += RichBlock("", BlockStyle.RULE)

                trimmed.startsWith(">") -> {
                    val content = trimmed.drop(1).trim()
                    if (content.isNotEmpty()) {
                        val (t, s) = parseInline(content)
                        blocks += RichBlock(t, BlockStyle.QUOTE, s)
                    }
                }

                UNORDERED.matches(trimmed) -> {
                    val content = trimmed.dropWhile { it == '-' || it == '*' || it == '+' }.trim()
                    val (t, s) = parseInline(content)
                    blocks += RichBlock("• $t", BlockStyle.LIST_ITEM, shift(s, 2))
                }

                ORDERED.matches(trimmed) -> {
                    val dot = trimmed.indexOf('.')
                    val number = trimmed.take(dot + 1)
                    val content = trimmed.drop(dot + 1).trim()
                    val (t, s) = parseInline(content)
                    val prefix = "$number "
                    blocks += RichBlock(prefix + t, BlockStyle.ORDERED_ITEM, shift(s, prefix.length))
                }

                else -> {
                    val (t, s) = parseInline(trimmed)
                    if (t.isNotEmpty()) {
                        // 超长段落按长度硬切，避免单块过大拖累滚动
                        if (t.length <= MAX_BLOCK_CHARS) {
                            blocks += RichBlock(t, BlockStyle.BODY, s)
                        } else {
                            var start = 0
                            while (start < t.length) {
                                val end = minOf(start + MAX_BLOCK_CHARS, t.length)
                                val piece = t.substring(start, end)
                                val local = s.filter { it.start >= start && it.end <= end }
                                    .map { it.copy(start = it.start - start, end = it.end - start) }
                                blocks += RichBlock(piece, BlockStyle.BODY, local)
                                start = end
                            }
                        }
                    }
                }
            }
            i++
        }
        flush()

        val usable = chapters.filter { it.blocks.isNotEmpty() || it.title.isNotEmpty() }
        return BookContent(
            title = sniffTitle(text, fallbackTitle),
            author = null,
            chapters = usable.ifEmpty {
                listOf(Chapter("", text.trim(), emptyList()))
            },
            coverBytes = null,
        )
    }

    private fun headingStyleOf(level: Int): BlockStyle = when (level) {
        1 -> BlockStyle.H1
        2 -> BlockStyle.H2
        3 -> BlockStyle.H3
        4 -> BlockStyle.H4
        5 -> BlockStyle.H5
        else -> BlockStyle.H6
    }

    private val H1 = Regex("^#\\s+.*$")
    private val HEADING = Regex("^#{1,6}\\s+.*$")
    private val RULE = Regex("^(-{3,}|\\*{3,}|_{3,})$")
    private val UNORDERED = Regex("^[-*+]\\s+.*$")
    private val ORDERED = Regex("^\\d+[.)]\\s+.*$")

    // ------------------------------------------------------ 行内解析

    private class Match(val inner: String, val style: InlineStyle, val next: Int)

    /**
     * 行内标记解析。返回「去掉标记后的文本」+ 各样式区间。
     *
     * 支持一层嵌套（如 `**粗体里有 \`代码\`**`），够用且实现简单；
     * 不处理嵌套同种标记这类边缘情况。
     */
    private fun parseInline(src: String): Pair<String, List<InlineSpan>> {
        val sb = StringBuilder(src.length)
        val spans = ArrayList<InlineSpan>()
        parseInto(src, sb, spans)
        return sb.toString() to spans
    }

    private fun parseInto(src: String, sb: StringBuilder, spans: MutableList<InlineSpan>) {
        var i = 0
        while (i < src.length) {
            val m = matchAt(src, i)
            if (m != null) {
                val start = sb.length
                parseInto(m.inner, sb, spans)
                if (sb.length > start) {
                    spans += InlineSpan(start, sb.length, m.style)
                }
                i = m.next
            } else {
                sb.append(src[i])
                i++
            }
        }
    }

    private fun matchAt(src: String, i: Int): Match? {
        // 行内代码优先级最高：里面的 * 不该被当强调
        if (src[i] == '`') {
            val end = src.indexOf('`', i + 1)
            if (end > i + 1) return Match(src.substring(i + 1, end), InlineStyle.CODE, end + 1)
        }
        if (src.startsWith("**", i)) {
            val end = src.indexOf("**", i + 2)
            if (end > i + 2) return Match(src.substring(i + 2, end), InlineStyle.BOLD, end + 2)
        }
        if (src.startsWith("~~", i)) {
            val end = src.indexOf("~~", i + 2)
            if (end > i + 2) return Match(src.substring(i + 2, end), InlineStyle.STRIKE, end + 2)
        }
        if (src[i] == '*') {
            val end = src.indexOf('*', i + 1)
            if (end > i + 1) return Match(src.substring(i + 1, end), InlineStyle.ITALIC, end + 1)
        }
        if (src[i] == '_') {
            val end = src.indexOf('_', i + 1)
            if (end > i + 1) return Match(src.substring(i + 1, end), InlineStyle.ITALIC, end + 1)
        }
        if (src[i] == '[') {
            val close = src.indexOf(']', i + 1)
            if (close > i + 1 && close + 1 < src.length && src[close + 1] == '(') {
                val paren = src.indexOf(')', close + 2)
                if (paren > close) {
                    // 链接只保留显示文字，URL 丢掉
                    return Match(src.substring(i + 1, close), InlineStyle.BOLD, paren + 1)
                }
            }
        }
        return null
    }

    /** 给列表项加了前缀后，行内区间要整体后移。 */
    private fun shift(spans: List<InlineSpan>, delta: Int): List<InlineSpan> =
        if (delta == 0) spans else spans.map { it.copy(start = it.start + delta, end = it.end + delta) }

    /** 从正文里嗅一个书名：第一个 H1，否则用文件名。 */
    private fun sniffTitle(text: String, fallback: String): String {
        for (line in text.lineSequence().take(40)) {
            val t = line.trim()
            if (t.startsWith("# ")) {
                val title = t.drop(2).trim()
                if (title.isNotEmpty()) return title
            }
        }
        return fallback
    }
}
