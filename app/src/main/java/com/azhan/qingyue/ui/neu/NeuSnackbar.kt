package com.azhan.qingyue.ui.neu

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azhan.qingyue.ui.theme.LocalReaderPalette
import kotlinx.coroutines.delay

/**
 * 拟态提示条：**只有文字，没有底板**。
 *
 * 演进过程：Material3 默认是 inverseSurface 黑底 → 1.3.16 换成主题色不透明底板
 * （盖住正文）→ 1.3.17 半透明 → 现在按用户要求彻底去掉底板，只留加粗文字。
 *
 * 没有底板还能读，靠的是**背景色光晕**：给文字加一层和页面背景同色的阴影，
 * 字形周围就「抠」出一圈干净的底色，压在正文上也不会糊成一团。
 * 停留时间仍由自己控制（默认 1.8 秒），不等 Material 的 4 秒。
 */
@Composable
fun NeuSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    durationMillis: Long = 1_800L,
) {
    val palette = LocalReaderPalette.current
    val density = LocalDensity.current
    val haloBlur = with(density) { 8.dp.toPx() }

    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        LaunchedEffect(data) {
            delay(durationMillis)
            data.dismiss()
        }

        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text(
                text = data.visuals.message,
                color = palette.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(
                    shadow = Shadow(
                        color = palette.background,
                        offset = Offset.Zero,
                        blurRadius = haloBlur,
                    ),
                ),
            )
        }
    }
}
