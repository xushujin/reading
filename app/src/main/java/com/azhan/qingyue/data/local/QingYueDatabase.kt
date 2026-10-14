package com.azhan.qingyue.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ShelfEntity::class, BookEntity::class, ClipEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class QingYueDatabase : RoomDatabase() {

    abstract fun libraryDao(): LibraryDao

    companion object {

        /**
         * v1 → v2：books 表加 `isFavorite` 列。
         *
         * 这里**刻意不用** `fallbackToDestructiveMigration()` ——
         * 它遇到版本不匹配会直接删库重建，用户的书架、阅读进度、
         * 收藏会全部丢失。宁可让缺迁移时崩溃（开发期立刻发现），
         * 也不能静默清空用户数据。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE books ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * v2 → v3：新增 `clips` 表，存收藏的文字片段。
         *
         * 建表 SQL 必须与 Room 生成的 schema 完全一致（列名、类型、NOT NULL、主键），
         * 否则校验迁移时会抛异常。核对办法：比对 `app/schemas/.../3.json`
         * 里 clips 的字段定义与这里逐项对齐。
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `clips` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`bookId` INTEGER NOT NULL, " +
                        "`bookTitle` TEXT NOT NULL, " +
                        "`chapterTitle` TEXT, " +
                        "`text` TEXT NOT NULL, " +
                        "`offset` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
            }
        }

        fun create(context: Context): QingYueDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                QingYueDatabase::class.java,
                "qingyue.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
