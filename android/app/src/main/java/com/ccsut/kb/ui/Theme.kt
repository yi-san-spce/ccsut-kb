package com.ccsut.kb.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import kotlin.math.max
import kotlin.math.min

private val LightColors = lightColorScheme(
    primary = Color(0xFF3B64D8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE3FF),
    onPrimaryContainer = Color(0xFF00174A),
    secondary = Color(0xFF595D72),
    tertiary = Color(0xFF76546E),
    background = Color(0xFFFBF8FF),
    surface = Color(0xFFFBF8FF),
    surfaceVariant = Color(0xFFE2E2EC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB7C4FF),
    onPrimary = Color(0xFF24337A),
    primaryContainer = Color(0xFF3C4A96),
    onPrimaryContainer = Color(0xFFDCE3FF),
    secondary = Color(0xFFC2C5DD),
    tertiary = Color(0xFFE5BAD9),
    background = Color(0xFF131318),
    surface = Color(0xFF131318),
    surfaceVariant = Color(0xFF45464F),
)

@Composable
fun KbTheme(themeMode: Int = 0, colorSource: Int = 0, seed: Int = 0, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val scheme: ColorScheme = when (colorSource) {
        // 跟随背景图取色: 种子色缺失时退回壁纸配色
        1 -> if (seed != 0) {
            com.materialkolor.dynamicColorScheme(
                seedColor = Color(seed),
                isDark = dark,
                isAmoled = false,
                style = com.materialkolor.PaletteStyle.TonalSpot,
            )
        } else if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        // 固定品牌色
        2 -> if (dark) DarkColors else LightColors
        // 跟随系统壁纸 (默认)
        else -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** 课程块配色: 在动态取色的容器色基础上做色相旋转, 保证整版协调 */
object CourseColors {

    private const val N = 10

    /** 自动配色的种子: 同一门课(同名+同分项)共用一个颜色 */
    fun seedOf(kc: String, fx: String) = "$kc|$fx"

    /** 自定义色板数量 */
    const val CUSTOM_N = 12

    /** 课程块颜色: custom 非空用自定义色板, 否则自动配色 */
    fun container(base: ColorScheme, seed: String, custom: Int? = null): Color =
        custom?.let { customContainer(it, isDark(base)) } ?: autoContainer(base, seed)

    fun onContainer(base: ColorScheme, seed: String, custom: Int? = null): Color =
        custom?.let { customOnContainer(it, isDark(base)) } ?: autoOnContainer(base, seed)

    private fun autoContainer(base: ColorScheme, seed: String): Color {
        val dark = isDark(base)
        val src = when (kotlin.math.abs(seed.hashCode()) % 3) {
            0 -> base.primaryContainer
            1 -> base.secondaryContainer
            else -> base.tertiaryContainer
        }
        val rotated = rotateHue(src, ((kotlin.math.abs(seed.hashCode()) % 8) - 3.5f) * 24f)
        return vivid(rotated, dark)
    }

    /** 深色模式提鲜: 饱和度×1.35(≤0.65)、亮度+0.07(≤0.42), 摆脱动态取色容器色的灰闷感 */
    private fun vivid(c: Color, dark: Boolean): Color {
        if (!dark) return c
        val (h, s, l, a) = hslOf(c)
        return hsl(h, (s * 1.35f).coerceAtMost(0.65f), (l + 0.07f).coerceAtMost(0.42f), a)
    }

    private fun autoOnContainer(base: ColorScheme, seed: String): Color {
        val src = when (kotlin.math.abs(seed.hashCode()) % 3) {
            0 -> base.onPrimaryContainer
            1 -> base.onSecondaryContainer
            else -> base.onTertiaryContainer
        }
        return rotateHue(src, ((kotlin.math.abs(seed.hashCode()) % 8) - 3.5f) * 24f)
    }

    /** 当前配色方案是否为深色(按背景亮度判断, 不依赖系统主题) */
    fun isDark(base: ColorScheme): Boolean = base.background.luminance() < 0.5f

    /** 自定义色板容器色: 每档一个色相, 深/浅两套明度 (深色提饱和提亮, 鲜而不闷) */
    fun customContainer(idx: Int, dark: Boolean): Color =
        if (dark) hsl(idx * 30f, 0.48f, 0.36f) else hsl(idx * 30f, 0.62f, 0.86f)

    fun customOnContainer(idx: Int, dark: Boolean): Color =
        if (dark) hsl(idx * 30f, 0.55f, 0.90f) else hsl(idx * 30f, 0.72f, 0.28f)

    /** ARGB -> HSL 色相旋转 -> ARGB */
    fun rotateHue(c: Color, degrees: Float): Color {
        val a = (c.toArgb() ushr 24) / 255f
        val r = ((c.toArgb() shr 16) and 0xFF) / 255f
        val g = ((c.toArgb() shr 8) and 0xFF) / 255f
        val b = (c.toArgb() and 0xFF) / 255f
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b)); val d = mx - mn
        var h = when {
            d == 0f -> 0f
            mx == r -> 60f * (((g - b) / d) % 6f)
            mx == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        if (h < 0) h += 360f
        val l = (mx + mn) / 2f
        val s = if (d == 0f) 0f else d / (1f - kotlin.math.abs(2f * l - 1f))
        return hsl(h + degrees, s.coerceIn(0f, 1f), l, a)
    }

    fun hsl(h: Float, s: Float, l: Float, a: Float = 1f): Color = hslToColor(h, s, l, a)

    /** ARGB -> (h, s, l, a) */
    private fun hslOf(c: Color): List<Float> {
        val argb = c.toArgb()
        val a = (argb ushr 24) / 255f
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b)); val d = mx - mn
        var h = when {
            d == 0f -> 0f
            mx == r -> 60f * (((g - b) / d) % 6f)
            mx == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        if (h < 0) h += 360f
        val l = (mx + mn) / 2f
        val s = if (d == 0f) 0f else d / (1f - kotlin.math.abs(2f * l - 1f))
        return listOf(h, s.coerceIn(0f, 1f), l, a)
    }

    private fun hslToColor(h: Float, s: Float, l: Float, alpha: Float): Color {
        var hh = h % 360f
        if (hh < 0) hh += 360f
        val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
        val x = c * (1f - kotlin.math.abs((hh / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r1, g1, b1) = when {
            hh < 60f -> Triple(c, x, 0f)
            hh < 120f -> Triple(x, c, 0f)
            hh < 180f -> Triple(0f, c, x)
            hh < 240f -> Triple(0f, x, c)
            hh < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color(
            red = (r1 + m).coerceIn(0f, 1f),
            green = (g1 + m).coerceIn(0f, 1f),
            blue = (b1 + m).coerceIn(0f, 1f),
            alpha = alpha,
        )
    }
}
