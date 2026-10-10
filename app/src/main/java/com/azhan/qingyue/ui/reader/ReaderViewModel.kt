package com.azhan.qingyue.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azhan.qingyue.data.local.BookEntity
import com.azhan.qingyue.data.parser.BookDocument
import com.azhan.qingyue.data.prefs.ReaderSettings
import com.azhan.qingyue.data.prefs.SettingsStore
import com.azhan.qingyue.data.repo.BookRepository
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
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    val settings: StateFlow<ReaderSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderSettings())

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
}
