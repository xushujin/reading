package com.azhan.qingyue.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
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
 * 解析结果做「内存 LRU + 磁盘缓存」两级：内存缓存救的是翻回来看过的书，
 * 磁盘缓存救的是冷启动 —— 大书（尤其 MOBI）解析要几秒，序列化下来二次打开
 * 只剩一次反序列化。缓存有效性靠源文件指纹（size + lastModified）校验，
 * 文件被替换、重新添加都会导致指纹变化而重新解析，不会读出错版内容。
 */
class BookRepository(
    private val context: Context,
    private val dao: LibraryDao,
) {

    private val coverDir: File = File(context.filesDir, "covers").apply { mkdirs() }
    private val diskCache = DocumentDiskCache(File(context.filesDir, "doccache"))

    private val documentCache = object : LruCache<Long, BookDocument>(MAX_CACHED_BOOKS) {}

    fun observeBook(bookId: Long): Flow<BookEntity?> = dao.observeBook(bookId)

    suspend fun findBook(bookId: Long): BookEntity? = dao.findBook(bookId)

    suspend fun loadDocument(book: BookEntity): BookDocument = withContext(Dispatchers.IO) {
        // 大书解析是秒到十几秒的 CPU 大户，降到后台优先级 —— 发热小一点，
        // 前台手势仍然跟手。快取命中时什么都不做，没有代价。
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
        try {
            loadDocumentInternal(book)
        } finally {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_DEFAULT)
        }
    }

    private suspend fun loadDocumentInternal(book: BookEntity): BookDocument =
        withContext(Dispatchers.IO) {
            documentCache.get(book.id)?.let { return@withContext it }

            val stamp = querySourceStamp(Uri.parse(book.documentUri))
            if (stamp != null) {
                diskCache.load(book.id, stamp)?.let { cached ->
                    documentCache.put(book.id, cached)
                    return@withContext cached
                }
            }

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
            stamp?.let { diskCache.save(book.id, document, it) }
            document
        }

    /**
     * 查源文件指纹（size + lastModified）。一次单行 ContentResolver 查询，
     * 比把几 MB 的原文读一遍便宜得多。
     *
     * `COLUMN_LAST_MODIFIED` 不是所有 provider 都认，失败就退回只查 SIZE；
     * 两个都拿不到就不缓存 —— 没法验证新鲜度的缓存宁可用。
     */
    private fun querySourceStamp(uri: Uri): SourceStamp? = runCatching {
        val resolver = context.contentResolver

        fun read(projection: Array<String>): SourceStamp? =
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                val modifiedIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                SourceStamp(
                    size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else -1L,
                    lastModified = if (modifiedIdx >= 0 && !cursor.isNull(modifiedIdx)) {
                        cursor.getLong(modifiedIdx)
                    } else {
                        -1L
                    },
                )
            }

        // LAST_MODIFIED 不是所有 provider 都认，抛错就退回只查 SIZE
        runCatching {
            read(arrayOf(OpenableColumns.SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED))
        }.getOrNull() ?: read(arrayOf(OpenableColumns.SIZE))
    }.getOrNull()?.takeIf { it.size >= 0 || it.lastModified >= 0 }

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
        diskCache.remove(bookId)
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
            diskCache.remove(id)
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
