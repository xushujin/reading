package com.azhan.qingyue.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azhan.qingyue.AppContainer
import com.azhan.qingyue.BuildConfig
import com.azhan.qingyue.R
import com.azhan.qingyue.data.prefs.KeepAliveMode
import com.azhan.qingyue.data.prefs.ReaderSettings
import com.azhan.qingyue.data.prefs.ReaderTheme
import com.azhan.qingyue.ui.neu.NeuButton
import com.azhan.qingyue.ui.neu.NeuIconButton
import com.azhan.qingyue.ui.neu.NeuSegmented
import com.azhan.qingyue.ui.neu.NeuSlider
import com.azhan.qingyue.ui.neu.NeuSurface
import com.azhan.qingyue.ui.theme.LocalReaderPalette
import com.azhan.qingyue.ui.theme.ThemeSwatch
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val palette = LocalReaderPalette.current
    val scope = rememberCoroutineScope()
    val settings by container.settingsStore.settings.collectAsState(initial = ReaderSettings())
    val snackbarHost = remember { SnackbarHostState() }
    var scanning by remember { mutableStateOf(false) }

    fun update(transform: (ReaderSettings) -> ReaderSettings) {
        scope.launch { container.settingsStore.update(transform) }
    }

    Scaffold(
        containerColor = palette.background,
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NeuIconButton(onClick = onBack, size = 40.dp) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        tint = palette.textPrimary,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "设置",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp),
            ) {
                SectionTitle("屏幕常亮")

                SettingsCard {
                    Text(
                        text = "既要阅读时不熄屏，也要避免睡着后亮一整夜。",
                        fontSize = 12.sp,
                        color = palette.textSecondary,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(12.dp))

                    NeuSegmented(
                        options = KeepAliveMode.entries.toList(),
                        selected = settings.keepAliveMode,
                        onSelect = { mode -> update { it.copy(keepAliveMode = mode) } },
                        modifier = Modifier.fillMaxWidth(),
                        label = { it.label },
                    )

                    Spacer(Modifier.height(14.dp))

                    IntSliderRow(
                        label = "无操作超时",
                        value = settings.idleTimeoutMinutes,
                        range = 0..60,
                        step = 5,
                        valueText = if (settings.idleTimeoutMinutes == 0) {
                            "不限制"
                        } else {
                            "${settings.idleTimeoutMinutes} 分钟"
                        },
                        enabled = settings.keepAliveMode == KeepAliveMode.SMART,
                        hint = "手动阅读时，这么久没碰屏幕就允许系统熄屏",
                        onChange = { value -> update { it.copy(idleTimeoutMinutes = value) } },
                    )

                    Spacer(Modifier.height(6.dp))

                    IntSliderRow(
                        label = "最长连续亮屏",
                        value = settings.maxContinuousMinutes,
                        range = 0..180,
                        step = 5,
                        valueText = if (settings.maxContinuousMinutes == 0) {
                            "不限制"
                        } else {
                            "${settings.maxContinuousMinutes} 分钟"
                        },
                        enabled = settings.keepAliveMode != KeepAliveMode.OFF,
                        hint = "到点会暂停自动滚屏并解除常亮，防止亮一整夜",
                        onChange = { value -> update { it.copy(maxContinuousMinutes = value) } },
                    )
                }

                SectionTitle("自动滚屏")

                SettingsCard {
                    Text(
                        text = "速度可以随时在阅读页调整，这里设置的是默认值。",
                        fontSize = 12.sp,
                        color = palette.textSecondary,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "默认速度",
                            fontSize = 13.sp,
                            color = palette.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "%.1fx".format(settings.autoScrollSpeed),
                            fontSize = 12.sp,
                            color = palette.textSecondary,
                        )
                    }
                    NeuSlider(
                        value = settings.autoScrollSpeed,
                        onValueChange = { value -> update { it.copy(autoScrollSpeed = value) } },
                        valueRange = 0.3f..7f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                SectionTitle("阅读主题")

                SettingsCard {
                    Text(
                        text = "打开新书时默认使用的主题。",
                        fontSize = 12.sp,
                        color = palette.textSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    // 与阅读页的排版面板共用同一个 ThemeSwatch，
                    // 两处各写一套的话会慢慢长得不一样（而且不会编译报错）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        ReaderTheme.entries.forEach { theme ->
                            ThemeSwatch(
                                theme = theme,
                                selected = theme == settings.theme,
                                onClick = { update { it.copy(theme = theme) } },
                            )
                        }
                    }
                }

                SectionTitle("书架")

                SettingsCard {
                    Text(
                        text = "重新扫描全部目录，把新拷进来的书收进书架。",
                        fontSize = 12.sp,
                        color = palette.textSecondary,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    NeuButton(
                        onClick = {
                            scope.launch {
                                scanning = true
                                val added = container.libraryRepository.rescanAll()
                                container.bookRepository.backfillPendingMeta()
                                scanning = false
                                snackbarHost.showSnackbar(
                                    if (added > 0) "新增 $added 本" else "所有目录都是最新的",
                                )
                            }
                        },
                        enabled = !scanning,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(
                            text = if (scanning) "正在扫描…" else "重新扫描全部",
                            fontSize = 13.sp,
                            color = palette.textPrimary,
                        )
                    }
                }

                SectionTitle("关于")

                SettingsCard {
                    InfoRow(
                        label = "应用",
                        value = "${stringResource(R.string.app_name)} v${BuildConfig.VERSION_NAME}",
                    )
                    InfoRow("实现", "Kotlin + Jetpack Compose")
                    InfoRow("支持格式", "TXT / EPUB")
                    InfoRow("权限", "无需存储权限（SAF 授权）")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "书架通过系统文件选择器授权目录，App 只能读取你主动添加的文件夹。",
                        fontSize = 11.sp,
                        color = palette.textSecondary,
                        lineHeight = 17.sp,
                    )
                }

                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    val palette = LocalReaderPalette.current
    Row(
        modifier = Modifier.padding(start = 8.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(5.dp, 14.dp)
                .background(palette.accent, RoundedCornerShape(3.dp)),
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.textPrimary,
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    NeuSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        elevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            content = content,
        )
    }
}

@Composable
private fun IntSliderRow(
    label: String,
    value: Int,
    range: IntRange,
    step: Int,
    valueText: String,
    hint: String,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    val palette = LocalReaderPalette.current
    val alpha = if (enabled) 1f else 0.4f

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                fontSize = 13.sp,
                color = palette.textPrimary.copy(alpha = alpha),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                fontSize = 12.sp,
                color = palette.textSecondary.copy(alpha = alpha),
            )
        }
        // 滑块保持连续（不画刻度点），但取值吸附到 step 的整数倍。
        NeuSlider(
            value = value.toFloat(),
            onValueChange = { raw ->
                val snapped = (Math.round(raw / step) * step).coerceIn(range.first, range.last)
                onChange(snapped)
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = hint,
            fontSize = 11.sp,
            color = palette.textSecondary.copy(alpha = alpha),
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val palette = LocalReaderPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = palette.textSecondary,
            modifier = Modifier.width(88.dp),
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = palette.textPrimary,
        )
    }
}
