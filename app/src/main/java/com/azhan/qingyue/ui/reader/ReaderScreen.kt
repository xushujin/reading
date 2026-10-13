package com.azhan.qingyue.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import com.azhan.qingyue.data.parser.Paragraph
import com.azhan.qingyue.data.prefs.ReadMode
import com.azhan.qingyue.data.prefs.ReaderSettings
import com.azhan.qingyue.ui.neu.NeuButton
import com.azhan.qingyue.ui.neu.NeuIconButton
import com.azhan.qingyue.ui.neu.NeuProgress
import com.azhan.qingyue.ui.neu.NeuSurface
import com.azhan.qingyue.ui.theme.LocalReaderPalette
import com.azhan.qingyue.ui.theme.QingYueTheme
import com.azhan.qingyue.ui.theme.palette
import com.azhan.qingyue.util.ScreenAwakeEffect
import com.azhan.qingyue.util.formatPercent
import com.azhan.qingyue.util.rememberScreenAwakeGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** 自动滚屏基准速度（dp/秒）。1x 大致对应「舒适的连续阅读速度」。 */
private const val AUTO_SCROLL_BASE_DP_PER_SECOND = 10f

/** 手指触碰后暂停自动滚屏的时长，避免误触就停不下来。 */
private const val TOUCH_PAUSE_MILLIS = 2_000L

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
                ReaderViewModel(bookId, container.bookRepository, container.settingsStore)
            }
        },
    )

    val state by viewModel.state.collectAsState()
    val settings by viewModel.settings.collectAsState()

    QingYueTheme(palette = settings.theme.palette()) {
        ReaderBody(
            state = state,
            settings = settings,
            onBack = onBack,
            onRetry = viewModel::load,
            onSaveProgress = viewModel::saveProgress,
            onUpdateSettings = viewModel::updateSettings,
        )
    }
}

@Composable
private fun ReaderBody(
    state: ReaderUiState,
    settings: ReaderSettings,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSaveProgress: (Int) -> Unit,
    onUpdateSettings: ((ReaderSettings) -> ReaderSettings) -> Unit,
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
            onBack = onBack,
            onSaveProgress = onSaveProgress,
            onUpdateSettings = onUpdateSettings,
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
    onBack: () -> Unit,
    onSaveProgress: (Int) -> Unit,
    onUpdateSettings: ((ReaderSettings) -> ReaderSettings) -> Unit,
) {
    val palette = LocalReaderPalette.current
    val guard = rememberScreenAwakeGuard()

    var barsVisible by remember { mutableStateOf(true) }
    var autoScrolling by remember { mutableStateOf(false) }
    var touchTick by remember { mutableIntStateOf(0) }
    var suspended by remember { mutableStateOf(false) }
    var showSettingsPanel by remember { mutableStateOf(false) }
    var showChapters by remember { mutableStateOf(false) }
    var showLimitDialog by remember { mutableStateOf(false) }

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
                            currentOffset = offset
                            onSaveProgress(offset)
                        },
                        onReachEnd = { autoScrolling = false },
                        onTouch = {
                            guard.onUserInteraction()
                            touchTick++
                        },
                        onToggleBars = { barsVisible = !barsVisible },
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
                            currentOffset = offset
                            onSaveProgress(offset)
                        },
                        onReachEnd = { autoScrolling = false },
                        onTouch = { guard.onUserInteraction() },
                        onToggleBars = { barsVisible = !barsVisible },
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
        }

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
                    document.chapterStartIndices.getOrNull(index)?.let { seekTarget = it }
                },
                onDismiss = { showChapters = false },
            )
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
) {
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    var restored by remember { mutableStateOf(false) }
    var lastSeek by remember { mutableStateOf<Int?>(null) }

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
        if (target == lastSeek) return@LaunchedEffect
        lastSeek = target
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
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggleBars,
            )
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
            ParagraphItem(paragraph = paragraph, settings = settings)
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
        var lastSeek by remember { mutableStateOf<Int?>(null) }
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
            if (target == lastSeek) return@LaunchedEffect
            lastSeek = target
            pagerState.scrollToPage(loaded.pageIndexForOffset(target))
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
        // key 里**不能**放 `loaded`：收起工具栏会触发重新分页，而分页一变这个
        // effect 就重启、正在进行的 animateScrollToPage 被取消 —— pager 停在
        // 两页中间，屏幕上就成了「上半页 + 下半页」。分页只用来判断是否到末尾，
        // 用 rememberUpdatedState 读最新值即可，不必当 key。
        val pagesState = rememberUpdatedState(loaded)
        LaunchedEffect(autoScrolling, pagerState.currentPage) {
            if (!autoScrolling) return@LaunchedEffect
            delay(autoPageIntervalMillis(settings.autoScrollSpeed))
            val current = pagesState.value
            val next = pagerState.currentPage + 1
            if (next <= current.lastIndex) {
                pagerState.animateScrollToPage(next)
            } else {
                onReachEnd()
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggleBars,
                )
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.any { it.pressed }) onTouch()
                        }
                    }
                },
        ) { index ->
            PageContent(page = loaded[index], settings = settings)
        }
    }
}

/** 渲染一整页。样式与间距必须与 [BookPaginator] 测量时完全一致，否则会溢出。 */
@Composable
private fun PageContent(page: BookPage, settings: ReaderSettings) {
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

            Text(
                text = buildAnnotatedText(slice.text, slice.spans, color, palette.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = readerIndentStart(settings, block),
                        top = readerSpacingBefore(settings, block),
                    ),
                style = readerTextStyle(
                    settings = settings,
                    blockStyle = block,
                    isParagraphStart = slice.isParagraphStart,
                    color = color,
                ),
            )
        }
    }
}

@Composable
private fun ParagraphItem(paragraph: Paragraph, settings: ReaderSettings) {
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

    Text(
        text = buildAnnotatedText(paragraph.text, paragraph.spans, color, palette.accent),
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = settings.horizontalPaddingDp.dp + readerIndentStart(settings, block),
                end = settings.horizontalPaddingDp.dp,
                top = readerSpacingBefore(settings, block),
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
    }
}

@Composable
private fun ReaderBottomBar(
    progress: Float,
    chapterIndex: Int,
    chapterCount: Int,
    autoScrolling: Boolean,
    onToggleAutoScroll: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val palette = LocalReaderPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatPercent(progress),
                fontSize = 11.sp,
                color = palette.textSecondary,
            )
            Spacer(Modifier.width(10.dp))
            NeuProgress(progress = progress, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            Text(
                text = "${chapterIndex + 1}/$chapterCount",
                fontSize = 11.sp,
                color = palette.textSecondary,
            )
        }

        Spacer(Modifier.height(4.dp))

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
