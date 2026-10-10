package com.azhan.qingyue.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.azhan.qingyue.data.prefs.ReaderTheme

/**
 * 一套配色。
 *
 * 拟态（Neumorphism）的关键约束：元素与背景同色，只靠「左上受光 + 右下背光」
 * 两个方向的阴影塑造体积。所以这里必须同时给出 highlight / shadow 两色，
 * 而不是普通 UI 那样只给一个 elevation。
 */
data class ReaderPalette(
    val background: Color,
    val surface: Color,
    /** 左上高光色，用于外阴影的亮侧与内阴影的亮侧。 */
    val highlight: Color,
    /** 右下背光色。 */
    val shadow: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val divider: Color,
    val isDark: Boolean,
)

fun ReaderTheme.palette(): ReaderPalette = when (this) {
    ReaderTheme.DAY -> ReaderPalette(
        background = Color(0xFFF0F1F3),
        surface = Color(0xFFF0F1F3),
        highlight = Color(0xFFFFFFFF),
        shadow = Color(0xFFC4C9D0),
        textPrimary = Color(0xFF2B2F36),
        textSecondary = Color(0xFF878E98),
        accent = Color(0xFF6B7A8F),
        divider = Color(0xFFDDE0E4),
        isDark = false,
    )

    ReaderTheme.PAPER -> ReaderPalette(
        background = Color(0xFFEFE7D8),
        surface = Color(0xFFEFE7D8),
        highlight = Color(0xFFFFFCF5),
        shadow = Color(0xFFC5B79E),
        textPrimary = Color(0xFF3A342A),
        textSecondary = Color(0xFF8B8070),
        accent = Color(0xFF8A7A5C),
        divider = Color(0xFFDBD1BD),
        isDark = false,
    )

    ReaderTheme.GREEN -> ReaderPalette(
        background = Color(0xFFCCE8CF),
        surface = Color(0xFFCCE8CF),
        highlight = Color(0xFFE9F8EB),
        shadow = Color(0xFFA3C2A8),
        textPrimary = Color(0xFF24352A),
        textSecondary = Color(0xFF68806E),
        accent = Color(0xFF4E7A58),
        divider = Color(0xFFB5D2B9),
        isDark = false,
    )

    ReaderTheme.NIGHT -> ReaderPalette(
        background = Color(0xFF20242B),
        surface = Color(0xFF20242B),
        highlight = Color(0xFF303742),
        shadow = Color(0xFF12151A),
        textPrimary = Color(0xFFC6CCD6),
        textSecondary = Color(0xFF7B8492),
        accent = Color(0xFF8FA3BF),
        divider = Color(0xFF2A2F38),
        isDark = true,
    )

    ReaderTheme.BLACK -> ReaderPalette(
        background = Color(0xFF000000),
        surface = Color(0xFF0B0B0C),
        highlight = Color(0xFF1E1E20),
        shadow = Color(0xFF000000),
        textPrimary = Color(0xFFB0B0B0),
        textSecondary = Color(0xFF6B6B6B),
        accent = Color(0xFF7C8CA0),
        divider = Color(0xFF191919),
        isDark = true,
    )
}

/**
 * 应用外壳（书架/设置）的配色：只区分亮/暗，不把护眼绿、羊皮纸这类
 * 「阅读专用色」铺满整个 App —— 那样在书架页会显得脏。
 */
val ReaderTheme.shellPalette: ReaderPalette
    get() = if (palette().isDark) ReaderTheme.NIGHT.palette() else ReaderTheme.DAY.palette()

val LocalReaderPalette = staticCompositionLocalOf { ReaderTheme.DAY.palette() }
