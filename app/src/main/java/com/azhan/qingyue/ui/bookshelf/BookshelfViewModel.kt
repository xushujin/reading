package com.azhan.qingyue.ui.bookshelf

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azhan.qingyue.data.local.BookEntity
import com.azhan.qingyue.data.local.ShelfWithBooks
import com.azhan.qingyue.data.local.matchesSearch
import com.azhan.qingyue.data.repo.BookRepository
import com.azhan.qingyue.data.repo.DeleteFileResult
import com.azhan.qingyue.data.repo.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 等用户重新授权目录后才能继续删文件的请求。 */
data class PendingPermission(val book: BookEntity, val treeUri: String)

class BookshelfViewModel(
    private val library: LibraryRepository,
    private val books: BookRepository,
) : ViewModel() {

    val shelves: StateFlow<List<ShelfWithBooks>> = library.observeShelves()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recent: StateFlow<List<BookEntity>> = library.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** 搜索关键词。空串表示未在搜索，首页照常显示分组。 */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * 搜索结果：跨书架在**所有**已入库的书里匹配书名 / 文件名 / 作者。
     *
     * 用 combine 从 [shelves] 派生，而不是另开一条数据库查询 ——
     * 书本来就在 shelves 里，再查一次等于把同一份数据取两遍，
     * 还得额外操心两边不同步（比如刚收藏完、刚扫完目录）。
     */
    val searchResults: StateFlow<List<BookEntity>> =
        combine(shelves, _searchQuery) { groups, query ->
            if (query.isBlank()) {
                // 空查询直接短路，不必遍历全部书籍
                emptyList()
            } else {
                groups.asSequence()
                    .flatMap { it.books.asSequence() }
                    .filter { it.matchesSearch(query) }
                    .sortedBy { it.title }
                    .toList()
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** 非空时表示有一次「删文件但没写权限」的请求，UI 据此弹重新授权提示。 */
    private val _pendingPermission = MutableStateFlow<PendingPermission?>(null)
    val pendingPermission: StateFlow<PendingPermission?> = _pendingPermission.asStateFlow()

    /**
     * 已展开的书架 id。默认空集 = 全部折叠，首页先给一份干净的目录列表，
     * 点分组头才展开书目。状态放 ViewModel 而不是 remember ——
     * 进阅读页再返回时书架页会重新组合，remember 会丢。
     */
    private val _expandedShelves = MutableStateFlow<Set<Long>>(emptySet())
    val expandedShelves: StateFlow<Set<Long>> = _expandedShelves.asStateFlow()

    fun addShelf(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            val result = library.addShelf(uri)
            _busy.value = false

            _message.value = when {
                !result.isSuccess -> result.error
                result.addedCount == 0 -> "「${result.shelfName}」里没有新的电子书"
                else -> "已从「${result.shelfName}」添加 ${result.addedCount} 本"
            }
            if (result.isSuccess) books.backfillPendingMeta()
        }
    }

    fun rescan(shelfId: Long) {
        viewModelScope.launch {
            _busy.value = true
            val added = library.rescanShelf(shelfId)
            _busy.value = false
            _message.value = if (added > 0) "新增 $added 本" else "这个目录没有新书"
            books.backfillPendingMeta()
        }
    }

    fun rescanAll() {
        viewModelScope.launch {
            _busy.value = true
            val added = library.rescanAll()
            _busy.value = false
            _message.value = if (added > 0) "新增 $added 本" else "所有目录都是最新的"
            books.backfillPendingMeta()
        }
    }

    fun removeShelf(shelfId: Long, shelfName: String) {
        viewModelScope.launch {
            // 先清封面文件：书籍记录会被外键 CASCADE 删掉，但文件不会自己消失。
            val bookIds = library.booksOfShelf(shelfId).map { it.id }
            books.removeCachedFiles(bookIds)
            library.removeShelf(shelfId)
            _message.value = "已移除「$shelfName」"
        }
    }

    /**
     * 从书架移除一本书。
     *
     * @param deleteFile 是否连手机里的原始文件一起删。删文件需要目录的写权限；
     *   老书架只持久化过读权限，这时不会动记录，而是把请求挂到
     *   [pendingPermission] 上，由 UI 引导用户重新授权后再继续。
     */
    fun removeBook(book: BookEntity, deleteFile: Boolean = false) {
        viewModelScope.launch {
            if (deleteFile) {
                when (library.deleteDocument(book)) {
                    DeleteFileResult.NO_PERMISSION -> {
                        val treeUri = library.treeUriOfShelf(book.shelfId)
                        if (treeUri == null) {
                            _message.value = "找不到这本书所属的目录，无法删除文件"
                        } else {
                            _pendingPermission.value = PendingPermission(book, treeUri)
                        }
                        return@launch
                    }

                    DeleteFileResult.FAILED -> {
                        // 文件没删掉就保留记录，否则用户连重试的入口都没有了。
                        _message.value = "删除本地文件失败，书架记录未改动"
                        return@launch
                    }

                    DeleteFileResult.DELETED -> Unit
                }
            }

            books.removeBook(book)
            _message.value = if (deleteFile) {
                "已删除「${book.title}」和本地文件"
            } else {
                "已从书架移除「${book.title}」"
            }
        }
    }

    /** 用户重新选过目录后，补授权并继续删除。 */
    fun retryDeleteWithFile(book: BookEntity, treeUri: Uri) {
        viewModelScope.launch {
            _pendingPermission.value = null
            if (!library.grantWriteAccess(treeUri)) {
                _message.value = "没有拿到写入权限，删除已取消"
                return@launch
            }
            when (library.deleteDocument(book)) {
                DeleteFileResult.DELETED -> {
                    books.removeBook(book)
                    _message.value = "已删除「${book.title}」和本地文件"
                }

                DeleteFileResult.NO_PERMISSION -> {
                    _message.value = "删除失败：请确认重新选中的是这本书所在的目录"
                }

                DeleteFileResult.FAILED -> {
                    _message.value = "删除本地文件失败，书架记录未改动"
                }
            }
        }
    }

    fun clearPendingPermission() {
        _pendingPermission.value = null
    }

    fun toggleShelf(shelfId: Long) {
        _expandedShelves.update { current ->
            if (shelfId in current) current - shelfId else current + shelfId
        }
    }

    /** 切换收藏。写库后 Room 的 Flow 会自动回流刷新列表，不需要手动改 UI 状态。 */
    fun toggleFavorite(book: BookEntity) {
        viewModelScope.launch {
            library.setFavorite(book.id, !book.isFavorite)
        }
    }

    fun onSearchQueryChange(value: String) {
        _searchQuery.value = value
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    fun consumeMessage() {
        _message.value = null
    }
}
