package com.azhan.qingyue.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {

    @Transaction
    @Query("SELECT * FROM shelves ORDER BY addedAt ASC")
    fun observeShelvesWithBooks(): Flow<List<ShelfWithBooks>>

    @Query("SELECT * FROM books WHERE lastReadAt > 0 ORDER BY lastReadAt DESC LIMIT :limit")
    fun observeRecentBooks(limit: Int): Flow<List<BookEntity>>

    /** 收藏列表：读过的按最近阅读排前面，没读过的按书名。 */
    @Query("SELECT * FROM books WHERE isFavorite = 1 ORDER BY lastReadAt DESC, title ASC")
    fun observeFavoriteBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeBook(id: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun findBook(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE documentUri = :uri LIMIT 1")
    suspend fun findBookByUri(uri: String): BookEntity?

    @Query("SELECT * FROM books WHERE metaLoaded = 0 AND format = :format")
    suspend fun findBooksPendingMeta(format: String): List<BookEntity>

    @Query("SELECT * FROM shelves WHERE treeUri = :uri LIMIT 1")
    suspend fun findShelfByUri(uri: String): ShelfEntity?

    @Query("SELECT * FROM shelves WHERE id = :id")
    suspend fun findShelf(id: Long): ShelfEntity?

    @Query("SELECT * FROM shelves ORDER BY addedAt ASC")
    suspend fun allShelves(): List<ShelfEntity>

    @Query("SELECT documentUri FROM books")
    suspend fun allBookUris(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertShelf(shelf: ShelfEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBooks(books: List<BookEntity>): List<Long>

    @Update
    suspend fun updateBook(book: BookEntity)

    @Query(
        "UPDATE books SET title = :title, author = :author, charCount = :charCount, " +
            "coverPath = :coverPath, metaLoaded = 1 WHERE id = :id"
    )
    suspend fun updateBookMeta(
        id: Long,
        title: String,
        author: String?,
        charCount: Long,
        coverPath: String?,
    )

    @Query(
        "UPDATE books SET progress = :progress, lastOffset = :offset, " +
            "lastReadAt = :readAt, finished = :finished WHERE id = :id"
    )
    suspend fun updateProgress(
        id: Long,
        progress: Float,
        offset: Int,
        readAt: Long,
        finished: Boolean,
    )

    @Query("UPDATE shelves SET lastScanAt = :at WHERE id = :id")
    suspend fun touchShelf(id: Long, at: Long)

    @Query("UPDATE books SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("DELETE FROM shelves WHERE id = :id")
    suspend fun deleteShelf(id: Long)

    // ---------- 文字片段收藏 ----------

    @Query("SELECT * FROM clips ORDER BY createdAt DESC")
    fun observeClips(): Flow<List<ClipEntity>>

    @Query("SELECT COUNT(*) FROM clips WHERE bookId = :bookId AND text = :text")
    suspend fun countClip(bookId: Long, text: String): Int

    @Insert
    suspend fun insertClip(clip: ClipEntity): Long

    @Query("DELETE FROM clips WHERE id = :id")
    suspend fun deleteClip(id: Long)

    @Query("DELETE FROM clips WHERE bookId = :bookId")
    suspend fun deleteClipsOfBook(bookId: Long)
}
