package com.azhan.qingyue.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ShelfEntity::class, BookEntity::class],
    version = 2,
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

        fun create(context: Context): QingYueDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                QingYueDatabase::class.java,
                "qingyue.db",
            )
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
