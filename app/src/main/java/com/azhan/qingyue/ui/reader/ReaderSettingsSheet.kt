package com.azhan.qingyue.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azhan.qingyue.data.prefs.ReadMode
import com.azhan.qingyue.data.prefs.ReaderSettings
import com.azhan.qingyue.data.prefs.ReaderTheme
import com.azhan.qingyue.ui.neu.NeuButton
import com.azhan.qingyue.ui.neu.NeuSegmented
import com.azhan.qingyue.ui.neu.NeuSlider
import com.azhan.qingyue.ui.theme.LocalReaderPalette
import com.azhan.qingyue.ui.theme.ThemeSwatch
import com.azhan.qingyue.ui.theme.palette

@Composable
fun ReaderSettingsSheet(
    settings: ReaderSettings,
    onChange: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalReaderPalette.current

    PanelScaffold(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(38.dp, 4.dp)
                    .background(palette.divider, RoundedCornerShape(2.dp)),
            )

            Spacer(Modifier.height(18.dp))
            SheetLabel("阅读方式")
            Spacer(Modifier.height(10.dp))
            NeuSegmented(
                options = ReadMode.entries.toList(),
                selected = settings.readMode,
                onSelect = { mode -> onChange { it.copy(readMode = mode) } },
                modifier = Modifier.fillMaxWidth(),
                label = { it.label },
            )

            Spacer(Modifier.height(18.dp))
            SheetLabel("阅读主题")
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ReaderTheme.entries.forEach { theme ->
                    ThemeSwatch(
                        theme = theme,
                        selected = theme == settings.theme,
                        onClick = { onChange { it.copy(theme = theme) } },
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            SheetSlider(
                label = "字号",
                value = settings.fontSizeSp,
                range = 13f..26f,
                valueText = "${settings.fontSizeSp.toInt()} sp",
                onChange = { value -> onChange { it.copy(fontSizeSp = value) } },
            )
            SheetSlider(
                label = "行距",
                value = settings.lineHeightMultiplier,
                range = 1.2f..2.4f,
                valueText = "%.2f".format(settings.lineHeightMultiplier),
                onChange = { value -> onChange { it.copy(lineHeightMultiplier = value) } },
            )
            SheetSlider(
                label = "段间距",
                value = settings.paragraphSpacingDp,
                range = 4f..28f,
                valueText = "${settings.paragraphSpacingDp.toInt()} dp",
                onChange = { value -> onChange { it.copy(paragraphSpacingDp = value) } },
            )
            SheetSlider(
                label = "左右边距",
                value = settings.horizontalPaddingDp,
                range = 10f..44f,
                valueText = "${settings.horizontalPaddingDp.toInt()} dp",
                onChange = { value -> onChange { it.copy(horizontalPaddingDp = value) } },
            )
            SheetSlider(
                label = "自动滚屏速度",
                value = settings.autoScrollSpeed,
                range = 0.3f..7f,
                valueText = "%.1fx".format(settings.autoScrollSpeed),
                onChange = { value -> onChange { it.copy(autoScrollSpeed = value) } },
            )

            Spacer(Modifier.height(10.dp))

            NeuButton(
                onClick = { onChange { current -> ReaderSettings(theme = current.theme) } },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(
                    text = "恢复默认排版",
                    fontSize = 13.sp,
                    color = palette.textPrimary,
                )
            }

            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun SheetSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onChange: (Float) -> Unit,
) {
    val palette = LocalReaderPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                fontSize = 13.sp,
                color = palette.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                fontSize = 12.sp,
                color = palette.textSecondary,
            )
        }
        NeuSlider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SheetLabel(text: String) {
    val palette = LocalReaderPalette.current
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = palette.textSecondary,
    )
}
