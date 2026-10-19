package com.azhan.qingyue.ui.bookshelf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azhan.qingyue.data.local.BookEntity
import com.azhan.qingyue.ui.neu.NeuProgress
import com.azhan.qingyue.ui.neu.NeuSurface
import com.azhan.qingyue.ui.theme.LocalReaderPalette
import com.azhan.qingyue.util.formatCharCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

/**
 * 书籍行。
 *
 * 首页书架和收藏页共用 —— 两处各写一套的话，改了一边忘另一边，
 * 同一个 App 里同一种条目会长得不一样（而且不会编译报错）。
 */
@Composable
internal fun BookRow(
    book: BookEntity,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    /** 参数为 true 表示用户点的是「删除本地文件」，弹窗里的开关默认打开。 */
    onRequestRemove: ((deleteFile: Boolean) -> Unit)? = null,
) {
    val palette = LocalReaderPalette.current
    var menuOpen by remember { mutableStateOf(false) }

    NeuSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        cornerRadius = 16.dp,
        elevation = 6.dp,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(book = book, modifier = Modifier.size(44.dp, 60.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = buildMetaLine(book),
                    fontSize = 11.sp,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(9.dp))
                NeuProgress(progress = book.progress)
            }
            // 星标自己消费点击事件，不会连带触发整行的「打开这本书」
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (book.isFavorite) {
                        Icons.Rounded.Star
                    } else {
                        Icons.Rounded.StarBorder
                    },
                    contentDescription = if (book.isFavorite) "取消收藏" else "加入收藏",
                    tint = if (book.isFavorite) palette.accent else palette.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            // 移除入口只在书架页提供；收藏页传 null，菜单就不出现。
            if (onRequestRemove != null) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "更多操作",
                            tint = palette.textSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("从书架移除") },
                            onClick = {
                                menuOpen = false
                                onRequestRemove(false)
                            },
                        )
                        // 单独列一条：只写「从书架移除」的话，用户根本找不到删文件的入口
                        DropdownMenuItem(
                            text = { Text("删除本地文件…") },
                            onClick = {
                                menuOpen = false
                                onRequestRemove(true)
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 一行元信息：作者 · 格式 · 字数 · 进度。 */
internal fun buildMetaLine(book: BookEntity): String {
    val parts = mutableListOf<String>()
    book.author?.takeIf { it.isNotBlank() }?.let { parts += it }
    parts += book.format.uppercase()
    if (book.charCount > 0L) parts += formatCharCount(book.charCount)
    if (book.progress > 0.001f) parts += "已读 ${(book.progress * 100).toInt()}%"
    return parts.joinToString(" · ")
}

/**
 * 封面。
 *
 * 没有封面图时，按书名生成一个**稳定的渐变**（同一本书永远同一个色），
 * 比原来的「灰底 + 首字」耐看得多 —— 一屏书排下来是彩色的，
 * 而且颜色本身能帮人认出书。
 */
@Composable
internal fun BookCover(book: BookEntity, modifier: Modifier = Modifier) {
    val palette = LocalReaderPalette.current
    val bitmap = rememberCoverBitmap(book.coverPath)
    val gradient = remember(book.title) { coverGradient(book.title) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(gradient))
            .border(0.5.dp, palette.shadow.copy(alpha = 0.28f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = coverInitials(book.title),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.92f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = 3.dp),
            )
        }
    }
}

/**
 * 按书名取一个固定的渐变色对。
 *
 * 用 `hashCode` 做种子 —— 只要书名不变，颜色就不会变，不会每次重组都换色。
 * 两端的色相相差 40°，保证有明暗对比又不至于脏。
 */
private fun coverGradient(title: String): List<Color> {
    val hue = (title.hashCode().absoluteValue % 360).toFloat()
    return listOf(
        Color.hsl(hue, 0.42f, 0.63f),
        Color.hsl((hue + 40f) % 360f, 0.48f, 0.45f),
    )
}

/** 封面上的字：中文取前两字，英文取首字母缩写，最多两个字符。 */
private fun coverInitials(title: String): String {
    val trimmed = title.trim()
    if (trimmed.isEmpty()) return "书"

    val first = trimmed.first()
    // 英文按单词取首字母，最多两个
    if (first.isLetter() && first.code < 128) {
        return trimmed
            .split(' ', '-', '_')
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
            .ifEmpty { first.uppercaseChar().toString() }
    }
    return trimmed.take(2)
}

@Composable
private fun rememberCoverBitmap(path: String?): Bitmap? {
    val bitmap by produceState<Bitmap?>(initialValue = null, path) {
        value = if (path.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
            }
        }
    }
    return bitmap
}
