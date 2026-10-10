package com.azhan.qingyue.ui.bookshelf

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azhan.qingyue.data.local.BookEntity
import com.azhan.qingyue.data.local.ShelfWithBooks
import com.azhan.qingyue.data.repo.BookRepository
import com.azhan.qingyue.data.repo.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

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
            library.removeShelf(shelfId)
            _message.value = "已移除「$shelfName」"
        }
    }

    fun toggleShelf(shelfId: Long) {
        _expandedShelves.update { current ->
            if (shelfId in current) current - shelfId else current + shelfId
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
