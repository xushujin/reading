package com.azhan.qingyue.ui.neu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.azhan.qingyue.ui.theme.LocalReaderPalette

/**
 * 换过皮的 DropdownMenu。
 *
 * Material3 默认的菜单容器是自己的 surface 色 + 4dp 圆角 + tonal 叠色，
 * 在羊皮纸 / 护眼绿这些主题里弹出来一眼就是「系统的」。
 * 这里把容器换成主题 surface、圆角放大、tonal 归零，再加一条和封面同款的细边框，
 * 让它至少和页面是同一种材质。菜单项的文字色走 MaterialTheme 的 onSurface，
 * 而 `QingYueTheme` 已经把 onSurface 映射成了 palette.textPrimary，所以不用单独处理。
 */
@Composable
fun NeuDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalReaderPalette.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        containerColor = palette.surface,
        tonalElevation = 0.dp,
        shadowElevation = 14.dp,
        border = BorderStroke(0.5.dp, palette.shadow.copy(alpha = 0.28f)),
        content = content,
    )
}
