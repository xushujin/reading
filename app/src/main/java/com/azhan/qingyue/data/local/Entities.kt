package com.azhan.qingyue.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** 书架：对应一个用户添加的本地文件夹。 */
@Entity(tableName = "shelves")
data class ShelfEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** 文件夹名，直接作为列表分组标题。 */
    val name: String,
    /** SAF 持久化授权的 tree uri。 */
    val treeUri: String,
    val addedAt: Long = System.currentTimeMillis(),
    val lastScanAt: Long = 0L,
)

/**
 * 书籍。documentUri 全局唯一 —— 同一文件被父子目录同时覆盖时只入库一次，
 * 归属首次添加的那个书架，避免重复条目和进度分裂。
 */
@Entity(
    tableName = "books",
    foreignKeys = [
        ForeignKey(
            entity = ShelfEntity::class,
            parentColumns = ["id"],
            childColumns = ["shelfId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("shelfId"),
        Index(value = ["documentUri"], unique = true),
    ],
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val shelfId: Long,
    val documentUri: String,
    val fileName: String,
    val title: String,
    val author: String? = null,
    /** "txt" / "epub" */
    val format: String,
    /** 封面缓存的本地绝对路径，TXT 一般为 null。 */
    val coverPath: String? = null,
    val charCount: Long = 0L,
    val addedAt: Long = System.currentTimeMillis(),
    val lastReadAt: Long = 0L,
    /** 0f..1f */
    val progress: Float = 0f,
    /** 上次阅读位置，以正文字符偏移表示，与字号/行距无关，换设备也准。 */
    val lastOffset: Int = 0,
    val finished: Boolean = false,
    /** 元数据是否已解析（EPUB 需要解包才能拿到书名/作者/封面）。 */
    val metaLoaded: Boolean = false,
    /**
     * 是否收藏。收藏的书会在首页「收藏」分组里单独列一份。
     *
     * `defaultValue` 必须显式声明且与 MIGRATION_1_2 的 SQL 一致 ——
     * `ALTER TABLE ADD COLUMN` 对 NOT NULL 列必须带 DEFAULT，而 Room 校验迁移时
     * 会比对实体 schema 的默认值。两边不一致会在升级时抛 IllegalStateException。
     */
    @ColumnInfo(defaultValue = "0")
    val isFavorite: Boolean = false,
)

/** 书架分组查询结果。 */
data class ShelfWithBooks(
    @Embedded val shelf: ShelfEntity,
    @Relation(parentColumn = "id", entityColumn = "shelfId")
    val books: List<BookEntity>,
)

/**
 * 收藏的文字片段。
 *
 * **刻意不设到 books 的外键**：片段本身是有价值的摘录，用户把书从书架移除
 * （比如换了个目录重新添加）不该让摘录跟着消失。所以 [bookId] 只作为「能跳回去」
 * 的线索，另外冗余一份 [bookTitle] —— 书没了也还能看清这段是从哪本书摘的。
 *
 * [offset] 是片段起始处在这本书里的正文字符偏移，与阅读进度同一套坐标系，
 * 因此换字号、换设备后依然能准确定位。
 */
@Entity(tableName = "clips")
data class ClipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val bookId: Long,
    val bookTitle: String,
    val chapterTitle: String? = null,
    val text: String,
    val offset: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)
