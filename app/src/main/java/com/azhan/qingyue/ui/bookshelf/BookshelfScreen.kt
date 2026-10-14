package com.azhan.qingyue.ui.bookshelf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.azhan.qingyue.AppContainer
import com.azhan.qingyue.R
import com.azhan.qingyue.data.local.BookEntity
import com.azhan.qingyue.data.local.ShelfWithBooks
import com.azhan.qingyue.ui.neu.NeuButton
import com.azhan.qingyue.ui.neu.NeuIconButton
import com.azhan.qingyue.ui.neu.NeuProgress
import com.azhan.qingyue.ui.neu.NeuSurface
import com.azhan.qingyue.ui.theme.LocalReaderPalette
import com.azhan.qingyue.util.formatCharCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun BookshelfScreen(
    container: AppContainer,
    onOpenBook: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFavorites: () -> Unit,
) {
    val viewModel: BookshelfViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                BookshelfViewModel(container.libraryRepository, container.bookRepository)
            }
        },
    )

    val palette = LocalReaderPalette.current
    val shelves by viewModel.shelves.collectAsState()
    val recent by viewModel.recent.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()
    val expandedShelves by viewModel.expandedShelves.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val favoritesExpanded by viewModel.favoritesExpanded.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let(viewModel::addShelf) }

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
            ShelfTopBar(
                bookCount = shelves.sumOf { it.books.size },
                shelfCount = shelves.size,
                onAdd = { pickFolder.launch(null) },
                onOpenFavorites = onOpenFavorites,
                onSettings = onOpenSettings,
            )

            if (shelves.isEmpty()) {
                EmptyShelf(onAdd = { pickFolder.launch(null) })
            } else {
                SearchField(
                    query = searchQuery,
                    onQueryChange = viewModel::onSearchQueryChange,
                    onClear = viewModel::clearSearch,
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 28.dp),
                ) {
                    if (searchQuery.isNotBlank()) {
                        // 搜索态：跨书架扁平列出命中结果，不再按文件夹分组
                        if (searchResults.isEmpty()) {
                            item(key = "search-empty") { EmptySearchHint(searchQuery) }
                        } else {
                            item(key = "search-label") {
                                GroupLabel("找到 ${searchResults.size} 本")
                            }
                            items(searchResults, key = { "search-${it.id}" }) { book ->
                                BookRow(
                                    book = book,
                                    onClick = { onOpenBook(book.id) },
                                    onToggleFavorite = { viewModel.toggleFavorite(book) },
                                )
                            }
                        }
                    } else {
                        if (recent.isNotEmpty()) {
                            item(key = "recent-label") { GroupLabel("最近阅读") }
                            item(key = "recent-row") {
                                RecentRow(books = recent, onOpenBook = onOpenBook)
                            }
                        }

                        // 收藏跨书架，所以单独成组，不归在任何文件夹下
                        if (favorites.isNotEmpty()) {
                            item(key = "favorites-header") {
                                GroupHeader(
                                    name = "收藏",
                                    count = favorites.size,
                                    expanded = favoritesExpanded,
                                    onToggle = { viewModel.toggleFavoritesExpanded() },
                                )
                            }
                            if (favoritesExpanded) {
                                items(favorites, key = { "favorite-${it.id}" }) { book ->
                                    BookRow(
                                        book = book,
                                        onClick = { onOpenBook(book.id) },
                                        onToggleFavorite = { viewModel.toggleFavorite(book) },
                                    )
                                }
                            }
                        }

                        shelves.forEach { shelf ->
                            val expanded = shelf.shelf.id in expandedShelves
                            item(key = "shelf-${shelf.shelf.id}") {
                                GroupHeader(
                                    name = shelf.shelf.name,
                                    count = shelf.books.size,
                                    expanded = expanded,
                                    onToggle = { viewModel.toggleShelf(shelf.shelf.id) },
                                    onRescan = { viewModel.rescan(shelf.shelf.id) },
                                    onRemove = {
                                        viewModel.removeShelf(shelf.shelf.id, shelf.shelf.name)
                                    },
                                )
                            }
                            if (expanded) {
                                items(shelf.books, key = { "book-${it.id}" }) { book ->
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

        if (busy) {
            BusyOverlay()
        }
    }
}

@Composable
private fun ShelfTopBar(
    bookCount: Int,
    shelfCount: Int,
    onAdd: () -> Unit,
    onOpenFavorites: () -> Unit,
    onSettings: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 6.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (shelfCount == 0) "还没有添加目录" else "$shelfCount 个目录 · $bookCount 本",
                fontSize = 12.sp,
                color = palette.textSecondary,
            )
        }
        NeuIconButton(onClick = onOpenFavorites, size = 42.dp) {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = "我的收藏",
                tint = palette.accent,
                modifier = Modifier.size(20.dp),
            )
        }
        NeuIconButton(onClick = onAdd, size = 42.dp) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "添加本地目录",
                tint = palette.accent,
                modifier = Modifier.size(20.dp),
            )
        }
        NeuIconButton(onClick = onSettings, size = 42.dp) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = "设置",
                tint = palette.textSecondary,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun EmptyShelf(onAdd: () -> Unit) {
    val palette = LocalReaderPalette.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NeuSurface(cornerRadius = 30.dp, elevation = 10.dp) {
            Box(
                modifier = Modifier.size(88.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(38.dp),
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = "书架还是空的",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.textPrimary,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "添加一个本地文件夹，\n里面的 TXT / EPUB 会自动出现在这里。",
            fontSize = 13.sp,
            color = palette.textSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(26.dp))
        NeuButton(onClick = onAdd, highlighted = true) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("添加本地目录", color = Color.White, fontSize = 14.sp)
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    val palette = LocalReaderPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp, 16.dp)
                .background(palette.accent, RoundedCornerShape(3.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.textPrimary,
        )
    }
}

/**
 * 分组头。目录分组与收藏分组共用 ——
 * 收藏没有「重新扫描 / 移除」这类操作，传 null 就不显示菜单。
 */
@Composable
private fun GroupHeader(
    name: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRescan: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    val palette = LocalReaderPalette.current
    var menuOpen by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "groupArrow",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 8.dp, top = 10.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp, 16.dp)
                .background(palette.accent, RoundedCornerShape(3.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = name,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$count",
            fontSize = 12.sp,
            color = palette.textSecondary,
        )
        Icon(
            imageVector = Icons.Rounded.ExpandMore,
            contentDescription = if (expanded) "折叠" else "展开",
            tint = palette.textSecondary,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(18.dp)
                .rotate(arrowRotation),
        )
        if (onRescan != null || onRemove != null) {
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
                    if (onRescan != null) {
                        DropdownMenuItem(
                            text = { Text("重新扫描") },
                            onClick = {
                                menuOpen = false
                                onRescan()
                            },
                        )
                    }
                    if (onRemove != null) {
                        DropdownMenuItem(
                            text = { Text("移除书架") },
                            onClick = {
                                menuOpen = false
                                onRemove()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentRow(books: List<BookEntity>, onOpenBook: (Long) -> Unit) {
    val palette = LocalReaderPalette.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(books, key = { "recent-${it.id}" }) { book ->
            NeuSurface(
                modifier = Modifier.width(100.dp),
                cornerRadius = 14.dp,
                elevation = 6.dp,
                onClick = { onOpenBook(book.id) },
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BookCover(book = book, modifier = Modifier.size(54.dp, 72.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = book.title,
                        fontSize = 11.sp,
                        color = palette.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        lineHeight = 15.sp,
                    )
                }
            }
        }
    }
}

/** 搜索输入框。拟态外观，与整页风格保持一致。 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    val palette = LocalReaderPalette.current

    NeuSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        cornerRadius = 16.dp,
        elevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = palette.textSecondary,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, color = palette.textPrimary),
                cursorBrush = SolidColor(palette.accent),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 11.dp),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            text = "搜索书名、作者",
                            fontSize = 14.sp,
                            color = palette.textSecondary,
                        )
                    }
                    inner()
                },
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "清除搜索",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}

/** 搜索无结果时的提示。 */
@Composable
private fun EmptySearchHint(keyword: String) {
    val palette = LocalReaderPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp, start = 36.dp, end = 36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "没有找到与「$keyword」匹配的书",
            fontSize = 13.sp,
            color = palette.textSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
        )
    }
}

@Composable
private fun BusyOverlay() {
    val palette = LocalReaderPalette.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center,
    ) {
        NeuSurface(cornerRadius = 22.dp, elevation = 8.dp) {
            Box(
                modifier = Modifier.padding(26.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = palette.accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}
