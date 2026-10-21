package com.ccsut.kb.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlin.math.abs
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

    // 浅色外观下系统栏前景切深色: 透明状态栏压白底时浅色图标会不可读
    val view = LocalView.current
    SideEffect {
        if (!view.isInEditMode) {
            (view.context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
            }
        }
    }
}

/** 课程块配色: 在动态取色的容器色基础上做色相旋转, 保证整版协调; 选中主题风格包时改用主题精选色板 */
object CourseColors {

    private const val N = 10

    /** 当前生效的主题风格包 id (KbTheme 组合时写入; 小组件等非组合场景为 0=默认动态配色) */
    @Volatile
    var packOverride: Int = 0

    /** 课程种子的颜色分配表 (持久化注册表, 只增不减): 色板按序分配, 不同课程必不同色 */
    @Volatile
    private var seedOrder: List<String> = emptyList()

    /**
     * 注册表合并 (纯函数, 供单测): 既有分配原样保留 (删除课程不重排其余课程颜色,
     * 恢复课程回到原色), 新出现的课程追加到表尾。
     * 首次升级 (注册表为空): 以本次课程排序初始化, 与旧版「当前列表排序」的分配最接近。
     */
    internal fun mergeSeedOrder(persisted: List<String>, current: Collection<String>): List<String> {
        if (persisted.isEmpty()) return current.distinct().sorted()
        var next = persisted
        for (s in current.distinct()) if (s !in next) next = next + s
        return next
    }

    /** 绑定颜色分配表: 读持久化注册表, 新课程追加, 删除课程不回收槽位 */
    fun bindOrder(ctx: android.content.Context, seeds: Collection<String>) {
        val persisted = com.ccsut.kb.Prefs.courseSeedOrder(ctx)
        val next = mergeSeedOrder(persisted, seeds)
        if (next != persisted) com.ccsut.kb.Prefs.setCourseSeedOrder(ctx, next)
        if (next != seedOrder) seedOrder = next
    }

    /** 课程稳定序号: 排序表命中用序号, 未命中 (极端时序) 退回哈希 */
    private fun stableIndexOf(seed: String): Int =
        seedOrder.indexOf(seed).takeIf { it >= 0 } ?: abs(seed.hashCode())

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
        pair(base, seed, custom).second

    /** 容器色 + 文字色成对取色: 文字按容器最终亮度求对比, 一次算完避免两步各跑一遍 */
    fun pair(base: ColorScheme, seed: String, custom: Int? = null): Pair<Color, Color> {
        val dark = isDark(base)
        val c = custom?.let { customContainer(it, dark) } ?: autoContainer(base, seed)
        return c to onForContainer(c)
    }

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

    /** 当前配色方案是否为深色(按背景亮度判断, 不依赖系统主题) */
    fun isDark(base: ColorScheme): Boolean = base.background.luminance() < 0.5f

    /** 自定义色板容器色: 每档一个色相, 深/浅两套明度 (深色提饱和提亮, 鲜而不闷) */
    fun customContainer(idx: Int, dark: Boolean): Color =
        if (dark) hsl(idx * 30f, 0.48f, 0.36f) else hsl(idx * 30f, 0.62f, 0.86f)

    fun customOnContainer(idx: Int, dark: Boolean): Color =
        onForContainer(customContainer(idx, dark))

    /**
     * 按容器"最终亮度"取可读文字色: 亮容器配同相深字、深容器配同相亮字。
     * 容器经过 vivid/主题包色板后明度会漂移, 固定明度配对会出现白字压浅灰块这类失效组合;
     * 带对比度兜底: 不足 3:1 时向端点推明度。
     */
    fun onForContainer(container: Color, alpha: Float = 1f): Color {
        val (h, s, _, _) = hslOf(container)
        // 深/亮两端各推到极限后择优: 中明度容器 (如瑞士浅色的中灰) 两端都够不着 3:1, 取对比更高的一支
        fun push(dark: Boolean): Color {
            var on = if (dark) hsl(h, (s * 1.6f).coerceAtMost(0.72f), 0.24f)
            else hsl(h, (s * 1.2f).coerceAtMost(0.60f), 0.92f)
            var step = 0
            while (contrastRatio(on, container) < 3f && step++ < 6) {
                val (_, s2, l2, _) = hslOf(on)
                on = if (dark) hsl(h, s2, (l2 - 0.05f).coerceAtLeast(0.04f))
                else hsl(h, s2, (l2 + 0.05f).coerceAtMost(0.97f))
            }
            return on
        }
        val darkOn = push(true)
        val lightOn = push(false)
        val best = if (contrastRatio(darkOn, container) >= contrastRatio(lightOn, container)) darkOn else lightOn
        return best.copy(alpha = alpha)
    }

    /** WCAG 对比度 (1..21): (L亮+0.05)/(L暗+0.05) */
    fun contrastRatio(a: Color, b: Color): Float {
        val la = a.luminance() + 0.05f
        val lb = b.luminance() + 0.05f
        return if (la > lb) la / lb else lb / la
    }

    /** 空格子描边: outlineVariant 与背景合成后难分辨时 (纯白主题) 退用更深的 outline */
    fun gridStroke(base: ColorScheme): Color {
        val bg = base.background
        val composite = lerp(bg, base.outlineVariant, 0.60f)
        return if (abs(composite.luminance() - bg.luminance()) < 0.15f)
            base.outline.copy(alpha = 0.60f)
        else base.outlineVariant.copy(alpha = 0.60f)
    }

    /** 语义色: 成功/已是最新版本, 深浅两档 (UpdateScreen 用) */
    fun successColor(dark: Boolean): Color =
        if (dark) Color(0xFF86D99A) else Color(0xFF1E8E3E)

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
