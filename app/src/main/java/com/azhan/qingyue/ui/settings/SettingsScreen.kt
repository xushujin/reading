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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azhan.qingyue.AppContainer
import com.azhan.qingyue.BuildConfig
import com.azhan.qingyue.R
import com.azhan.qingyue.data.prefs.KeepAliveMode
import com.azhan.qingyue.data.prefs.ReaderSettings
import com.azhan.qingyue.data.prefs.ReaderTheme
import com.azhan.qingyue.data.update.DownloadProgress
import com.azhan.qingyue.data.update.UpdateInfo
import com.azhan.qingyue.ui.neu.NeuSnackbarHost
import com.azhan.qingyue.ui.neu.NeuButton
import com.azhan.qingyue.ui.neu.NeuIconButton
import com.azhan.qingyue.ui.neu.NeuSegmented
import com.azhan.qingyue.ui.neu.NeuSlider
import com.azhan.qingyue.ui.neu.NeuSurface
import com.azhan.qingyue.ui.theme.LocalReaderPalette
import com.azhan.qingyue.ui.theme.ThemeSwatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val palette = LocalReaderPalette.current
    val scope = rememberCoroutineScope()
    val settings by container.settingsStore.settings.collectAsState(initial = ReaderSettings())
    val snackbarHost = remember { SnackbarHostState() }
    var scanning by remember { mutableStateOf(false) }
    var updateState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    fun checkUpdate() {
        updateState = UpdateUiState.Checking
        scope.launch {
            container.updateRepository.checkForUpdate(BuildConfig.VERSION_NAME)
                .onSuccess { info ->
                    updateState = if (info == null) UpdateUiState.UpToDate else UpdateUiState.Available(info)
                }
                .onFailure {
                    updateState = UpdateUiState.Failed(it.message ?: "检查失败，请检查网络")
                }
        }
    }

    fun installReady(file: File) {
        if (!container.updateRepository.canInstallPackages()) {
            container.updateRepository.openInstallPermissionSettings()
            scope.launch { snackbarHost.showSnackbar("请允许「安装未知应用」，返回后再点安装") }
            return
        }
        container.updateRepository.install(file)
    }

    fun startDownload(info: UpdateInfo) {
        downloadJob?.cancel()
        updateState = UpdateUiState.Downloading(info, DownloadProgress(0, info.apkSize, 0))
        downloadJob = scope.launch {
            try {
                val file = container.updateRepository.download(info) { progress ->
                    updateState = UpdateUiState.Downloading(info, progress)
                }
                // 签名不一致时直接说清楚，别让用户对着系统的「应用未安装」猜
                if (!container.updateRepository.isSameSignature(file)) {
                    updateState = UpdateUiState.Failed(
                        "更新包与已安装版本的签名不一致，无法覆盖安装。\n" +
                            "如果你是从 F-Droid 安装的，请继续通过 F-Droid 更新；" +
                            "否则请先卸载当前版本，再手动安装下载好的 APK。",
                    )
                    return@launch
                }
                updateState = UpdateUiState.Ready(file)
                installReady(file)
            } catch (cancel: CancellationException) {
                // 用户取消：回到「发现新版本」，可以随时重下
                updateState = UpdateUiState.Available(info)
                throw cancel
            } catch (t: Throwable) {
                updateState = UpdateUiState.Failed(t.message ?: "下载失败，请检查网络")
            } finally {
                downloadJob = null
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
    }

    fun update(transform: (ReaderSettings) -> ReaderSettings) {
        scope.launch { container.settingsStore.update(transform) }
    }

    Scaffold(
        containerColor = palette.background,
        snackbarHost = { NeuSnackbarHost(snackbarHost) },
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

                SectionTitle("更新")

                SettingsCard {
                    InfoRow("当前版本", "v${BuildConfig.VERSION_NAME}")
                    Spacer(Modifier.height(10.dp))

                    when (val state = updateState) {
                        UpdateUiState.Idle -> {
                            UpdateButton(text = "检查更新", onClick = ::checkUpdate)
                        }

                        UpdateUiState.Checking -> {
                            UpdateHint("正在检查…")
                        }

                        UpdateUiState.UpToDate -> {
                            UpdateHint("已是最新版本")
                            Spacer(Modifier.height(10.dp))
                            UpdateButton(text = "再检查一次", onClick = ::checkUpdate)
                        }

                        is UpdateUiState.Available -> {
                            Text(
                                text = "发现新版本 v${state.info.versionName}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.textPrimary,
                            )
                            if (state.info.notes.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = state.info.notes,
                                    fontSize = 11.sp,
                                    color = palette.textSecondary,
                                    lineHeight = 17.sp,
                                    maxLines = 8,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            UpdateButton(
                                text = "下载并安装" + if (state.info.apkSize > 0) {
                                    "（${state.info.apkSize / 1024 / 1024} MB）"
                                } else {
                                    ""
                                },
                                onClick = { startDownload(state.info) },
                            )
                        }

                        is UpdateUiState.Downloading -> {
                            UpdateHint("正在下载 ${(state.progress.fraction * 100).toInt()}%")
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = downloadDetail(state.progress),
                                fontSize = 11.sp,
                                color = palette.textSecondary,
                            )
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .background(palette.shadow, RoundedCornerShape(2.dp)),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(state.progress.fraction)
                                        .height(4.dp)
                                        .background(palette.accent, RoundedCornerShape(2.dp)),
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            UpdateButton(text = "取消下载", onClick = ::cancelDownload)
                        }

                        is UpdateUiState.Ready -> {
                            UpdateHint("下载完成，可以安装了")
                            Spacer(Modifier.height(10.dp))
                            UpdateButton(text = "安装", onClick = { installReady(state.file) })
                        }

                        is UpdateUiState.Failed -> {
                            Text(
                                text = state.message,
                                fontSize = 11.sp,
                                color = palette.textSecondary,
                                lineHeight = 17.sp,
                            )
                            Spacer(Modifier.height(10.dp))
                            UpdateButton(text = "重试", onClick = ::checkUpdate)
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "更新来自 GitHub Releases，只在点「检查更新」时联网。",
                        fontSize = 11.sp,
                        color = palette.textSecondary,
                        lineHeight = 17.sp,
                    )
                }

                SectionTitle("关于")

                SettingsCard {
                    InfoRow(
                        label = "应用",
                        value = "${stringResource(R.string.app_name)} v${BuildConfig.VERSION_NAME}",
                    )
                    InfoRow("实现", "Kotlin + Jetpack Compose")
                    InfoRow("支持格式", "TXT / EPUB / MOBI / Markdown")
                    InfoRow("权限", "无存储权限（SAF 授权）；网络仅用于检查更新")
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

/** 更新流程的状态机。放在设置页内部 —— 它只服务于这一屏，没有复用价值。 */
private sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class Downloading(val info: UpdateInfo, val progress: DownloadProgress) : UpdateUiState
    data class Ready(val file: File) : UpdateUiState
    data class Failed(val message: String) : UpdateUiState
}

@Composable
private fun UpdateButton(text: String, onClick: () -> Unit) {
    val palette = LocalReaderPalette.current
    NeuButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(text = text, fontSize = 13.sp, color = palette.textPrimary)
    }
}

@Composable
private fun UpdateHint(text: String) {
    val palette = LocalReaderPalette.current
    Text(text = text, fontSize = 12.sp, color = palette.textSecondary)
}

/** 「1.2 MB/s · 剩余 8 秒」。速度还没算出来时只说正在连接。 */
private fun downloadDetail(progress: DownloadProgress): String {
    if (progress.bytesPerSecond <= 0) return "正在连接…"

    val speed = if (progress.bytesPerSecond >= 1024 * 1024) {
        "%.1f MB/s".format(progress.bytesPerSecond / 1024f / 1024f)
    } else {
        "%d KB/s".format((progress.bytesPerSecond / 1024).coerceAtLeast(1))
    }

    val eta = progress.etaSeconds
    return when {
        eta < 0 -> speed
        eta < 60 -> "$speed · 剩余 $eta 秒"
        else -> "$speed · 剩余 ${eta / 60} 分 ${eta % 60} 秒"
    }
}
