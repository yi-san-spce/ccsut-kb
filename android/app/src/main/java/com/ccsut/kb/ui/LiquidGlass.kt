package com.ccsut.kb.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 液态玻璃参数集中地: 真机调参只改这里, 不动逻辑。
 *
 * 性能红线 (b2553e1 回退的教训): 周网格 40+ 块各挂一个实时模糊效果节点会逐帧掉帧。
 * 本方案的模糊只在背景图/主题变化时算一次 (预渲染小图), 滚动每帧只是位图 blit,
 * 全屏效果节点数恒为 0。
 */
object LiquidGlassSpec {
    // ---- 卡片有色玻璃: 透过玻璃看到的课程色浓度 (区分度第一保险) ----
    const val TINT_ALPHA_LIGHT = 0.62f
    const val TINT_ALPHA_DARK = 0.70f

    // ---- 同色光影: 区分度第二保险 ----
    // 卡片外围课程色环境光 (柔光渐晕)
    const val GLOW_ALPHA_LIGHT = 0.16f
    const val GLOW_ALPHA_DARK = 0.22f
    // 底边课程色辉光描边 (rim light)
    const val RIM_ALPHA_LIGHT = 0.40f
    const val RIM_ALPHA_DARK = 0.30f
    // 顶面白色镜面高光 (按压时×1.9 = 由内点亮)
    const val SPECULAR_LIGHT = 0.20f
    const val SPECULAR_DARK = 0.10f

    // ---- 按压 ----
    const val PRESS_SCALE = 0.96f

    // ---- 预渲染模糊底图 ----
    // 小图最长边: 越小越糊也越省内存; 三趟 box blur 在小图上等效高斯, 半径按小图标
    const val BACKDROP_MAX_PX = 224
    const val BACKDROP_BLUR_RADIUS = 6

    // ---- 顶栏滚动联动: 每滚 SCROLL_STEP_PX 深一档, 顶栏样式每档重建一次 (不逐帧打脏标记) ----
    const val SCROLL_STEP_PX = 140
    const val SCROLL_MAX_BUCKET = 6

    fun depthBucket(scrollPx: Int): Int = (scrollPx / SCROLL_STEP_PX).coerceIn(0, SCROLL_MAX_BUCKET)

    fun depthFrac(bucket: Int): Float = bucket.toFloat() / SCROLL_MAX_BUCKET
}

/**
 * 卡片开窗注册表: key → 卡片在 window 坐标系的矩形。
 * 由各卡片 [glassWindow] 经 onGloballyPositioned 维护 (布局期写入),
 * 开窗层在 Draw 相位读取 —— 滚动/翻周只触发 Draw 重绘, 无重组。
 */
class GlassWindowsState {
    val rects = mutableStateMapOf<String, Rect>()

    fun put(key: String, rect: Rect) {
        if (rects[key] != rect) rects[key] = rect
    }

    fun remove(key: String) {
        rects.remove(key)
    }
}

/** 课程卡挂上开窗注册: 布局位置变化 (滚动/翻周/按压缩放) 自动回写注册表 */
fun Modifier.glassWindow(state: GlassWindowsState?, key: String): Modifier =
    this.onGloballyPositioned { coords -> state?.put(key, coords.boundsInWindow()) }

/** 模糊底图预渲染参数 (整体作 produceState 的 key, data class 等值比较避免无效重算) */
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
 * 液态玻璃开窗层: 铺满根 Box (背景 scrim 之上、课表内容之下)。
 * 把「预模糊底图」按每张课程卡注册的矩形开窗画出 —— 卡片自己的半透明 tint 叠在上面,
 * 透出的就是这块模糊底图, 实现「玻璃透出背景」而不需要任何实时模糊效果节点。
 */
@Composable
fun LiquidGlassWindows(
    bgBitmap: Bitmap?,
    bgAlpha: Float,
    baseColor: Color,
    scrimColor: Color,
    scrimAlpha: Float,
    windows: GlassWindowsState,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
) {
    if (bgBitmap == null) return
    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    var selfBounds by remember { mutableStateOf(Rect.Zero) }
    Box(
        modifier
            .onSizeChanged { sizePx = it }
            .onGloballyPositioned { selfBounds = it.boundsInWindow() },
    ) {
        val backdropCfg = BackdropCfg(
            bgBitmap, bgAlpha, baseColor, scrimColor, scrimAlpha, sizePx.width, sizePx.height,
        )
        val backdrop by produceState<ImageBitmap?>(null, backdropCfg) {
            value = if (backdropCfg.w == 0 || backdropCfg.h == 0) null else withContext(Dispatchers.Default) {
                runCatching {
                    buildBlurredBackdrop(
                        backdropCfg.bg, backdropCfg.alpha, backdropCfg.base,
                        backdropCfg.scrim, backdropCfg.scrimA, backdropCfg.w, backdropCfg.h,
                    )
                }.getOrNull()
            }
        }
        val bmp = backdrop ?: return@Box
        val radiusPx = with(LocalDensity.current) { cornerRadius.toPx() }
        Box(
            Modifier
                .matchParentSize()
                .drawWithCache {
                    val rects = windows.rects.values.toList()
                    onDrawBehind {
                        val sb = selfBounds
                        if (sb.width <= 0f || sb.height <= 0f) return@onDrawBehind
                        val sx = bmp.width / sb.width
                        val sy = bmp.height / sb.height
                        rects.forEach { r ->
                            if (r.width <= 0f || r.height <= 0f) return@forEach
                            // 邻页/屏外的窗不画
                            if (r.right < sb.left || r.left > sb.right || r.bottom < sb.top || r.top > sb.bottom) {
                                return@forEach
                            }
                            val local = r.translate(-sb.left, -sb.top)
                            val path = Path().apply { addRoundRect(RoundRect(local, CornerRadius(radiusPx))) }
                            clipPath(path) {
                                drawImage(
                                    image = bmp,
                                    srcOffset = IntOffset(
                                        (local.left * sx).roundToInt().coerceIn(0, bmp.width - 1),
                                        (local.top * sy).roundToInt().coerceIn(0, bmp.height - 1),
                                    ),
                                    srcSize = IntSize(
                                        (local.width * sx).roundToInt().coerceAtLeast(1),
                                        (local.height * sy).roundToInt().coerceAtLeast(1),
                                    ),
                                    dstOffset = IntOffset(local.left.roundToInt(), local.top.roundToInt()),
                                    dstSize = IntSize(
                                        local.width.roundToInt().coerceAtLeast(1),
                                        local.height.roundToInt().coerceAtLeast(1),
                                    ),
                                    filterQuality = FilterQuality.Medium,
                                )
                            }
                        }
                    }
                },
        )
    }
}

/**
 * 预渲染模糊底图: 复刻真实可见层 (底色 → 背景图 Crop ×bgAlpha → scrim) 后缩到小图,
 * 三趟 box blur (横竖交替) ≈ 高斯。绘制时双线性放大回窗口尺寸, 磨砂质感成立。
 */
internal fun buildBlurredBackdrop(
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
 * 单方向 box blur (原地语义, 经临时缓冲): horizontal=true 沿行扫, false 沿列扫。
 * 滑动窗口增量更新, O(像素) 每趟。纯 Kotlin 无 Android 依赖, JVM 单测可跑。
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
