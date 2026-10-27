package com.ccsut.kb.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ccsut.kb.data.Cls
import com.ccsut.kb.data.Dataset
import com.ccsut.kb.util.Block
import com.ccsut.kb.util.KbClock
import com.ccsut.kb.util.Merger
import com.ccsut.kb.util.TimeParse
import com.ccsut.kb.util.Weeks
import kotlinx.coroutines.delay
import java.time.LocalTime

/**
 * 课前全屏聚焦页 (视觉方向 C「课程色大卡」):
 * 该课的周网格专属色放大成 hero 大卡 + 环形倒计时, 下方「今日剩余」单日时间轴 (日视图落点)。
 * 单击页面任意处 / 底部按钮 / 系统返回键 → 屏内错峰退场后回调 [onClose] 回周课表。
 */
@Composable
fun PreClassScreen(
    dataset: Dataset,
    cls: Cls,
    colorMap: Map<String, Int>,
    windowMin: Int,
    preview: Boolean = false,   // 开发者预览: 无视触发窗口取目标课, 关闭不记去重键
    onClose: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    // 屏内 30 秒 ticker: 倒计时随停留推进, 跨零点自动跟到新的一天 (同 ScheduleScreen 口径)
    var today by remember { mutableStateOf(KbClock.today()) }
    var nowMin by remember { mutableStateOf(LocalTime.now().let { it.hour * 60 + it.minute }) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
            today = KbClock.today()
        }
    }
    val week = remember(dataset.startDate, dataset.weeks, today) { Weeks.currentWeek(dataset, today) }
    val dayBlocks = remember(cls.courses, week, today) {
        Merger.blocksOf(cls.courses, week).filter { it.course.day == today.dayOfWeek.value }
    }
    val state = remember(dayBlocks, dataset.periods, nowMin, windowMin, today, preview) {
        if (preview) {
            PreClass.preview(cls.courses, dataset.periods, nowMin, today, dataset.startDate, dataset.weeks)
        } else {
            PreClass.compute(dayBlocks, dataset.periods, nowMin, windowMin, today)
        }
    }

    // 关闭编排: 先屏内错峰退场, 走完再回调真正关闭 (外层再补一次快速淡出)
    var closing by remember { mutableStateOf(false) }
    val exitP = remember { Animatable(0f) }
    LaunchedEffect(closing) {
        if (closing) {
            exitP.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
            onClose()
        }
    }
    // 停留期间状态消失 (下课了/课表被改): 自动退出, 不留空壳
    LaunchedEffect(state) { if (state == null && !closing) closing = true }
    BackHandler { closing = true }

    // 入场进度 0→1: 各元素按阈值错峰浮现
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) }

    val st = state ?: return
    val seed = CourseColors.seedOf(st.block.course.kc, st.block.course.fx)
    val container = CourseColors.container(cs, seed, colorMap[seed])
    val onC = CourseColors.onForContainer(container)
    val dark = CourseColors.isDark(cs)

    val isBefore = st.kind == PreClass.Kind.BEFORE
    val room = st.block.rooms.ifBlank { st.block.course.room }.trim()
    val teacher = st.block.teachers.ifBlank { st.block.course.teacher }.trim()
    val jcText = if (st.block.span > 1) "第 ${st.block.startJc}–${st.block.startJc + st.block.span - 1} 节" else "第 ${st.block.startJc} 节"
    val timeText = "${fmtMin(st.startMin)} – ${fmtMin(st.endMin)}"
    // 倒计时展示: 分钟级为主; 预览跨天时(未来 7 天取最近一节)换算成天, 防大数字溢出
    val minsText = when {
        st.mins <= 0 -> "马上"
        st.mins < 1440 -> "${st.mins}"
        else -> "${(st.mins + 1439) / 1440}"
    }
    val minsUnit = when {
        st.mins <= 0 -> ""
        st.mins < 1440 -> "分钟"
        else -> "天"
    }
    val minsCaption = when {
        st.mins <= 0 -> if (isBefore) "就要上课啦" else "就要下课啦"
        st.mins < 1440 -> if (isBefore) "分钟后上课" else "分钟后下课"
        else -> "后上课"
    }
    val ringFrac = when (st.kind) {
        PreClass.Kind.BEFORE -> (st.mins.toFloat() / windowMin.coerceAtLeast(1)).coerceIn(0f, 1f)
        PreClass.Kind.CURRENT ->
            (st.mins.toFloat() / (st.endMin - st.startMin).coerceAtLeast(1)).coerceIn(0f, 1f)
    }
    val frac by animateFloatAsState(ringFrac, tween(700, easing = FastOutSlowInEasing), label = "pcRing")

    // 今日剩余节数 (含进行中): 时间轴标题用
    val pm = remember(dataset.periods) { dataset.periods.associateBy { it.jc } }
    val remaining = remember(dayBlocks, pm, nowMin) {
        dayBlocks.count { b ->
            pm[b.startJc + b.span - 1]?.let { (TimeParse.minutesOf(it.end) ?: 0) > nowMin } == true
        }
    }

    // 无障碍: hero 合并为一句完整描述
    val heroDesc = buildString {
        append(if (isBefore) "下节课 " else "正在上 ")
        append(st.block.course.kc)
        append(" ${fmtMin(st.startMin)}到${fmtMin(st.endMin)}")
        if (room.isNotBlank()) append(" $room")
        append(if (isBefore) " 还有" else " 还剩")
        if (st.mins < 1440) append(" ${st.mins} 分钟") else append(" ${(st.mins + 1439) / 1440} 天")
    }

    // 错峰工具: p 在 [a,b] 区间的进度
    fun ph(p: Float, a: Float, b: Float): Float = ((p - a) / (b - a)).coerceIn(0f, 1f)
    val ev = enter.value
    val xv = exitP.value

    Box(
        Modifier
            .fillMaxSize()
            .background(cs.background)
            // 全页单击关闭: 按钮等可点元素自己消费点击, 不会穿透到这里
            .pointerInput(Unit) { detectTapGestures { closing = true } }
            .graphicsLayer {
                val s = 0.94f + 0.06f * ev
                scaleX = s
                scaleY = s
                alpha = (ev * 3f).coerceIn(0f, 1f)
            },
    ) {
        // 课程色氛围铺底: 顶部一道该课颜色的淡淡渐变, 整屏带上「这门课的颜色」
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to container.copy(alpha = if (dark) 0.20f else 0.14f),
                        0.55f to Color.Transparent,
                    ),
                ),
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = 560.dp)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ---- 顶行: 状态标签 + 关闭 ----
                Row(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val a = ph(ev, 0f, 0.30f) * (1f - ph(xv, 0.30f, 0.90f))
                            alpha = a
                            translationY = (1f - a) * 16f
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = container,
                        contentColor = onC,
                    ) {
                        Text(
                            if (isBefore) "下节课" else "正在上",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Surface(
                        onClick = { closing = true },
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Transparent,
                        contentColor = cs.onSurfaceVariant,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Close, contentDescription = "返回课表", modifier = Modifier.size(24.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                // ---- Hero: 课程色大卡 ----
                val heroA = ph(ev, 0.08f, 0.45f) * (1f - ph(xv, 0.10f, 0.60f))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = heroA
                            translationY = (1f - heroA) * 20f - ph(xv, 0.10f, 0.60f) * 14f
                        }
                        .shadow(10.dp, HeroShape)
                        .clip(HeroShape)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    lerp(container, if (dark) Color.Black else Color.White, if (dark) 0.08f else 0.14f),
                                    container,
                                ),
                            ),
                        )
                        .padding(24.dp),
                ) {
                    Column(
                        Modifier.semantics(mergeDescendants = true) { contentDescription = heroDesc },
                    ) {
                        Text(
                            "$jcText · $timeText",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = onC.copy(alpha = 0.85f),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            st.block.course.kc,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 40.sp,
                            color = onC,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(18.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 环形倒计时: 剩余量随时间流逝顺时针缩水
                            val ringA = ph(ev, 0.15f, 0.55f) * (1f - ph(xv, 0f, 0.40f))
                            Box(
                                Modifier
                                    .size(92.dp)
                                    .graphicsLayer {
                                        alpha = ringA
                                        translationY = -ph(xv, 0f, 0.40f) * 20f
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Canvas(Modifier.size(92.dp)) {
                                    val stroke = 9.dp.toPx()
                                    val inset = stroke / 2
                                    val arcSize = Size(size.width - stroke, size.height - stroke)
                                    drawArc(
                                        color = onC.copy(alpha = 0.16f),
                                        startAngle = -90f, sweepAngle = 360f, useCenter = false,
                                        topLeft = Offset(inset, inset), size = arcSize,
                                        style = Stroke(stroke, cap = StrokeCap.Round),
                                    )
                                    drawArc(
                                        color = onC,
                                        startAngle = -90f, sweepAngle = 360f * frac, useCenter = false,
                                        topLeft = Offset(inset, inset), size = arcSize,
                                        style = Stroke(stroke, cap = StrokeCap.Round),
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    AnimatedContent(
                                        targetState = minsText,
                                        transitionSpec = {
                                            (slideInVertically(tween(200)) { it / 2 } + fadeIn(tween(200))) togetherWith
                                                (slideOutVertically(tween(200)) { -it / 2 } + fadeOut(tween(200)))
                                        },
                                        label = "pcMins",
                                    ) { t ->
                                        Text(t, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = onC)
                                    }
                                    if (minsUnit.isNotEmpty()) {
                                        Text(minsUnit, fontSize = 11.sp, color = onC.copy(alpha = 0.80f))
                                    }
                                }
                            }
                            Spacer(Modifier.width(20.dp))
                            Column {
                                Text(
                                    minsCaption,
                                    fontSize = 14.sp,
                                    color = onC.copy(alpha = 0.85f),
                                )
                                if (room.isNotBlank()) {
                                    Spacer(Modifier.height(10.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Rounded.Place,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = onC.copy(alpha = 0.80f),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            room,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = onC,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                if (teacher.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Rounded.Person,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = onC.copy(alpha = 0.80f),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            teacher,
                                            fontSize = 14.sp,
                                            color = onC.copy(alpha = 0.85f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))

                // ---- 今日剩余: 单日时间轴 (日视图落点) ----
                val agendaA = ph(ev, 0.25f, 0.70f) * (1f - ph(xv, 0.25f, 0.80f))
                Box(Modifier.fillMaxWidth().graphicsLayer {
                    alpha = agendaA
                    translationY = (1f - agendaA) * 16f + ph(xv, 0.25f, 0.80f) * 12f
                }) {
                    Text(
                        "今日 · 还剩 $remaining 节",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = cs.onSurfaceVariant,
                    )
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .graphicsLayer { alpha = agendaA },
                ) {
                    dayBlocks.forEach { b ->
                        AgendaRow(
                            block = b,
                            periods = pm,
                            nowMin = nowMin,
                            colorMap = colorMap,
                            highlighted = b == st.block,
                            isBefore = isBefore,
                            cs = cs,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // ---- 进入课表 ----
                val btnA = ph(ev, 0.35f, 0.85f) * (1f - ph(xv, 0.30f, 0.90f))
                Surface(
                    onClick = { closing = true },
                    shape = RoundedCornerShape(26.dp),
                    color = cs.primary,
                    contentColor = cs.onPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .graphicsLayer { alpha = btnA },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("进入课表", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

/** 时间轴行: 左侧起止时刻, 右侧该课专属色药丸; hero 对应块描边高亮, 已结束的整行降透明 */
@Composable
private fun AgendaRow(
    block: Block,
    periods: Map<Int, com.ccsut.kb.data.Period>,
    nowMin: Int,
    colorMap: Map<String, Int>,
    highlighted: Boolean,
    isBefore: Boolean,
    cs: androidx.compose.material3.ColorScheme,
) {
    val p0 = periods[block.startJc]
    val p1 = periods[block.startJc + block.span - 1]
    val startM = p0?.let { TimeParse.minutesOf(it.start) } ?: return
    val endM = p1?.let { TimeParse.minutesOf(it.end) } ?: return
    val ongoing = startM <= nowMin && nowMin < endM
    val past = endM <= nowMin
    val seed = CourseColors.seedOf(block.course.kc, block.course.fx)
    val container = CourseColors.container(cs, seed, colorMap[seed])
    val onC = CourseColors.onForContainer(container)
    val room = block.rooms.ifBlank { block.course.room }.trim()

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .graphicsLayer { alpha = if (past) 0.45f else 1f },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(78.dp)) {
            Text(fmtMin(startM), fontSize = 12.sp, color = cs.onSurfaceVariant)
            Text(
                fmtMin(endM),
                fontSize = 11.sp,
                color = cs.onSurfaceVariant.copy(alpha = 0.70f),
            )
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = container,
            contentColor = onC,
            border = if (highlighted) BorderStroke(1.5.dp, onC.copy(alpha = 0.40f)) else null,
            modifier = Modifier.weight(1f),
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        block.course.kc,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (room.isNotBlank()) {
                        Text(
                            room,
                            fontSize = 11.sp,
                            color = onC.copy(alpha = 0.80f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                val tag = when {
                    ongoing -> "进行中"
                    highlighted && isBefore -> "下一节"
                    else -> null
                }
                if (tag != null) {
                    Text(
                        tag,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = onC.copy(alpha = 0.90f),
                    )
                }
            }
        }
    }
}

/** 分钟数 → "H:MM" */
private fun fmtMin(m: Int): String = "${m / 60}:${(m % 60).toString().padStart(2, '0')}"

private val HeroShape = RoundedCornerShape(28.dp)
