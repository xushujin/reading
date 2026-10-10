package com.azhan.qingyue.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.azhan.qingyue.AppContainer
import com.azhan.qingyue.data.prefs.ReaderSettings
import com.azhan.qingyue.ui.bookshelf.BookshelfScreen
import com.azhan.qingyue.ui.reader.ReaderScreen
import com.azhan.qingyue.ui.settings.SettingsScreen
import com.azhan.qingyue.ui.theme.QingYueTheme
import com.azhan.qingyue.ui.theme.shellPalette

object Routes {
    const val SHELF = "shelf"
    const val SETTINGS = "settings"
    const val ARG_BOOK_ID = "bookId"
    const val READER = "reader/{$ARG_BOOK_ID}"

    fun reader(bookId: Long): String = "reader/$bookId"
}

@Composable
fun QingYueRoot(container: AppContainer) {
    val settings by container.settingsStore.settings.collectAsState(initial = ReaderSettings())
    val navController = rememberNavController()

    QingYueTheme(palette = settings.theme.shellPalette) {
        NavHost(navController = navController, startDestination = Routes.SHELF) {
            composable(Routes.SHELF) {
                BookshelfScreen(
                    container = container,
                    onOpenBook = { bookId -> navController.navigate(Routes.reader(bookId)) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }

            composable(
                route = Routes.READER,
                arguments = listOf(navArgument(Routes.ARG_BOOK_ID) { type = NavType.LongType }),
            ) { entry ->
                ReaderScreen(
                    container = container,
                    bookId = entry.arguments?.getLong(Routes.ARG_BOOK_ID) ?: -1L,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
