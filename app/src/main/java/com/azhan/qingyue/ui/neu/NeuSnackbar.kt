package com.azhan.qingyue.ui.neu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azhan.qingyue.ui.theme.LocalReaderPalette
import kotlinx.coroutines.delay

/**
 * 拟态提示条（半透明）。
 *
 * 两件事要同时解决：
 *
 * 1. Material3 的 Snackbar 默认用 `inverseSurface` —— 浅色主题下是一块黑底白字，
 *    跟这个「元素与背景同色」的界面完全不搭。
 * 2. 默认还要停 4 秒。阅读时提示只是确认「已锁定 / 已解锁」，4 秒足够读三遍，
 *    而它一直盖在文字上。
 *
 * 所以这里改成：**主题背景色 + 低透明度**（底下的文字仍透出来），不画阴影
 * （半透明面板叠阴影会糊），只用一条细边勾轮廓；[durationMillis] 到点自动收起，
 * 比系统默认短得多。
 */
@Composable
fun NeuSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    durationMillis: Long = 1_800L,
) {
    val palette = LocalReaderPalette.current
    val shape = RoundedCornerShape(16.dp)

    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        // 自己计时收起，不等 Material 的 4 秒
        LaunchedEffect(data) {
            delay(durationMillis)
            data.dismiss()
        }

        Box(
            modifier = Modifier
                .background(palette.background.copy(alpha = 0.62f), shape)
                .border(0.5.dp, palette.shadow.copy(alpha = 0.32f), shape)
                .padding(horizontal = 16.dp, vertical = 9.dp),
        ) {
            Text(
                text = data.visuals.message,
                color = palette.textPrimary,
                fontSize = 12.sp,
            )
        }
    }
}
