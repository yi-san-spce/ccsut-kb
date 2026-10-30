package com.ccsut.kb.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 课程卡液态玻璃 (v3): 近纯透明玻璃 + 状态栏同款视觉语言 (低染色/glassEdge 白高光/顶面反光),
 * 课程区分只靠左缘竖线 (色系区分推迟)。材质配方对齐用户实机标定 (状态栏 87% 档的观感)。
 *
 * 性能架构 (调研 docs/开发者/liquid-glass-research.md): 背景图静态 → 模糊只算一次;
 * 周视图纵向无滚动 → 静止零每帧成本; 动态仅翻周滑动 (~300ms 每页 7 次 clip+blit)。
 * 效果节点数恒为 0, 禁止任何每卡每帧回调/状态写入 (a8c94a6/60170f9 两次教训)。
 */
object LiquidGlassSpec {
    // 中性染色: 状态栏同源 surface 色, 近纯透明 (87% 档的量级)
    const val TINT_ALPHA_LIGHT = 0.10f
    const val TINT_ALPHA_DARK = 0.14f

    // glassEdge 三段白高光强度 (状态栏 = 1.0, 卡片略收敛)
    const val EDGE_STRENGTH = 0.9f

    // 顶面反光 (状态栏 TodayBar 同款全强度)
    const val SHEEN_LIGHT = 0.22f
    const val SHEEN_DARK = 0.10f

    // 按压: 缩放 + 反光增强 (由内点亮, 白色)
    const val PRESS_SCALE = 0.96f
    const val PRESS_SHEEN_BOOST = 0.9f

    // 预渲染底图: 512px + 半径 24 (屏等效 ≈20dp, 轻霜化不糊)
    const val BACKDROP_MAX_PX = 512
    const val BACKDROP_BLUR_RADIUS = 24
}

/** 模糊底图预渲染参数 (整体作 produceState key, data class 等值比较避免无效重算) */
private data class BackdropCfg(
    val bg: Bitmap,
    val alpha: Float,
    val base: Color,
    val scrim: Color,
    val scrimA: Float,
    val w: Int,
    val h: Int,
)

/**
 * 预渲染模糊底图 (屏幕级, 只建一次): 复刻真实可见层 (底色 → 背景图 Crop ×bgAlpha → scrim)
 * 后缩到 512px 小图, 三趟 box blur ≈ 高斯; 绘制时双线性放大。
 */
@Composable
fun rememberGlassBackdrop(
    bgBitmap: Bitmap,
    bgAlpha: Float,
    baseColor: Color,
    scrimColor: Color,
    scrimAlpha: Float,
    winSizePx: IntSize,
): ImageBitmap? {
    val cfg = BackdropCfg(
        bgBitmap, bgAlpha, baseColor, scrimColor, scrimAlpha, winSizePx.width, winSizePx.height,
    )
    return produceState<ImageBitmap?>(null, cfg) {
        value = if (cfg.w == 0 || cfg.h == 0) null else withContext(Dispatchers.Default) {
            runCatching {
                buildGlassBackdrop(cfg.bg, cfg.alpha, cfg.base, cfg.scrim, cfg.scrimA, cfg.w, cfg.h)
            }.getOrNull()
        }
    }.value
}

private fun buildGlassBackdrop(
    bg: Bitmap,
    bgAlpha: Float,
    baseColor: Color,
    scrimColor: Color,
    scrimAlpha: Float,
    winW: Int,
    winH: Int,
): ImageBitmap {
    val aspect = winW.toFloat() / winH
    var w = LiquidGlassSpec.BACKDROP_MAX_PX
    var h = (w / aspect).roundToInt().coerceAtLeast(1)
    if (h > LiquidGlassSpec.BACKDROP_MAX_PX) {
        h = LiquidGlassSpec.BACKDROP_MAX_PX
        w = (h * aspect).roundToInt().coerceAtLeast(1)
    }
    val small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(small)
    canvas.drawColor(baseColor.toArgb())
    // ContentScale.Crop 等价: 等比放大铺满、居中裁切
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
    repeat(3) { i -> boxBlur(px, w, h, LiquidGlassSpec.BACKDROP_BLUR_RADIUS, horizontal = i % 2 == 0) }
    small.setPixels(px, 0, w, 0, 0, w, h)
    return small.asImageBitmap()
}

/**
 * 单方向 box blur (滑动窗口增量, O(像素) 每趟)。纯 Kotlin 无 Android 依赖, JVM 单测可跑。
 */
internal fun boxBlur(px: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
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
 * 课程卡玻璃开窗层 (每页一个, 与滚动 Box 同界)。
 *
 * - 卡片矩形由 WeekGrid 按槽位几何解析计算 (content 坐标, 静止恒定), 按天分组 (7 列)
 * - Draw 相位读 scrollState (verticalScroll 兜底场景) 与每列入场 alpha —— 只失效 Draw, 不重组
 * - src/dst 【求交】而非夹取 —— 修 v2.2 翻周错位 (coerceIn 把半屏页采样点夹到 0)
 * - 每列独立 alpha 跟随落场淡入 —— 修 v2.2 闪窗 (窗先于卡片内容出现)
 * - 静止零每帧成本; 翻周时 selfBounds 跟随 (1 写/帧/页)
 */
@Composable
fun GlassCardWindowsOverlay(
    backdrop: ImageBitmap,
    cardRectsByDay: List<List<Rect>>,
    entranceAlpha: (Int) -> Float,
    scrollState: ScrollState,
    windowSizePx: IntSize,
    cornerRadiusPx: Float,
    modifier: Modifier = Modifier,
) {
    var selfBounds by remember { mutableStateOf(Rect.Zero) }
    val paths = remember { List(7) { Path() } }
    Box(
        modifier
            .onGloballyPositioned { selfBounds = it.boundsInWindow() }
            .drawWithCache {
                val sb = selfBounds
                val winW = windowSizePx.width
                val winH = windowSizePx.height
                if (sb.width > 0f && sb.height > 0f && winW > 0 && winH > 0) {
                    val sx = backdrop.width / winW.toFloat()
                    val sy = backdrop.height / winH.toFloat()
                    // Draw 相位读滚动: 只失效 Draw, 不重组不布局
                    val scroll = scrollState.value
                    for (d in 0..6) {
                        paths[d].reset()
                        for (r in cardRectsByDay.getOrElse(d) { emptyList() }) {
                            val top = r.top - scroll
                            val bottom = r.bottom - scroll
                            if (top > sb.height || bottom < 0f) continue   // 视口外
                            paths[d].addRoundRect(
                                RoundRect(Rect(r.left, top, r.right, bottom), CornerRadius(cornerRadiusPx)),
                            )
                        }
                    }
                    // src(底图像素) 与 dst(本页局部 px) 一一对应: 对 src 求交, dst 同步裁剪
                    val il = max(0f, sb.left * sx)
                    val tc = max(0f, sb.top * sy)
                    val ir = min(backdrop.width.toFloat(), sb.right * sx)
                    val ib = min(backdrop.height.toFloat(), sb.bottom * sy)
                    val srcOk = ir > il && ib > tc
                    val dstL = il / sx - sb.left
                    val dstT = tc / sy - sb.top
                    val dstW = (ir - il) / sx
                    val dstH = (ib - tc) / sy
                    onDrawBehind {
                        if (!srcOk) return@onDrawBehind
                        for (d in 0..6) {
                            if (paths[d].isEmpty) continue
                            val a = entranceAlpha(d)
                            if (a <= 0.01f) continue
                            clipPath(paths[d]) {
                                drawImage(
                                    image = backdrop,
                                    srcOffset = IntOffset(il.roundToInt(), tc.roundToInt()),
                                    srcSize = IntSize((ir - il).roundToInt().coerceAtLeast(1), (ib - tc).roundToInt().coerceAtLeast(1)),
                                    dstOffset = IntOffset(dstL.roundToInt(), dstT.roundToInt()),
                                    dstSize = IntSize(dstW.roundToInt().coerceAtLeast(1), dstH.roundToInt().coerceAtLeast(1)),
                                    alpha = a,
                                    filterQuality = FilterQuality.Low,
                                )
                            }
                        }
                    }
                } else {
                    onDrawBehind { }
                }
            },
    )
}
