package com.ccsut.kb.ui

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 液态玻璃 v3 纯逻辑: boxBlur 内核 (纯 Kotlin) + Spec 参数域 */
class LiquidGlassSpecTest {

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
        val px = IntArray(w * h) { if (it / w % 2 == 0) 0xFF202020.toInt() else 0xFFE0E0E0.toInt() }
        boxBlur(px, w, h, 2, horizontal = false)
        val mid = px[8 * w + 2]
        val g = (mid shr 8) and 0xFF
        assertTrue("纵向也应被抹平, 实际 g=$g", g in 1..254)
    }

    @Test
    fun boxBlur_大半径不越界不崩() {
        val w = 6
        val h = 6
        val px = IntArray(w * h) { 0xFF808080.toInt() }
        boxBlur(px, w, h, 10, horizontal = true)
        boxBlur(px, w, h, 10, horizontal = false)
        assertEquals(w * h, px.size)
        assertEquals(0xFF808080.toInt(), px[0])
    }

    // ---- Spec 参数域 (材质基线, 防止误调出实色) ----

    @Test
    fun spec_染色保持近透明() {
        assertTrue("亮色染色应 <0.2 (87% 标定), 实际 ${LiquidGlassSpec.TINT_ALPHA_LIGHT}", LiquidGlassSpec.TINT_ALPHA_LIGHT < 0.2f)
        assertTrue("暗色染色应 <0.25, 实际 ${LiquidGlassSpec.TINT_ALPHA_DARK}", LiquidGlassSpec.TINT_ALPHA_DARK < 0.25f)
    }

    @Test
    fun spec_玻璃边缘与顶面反光在状态栏量级() {
        assertTrue(LiquidGlassSpec.EDGE_STRENGTH in 0.5f..1.0f)
        assertTrue(LiquidGlassSpec.SHEEN_LIGHT in 0.05f..0.35f)
        assertTrue(LiquidGlassSpec.SHEEN_DARK <= LiquidGlassSpec.SHEEN_LIGHT)
    }

    @Test
    fun spec_按压回弹幅度合理() {
        assertTrue(LiquidGlassSpec.PRESS_SCALE in 0.9f..1.0f)
        assertTrue(LiquidGlassSpec.PRESS_SHEEN_BOOST in 0f..2f)
    }

    @Test
    fun spec_底图预算不超内存红线() {
        // 512px 长边 ≈ 2MB 内, blur 半径落在轻霜化区间
        assertTrue(LiquidGlassSpec.BACKDROP_MAX_PX <= 512)
        assertTrue(LiquidGlassSpec.BACKDROP_BLUR_RADIUS in 4..40)
    }
}
