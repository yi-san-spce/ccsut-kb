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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import kotlin.math.max
import kotlin.math.min

/** 经典品牌色 (主题配色"品牌"档的默认蓝), 选择器预览要用, 开放 internal 访问 */
val LightColors = lightColorScheme(
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

val DarkColors = darkColorScheme(
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
fun KbTheme(
    themeMode: Int = 0,
    colorSource: Int = 0,
    seed: Int = 0,
    styleTheme: Int = 0,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dark = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    // 主题风格包优先: 选中后接管全局配色, 永不随壁纸/背景变化 (id 失效时自然回落)
    val pack = if (styleTheme != 0) ThemePacks.byId(styleTheme) else null
    // 课程块配色也要跟主题走: 写入全局覆盖 (小组件等非组合场景读不到, 保持默认)
    CourseColors.packOverride = styleTheme
    val scheme: ColorScheme = when {
        pack != null -> if (dark) pack.dark else pack.light
        // 跟随背景图取色: 种子色缺失时退回壁纸配色
        colorSource == 1 -> if (seed != 0) {
            com.materialkolor.dynamicColorScheme(
                seedColor = Color(seed),
                isDark = dark,
                isAmoled = false,
                style = com.materialkolor.PaletteStyle.TonalSpot,
            )
        } else if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        // 固定品牌色
        colorSource == 2 -> if (dark) DarkColors else LightColors
        // 跟随系统壁纸 (默认)
        else -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    // 全局圆角基线: M3 默认组件(Button/Chip/Card/TextField 等)统一对齐; 显式传 shape 的以各处为准
    MaterialTheme(
        colorScheme = scheme,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(22.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}

/** 课程块配色: 在动态取色的容器色基础上做色相旋转, 保证整版协调; 选中主题风格包时改用主题精选色板 */
object CourseColors {

    private const val N = 10

    /** 当前生效的主题风格包 id (KbTheme 组合时写入; 小组件等非组合场景为 0=默认动态配色) */
    @Volatile
    var packOverride: Int = 0

    /** 本学期全部课程种子的稳定排序 (主界面/小组件渲染前绑定同一份): 色板按序分配, 不同课程必不同色 */
    @Volatile
    private var seedOrder: List<String> = emptyList()

    fun bindOrder(seeds: Collection<String>) {
        val next = seeds.distinct().sorted()
        if (next != seedOrder) seedOrder = next
    }

    /** 课程稳定序号: 排序表命中用序号, 未命中 (极端时序) 退回哈希 */
    private fun stableIndexOf(seed: String): Int =
        seedOrder.indexOf(seed).takeIf { it >= 0 } ?: kotlin.math.abs(seed.hashCode())

    /** 自动配色的稳定序号 (开放给小组件): 12 色板按同一份课程排序分配, 与 App 侧同源不撞色 */
    fun autoIndexOf(seed: String): Int = stableIndexOf(seed)

    /** 主题色板取块色: 课程序号隔 3 位跨步取色 (相邻课程在色板上跳到最远色相),
     *  超出色板数时同色相做明度偏移续接, 依旧可辨 */
    private fun packBlock(pack: ThemePack, i: Int, dark: Boolean): Color {
        val list = if (dark) pack.blocksDark else pack.blocksLight
        val slot = i * 3
        var c = ThemePacks.blockContainer(pack, slot, dark)
        val cycle = slot / list.size
        if (cycle > 0) {
            val (h, s, l, a) = hslOf(c)
            c = hsl(h, s, if (dark) (l + 0.07f * cycle).coerceAtMost(0.60f) else (l - 0.08f * cycle).coerceAtLeast(0.58f), a)
        }
        return c
    }

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
        val i = stableIndexOf(seed)
        // 主题风格包生效: 用与主题气质同族的精选色板, 按课程排序分配保证互不相同
        val pack = if (packOverride != 0) ThemePacks.byId(packOverride) else null
        if (pack != null) {
            val list = if (dark) pack.blocksDark else pack.blocksLight
            if (list.isNotEmpty()) return packBlock(pack, i, dark)
        }
        val src = when (i % 3) {
            0 -> base.primaryContainer
            1 -> base.secondaryContainer
            else -> base.tertiaryContainer
        }
        // 低彩度容器色近灰阶: 色相旋转对灰色无效, 改按课程序号在全色轮取低饱和淡彩
        if (hslOf(src)[1] < 0.15f) {
            val h = (i * 137 % 360).toFloat()
            return hsl(h, 0.32f, if (dark) 0.40f else 0.86f)
        }
        val rotated = rotateHue(src, ((i % 8) - 3.5f) * 24f)
        val vivid = vivid(rotated, dark)
        // 浅色下发闷的兜底钳制
        if (!dark) {
            val (h, s, l, a) = hslOf(vivid)
            if (s < 0.25f) return hsl(h, 0.25f, l, a)
        }
        return vivid
    }

    /** 深色模式提鲜: 饱和度×1.35(≤0.65)、亮度+0.07(≤0.42), 摆脱动态取色容器色的灰闷感 */
    private fun vivid(c: Color, dark: Boolean): Color {
        if (!dark) return c
        val (h, s, l, a) = hslOf(c)
        return hsl(h, (s * 1.35f).coerceAtMost(0.65f), (l + 0.07f).coerceAtMost(0.42f), a)
    }

    private fun autoOnContainer(base: ColorScheme, seed: String): Color {
        val dark = isDark(base)
        val i = stableIndexOf(seed)
        // 主题风格包生效: on 色从同一精选块色的色相派生 (与自定义色板同款明度套路)
        val pack = if (packOverride != 0) ThemePacks.byId(packOverride) else null
        if (pack != null) {
            val list = if (dark) pack.blocksDark else pack.blocksLight
            if (list.isNotEmpty()) {
                val (h, s, _, a) = hslOf(packBlock(pack, i, dark))
                return if (dark) hsl(h, (s * 1.2f).coerceAtMost(0.6f), 0.90f, a)
                else hsl(h, (s * 1.6f).coerceAtMost(0.72f), 0.26f, a)
            }
        }
        val src = when (i % 3) {
            0 -> base.onPrimaryContainer
            1 -> base.onSecondaryContainer
            else -> base.onTertiaryContainer
        }
        return rotateHue(src, ((i % 8) - 3.5f) * 24f)
    }

    /** 当前配色方案是否为深色(按背景亮度判断, 不依赖系统主题) */
    fun isDark(base: ColorScheme): Boolean = base.background.luminance() < 0.5f

    /** 自定义色板容器色: 每档一个色相, 深/浅两套明度 (深色提饱和提亮, 鲜而不闷) */
    fun customContainer(idx: Int, dark: Boolean): Color =
        if (dark) hsl(idx * 30f, 0.48f, 0.36f) else hsl(idx * 30f, 0.62f, 0.86f)

    fun customOnContainer(idx: Int, dark: Boolean): Color =
        if (dark) hsl(idx * 30f, 0.55f, 0.90f) else hsl(idx * 30f, 0.72f, 0.28f)

    /** ARGB -> HSL 色相旋转 -> ARGB (HSL 计算统一走 [hslOf], 勿再复制内联实现) */
    fun rotateHue(c: Color, degrees: Float): Color {
        val (h, s, l, a) = hslOf(c)
        return hsl(h + degrees, s, l, a)
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
