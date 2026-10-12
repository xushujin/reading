package com.azhan.qingyue.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azhan.qingyue.data.prefs.ReaderTheme
import com.azhan.qingyue.ui.neu.neuInner

/**
 * 阅读主题选择器：圆形色块 + 主题名。
 *
 * 阅读页的排版面板与设置页**共用这一个组件**。
 * 两处各写一套的话，改了这边忘那边，就会出现「同一个设置在两处长得不一样」——
 * 而且这种不一致不会编译报错，只能靠人发现。
 */
@Composable
internal fun ThemeSwatch(
    theme: ReaderTheme,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    val swatch = theme.palette()

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .then(
                    if (selected) Modifier.neuInner(23.dp, palette, 3.dp) else Modifier,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(if (selected) 30.dp else 34.dp)
                    .background(swatch.background, CircleShape)
                    .then(
                        if (selected) {
                            Modifier.border(2.dp, palette.accent, CircleShape)
                        } else {
                            Modifier
                        },
                    ),
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = theme.label,
            fontSize = 11.sp,
            color = if (selected) palette.accent else palette.textSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
