package com.azhan.qingyue.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azhan.qingyue.data.local.BookEntity
import com.azhan.qingyue.data.local.ClipEntity
import com.azhan.qingyue.data.repo.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 收藏页的两个分类。 */
enum class FavoriteTab(val label: String) {
    CLIPS("文字片段"),
    BOOKS("电子书"),
}

/**
 * 收藏页。
 *
 * 片段和电子书是两种东西，但都属于「我特意留着的」，
 * 所以合在一个页面用 Tab 分开，而不是各做一个入口。
 */
class FavoritesViewModel(
    private val library: LibraryRepository,
) : ViewModel() {

    /** 文字片段，按收藏时间倒序（最近摘的排最前）。 */
    val clips: StateFlow<List<ClipEntity>> = library.observeClips()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 收藏的电子书。与首页「收藏」分组同源，只是这里全列出来。 */
    val favoriteBooks: StateFlow<List<BookEntity>> = library.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _tab = MutableStateFlow(FavoriteTab.CLIPS)
    val tab: StateFlow<FavoriteTab> = _tab.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun selectTab(value: FavoriteTab) {
        _tab.value = value
    }

    fun removeClip(clip: ClipEntity) {
        viewModelScope.launch {
            library.removeClip(clip.id)
            _message.value = "已删除这条摘录"
        }
    }

    /** 取消收藏某本书（收藏页里点星标）。 */
    fun toggleFavorite(book: BookEntity) {
        viewModelScope.launch {
            library.setFavorite(book.id, !book.isFavorite)
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
