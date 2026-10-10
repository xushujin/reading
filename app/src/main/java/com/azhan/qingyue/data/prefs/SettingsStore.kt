package com.azhan.qingyue.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "qingyue_settings")

/** 阅读配色。五种足够覆盖白天/夜间/护眼三类诉求，不做无限调色盘。 */
enum class ReaderTheme(val label: String) {
    PAPER("羊皮纸"),
    DAY("白天"),
    GREEN("护眼绿"),
    NIGHT("夜间"),
    BLACK("纯黑"),
}

/**
 * 阅读方式。
 *
 * - [SCROLL] 竖向连续滚动，配自动滚屏。
 * - [PAGE]   左右翻页，正文按屏幕尺寸预分页。
 */
enum class ReadMode(val label: String) {
    SCROLL("滚动"),
    PAGE("翻页"),
}

/**
 * 屏幕常亮策略。
 *
 * - [OFF]    不干预，交给系统。
 * - [SMART]  手动阅读时，[ReaderSettings.idleTimeoutMinutes] 分钟内无触摸就解除常亮，
 *            让系统正常熄屏 —— 防止「看着看着睡着，屏幕亮一整夜」。
 * - [ALWAYS] 只要停在阅读页就一直亮，仅受 [ReaderSettings.maxContinuousMinutes] 硬上限约束。
 */
enum class KeepAliveMode(val label: String) {
    OFF("关闭"),
    SMART("智能（推荐）"),
    ALWAYS("始终常亮"),
}

data class ReaderSettings(
    val theme: ReaderTheme = ReaderTheme.PAPER,
    val readMode: ReadMode = ReadMode.SCROLL,
    val fontSizeSp: Float = 17f,
    val lineHeightMultiplier: Float = 1.75f,
    val paragraphSpacingDp: Float = 14f,
    val horizontalPaddingDp: Float = 22f,
    /** 自动滚屏 / 自动翻页速度倍率，0.3x ~ 7.0x。 */
    val autoScrollSpeed: Float = 1.0f,
    val keepAliveMode: KeepAliveMode = KeepAliveMode.SMART,
    /** 无操作超时（分钟），0 表示不限制。 */
    val idleTimeoutMinutes: Int = 10,
    /** 连续亮屏硬上限（分钟），0 表示不限制。自动滚屏时同样受此约束。 */
    val maxContinuousMinutes: Int = 30,
)

/**
 * 阅读设置的持久化。刻意用「整体读写」而不是逐字段 setter：
 * 调用方只描述「我要变成什么样」，避免状态在多处拼装。
 */
class SettingsStore(context: Context) {

    private val store = context.applicationContext.settingsDataStore

    val settings: Flow<ReaderSettings> = store.data.map { it.toSettings() }

    suspend fun update(transform: (ReaderSettings) -> ReaderSettings) {
        store.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[KEY_THEME] = next.theme.name
            prefs[KEY_READ_MODE] = next.readMode.name
            prefs[KEY_FONT_SIZE] = next.fontSizeSp
            prefs[KEY_LINE_HEIGHT] = next.lineHeightMultiplier
            prefs[KEY_PARAGRAPH_SPACING] = next.paragraphSpacingDp
            prefs[KEY_H_PADDING] = next.horizontalPaddingDp
            prefs[KEY_AUTO_SCROLL_SPEED] = next.autoScrollSpeed
            prefs[KEY_KEEP_ALIVE_MODE] = next.keepAliveMode.name
            prefs[KEY_IDLE_TIMEOUT] = next.idleTimeoutMinutes
            prefs[KEY_MAX_CONTINUOUS] = next.maxContinuousMinutes
        }
    }

    private fun Preferences.toSettings() = ReaderSettings(
        theme = this[KEY_THEME]?.let { runCatching { ReaderTheme.valueOf(it) }.getOrNull() }
            ?: ReaderTheme.PAPER,
        readMode = this[KEY_READ_MODE]?.let { runCatching { ReadMode.valueOf(it) }.getOrNull() }
            ?: ReadMode.SCROLL,
        fontSizeSp = this[KEY_FONT_SIZE] ?: 17f,
        lineHeightMultiplier = this[KEY_LINE_HEIGHT] ?: 1.75f,
        paragraphSpacingDp = this[KEY_PARAGRAPH_SPACING] ?: 14f,
        horizontalPaddingDp = this[KEY_H_PADDING] ?: 22f,
        autoScrollSpeed = this[KEY_AUTO_SCROLL_SPEED] ?: 1.0f,
        keepAliveMode = this[KEY_KEEP_ALIVE_MODE]?.let {
            runCatching { KeepAliveMode.valueOf(it) }.getOrNull()
        } ?: KeepAliveMode.SMART,
        idleTimeoutMinutes = this[KEY_IDLE_TIMEOUT] ?: 10,
        maxContinuousMinutes = this[KEY_MAX_CONTINUOUS] ?: 30,
    )

    private companion object {
        val KEY_THEME = stringPreferencesKey("reader_theme")
        val KEY_READ_MODE = stringPreferencesKey("read_mode")
        val KEY_FONT_SIZE = floatPreferencesKey("font_size_sp")
        val KEY_LINE_HEIGHT = floatPreferencesKey("line_height")
        val KEY_PARAGRAPH_SPACING = floatPreferencesKey("paragraph_spacing")
        val KEY_H_PADDING = floatPreferencesKey("horizontal_padding")
        val KEY_AUTO_SCROLL_SPEED = floatPreferencesKey("auto_scroll_speed")
        val KEY_KEEP_ALIVE_MODE = stringPreferencesKey("keep_alive_mode")
        val KEY_IDLE_TIMEOUT = intPreferencesKey("idle_timeout_minutes")
        val KEY_MAX_CONTINUOUS = intPreferencesKey("max_continuous_minutes")
    }
}
