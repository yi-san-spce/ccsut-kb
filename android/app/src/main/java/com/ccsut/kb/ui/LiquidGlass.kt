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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 液态玻璃参数集中地: 真机调参只改这里, 不动逻辑。
 *
 * 材质配方 (对齐 Apple Clear/Regular 思路 + 用户实机反馈「87% 透明档才像玻璃」):
 * 玻璃本体近乎透明 (中性 frost 极低 alpha), 课程色只以「小面积色元素」出现 ——
 * wash 低浓度色洗 + 底边 rim light + 左缘色条; 文字白色+暗影 (bold & bright)。
 *
 * 性能红线 (b2553e1 / 60170f9 两次教训): 效果节点数恒为 0, 模糊只在背景变化时算一次,
 * 滚动每帧 = 每页 1 次 clipPath + 1 次 drawImage, 禁止任何每卡每帧的回调/状态写入/裁剪。
 */
object LiquidGlassSpec {
    // ---- 中性玻璃体: 白(亮)/黑(暗) frost, 顶略重 ----
    const val FROST_ALPHA_LIGHT = 0.16f
    const val FROST_ALPHA_DARK = 0.24f
    const val FROST_TOP_BOOST = 0.04f

    // ---- 课程色洗: 「稍微有点颜色」的浓度 ----
    const val WASH_ALPHA_LIGHT = 0.16f
    const val WASH_ALPHA_DARK = 0.22f

    // ---- 底边课程色 rim light: 首要颜色载体 ----
    const val RIM_ALPHA_LIGHT = 0.55f
    const val RIM_ALPHA_DARK = 0.45f

    // ---- 左缘课程色竖条: 最可靠的区分标识 ----
    const val ACCENT_BAR_ALPHA = 0.95f

    // ---- 顶面镜面高光 (按压时增强 = 由内点亮) ----
    const val SPECULAR_LIGHT = 0.20f
    const val SPECULAR_DARK = 0.10f

    const val PRESS_SCALE = 0.96f

    // ---- 预渲染模糊底图: 512px + 半径 24 ≈ 屏等效 18dp, 奶感足够且只算一次 ----
    const val BACKDROP_MAX_PX = 512
    const val BACKDROP_BLUR_RADIUS = 24

    // ---- 顶栏滚动联动: 每滚 SCROLL_STEP_PX 深一档, 顶栏样式每档重建一次 (不逐帧打脏标记) ----
    const val SCROLL_STEP_PX = 140
    const val SCROLL_MAX_BUCKET = 6

    fun depthBucket(scrollPx: Int): Int = (scrollPx / SCROLL_STEP_PX).coerceIn(0, SCROLL_MAX_BUCKET)

    fun depthFrac(bucket: Int): Float = bucket.toFloat() / SCROLL_MAX_BUCKET
}

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
 * 预渲染模糊底图 (屏幕级, 只建一次): 复刻真实可见层 (底色 → 背景图 Crop ×bgAlpha → scrim)
 * 后缩到小图, 三趟 box blur (横竖交替) ≈ 高斯。绘制时双线性放大回窗口尺寸。
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
                buildBlurredBackdrop(
                    cfg.bg, cfg.alpha, cfg.base, cfg.scrim, cfg.scrimA, cfg.w, cfg.h,
                )
            }.getOrNull()
        }
    }.value
}

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
    val scale = maxOf(w / bg.width.toFloat(), h / bg.height.toFloat())
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

/**
 * 课程卡玻璃开窗层 (每页一个, 与滚动 Box 同界)。
 *
 * 性能架构 (v2.2): 卡片矩形由 WeekGrid 按槽位几何解析计算 (content 坐标, 滚动期间恒定),
 * 本层在 Draw 相位读 scrollState —— 滚动每帧只有 1 次 path 重建 + 1 次 clipPath + 1 次 drawImage,
 * 零回调、零状态写入、零每帧分配 (Path 复用实例 reset)。
 * 翻周滑动时 overlay 随页平移, 自身 window 坐标变化 → src 偏移自动跟随, 底图内容仍然对位。
 */
@Composable
fun GlassCardWindowsOverlay(
    backdrop: ImageBitmap,
    cardRectsPx: List<Rect>,
    scrollState: ScrollState,
    windowSizePx: IntSize,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
) {
    var selfBounds by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current
    val radiusPx = remember(density, cornerRadius) { with(density) { cornerRadius.toPx() } }
    val path = remember { Path() }
    Box(
        modifier
            .onGloballyPositioned { selfBounds = it.boundsInWindow() }
            .drawWithCache {
                val sb = selfBounds
                val winW = windowSizePx.width
                val winH = windowSizePx.height
                if (sb.width > 0f && sb.height > 0f && winW > 0 && winH > 0 && cardRectsPx.isNotEmpty()) {
                    // Draw 相位读滚动: 只失效 Draw, 不重组不布局
                    val scroll = scrollState.value
                    path.reset()
                    for (r in cardRectsPx) {
                        val top = r.top - scroll
                        val bottom = r.bottom - scroll
                        if (top > sb.height || bottom < 0f) continue   // 视口外
                        path.addRoundRect(
                            RoundRect(
                                rect = Rect(r.left, top, r.right, bottom),
                                cornerRadius = CornerRadius(radiusPx),
                            ),
                        )
                    }
                    val sx = backdrop.width / winW.toFloat()
                    val sy = backdrop.height / winH.toFloat()
                    onDrawBehind {
                        clipPath(path) {
                            drawImage(
                                image = backdrop,
                                srcOffset = IntOffset(
                                    (sb.left * sx).roundToInt().coerceIn(0, backdrop.width - 1),
                                    (sb.top * sy).roundToInt().coerceIn(0, backdrop.height - 1),
                                ),
                                srcSize = IntSize(
                                    (sb.width * sx).roundToInt().coerceAtLeast(1),
                                    (sb.height * sy).roundToInt().coerceAtLeast(1),
                                ),
                                dstOffset = IntOffset.Zero,
                                dstSize = IntSize(
                                    sb.width.roundToInt().coerceAtLeast(1),
                                    sb.height.roundToInt().coerceAtLeast(1),
                                ),
                                filterQuality = FilterQuality.Low,
                            )
                        }
                    }
                } else {
                    onDrawBehind { }
                }
            },
    )
}
