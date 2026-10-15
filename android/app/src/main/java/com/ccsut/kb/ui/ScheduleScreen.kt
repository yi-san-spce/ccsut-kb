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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ripple
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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
import kotlin.math.roundToInt

private val BREAK_H = 22.dp      // 大课间(午休/晚饭)分隔带高度
private val EVE_H = 88.dp        // 晚上大节槽位高度 (整周无晚课时收窄为细条, 见 WeekGrid.eveH)
private val LEFT_W = 42.dp       // 左侧时间轴: 10sp 刻度可读的最小宽度

/** 拖拽药片的抬升缩放: graphicsLayer 在 pointerInput 外层, positionChange 是缩放空间值,
 *  累计位移必须按它还原成屏幕位移, 否则药片永远比手指慢 4.8% */
private const val PILL_SCALE = 1.05f

/** 拖拽块的生效节数: 本地编辑会话可能只占融合连堂的一部分(Weeks.editSpan), 未指定(0/越界)按原始 span 处理 */
private fun Block.effSpan(): Int = if (editSpan in 1..span) editSpan else span

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

/** 课程块文字: 块本体与拖拽幽灵共用同一份排版, 内边距 6dp 给小字留出呼吸空间 */
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

/**
 * 长按拖拽换位的会话状态 (ScheduleScreen 级, 全周页共享)。raw: 拖拽累计位移, anchorDay: 锚定列 (初始 = 课程原 day);
 * settleStart/targetOffset: 松手帧药片的最终视觉偏移, 两者相等 → settle 协程 snapTo 后 animateTo 瞬时完成;
 * pendingMove: settle 结束后才提交 onMoveBlock, 提交瞬间数据位置变化与视觉归零同帧重合, 消除松手帧跳。
 */
/** 一次待提交的拖拽移动: week=拖拽起始周, 数据层只移动该周的这节课 */
private data class PendingMove(val block: Block, val day: Int, val jc: Int, val week: Int)

private class DragHost {
    var block by mutableStateOf<Block?>(null)
    var dragWeek by mutableIntStateOf(0)
    var raw by mutableStateOf(Offset.Zero)
    var anchorDay by mutableIntStateOf(0)
    var targetDay by mutableIntStateOf(0)
    var targetJc by mutableIntStateOf(0)
    var settling by mutableStateOf(false)
    var targetOffset by mutableStateOf(Offset.Zero)
    var pendingMove by mutableStateOf<PendingMove?>(null)
    // 松手首帧: dragEnd 在手势线程同步算好最终视觉偏移, 协程 snapTo 前的重组帧不会闪回原位
    var settleStart by mutableStateOf(Offset.Zero)
    var settleStarted by mutableStateOf(false)
    // 提交钉住: 回弹动画结束后数据还没落地(异步 IO), 视觉钉在新槽位等 dataTick 同帧换块,
    // 消除「旧块闪回原位一帧再跳到新位」的抽搐
    var pinning by mutableStateOf(false)
    var pinnedOffset by mutableStateOf(Offset.Zero)
    // 最近一次手势活动时间 (System.nanoTime), 供看门狗判断手势协程是否已被翻页连带杀死
    var lastActiveAt: Long = 0L
    // 会话拆分拖拽 (混合融合块): 按抓握位置选中某段 session 拎起, 其余段原地留置
    var splitSource by mutableStateOf<Block?>(null)   // 非 null = 拆分拖拽中, 值 = 被拆的原融合卡
    var sessionBlock by mutableStateOf<Block?>(null)  // 被拎起的 session 合成块 (提交移动用它)
    // 本次拖拽的起始周: 拖拽锁定在当前周页内, 落点只可能是本周的格子 (跨周页不进入拖拽会话)
    var originWeek by mutableIntStateOf(0)
    // 每次提交移动(onMoveBlock)自增: 钉住解锁的兜底信号 —— 同内容提交(如拖回原状)时
    // cls.courses 内容不变, 数据帧永远不来, 靠它 + 短延时兜底解锁翻页
    var commitGen by mutableIntStateOf(0)
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
    onMoveBlock: (Block, Int, Int, Int) -> Unit,
) {
    // 长按拖拽会话: 期间禁用周翻页, 避免父级抢手势
    val drag = remember { DragHost() }
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
            userScrollEnabled = drag.block == null,
            // 拖拽期间锁定当前周页: 手势协程住在当前页的课程块里,
            // 页面被销毁 = 手势死亡 = 拖拽失焦卡死 (看门狗只是兜底)
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
                drag = drag,
                pagerState = pagerState,
                onCourseClick = onCourseClick,
                onAddAt = { d, j -> onAddAt(d, j, page + 1) },
                onMoveBlock = onMoveBlock,
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
    modifier: Modifier = Modifier,
) {
    // 浅色底流光用黑光带才可见; 无限动画本体在 ShimmerBand 内驱动
    val dark = CourseColors.isDark(MaterialTheme.colorScheme)

    Surface(
        color = if (haze != null) Color.Transparent else barBg,
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
                            tints = listOf(HazeTint(barBg.copy(alpha = 0.45f))),
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
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
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
    drag: DragHost,
    pagerState: PagerState,   // 组合期不读: 落场判定由 DayColumn 在协程里读 settledPage
    onCourseClick: (Block) -> Unit,
    onAddAt: (Int, Int) -> Unit,
    onMoveBlock: (Block, Int, Int, Int) -> Unit,   // (block, day, jc, week) —— week=拖拽起始周
) {
    val todayDay = today.dayOfWeek.value
    val isCurrentWeek = week == initialWeek
    val ctx = LocalContext.current
    val model = remember(dataset.periods) { SlotModel(dataset.periods) }
    val blocksByDay = remember(cls.courses, week) {
        // 色板按全学期课程排序分配, 不同课程必不同色 (小组件绑同一份 ClassCache 排序)
        CourseColors.bindOrder(cls.courses.map { CourseColors.seedOf(it.kc, it.fx) })
        Merger.blocksOf(cls.courses, week).groupBy { it.course.day }
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

        // ---- 长按拖拽换位: 锚定列吸附 + 松手即落位、settle 结束才提交移动 (无帧跳) ----
        val density = LocalDensity.current
        val haptic = LocalHapticFeedback.current
        val settleAnim = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
        // 单列宽度(px): 跨列位移换算与吸附计算共用
        val colW = with(density) { ((maxWidth - LEFT_W) / 7).toPx() }
        // 单列宽(Dp): 跨周幽灵药片的宽度用 (与 DayColumn 列内块等宽, 减左右各 1dp)
        val colWidthDp = (maxWidth - LEFT_W) / 7 - 2.dp
        // 松手即落位: settleStart 由 dragEnd 同步定为最终视觉位置(落位偏移或零),
        // 协程 snapTo 后 animateTo(同值) 瞬时完成, 药片直接出现在最终位置。
        // 只在拖拽周所在页执行, 其余页的组合里直接返回, 避免重复动画/重复提交。
        LaunchedEffect(drag.settling) {
            if (!drag.settling || week != drag.dragWeek) return@LaunchedEffect
            settleAnim.snapTo(drag.settleStart)
            drag.settleStarted = true
            settleAnim.animateTo(
                drag.targetOffset,
                spring(
                    dampingRatio = 1f,   // 临界阻尼: 平滑滑入无过冲 (欠阻尼会冲过头再晃回来 = 抽搐)
                    stiffness = Spring.StiffnessMediumLow,
                    visibilityThreshold = Offset(1f, 1f),
                ),
            )
            drag.settleStarted = false
            val pm = drag.pendingMove
            drag.settling = false
            if (pm == null) {
                drag.block = null
                drag.raw = Offset.Zero
                drag.splitSource = null
                drag.sessionBlock = null
                drag.originWeek = 0
                drag.pendingMove = null
            } else {
                // 钉住视觉在新槽位, 先提交移动; 新数据(cls.courses)落地那一帧,
                // 旧 Block 实例被新课表替换, 视觉无缝交接
                drag.pinning = true
                drag.pinnedOffset = drag.targetOffset
                onMoveBlock(pm.block, pm.day, pm.jc, pm.week)
                // 兜底解锁信号: 同内容提交(拖回原状/还原)时数据帧不来, 见下方 commitGen effect
                drag.commitGen++
            }
        }
        // 钉住解除(主路径): 新数据落地(任一课表数据变化)即清拖拽会话, 解锁翻页
        LaunchedEffect(cls.courses) {
            if (drag.pinning) {
                drag.pinning = false
                drag.block = null
                drag.raw = Offset.Zero
                drag.splitSource = null
                drag.sessionBlock = null
                drag.originWeek = 0
                drag.pendingMove = null
            }
        }
        // 钉住解除(兜底): 移动被提交但数据内容不变(cls.courses 不触发重组)时,
        // 数据帧永远等不来, 翻页会锁死到看门狗 6 秒强拆 —— commitGen + 短延时兜底。
        // 仅在「数据不变」场景走到延时, 此时钉住的视觉位置就是数据位置, 释放无可见跳变。
        LaunchedEffect(drag.commitGen) {
            if (drag.commitGen == 0) return@LaunchedEffect
            delay(200)
            if (drag.pinning) {
                drag.pinning = false
                drag.block = null
                drag.raw = Offset.Zero
                drag.splitSource = null
                drag.sessionBlock = null
                drag.originWeek = 0
                drag.pendingMove = null
            }
        }
        // 兜底看门狗: 若手势协程被意外取消, onDragEnd/onDragCancel 都不会再回调,
        // 拖拽会话会卡死 (pager 一直禁翻页)。长时间无手势进展时强制复位。
        LaunchedEffect(drag.block, drag.dragWeek) {
            if (drag.block == null || week != drag.dragWeek) return@LaunchedEffect
            while (true) {
                delay(2000)
                if (drag.block == null || drag.settling) break
                if (System.nanoTime() - drag.lastActiveAt > 6_000_000_000L) {
                    drag.pinning = false
                    drag.splitSource = null
                    drag.sessionBlock = null
                    drag.targetOffset = Offset.Zero
                    drag.pendingMove = null
                    drag.settling = true   // 走回弹动画归位, 数据不动
                    break
                }
            }
        }
        // 药片拖拽/回弹期间叠加的位移 (布局期读取, 不触发重组):
        // 视觉 = raw + (anchorDay - 课程原day) × 列宽 (拖拽锁定在本周页内)
        val visualOf: (Block) -> Offset = { b ->
            val d = drag.block
            if (d == null || b != d || week != drag.dragWeek) Offset.Zero
            else if (drag.pinning) drag.pinnedOffset
            else if (drag.settling) if (drag.settleStarted) settleAnim.value else drag.settleStart
            else Offset(
                drag.raw.x + (drag.anchorDay - b.course.day) * colW,
                drag.raw.y,
            )
        }
        // 抬升程度 0..1: 拖拽中 1, 松手即落位/钉住等待提交均视为已落地 → 0
        val liftOf: (Block) -> Float = { b ->
            val d = drag.block
            if (d != null && b == d && week == drag.dragWeek) {
                if (drag.pinning || drag.settling) 0f else 1f
            } else 0f
        }
        val dragStart: (Block, Offset) -> Unit = dragStart@{ b, grab ->
            // 已有拖拽进行中(如第二根手指误触)不接受新会话
            if (drag.block == null) {
                drag.anchorDay = b.course.day
                drag.raw = Offset.Zero
                drag.settling = false
                drag.pinning = false
                drag.pendingMove = null
                drag.targetDay = b.course.day
                drag.targetJc = b.startJc
                drag.dragWeek = week
                drag.originWeek = week   // 拖拽锁定本周, 起始周即当前周页
                // 会话拆分: 混合融合块 (自建课与学校课贴在一起) 按抓握位置选中一段 session 拎起,
                // 其余段原地留置 —— 按上段动上段, 按下段动下段; 单 session 块整卡一起动
                if (b.sessions.size > 1) {
                    val picked = with(density) {
                        val grabPx = grab.y
                        b.sessions.firstOrNull { r ->
                            grabPx >= yOfJc(r.startJc).toPx() && grabPx < yBottomJc(r.startJc + r.span - 1).toPx()
                        } ?: b.sessions.last()
                    }
                    if (picked.span < b.span) {
                        drag.splitSource = b
                        drag.sessionBlock = Block(picked.course, picked.startJc, picked.span, editSpan = picked.span)
                        drag.targetJc = picked.startJc
                    }
                }
                drag.block = b
                drag.lastActiveAt = System.nanoTime()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
        val dragMove: (Block, Offset) -> Unit = dragMove@{ b, amt ->
            // 会话互斥: 非本会话块的手指事件 (第二根手指长按到另一门课) 一律忽略
            if (drag.block == null || b != drag.block) return@dragMove
            // amt 在 1.05 缩放空间里, 换算回屏幕位移再累计 (见 PILL_SCALE 注释)
            drag.raw += amt * PILL_SCALE
            // 拖拽节数: 会话拆分时用被拎起段的节数, 否则用生效节数(编辑会话可能只占连堂一部分)
            val span = drag.sessionBlock?.span ?: drag.block?.effSpan() ?: b.span
            // 列吸附: 锚定列 + 累计位移换算成 0-based 连续列, 四舍五入得目标天
            val col = (drag.anchorDay - 1) + drag.raw.x / colW
            drag.targetDay = (col.roundToInt() + 1).coerceIn(1, 7)
            with(density) {
                val fingerY = yOfJc(b.startJc) + drag.raw.y.toDp()
                var k = 0
                for (i in 0 until model.slots) if (yTop[i] <= fingerY) k = i
                // 骑缝杜绝: 多节卡必须对齐大节起点, 不再出现骑在两节课之间或午休中轴线上的落点;
                // 单节卡不存在骑缝, 保留大节上下半格的自由
                drag.targetJc = if (span <= 1) {
                    val half = ((fingerY - yTop[k]) / (slotH(k) / 2)).toInt().coerceIn(0, 1)
                    (2 * k + 1 + half).coerceIn(1, dataset.periods.size - span + 1)
                } else {
                    model.jcRange[k].first.coerceIn(1, dataset.periods.size - span + 1)
                }
            }
            // 拖拽锁定在当前周页内: 不做边缘翻周, 松手落点只可能是本周的格子
            drag.lastActiveAt = System.nanoTime()
        }
        val dragEnd: (Block) -> Unit = dragEnd@{ b ->
            // 会话互斥: 非本会话块的抬手一律忽略
            if (drag.block == null || b != drag.block) return@dragEnd
            // 防御: 落点必须在起始周页内 (拖拽已锁定本周, 此处恒为 false)
            val navigating = drag.dragWeek != drag.originWeek
            // 会话拆分: 拎起的是混合融合块里的某一段, 移动判定/占用检测/提交都以该段为准
            val sb = drag.sessionBlock
            var moved = !navigating && if (sb != null) {
                drag.targetDay != sb.course.day || drag.targetJc != sb.startJc
            } else {
                drag.targetDay != b.course.day || drag.targetJc != b.startJc
            }
            // 落点占用检测: 当前周视图里节次区间重叠即绝对冲突 (两块卡片绝对定位完全重叠,
            // 下层不可见不可点)。拆分拖拽时, 同卡里被留下的 session 段也算占用
            if (moved) {
                val mSpan = sb?.span ?: b.span
                val occupied = blocksByDay[drag.targetDay].orEmpty().any { o ->
                    o != b && o.startJc < drag.targetJc + mSpan && drag.targetJc < o.startJc + o.span
                } || (sb != null && drag.targetDay == b.course.day &&
                    b.sessions.filter { it.startJc != sb.startJc }.any { r ->
                        r.startJc < drag.targetJc + mSpan && drag.targetJc < r.startJc + r.span
                    })
                if (occupied) {
                    android.widget.Toast.makeText(
                        ctx, "该时段已有课程，换个位置试试", android.widget.Toast.LENGTH_SHORT,
                    ).show()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    moved = false
                }
            }
            // 松手帧的最终视觉偏移 (= settleStart = targetOffset): 落位取目标槽位偏移,
            // 否则取零 —— 协程 snapTo 后 animateTo 同值瞬时完成, 药片直接出现在最终位置不滑行。
            // 拆分拖拽的药片锚在被拎起段的起点 (渲染层同锚), 归位/落位都相对它计算
            val anchorTop = (sb ?: b).startJc
            val target = with(density) {
                Offset(
                    (drag.targetDay - b.course.day) * colW,
                    (yOfJc(drag.targetJc) - yOfJc(anchorTop)).toPx(),
                )
            }
            drag.settleStart = if (moved) target else Offset.Zero
            drag.targetOffset = drag.settleStart
            drag.pendingMove = if (moved) PendingMove(sb ?: b, drag.targetDay, drag.targetJc, drag.originWeek) else null
            drag.settling = true
            if (moved) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        val dragCancel: () -> Unit = {
            val d = drag.block
            drag.settleStart = if (d != null) {
                Offset(drag.raw.x + (drag.anchorDay - d.course.day) * colW, drag.raw.y)
            } else Offset.Zero
            drag.targetOffset = Offset.Zero
            drag.pendingMove = null
            drag.settling = true
        }

        val weekHasCourse = remember(blocksByDay) { blocksByDay.values.any { it.isNotEmpty() } }
        Box(Modifier.fillMaxSize().verticalScroll(vScroll, enabled = drag.block == null)) {
            Box(Modifier.fillMaxWidth().height(totalH)) {
                // 空周表态: 假期/调休周整周无课时给明确文案
                if (!weekHasCourse) {
                    Column(
                        Modifier.align(Alignment.TopCenter).offset(y = yTop[0] + ((yTop.last() - yTop[0]).coerceAtLeast(160.dp)) / 3),
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
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            "$j1-$j2",
                                            fontSize = 10.sp,
                                            fontWeight = if (k == activeSlot) FontWeight.Bold else FontWeight.Medium,
                                            color = if (k == activeSlot) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                } else {
                                    Column(
                                        Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(tsStr, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                        Text(
                                            "$j1-$j2",
                                            fontSize = 10.sp,
                                            fontWeight = if (k == activeSlot) FontWeight.Bold else FontWeight.Medium,
                                            color = if (k == activeSlot) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(teStr, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
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
                                showTarget = week == drag.originWeek,
                                model = model,
                                yTop = yTop,
                                slotH = slotH,
                                yOfJc = yOfJc,
                                yBottomJc = yBottomJc,
                                blocks = blocksByDay[day].orEmpty(),
                                colorMap = colorMap,
                                drag = drag,
                                visualOf = visualOf,
                                liftOf = liftOf,
                                onDragStart = dragStart,
                                onDragMove = dragMove,
                                onDragEnd = dragEnd,
                                onDragCancel = dragCancel,
                                onCourseClick = onCourseClick,
                                onAddAt = onAddAt,
                                blockAlpha = if (hasBg) 0.97f else 1f,
                                week = week,
                                pagerState = pagerState,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
                // 当前时间线: 画在课程列之后 = 悬浮最上层, 跨过课程块也始终可见;
                // 末节贴底时钳到网格内, 避免线条/圆点被底边裁掉半截。
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
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .offset { IntOffset(0, animY.coerceAtMost(totalH - 2.dp).roundToPx()) }
                                .padding(start = LEFT_W)
                                .height(2.dp),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxSize()
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
                            Box(
                                Modifier
                                    .align(Alignment.CenterStart)
                                    .offset(x = (-2).dp, y = (-5).dp)
                                    .size(12.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(12.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), CircleShape),
                                )
                                Box(
                                    Modifier
                                        .align(Alignment.Center)
                                        .size(8.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                                )
                            }
                        }
                    }
                }
                // 跨周药片(幽灵): 翻周后块本体仍画在另一周页面, 本页按同一套视觉公式渲染幽灵
                // (跟随 raw/回弹/钉住), 与块本体互斥, 翻回原周自动切回;
                // landed 门控: 钉住期间新块可能已异步落地本页, 再画幽灵就是一帧双影, 检测到落位新块即停画。
                val dragBlock = drag.block
                // span 用 effSpan 比对: 混合融合块(kind=0 自建+学校同 key)提交只搬自建 session,
                // 落地块的 span == effSpan 而不是整块 span, 按整块比会漏判 → 落地帧双影
                val landed = dragBlock != null && drag.pinning &&
                    (blocksByDay[drag.targetDay]?.any {
                        it.course.kc == dragBlock.course.kc && it.course.day == drag.targetDay &&
                            it.startJc == drag.targetJc && it.span == dragBlock.effSpan()
                    } == true)
                val ghostBlock = dragBlock?.takeIf {
                    week == drag.dragWeek && !landed &&
                        blocksByDay.values.none { list -> list.any { b -> b === dragBlock } }
                }
                ghostBlock?.let { gb ->
                    val gcs = MaterialTheme.colorScheme
                    val gDark = CourseColors.isDark(gcs)
                    val seed = CourseColors.seedOf(gb.course.kc, gb.course.fx)
                    val (container, onContainer) = CourseColors.pair(gcs, seed, colorMap[seed])
                    val gShape = RoundedCornerShape(12.dp)
                    val gh = yBottomJc(gb.startJc + gb.span - 1) - yOfJc(gb.startJc) - 4.dp
                    Box(
                        Modifier
                            .graphicsLayer {
                                scaleX = PILL_SCALE
                                scaleY = PILL_SCALE
                                shadowElevation = 6f
                            }
                            .offset {
                                val v = visualOf(gb)
                                IntOffset(
                                    ((gb.course.day - 1) * colW + v.x).roundToInt(),
                                    ((yOfJc(gb.startJc) + 2.dp).toPx() + v.y).roundToInt(),
                                )
                            }
                            .width(colWidthDp)
                            .height(gh)
                            .shadow(if (gDark) 3.dp else 4.dp, gShape)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        lerp(container, Color.White, if (gDark) 0.08f else 0.12f)
                                            .copy(alpha = if (hasBg) 0.97f else 1f),
                                        container.copy(alpha = if (hasBg) 0.97f else 1f),
                                    ),
                                ),
                                gShape,
                            )
                            // 玻璃贴片高光: 色块从壁纸「立」起来的关键一笔
                            .border(
                                0.8.dp,
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = if (gDark) 0.16f else 0.34f),
                                        Color.White.copy(alpha = if (gDark) 0.04f else 0.08f),
                                    ),
                                ),
                                gShape,
                            ),
                    ) {
                        BlockContent(gb.course.kc, gb.course.room, onContainer)
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
    showTarget: Boolean,   // 是否渲染拖拽吸附高亮: 只在起始周页(跨周只看不放, 其它周不画落点)
    model: SlotModel,
    yTop: List<Dp>,
    slotH: (Int) -> Dp,
    yOfJc: (Int) -> Dp,
    yBottomJc: (Int) -> Dp,
    blocks: List<Block>,
    colorMap: Map<String, Int>,
    drag: DragHost,
    visualOf: (Block) -> Offset,
    liftOf: (Block) -> Float,
    onDragStart: (Block, Offset) -> Unit,  // Offset = 抓握点在块内的位置, 会话拆分按纵坐标选段
    onDragMove: (Block, Offset) -> Unit,
    onDragEnd: (Block) -> Unit,
    onDragCancel: () -> Unit,
    onCourseClick: (Block) -> Unit,
    onAddAt: (Int, Int) -> Unit,
    blockAlpha: Float = 1f,   // 有自定义背景时 0.97 微透, 色块透出一点壁纸呼吸感
    week: Int = 1,            // 本列所属周: 落场判定与 settledPage 比较
    pagerState: PagerState,   // 落场判定在协程内读 settledPage, 不在组合期订阅
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val dark = CourseColors.isDark(cs)
    // 落场: 本页以「当前已落定页」身份首次组合时逐列淡入 (App 打开首屏, 周一先到,
    // 每列错 25ms, 200ms 完成; 拖拽中不播放)。
    // 翻周滑入的页在滑动全程已可见, 落定后再整页从 0 重放淡入只会像闪一下;
    // 旧实现把 settledPage+1 当组合期键传进每一页, 每次落定把 3 个已组合页全部拉进重组 —— 翻周卡顿主源之一
    val staggerAlpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        if (pagerState.settledPage + 1 != week || drag.block != null) return@LaunchedEffect
        staggerAlpha.snapTo(0f)
        delay((day - 1) * 25L)
        staggerAlpha.animateTo(1f, tween(200))
    }
    Box(modifier.padding(horizontal = 1.dp)) {
        // 今天列高亮: 平色半透明条, 止于最后一节有课处;
        // 无课日不画条, 只靠表头胶囊标记
        if (isToday) {
            blocks.maxOfOrNull { yBottomJc(it.startJc + it.span - 1) }?.let { lastBottom ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(lastBottom + 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(cs.primary.copy(alpha = 0.10f)),
                )
            }
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
        // 拖拽落点吸附预览: 目标位置实时高亮 —— 从拖拽开始一直亮到数据落地随块一起消失,
        // 期间 (落位/钉住) 绝不能隐藏, 否则放置瞬间高亮"灭→亮→灭"两连闪 = 用户看到的闪烁抽搐。
        // 只在起始周页渲染 (showTarget), 高亮节数用生效节数(编辑会话可能只占连堂一部分)
        val dSpan = drag.sessionBlock?.span ?: drag.block?.effSpan()
        if (dSpan != null && showTarget && drag.targetDay == day) {
            val ty = yOfJc(drag.targetJc)
            val th = yBottomJc(drag.targetJc + dSpan - 1) - ty - 4.dp
            Box(
                Modifier
                    .fillMaxWidth()
                    .offset(y = ty + 2.dp)
                    .height(th)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        RoundedCornerShape(12.dp),
                    )
                    .border(
                        2.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        RoundedCornerShape(12.dp),
                    ),
            )
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
            // 会话拆分: 混合融合块被长按且按握位置选中了某一段 session
            val split = drag.splitSource == b && drag.sessionBlock != null
            // 按压缩放 + 轻震: 点按有「捏了一下」的实感; 拖拽由 liftOf 接管缩放, 松开回弹
            val blockHaptic = LocalHapticFeedback.current
            val pressIsrc = remember { MutableInteractionSource() }
            val pressed by pressIsrc.collectIsPressedAsState()
            LaunchedEffect(pressed) {
                if (pressed) blockHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            val pressScale by animateFloatAsState(
                targetValue = if (pressed) 0.97f else 1f,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                label = "blockPress",
            )
            // 外壳恒挂 pointerInput: 手势协程必须随原卡存活 (拆分渲染的碎片画在这层里面)
            Box(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        if (!split) {
                            val p = liftOf(b)
                            if (p > 0.01f) {
                                val sc = 1f + (PILL_SCALE - 1f) * p
                                scaleX = sc * pressScale
                                scaleY = sc * pressScale
                                shadowElevation = 6f * p
                            } else {
                                scaleX = pressScale
                                scaleY = pressScale
                            }
                        }
                    }
                    .offset {
                        if (!split) {
                            val v = visualOf(b)
                            IntOffset(
                                v.x.roundToInt(),
                                (top + 2.dp).roundToPx() + v.y.roundToInt(),
                            )
                        } else {
                            IntOffset(0, (top + 2.dp).roundToPx())
                        }
                    }
                    .height(h)
                    .then(
                        if (!split) cardBg
                            .clip(shape).clickable(
                                interactionSource = pressIsrc,
                                indication = ripple(),
                                onClickLabel = "查看课程详情",
                                onClick = { onCourseClick(b) },
                            )  // 高亮裁到课程块形状
                            .semantics {
                                val roomPart = if (b.course.room.isNotEmpty()) "，${b.course.room}" else ""
                                contentDescription =
                                    "${b.course.kc}，${Weeks.cnDay(b.course.day)}第${b.startJc}-${b.startJc + b.span - 1}节$roomPart"
                            }
                        else Modifier,
                    )
                    .pointerInput(b) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { pos -> onDragStart(b, pos) },
                            onDrag = { change, amt ->
                                change.consume()
                                onDragMove(b, amt)
                            },
                            onDragEnd = { onDragEnd(b) },
                            onDragCancel = { onDragCancel() },
                        )
                    },
            ) {
                if (split) {
                    val sb = drag.sessionBlock!!
                    // 其余 session 段: 原地留置并压暗, 与拎起的药片之间出现清晰断口
                    b.sessions.filter { it.startJc != sb.startJc }.forEach { r ->
                        val rTop = yOfJc(r.startJc) - top
                        val rH = yBottomJc(r.startJc + r.span - 1) - yOfJc(r.startJc) - 4.dp
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .offset(y = rTop + 2.dp)
                                .height(rH)
                                .graphicsLayer { alpha = 0.55f }
                                .then(cardBg)
                                .clip(shape),
                        ) {
                            BlockContent(b.course.kc, b.course.room, onContainer)
                        }
                    }
                    // 被拎起的 session 段: 抬升放大 + 重阴影跟手走, 视觉偏移锚在段起点
                    val sTop = yOfJc(sb.startJc) - top
                    val sH = yBottomJc(sb.startJc + sb.span - 1) - yOfJc(sb.startJc) - 4.dp
                    Box(
                        Modifier
                            .zIndex(1f)
                            .graphicsLayer {
                                scaleX = PILL_SCALE
                                scaleY = PILL_SCALE
                                shadowElevation = if (dark) 10f else 12f
                            }
                            .offset {
                                val v = visualOf(b)
                                IntOffset(v.x.roundToInt(), (sTop + 2.dp).roundToPx() + v.y.roundToInt())
                            }
                            .height(sH)
                            .shadow(if (dark) 8.dp else 10.dp, shape)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        lerp(container, Color.White, if (dark) 0.10f else 0.16f)
                                            .copy(alpha = blockAlpha),
                                        container.copy(alpha = blockAlpha),
                                    ),
                                ),
                                shape,
                            )
                            .border(
                                1.dp,
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = if (dark) 0.22f else 0.45f),
                                        Color.White.copy(alpha = if (dark) 0.06f else 0.12f),
                                    ),
                                ),
                                shape,
                            )
                            .clip(shape),
                    ) {
                        BlockContent(sb.course.kc, sb.course.room, onContainer)
                    }
                } else {
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
