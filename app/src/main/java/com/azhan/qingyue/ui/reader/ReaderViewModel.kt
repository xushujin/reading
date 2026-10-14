package com.azhan.qingyue.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azhan.qingyue.data.local.BookEntity
import com.azhan.qingyue.data.parser.BookDocument
import com.azhan.qingyue.data.prefs.ReaderSettings
import com.azhan.qingyue.data.prefs.SettingsStore
import com.azhan.qingyue.data.repo.BookRepository
import com.azhan.qingyue.data.repo.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReaderUiState(
    val loading: Boolean = true,
    val book: BookEntity? = null,
    val document: BookDocument? = null,
    val error: String? = null,
)

class ReaderViewModel(
    private val bookId: Long,
    private val books: BookRepository,
    private val library: LibraryRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    val settings: StateFlow<ReaderSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderSettings())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }

            val entity = books.findBook(bookId)
            if (entity == null) {
                _state.update { it.copy(loading = false, error = "找不到这本书，可能已被移除") }
                return@launch
            }

            runCatching { books.loadDocument(entity) }
                .onSuccess { document ->
                    _state.update {
                        it.copy(loading = false, book = entity, document = document, error = null)
                    }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(loading = false, error = throwable.message ?: "这本书解析失败了")
                    }
                }
        }
    }

    fun saveProgress(offset: Int) {
        val document = _state.value.document ?: return
        viewModelScope.launch { books.saveProgress(bookId, offset, document.totalChars) }
    }

    fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) {
        viewModelScope.launch { settingsStore.update(transform) }
    }

    /** 保存正文中实际高亮的选区原文；偏移仅用来记录来源章节。 */
    fun addClip(offset: Int, text: String) {
        val document = _state.value.document ?: return
        val book = _state.value.book ?: return
        val paragraph = document.paragraphs
            .getOrNull(document.paragraphIndexForOffset(offset))
            ?: return

        viewModelScope.launch {
            val added = library.addClip(
                bookId = bookId,
                bookTitle = book.title,
                chapterTitle = document.chapterTitles.getOrNull(paragraph.chapterIndex),
                text = text,
                offset = offset,
            )
            _message.value = if (added) "已收藏这段文字" else "这段文字之前已经收藏过了"
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
