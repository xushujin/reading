package com.azhan.qingyue.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ShelfEntity::class, BookEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class QingYueDatabase : RoomDatabase() {

    abstract fun libraryDao(): LibraryDao

    companion object {
        fun create(context: Context): QingYueDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                QingYueDatabase::class.java,
                "qingyue.db",
            )
                .fallbackToDestructiveMigration()
                .build()
    }
}
