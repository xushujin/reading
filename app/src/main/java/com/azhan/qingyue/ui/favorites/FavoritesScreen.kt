package com.azhan.qingyue.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.azhan.qingyue.AppContainer
import com.azhan.qingyue.data.local.ClipEntity
import com.azhan.qingyue.ui.bookshelf.BookRow
import com.azhan.qingyue.ui.neu.NeuSurface
import com.azhan.qingyue.ui.theme.LocalReaderPalette
import com.azhan.qingyue.util.formatRelativeTime

/**
 * 收藏页：把「文字片段」和「电子书」两种收藏放在一处，用 Tab 分开。
 *
 * 两者性质不同（一个是摘录、一个是整本书），但都是「我特意留着的」，
 * 分成两个入口反而要多记一个位置。
 */
@Composable
fun FavoritesScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenBook: (Long) -> Unit,
) {
    val viewModel: FavoritesViewModel = viewModel(
        factory = viewModelFactory {
            initializer { FavoritesViewModel(container.libraryRepository) }
        },
    )

    val palette = LocalReaderPalette.current
    val clips by viewModel.clips.collectAsState()
    val books by viewModel.favoriteBooks.collectAsState()
    val tab by viewModel.tab.collectAsState()
    val message by viewModel.message.collectAsState()

    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHost.showSnackbar(text)
        viewModel.consumeMessage()
    }

    Scaffold(
        containerColor = palette.background,
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            FavoritesTopBar(onBack = onBack)

            TabSwitcher(
                current = tab,
                clipCount = clips.size,
                bookCount = books.size,
                onSelect = viewModel::selectTab,
            )

            Spacer(Modifier.height(4.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 28.dp),
            ) {
                when (tab) {
                    FavoriteTab.CLIPS -> {
                        if (clips.isEmpty()) {
                            item(key = "clips-empty") {
                                EmptyHint(
                                    title = "还没有摘录",
                                    detail = "阅读时长按一段文字，就能把它收藏到这里。",
                                )
                            }
                        } else {
                            items(clips, key = { "clip-${it.id}" }) { clip ->
                                ClipCard(
                                    clip = clip,
                                    onDelete = { viewModel.removeClip(clip) },
                                    onClick = { onOpenBook(clip.bookId) },
                                )
                            }
                        }
                    }

                    FavoriteTab.BOOKS -> {
                        if (books.isEmpty()) {
                            item(key = "books-empty") {
                                EmptyHint(
                                    title = "还没有收藏的书",
                                    detail = "在书架上点书籍右侧的星标即可收藏。",
                                )
                            }
                        } else {
                            items(books, key = { "fav-${it.id}" }) { book ->
                                BookRow(
                                    book = book,
                                    onClick = { onOpenBook(book.id) },
                                    onToggleFavorite = { viewModel.toggleFavorite(book) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritesTopBar(onBack: () -> Unit) {
    val palette = LocalReaderPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                tint = palette.textPrimary,
            )
        }
        Text(
            text = "我的收藏",
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.textPrimary,
        )
    }
}

/** 两个分类的切换条。用拟态按钮，与设置页的分段控件观感一致。 */
@Composable
private fun TabSwitcher(
    current: FavoriteTab,
    clipCount: Int,
    bookCount: Int,
    onSelect: (FavoriteTab) -> Unit,
) {
    val palette = LocalReaderPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FavoriteTab.entries.forEach { entry ->
            val active = entry == current
            val count = if (entry == FavoriteTab.CLIPS) clipCount else bookCount

            NeuSurface(
                modifier = Modifier.weight(1f),
                cornerRadius = 14.dp,
                elevation = if (active) 3.dp else 5.dp,
                onClick = { onSelect(entry) },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${entry.label} $count",
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) palette.accent else palette.textPrimary,
                    )
                }
            }
        }
    }
}

/** 一条摘录。正文给足行数，书名/章节/时间压到一行小字。 */
@Composable
private fun ClipCard(
    clip: ClipEntity,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    NeuSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        cornerRadius = 16.dp,
        elevation = 6.dp,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 10.dp)) {
            Text(
                text = clip.text,
                fontSize = 14.sp,
                color = palette.textPrimary,
                lineHeight = 22.sp,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(3.dp, 12.dp)
                        .background(palette.accent, RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = buildSourceLine(clip),
                    fontSize = 11.sp,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "删除这条摘录",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

private fun buildSourceLine(clip: ClipEntity): String {
    val parts = mutableListOf(clip.bookTitle)
    clip.chapterTitle?.takeIf { it.isNotBlank() }?.let { parts += it }
    parts += formatRelativeTime(clip.createdAt)
    return parts.joinToString(" · ")
}

@Composable
private fun EmptyHint(title: String, detail: String) {
    val palette = LocalReaderPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 90.dp, start = 40.dp, end = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = palette.textPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = detail,
                fontSize = 12.sp,
                color = palette.textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp,
            )
        }
    }
}
