package com.azhan.qingyue.ui.bookshelf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
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
) {
    val palette = LocalReaderPalette.current
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

@Composable
internal fun BookCover(book: BookEntity, modifier: Modifier = Modifier) {
    val palette = LocalReaderPalette.current
    val bitmap = rememberCoverBitmap(book.coverPath)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(palette.divider),
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
                text = book.title.take(1),
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textSecondary,
            )
        }
    }
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
