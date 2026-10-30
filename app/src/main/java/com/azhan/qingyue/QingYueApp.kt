package com.azhan.qingyue

import android.app.Application
import android.content.Context
import com.azhan.qingyue.data.local.QingYueDatabase
import com.azhan.qingyue.data.local.LibraryDao
import com.azhan.qingyue.data.prefs.SettingsStore
import com.azhan.qingyue.data.repo.BookRepository
import com.azhan.qingyue.data.repo.LibraryRepository
import com.azhan.qingyue.data.update.UpdateRepository

/**
 * 极简依赖容器。刻意不引入 DI 框架 —— 这个 App 的依赖图只有四个节点，
 * 手写容器比 Hilt 更轻、构建更快。
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    private val database: QingYueDatabase by lazy { QingYueDatabase.create(appContext) }

    val libraryDao: LibraryDao get() = database.libraryDao()

    val settingsStore: SettingsStore by lazy { SettingsStore(appContext) }

    val libraryRepository: LibraryRepository by lazy {
        LibraryRepository(appContext, libraryDao)
    }

    val bookRepository: BookRepository by lazy {
        BookRepository(appContext, libraryDao)
    }

    val updateRepository: UpdateRepository by lazy {
        UpdateRepository(appContext)
    }
}

class QingYueApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
