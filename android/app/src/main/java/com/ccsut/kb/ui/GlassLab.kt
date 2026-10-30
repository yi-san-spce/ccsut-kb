package com.ccsut.kb.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ccsut.kb.Prefs
import com.ccsut.kb.data.BgStore
import com.ccsut.kb.data.Cls
import com.ccsut.kb.data.Dataset
import com.ccsut.kb.data.Period
import com.ccsut.kb.util.Block
import com.ccsut.kb.util.Merger
import com.ccsut.kb.util.TimeParse
import com.ccsut.kb.util.Weeks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 玻璃材质实验室 (开发者模式专属, 不进发布路径)。
 * 真实课表网格 + 真实用户壁纸, 全参数滑杆实时生效; 翻周试玩区验收 L0/L1 动效,
 * L2 为单卡 AGSL 折射试玩 (API 33+)。参数即调即存 (Prefs), JSON 可复制回填 LiquidGlassSpec。
 * 调研结论 (docs/开发者/liquid-glass-research.md): 网格玻璃 = 静态预渲染底图 + 解析矩形开窗,
 * 纵向无滚动 → 静止零每帧成本, 动态只在翻周滑动 (~300ms)。
 */
object GlassLabPresets {
    /** 用户标定基准: 状态栏 87% 透明档的观感 (tint 6%, 轻 blur) */
    val REF_87 = GlassLabParams(blurRadiusPx = 8, frostAlpha = 0.06f, washAlpha = 0f, rimAlpha = 0.35f, specularAlpha = 0.22f)

    val V22 = GlassLabParams(blurRadiusPx = 24, frostAlpha = 0.16f, washAlpha = 0.16f, rimAlpha = 0.55f, specularAlpha = 0.20f)

    /** 实色对照 (≈ 玻璃关闭时的旧版课程卡) */
    val SOLID = GlassLabParams(blurRadiusPx = 24, frostAlpha = 0f, washAlpha = 0.62f, rimAlpha = 0.34f, specularAlpha = 0.12f)
}

data class GlassLabParams(
    val blurRadiusPx: Int = 14,
    val frostAlpha: Float = 0.10f,
    val washAlpha: Float = 0.10f,
    val rimAlpha: Float = 0.50f,
    val accentBar: Boolean = true,
    val specularAlpha: Float = 0.18f,
    val textShadow: Boolean = true,
    val motionL1: Boolean = false,
    val motionL2: Boolean = false,
) {
    fun toJson(): String = JSONObject().apply {
        put("blurRadiusPx", blurRadiusPx)
        put("frostAlpha", frostAlpha.toDouble())
        put("washAlpha", washAlpha.toDouble())
        put("rimAlpha", rimAlpha.toDouble())
        put("accentBar", accentBar)
        put("specularAlpha", specularAlpha.toDouble())
        put("textShadow", textShadow)
        put("motionL1", motionL1)
        put("motionL2", motionL2)
    }.toString()

    companion object {
        fun fromJson(s: String?): GlassLabParams? = runCatching {
            val o = JSONObject(s ?: return null)
            GlassLabParams(
                blurRadiusPx = o.getInt("blurRadiusPx"),
                frostAlpha = o.getDouble("frostAlpha").toFloat(),
                washAlpha = o.getDouble("washAlpha").toFloat(),
                rimAlpha = o.getDouble("rimAlpha").toFloat(),
                accentBar = o.getBoolean("accentBar"),
                specularAlpha = o.getDouble("specularAlpha").toFloat(),
                textShadow = o.getBoolean("textShadow"),
                motionL1 = o.getBoolean("motionL1"),
                motionL2 = o.getBoolean("motionL2"),
            )
        }.getOrNull()
    }
}

/** 槽位几何: 与 ScheduleScreen.SlotModel 同语义的实验室复制品 (periods → 槽位/大课间/分钟区间) */
private class LabSlotModel(periods: List<Period>) {
    val slots: Int
    val mainSlots: Int
    val breaks = mutableSetOf<Int>()
    val times = mutableListOf<Pair<Int, Int>>()
    val jcRange = mutableListOf<Pair<Int, Int>>()

    init {
        val byJc = periods.associateBy { it.jc }
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
        mainSlots = if (slots > 0) slots - 1 else 0   // 末槽按晚节处理 (实验室近似)
    }
}

private val LAB_LEFT_W = 42.dp
private val LAB_BREAK_H = 22.dp
private val LAB_EVE_H = 88.dp

@Composable
fun GlassLabScreen(
    dataset: Dataset,
    cls: Cls?,
    colorMap: Map<String, Int>,
    bgFile: java.io.File?,
    bgVersion: Int,
    bgAlpha: Float,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var params by remember { mutableStateOf(GlassLabParams.fromJson(Prefs.glassLabJson(ctx)) ?: GlassLabParams()) }
    LaunchedEffect(params) { Prefs.setGlassLabJson(ctx, params.toJson()) }

    // 背景图 (与主界面同源): bg.jpg + bgVersion 失效重解码
    val bgBitmap by produceState<Bitmap?>(null, bgFile, bgVersion) {
        value = bgFile?.let { f ->
            withContext(Dispatchers.IO) { runCatching { BgStore.decode(f.absolutePath) }.getOrNull() }
        }
    }
    val dark = CourseColors.isDark(MaterialTheme.colorScheme)

    // 周网格数据: 本周 (翻周由三页同内容 pager 模拟)
    val week = remember(dataset.startDate, dataset.weeks) {
        Weeks.currentWeek(dataset, com.ccsut.kb.util.KbClock.today())
    }
    val blocksByDay = remember(cls, week) {
        cls?.courses?.let { Merger.blocksOf(it, week).groupBy { b -> b.course.day } } ?: emptyMap()
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // ---- 顶栏 ----
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "关闭实验室",
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickableNoRipple(onDismiss)
                    .padding(6.dp),
            )
            Text(
                "玻璃材质实验室", fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            Text(
                "复制参数",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickableNoRipple { clipboard.setText(AnnotatedString(params.toJson())) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        // ---- 试玩画布: 三页同内容 pager (翻周 = L0/L1 动效验证) ----
        val pagerState = rememberPagerState { 3 }
        LabGlassCanvas(
            params = params,
            pagerState = pagerState,
            dark = dark,
            bgBitmap = bgBitmap,
            bgAlpha = bgAlpha,
            baseColor = MaterialTheme.colorScheme.background,
            blocksByDay = blocksByDay,
            periods = dataset.periods,
            colorMap = colorMap,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )

        // ---- 参数面板 ----
        Column(
            Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabChip("87% 基准", params == GlassLabPresets.REF_87) { params = GlassLabPresets.REF_87 }
                LabChip("v2.2 配方", params == GlassLabPresets.V22) { params = GlassLabPresets.V22 }
                LabChip("实色对照", params == GlassLabPresets.SOLID) { params = GlassLabPresets.SOLID }
            }
            LabSlider("底图模糊 (px, 4-40)", params.blurRadiusPx.toFloat(), 4f, 40f) {
                params = params.copy(blurRadiusPx = it.roundToInt())
            }
            LabSlider("Frost 中性玻璃体", params.frostAlpha, 0f, 0.5f) { params = params.copy(frostAlpha = it) }
            LabSlider("Wash 课程色洗", params.washAlpha, 0f, 0.6f) { params = params.copy(washAlpha = it) }
            LabSlider("Rim 底边辉光", params.rimAlpha, 0f, 1f) { params = params.copy(rimAlpha = it) }
            LabSlider("Specular 顶高光", params.specularAlpha, 0f, 0.6f) { params = params.copy(specularAlpha = it) }
            LabToggle("左缘色条", params.accentBar) { params = params.copy(accentBar = it) }
            LabToggle("白字暗影", params.textShadow) { params = params.copy(textShadow = it) }
            LabToggle("L1 掠光 (翻周)", params.motionL1) { params = params.copy(motionL1 = it) }
            LabToggle("L2 单卡折射 (本机 API ${Build.VERSION.SDK_INT}${if (Build.VERSION.SDK_INT >= 33) " ✓" else " ✗ 需33+"})", params.motionL2) {
                params = params.copy(motionL2 = it)
            }
            LabFps(Modifier.padding(top = 4.dp))
            Text(
                "调好后点右上「复制参数」发给助手回填。深浅色对比请切系统深色模式后重开实验室。",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
        }
    }
    if (cls == null) {
        Text(
            "尚未选择班级：网格为空，先回主界面选班级再来调玻璃",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun LabChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 12.sp,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickableNoRipple(onClick)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun LabSlider(label: String, value: Float, from: Float, to: Float, onChange: (Float) -> Unit) {
    Column(Modifier.padding(vertical = 2.dp)) {
        Row {
            Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("%.2f".format(value), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value.coerceIn(from, to), onValueChange = onChange, valueRange = from..to)
    }
}

@Composable
private fun LabToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** 轻量帧率表: withFrameNanos 环, 每 ~500ms 刷一次状态避免逐帧重组 */
@Composable
private fun LabFps(modifier: Modifier = Modifier) {
    var fps by remember { mutableFloatStateOf(0f) }
    var jankPct by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val deltas = LongArray(120)
        var idx = 0
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                if (last != 0L) { deltas[idx % 120] = t - last; idx++ }
                last = t
            }
            if (idx % 30 == 0 && idx > 10) {
                val n = min(idx, 120)
                val recent = deltas.copyOf(n)
                val avg = recent.sum() / n
                fps = (1e9 / avg).toFloat()
                jankPct = recent.count { it > 20_000_000L } * 100f / n
            }
        }
    }
    Text(
        "%.0f fps · 掉帧(>20ms) %.0f%%".format(fps, jankPct),
        fontSize = 12.sp,
        color = if (fps > 50f || fps == 0f) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        modifier = modifier,
    )
}

/** 试玩画布: 三页同内容 pager; 每页 = 底图 + 玻璃开窗层 + 卡片材质 */
@Composable
private fun LabGlassCanvas(
    params: GlassLabParams,
    pagerState: PagerState,
    dark: Boolean,
    bgBitmap: Bitmap?,
    bgAlpha: Float,
    baseColor: Color,
    blocksByDay: Map<Int, List<Block>>,
    periods: List<Period>,
    colorMap: Map<String, Int>,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    var winSizePx by remember { mutableStateOf(IntSize.Zero) }
    val scrimAlpha = if (dark) 0.22f else 0.10f
    // 底图: 复刻可见层 (底色 → 背景图 Crop ×bgAlpha → scrim) 后三趟 box blur; 滑杆变化即重算 (后台线程)
    val backdrop by produceState<ImageBitmap?>(null, bgBitmap, bgAlpha, baseColor, dark, winSizePx, params.blurRadiusPx) {
        value = if (bgBitmap == null || winSizePx.width == 0 || winSizePx.height == 0) null
        else withContext(Dispatchers.Default) {
            runCatching {
                buildLabBackdrop(
                    bgBitmap!!, bgAlpha, baseColor, cs.background, scrimAlpha,
                    winSizePx.width, winSizePx.height, params.blurRadiusPx,
                )
            }.getOrNull()
        }
    }
    Box(modifier.onSizeChanged { winSizePx = it }) {
        if (bgBitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bgBitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                alpha = bgAlpha.coerceIn(0.1f, 1f),
            )
            Box(Modifier.matchParentSize().background(cs.background.copy(alpha = scrimAlpha)))
        } else {
            // 无壁纸: 合成渐变底, 让 blur/折射有东西可弯
            Box(
                Modifier.matchParentSize().background(
                    Brush.linearGradient(
                        listOf(cs.primaryContainer, cs.tertiaryContainer, cs.secondaryContainer),
                        start = Offset.Zero,
                        end = Offset(1200f, 2400f),
                    ),
                ),
            )
        }
        HorizontalPager(state = pagerState, modifier = Modifier.matchParentSize()) { _ ->
            LabGlassPage(
                params = params,
                pagerState = pagerState,
                dark = dark,
                backdrop = backdrop,
                blocksByDay = blocksByDay,
                periods = periods,
                colorMap = colorMap,
            )
        }
    }
}

/** 单页: 空槽格线 + 玻璃开窗层 + 卡片材质 (几何与主界面 WeekGrid 同源公式) */
@Composable
private fun LabGlassPage(
    params: GlassLabParams,
    pagerState: PagerState,
    dark: Boolean,
    backdrop: ImageBitmap?,
    blocksByDay: Map<Int, List<Block>>,
    periods: List<Period>,
    colorMap: Map<String, Int>,
) {
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val model = remember(periods) { LabSlotModel(periods) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val gridH = maxHeight
        val mainH = ((gridH - LAB_BREAK_H * model.breaks.size - LAB_EVE_H) / model.mainSlots.coerceAtLeast(1)).coerceAtLeast(40.dp)
        val yTop = remember(model, mainH) {
            val ys = mutableListOf<Dp>()
            var y = 0.dp
            for (k in 0 until model.slots) {
                if (k in model.breaks) y += LAB_BREAK_H
                ys += y
                y += if (k < model.mainSlots) mainH else LAB_EVE_H
            }
            ys
        }
        val slotH: (Int) -> Dp = { k -> if (k < model.mainSlots) mainH else LAB_EVE_H }
        val yOfJc: (Int) -> Dp = { jc ->
            val k = (jc - 1) / 2
            (yTop.getOrElse(k) { 0.dp }) + (slotH(k) / 2) * ((jc - 1) % 2)
        }
        val yBottomJc: (Int) -> Dp = { jc ->
            val k = (jc - 1) / 2
            (yTop.getOrElse(k) { 0.dp }) + (slotH(k) / 2) * (((jc - 1) % 2) + 1)
        }
        // 卡片矩形 (页内坐标, px) —— 与主界面 WeekGrid 同源公式 (LEFT_W + 7 列 + 1dp 列内边距 + 2dp 上下 inset)
        val colW = with(density) { (constraints.maxWidth - LAB_LEFT_W.toPx()) / 7f }
        val glassRects = remember(blocksByDay, yTop, model, constraints.maxWidth) {
            with(density) {
                buildList {
                    for (day in 1..7) {
                        val x = LAB_LEFT_W.toPx() + (day - 1) * colW + 1.dp.toPx()
                        val w = colW - 2.dp.toPx()
                        for (b in blocksByDay[day].orEmpty()) {
                            val top = yOfJc(b.startJc).toPx()
                            val bottom = yBottomJc(b.startJc + b.span - 1).toPx()
                            if (bottom - top - 4.dp.toPx() > 0f) add(Rect(x, top + 2.dp.toPx(), x + w, bottom - 2.dp.toPx()))
                        }
                    }
                }
            }
        }
        // 空槽格线 (定位参照, 与主界面同款 12dp 圆角框线)
        for (k in 0 until model.slots) {
            Box(
                Modifier
                    .offset(x = LAB_LEFT_W, y = yTop[k] + 2.dp)
                    .padding(end = 0.dp)
                    .fillMaxWidth()
                    .height((slotH(k) - 6.dp).coerceAtLeast(12.dp))
                    .border(1.dp, CourseColors.gridStroke(cs), RoundedCornerShape(12.dp)),
            )
        }
        // 玻璃开窗层 (每页一个; src/dst 求交修正版, 翻周时 selfBounds 跟随)
        if (backdrop != null) {
            LabGlassOverlay(
                backdrop = backdrop,
                cardRectsPx = glassRects,
                windowSizePx = IntSize(constraints.maxWidth, constraints.maxHeight),
                cornerRadiusPx = with(density) { 12.dp.toPx() },
                params = params,
                pagerState = pagerState,
                modifier = Modifier.matchParentSize(),
            )
        }
        // 卡片材质 (七日列, 每列一个 Box 绝对定位卡片 —— 与 DayColumn 同构)
        (1..7).forEach { day ->
            val dayX = with(density) { (LAB_LEFT_W.toPx() + (day - 1) * colW).toDp() }
            val cardW = with(density) { (colW - 2.dp.toPx()).toDp() }
            Box(Modifier.offset(x = dayX).width(cardW).fillMaxHeight()) {
                blocksByDay[day].orEmpty().forEach { b ->
                    val seed = CourseColors.seedOf(b.course.kc, b.course.fx)
                    val (container, onContainer) = CourseColors.pair(cs, seed, colorMap[seed])
                    val top = yOfJc(b.startJc)
                    val h = yBottomJc(b.startJc + b.span - 1) - top - 4.dp
                    LabGlassCard(
                        name = b.course.kc,
                        room = b.course.room,
                        container = container,
                        onContainer = onContainer,
                        params = params,
                        dark = dark,
                        modifier = Modifier
                            .offset(y = top + 2.dp)
                            .fillMaxWidth()
                            .height(h.coerceAtLeast(16.dp)),
                    )
                }
            }
        }
    }
}

/** 课程卡材质 (实验室版: 与最终实现同一套层叠 frost→wash→rim→specular→色条→白字暗影) */
@Composable
private fun LabGlassCard(
    name: String,
    room: String,
    container: Color,
    onContainer: Color,
    params: GlassLabParams,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val frost = (if (dark) Color.Black else Color.White).copy(alpha = params.frostAlpha)
    val titleColor = if (params.textShadow) Color.White else onContainer
    val titleStyle = if (params.textShadow) {
        TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 1f), blurRadius = 5f))
    } else {
        TextStyle.Default
    }
    Box(
        modifier
            .shadow(3.dp, shape)
            .background(frost, shape)
            .background(container.copy(alpha = params.washAlpha), shape)
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Transparent, container.copy(alpha = params.rimAlpha)),
                ),
                shape,
            ),
    ) {
        if (params.specularAlpha > 0f) {
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = params.specularAlpha), Color.Transparent),
                    ),
                    shape,
                ),
            )
        }
        if (params.accentBar) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 3.dp, top = 6.dp, bottom = 6.dp)
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(container.copy(alpha = 0.95f), RoundedCornerShape(2.dp)),
            )
        }
        Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            Text(
                name, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium,
                color = titleColor, style = titleStyle,
                maxLines = 6, overflow = TextOverflow.Ellipsis,
            )
            if (room.isNotEmpty()) {
                Text(
                    room, fontSize = 10.sp, lineHeight = 13.sp,
                    color = if (params.textShadow) Color.White.copy(alpha = 0.82f) else onContainer.copy(alpha = 0.85f),
                    style = titleStyle,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 玻璃开窗层 (实验室修正版): src 矩形与底图边界【求交】后映射到 dst —— 修 v2.2 的 coerceIn 夹取错位。
 * 静止零每帧成本; 翻周时 selfBounds 跟随 (1 写/帧/页)。
 */
@Composable
private fun LabGlassOverlay(
    backdrop: ImageBitmap,
    cardRectsPx: List<Rect>,
    windowSizePx: IntSize,
    cornerRadiusPx: Float,
    params: GlassLabParams,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
) {
    var selfBounds by remember { mutableStateOf(Rect.Zero) }
    val path = remember { Path() }
    Box(
        modifier
            .onGloballyPositioned { selfBounds = it.boundsInWindow() }
            .drawWithCache {
                val sb = selfBounds
                val winW = windowSizePx.width
                val winH = windowSizePx.height
                if (sb.width > 0f && sb.height > 0f && winW > 0 && winH > 0 && cardRectsPx.isNotEmpty()) {
                    val sx = backdrop.width / winW.toFloat()
                    val sy = backdrop.height / winH.toFloat()
                    path.reset()
                    for (r in cardRectsPx) path.addRoundRect(RoundRect(r, CornerRadius(cornerRadiusPx)))
                    onDrawBehind {
                        // src(底图像素) 与 dst(本页局部 px) 一一对应: 对 src 求交, dst 同步裁剪
                        val srcL = sb.left * sx
                        val srcT = sb.top * sy
                        val srcR = sb.right * sx
                        val srcB = sb.bottom * sy
                        val il = max(0f, srcL)
                        val topCut = max(0f, srcT)
                        val ir = min(backdrop.width.toFloat(), srcR)
                        val ib = min(backdrop.height.toFloat(), srcB)
                        if (ir <= il || ib <= topCut) return@onDrawBehind
                        val dstL = il / sx - sb.left
                        val dstT = topCut / sy - sb.top
                        val dstR = ir / sx - sb.left
                        val dstB = ib / sy - sb.top
                        clipPath(path) {
                            drawImage(
                                image = backdrop,
                                srcOffset = IntOffset(il.roundToInt(), topCut.roundToInt()),
                                srcSize = IntSize((ir - il).roundToInt().coerceAtLeast(1), (ib - topCut).roundToInt().coerceAtLeast(1)),
                                dstOffset = IntOffset(dstL.roundToInt(), dstT.roundToInt()),
                                dstSize = IntSize((dstR - dstL).roundToInt().coerceAtLeast(1), (dstB - dstT).roundToInt().coerceAtLeast(1)),
                                filterQuality = FilterQuality.Low,
                            )
                            // L1 掠光: 高光带位置 = pager 相位的纯函数, 裁在窗内画, 零新增节点
                            if (params.motionL1) {
                                val frac = pagerState.currentPage + pagerState.currentPageOffsetFraction
                                val band = size.width * ((frac / 3f + 0.15f) % 1f)
                                drawRect(
                                    brush = Brush.linearGradient(
                                        listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent),
                                        start = Offset(band - 90f, 0f),
                                        end = Offset(band + 90f, size.height),
                                    ),
                                )
                            }
                        }
                    }
                } else {
                    onDrawBehind { }
                }
            },
    ) {
        // L2: 可拖拽单卡折射试玩 (API 33+), 独立于网格 —— 判断「要不要真折射」用
        if (params.motionL2 && Build.VERSION.SDK_INT >= 33) {
            LabLensCard(backdrop = backdrop, modifier = Modifier.matchParentSize())
        }
    }
}

/** 底图预渲染: 缩 512px → 三趟 box blur → ImageBitmap (仅背景/参数变化时算一次) */
private fun buildLabBackdrop(
    bg: Bitmap,
    bgAlpha: Float,
    baseColor: Color,
    scrimColor: Color,
    scrimAlpha: Float,
    winW: Int,
    winH: Int,
    blurRadiusPx: Int,
): ImageBitmap {
    val aspect = winW.toFloat() / winH
    var w = 512
    var h = (w / aspect).roundToInt().coerceAtLeast(1)
    if (h > 512) {
        h = 512
        w = (h * aspect).roundToInt().coerceAtLeast(1)
    }
    val small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(small)
    canvas.drawColor(baseColor.toArgb())
    val scale = max(w / bg.width.toFloat(), h / bg.height.toFloat())
    val dw = bg.width * scale
    val dh = bg.height * scale
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        alpha = (bgAlpha.coerceIn(0.1f, 1f) * 255f).roundToInt()
    }
    canvas.drawBitmap(bg, null, RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f), paint)
    canvas.drawColor(scrimColor.copy(alpha = scrimAlpha).toArgb())
    val px = IntArray(w * h)
    small.getPixels(px, 0, w, 0, 0, w, h)
    repeat(3) { i -> labBoxBlur(px, w, h, blurRadiusPx, horizontal = i % 2 == 0) }
    small.setPixels(px, 0, w, 0, 0, w, h)
    return small.asImageBitmap()
}

/** 单方向 box blur (滑动窗口增量), 纯 Kotlin */
private fun labBoxBlur(px: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
    if (radius <= 0 || w <= 0 || h <= 0) return
    val outer = if (horizontal) h else w
    val inner = if (horizontal) w else h
    val stride = if (horizontal) 1 else w
    val out = IntArray(px.size)
    val win = radius * 2 + 1
    for (o in 0 until outer) {
        val base = if (horizontal) o * w else o
        var ta = 0
        var tr = 0
        var tg = 0
        var tb = 0
        for (i in -radius..radius) {
            val p = px[base + stride * i.coerceIn(0, inner - 1)]
            ta += p ushr 24
            tr += (p shr 16) and 0xFF
            tg += (p shr 8) and 0xFF
            tb += p and 0xFF
        }
        for (i in 0 until inner) {
            out[base + stride * i] =
                ((ta / win) shl 24) or ((tr / win) shl 16) or ((tg / win) shl 8) or (tb / win)
            val po = px[base + stride * (i - radius).coerceIn(0, inner - 1)]
            val pi = px[base + stride * (i + radius + 1).coerceIn(0, inner - 1)]
            ta += (pi ushr 24) - (po ushr 24)
            tr += ((pi shr 16) and 0xFF) - ((po shr 16) and 0xFF)
            tg += ((pi shr 8) and 0xFF) - ((po shr 8) and 0xFF)
            tb += (pi and 0xFF) - (po and 0xFF)
        }
    }
    System.arraycopy(out, 0, px, 0, px.size)
}

/**
 * L2 可拖拽单卡: AGSL 边缘折射 (API 33+)。
 * 节点内容 = 底图上与卡片同窗的切片; RuntimeShader 按 SDF 做边缘折射 + Fresnel rim。
 */
@Composable
private fun LabLensCard(backdrop: ImageBitmap, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val cardW = with(density) { 150.dp.toPx() }
    val cardH = with(density) { 200.dp.toPx() }
    val radius = with(density) { 20.dp.toPx() }
    var drag by remember { mutableStateOf(Offset(80f, 160f)) }
    var nodeBounds by remember { mutableStateOf(Rect.Zero) }
    val shader = remember { runCatching { RuntimeShader(LENS_SKSL) }.getOrNull() }
    Box(
        modifier
            .pointerInput(Unit) {
                detectDragGestures { change, amount ->
                    change.consume()
                    drag = Offset(drag.x + amount.x, drag.y + amount.y)
                }
            }
            .onGloballyPositioned { nodeBounds = it.boundsInWindow() },
    ) {
        if (shader != null) {
            Box(
                Modifier
                    .offset { IntOffset(drag.x.roundToInt(), drag.y.roundToInt()) }
                    .size(with(density) { cardW.toDp() }, with(density) { cardH.toDp() })
                    .graphicsLayer {
                        runCatching {
                            shader.setFloatUniform("uSize", size.width, size.height)
                            shader.setFloatUniform("uRadius", radius)
                            shader.setFloatUniform("uHeight", radius * 0.9f)
                            renderEffect = RenderEffect
                                .createRuntimeShaderEffect(shader, "uBackdrop")
                                .asComposeRenderEffect()
                        }
                    }
                    .drawBehind {
                        // 节点内容 = 底图上与本窗对位的切片 (拖动即换采样区)
                        val winW = nodeBounds.width
                        val winH = nodeBounds.height
                        if (winW <= 0f || winH <= 0f) return@drawBehind
                        val sx = backdrop.width / max(winW, 1f)
                        val sy = backdrop.height / max(winH, 1f)
                        val srcL = nodeBounds.left * sx
                        val srcT = nodeBounds.top * sy
                        drawImage(
                            image = backdrop,
                            srcOffset = IntOffset(
                                srcL.roundToInt().coerceIn(0, backdrop.width - 1),
                                srcT.roundToInt().coerceIn(0, backdrop.height - 1),
                            ),
                            srcSize = IntSize((cardW * sx).roundToInt().coerceAtLeast(1), (cardH * sy).roundToInt().coerceAtLeast(1)),
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                            filterQuality = FilterQuality.Low,
                        )
                    },
            )
            Text(
                "拖我试折射 (L2)",
                fontSize = 11.sp, color = Color.White,
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), 5f)),
                modifier = Modifier.offset { IntOffset(drag.x.roundToInt() + 12, drag.y.roundToInt() + 8) },
            )
        }
    }
}

/** AGSL: 圆角矩形 SDF → 边缘折射 (沿法线内拉, 外 12px 保护带) → Fresnel rim; API 33+ */
private const val LENS_SKSL = """
uniform shader uBackdrop;
uniform float2 uSize;
uniform float uRadius;
uniform float uHeight;

half4 main(float2 xy) {
    float2 halfSize = uSize * 0.5;
    float2 q = abs(xy - halfSize) - (halfSize - float2(uRadius));
    float sd = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - uRadius;
    float dist = -sd;
    if (dist <= 0.0) return half4(0.0);
    float2 core = clamp(xy, float2(uRadius), uSize - float2(uRadius));
    float2 dir = xy - core;
    float2 n = (length(dir) > 0.001) ? normalize(dir) : float2(0.0, -1.0);
    float guard = smoothstep(0.0, 12.0, dist);
    float rim = clamp(1.0 - dist / max(uHeight, 1.0), 0.0, 1.0);
    float2 pull = n * pow(rim, 2.2) * uHeight * guard;
    float2 uv = clamp(xy - pull, float2(0.5), uSize - float2(0.5));
    half4 c = uBackdrop.eval(uv);
    c.rgb += half3(pow(rim, 3.0) * 0.35 * guard);
    return c;
}
"""

/** 无涟漪点击 (实验室工具行/图标用) */
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(Modifier.pointerInput(onClick) { detectTapGestures(onTap = { onClick() }) })
