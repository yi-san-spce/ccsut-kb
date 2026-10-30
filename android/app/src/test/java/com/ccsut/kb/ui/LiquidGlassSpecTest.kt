package com.ccsut.kb.ui

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 液态玻璃纯逻辑: 顶栏滚动分档 + boxBlur 内核 (纯 Kotlin, 不触 Android API) */
class LiquidGlassSpecTest {

    // ---- depthBucket ----

    @Test
    fun depthBucket_零滚动为最浅档() {
        assertEquals(0, LiquidGlassSpec.depthBucket(0))
    }

    @Test
    fun depthBucket_按步长进档() {
        assertEquals(1, LiquidGlassSpec.depthBucket(LiquidGlassSpec.SCROLL_STEP_PX))
        assertEquals(
            2,
            LiquidGlassSpec.depthBucket(LiquidGlassSpec.SCROLL_STEP_PX * 2),
        )
        assertEquals(
            0,
            LiquidGlassSpec.depthBucket(LiquidGlassSpec.SCROLL_STEP_PX - 1),
        )
    }

    @Test
    fun depthBucket_钳到最深档且负值不越界() {
        assertEquals(
            LiquidGlassSpec.SCROLL_MAX_BUCKET,
            LiquidGlassSpec.depthBucket(Int.MAX_VALUE),
        )
        assertEquals(0, LiquidGlassSpec.depthBucket(-1))
        assertEquals(0, LiquidGlassSpec.depthBucket(-5000))
    }

    @Test
    fun depthFrac_始终落在零到一() {
        assertEquals(0f, LiquidGlassSpec.depthFrac(0), 1e-6f)
        assertEquals(
            1f,
            LiquidGlassSpec.depthFrac(LiquidGlassSpec.SCROLL_MAX_BUCKET),
            1e-6f,
        )
        val mid = LiquidGlassSpec.depthFrac(LiquidGlassSpec.SCROLL_MAX_BUCKET / 2)
        assertTrue(mid in 0f..1f)
    }

    // ---- boxBlur ----

    @Test
    fun boxBlur_均匀图不变() {
        val w = 9
        val h = 7
        val px = IntArray(w * h) { 0xFF336699.toInt() }
        val expected = px.copyOf()
        boxBlur(px, w, h, 2, horizontal = true)
        boxBlur(px, w, h, 2, horizontal = false)
        assertArrayEquals(expected, px)
    }

    @Test
    fun boxBlur_零半径不动() {
        val px = IntArray(25) { it }
        val expected = px.copyOf()
        boxBlur(px, 5, 5, 0, horizontal = true)
        boxBlur(px, 5, 5, 0, horizontal = false)
        assertArrayEquals(expected, px)
    }

    @Test
    fun boxBlur_高频条纹被抹平() {
        // 黑白交替竖纹: 模糊后中间像素不再是纯黑/纯白
        val w = 16
        val h = 16
        val px = IntArray(w * h) { if (it % w % 2 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        boxBlur(px, w, h, 3, horizontal = true)
        val mid = px[8 * w + 8]
        val r = (mid shr 16) and 0xFF
        val a = mid ushr 24
        assertEquals(255, a)   // 底图恒不透明
        assertTrue("中间像素应被抹平, 实际 r=$r", r in 1..254)
    }

    @Test
    fun boxBlur_列方向同样有效() {
        val w = 4
        val h = 16
        // 横向均匀、纵向黑白交替
        val px = IntArray(w * h) { if (it / w % 2 == 0) 0xFF202020.toInt() else 0xFFE0E0E0.toInt() }
        boxBlur(px, w, h, 2, horizontal = false)
        val mid = px[8 * w + 2]
        val g = (mid shr 8) and 0xFF
        assertTrue("纵向也应被抹平, 实际 g=$g", g in 1..254)
    }

    @Test
    fun boxBlur_边界像素受钳制不影响不越界() {
        val w = 6
        val h = 6
        val px = IntArray(w * h) { 0xFF808080.toInt() }
        // 只允许不抛异常 + 长度不变
        boxBlur(px, w, h, 10, horizontal = true)
        boxBlur(px, w, h, 10, horizontal = false)
        assertEquals(w * h, px.size)
        assertEquals(0xFF808080.toInt(), px[0])
    }
}
