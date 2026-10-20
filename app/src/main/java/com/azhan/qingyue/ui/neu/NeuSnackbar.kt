package com.azhan.qingyue.ui.neu

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azhan.qingyue.ui.theme.LocalReaderPalette

/**
 * 拟态提示条。
 *
 * Material3 的 Snackbar 默认用 `inverseSurface` —— 浅色主题下就是一块黑底白字，
 * 在这个「元素与背景同色、只靠阴影分层」的界面里格外刺眼。这里换成主题 surface
 * + 拟态阴影：看起来就是从页面里浮出来的一块，文字用 textPrimary，不反色。
 */
@Composable
fun NeuSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val palette = LocalReaderPalette.current

    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        NeuSurface(cornerRadius = 18.dp, elevation = 8.dp) {
            Text(
                text = data.visuals.message,
                color = palette.textPrimary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}
