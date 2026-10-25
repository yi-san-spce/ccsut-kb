package com.ccsut.kb

import org.junit.Assert.assertEquals
import org.junit.Test

/** 旧「不透明度」键 → 新「透明度」键的翻转迁移: 老用户视觉必须不变 */
class PrefsMigrationTest {

    @Test
    fun legacyDefault_fullyOpaque_mapsToZero() {
        assertEquals(0, Prefs.migrateLegacyTodayBarAlpha(100))
    }

    @Test
    fun legacyMinimum_mapsTo90() {
        assertEquals(90, Prefs.migrateLegacyTodayBarAlpha(10))
    }

    @Test
    fun legacyMidpoint_mapsToMidpoint() {
        assertEquals(45, Prefs.migrateLegacyTodayBarAlpha(55))
    }

    @Test
    fun neverStored_mapsToZero() {
        // 旧键缺席时旧默认 100(不透明), 等价于新键 0
        assertEquals(0, Prefs.migrateLegacyTodayBarAlpha(null))
    }

    @Test
    fun outOfRangeLegacy_clampsIntoDomain() {
        // 旧域外值钳位: 0(极限不透明)→100(全透明), 120 越界→0(不透明)
        assertEquals(100, Prefs.migrateLegacyTodayBarAlpha(0))
        assertEquals(0, Prefs.migrateLegacyTodayBarAlpha(120))
    }
}
