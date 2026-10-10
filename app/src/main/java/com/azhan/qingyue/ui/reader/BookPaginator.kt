package com.azhan.qingyue.ui.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.azhan.qingyue.data.parser.BlockStyle
import com.azhan.qingyue.data.parser.BookDocument
import com.azhan.qingyue.data.parser.InlineSpan
import com.azhan.qingyue.data.parser.InlineStyle
import com.azhan.qingyue.data.prefs.ReaderSettings

/**
 * 正文样式。
 *
 * 测量和渲染**必须**共用这一个函数 —— 分页高度是照着测量结果算的，
 * 只要渲染时样式有一点不同（字号、行距、首行缩进），页面就会溢出或留白。
 * 颜色不参与排版，所以测量时传 [Color.Black]，渲染时传主题色。
 */
internal fun readerTextStyle(
    settings: ReaderSettings,
    blockStyle: BlockStyle,
    isParagraphStart: Boolean,
    color: Color,
): TextStyle {
    val heading = blockStyle.isHeading()
    val scale = when (blockStyle) {
        BlockStyle.H1 -> 1.60f
        BlockStyle.H2 -> 1.40f
        BlockStyle.H3 -> 1.24f
        BlockStyle.H4 -> 1.14f
        BlockStyle.H5 -> 1.07f
        BlockStyle.H6 -> 1.00f
        BlockStyle.CODE -> 0.92f
        else -> 1.00f
    }
    val fontSize = settings.fontSizeSp * scale

    return TextStyle(
        color = color,
        fontSize = fontSize.sp,
        lineHeight = (fontSize * settings.lineHeightMultiplier).sp,
        fontFamily = if (blockStyle == BlockStyle.CODE) FontFamily.Monospace else null,
        fontWeight = when {
            heading -> FontWeight.SemiBold
            blockStyle == BlockStyle.QUOTE -> FontWeight.Normal
            else -> FontWeight.Normal
        },
        textAlign = if (heading) TextAlign.Center else TextAlign.Start,
        // 列表项和引用不首行缩进，靠左侧留白区分层次
        textIndent = if (!heading && isParagraphStart &&
            blockStyle != BlockStyle.LIST_ITEM &&
            blockStyle != BlockStyle.ORDERED_ITEM &&
            blockStyle != BlockStyle.CODE &&
            blockStyle != BlockStyle.QUOTE
        ) {
            TextIndent(firstLine = 2.em)
        } else {
            TextIndent()
        },
    )
}

/** 段前距。同样必须测量与渲染共用。 */
internal fun readerSpacingBefore(settings: ReaderSettings, blockStyle: BlockStyle): Dp = when {
    blockStyle.isHeading() -> (settings.paragraphSpacingDp * 2.0f).dp
    blockStyle == BlockStyle.CODE -> (settings.paragraphSpacingDp * 0.4f).dp
    blockStyle == BlockStyle.RULE -> (settings.paragraphSpacingDp * 1.2f).dp
    blockStyle == BlockStyle.QUOTE -> (settings.paragraphSpacingDp * 0.6f).dp
    blockStyle == BlockStyle.LIST_ITEM || blockStyle == BlockStyle.ORDERED_ITEM ->
        (settings.paragraphSpacingDp * 0.35f).dp

    else -> settings.paragraphSpacingDp.dp
}

/** 左侧额外缩进：列表、引用、代码块需要让出层次。 */
internal fun readerIndentStart(settings: ReaderSettings, blockStyle: BlockStyle): Dp = when (blockStyle) {
    BlockStyle.QUOTE -> 16.dp
    BlockStyle.CODE -> 12.dp
    BlockStyle.LIST_ITEM, BlockStyle.ORDERED_ITEM -> 8.dp
    else -> 0.dp
}

/**
 * 把行内样式区间转成 AnnotatedString。
 *
 * 测量和渲染都走这里 —— 行内代码会换成等宽字体，字宽不同会直接影响换行位置，
 * 两边不一致的话分页又会算错。
 */
internal fun buildAnnotatedText(
    text: String,
    spans: List<InlineSpan>,
    baseColor: Color,
    accentColor: Color,
): AnnotatedString {
    if (spans.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        for (span in spans) {
            val from = span.start.coerceIn(0, text.length)
            val to = span.end.coerceIn(from, text.length)
            if (from == to) continue
            val style = when (span.style) {
                InlineStyle.BOLD -> SpanStyle(fontWeight = FontWeight.SemiBold)
                InlineStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                InlineStyle.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                InlineStyle.CODE -> SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    color = accentColor,
                )
            }
            addStyle(style, from, to)
        }
    }
}

/** 页内的一段文字。跨页时可能只是某个段落的一部分。 */
data class PageSlice(
    val text: String,
    val startOffset: Int,
    val chapterIndex: Int,
    val isHeading: Boolean,
    /** 是否是段落的真正开头 —— 决定要不要首行缩进。跨页续接的部分不缩进。 */
    val isParagraphStart: Boolean,
    val blockStyle: BlockStyle = BlockStyle.BODY,
    val spans: List<InlineSpan> = emptyList(),
)

/** 一页。 */
data class BookPage(
    val index: Int,
    val startOffset: Int,
    val slices: List<PageSlice>,
)

/**
 * 预分页。
 *
 * 做法是「按行装箱」：用 [TextMeasurer] 把段落真正排版出来，读它的行高，
 * 一行一行往当前页里塞，塞不下就翻页；单段跨页时在**行边界**切开。
 *
 * 为什么必须按行切而不是按段切 —— 一段 900 字的正文在手机上往往超过一屏，
 * 按段切会让长段落直接溢出屏幕。
 */
class BookPaginator(
    private val measurer: TextMeasurer,
    private val settings: ReaderSettings,
    private val density: Density,
) {

    fun paginate(document: BookDocument, widthPx: Int, heightPx: Int): List<BookPage> {
        if (widthPx <= 0 || heightPx <= 0) return emptyList()

        // 留 2dp 余量：字体度量与实际绘制之间可能有亚像素差异，
        // 不预留的话最后一行偶尔会被切掉一条边。
        val safetyMarginPx = with(density) { 2.dp.toPx() }
        val pageHeight = heightPx.toFloat() - safetyMarginPx
        val constraints = Constraints(maxWidth = widthPx)

        val pages = ArrayList<BookPage>(256)
        var slices = ArrayList<PageSlice>(24)
        var usedHeight = 0f
        var pageStart = 0

        fun flush() {
            if (slices.isEmpty()) return
            pages += BookPage(index = pages.size, startOffset = pageStart, slices = slices)
            slices = ArrayList(24)
            usedHeight = 0f
        }

        for (paragraph in document.paragraphs) {
            var remaining = paragraph.text
            var consumed = 0
            var isParagraphStart = true

            while (remaining.isNotEmpty()) {
                val spacingPx = with(density) {
                    readerSpacingBefore(settings, paragraph.blockStyle).toPx()
                }

                if (usedHeight + spacingPx >= pageHeight) flush()
                if (slices.isEmpty()) pageStart = paragraph.startOffset + consumed
                usedHeight += spacingPx

                // spans 的下标是相对整段的，切分后要换算到 remaining 上；
                // 跨切分点的 span 会丢掉（粗体跨页的极端情况，可接受）
                val localSpans = if (paragraph.spans.isEmpty()) {
                    emptyList()
                } else {
                    paragraph.spans
                        .filter { it.start >= consumed && it.end <= consumed + remaining.length }
                        .map { it.copy(start = it.start - consumed, end = it.end - consumed) }
                }

                val layout = measurer.measure(
                    text = buildAnnotatedText(remaining, localSpans, Color.Black, Color.Black),
                    style = readerTextStyle(
                        settings = settings,
                        blockStyle = paragraph.blockStyle,
                        isParagraphStart = isParagraphStart,
                        color = Color.Black,
                    ),
                    constraints = constraints,
                )

                // 当前页剩余空间能装下几整行
                var lines = 0
                var blockHeight = 0f
                while (lines < layout.lineCount) {
                    val lineHeight = layout.getLineBottom(lines) - layout.getLineTop(lines)
                    if (usedHeight + blockHeight + lineHeight > pageHeight) break
                    blockHeight += lineHeight
                    lines++
                }

                if (lines == 0) {
                    if (slices.isEmpty()) {
                        // 翻页之后一行都还放不下（页高比一行还矮的极端情况）。
                        // 强行放一行，否则这里会死循环。
                        lines = 1
                        blockHeight = layout.getLineBottom(0) - layout.getLineTop(0)
                    } else {
                        flush()
                        pageStart = paragraph.startOffset + consumed
                        continue
                    }
                }

                val endChar = layout.getLineEnd(lines - 1)
                slices += PageSlice(
                    text = remaining.substring(0, endChar),
                    startOffset = paragraph.startOffset + consumed,
                    chapterIndex = paragraph.chapterIndex,
                    isHeading = paragraph.isHeading,
                    isParagraphStart = isParagraphStart,
                    blockStyle = paragraph.blockStyle,
                    spans = localSpans.filter { it.end <= endChar },
                )
                usedHeight += blockHeight

                remaining = remaining.substring(endChar)
                consumed += endChar
                isParagraphStart = false

                if (remaining.isNotEmpty()) flush()
            }
        }
        flush()
        return pages
    }
}

/** 给定正文字符偏移，找出它落在第几页。 */
fun List<BookPage>.pageIndexForOffset(offset: Int): Int {
    if (isEmpty()) return 0
    var low = 0
    var high = lastIndex
    var result = 0
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (this[mid].startOffset <= offset) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return result
}
