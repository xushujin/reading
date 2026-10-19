package com.azhan.qingyue.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Toc
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.azhan.qingyue.AppContainer
import com.azhan.qingyue.data.local.BookEntity
import com.azhan.qingyue.data.parser.BlockStyle
import com.azhan.qingyue.data.parser.BookDocument
import com.azhan.qingyue.data.parser.InlineSpan
import com.azhan.qingyue.data.parser.Paragraph
import com.azhan.qingyue.data.prefs.ReadMode
import com.azhan.qingyue.data.prefs.ReaderSettings
import com.azhan.qingyue.ui.neu.NeuButton
import com.azhan.qingyue.ui.neu.NeuIconButton
import com.azhan.qingyue.ui.neu.NeuSlider
import com.azhan.qingyue.ui.neu.NeuSurface
import com.azhan.qingyue.ui.theme.LocalReaderPalette
import com.azhan.qingyue.ui.theme.ReaderPalette
import com.azhan.qingyue.ui.theme.QingYueTheme
import com.azhan.qingyue.ui.theme.palette
import com.azhan.qingyue.util.ScreenAwakeEffect
import com.azhan.qingyue.util.formatPercent
import com.azhan.qingyue.util.rememberScreenAwakeGuard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** 自动滚屏基准速度（dp/秒）。1x 大致对应「舒适的连续阅读速度」。 */
private const val AUTO_SCROLL_BASE_DP_PER_SECOND = 10f

/** 手指触碰后暂停自动滚屏的时长，避免误触就停不下来。 */
private const val TOUCH_PAUSE_MILLIS = 2_000L

/**
 * 点击切工具栏的有效横向区间（占屏宽的比例）。
 *
 * 之所以不整屏可点：阅读时拇指常搭在屏幕边缘，滑动收尾也容易多一次点击，
 * 整屏响应会把工具栏频繁抖出来。中间 40% 是「刻意去点」才容易命中的区域，
 * 两侧留给后续可能加的翻页手势，目前点上去不做任何事。
 */
private const val TAP_ZONE_START = 0.30f
private const val TAP_ZONE_END = 0.70f

/** 只有落在中间区域才回调 —— 其余位置的点击直接丢弃。 */
private fun Modifier.centerTapToToggleBars(onToggle: () -> Unit): Modifier = pointerInput(Unit) {
    detectTapGestures(
        // 显式给一个空的长按回调：长按是「选字」的起手式，
        // 不声明的话长按结束仍会被判成一次 tap，松手就把工具栏抖出来。
        onLongPress = {},
        onTap = { offset ->
            val width = size.width
            if (width > 0 && offset.x >= width * TAP_ZONE_START && offset.x <= width * TAP_ZONE_END) {
                onToggle()
            }
        },
    )
}

@Composable
fun ReaderScreen(
    container: AppContainer,
    bookId: Long,
    onBack: () -> Unit,
) {
    val viewModel: ReaderViewModel = viewModel(
        key = "reader-$bookId",
        factory = viewModelFactory {
            initializer {
                ReaderViewModel(
                    bookId,
                    container.bookRepository,
                    container.libraryRepository,
                    container.settingsStore,
                )
            }
        },
    )

    val state by viewModel.state.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val message by viewModel.message.collectAsState()

    QingYueTheme(palette = settings.theme.palette()) {
        ReaderBody(
            state = state,
            settings = settings,
            message = message,
            onMessageShown = viewModel::consumeMessage,
            onBack = onBack,
            onRetry = viewModel::load,
            onSaveProgress = viewModel::saveProgress,
            onUpdateSettings = viewModel::updateSettings,
            onClip = viewModel::addClip,
        )
    }
}

@Composable
private fun ReaderBody(
    state: ReaderUiState,
    settings: ReaderSettings,
    message: String?,
    onMessageShown: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSaveProgress: (Int) -> Unit,
    onUpdateSettings: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onClip: (Int, String) -> Unit,
) {
    val palette = LocalReaderPalette.current
    val document = state.document
    val error = state.error

    when {
        state.loading -> CenteredHint(palette.background) {
            CircularProgressIndicator(color = palette.accent, strokeWidth = 2.dp)
            Spacer(Modifier.height(18.dp))
            Text("正在解析…", color = palette.textSecondary, fontSize = 13.sp)
        }

        error != null -> CenteredHint(palette.background) {
            Text("打不开这本书", color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(error, color = palette.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Row {
                NeuButton(onClick = onRetry) {
                    Text("重试", color = palette.textPrimary, fontSize = 14.sp)
                }
                NeuButton(onClick = onBack) {
                    Text("返回书架", color = palette.textPrimary, fontSize = 14.sp)
                }
            }
        }

        document != null -> ReadingView(
            book = state.book,
            document = document,
            settings = settings,
            message = message,
            onMessageShown = onMessageShown,
            onBack = onBack,
            onSaveProgress = onSaveProgress,
            onUpdateSettings = onUpdateSettings,
            onClip = onClip,
        )

        else -> CenteredHint(palette.background) {
            Text("没有内容", color = palette.textSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun CenteredHint(
    background: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

@Composable
private fun ReadingView(
    book: BookEntity?,
    document: BookDocument,
    settings: ReaderSettings,
    message: String?,
    onMessageShown: () -> Unit,
    onBack: () -> Unit,
    onSaveProgress: (Int) -> Unit,
    onUpdateSettings: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onClip: (Int, String) -> Unit,
) {
    val palette = LocalReaderPalette.current
    val guard = rememberScreenAwakeGuard()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHost.showSnackbar(text)
        onMessageShown()
    }

    var barsVisible by remember { mutableStateOf(true) }
    var autoScrolling by remember { mutableStateOf(false) }

    // 高亮和按钮共用 picked 这一份快照；不能各自保留旧选区。
    var picked by remember(document) { mutableStateOf<ClipSelection?>(null) }

    val handleSelect: (ClipSelection?) -> Unit = { picked = it }

    val pickedText = picked?.text
    var touchTick by remember { mutableIntStateOf(0) }
    var suspended by remember { mutableStateOf(false) }
    var showSettingsPanel by remember { mutableStateOf(false) }
    var showChapters by remember { mutableStateOf(false) }
    var showLimitDialog by remember { mutableStateOf(false) }

    /**
     * 阅读锁定。
     *
     * 锁的是「会改变阅读位置或直接退出」的两类误操作：
     * - 系统返回（含手势返回）被吞掉，不会一路 pop 回书架；
     * - 单击不再切换工具栏，菜单不会莫名其妙冒出来/收起来。
     *
     * **翻页和滚动照常可用** —— 锁定的目的是防误触，不是把阅读本身也锁死。
     * 长按选字同样禁用（选字会浮出收藏按钮，属于会打断阅读的误触）。
     * 用 rememberSaveable：转屏/分屏重建后锁不会莫名其妙解开。
     */
    var locked by rememberSaveable { mutableStateOf(false) }

    // 阅读位置由内容区上报，用正文字符偏移而不是页码/段落号 ——
    // 这样滚动模式和翻页模式的坐标系是同一个，切换模式、改字号都不会丢位置。
    var currentOffset by remember { mutableIntStateOf(book?.lastOffset ?: 0) }

    // 目录跳转请求，交给内容区执行。
    var seekTarget by remember { mutableStateOf<Int?>(null) }

    val currentParagraph = document.paragraphIndexForOffset(currentOffset)
    val chapterIndex = document.chapterIndexForParagraph(currentParagraph)
    val chapterTitle = document.chapterTitles.getOrNull(chapterIndex).orEmpty()
    val progress = document.progressOfOffset(currentOffset)

    // 手指触碰后暂停两秒，避免误触直接打断阅读节奏。
    LaunchedEffect(touchTick) {
        if (touchTick == 0) return@LaunchedEffect
        suspended = true
        delay(TOUCH_PAUSE_MILLIS)
        suspended = false
    }

    // 屏幕常亮守卫：每 2 秒重新判定一次。
    LaunchedEffect(settings, autoScrolling) {
        while (true) {
            guard.evaluate(settings, autoScrolling)
            delay(2_000L)
        }
    }
    ScreenAwakeEffect(guard.keepScreenOn)

    // 进入阅读页，开一次新的常亮会话。
    LaunchedEffect(Unit) { guard.resetSession() }

    // 连续亮屏触顶：停掉自动滚屏并解除常亮，弹窗让用户自己决定。
    LaunchedEffect(guard.continuousLimitReached) {
        if (guard.continuousLimitReached) {
            autoScrolling = false
            showLimitDialog = true
        }
    }

    // 开自动滚屏就收起工具栏，把屏幕让给正文。
    LaunchedEffect(autoScrolling) {
        if (autoScrolling) barsVisible = false
    }

    // 上锁时立即收起工具栏、清掉选区 —— 否则「锁住了但菜单还开着」没有意义。
    LaunchedEffect(locked) {
        if (locked) {
            barsVisible = false
            picked = null
            showSettingsPanel = false
            showChapters = false
        }
    }

    // 进度条拖动结束后跳转。先夹到正文范围内，再落到段落起点 ——
    // 段落中间的位置没有渲染意义，落到段首才能稳定复现。
    val handleSeek: (Float) -> Unit = { fraction ->
        val target = (fraction.coerceIn(0f, 1f) * document.totalChars).toInt()
        val paragraphStart =
            document.offsetOfParagraph(document.paragraphIndexForOffset(target))
        // 立刻把进度条与章节标签推到目标位置。否则要等正文区下一次上报
        // （滚动模式有 600ms 防抖），滑块会先弹回原位再跳过去。
        currentOffset = paragraphStart
        seekTarget = paragraphStart
        onSaveProgress(paragraphStart)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        // 上中下分栏：正文是独立的一栏，工具栏显隐只改变它的高度。
        //
        // 之前用「浮层 + contentPadding」是挡不住的 —— contentPadding 只作用于
        // 列表的首尾，滚到中间就失效，文字会从按钮底下穿过去。
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            AnimatedVisibility(
                visible = barsVisible,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
            ) {
                ReaderTopBar(
                    title = book?.title ?: document.title,
                    subtitle = chapterTitle,
                    onBack = onBack,
                    onOpenChapters = { showChapters = true },
                    onLock = { locked = true },
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when (settings.readMode) {
                    ReadMode.SCROLL -> ScrolledReader(
                        document = document,
                        settings = settings,
                        initialOffset = book?.lastOffset ?: 0,
                        autoScrolling = autoScrolling,
                        suspended = suspended,
                        seekTarget = seekTarget,
                        onSeekHandled = { seekTarget = null },
                        onOffsetChanged = { offset ->
                            if (offset != currentOffset) picked = null
                            currentOffset = offset
                            onSaveProgress(offset)
                        },
                        onReachEnd = { autoScrolling = false },
                        onTouch = {
                            guard.onUserInteraction()
                            touchTick++
                        },
                        onToggleBars = {
                            if (!locked) {
                                picked = null
                                barsVisible = !barsVisible
                            }
                        },
                        selectable = !locked,
                        selected = picked,
                        onSelect = handleSelect,
                    )

                    ReadMode.PAGE -> PagedReader(
                        document = document,
                        settings = settings,
                        initialOffset = book?.lastOffset ?: 0,
                        currentOffset = currentOffset,
                        autoScrolling = autoScrolling,
                        seekTarget = seekTarget,
                        onSeekHandled = { seekTarget = null },
                        onOffsetChanged = { offset ->
                            if (offset != currentOffset) picked = null
                            currentOffset = offset
                            onSaveProgress(offset)
                        },
                        onReachEnd = { autoScrolling = false },
                        onTouch = { guard.onUserInteraction() },
                        onToggleBars = {
                            if (!locked) {
                                picked = null
                                barsVisible = !barsVisible
                            }
                        },
                        selectable = !locked,
                        selected = picked,
                        onSelect = handleSelect,
                    )
                }
            }

            AnimatedVisibility(
                visible = barsVisible,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
            ) {
                ReaderBottomBar(
                    progress = progress,
                    chapterIndex = chapterIndex,
                    chapterCount = document.chapterTitles.size,
                    autoScrolling = autoScrolling,
                    onSeek = handleSeek,
                    onToggleAutoScroll = { autoScrolling = !autoScrolling },
                    onOpenSettings = { showSettingsPanel = true },
                )
            }

            // 工具栏收起后仍需要一个「停」的入口。做成占位的一栏而不是浮动按钮，
            // 否则按钮会压在正文右下角上 —— 那就又变成重合了。
            AnimatedVisibility(
                visible = autoScrolling && !barsVisible,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
            ) {
                AutoScrollBar(
                    label = if (settings.readMode == ReadMode.PAGE) "自动翻页中" else "自动滚屏中",
                    speed = settings.autoScrollSpeed,
                    onStop = { autoScrolling = false },
                )
            }

            // 锁定条同样占位在正文之外，不浮在文字上 —— 1.3.7 的浮层版本
            // 会挡住最后一行，长按解锁时反而看不到自己读到哪。
            //
            // 只做淡入淡出、不做高度动画：高度每帧变化会让翻页模式跟着重新分页
            // （几百次文本测量），一次锁定要重排十几遍，肉眼可见地卡。
            AnimatedVisibility(
                visible = locked,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                LockBar(onUnlock = { locked = false })
            }
        }

        // 选中文字后浮现的收藏按钮。放底部中央，避开选择手柄所在的选中行
        if (pickedText != null && pickedText.isNotBlank()) {
            NeuButton(
                onClick = {
                    val selection = picked ?: return@NeuButton
                    onClip(selection.offset, selection.text)
                    picked = null
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp),
            ) {
                Text(
                    text = "收藏选中 ${pickedText.length} 字",
                    color = palette.accent,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
        }

        // 收藏摘录后的反馈。抬到底栏之上，避免被工具栏压住
        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp),
        )

        if (showSettingsPanel) {
            ReaderSettingsPanel(
                settings = settings,
                onChange = onUpdateSettings,
                onDismiss = { showSettingsPanel = false },
            )
        }

        if (showChapters) {
            ChapterPanel(
                titles = document.chapterTitles,
                currentChapter = chapterIndex,
                onSelect = { index ->
                    showChapters = false
                    // chapterStartIndices 存的是段落号，seekTarget 要的是字符偏移。
                    // 以前直接把段落号当偏移传下去，目录跳转会静默跳到书首附近。
                    document.offsetOfChapter(index)?.let { seekTarget = it }
                },
                onDismiss = { showChapters = false },
            )
        }

        // 面板开着时，系统返回（含手势返回）应当先关面板，而不是退出阅读页。
        // 这两个面板是页内浮层、不是独立的导航目的地 —— 不拦的话返回键会
        // 一路 pop 掉整个阅读页，直接回到首页。
        BackHandler(enabled = showSettingsPanel || showChapters) {
            when {
                showSettingsPanel -> showSettingsPanel = false
                showChapters -> showChapters = false
            }
        }

        // 锁定期间吞掉返回（含手势返回）。放在面板那个 BackHandler 之后 ——
        // Compose 里后组合的 enabled handler 优先，锁定时必须它说了算。
        BackHandler(enabled = locked) {
            // 故意什么都不做：这是「防误触」的核心，不是漏了实现。
        }

        if (showLimitDialog) {
            AlertDialog(
                onDismissRequest = { showLimitDialog = false },
                title = {
                    Text("已连续亮屏 ${settings.maxContinuousMinutes} 分钟")
                },
                text = {
                    Text(
                        "自动滚屏已暂停，屏幕常亮已解除 —— 这样即使看着看着睡着，也不会亮一整夜。",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showLimitDialog = false
                            guard.resetSession()
                        },
                    ) { Text("继续阅读") }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showLimitDialog = false
                            onBack()
                        },
                    ) { Text("退出阅读") }
                },
            )
        }
    }
}

/** 翻页模式下的自动翻页间隔：1x 约 4 秒一页，倍率越高越快。 */
private fun autoPageIntervalMillis(speed: Float): Long =
    (4_000f / speed.coerceAtLeast(0.1f)).toLong().coerceAtLeast(200L)

/** 滚动模式的正文区。 */
@Composable
private fun ScrolledReader(
    document: BookDocument,
    settings: ReaderSettings,
    initialOffset: Int,
    autoScrolling: Boolean,
    suspended: Boolean,
    seekTarget: Int?,
    onSeekHandled: () -> Unit,
    onOffsetChanged: (Int) -> Unit,
    onReachEnd: () -> Unit,
    onTouch: () -> Unit,
    onToggleBars: () -> Unit,
    selectable: Boolean,
    selected: ClipSelection?,
    onSelect: (ClipSelection?) -> Unit,
) {
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    var restored by remember { mutableStateOf(false) }

    val baseSpeedPx = remember(density) {
        with(density) { AUTO_SCROLL_BASE_DP_PER_SECOND.dp.toPx() }
    }

    // 恢复上次阅读位置：字符偏移反查段落，改字号行距也不会跑偏。
    LaunchedEffect(document) {
        if (restored) return@LaunchedEffect
        val index = document.paragraphIndexForOffset(initialOffset)
        if (index > 0) listState.scrollToItem(index)
        restored = true
    }

    LaunchedEffect(seekTarget) {
        val target = seekTarget ?: return@LaunchedEffect
        // 不做「和上次相同就跳过」的优化：跳到同一章也要能重新对齐，
        // 而且 seekTarget 每次都先被置回 null，这里不会重复触发。
        listState.scrollToItem(document.paragraphIndexForOffset(target))
        onSeekHandled()
    }

    // 进度上报：停顿 600ms 才落库，避免快速滑动时疯狂写库。
    //
    // 取「屏幕顶部所在段落」，但真正滚到底时直接按读完算 ——
    // 否则滚到书末时屏幕顶部离末尾还差整整一屏，进度会永远卡在 80% 上下。
    // 不用「最后一个可见段落」是因为它会让刚打开的书就显示 20%（第一屏覆盖量），
    // 恢复阅读位置时也会虚高。
    LaunchedEffect(document) {
        snapshotFlow {
            if (listState.canScrollForward) {
                listState.firstVisibleItemIndex
            } else {
                document.paragraphCount
            }
        }
            .collectLatest { index ->
                delay(600)
                onOffsetChanged(document.offsetOfParagraph(index))
            }
    }

    // 自动滚屏：按帧推进，速度与屏幕刷新率无关。
    val speedState = rememberUpdatedState(settings.autoScrollSpeed)
    val suspendedState = rememberUpdatedState(suspended)
    LaunchedEffect(autoScrolling, document) {
        if (!autoScrolling) return@LaunchedEffect
        var lastFrame = withFrameNanos { it }
        while (isActive) {
            val now = withFrameNanos { it }
            val deltaSeconds = (now - lastFrame) / 1_000_000_000f
            lastFrame = now

            if (suspendedState.value) continue

            // dispatchRawDelta 是同步的、按像素推进，正好适合逐帧自动滚屏。
            listState.dispatchRawDelta(baseSpeedPx * speedState.value * deltaSeconds)
            if (!listState.canScrollForward) {
                onReachEnd()
                break
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            // 只有屏幕中间那一竖条会切工具栏。整屏可点是误触的主要来源 ——
            // 拇指搭在屏幕边缘、滑动结束的余指都会把菜单抖出来。
            .centerTapToToggleBars(onToggleBars)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.pressed }) onTouch()
                    }
                }
            },
        contentPadding = PaddingValues(top = 8.dp, bottom = 14.dp),
    ) {
        items(
            items = document.paragraphs,
            key = { it.startOffset },
        ) { paragraph ->
            ParagraphItem(
                paragraph = paragraph,
                settings = settings,
                selectable = selectable,
                selected = selected,
                onSelect = onSelect,
            )
        }
    }
}

/** 翻页模式的正文区：按屏幕尺寸预分页，左右滑动翻页。 */
@Composable
private fun PagedReader(
    document: BookDocument,
    settings: ReaderSettings,
    initialOffset: Int,
    currentOffset: Int,
    autoScrolling: Boolean,
    seekTarget: Int?,
    onSeekHandled: () -> Unit,
    onOffsetChanged: (Int) -> Unit,
    onReachEnd: () -> Unit,
    onTouch: () -> Unit,
    onToggleBars: () -> Unit,
    selectable: Boolean,
    selected: ClipSelection?,
    onSelect: (ClipSelection?) -> Unit,
) {
    val palette = LocalReaderPalette.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // 内边距必须加在 BoxWithConstraints 这一层：分页用的就是 constraints 给出的
    // 可用尺寸。如果 padding 挂在里面的 PageContent 上，分页会按「未扣除内边距」的
    // 高度计算，每页多塞进一两行 —— 那一行就会溢出屏幕底部被切掉半截。
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = settings.horizontalPaddingDp.dp)
            .padding(top = 8.dp, bottom = 14.dp),
    ) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight

        // 分页是纯计算（几百次文本测量），丢到后台线程，别卡住首帧。
        val pages by produceState<List<BookPage>?>(
            initialValue = null,
            document, settings, widthPx, heightPx,
        ) {
            value = if (widthPx <= 0 || heightPx <= 0) {
                emptyList()
            } else {
                withContext(Dispatchers.Default) {
                    BookPaginator(measurer, settings, density)
                        .paginate(document, widthPx, heightPx)
                }
            }
        }

        val loaded = pages
        if (loaded.isNullOrEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = palette.accent, strokeWidth = 2.dp)
            }
            return@BoxWithConstraints
        }

        val pagerState = rememberPagerState { loaded.size }
        var restored by remember { mutableStateOf(false) }
        val offsetState = rememberUpdatedState(currentOffset)

        // 首次进入用存档位置；之后每次重新分页（改字号 / 行距 / 边距 / 主题）
        // 都按当前读到的位置重新定位 —— 否则页数变少时旧页码会越界。
        LaunchedEffect(loaded) {
            val target = if (restored) offsetState.value else initialOffset
            restored = true
            pagerState.scrollToPage(
                loaded.pageIndexForOffset(target).coerceIn(0, loaded.lastIndex),
            )
        }

        LaunchedEffect(seekTarget) {
            val target = seekTarget ?: return@LaunchedEffect
            pagerState.scrollToPage(
                loaded.pageIndexForOffset(target).coerceIn(0, loaded.lastIndex),
            )
            onSeekHandled()
        }

        LaunchedEffect(loaded) {
            snapshotFlow { pagerState.currentPage }
                .collectLatest { index ->
                    delay(300)
                    // 最后一页直接按读完算：它的起点离书末还差一整页，
                    // 照实算会显示成 9x%，让人以为没读完。
                    val offset = if (index >= loaded.lastIndex) {
                        document.totalChars
                    } else {
                        loaded[index].startOffset
                    }
                    onOffsetChanged(offset)
                }
        }

        // 自动翻页。
        //
        // key 里**只能**放 autoScrolling —— 这是踩过两次的坑：
        //   · 放 `pagerState.currentPage`：动画过半时 currentPage 就已经切到下一页，
        //     effect 随之重启、animateScrollToPage 被取消，pager 停在 offset 0.5，
        //     屏幕上就成了「左半页 + 右半页」；
        //   · 放 `loaded`：收起工具栏会触发重新分页，同样打断动画。
        // 改成 while 循环后，翻页节奏由 animateScrollToPage 自身挂起、串行驱动，
        // 页数与间隔都用 State 读取，不参与重启判定。
        val pagesState = rememberUpdatedState(loaded)
        val intervalState = rememberUpdatedState(
            autoPageIntervalMillis(settings.autoScrollSpeed),
        )
        LaunchedEffect(autoScrolling) {
            if (!autoScrolling) return@LaunchedEffect
            while (isActive) {
                delay(intervalState.value)
                val pages = pagesState.value
                val next = pagerState.currentPage + 1
                if (next > pages.lastIndex) {
                    onReachEnd()
                    break
                }
                try {
                    pagerState.animateScrollToPage(next)
                } catch (cancelled: CancellationException) {
                    // animateScrollToPage 受 MutatorMutex 保护，会被其它滚动操作抢占 ——
                    // 最典型的是重新分页后 LaunchedEffect(loaded) 里的 scrollToPage。
                    // 抢占抛的也是 CancellationException，若任其冒泡，本协程会被当成
                    // 「已取消」而静默退出：翻页停住，但 autoScrolling 仍是 true，
                    // 用户得关掉再开才恢复。所以这里只放行「真取消」。
                    if (!isActive) throw cancelled
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            // 锁定不影响翻页：左右滑动始终可用，锁的只是「退出」和「工具栏」。
            modifier = Modifier
                .fillMaxSize()
                .centerTapToToggleBars(onToggleBars)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.any { it.pressed }) onTouch()
                        }
                    }
                },
        ) { index ->
            PageContent(
                page = loaded[index],
                settings = settings,
                selectable = selectable,
                selected = selected,
                onSelect = onSelect,
            )
        }
    }
}

/**
 * 一段可选中、且点击能穿透的正文。
 *
 * 为什么不用 `BasicTextField` 做选择（v1.3.1 的教训）：
 * 它在**自己的内部节点**处理手势，外层挂什么都不管用 —— 点击被吞掉（工具栏切不了），
 * 而且它自己维护一套 selection，外面同步过来的范围经常和显示的对不上。
 *
 * 现在改成：`Text` 负责渲染与点击（它不吞手势），选择自己实现 ——
 * 长按后拖动，用 [TextLayoutResult.getOffsetForPosition] 把手指位置换算成字符下标。
 * 这样分页测量完全不变，三条手势（点击 / 长按拖动 / 翻页）也互不干扰。
 */
@Composable
private fun SelectableSlice(
    slice: PageSlice,
    settings: ReaderSettings,
    selectable: Boolean,
    selected: ClipSelection?,
    onSelect: (ClipSelection?) -> Unit,
) {
    val palette = LocalReaderPalette.current
    val block = slice.blockStyle

    var layout by remember(slice.startOffset, slice.text) { mutableStateOf<TextLayoutResult?>(null) }
    var anchor by remember(slice.startOffset, slice.text) { mutableStateOf<Int?>(null) }
    var cursor by remember(slice.startOffset, slice.text) { mutableStateOf<Int?>(null) }

    val range = selectedRange(slice.text, slice.startOffset, selected)

    Text(
        text = highlight(text = slice.text, spans = slice.spans, range = range, palette = palette),
        onTextLayout = { layout = it },
        modifier = Modifier
            .fillMaxWidth()
            // 手势坐标必须以文字区域为原点；否则缩进与段前距会让高亮偏移。
            .padding(
                start = readerIndentStart(settings, block),
                top = readerSpacingBefore(settings, block),
            )
            // 长按后拖动 = 选择。锁定时整段手势都不挂载 —— 否则「锁定」只是
            // 关掉了点击，长按照样能选字、弹出收藏按钮。
            // 同一个起始 offset 在改变字号/工具栏显隐后的切片文本可能完全不同，
            // 手势协程必须随文本重启，否则 onDragEnd 会拿旧切片的文字保存。
            .then(
                if (selectable) {
                    Modifier.pointerInput(slice.startOffset, slice.text) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { position ->
                                onSelect(null)
                                val index = layout?.getOffsetForPosition(position)
                                    ?: return@detectDragGesturesAfterLongPress
                                anchor = index
                                cursor = index
                            },
                            onDrag = { change, _ ->
                                cursor = layout?.getOffsetForPosition(change.position)
                                onSelect(
                                    selectionRange(anchor, cursor)?.let {
                                        selectedClip(slice.text, slice.startOffset, it)
                                    },
                                )
                                change.consume()
                            },
                            onDragEnd = {
                                onSelect(
                                    selectionRange(anchor, cursor)?.let {
                                        selectedClip(slice.text, slice.startOffset, it)
                                    },
                                )
                            },
                            onDragCancel = {
                                anchor = null
                                cursor = null
                                onSelect(null)
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
        style = readerTextStyle(
            settings = settings,
            blockStyle = block,
            isParagraphStart = slice.isParagraphStart,
            color = palette.textPrimary,
        ),
    )
}

/** 保存按钮和入库共用同一份已选文本，避免重排后反查到另一段。 */
internal data class ClipSelection(val offset: Int, val text: String)

/** 从被高亮的同一份文本、同一区间直接截取摘录。区间不合法时不允许保存。 */
internal fun selectedClip(text: String, startOffset: Int, range: IntRange): ClipSelection? {
    if (startOffset < 0 || range.first < 0 || range.last < range.first || range.last >= text.length) {
        return null
    }
    val selected = text.substring(range.first, range.last + 1)
    if (selected.isBlank()) return null
    return ClipSelection(startOffset + range.first, selected)
}

/** 只有当前展示的原文和待保存文本逐字相同时才允许画高亮。 */
internal fun selectedRange(text: String, startOffset: Int, selected: ClipSelection?): IntRange? {
    if (selected == null) return null
    val first = selected.offset - startOffset
    val lastExclusive = first + selected.text.length
    if (first < 0 || lastExclusive > text.length || first == lastExclusive) return null
    if (text.substring(first, lastExclusive) != selected.text) return null
    return first until lastExclusive
}

/** 把长按锚点与拖动终点归一成一个区间；任一为空或重合则返回 null。 */
private fun selectionRange(anchor: Int?, cursor: Int?): IntRange? {
    if (anchor == null || cursor == null) return null
    val a = minOf(anchor, cursor)
    val b = maxOf(anchor, cursor)
    if (a == b) return null
    return a until b
}

/** 在选中范围上叠一层底色。用 copy 重建 AnnotatedString，不动原始 spans。 */
private fun highlight(
    text: String,
    spans: List<InlineSpan>,
    range: IntRange?,
    palette: ReaderPalette,
): AnnotatedString {
    val base = buildAnnotatedText(text, spans, palette.textPrimary, palette.accent)
    if (range == null) return base
    return buildAnnotatedString {
        append(base)
        addStyle(
            style = SpanStyle(background = palette.accent.copy(alpha = 0.30f)),
            start = range.first.coerceIn(0, text.length),
            end = (range.last + 1).coerceIn(0, text.length),
        )
    }
}

/** 渲染一整页。样式与间距必须与 [BookPaginator] 测量时完全一致，否则会溢出。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageContent(
    page: BookPage,
    settings: ReaderSettings,
    selectable: Boolean,
    selected: ClipSelection?,
    onSelect: (ClipSelection?) -> Unit,
) {
    val palette = LocalReaderPalette.current
    // 内边距已经由外层 BoxWithConstraints 承担，这里只管排版 ——
    // 两边各留一份的话，分页高度会比实际可用高度多算一倍内边距。
    Column(modifier = Modifier.fillMaxSize()) {
        page.slices.forEach { slice ->
            val block = slice.blockStyle

            if (block == BlockStyle.RULE) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = readerSpacingBefore(settings, block))
                        .height(1.dp)
                        .background(palette.divider),
                )
                return@forEach
            }

            val color = when {
                block.isHeading() -> palette.accent
                block == BlockStyle.QUOTE -> palette.textSecondary
                else -> palette.textPrimary
            }

            SelectableSlice(
                slice = slice,
                settings = settings,
                selectable = selectable,
                selected = selected,
                onSelect = onSelect,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ParagraphItem(
    paragraph: Paragraph,
    settings: ReaderSettings,
    selectable: Boolean,
    onSelect: (ClipSelection?) -> Unit,
    selected: ClipSelection?,
) {
    val palette = LocalReaderPalette.current
    val block = paragraph.blockStyle

    // 水平线没有文字，直接画一条
    if (block == BlockStyle.RULE) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = settings.horizontalPaddingDp.dp,
                    end = settings.horizontalPaddingDp.dp,
                    top = readerSpacingBefore(settings, block),
                )
                .height(1.dp)
                .background(palette.divider),
        )
        return
    }

    val color = when {
        block.isHeading() -> palette.accent
        block == BlockStyle.QUOTE -> palette.textSecondary
        else -> palette.textPrimary
    }

    var layout by remember(paragraph.startOffset, paragraph.text) { mutableStateOf<TextLayoutResult?>(null) }
    var anchor by remember(paragraph.startOffset, paragraph.text) { mutableStateOf<Int?>(null) }
    var cursor by remember(paragraph.startOffset, paragraph.text) { mutableStateOf<Int?>(null) }

    val range = selectedRange(paragraph.text, paragraph.startOffset, selected)

    Text(
        text = highlight(paragraph.text, paragraph.spans, range, palette),
        onTextLayout = { layout = it },
        modifier = Modifier
            .fillMaxWidth()
            // 手势坐标与 TextLayoutResult 都以文字区域为原点。
            .padding(
                start = settings.horizontalPaddingDp.dp + readerIndentStart(settings, block),
                end = settings.horizontalPaddingDp.dp,
                top = readerSpacingBefore(settings, block),
            )
            // 长按后拖动 = 选择。锁定时不挂这个手势。
            .then(
                if (selectable) {
                    Modifier.pointerInput(paragraph.startOffset, paragraph.text) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { position ->
                                onSelect(null)
                                val index = layout?.getOffsetForPosition(position)
                                    ?: return@detectDragGesturesAfterLongPress
                                anchor = index
                                cursor = index
                            },
                            onDrag = { change, _ ->
                                cursor = layout?.getOffsetForPosition(change.position)
                                onSelect(
                                    selectionRange(anchor, cursor)?.let {
                                        selectedClip(paragraph.text, paragraph.startOffset, it)
                                    },
                                )
                                change.consume()
                            },
                            onDragEnd = {
                                onSelect(
                                    selectionRange(anchor, cursor)?.let {
                                        selectedClip(paragraph.text, paragraph.startOffset, it)
                                    },
                                )
                            },
                            onDragCancel = {
                                anchor = null
                                cursor = null
                                onSelect(null)
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
        style = readerTextStyle(
            settings = settings,
            blockStyle = block,
            isParagraphStart = true,
            color = color,
        ),
    )
}

@Composable
private fun ReaderTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onOpenChapters: () -> Unit,
    onLock: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
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
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        NeuIconButton(onClick = onOpenChapters, size = 40.dp) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Toc,
                contentDescription = "目录",
                tint = palette.textPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
        NeuIconButton(onClick = onLock, size = 40.dp) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = "锁定阅读",
                tint = palette.textPrimary,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
private fun ReaderBottomBar(
    progress: Float,
    chapterIndex: Int,
    chapterCount: Int,
    autoScrolling: Boolean,
    onSeek: (Float) -> Unit,
    onToggleAutoScroll: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val palette = LocalReaderPalette.current

    // 拖动过程中只更新本地显示值，松手才真正跳转 ——
    // 翻页模式的每次定位都要重新走一遍分页查找，跟着手指逐像素触发会明显发涩。
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(progress) }
    LaunchedEffect(progress) {
        if (!dragging) dragValue = progress
    }
    val shown = if (dragging) dragValue else progress

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatPercent(shown),
                fontSize = 11.sp,
                color = palette.textSecondary,
                modifier = Modifier.width(38.dp),
            )
            NeuSlider(
                value = shown,
                onValueChange = {
                    dragging = true
                    dragValue = it
                },
                onValueChangeFinished = {
                    dragging = false
                    onSeek(dragValue)
                },
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${chapterIndex + 1}/$chapterCount",
                fontSize = 11.sp,
                color = palette.textSecondary,
                modifier = Modifier.width(44.dp),
                textAlign = TextAlign.End,
            )
        }

        // 目录入口只留顶栏那一个，这里不再重复。
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NeuIconButton(onClick = onToggleAutoScroll, size = 42.dp) {
                Icon(
                    imageVector = if (autoScrolling) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "自动滚屏",
                    tint = if (autoScrolling) palette.accent else palette.textPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            NeuIconButton(onClick = onOpenSettings, size = 42.dp) {
                Icon(
                    imageVector = Icons.Rounded.FormatSize,
                    contentDescription = "阅读设置",
                    tint = palette.textPrimary,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}

/** 自动滚屏时占位在正文区之外的控制条 —— 不浮在文字上。 */
@Composable
private fun AutoScrollBar(
    label: String,
    speed: Float,
    onStop: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$label · %.1fx".format(speed),
            fontSize = 12.sp,
            color = palette.textSecondary,
            modifier = Modifier.weight(1f),
        )
        NeuIconButton(onClick = onStop, size = 38.dp, elevation = 5.dp) {
            Icon(
                imageVector = Icons.Rounded.Pause,
                contentDescription = "停止自动滚屏",
                tint = palette.accent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * 锁定条：占位在正文区之外的一栏，不浮在文字上。
 *
 * 1.3.7 把它做成了底部浮层，结果正好盖住最后一行 —— 而「长按解锁」这个动作
 * 恰恰发生在你想看清自己读到哪的时候。现在改成和 [AutoScrollBar] 一样的分栏，
 * 锁定时正文区让出一行高度，字不会被压住。
 *
 * 只有长按才解锁：轻碰就解开的锁等于没锁。
 */
@Composable
private fun LockBar(onUnlock: () -> Unit) {
    val palette = LocalReaderPalette.current
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        NeuSurface(cornerRadius = 18.dp, elevation = 5.dp) {
            Row(
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onUnlock()
                            },
                        )
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "已锁定 · 长按解锁",
                    fontSize = 12.sp,
                    color = palette.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun ReaderSettingsPanel(
    settings: ReaderSettings,
    onChange: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    ReaderSettingsSheet(settings = settings, onChange = onChange, onDismiss = onDismiss)
}

@Composable
private fun ChapterPanel(
    titles: List<String>,
    currentChapter: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ChapterSheet(
        titles = titles,
        currentChapter = currentChapter,
        onSelect = onSelect,
        onDismiss = onDismiss,
    )
}

/** 面板底部的通用容器：半透明遮罩 + 拟态面板。 */
@Composable
internal fun PanelScaffold(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        NeuSurface(
            modifier = modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            cornerRadius = 26.dp,
            elevation = 12.dp,
        ) {
            content()
        }
    }
}
