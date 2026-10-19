package com.azhan.qingyue.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import com.azhan.qingyue.data.local.BookEntity
import com.azhan.qingyue.data.local.LibraryDao
import com.azhan.qingyue.data.parser.BookDocument
import com.azhan.qingyue.data.parser.BookDocumentBuilder
import com.azhan.qingyue.data.parser.EpubParser
import com.azhan.qingyue.data.parser.MarkdownParser
import com.azhan.qingyue.data.parser.MobiParser
import com.azhan.qingyue.data.parser.TextDecoding
import com.azhan.qingyue.data.parser.TxtParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 书籍内容加载与进度保存。
 *
 * 解析结果只做「内存 LRU + 本地封面文件」两级缓存，不落整书磁盘缓存 ——
 * 一本普通书解析在百毫秒级，换来的是不占额外存储、不会因缓存失效而读出错版内容。
 */
class BookRepository(
    private val context: Context,
    private val dao: LibraryDao,
) {

    private val coverDir: File = File(context.filesDir, "covers").apply { mkdirs() }

    private val documentCache = object : LruCache<Long, BookDocument>(MAX_CACHED_BOOKS) {}

    fun observeBook(bookId: Long): Flow<BookEntity?> = dao.observeBook(bookId)

    suspend fun findBook(bookId: Long): BookEntity? = dao.findBook(bookId)

    suspend fun loadDocument(book: BookEntity): BookDocument = withContext(Dispatchers.IO) {
        documentCache.get(book.id)?.let { return@withContext it }

        val bytes = readBytes(Uri.parse(book.documentUri), book.fileName)
        val content = when (book.format.lowercase()) {
            "epub" -> EpubParser.parse(bytes, book.title)
            "mobi" -> MobiParser.parse(bytes, book.title)
            "md", "markdown" -> MarkdownParser.parse(
                TextDecoding.decode(bytes),
                book.title,
            )

            else -> TxtParser.parse(bytes, book.title)
        }

        val document = BookDocumentBuilder.build(content)

        val coverPath = content.coverBytes?.let { saveCover(book.id, it) } ?: book.coverPath
        dao.updateBookMeta(
            id = book.id,
            title = content.title.ifBlank { book.title },
            author = content.author ?: book.author,
            charCount = document.totalChars.toLong(),
            coverPath = coverPath,
        )

        documentCache.put(book.id, document)
        document
    }

    suspend fun saveProgress(bookId: Long, offset: Int, totalChars: Int) =
        withContext(Dispatchers.IO) {
            val progress = if (totalChars <= 0) {
                0f
            } else {
                (offset.toFloat() / totalChars).coerceIn(0f, 1f)
            }
            dao.updateProgress(
                id = bookId,
                progress = progress,
                offset = offset,
                readAt = System.currentTimeMillis(),
                finished = progress >= 0.999f,
            )
        }

    /**
     * 扫描完成后后台逐本补全 EPUB 元数据（书名/作者/封面）。
     * 串行 + yield，避免一次解包几十本书把 IO 打满。
     */
    suspend fun backfillPendingMeta() = withContext(Dispatchers.IO) {
        for (book in dao.findBooksPendingMeta("epub")) {
            runCatching {
                val bytes = readBytes(Uri.parse(book.documentUri), book.fileName)
                val content = EpubParser.parse(bytes, book.title)
                val coverPath = content.coverBytes?.let { saveCover(book.id, it) } ?: book.coverPath
                dao.updateBookMeta(
                    id = book.id,
                    title = content.title.ifBlank { book.title },
                    author = content.author ?: book.author,
                    charCount = book.charCount,
                    coverPath = coverPath,
                )
            }
            yield()
        }
    }

    fun invalidate(bookId: Long) {
        documentCache.remove(bookId)
        runCatching { File(coverDir, "cover_$bookId.jpg").delete() }
    }

    /**
     * 从书架移除一本书：清掉内存缓存与封面文件，再删数据库记录。
     *
     * 顺序不能反 —— 先删记录的话就拿不到 coverPath 之外的信息，
     * 而且中途失败会留下一份「查不到书、封面文件还在」的孤儿文件。
     * 原始电子书文件不在这里处理（SAF 只授了读权限，也不该由阅读器删）。
     */
    suspend fun removeBook(book: BookEntity) = withContext(Dispatchers.IO) {
        invalidate(book.id)
        dao.deleteBook(book.id)
    }

    /** 批量清理缓存文件（移除整个书架时用，记录由外键 CASCADE 负责删）。 */
    suspend fun removeCachedFiles(bookIds: Collection<Long>) = withContext(Dispatchers.IO) {
        bookIds.forEach { id ->
            documentCache.remove(id)
            runCatching { File(coverDir, "cover_$id.jpg").delete() }
        }
    }

    // ---------------------------------------------------------------- io

    private fun readBytes(uri: Uri, fileName: String): ByteArray {
        val stream = context.contentResolver.openInputStream(uri)
            ?: error("无法打开文件：$fileName")
        return stream.use { input ->
            val out = ByteArrayOutputStream()
            val chunk = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(chunk)
                if (read < 0) break
                total += read
                if (total > MAX_FILE_BYTES) {
                    error("文件超过 ${MAX_FILE_BYTES / 1024 / 1024}MB，暂不支持")
                }
                out.write(chunk, 0, read)
            }
            out.toByteArray()
        }
    }

    private fun saveCover(bookId: Long, bytes: ByteArray): String? =
        runCatching { decodeCover(bookId, bytes) }.getOrNull()

    private fun decodeCover(bookId: Long, bytes: ByteArray): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, TARGET_COVER_PX)
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        return try {
            val file = File(coverDir, "cover_$bookId.jpg")
            file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out) }
            file.absolutePath
        } finally {
            bitmap.recycle()
        }
    }

    private fun sampleSizeFor(width: Int, height: Int, target: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= target && h / 2 >= target) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    private companion object {
        const val MAX_CACHED_BOOKS = 3
        const val MAX_FILE_BYTES = 64L * 1024 * 1024
        const val TARGET_COVER_PX = 480
    }
}
