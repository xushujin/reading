package com.azhan.qingyue.ui.neu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.azhan.qingyue.ui.theme.LocalReaderPalette

/** 危险操作按钮的底色。五套主题都用同一个暗红，语义比主题色更重要。 */
val DestructiveColor = Color(0xFFB4453A)

/**
 * 拟态确认框。
 *
 * 项目里原先用的是 Material3 的 `AlertDialog` —— 圆角、字号、按钮都是另一套语言，
 * 放在这个满是凹槽和双向阴影的界面里像外挂的。这里用 [NeuSurface] 重做，
 * 保证弹窗和页面是同一套材质。
 *
 * 遮罩用「主题背景色 + 透明度」而不是纯黑，深色主题下不会糊成一片。
 *
 * @param extra 正文与按钮之间的自定义区域（比如「同时删除本地文件」的开关行）。
 */
@Composable
fun NeuDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "取消",
    destructive: Boolean = false,
    /** 左下按钮的行为。默认和点遮罩/返回键一样只是关掉弹窗。 */
    dismissAction: (() -> Unit)? = null,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val palette = LocalReaderPalette.current

    // 关掉窗口自带的 dim，遮罩完全由下面这层自己画 —— 否则系统 dim 叠上自绘遮罩，
    // 深色主题下会黑成一片，浅色主题下又脏又灰。
    val view = LocalView.current
    SideEffect {
        (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (palette.isDark) 0.58f else 0.32f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            NeuSurface(
                modifier = Modifier
                    .padding(horizontal = 30.dp)
                    .widthIn(max = 360.dp)
                    .fillMaxWidth(),
                cornerRadius = 24.dp,
                elevation = 14.dp,
                // 吃掉面板内的点击，否则会冒泡到遮罩上把弹窗关掉
                onClick = {},
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textPrimary,
                    )
                    Spacer(Modifier.height(9.dp))
                    Text(
                        text = message,
                        fontSize = 13.sp,
                        color = palette.textSecondary,
                        lineHeight = 20.sp,
                    )

                    if (extra != null) {
                        Spacer(Modifier.height(14.dp))
                        extra()
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NeuButton(onClick = dismissAction ?: onDismiss) {
                            Text(
                                text = dismissLabel,
                                fontSize = 13.sp,
                                color = palette.textSecondary,
                            )
                        }
                        NeuButton(
                            onClick = onConfirm,
                            highlighted = !destructive,
                            containerColor = if (destructive) DestructiveColor else null,
                        ) {
                            Text(
                                text = confirmLabel,
                                fontSize = 13.sp,
                                // 两种确认按钮都是实心底，白字在 accent 和暗红上都够对比
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}
