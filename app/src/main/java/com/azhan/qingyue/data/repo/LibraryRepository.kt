package com.azhan.qingyue.data.repo

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.azhan.qingyue.data.local.BookEntity
import com.azhan.qingyue.data.local.ClipEntity
import com.azhan.qingyue.data.local.LibraryDao
import com.azhan.qingyue.data.local.ShelfEntity
import com.azhan.qingyue.data.local.ShelfWithBooks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class AddShelfResult(
    val shelfId: Long,
    val shelfName: String,
    val addedCount: Int,
    val alreadyExists: Boolean = false,
    val error: String? = null,
) {
    val isSuccess: Boolean get() = error == null
}

/**
 * 书架与扫描。
 *
 * 目录授权走 SAF（ACTION_OPEN_DOCUMENT_TREE）并 takePersistableUriPermission，
 * 因此不需要任何存储权限，重启后依然可读。
 */
class LibraryRepository(
    private val context: Context,
    private val dao: LibraryDao,
) {

    /**
     * 书架列表：组内按「最后阅读时间」倒序 —— 最近读过的排最前，
     * 没读过的（lastReadAt = 0）按书名排在后面。
     * Room 的 @Relation 本身不带排序能力，所以在这一层补。
     */
    fun observeShelves(): Flow<List<ShelfWithBooks>> =
        dao.observeShelvesWithBooks().map { shelves ->
            shelves.map { shelf -> shelf.copy(books = shelf.books.sortedWith(BOOK_ORDER)) }
        }

    /** 首页「最近阅读」只展示最近 3 本，多了会把纵向空间挤掉。 */
    fun observeRecent(limit: Int = 3): Flow<List<BookEntity>> = dao.observeRecentBooks(limit)

    /** 收藏列表。收藏是跨书架的标记，所以这里单独查而不是按 shelf 分组。 */
    fun observeFavorites(): Flow<List<BookEntity>> = dao.observeFavoriteBooks()

    suspend fun setFavorite(bookId: Long, favorite: Boolean) = withContext(Dispatchers.IO) {
        dao.setFavorite(bookId, favorite)
    }

    // ---------- 文字片段收藏 ----------

    fun observeClips(): Flow<List<ClipEntity>> = dao.observeClips()

    /**
     * 收藏一段文字。
     *
     * @return false 表示这段文字之前已经收藏过（同书同内容），不重复入库。
     */
    suspend fun addClip(
        bookId: Long,
        bookTitle: String,
        chapterTitle: String?,
        text: String,
        offset: Int,
    ): Boolean = withContext(Dispatchers.IO) {
        // 持久化必须保留用户圈选的原文；trim 会让列表与高亮不完全一致。
        if (text.isBlank()) return@withContext false
        if (dao.countClip(bookId, text) > 0) return@withContext false
        dao.insertClip(
            ClipEntity(
                bookId = bookId,
                bookTitle = bookTitle,
                chapterTitle = chapterTitle,
                text = text,
                offset = offset,
            ),
        )
        true
    }

    suspend fun removeClip(clipId: Long) = withContext(Dispatchers.IO) {
        dao.deleteClip(clipId)
    }

    /** 添加一个本地文件夹为书架。重复添加同一目录会直接重扫而不是建第二条记录。 */
    suspend fun addShelf(treeUri: Uri): AddShelfResult = withContext(Dispatchers.IO) {
        val uriString = treeUri.toString()

        val existing = dao.findShelfByUri(uriString)
        if (existing != null) {
            val added = scanShelf(existing.id, treeUri)
            return@withContext AddShelfResult(
                shelfId = existing.id,
                shelfName = existing.name,
                addedCount = added,
                alreadyExists = true,
            )
        }

        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }

        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return@withContext AddShelfResult(-1L, "", 0, error = "无法读取该目录")

        val name = root.name?.takeIf { it.isNotBlank() } ?: "未命名目录"
        val shelfId = dao.insertShelf(ShelfEntity(name = name, treeUri = uriString))
        if (shelfId == -1L) {
            return@withContext AddShelfResult(-1L, name, 0, error = "目录已存在")
        }

        val added = scanShelf(shelfId, treeUri)
        AddShelfResult(shelfId = shelfId, shelfName = name, addedCount = added)
    }

    suspend fun rescanShelf(shelfId: Long): Int = withContext(Dispatchers.IO) {
        val shelf = dao.findShelf(shelfId) ?: return@withContext 0
        scanShelf(shelfId, Uri.parse(shelf.treeUri))
    }

    suspend fun rescanAll(): Int = withContext(Dispatchers.IO) {
        var total = 0
        for (shelf in dao.allShelves()) {
            total += scanShelf(shelf.id, Uri.parse(shelf.treeUri))
        }
        total
    }

    suspend fun removeShelf(shelfId: Long) = withContext(Dispatchers.IO) {
        val shelf = dao.findShelf(shelfId)
        // 外键 CASCADE 会一并清掉该书架下的书籍记录。
        dao.deleteShelf(shelfId)
        shelf?.let {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(it.treeUri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }

    /**
     * 扫描目录下的电子书并入库。已存在的书（按 documentUri 判定）跳过，
     * 从而保留阅读进度不被覆盖。
     */
    private suspend fun scanShelf(shelfId: Long, treeUri: Uri): Int {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return 0

        val found = ArrayList<DocumentFile>(64)
        walk(root, 0, found)

        val known = dao.allBookUris().toHashSet()
        val fresh = ArrayList<BookEntity>(found.size)
        for (file in found) {
            val uri = file.uri.toString()
            if (uri in known) continue
            val fileName = file.name ?: continue
            fresh += BookEntity(
                shelfId = shelfId,
                documentUri = uri,
                fileName = fileName,
                title = titleFromFileName(fileName),
                format = formatOf(fileName),
            )
        }
        if (fresh.isNotEmpty()) dao.insertBooks(fresh)
        dao.touchShelf(shelfId, System.currentTimeMillis())
        return fresh.size
    }

    private fun walk(dir: DocumentFile, depth: Int, out: MutableList<DocumentFile>) {
        if (depth > MAX_SCAN_DEPTH) return
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return
        for (child in children) {
            val name = child.name ?: continue
            if (name.startsWith('.')) continue
            if (child.isDirectory) {
                walk(child, depth + 1, out)
            } else if (isSupported(name)) {
                out += child
            }
        }
    }

    companion object {
        private const val MAX_SCAN_DEPTH = 8

        /** 组内排序：最近阅读的排最前；没读过的按书名排。 */
        private val BOOK_ORDER = compareByDescending<BookEntity> { it.lastReadAt }
            .thenBy { it.title }

        val SUPPORTED_EXTENSIONS = setOf("txt", "epub", "mobi", "md", "markdown")

        fun formatOf(fileName: String): String =
            fileName.substringAfterLast('.', "").lowercase()

        fun isSupported(fileName: String): Boolean =
            formatOf(fileName) in SUPPORTED_EXTENSIONS

        fun titleFromFileName(fileName: String): String =
            fileName.substringBeforeLast('.').trim().ifBlank { fileName }
    }
}
