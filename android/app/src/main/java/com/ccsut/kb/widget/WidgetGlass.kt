package com.ccsut.kb.widget

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader

/**
 * 小组件「液态玻璃」磨砂快照: 抓当前桌面壁纸 → 降采样再放大 (快速伪高斯) → 烤染色与顶部高光。
 * RemoteViews 无实时 blur (窗口级能力不覆盖 widget), 预渲染位图是唯一全机型一致的毛玻璃路径。
 * 位图 ≤480px 宽 (≈0.7MB ARGB), 远低于 RemoteViews 的位图内存限额 (~1.5 屏)。
 * 渲染在 goAsync 后台线程现算: 24px 中间态再双线性放大, 无需高斯卷积, 单次 <20ms。
 */
object WidgetGlass {

    /**
     * [strength] 0-100 (Prefs.widgetGlass), 返回 null 表示玻璃不可用 (强度 0 / 壁纸不可得 /
     * 解码失败), 调用方回落实色卡片路径。[tintBase] 为玻璃染色基色 (不透明), [aspectW/H] 为
     * 当前组件宽高比 (dp), [hlAlpha] 顶部高光强度 (浅色 0.42 / 深色 0.14, 同 App glassEdge)。
     */
    fun snapshot(
        ctx: Context,
        strength: Int,
        tintBase: Int,
        aspectW: Int,
        aspectH: Int,
        hlAlpha: Float,
    ): Bitmap? {
        if (strength <= 0) return null
        val drawable = runCatching { WallpaperManager.getInstance(ctx).drawable }.getOrNull() ?: return null
        return runCatching {
            val aspect = aspectW.coerceAtLeast(1).toFloat() / aspectH.coerceAtLeast(1)
            // 目标: ≤480px 宽的成品; 模糊中间态 96px → 24px, 放大回来即柔和高斯观感
            val tw = 480
            val th = (tw / aspect).toInt().coerceIn(64, 1024)
            val sw = 96
            val sh = (96 / aspect).toInt().coerceIn(24, 192)

            val small = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
            val cs = Canvas(small)
            val dw = drawable.intrinsicWidth
            val dh = drawable.intrinsicHeight
            if (dw <= 0 || dh <= 0) {
                // 尺寸未知: 直接拉伸绘制
                drawable.setBounds(0, 0, sw, sh)
                drawable.draw(cs)
            } else {
                // 居中裁剪到目标宽高比再缩到 sw×sh
                val srcAspect = dw.toFloat() / dh
                val cropW: Int
                val cropH: Int
                if (srcAspect > aspect) {
                    cropH = dh
                    cropW = (dh * aspect).toInt().coerceAtMost(dw)
                } else {
                    cropW = dw
                    cropH = (dw / aspect).toInt().coerceAtMost(dh)
                }
                val sx = (dw - cropW) / 2
                val sy = (dh - cropH) / 2
                val scale = sw.toFloat() / cropW
                cs.save()
                cs.scale(scale, scale)
                cs.translate(-sx.toFloat(), -sy.toFloat())
                drawable.setBounds(0, 0, dw, dh)
                drawable.draw(cs)
                cs.restore()
            }

            val tinyW = (sw / 4).coerceAtLeast(8)
            val tinyH = (sh / 4).coerceAtLeast(8)
            val tiny = Bitmap.createScaledBitmap(small, tinyW, tinyH, true)
            small.recycle()
            val out = Bitmap.createScaledBitmap(tiny, tw, th, true)
            tiny.recycle()

            val c = Canvas(out)
            // 玻璃染色: 强度 10→~0.20, 55→~0.46, 100→~0.72 —— 低强度透亮, 高强度保可读
            val alpha = (0.14f + 0.58f * (strength.coerceIn(0, 100) / 100f)).coerceIn(0f, 1f)
            val p = Paint().apply { color = tintBase }
            p.alpha = (alpha * 255).toInt()
            c.drawRect(0f, 0f, tw.toFloat(), th.toFloat(), p)
            // 顶部高光: 玻璃上沿一道淡白渐变 (液态玻璃的关键一笔), 40% 高度内渐隐
            val hl = hlAlpha.coerceIn(0f, 1f)
            p.shader = LinearGradient(
                0f, 0f, 0f, th * 0.4f,
                Color.argb((hl * 255).toInt(), 255, 255, 255),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
            c.drawRect(RectF(0f, 0f, tw.toFloat(), th.toFloat()), p)
            out
        }.getOrNull()
    }
}
