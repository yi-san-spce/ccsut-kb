package com.ccsut.kb.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
private val EVE_H = 88.dp        // 晚上大节槽位高度
private val LEFT_W = 34.dp

/** 拖拽药片的抬升缩放: graphicsLayer 在 pointerInput 外层, positionChange 是缩放空间值,
 *  累计位移必须按它还原成屏幕位移, 否则药片永远比手指慢 4.8%, 贴边翻周永远差一截 */
private const val PILL_SCALE = 1.05f

/** 拖拽块的生效节数: 本地编辑会话可能只占融合连堂的一部分(Weeks.editSpan), 未指定(0/越界)按原始 span 处理 */
private fun Block.effSpan(): Int = if (editSpan in 1..span) editSpan else span

/**
 * 长按拖拽换位的会话状态 (ScheduleScreen 级, 全周页共享)。
 * raw: 拖拽中相对课程原位置的累计位移 (松手即落位, 不再作为回弹起点);
 * anchorDay: 锚定列, 初始 = 课程原 day, 拖到边缘翻周后重置为新页最边列,
 *            拖拽中药片视觉 = raw + (anchorDay - 课程原day) × 列宽;
 * targetDay/targetJc: 吸附目标槽位;
 * settleStart/targetOffset: 松手帧药片的最终视觉偏移 (落位取目标槽位偏移, 原地归位取零),
 *                           两者相等 → settle 协程 snapTo 后 animateTo 瞬时完成;
 * pendingMove: 待提交的移动, settle 结束后才调 onMoveBlock ——
 *              提交瞬间数据位置变化与视觉归零同帧重合, 彻底消除松手帧跳。
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
    var edgeHint by mutableIntStateOf(0)    // -1 悬停在左缘 / 0 无 / 1 悬停在右缘
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
    // 手指抓握点在列内的相对位置(0..1): 边缘翻周判定用绝对列号, 修正右缘永远够不到的偏差
    var grabFrac by mutableFloatStateOf(0f)
    // 刚翻过周的方向锁: 落位后手指往回拖出一段才允许再次触发边缘翻周, 防止瞬间连环翻
    var flipLock by mutableIntStateOf(0)
    // 翻页动画进行中: 页面从手指下滑过会产生假 positionChange, 期间禁止累计 drag.raw
    var flipInProgress by mutableStateOf(false)
    // 最近一次手势活动时间 (System.nanoTime), 供看门狗判断手势协程是否已被翻页连带杀死
    var lastActiveAt: Long = 0L
    // 本次拖拽的起始周: 拖拽语义 = 只移动该周的这节课 (数据层按 week 拆分),
    // 跨周松手仍只看不放 —— 翻周页面不属于起始周, 落课必是误操作
    var originWeek by mutableIntStateOf(0)
    // 大于 0 时自动翻回的目标周: 跨周导航松手后由 LaunchedEffect 消费(把镜头带回起始周)后清零
    var flipBackTo by mutableIntStateOf(0)
}

private fun minutes(t: String): Int {
    val (h, m) = t.split(":").map { it.trim().toInt() }
    return h * 60 + m
}

/** 大节模型: 槽位 k 覆盖 jc (2k+1, 2k+2) */
private class SlotModel(periods: List<Period>) {
    val slots = periods.size / 2
    val mainSlots = slots - 1
    val breaks = mutableSetOf<Int>()
    val times = mutableListOf<Pair<Int, Int>>()
    val jcRange = mutableListOf<Pair<Int, Int>>()

    init {
        for (k in 0 until slots) {
            val a = periods.first { it.jc == 2 * k + 1 }
            val b = periods.first { it.jc == 2 * k + 2 }
            times += minutes(a.start) to minutes(b.end)
            jcRange += a.jc to b.jc
            if (k > 0 && times[k].first - times[k - 1].second >= 60) breaks += k
        }
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

    // 状态栏容器色随状态流转; 回到本周药丸与状态栏同色等高, 视觉上像从状态栏分割出来
    val cs = MaterialTheme.colorScheme
    val (barBg, barFg) = when (todayStatus.tone) {
        TodayStatus.Tone.ACTIVE -> cs.primaryContainer to cs.onPrimaryContainer
        TodayStatus.Tone.NIGHT -> cs.tertiaryContainer to cs.onTertiaryContainer
        TodayStatus.Tone.JOY -> cs.secondaryContainer to cs.onSecondaryContainer
        TodayStatus.Tone.CALM -> cs.surfaceColorAtElevation(2.dp) to cs.onSurface
    }
    val animBarBg by animateColorAsState(barBg, tween(650), label = "todayBarBg")
    val animBarFg by animateColorAsState(barFg, tween(650), label = "todayBarFg")
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
            // 轻度压一层底色, 保证文字可读; 浅色模式的白纱是泛白元凶, 档位减到 0.10, 深色微降
            val bgScrim = CourseColors.isDark(MaterialTheme.colorScheme)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = if (bgScrim) 0.22f else 0.10f)),
            )
        }
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (compact) {
            // ---------------- 横屏紧凑头: 周次 + 状态一行 ----------------
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
        // ---------------- 顶栏: 周次 + 日期 ----------------
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
                .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "第${viewWeek}周",
                        fontSize = 24.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (viewWeek != initialWeek) {
                        Text(
                            "（非本周）",
                            fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 2.dp, bottom = 3.dp),
                        )
                    }
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

        // ---------------- 今日课程横条(动态状态栏) + 回到本周药丸 ----------------
        // 药丸与状态栏同色等高: 滑到非本周时, 像从状态栏右端分裂出一部分
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            TodayBar(
                status = todayStatus,
                barBg = animBarBg,
                barFg = animBarFg,
                haze = haze,
                modifier = Modifier.weight(1f).onSizeChanged { barH = with(density) { it.height.toDp() } },
            )
            AnimatedVisibility(
                visible = viewWeek != initialWeek,
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
                    bg = animBarBg,
                    fg = animBarFg,
                    modifier = Modifier.padding(start = 6.dp).height(barH),
                    onClick = { scope.launch { pagerState.animateScrollToPage(initialWeek - 1) } },
                )
            }
        }
        }

        // ---------------- 周网格(左右翻页) ----------------
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            pageSpacing = 8.dp,
            userScrollEnabled = drag.block == null,
            // 拖拽翻周后原页必须保持组合: 手势协程住在原页的课程块里,
            // 页面被销毁 = 手势死亡 = 拖拽失焦卡死 (看门狗只是兜底)
            beyondViewportPageCount = 1,
        ) { page ->
            WeekGrid(
                hasBg = bgFile != null,
                dataset = dataset,
                cls = cls,
                week = page + 1,
                initialWeek = initialWeek,
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
    // 上课中: 一层缓慢扫过的微光, Material You 流光感 (仅此状态开动画); 浅色底改用黑光带才可见
    val dark = CourseColors.isDark(MaterialTheme.colorScheme)
    val shimmer = if (status.tone == TodayStatus.Tone.ACTIVE) {
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
        colShimmer(x, 0.20f, dark)
    } else null

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
            shimmer?.let { Box(Modifier.matchParentSize().background(it)) }
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        status.title,
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = barFg,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    status.sub?.let {
                        Text(
                            it,
                            fontSize = 10.sp,
                            color = barFg.copy(alpha = 0.72f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
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
            .heightIn(min = 26.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
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
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "第${viewWeek}周",
            fontSize = 18.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (viewWeek != initialWeek) {
            Text(
                "（非本周）",
                fontSize = 11.sp, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
        Text(
            " ${Weeks.cnDay(todayDay)}",
            fontSize = 18.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            status.title,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = if (status.tone == TodayStatus.Tone.ACTIVE) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (showBackToCurrent) {
            BackToCurrentPill(
                bg = MaterialTheme.colorScheme.secondaryContainer,
                fg = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = onBackToCurrent,
            )
        }
        Box(
            Modifier.width(36.dp).height(36.dp).clip(CircleShape).clickable(onClick = onOpenMore),
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
    hasBg: Boolean,   // 自定义背景生效中: 课程块 0.97 微透呼应壁纸
    today: LocalDate,
    colorMap: Map<String, Int>,
    drag: DragHost,
    pagerState: PagerState,
    onCourseClick: (Block) -> Unit,
    onAddAt: (Int, Int) -> Unit,
    onMoveBlock: (Block, Int, Int, Int) -> Unit,   // (block, day, jc, week) —— week=拖拽起始周
) {
    val todayDay = today.dayOfWeek.value
    val isCurrentWeek = week == initialWeek
    // 今天列/课卡共用的流光相位: 仅当前周才启动动画, 省电且全列同步
    val shimmer: State<Float>? = if (isCurrentWeek) {
        val t = rememberInfiniteTransition(label = "kbColShimmer")
        t.animateFloat(
            0f, 1f,
            // 扫过 4.6s + 屏外休息 2.4s: 光带两端在屏幕外, 循环重置不可见
            infiniteRepeatable(
                keyframes {
                    durationMillis = 7000
                    0f at 0 using LinearEasing
                    1f at 4600 using LinearEasing
                    1f at 7000
                }
            ),
            label = "kbColShimmerX",
        )
    } else null
    val ctx = LocalContext.current
    val model = remember(dataset.periods) { SlotModel(dataset.periods) }
    val blocksByDay = remember(cls.courses, week) {
        // 色板按全学期课程排序分配, 不同课程必不同色 (小组件绑同一份 ClassCache 排序)
        CourseColors.bindOrder(cls.courses.map { CourseColors.seedOf(it.kc, it.fx) })
        Merger.blocksOf(cls.courses, week).groupBy { it.course.day }
    }

    // 当前时间, 每 30 秒刷新
    var now by remember { mutableStateOf(LocalTime.now().truncatedTo(ChronoUnit.MINUTES)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = LocalTime.now().truncatedTo(ChronoUnit.MINUTES)
        }
    }
    val nowMin = now.hour * 60 + now.minute

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
        val mainH = ((gridH - BREAK_H * model.breaks.size - EVE_H) / model.mainSlots)
            .coerceAtLeast(64.dp)
        val yTop = remember(model, mainH) {
            val ys = mutableListOf<Dp>()
            var y = 0.dp
            for (k in 0 until model.slots) {
                if (k in model.breaks) y += BREAK_H
                ys += y
                y += if (k < model.mainSlots) mainH else EVE_H
            }
            ys
        }
        val totalH = yTop.last() + EVE_H
        val slotH: (Int) -> Dp = { k -> if (k < model.mainSlots) mainH else EVE_H }
        val yOfJc: (Int) -> Dp = { jc ->
            val k = (jc - 1) / 2
            yTop[k] + ((jc - 1) % 2) * (slotH(k) / 2)
        }
        val yBottomJc: (Int) -> Dp = { jc ->
            val k = (jc - 1) / 2
            yTop[k] + (((jc - 1) % 2) + 1) * (slotH(k) / 2)
        }

        // 当前进行中的大节 (左侧时间轴加粗高亮用); 课间/午休/放学后 = -1 不高亮
        val activeSlot = if (isCurrentWeek)
            model.times.indexOfFirst { (s, e) -> nowMin in s until e } else -1
        // 当前时间线: 有课时段按真实分钟比例走; 课间/午休钳到上一节槽底, 早八前贴顶;
        // 晚上最后一节结束后隐藏, 翻到非本周也隐藏 —— 不再出现「时间线时有时无」
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
        // 松手即落位: settleStart 已由 dragEnd 同步定为最终视觉位置(落位偏移或零),
        // 协程 snapTo 后 animateTo(同值) 瞬时完成, 药片不再从松手点滑向目标格。
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
                drag.edgeHint = 0
                drag.flipLock = 0
                drag.originWeek = 0
                drag.pendingMove = null
            } else {
                // 钉住视觉在新槽位, 先提交移动; 新数据(cls.courses)落地那一帧,
                // 旧 Block 实例被新课表替换, 视觉无缝交接 —— 不再闪回原位
                drag.pinning = true
                drag.pinnedOffset = drag.targetOffset
                onMoveBlock(pm.block, pm.day, pm.jc, pm.week)
            }
        }
        // 钉住解除: 新数据落地(任一课表数据变化)即清拖拽会话, 解锁翻页
        LaunchedEffect(cls.courses) {
            if (drag.pinning) {
                drag.pinning = false
                drag.block = null
                drag.raw = Offset.Zero
                drag.edgeHint = 0
                drag.flipLock = 0
                drag.originWeek = 0
                drag.pendingMove = null
            }
        }
        // 拖到左/右边缘悬停半秒自动翻周; 翻页后锚定列重置到边缘列, 药片跟随手指出现在新页
        LaunchedEffect(drag.edgeHint, drag.block) {
            val dir = drag.edgeHint
            if (dir != 0 && drag.block != null && week == drag.dragWeek) {
                delay(500)   // 悬停半秒才翻周, 防误触
                val target = pagerState.currentPage + dir
                if (target in 0 until pagerState.pageCount) {
                    // 动画期间冻结 dragMove: 页面从手指下滑过会产生假 positionChange,
                    // 不冻结既会污染 raw 又会搅动状态导致翻页动画中途夭折
                    drag.flipInProgress = true
                    pagerState.animateScrollToPage(target)
                    drag.flipInProgress = false
                    drag.dragWeek = target + 1
                    drag.anchorDay = if (dir > 0) 7 else 1   // 手指停在边缘, 新页锚定到最边列
                    drag.raw = Offset(0f, drag.raw.y)
                    drag.flipLock = dir
                }
                drag.edgeHint = 0
            }
        }
        // 跨周导航松手后自动翻回起始周: 药片已原地归位, 翻页只是把镜头带回去
        LaunchedEffect(drag.flipBackTo) {
            if (drag.flipBackTo > 0 && week == drag.dragWeek) {
                pagerState.animateScrollToPage(drag.flipBackTo - 1)
                drag.flipBackTo = 0
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
                    drag.targetOffset = Offset.Zero
                    drag.pendingMove = null
                    drag.settling = true   // 走回弹动画归位, 数据不动
                    break
                }
            }
        }
        // 药片拖拽/回弹期间叠加的位移 (布局期读取, 不触发重组):
        // 未翻周时锚定列 = 课程原 day, 视觉 = raw; 翻周后锚定到新页边缘列,
        // 视觉 = raw + (anchorDay - 原day) × 列宽, 药片正好落在手指所在的边缘列
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
        val dragStart: (Block, Float) -> Unit = { b, grabX ->
            // 已有拖拽进行中(如第二根手指误触)不接受新会话
            if (drag.block == null) {
                drag.anchorDay = b.course.day
                drag.grabFrac = (grabX / colW).coerceIn(0f, 1f)
                drag.raw = Offset.Zero
                drag.settling = false
                drag.pinning = false
                drag.flipLock = 0
                drag.pendingMove = null
                drag.targetDay = b.course.day
                drag.targetJc = b.startJc
                drag.dragWeek = week
                drag.originWeek = week   // 跨周只看不放: 记住起始周, 松手据此回切
                drag.block = b
                drag.lastActiveAt = System.nanoTime()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
        val dragMove: (Block, Offset) -> Unit = dragMove@{ b, amt ->
            // 会话互斥: 非本会话块的手指事件 (第二根手指长按到另一门课) 一律忽略
            if (drag.block == null || b != drag.block) return@dragMove
            if (drag.flipInProgress) {
                // 翻页动画把页面从手指下滑过, positionChange 是假位移, 严禁累计
                drag.lastActiveAt = System.nanoTime()
            } else {
                // amt 在 1.05 缩放空间里, 换算回屏幕位移再累计 (见 PILL_SCALE 注释)
                drag.raw += amt * PILL_SCALE
                // 目标节数用生效节数(编辑会话可能只占连堂一部分, 见 effSpan), 吸附范围随之放宽
                val span = drag.block?.effSpan() ?: b.span
                // 列吸附: 锚定列 + 累计位移换算成 0-based 连续列, 四舍五入得目标天
                val col = (drag.anchorDay - 1) + drag.raw.x / colW
                drag.targetDay = (col.roundToInt() + 1).coerceIn(1, 7)
                with(density) {
                    val fingerY = yOfJc(b.startJc) + drag.raw.y.toDp()
                    var k = 0
                    for (i in 0 until model.slots) if (yTop[i] <= fingerY) k = i
                    val half = ((fingerY - yTop[k]) / (slotH(k) / 2)).toInt().coerceIn(0, 1)
                    drag.targetJc = (2 * k + 1 + half).coerceIn(1, dataset.periods.size - span + 1)
                }
                // 边缘翻周判定用「手指的绝对列号」(锚点列 + 抓握点偏移 + 位移):
                // 旧写法只看累计位移, 从周一往右拖到屏幕边缘也够不到右缘阈值, 导致只能翻上一周
                val fingerCol = (drag.anchorDay - 1) + drag.grabFrac + drag.raw.x / colW
                drag.edgeHint = when {
                    drag.flipLock != 0 -> 0
                    fingerCol > 6.35f -> 1     // 右缘 ~90px 内即触发 (真机边缘拒识区比模拟器宽, 阈值太贴边永远够不到)
                    fingerCol < -0.42f -> -1   // 手指压到屏幕左缘(左侧时间轴宽 0.63 列)
                    else -> 0
                }
                // 翻周落位后, 往回拖出一段才解锁, 防止锚定在边缘列瞬间连环翻周
                if (drag.flipLock == 1 && drag.raw.x < -0.45f * colW) drag.flipLock = 0
                if (drag.flipLock == -1 && drag.raw.x > 0.45f * colW) drag.flipLock = 0
                drag.lastActiveAt = System.nanoTime()
            }
        }
        val dragEnd: (Block) -> Unit = dragEnd@{ b ->
            // 会话互斥: 非本会话块的抬手一律忽略
            if (drag.block == null || b != drag.block) return@dragEnd
            // 跨周只看不放: 翻到别的周的拖拽只用于查看(含翻周后锁未解的纯导航),
            // 松手一律不提交移动 —— 落课语义只认起始周, 翻周页面落课必是误操作。
            // 药片在起始周原地归位, 并由 flipBackTo effect 自动翻页把镜头带回起始周。
            val navigating = drag.flipLock != 0 || drag.dragWeek != drag.originWeek
            var moved = !navigating &&
                (drag.targetDay != b.course.day || drag.targetJc != b.startJc)
            // 落点占用检测: 单周拖拽下, 当前周视图里的块都在同一周, 节次区间重叠即绝对冲突
            // (两块卡片绝对定位完全重叠, 下层不可见不可点)
            if (moved) {
                val occupied = blocksByDay[drag.targetDay].orEmpty().any { o ->
                    o != b &&
                        o.startJc < drag.targetJc + b.span && drag.targetJc < o.startJc + o.span
                }
                if (occupied) {
                    android.widget.Toast.makeText(
                        ctx, "该时段已有课程，换个位置试试", android.widget.Toast.LENGTH_SHORT,
                    ).show()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    moved = false
                }
            }
            // 松手帧的最终视觉偏移 (= settleStart = targetOffset): 落位取目标槽位偏移,
            // 否则取零 —— 协程 snapTo 后 animateTo 同值瞬时完成, 药片直接出现在最终位置不滑行
            val target = with(density) {
                Offset(
                    (drag.targetDay - b.course.day) * colW,
                    (yOfJc(drag.targetJc) - yOfJc(b.startJc)).toPx(),
                )
            }
            drag.settleStart = if (moved) target else Offset.Zero
            drag.targetOffset = drag.settleStart
            if (navigating && drag.dragWeek != drag.originWeek) {
                // 拖拽周切回起始周: 本页幽灵消失, 起始周块本体原地归位(视觉零偏移)
                drag.dragWeek = drag.originWeek
                drag.flipBackTo = drag.originWeek
            }
            drag.pendingMove = if (moved) PendingMove(b, drag.targetDay, drag.targetJc, drag.originWeek) else null
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
                // 空周表态: 假期/调休周整周无课时给明确文案, 不再是一屏空的格子框
                if (!weekHasCourse) {
                    Column(
                        Modifier.align(Alignment.TopCenter).offset(y = yTop[0] + (yTop.last() - yTop[0]) / 3),
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
                            fontSize = 8.sp,
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
                                Column(
                                    Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(tsStr, fontSize = 8.sp, color = MaterialTheme.colorScheme.outline)
                                    Text(
                                        "$j1-$j2",
                                        fontSize = 9.sp,
                                        fontWeight = if (k == activeSlot) FontWeight.Bold else FontWeight.Medium,
                                        color = if (k == activeSlot) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(teStr, fontSize = 8.sp, color = MaterialTheme.colorScheme.outline)
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
                                shimmer = shimmer,
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
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
                // 当前时间线(避开左侧时间列): 跟随主题色, 左实右渐隐 + 圆点光环
                nowY?.let { y ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .offset(y = y)
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
                                    RoundedCornerShape(2.dp),  // 细条端头圆润, 与全局圆角语言一致
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
                // 拖到左右边缘时的翻周提示: 对应侧一条向内渐隐的竖向光带
                if (drag.block != null && drag.edgeHint != 0) {
                    val hintColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    if (drag.edgeHint < 0) {
                        Box(
                            Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxHeight()
                                .width(10.dp)
                                .background(Brush.horizontalGradient(listOf(hintColor, Color.Transparent))),
                        )
                    } else {
                        Box(
                            Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(10.dp)
                                .background(Brush.horizontalGradient(listOf(Color.Transparent, hintColor))),
                        )
                    }
                }
                // 跨周药片(幽灵): 翻周后被拖的块还画在另一周的页面上, 本页(=拖拽当前周)看不到它。
                // 在本页网格坐标按同一套视觉公式渲染幽灵药片 (跟随 raw/回弹/钉住), 与原页药片互斥:
                // 拖拽周页面有块本体就不画幽灵, 翻回原周时自动切回块本体。
                // landed 门控: 提交钉住期间新块可能已在本页异步落地, 此帧幽灵与新落位块同格,
                // 再画就是一帧双影 —— 检测到落位新块即停画幽灵。
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
                    val container = CourseColors.container(gcs, seed, colorMap[seed])
                    val onContainer = CourseColors.onContainer(gcs, seed, colorMap[seed])
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
                        Column(Modifier.padding(horizontal = 3.dp, vertical = 4.dp)) {
                            Text(
                                gb.course.kc,
                                fontSize = 9.5.sp, lineHeight = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = onContainer,
                                maxLines = 6, overflow = TextOverflow.Ellipsis,
                            )
                            if (gb.course.room.isNotEmpty()) {
                                Text(
                                    gb.course.room,
                                    fontSize = 8.sp, lineHeight = 10.sp,
                                    color = onContainer.copy(alpha = 0.85f),
                                    maxLines = 2, overflow = TextOverflow.Ellipsis,
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
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
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
    shimmer: State<Float>?,
    drag: DragHost,
    visualOf: (Block) -> Offset,
    liftOf: (Block) -> Float,
    onDragStart: (Block, Float) -> Unit,   // Float = 抓握点在块内的 x (px), 供边缘翻周绝对列号计算
    onDragMove: (Block, Offset) -> Unit,
    onDragEnd: (Block) -> Unit,
    onDragCancel: () -> Unit,
    onCourseClick: (Block) -> Unit,
    onAddAt: (Int, Int) -> Unit,
    blockAlpha: Float = 1f,   // 有自定义背景时 0.97 微透, 色块透出一点壁纸呼吸感
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val dark = CourseColors.isDark(cs)
    Box(modifier.padding(horizontal = 1.dp)) {
        // 今天列: 纵向渐变底 + 缓慢流光 (替代旧的扁平矩形高亮)
        if (isToday) {
            val todayShape = RoundedCornerShape(14.dp)
            Box(
                Modifier
                    .matchParentSize()
                    .clip(todayShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(cs.primary.copy(alpha = 0.16f), cs.primary.copy(alpha = 0.05f)),
                        ),
                    ),
            )
            shimmer?.let { st ->
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(todayShape)
                        .background(colShimmer(st.value, 0.08f, dark)),
                )
            }
        }
        // 大节空槽框线 (点击空白格 = 在此添加课程)
        for (k in 0 until model.slots) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .offset(y = yTop[k] + 2.dp)
                    .padding(horizontal = 1.dp)
                    .height(slotH(k) - 6.dp)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        RoundedCornerShape(12.dp),
                    )
                    .clip(RoundedCornerShape(12.dp))  // 空格子高亮与虚线框同角
                    .clickable { onAddAt(day, model.jcRange[k].first) },
            )
        }
        // 拖拽落点吸附预览: 目标位置实时高亮 —— 从拖拽开始一直亮到数据落地随块一起消失,
        // 期间 (落位/钉住) 绝不能隐藏, 否则放置瞬间高亮"灭→亮→灭"两连闪 = 用户看到的闪烁抽搐。
        // 只在起始周页渲染 (showTarget), 高亮节数用生效节数(编辑会话可能只占连堂一部分)
        val dSpan = drag.block?.effSpan()
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
        // 课程块(按真实小节定位): 渐变 + 柔影, 顶部微微提亮出光泽
        blocks.forEach { b ->
            val seed = CourseColors.seedOf(b.course.kc, b.course.fx)
            val container = CourseColors.container(cs, seed, colorMap[seed])
            val onContainer = CourseColors.onContainer(cs, seed, colorMap[seed])
            val top = yOfJc(b.startJc)
            val h = yBottomJc(b.startJc + b.span - 1) - top - 4.dp
            val shape = RoundedCornerShape(12.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val p = liftOf(b)
                        if (p > 0.01f) {
                            val s = 1f + (PILL_SCALE - 1f) * p
                            scaleX = s
                            scaleY = s
                            shadowElevation = 6f * p
                        }
                    }
                    .offset {
                        val v = visualOf(b)
                        IntOffset(
                            v.x.roundToInt(),
                            (top + 2.dp).roundToPx() + v.y.roundToInt(),
                        )
                    }
                    .height(h)
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
                    .clip(shape).clickable(onClick = { onCourseClick(b) })  // 高亮裁到课程块形状
                    .pointerInput(b) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { pos -> onDragStart(b, pos.x) },
                            onDrag = { change, amt ->
                                change.consume()
                                onDragMove(b, amt)
                            },
                            onDragEnd = { onDragEnd(b) },
                            onDragCancel = { onDragCancel() },
                        )
                    },
            ) {
                if (isToday) shimmer?.let { st ->
                    Box(
                        Modifier
                            .matchParentSize()
                            .clip(shape)
                            .background(colShimmer(st.value, 0.14f, dark)),
                    )
                }
                Column(Modifier.padding(horizontal = 3.dp, vertical = 4.dp)) {
                    Text(
                        b.course.kc,
                        fontSize = 9.5.sp, lineHeight = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = onContainer,
                        maxLines = 6, overflow = TextOverflow.Ellipsis,
                    )
                    if (b.course.room.isNotEmpty()) {
                        Text(
                            b.course.room,
                            fontSize = 8.sp, lineHeight = 10.sp,
                            color = onContainer.copy(alpha = 0.85f),
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start,
                        )
                    }
                }
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
 *  深色底用白光带, 浅色底白光不可见 → 改用黑光带(强度×0.6) */
private fun colShimmer(x: Float, alpha: Float, dark: Boolean): Brush = Brush.linearGradient(
    listOf(
        Color.Transparent,
        (if (dark) Color.White else Color.Black).copy(alpha = if (dark) alpha else alpha * 0.6f),
        Color.Transparent,
    ),
    start = Offset(x * 2400f - 500f, -120f),
    end = Offset(x * 2400f - 100f, 520f),
)
