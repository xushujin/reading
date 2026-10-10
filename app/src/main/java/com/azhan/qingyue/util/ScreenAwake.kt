package com.azhan.qingyue.util

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import com.azhan.qingyue.data.prefs.KeepAliveMode
import com.azhan.qingyue.data.prefs.ReaderSettings

/**
 * 屏幕常亮守卫。
 *
 * 需求 4 里有两个互相拉扯的诉求：
 *   1. 阅读时不能熄屏；
 *   2. 看着看着睡着了，不能亮一整夜。
 *
 * 用两条互相独立的闸门来解：
 *
 * - **无操作闸门**：手动阅读时，[ReaderSettings.idleTimeoutMinutes] 分钟内没有任何
 *   触摸就解除常亮，交还给系统正常熄屏。
 *
 * - **连续亮屏闸门**：不管手动还是自动滚屏，只要连续亮屏超过
 *   [ReaderSettings.maxContinuousMinutes] 就强制解除常亮。
 *
 * 第二条是必需的：自动滚屏时用户本来就不碰屏幕，「无操作」永远为假，
 * 只靠第一条闸门根本拦不住「开着自动滚屏睡到天亮」。
 */
@Stable
class ScreenAwakeGuard {

    /** 是否请求系统保持亮屏。 */
    var keepScreenOn by mutableStateOf(true)
        private set

    /** 连续亮屏到达上限，已经被强制解除。UI 据此提示用户。 */
    var continuousLimitReached by mutableStateOf(false)
        private set

    private var lastTouchAt = SystemClock.elapsedRealtime()
    private var sessionStartAt = SystemClock.elapsedRealtime()

    /** 进入阅读页时调用：重新开始一次「连续亮屏会话」。 */
    fun resetSession() {
        val now = SystemClock.elapsedRealtime()
        lastTouchAt = now
        sessionStartAt = now
        continuousLimitReached = false
        keepScreenOn = true
    }

    /** 每次用户触摸（滚动、点击）调用。 */
    fun onUserInteraction() {
        lastTouchAt = SystemClock.elapsedRealtime()
        if (continuousLimitReached) {
            // 用户回来了，重新计时，允许再亮一个周期。
            continuousLimitReached = false
            sessionStartAt = SystemClock.elapsedRealtime()
        }
    }

    /** 周期性求值。建议 2 秒一次 —— 精度足够，开销可忽略。 */
    fun evaluate(settings: ReaderSettings, autoScrolling: Boolean) {
        val now = SystemClock.elapsedRealtime()
        val idleMs = now - lastTouchAt
        val sessionMs = now - sessionStartAt

        val maxContinuousMs = settings.maxContinuousMinutes.toLong() * 60_000L
        val idleLimitMs = settings.idleTimeoutMinutes.toLong() * 60_000L

        if (maxContinuousMs > 0L && sessionMs >= maxContinuousMs) {
            continuousLimitReached = true
        }

        keepScreenOn = when {
            continuousLimitReached -> false

            // 自动滚屏必须常亮，否则「解放双手」这个功能根本不成立。
            autoScrolling -> true

            settings.keepAliveMode == KeepAliveMode.OFF -> false
            settings.keepAliveMode == KeepAliveMode.ALWAYS -> true

            // 0 表示不限制无操作时长。
            idleLimitMs <= 0L -> true

            else -> idleMs < idleLimitMs
        }
    }
}

@Composable
fun rememberScreenAwakeGuard(): ScreenAwakeGuard = remember { ScreenAwakeGuard() }

/**
 * 把守卫的结论落到 View 上。`View.keepScreenOn` 等价于
 * `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON`，不需要 WAKE_LOCK 权限。
 */
@Composable
fun ScreenAwakeEffect(keepScreenOn: Boolean) {
    val view = LocalView.current
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
}
