package com.ccsut.kb.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ripple
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import com.ccsut.kb.data.BgStore
import com.ccsut.kb.data.UserEdits
import com.ccsut.kb.data.Cls
import com.ccsut.kb.data.Dataset
import com.ccsut.kb.data.Period
import com.ccsut.kb.util.Block
import com.ccsut.kb.util.DebugLog
import com.ccsut.kb.util.KbClock
import com.ccsut.kb.util.Merger
import com.ccsut.kb.util.TimeParse
import com.ccsut.kb.util.Weeks
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

private val BREAK_H = 22.dp      // 大课间(午休/晚饭)分隔带高度
private val EVE_H = 88.dp        // 晚上大节槽位高度 (整周无晚课时收窄为细条, 见 WeekGrid.eveH)
private val LEFT_W = 42.dp       // 左侧时间轴: 10sp 刻度可读的最小宽度

/** 课名断行增强: 括号前/后给断行机会 (ZWSP), 括号组内部用 WORD JOINER 禁断, 连字符后允许断行;
 *  「创业基础（创新思维）」稳定断成 创业基础 / （创新思维） 不出碎行 */
private fun courseDisplayName(kc: String): String {
    val sb = StringBuilder()
    var inParen = false
    for (ch in kc) {
        when {
            ch == '（' || ch == '(' -> {
                if (sb.isNotEmpty()) sb.append('\u200B')
                inParen = true
                sb.append(ch)
            }
            ch == '）' || ch == ')' -> {
                inParen = false
                sb.append(ch)
                sb.append('\u200B')
            }
            else -> {
                if (inParen && sb.isNotEmpty()) sb.append('\u2060')
                sb.append(ch)
            }
        }
    }
    return sb.toString().replace("-", "-\u200B")
}

/** 课程块文字: 块本体与详情预览共用同一份排版, 内边距 6dp 给小字留出呼吸空间 */
@Composable
private fun BlockContent(name: String, room: String, onColor: Color, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
        Text(
            courseDisplayName(name),
            fontSize = 11.sp, lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            color = onColor,
            maxLines = 6, overflow = TextOverflow.Ellipsis,
        )
        if (room.isNotEmpty()) {
            Text(
                room,
                fontSize = 10.sp, lineHeight = 13.sp,
                color = onColor.copy(alpha = 0.85f),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 大节模型: 槽位 k 覆盖 jc (2k+1, 2k+2) */
private class SlotModel(periods: List<Period>) {
    val slots: Int
    val mainSlots: Int
    val breaks = mutableSetOf<Int>()
    val times = mutableListOf<Pair<Int, Int>>()
    val jcRange = mutableListOf<Pair<Int, Int>>()

    init {
        val byJc = periods.associateBy { it.jc }
        // 缺节/脏时间的槽位跳过而非 first{} 抛异常 —— 教务作息字段异常时降级渲染, 不崩组合期
        for (k in 0 until (byJc.keys.maxOrNull() ?: 0) / 2) {
            val a = byJc[2 * k + 1] ?: continue
            val b = byJc[2 * k + 2] ?: continue
            val s = TimeParse.minutesOf(a.start) ?: continue
            val e = TimeParse.minutesOf(b.end) ?: continue
            val idx = times.size
            times += s to e
            jcRange += a.jc to b.jc
            if (idx > 0 && s - times[idx - 1].second >= 60) breaks += idx
        }
        slots = times.size
        mainSlots = (slots - 1).coerceAtLeast(0)
    }
}

@Composable
fun ScheduleScreen(
    dataset: Dataset,
    cls: Cls,
    initialWeek: Int,
    colorMap: Map<String, Int> = emptyMap(),
    bgFile: File? = null,
    bgVersion: Int = 0,
    bgAlpha: Float = 0.45f,
    dbgOffset: Int = 0,
    personalBadge: Boolean = false,
    onToggleTimetable: () -> Unit = {},
    onOpenMore: () -> Unit,
    onCourseClick: (Block) -> Unit,
    onAddAt: (Int, Int, Int) -> Unit,   // (day, jc, week) —— week 供加课表单默认勾选当前周
    // 编辑会话: 回退按钮 + 虚影 (由 MainActivity 持有状态, 这里只渲染)
    undoVisible: Boolean = false,
    undoCount: Int = 0,
    onUndo: () -> Unit = {},
    onUndoAll: () -> Unit = {},
    ghosts: List<Ghost> = emptyList(),
    onGhostRestore: (Ghost) -> Unit = {},
    // 自定义外观: 「今天」状态条 (今日课程横条) 的透明度百分比 (10-100)
    todayBarAlpha: Int = 100,
) {
    // 时间旅行: dbgOffset 变化时重取语义今天 (KbClock, 只平移日期);
    // 之后由下方 30 秒 ticker 自动跟进真实日期, 跨零点/跨周后顶栏日期、今日列高亮、今日横条不会停在昨天
    var today by remember(dbgOffset) { mutableStateOf(KbClock.today()) }
    val todayDay = today.dayOfWeek.value
    // 今日块所在周跟随语义今天 (跨周后自动跟到新一周), 组合初值与 MainActivity 传入的 initialWeek 一致
    val todayWeek = remember(dataset.startDate, dataset.weeks, today) {
        Weeks.currentWeek(dataset, today)
    }
    val todayBlocks = remember(cls.courses, todayWeek, todayDay) {
        Merger.blocksOf(cls.courses, todayWeek).filter { it.course.day == todayDay }
    }
    // 明天第一节课(仅当 8:20 开头), 供晚间早八提示
    val tomorrowEarly = remember(cls.courses, dataset.startDate, dataset.weeks, today) {
        val tmr = today.plusDays(1)
        val start = runCatching { LocalDate.parse(dataset.startDate) }.getOrNull()
        val week = start?.let { (ChronoUnit.DAYS.between(it, tmr) / 7 + 1).toInt() } ?: 0
        if (week < 1 || week > dataset.weeks) null
        else Merger.blocksOf(cls.courses, week)
            .filter { it.course.day == tmr.dayOfWeek.value }
            .minByOrNull { it.startJc }
            ?.takeIf { it.startJc == 1 }
    }
    // 当前分钟(30 秒刷新): 状态栏横竖屏共用; 顺带重取语义今天, 覆盖跨零点/跨周
    var nowMin by remember { mutableStateOf(LocalTime.now().let { it.hour * 60 + it.minute }) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
            // LocalDate 按值相等, 未跨天时赋值不触发重组
            today = KbClock.today()
        }
    }
    val todayStatus = remember(todayBlocks, dataset.periods, tomorrowEarly, nowMin) {
        TodayStatus.compute(todayBlocks, dataset.periods, nowMin, todayDay, tomorrowEarly)
    }
    // 横屏等矮窗口: 顶栏与状态栏合并成一行, 课表拿回高度
    val compact = LocalConfiguration.current.screenHeightDp < 480
    val pagerState = rememberPagerState(initialPage = initialWeek - 1) { dataset.weeks }
    val scope = rememberCoroutineScope()
    val viewWeek = pagerState.currentPage + 1
    // 翻到非本周时横幅换成该周概要 (节数/门数/最早开课/最忙日/空闲日), 不复述顶栏周数
    val viewBlocks = remember(cls.courses, viewWeek) { Merger.blocksOf(cls.courses, viewWeek) }
    val bannerStatus = if (viewWeek == initialWeek) todayStatus else TodayStatus.weekSummary(viewBlocks, dataset.periods)

    // 颜色不做 650ms 过渡(atrace 实测: 跨本周边界慢帧几乎全花在逐帧重组 + applyChanges 上):
    // 与周数滚动同帧硬切, 过渡感由横幅文案的 AnimatedContent 承担
    val cs = MaterialTheme.colorScheme
    val (barBg, barFg) = when (bannerStatus.tone) {
        TodayStatus.Tone.ACTIVE -> cs.primaryContainer to cs.onPrimaryContainer
        TodayStatus.Tone.NIGHT -> cs.tertiaryContainer to cs.onTertiaryContainer
        TodayStatus.Tone.JOY -> cs.secondaryContainer to cs.onSecondaryContainer
        TodayStatus.Tone.CALM -> cs.surfaceColorAtElevation(2.dp) to cs.onSurface
    }
    // 状态栏实测高度: 药丸用它做到 Y 轴等大
    val density = LocalDensity.current
    var barH by remember { mutableStateOf(0.dp) }

    // 背景图: 路径固定为 bg.jpg, 换图时路径不变, 必须以 bgVersion 为 key 才会重新解码
    val bgBitmap by produceState<android.graphics.Bitmap?>(null, bgFile, bgVersion) {
        value = bgFile?.let { f ->
            withContext(Dispatchers.IO) { runCatching { BgStore.decode(f.absolutePath) }.getOrNull() }
        }
    }
    // 有背景图时顶栏/今日状态条走毛玻璃 (haze), 无背景图保持纯色
    val hazeState = remember { HazeState() }
    val haze = if (bgBitmap != null) hazeState else null

    Box(Modifier.fillMaxSize()) {
        bgBitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().haze(hazeState),
                contentScale = ContentScale.Crop,
                alpha = bgAlpha.coerceIn(0.1f, 1f),
            )
            // 轻度压一层底色, 保证文字可读; 浅色档位压低防泛白
            val bgScrim = CourseColors.isDark(MaterialTheme.colorScheme)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = if (bgScrim) 0.22f else 0.10f)),
            )
        }
        // 状态栏 insets 由各顶栏在玻璃内部消化 (玻璃上探覆盖状态栏, 消除裸照片与玻璃板的割裂感)
        Column(Modifier.fillMaxSize()) {
        if (compact) {
            // ---- 横屏紧凑头: 周次 + 状态一行 ----
            CompactBar(
                status = todayStatus,
                viewWeek = viewWeek,
                initialWeek = initialWeek,
                todayDay = todayDay,
                showBackToCurrent = viewWeek != initialWeek,
                onBackToCurrent = { scope.launch { pagerState.animateScrollToPage(initialWeek - 1) } },
                onOpenMore = onOpenMore,
                undoVisible = undoVisible,
                undoCount = undoCount,
                onUndo = onUndo,
                onUndoAll = onUndoAll,
            )
        } else {
        // ---- 顶栏: 周次 + 日期 ----
        Row(
            Modifier
                .fillMaxWidth()
                .then(
                    if (haze != null) Modifier
                    .clip(TopBarShape)
                    .hazeChild(
                        haze,
                        style = HazeStyle(
                            backgroundColor = MaterialTheme.colorScheme.surface,
                            tints = listOf(HazeTint(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))),
                            blurRadius = 30.dp,
                            noiseFactor = 0f,
                        ),
                    )
                    .glassEdge(TopBarShape, CourseColors.isDark(MaterialTheme.colorScheme)) else Modifier,
                )
                // 玻璃上探覆盖状态栏: insets 在玻璃内部消化, 状态栏与顶栏融为一体
                .statusBarsPadding()
                .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    // 翻周时数字按方向纵向上滚/下滚, 不再硬切
                    WeekNumberText(viewWeek, 24.sp)
                    // 非本周不加"(非本周)"小字: 「回到本周」药丸已表达同一状态
                    Text(
                        " ${Weeks.cnDay(todayDay)}",
                        fontSize = 24.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${today.year}/${today.monthValue}/${today.dayOfMonth}",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    // 徽标即可点: 点一下在班级/个人课表间来回切
                    if (personalBadge) {
                        Text(
                            "个人课表 ⇄",
                            fontSize = 10.sp, fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onToggleTimetable)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                    RoundedCornerShape(8.dp),
                                )
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                    RoundedCornerShape(8.dp),
                                )
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    } else {
                        Text(
                            "班级课表 ⇄",
                            fontSize = 10.sp, fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onToggleTimetable)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                    RoundedCornerShape(8.dp),
                                )
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            // 编辑会话回退按钮: 有可回退操作且在生存期内才出现, 点按回退一步, 长按震动还原全部
            if (undoVisible && undoCount > 0) {
                UndoButton(
                    onClick = onUndo,
                    onLongClick = onUndoAll,
                    modifier = Modifier.width(48.dp).height(48.dp),
                )
            }
            Box(
                Modifier.width(48.dp).height(48.dp).clip(CircleShape).clickable(onClick = onOpenMore),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Menu,
                    contentDescription = "更多",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---- 今日课程横条(动态状态栏) + 回到本周药丸 ----
        // 横幅常驻, 非本周时文案切换为所查周视角
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            TodayBar(
                status = bannerStatus,
                barBg = barBg,
                barFg = barFg,
                haze = haze,
                alpha = todayBarAlpha / 100f,
                modifier = Modifier.weight(1f).onSizeChanged { barH = with(density) { it.height.toDp() } },
            )
            AnimatedVisibility(
                visible = viewWeek != initialWeek,
                // 宽度展开: 真正的卡顿源是副文案行高度的 SizeTransform(已改为常占一行),
                // 展开每帧只重排本行 + TodayBar 文案重测量(~3ms), 实测在帧预算内
                enter = expandHorizontally(
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Start,
                ) + fadeIn(tween(200, delayMillis = 70)),
                exit = shrinkHorizontally(
                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Start,
                ) + fadeOut(tween(140)),
            ) {
                BackToCurrentPill(
                    bg = barBg,
                    fg = barFg,
                    alpha = todayBarAlpha / 100f,
                    modifier = Modifier.padding(start = 6.dp).height(barH),
                    onClick = { scope.launch { pagerState.animateScrollToPage(initialWeek - 1) } },
                )
            }
        }
        }

        // ---- 周网格(左右翻页) ----
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            pageSpacing = 8.dp,
            beyondViewportPageCount = 1,
        ) { page ->
            WeekGrid(
                hasBg = bgFile != null,
                dataset = dataset,
                cls = cls,
                week = page + 1,
                initialWeek = initialWeek,
                nowMin = nowMin,
                today = today,
                colorMap = colorMap,
                pagerState = pagerState,
                onCourseClick = onCourseClick,
                onAddAt = { d, j -> onAddAt(d, j, page + 1) },
                ghosts = ghosts,
                onGhostRestore = onGhostRestore,
            )
        }
        }
    }
}

@Composable
private fun TodayBar(
    status: TodayStatus.Result,
    barBg: Color,
    barFg: Color,
    haze: HazeState? = null,
    alpha: Float = 1f,   // 自定义外观: 状态条底色透明度 (文字保持全不透明保证可读)
    modifier: Modifier = Modifier,
) {
    // 浅色底流光用黑光带才可见; 无限动画本体在 ShimmerBand 内驱动
    val dark = CourseColors.isDark(MaterialTheme.colorScheme)

    Surface(
        color = if (haze != null) Color.Transparent else barBg.copy(alpha = alpha),
        contentColor = barFg,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .shadow(6.dp, RoundedCornerShape(16.dp))
            .then(
                if (haze != null) Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .hazeChild(
                        haze,
                        style = HazeStyle(
                            backgroundColor = MaterialTheme.colorScheme.surface,
                            tints = listOf(HazeTint(barBg.copy(alpha = 0.45f * alpha))),
                            blurRadius = 30.dp,
                            noiseFactor = 0f,
                        ),
                    )
                    .glassEdge(RoundedCornerShape(16.dp), dark)
                else Modifier,
            ),
    ) {
        Box {
            // 顶面反光: 玻璃上沿一道淡白渐变, 悬浮感的关键一笔 (深色模式更弱)
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = if (dark) 0.10f else 0.22f), Color.Transparent),
                    ),
                ),
            )
            // 上课中: 一层缓慢扫过的微光, Material You 流光感 (仅此状态开动画)
            if (status.tone == TodayStatus.Tone.ACTIVE) ShimmerBand(dark, Modifier.matchParentSize())
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    // 文案过渡: 翻周/状态切换时淡入+小幅上滑; 键用字符串
                    // (非本周的 Result 每次重组都是新实例, 键对象会每帧重放)
                    AnimatedContent(
                        targetState = status.title,
                        transitionSpec = {
                            (fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 3 }) togetherWith
                                fadeOut(tween(350))
                        },
                        label = "barTitle",
                    ) { t ->
                        Text(
                            t,
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = barFg,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    AnimatedContent(
                        targetState = status.sub,
                        transitionSpec = {
                            (fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 3 }) togetherWith
                                fadeOut(tween(350)) using
                                // 尺寸瞬切: null↔有值时容器高度一步到位, 不做逐帧动画
                                SizeTransform(clip = true) { _, _ -> snap() }
                        },
                        label = "barSub",
                    ) { s ->
                        // 副行永远占住一行(空内容渲染同规格空 Text): 本周无副文案(晚/放学后)时
                        // 高度不变 —— 否则 AnimatedContent 的 SizeTransform 逐帧动画容器高度,
                        // 横幅行高度逐帧变 → Column→pager(weight) 逐帧重测两个整页
                        // (atrace 实测 measureAndLayout 23ms × ~10 帧) —— 跨本周边界卡顿的真正主源
                        Text(
                            s ?: "",
                            fontSize = 10.sp, lineHeight = 13.sp,
                            color = barFg.copy(alpha = 0.72f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** 上课中状态条的扫光层: 无限动画逐帧读取隔离在本组合内, 不带着 TodayBar 整体重组 */
@Composable
private fun ShimmerBand(dark: Boolean, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "kbShimmer")
    val x by t.animateFloat(
        0f, 1f,
        // 扫过 3s + 屏外休息 1.8s: 重置发生在两端屏幕外, 视觉上无缝循环不顿挫
        infiniteRepeatable(
            keyframes {
                durationMillis = 4800
                0f at 0 using LinearEasing
                1f at 3000 using LinearEasing
                1f at 4800
            }
        ),
        label = "kbShimmerX",
    )
    Box(modifier.background(colShimmer(x, 0.20f, dark)))
}

/** 「第 N 周」数字滚动: 翻周时数字按滑动方向纵向上滚/下滚(方向感知 200ms), 竖屏顶栏与横屏 CompactBar 共用 */
@Composable
private fun WeekNumberText(viewWeek: Int, fontSize: TextUnit) {
    AnimatedContent(
        targetState = viewWeek,
        transitionSpec = {
            val dir = if (targetState > initialState) 1 else -1
            (slideInVertically(tween(200, easing = FastOutSlowInEasing)) { h -> dir * h } +
                fadeIn(tween(200, easing = FastOutSlowInEasing))) togetherWith
                (slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { h -> -dir * h } +
                fadeOut(tween(200, easing = FastOutSlowInEasing)))
        },
        label = "weekNum",
    ) { week ->
        Text(
            "第${week}周",
            fontSize = fontSize, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** 「回到本周」椭圆小药丸: 由调用方给定底色/文字色/高度(与状态栏同色等高), 纯 Box+clickable 不触发 48dp 触控目标 */
@Composable
private fun BackToCurrentPill(
    bg: Color,
    fg: Color,
    onClick: () -> Unit,
    alpha: Float = 1f,   // 与状态条同一族: 跟随自定义外观的透明度
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(50))
            .background(bg.copy(alpha = alpha))
            .clickable(onClickLabel = "回到本周", onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "回到本周",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = fg,
        )
    }
}

/** 横屏等矮窗口的单行头: 周次 + 今日状态 + 回到本周药丸 + 菜单 */
@Composable
private fun CompactBar(
    status: TodayStatus.Result,
    viewWeek: Int,
    initialWeek: Int,
    todayDay: Int,
    showBackToCurrent: Boolean,
    onBackToCurrent: () -> Unit,
    onOpenMore: () -> Unit,
    undoVisible: Boolean = false,
    undoCount: Int = 0,
    onUndo: () -> Unit = {},
    onUndoAll: () -> Unit = {},
) {
    Row(
        // 横屏无玻璃, 状态栏 insets 自行消化
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 横屏同样用数字滚动, 与竖屏一致
        WeekNumberText(viewWeek, 18.sp)
        Text(
            " ${Weeks.cnDay(todayDay)}",
            fontSize = 18.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.width(10.dp))
        // 非本周不显示「今天」状态: 今天不属于所查看的周
        if (viewWeek == initialWeek) {
            Text(
                status.title,
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = if (status.tone == TodayStatus.Tone.ACTIVE) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (showBackToCurrent) {
            BackToCurrentPill(
                bg = MaterialTheme.colorScheme.secondaryContainer,
                fg = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = onBackToCurrent,
            )
        }
        if (undoVisible && undoCount > 0) {
            UndoButton(
                onClick = onUndo,
                onLongClick = onUndoAll,
                modifier = Modifier.width(44.dp).height(44.dp),
            )
        }
        Box(
            Modifier.width(44.dp).height(44.dp).clip(CircleShape).clickable(onClick = onOpenMore),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Menu,
                contentDescription = "更多",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 顶栏回退按钮: 点按回退一步, 长按震动后还原全部; 角标显示可回退步数 (尺寸由调用方给定) */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UndoButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(CircleShape)
            .combinedClickable(
                onClick = onClick,
                onLongClickLabel = "还原全部修改",
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.Undo,
            contentDescription = "回退上一步修改，长按还原全部",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WeekGrid(
    dataset: Dataset,
    cls: Cls,
    week: Int,
    initialWeek: Int,
    nowMin: Int,       // 当前分钟, 由 ScheduleScreen 的 30s ticker 统一供入
    hasBg: Boolean,   // 自定义背景生效中: 课程块 0.97 微透呼应壁纸
    today: LocalDate,
    colorMap: Map<String, Int>,
    pagerState: PagerState,   // 组合期不读: 落场判定由 DayColumn 在协程里读 settledPage
    onCourseClick: (Block) -> Unit,
    onAddAt: (Int, Int) -> Unit,
    ghosts: List<Ghost> = emptyList(),
    onGhostRestore: (Ghost) -> Unit = {},
) {
    val todayDay = today.dayOfWeek.value
    val isCurrentWeek = week == initialWeek
    val context = LocalContext.current
    val model = remember(dataset.periods) { SlotModel(dataset.periods) }
    val blocksByDay = remember(cls.courses, week) {
        // 色板按持久化注册表分配 (只增不减): 删除/恢复课程不影响其余课程颜色
        CourseColors.bindOrder(context, cls.courses.map { CourseColors.seedOf(it.kc, it.fx) })
        Merger.blocksOf(cls.courses, week).groupBy { it.course.day }
    }
    // 本周仍处生存期的虚影, 按天分组 (原课块的周次区间覆盖到本周才显示)
    val ghostsByDay = remember(ghosts, week) {
        ghosts.filter { it.coversWeek(week) }.groupBy { it.block.course.day }
    }

    // 网格内字号放大封顶 1.15: 课程块 11sp、时间轴 10sp 在系统大字体下仍不撑爆行高/列宽,
    // 同时保留大部分无障碍放大收益
    val baseDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(baseDensity.density, baseDensity.fontScale.coerceAtMost(1.15f)),
    ) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(LEFT_W))
            (1..7).forEach { day ->
                DayHeader(
                    day = day,
                    date = Weeks.md(Weeks.dateOf(dataset, week, day)),
                    isToday = day == todayDay && isCurrentWeek,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        val vScroll = rememberScrollState()
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
        val gridH = maxHeight
        // 晚上大节整周无课时收窄成细条: 高度只进 yTop/slotH 一处, 拖拽吸附/时间线全链路自动跟随
        val lastSlotJc = model.jcRange.lastOrNull()?.first ?: 0
        val eveUsed = blocksByDay.values.any { list -> list.any { it.startJc + it.span - 1 >= lastSlotJc } }
        val eveH = if (eveUsed) EVE_H else 20.dp
        val mainH = ((gridH - BREAK_H * model.breaks.size - eveH) / model.mainSlots)
            .coerceAtLeast(64.dp)
        val yTop = remember(model, mainH, eveH) {
            val ys = mutableListOf<Dp>()
            var y = 0.dp
            for (k in 0 until model.slots) {
                if (k in model.breaks) y += BREAK_H
                ys += y
                y += if (k < model.mainSlots) mainH else eveH
            }
            ys
        }
        val totalH = (yTop.lastOrNull() ?: 0.dp) + eveH
        val slotH: (Int) -> Dp = { k -> if (k < model.mainSlots) mainH else eveH }
        // 作息极端缺失时 yTop 可能为空/越界: 兜底取 0dp 保证不崩 (正常数据走不到)
        val yOfJc: (Int) -> Dp = { jc ->
            val k = (jc - 1) / 2
            (yTop.getOrElse(k) { 0.dp }) + (((jc - 1) % 2) * (slotH(k) / 2))
        }
        val yBottomJc: (Int) -> Dp = { jc ->
            val k = (jc - 1) / 2
            (yTop.getOrElse(k) { 0.dp }) + ((((jc - 1) % 2) + 1) * (slotH(k) / 2))
        }

        // 当前进行中的大节 (左侧时间轴加粗高亮用); 课间/午休/放学后 = -1 不高亮
        val activeSlot = if (isCurrentWeek)
            model.times.indexOfFirst { (s, e) -> nowMin in s until e } else -1
        // 当前时间线: 有课时段按真实分钟比例走; 课间/午休钳到上一节槽底, 早八前贴顶;
        // 晚上最后一节结束后隐藏, 翻到非本周也隐藏
        val nowY: Dp? = if (!isCurrentWeek || model.times.isEmpty()) null else {
            val idx = model.times.indexOfFirst { (s, e) -> nowMin in s until e }
            when {
                idx >= 0 -> {
                    val (s, e) = model.times[idx]
                    val frac = ((nowMin - s).toFloat() / (e - s)).coerceIn(0f, 1f)
                    yTop[idx] + slotH(idx) * frac
                }
                nowMin < model.times.first().first -> yTop[0]
                nowMin >= model.times.last().second -> null
                else -> {
                    val k = model.times.indexOfLast { it.second <= nowMin }
                    yTop[k] + slotH(k)
                }
            }
        }

        val weekHasCourse = remember(blocksByDay) { blocksByDay.values.any { it.isNotEmpty() } }
        Box(Modifier.fillMaxSize().verticalScroll(vScroll)) {
            Box(Modifier.fillMaxWidth().height(totalH)) {
                // 空周表态: 假期/调休周整周无课时给明确文案
                if (!weekHasCourse) {
                    // 作息极端缺失时 yTop 可能为空表: 兜底取 0dp 保证不崩 (正常数据走不到, 与 yOfJc 同款防御)
                    val top = yTop.firstOrNull() ?: 0.dp
                    val bottom = yTop.lastOrNull() ?: top
                    Column(
                        Modifier.align(Alignment.TopCenter).offset(y = top + ((bottom - top).coerceAtLeast(160.dp)) / 3),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "本周没有课",
                            fontSize = 15.sp, fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "假期愉快，翻回有课的周次看看吧",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
                // 大课间分隔带(画在课程卡下层, 全天连堂课不会被切断)
                for (k in model.breaks) {
                    val prevEnd = dataset.periods.first { it.jc == model.jcRange[k - 1].second }.end
                    val nextStart = dataset.periods.first { it.jc == model.jcRange[k].first }.start
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .offset(y = yTop[k] - BREAK_H)
                            .height(BREAK_H),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp)
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        )
                        Text(
                            "$prevEnd – $nextStart",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp),
                        )
                    }
                }
                Row(Modifier.fillMaxSize()) {
                    // 左侧时间轴: 开始时间贴槽顶, 节次居中, 结束时间贴槽底
                    Box(Modifier.width(LEFT_W).fillMaxHeight()) {
                        // 苹果日历同款: 被时刻胶囊压住的档位标签直接不画, 胶囊让开后自动恢复。
                        // 胶囊纵带 ≈ nowY ± 11dp; 标签文字高按 14dp 近似
                        val pillTop = nowY?.minus(11.dp)
                        val pillBottom = nowY?.plus(11.dp)
                        fun occluded(labelTop: Dp): Boolean =
                            pillTop != null && labelTop < pillBottom!! && (labelTop + 14.dp) > pillTop
                        for (k in 0 until model.slots) {
                            val (j1, j2) = model.jcRange[k]
                            val tsStr = dataset.periods.first { it.jc == j1 }.start
                            val teStr = dataset.periods.first { it.jc == j2 }.end
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .offset(y = yTop[k])
                                    .height(slotH(k)),
                            ) {
                                if (slotH(k) < 40.dp) {
                                    // 收窄的空末节细条放不下三行时刻, 只显示居中节次号
                                    if (!occluded(yTop[k] + (slotH(k) - 14.dp) / 2)) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text(
                                                "$j1-$j2",
                                                fontSize = 10.sp,
                                                fontWeight = if (k == activeSlot) FontWeight.Bold else FontWeight.Medium,
                                                color = if (k == activeSlot) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                } else {
                                    // 三行标签各自绝对定位 (顶/中/底): 某一档被胶囊压住时
                                    // 其余两档位置不动, 不因 SpaceBetween 重排
                                    if (!occluded(yTop[k])) {
                                        Text(
                                            tsStr,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.align(Alignment.TopCenter),
                                        )
                                    }
                                    if (!occluded(yTop[k] + (slotH(k) - 14.dp) / 2)) {
                                        Text(
                                            "$j1-$j2",
                                            fontSize = 10.sp,
                                            fontWeight = if (k == activeSlot) FontWeight.Bold else FontWeight.Medium,
                                            color = if (k == activeSlot) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.align(Alignment.Center),
                                        )
                                    }
                                    if (!occluded(yTop[k] + slotH(k) - 14.dp)) {
                                        Text(
                                            teStr,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.align(Alignment.BottomCenter),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // 七天课程列
                    Row(Modifier.weight(1f).fillMaxHeight()) {
                        (1..7).forEach { day ->
                            DayColumn(
                                day = day,
                                isToday = day == todayDay && isCurrentWeek,
                                model = model,
                                yTop = yTop,
                                slotH = slotH,
                                yOfJc = yOfJc,
                                yBottomJc = yBottomJc,
                                blocks = blocksByDay[day].orEmpty(),
                                colorMap = colorMap,
                                onCourseClick = onCourseClick,
                                onAddAt = onAddAt,
                                blockAlpha = if (hasBg) 0.97f else 1f,
                                week = week,
                                pagerState = pagerState,
                                ghosts = ghostsByDay[day].orEmpty(),
                                onGhostRestore = onGhostRestore,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
                // 当前时间线 (苹果日历同款): 画在课程列之后 = 悬浮最上层, 跨过课程块也始终可见;
                // 左侧时刻胶囊 + 自胶囊向右延伸的细线; 末节贴底时钳到网格内, 避免被底边裁掉半截。
                // nowY 每 30s 步进一次会瞬间跳变 → animateDpAsState 在 400ms 内线性滑到新位置;
                // 放课后/翻周消失时淡出, 记住最后位置避免从顶部闪回。
                var lastY by remember { mutableStateOf<Dp?>(null) }
                if (nowY != null) lastY = nowY
                val shownY = nowY ?: lastY
                // 全限定调用: 这里处于 Column→BoxWithConstraints→Box 三层嵌套,
                // ColumnScope.AnimatedVisibility 扩展会被远层隐式接收器抢解析(编译错), 故用顶层函数
                androidx.compose.animation.AnimatedVisibility(
                    visible = nowY != null,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200)),
                ) {
                    if (shownY != null) {
                        val animY by animateDpAsState(
                            targetValue = shownY,
                            animationSpec = tween(400, easing = LinearEasing),
                            label = "nowYLine",
                        )
                        // lambda 版 offset: 动画值只在放置期读取, 400ms 滑动每帧不再重组本作用域
                        // (组合期读值曾让滑动逐帧重跑时间线并重建渐变 Brush, 跨今天边界翻周卡顿的主源)
                        // 外层盒 20dp 高给胶囊容身; 顶部钳位保证胶囊不出网格
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .offset {
                                    IntOffset(
                                        0,
                                        (animY - 9.dp).coerceIn(0.dp, (totalH - 20.dp).coerceAtLeast(0.dp)).roundToPx(),
                                    )
                                }
                                .height(20.dp)
                                .padding(start = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 时刻胶囊: 主题色底白字时刻 (苹果日历同款)。
                            // 固定高 + 居中对齐 + 关字体补白: 数字不随字体度量上下偏
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 7.dp)
                                    .height(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${nowMin / 60}:${(nowMin % 60).toString().padStart(2, '0')}",
                                    fontSize = 10.sp,
                                    lineHeight = 10.sp,   // 压平行盒: LocalTextStyle 自带 24sp 行高会把数字坠底
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    maxLines = 1,
                                    style = LocalTextStyle.current.copy(
                                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                                    ),
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            // 横线: 自胶囊右缘向右延伸到最右
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(2.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                                            ),
                                        ),
                                        RoundedCornerShape(2.dp),
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
    }
    }
}

@Composable
private fun DayHeader(day: Int, date: String, isToday: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.padding(bottom = 4.dp), contentAlignment = Alignment.Center) {
        if (isToday) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    Weeks.cnDay(day),
                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    date,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    Weeks.cnDay(day),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    date,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun DayColumn(
    day: Int,
    isToday: Boolean,
    model: SlotModel,
    yTop: List<Dp>,
    slotH: (Int) -> Dp,
    yOfJc: (Int) -> Dp,
    yBottomJc: (Int) -> Dp,
    blocks: List<Block>,
    colorMap: Map<String, Int>,
    onCourseClick: (Block) -> Unit,
    onAddAt: (Int, Int) -> Unit,
    blockAlpha: Float = 1f,   // 有自定义背景时 0.97 微透, 色块透出一点壁纸呼吸感
    week: Int = 1,            // 本列所属周: 落场判定与 settledPage 比较
    pagerState: PagerState,   // 落场判定在协程内读 settledPage, 不在组合期订阅
    ghosts: List<Ghost> = emptyList(),
    onGhostRestore: (Ghost) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val dark = CourseColors.isDark(cs)
    // 落场: 本页以「当前已落定页」身份首次组合时逐列淡入 (App 打开首屏, 周一先到,
    // 每列错 25ms, 200ms 完成)。
    // 翻周滑入的页在滑动全程已可见, 落定后再整页从 0 重放淡入只会像闪一下;
    // 旧实现把 settledPage+1 当组合期键传进每一页, 每次落定把 3 个已组合页全部拉进重组 —— 翻周卡顿主源之一
    val staggerAlpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        if (pagerState.settledPage + 1 != week) return@LaunchedEffect
        staggerAlpha.snapTo(0f)
        delay((day - 1) * 25L)
        staggerAlpha.animateTo(1f, tween(200))
    }
    Box(modifier.padding(horizontal = 1.dp)) {
        // 今天列高亮: 半透明蓝色面板铺满整列, 标识「今天这一列」。
        // 必须铺满全列高度 —— 旧实现只铺到最后一节课底部, 晚上部分露底,
        // 看起来像面板没盖完/被裁掉
        if (isToday) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .background(cs.primary.copy(alpha = 0.10f)),
            )
        }
        // 大节空槽框线 (点击空白格 = 在此添加课程)
        for (k in 0 until model.slots) {
            // 按压缩放: 轻微 0.97 回弹, 给空白格一点「按到了」的实感
            val slotIsrc = remember { MutableInteractionSource() }
            val slotPressed by slotIsrc.collectIsPressedAsState()
            val slotScale by animateFloatAsState(
                targetValue = if (slotPressed) 0.97f else 1f,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                label = "slotPress",
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = slotScale
                        scaleY = slotScale
                    }
                    .offset(y = yTop[k] + 2.dp)
                    .padding(horizontal = 1.dp)
                    .height((slotH(k) - 6.dp).coerceAtLeast(12.dp))
                    .border(1.dp, CourseColors.gridStroke(cs), RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))  // 空格子高亮与虚线框同角
                    .clickable(
                        interactionSource = slotIsrc,
                        indication = ripple(),
                        onClickLabel = "添加课程",
                    ) { onAddAt(day, model.jcRange[k].first) }
                    .semantics {
                        contentDescription = "${Weeks.cnDay(day)}第${model.jcRange[k].first}节，双击添加课程"
                    },
            )
        }
        // 虚影卡片: 删除/移位后的原位置残影 —— 全部使用同一套灰色 + 虚线描边,
        // 与彩色正式卡片拉开对比; 生存期内点按即恢复, 画在真实课程块下层
        ghosts.forEach { g ->
            val gb = g.block
            val gTop = yOfJc(gb.startJc)
            val gH = yBottomJc(gb.startJc + gb.span - 1) - gTop - 4.dp
            val gShape = RoundedCornerShape(12.dp)
            val gLine = cs.onSurfaceVariant
            Box(
                Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, (gTop + 2.dp).roundToPx()) }
                    .height(gH)
                    .background(cs.onSurfaceVariant.copy(alpha = 0.14f), gShape)
                    .drawBehind {
                        drawRoundRect(
                            color = gLine.copy(alpha = 0.85f),
                            style = Stroke(
                                width = 1.2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 7f)),
                            ),
                            cornerRadius = CornerRadius(12.dp.toPx()),
                        )
                    }
                    .clip(gShape)
                    .clickable(onClickLabel = "恢复课程") { onGhostRestore(g) }
                    .semantics {
                        val what = if (g.kind == EditSession.GHOST_DELETED) "已删除" else "已移动"
                        contentDescription = "$what 的课程 ${gb.course.kc}，点按恢复"
                    },
            ) {
                BlockContent(gb.course.kc, gb.course.room, gLine)
                // 角标放右下: 避开居中的课程名
                Text(
                    if (g.kind == EditSession.GHOST_DELETED) "已删除" else "已移动",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = cs.background,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(gLine.copy(alpha = 0.9f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        // 课程块(按真实小节定位): 渐变 + 柔影, 顶部微微提亮出光泽;
        // 整列块包进单层 graphicsLayer 做落场淡入 —— 每帧只更新一层 alpha,
        // 不再让 40+ 块各自的层属性逐帧失效(翻周卡顿修复)
        Box(Modifier.graphicsLayer { alpha = staggerAlpha.value }) {
        blocks.forEach { b ->
            val seed = CourseColors.seedOf(b.course.kc, b.course.fx)
            val (container, onContainer) = CourseColors.pair(cs, seed, colorMap[seed])
            val top = yOfJc(b.startJc)
            val h = yBottomJc(b.startJc + b.span - 1) - top - 4.dp
            val shape = RoundedCornerShape(12.dp)
            val cardBg = Modifier
                .shadow(if (dark) 3.dp else 4.dp, shape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            lerp(container, Color.White, if (dark) 0.08f else 0.12f)
                                .copy(alpha = blockAlpha),
                            container.copy(alpha = blockAlpha),
                        ),
                    ),
                    shape,
                )
                .border(
                    0.8.dp,
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (dark) 0.16f else 0.34f),
                            Color.White.copy(alpha = if (dark) 0.04f else 0.08f),
                        ),
                    ),
                    shape,
                )
            Box(
                Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, (top + 2.dp).roundToPx()) }
                    .height(h)
                    .then(
                        cardBg
                            .clip(shape).clickable(
                                onClickLabel = "查看课程详情",
                                onClick = { onCourseClick(b) },
                            )  // 高亮裁到课程块形状
                            .semantics {
                                val roomPart = if (b.course.room.isNotEmpty()) "，${b.course.room}" else ""
                                contentDescription =
                                    "${b.course.kc}，${Weeks.cnDay(b.course.day)}第${b.startJc}-${b.startJc + b.span - 1}节$roomPart"
                            },
                    ),
            ) {
                BlockContent(b.course.kc, b.course.room, onContainer)
                // 用户改过/自建的课: 右上角小圆点标识
                if (b.course.editId != null) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                            .size(4.dp)
                            .background(onContainer.copy(alpha = 0.85f), CircleShape),
                    )
                }
            }
        }
        }
    }
}

/** 顶栏玻璃形状: 顶部贴屏幕边缘, 底部两角收圆 —— 消除整条方框感 */
private val TopBarShape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)

/**
 * 液态玻璃边缘高光: 顶部亮、中段渐隐、底部微亮的 1dp 描边。
 * 有它玻璃表面才有「厚度」—— 否则毛玻璃糊在壁纸上, 与壁纸融为一体没有层次。
 */
private fun Modifier.glassEdge(shape: Shape, dark: Boolean, width: Dp = 1.dp, strength: Float = 1f): Modifier =
    this.border(
        width,
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = (if (dark) 0.14f else 0.42f) * strength),
                Color.Transparent,
                Color.White.copy(alpha = (if (dark) 0.05f else 0.08f) * strength),
            ),
        ),
        shape,
    )

/** 流光笔刷: x∈[0,1] 为相位, 0=屏外左 1=屏外右(全程穿屏, 循环重置不可见), alpha 为光带强度;
 *  深色底用白光带, 浅色底白光不可见, 用黑光带(强度×0.6) */
private fun colShimmer(x: Float, alpha: Float, dark: Boolean): Brush = Brush.linearGradient(
    listOf(
        Color.Transparent,
        (if (dark) Color.White else Color.Black).copy(alpha = if (dark) alpha else alpha * 0.6f),
        Color.Transparent,
    ),
    start = Offset(x * 2400f - 500f, -120f),
    end = Offset(x * 2400f - 100f, 520f),
)
