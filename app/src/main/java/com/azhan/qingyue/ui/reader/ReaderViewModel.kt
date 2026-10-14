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

    /**
     * 收藏包含 [offset] 的那一段文字。
     *
     * 传 offset 而不是段落下标，是因为渲染单位（翻页模式下的 slice）只带 offset；
     * 这里反查回整段再存 —— 一个段落跨页时会被切成多个 slice，
     * 直接存 slice 的文本就只摘到半段，那就失去摘录的意义了。
     */
    fun addClipAt(offset: Int) {
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
                text = paragraph.text,
                offset = paragraph.startOffset,
            )
            _message.value = if (added) "已收藏这段文字" else "这段文字之前已经收藏过了"
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
