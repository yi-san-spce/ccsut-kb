package com.ccsut.kb.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import com.ccsut.kb.data.BgStore
import com.ccsut.kb.data.Cls
import com.ccsut.kb.data.Dataset
import com.ccsut.kb.data.Period
import com.ccsut.kb.util.Block
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

private val BREAK_H = 22.dp      // 大课间(午休/晚饭)分隔带高度
private val EVE_H = 88.dp        // 晚上大节槽位高度
private val LEFT_W = 34.dp

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
    onOpenMore: () -> Unit,
    onCourseClick: (Block) -> Unit,
) {
    // 时间旅行: dbgOffset 变化时重取语义今天 (KbClock, 只平移日期)
    val today = remember(dbgOffset) { KbClock.today() }
    val todayDay = today.dayOfWeek.value
    val todayBlocks = remember(cls.courses, initialWeek, todayDay) {
        Merger.blocksOf(cls.courses, initialWeek).filter { it.course.day == todayDay }
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
    // 当前分钟(30 秒刷新): 状态栏横竖屏共用
    var nowMin by remember { mutableStateOf(LocalTime.now().let { it.hour * 60 + it.minute }) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
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
            // 轻度压一层底色, 保证文字可读
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.25f)),
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
                    if (haze != null) Modifier.hazeChild(
                        haze,
                        style = HazeStyle(
                            backgroundColor = MaterialTheme.colorScheme.surface,
                            tints = listOf(HazeTint(MaterialTheme.colorScheme.surface.copy(alpha = 0.68f))),
                            blurRadius = 24.dp,
                            noiseFactor = 0f,
                        ),
                    ) else Modifier,
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
                Text(
                    "${today.year}/${today.monthValue}/${today.dayOfMonth}",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Box(
                Modifier.width(44.dp).height(44.dp).clickable(onClick = onOpenMore),
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
        ) { page ->
            WeekGrid(
                dataset = dataset,
                cls = cls,
                week = page + 1,
                initialWeek = initialWeek,
                today = today,
                colorMap = colorMap,
                onCourseClick = onCourseClick,
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
    // 上课中: 一层缓慢扫过的微光, Material You 流光感 (仅此状态开动画)
    val shimmer = if (status.tone == TodayStatus.Tone.ACTIVE) {
        val t = rememberInfiniteTransition(label = "kbShimmer")
        val x by t.animateFloat(
            0f, 1f,
            infiniteRepeatable(tween(3600, easing = LinearEasing)),
            label = "kbShimmerX",
        )
        colShimmer(x, 0.20f)
    } else null

    Surface(
        color = if (haze != null) Color.Transparent else barBg,
        contentColor = barFg,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .then(
                if (haze != null) Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .hazeChild(
                        haze,
                        style = HazeStyle(
                            backgroundColor = MaterialTheme.colorScheme.surface,
                            tints = listOf(HazeTint(barBg.copy(alpha = 0.55f))),
                            blurRadius = 24.dp,
                            noiseFactor = 0f,
                        ),
                    ) else Modifier,
            ),
    ) {
        Box {
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
            Modifier.width(36.dp).height(36.dp).clickable(onClick = onOpenMore),
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
    today: LocalDate,
    colorMap: Map<String, Int>,
    onCourseClick: (Block) -> Unit,
) {
    val todayDay = today.dayOfWeek.value
    val isCurrentWeek = week == initialWeek
    // 今天列/课卡共用的流光相位: 仅当前周才启动动画, 省电且全列同步
    val shimmer: State<Float>? = if (isCurrentWeek) {
        val t = rememberInfiniteTransition(label = "kbColShimmer")
        t.animateFloat(
            0f, 1f,
            infiniteRepeatable(tween(5000, easing = LinearEasing)),
            label = "kbColShimmerX",
        )
    } else null
    val model = remember(dataset.periods) { SlotModel(dataset.periods) }
    val blocksByDay = remember(cls.courses, week) {
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

        val nowSlotIdx = if (isCurrentWeek)
            model.times.indexOfFirst { (s, e) -> nowMin in s until e } else -1
        val nowY: Dp? = if (nowSlotIdx >= 0) {
            val (s, e) = model.times[nowSlotIdx]
            val frac = ((nowMin - s).toFloat() / (e - s)).coerceIn(0f, 1f)
            yTop[nowSlotIdx] + slotH(nowSlotIdx) * frac
        } else null

        Box(Modifier.fillMaxSize().verticalScroll(vScroll)) {
            Box(Modifier.fillMaxWidth().height(totalH)) {
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
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
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
                                        fontWeight = if (k == nowSlotIdx) FontWeight.Bold else FontWeight.Medium,
                                        color = if (k == nowSlotIdx) MaterialTheme.colorScheme.primary
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
                                model = model,
                                yTop = yTop,
                                slotH = slotH,
                                yOfJc = yOfJc,
                                yBottomJc = yBottomJc,
                                blocks = blocksByDay[day].orEmpty(),
                                colorMap = colorMap,
                                shimmer = shimmer,
                                onCourseClick = onCourseClick,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
                // 当前时间线(避开左侧时间列)
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
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
                        )
                        Box(
                            Modifier
                                .align(Alignment.CenterStart)
                                .offset(x = (-1).dp, y = (-3).dp)
                                .width(8.dp)
                                .height(8.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                        )
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
    model: SlotModel,
    yTop: List<Dp>,
    slotH: (Int) -> Dp,
    yOfJc: (Int) -> Dp,
    yBottomJc: (Int) -> Dp,
    blocks: List<Block>,
    colorMap: Map<String, Int>,
    shimmer: State<Float>?,
    onCourseClick: (Block) -> Unit,
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
                        .background(colShimmer(st.value, 0.08f)),
                )
            }
        }
        // 大节空槽框线
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
                    .offset(y = top + 2.dp)
                    .height(h)
                    .shadow(if (dark) 0.dp else 2.dp, shape)
                    .background(
                        Brush.verticalGradient(
                            listOf(lerp(container, Color.White, if (dark) 0.08f else 0.12f), container),
                        ),
                        shape,
                    )
                    .clickable { onCourseClick(b) },
            ) {
                if (isToday) shimmer?.let { st ->
                    Box(
                        Modifier
                            .matchParentSize()
                            .clip(shape)
                            .background(colShimmer(st.value, 0.14f)),
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
            }
        }
    }
}

/** 流光笔刷: x∈[0,1] 为相位, alpha 为白光强度 */
private fun colShimmer(x: Float, alpha: Float): Brush = Brush.linearGradient(
    listOf(Color.Transparent, Color.White.copy(alpha = alpha), Color.Transparent),
    start = Offset(x * 1400f - 400f, -120f),
    end = Offset(x * 1400f, 520f),
)
