package com.ccsut.kb.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * 主题风格包 (v2.10.0, v2.10.1 收编为"品牌"档): 每套固定气质的光/暗双配色,
 * 选中后接管全局 ColorScheme, 永不随壁纸/背景变化。课程块、玻璃顶栏、背景
 * 蒙层均从 scheme/色板派生, 换包即全局生效; 全局圆角基线 (M3 Shapes) 不随主题变化。
 *
 * 机械 token (surfaceContainer 族/inverse 族/scrim 等) 由 packScheme 从核心色
 * 推导, 只需为每套主题手写 20 个核心色值; 课程块用与主题同族气质的精选色板
 * (blocksLight/blocksDark), 代替全局的色相散射, 保证整版色调协调。
 * 注意: 所有色值一律写 8 位 0xFFxxxxxx —— Color(Long) 按 0xAARRGGBB 解析。
 */
data class ThemePack(
    val id: Int,
    val title: String,
    val tagline: String,
    val light: ColorScheme,
    val dark: ColorScheme,
    /** 主题内课程块精选色板 (容器色, 8 位 ARGB), 空表 = 走全局自动配色 */
    val blocksLight: List<Long> = emptyList(),
    val blocksDark: List<Long> = emptyList(),
)

object ThemePacks {

    /** 预览色卡: 取该主题浅色 scheme 的关键色, 供选择器画圆点 */
    fun previewOf(p: ThemePack): List<Color> = listOf(
        p.light.background,
        p.light.primary,
        p.light.primaryContainer,
        p.light.secondaryContainer,
        p.light.tertiaryContainer,
    )

    fun byId(id: Int): ThemePack? = ALL.find { it.id == id }

    /** 主题课程块容器色: 按课程在排序表中的序号取色 (on 色由 CourseColors 从同一色相派生) */
    fun blockContainer(p: ThemePack, idx: Int, dark: Boolean): Color {
        val list = if (dark) p.blocksDark else p.blocksLight
        return Color(list[idx % list.size].toArgbInt())
    }

    private fun Long.toArgbInt(): Int = (this or 0xFF000000L).toInt()

    // ---------------- 1. 卡带未来主义: 80 年代模拟电脑, 米色面板 + 琥珀荧光屏 ----------------
    private val cassette = ThemePack(
        id = 1,
        title = "卡带未来主义",
        tagline = "琥珀荧光屏 · 复古模拟电脑",
        light = packScheme(
            dark = false,
            primary = 0x8A5A00, onPrimary = 0xFFFFFF, primaryContainer = 0xFFD79E, onPrimaryContainer = 0x2B1700,
            secondary = 0x6C5C3F, onSecondary = 0xFFFFFF, secondaryContainer = 0xF5E0BA, onSecondaryContainer = 0x241A04,
            tertiary = 0x4D6545, onTertiary = 0xFFFFFF, tertiaryContainer = 0xCFE3C4, onTertiaryContainer = 0x0C2008,
            background = 0xF2EBDC, onBackground = 0x352B1C,
            surfaceVariant = 0xE7DEC9, onSurfaceVariant = 0x6B5D45,
            outline = 0x84755A, outlineVariant = 0xD5C9AC,
        ),
        dark = packScheme(
            dark = true,
            primary = 0xFFB000, onPrimary = 0x271800, primaryContainer = 0x5F4200, onPrimaryContainer = 0xFFDDB0,
            secondary = 0xDCC49A, onSecondary = 0x3A2E15, secondaryContainer = 0x52432A, onSecondaryContainer = 0xF9E0BC,
            tertiary = 0xA6C995, onTertiary = 0x1C300F, tertiaryContainer = 0x32491F, onTertiaryContainer = 0xC2E3B0,
            background = 0x171310, onBackground = 0xEBE0CB,
            surfaceVariant = 0x4A4232, onSurfaceVariant = 0xCEBFA0,
            outline = 0x97886C, outlineVariant = 0x4A4232,
        ),
        // 琥珀/赤陶/橄榄/雾蓝: 同一暖调荧光屏气质, 拒绝彩虹散射
        blocksLight = listOf(
            0xFFF2D9A4, 0xFFE9C9B2, 0xFFD9D6A9, 0xFFC9D6B8,
            0xFFBBD3CD, 0xFFE3D3B9, 0xFFDCC0AC, 0xFFCBC8B0,
        ),
        blocksDark = listOf(
            0xFF8A6420, 0xFF8C4A36, 0xFF6E6A34, 0xFF55704A,
            0xFF3C6A62, 0xFF7A6848, 0xFF7E4A38, 0xFF6A5E40,
        ),
    )

    // ---------------- 2. 极简主义: 近单色石墨灰阶, 克制留白 ----------------
    private val minimal = ThemePack(
        id = 2,
        title = "极简主义",
        tagline = "石墨灰阶 · 克制留白",
        light = packScheme(
            dark = false,
            primary = 0x242429, onPrimary = 0xFFFFFF, primaryContainer = 0xE8E8EA, onPrimaryContainer = 0x1A1A1C,
            secondary = 0x57575E, onSecondary = 0xFFFFFF, secondaryContainer = 0xE3E3E6, onSecondaryContainer = 0x1D1D20,
            tertiary = 0x6E6E76, onTertiary = 0xFFFFFF, tertiaryContainer = 0xE9E9EB, onTertiaryContainer = 0x1F1F22,
            background = 0xFFFFFF, onBackground = 0x1A1A1A,
            surfaceVariant = 0xF0F0F1, onSurfaceVariant = 0x6E6E73,
            outline = 0xB4B4BA, outlineVariant = 0xE4E4E7,
        ),
        dark = packScheme(
            dark = true,
            primary = 0xC9C9CE, onPrimary = 0x1C1C1F, primaryContainer = 0x34343A, onPrimaryContainer = 0xE2E2E6,
            secondary = 0xAFAFB6, onSecondary = 0x1E1E22, secondaryContainer = 0x2E2E33, onSecondaryContainer = 0xE6E6EB,
            tertiary = 0x9D9DA4, onTertiary = 0x1F1F24, tertiaryContainer = 0x303036, onTertiaryContainer = 0xE4E4E9,
            background = 0x0A0A0B, onBackground = 0xE8E8EA,
            surfaceVariant = 0x262629, onSurfaceVariant = 0xA9A9B0,
            outline = 0x55555C, outlineVariant = 0x2A2A2E,
        ),
        // 墨阶: 冷暖灰温微差代替彩色, 克制但并排可辨
        blocksLight = listOf(
            0xFFE9E9EB, 0xFFE3E6E9, 0xFFEAE6DF, 0xFFE2E7E1,
            0xFFE7E4EB, 0xFFEDEDED, 0xFFE0E4E6, 0xFFEAE2DA,
        ),
        blocksDark = listOf(
            0xFF2E2E32, 0xFF2D3136, 0xFF35312B, 0xFF2E3530,
            0xFF332E36, 0xFF2A2A2C, 0xFF2C3235, 0xFF363029,
        ),
    )

    // ---------------- 3. 瑞士国际主义: 纯黑白 + 单一瑞士红, 网格与粗排版 ----------------
    private val swiss = ThemePack(
        id = 3,
        title = "瑞士国际主义",
        tagline = "黑白红 · 网格与粗排版",
        light = packScheme(
            dark = false,
            primary = 0xE30613, onPrimary = 0xFFFFFF, primaryContainer = 0xFFDAD6, onPrimaryContainer = 0x410002,
            secondary = 0x2A2A2A, onSecondary = 0xFFFFFF, secondaryContainer = 0xE4E4E4, onSecondaryContainer = 0x191919,
            tertiary = 0x1C1C1C, onTertiary = 0xFFFFFF, tertiaryContainer = 0xDEDEDE, onTertiaryContainer = 0x161616,
            background = 0xFFFFFF, onBackground = 0x111111,
            surfaceVariant = 0xEFEFEF, onSurfaceVariant = 0x616161,
            outline = 0x8A8A8A, outlineVariant = 0xDEDEDE,
        ),
        dark = packScheme(
            dark = true,
            primary = 0xFF5449, onPrimary = 0x2B0000, primaryContainer = 0x93000A, onPrimaryContainer = 0xFFDAD6,
            secondary = 0xC9C9C9, onSecondary = 0x1E1E1E, secondaryContainer = 0x333333, onSecondaryContainer = 0xE0E0E0,
            tertiary = 0xBFBFBF, onTertiary = 0x1F1F1F, tertiaryContainer = 0x303030, onTertiaryContainer = 0xDEDEDE,
            background = 0x0D0D0D, onBackground = 0xF2F2F2,
            surfaceVariant = 0x282828, onSurfaceVariant = 0xB3B3B3,
            outline = 0x8A8A8A, outlineVariant = 0x333333,
        ),
        // 黑白灰 + 一点红: 红只做点缀, 主体交给排版与描边
        blocksLight = listOf(
            0xFFEFEFEF, 0xFFE8E8E8, 0xFFF4F4F4, 0xFFE4E4E4,
            0xFFF8DAD6, 0xFFECECEC, 0xFFF0E4E2, 0xFFEAEAEA,
        ),
        blocksDark = listOf(
            0xFF2A2A2A, 0xFF303030, 0xFF262626, 0xFF333333,
            0xFF6E1A16, 0xFF2C2C2C, 0xFF58201C, 0xFF2E2E2E,
        ),
    )

    // ---------------- 4. 学院风: 羊皮纸 + 黄铜 + 绯红的书卷气 ----------------
    private val academia = ThemePack(
        id = 4,
        title = "学院风",
        tagline = "羊皮纸 · 黄铜与绯红书卷气",
        light = packScheme(
            dark = false,
            primary = 0x7C1F2D, onPrimary = 0xFFFFFF, primaryContainer = 0xF5D3D5, onPrimaryContainer = 0x35060C,
            secondary = 0x7A5C33, onSecondary = 0xFFFFFF, secondaryContainer = 0xEBD6A8, onSecondaryContainer = 0x261A05,
            tertiary = 0x4E5D43, onTertiary = 0xFFFFFF, tertiaryContainer = 0xD5E0C6, onTertiaryContainer = 0x141B0D,
            background = 0xF1E8D8, onBackground = 0x3A2E22,
            surfaceVariant = 0xE3D6BE, onSurfaceVariant = 0x77664E,
            outline = 0x98876C, outlineVariant = 0xD8CBB0,
        ),
        dark = packScheme(
            dark = true,
            primary = 0xC9A962, onPrimary = 0x33260B, primaryContainer = 0x6E5522, onPrimaryContainer = 0xFFE8B8,
            secondary = 0xC9B394, onSecondary = 0x3B2E1D, secondaryContainer = 0x55432C, onSecondaryContainer = 0xF0DDBE,
            tertiary = 0xA8B894, onTertiary = 0x1E2814, tertiaryContainer = 0x3A4730, onTertiaryContainer = 0xC9D9B4,
            background = 0x1C1714, onBackground = 0xEFE3D3,
            surfaceVariant = 0x463C30, onSurfaceVariant = 0xCDBBA2,
            outline = 0xA18F73, outlineVariant = 0x463C30,
        ),
        // 黄铜/绯红/常春藤/乌木: 书架上的旧书脊
        blocksLight = listOf(
            0xFFE9D6B4, 0xFFEACCD0, 0xFFD8DFC4, 0xFFE0D2E0,
            0xFFE4D8BE, 0xFFD9DDD3, 0xFFE6CBB8, 0xFFD8D0BC,
        ),
        blocksDark = listOf(
            0xFF6E5522, 0xFF76323C, 0xFF46543A, 0xFF54425A,
            0xFF6A5C40, 0xFF3E4C42, 0xFF704838, 0xFF54503E,
        ),
    )

    // ---------------- 5. 赛博朋克: 深空夜城 + 霓虹青/品红/荧光绿 ----------------
    private val cyberpunk = ThemePack(
        id = 5,
        title = "赛博朋克",
        tagline = "霓虹赛博 · 深空夜城",
        light = packScheme(
            dark = false,
            primary = 0x0E7490, onPrimary = 0xFFFFFF, primaryContainer = 0xC5F0FA, onPrimaryContainer = 0x00323E,
            secondary = 0xA62183, onSecondary = 0xFFFFFF, secondaryContainer = 0xF9D4F0, onSecondaryContainer = 0x3A0A30,
            tertiary = 0x4A7222, onTertiary = 0xFFFFFF, tertiaryContainer = 0xD8F0B8, onTertiaryContainer = 0x142600,
            background = 0xE9EDF2, onBackground = 0x14181D,
            surfaceVariant = 0xDEE4EA, onSurfaceVariant = 0x5A6570,
            outline = 0x6E7A88, outlineVariant = 0xD0D8DF,
        ),
        dark = packScheme(
            dark = true,
            primary = 0x00D4FF, onPrimary = 0x00202A, primaryContainer = 0x00708A, onPrimaryContainer = 0xC5F4FF,
            secondary = 0xF45CE8, onSecondary = 0x3A0038, secondaryContainer = 0x8E1C86, onSecondaryContainer = 0xFFD7F8,
            tertiary = 0x5CE87E, onTertiary = 0x00390F, tertiaryContainer = 0x1F6B2A, onTertiaryContainer = 0xC4F9CB,
            background = 0x0A0A0F, onBackground = 0xE2E4EA,
            surfaceVariant = 0x23232E, onSurfaceVariant = 0xA7A9B8,
            outline = 0x8B8DA0, outlineVariant = 0x2C2C38,
        ),
        // 霓虹管: 青/品红/绿/紫的深管身, 浅色是同族电光粉彩
        blocksLight = listOf(
            0xFFC5F0FA, 0xFFF6D2F2, 0xFFD8F0BC, 0xFFDCD8F6,
            0xFFF6E8C0, 0xFFC2EDE4, 0xFFF8DAD0, 0xFFD4E4F8,
        ),
        blocksDark = listOf(
            0xFF00708A, 0xFF8E1C86, 0xFF1F6B2A, 0xFF4A2492,
            0xFF8A6A14, 0xFF0E5A5E, 0xFF7E3020, 0xFF1C4E8A,
        ),
    )

    val ALL: List<ThemePack> = listOf(cassette, minimal, swiss, academia, cyberpunk)

    /**
     * 由 20 个核心色值补全整套 M3 ColorScheme。
     * surface 与 background 同色 (扁平基调), surfaceContainer 族 / inverse 族按
     * 明度阶梯推导 —— 浅色向黑加深做卡片层次, 深色向白提亮, 与 M3 tone 梯度同向。
     */
    private fun packScheme(
        dark: Boolean,
        primary: Long, onPrimary: Long, primaryContainer: Long, onPrimaryContainer: Long,
        secondary: Long, onSecondary: Long, secondaryContainer: Long, onSecondaryContainer: Long,
        tertiary: Long, onTertiary: Long, tertiaryContainer: Long, onTertiaryContainer: Long,
        background: Long, onBackground: Long,
        surfaceVariant: Long, onSurfaceVariant: Long,
        outline: Long, outlineVariant: Long,
    ): ColorScheme {
        val bg = Color(background.toArgbInt())
        val onBg = Color(onBackground.toArgbInt())
        val pri = Color(primary.toArgbInt())

        val lowest: Color; val low: Color; val container: Color; val high: Color
        val highest: Color; val bright: Color; val dim: Color
        if (!dark) {
            lowest = lerp(bg, Color.White, 0.55f)
            low = lerp(bg, Color.Black, 0.03f)
            container = lerp(bg, Color.Black, 0.05f)
            high = lerp(bg, Color.Black, 0.08f)
            highest = lerp(bg, Color.Black, 0.12f)
            bright = lerp(bg, Color.White, 0.35f)
            dim = lerp(bg, Color.Black, 0.04f)
        } else {
            lowest = lerp(bg, Color.Black, 0.35f)
            low = lerp(bg, Color.White, 0.05f)
            container = lerp(bg, Color.White, 0.09f)
            high = lerp(bg, Color.White, 0.14f)
            highest = lerp(bg, Color.White, 0.20f)
            bright = lerp(bg, Color.White, 0.26f)
            dim = lerp(bg, Color.Black, 0.25f)
        }
        // inverse 族: 反色表面用于 Tooltip/Snackbar, 取对面明度端
        val inverseSurface = if (!dark) lerp(Color.Black, onBg, 0.15f) else lerp(Color.White, onBg, 0.15f)
        val inversePrimary = if (!dark) lerp(pri, Color.White, 0.55f) else lerp(pri, Color.Black, 0.50f)

        return if (!dark) lightColorScheme(
            primary = pri, onPrimary = Color(onPrimary.toArgbInt()),
            primaryContainer = Color(primaryContainer.toArgbInt()), onPrimaryContainer = Color(onPrimaryContainer.toArgbInt()),
            inversePrimary = inversePrimary,
            secondary = Color(secondary.toArgbInt()), onSecondary = Color(onSecondary.toArgbInt()),
            secondaryContainer = Color(secondaryContainer.toArgbInt()), onSecondaryContainer = Color(onSecondaryContainer.toArgbInt()),
            tertiary = Color(tertiary.toArgbInt()), onTertiary = Color(onTertiary.toArgbInt()),
            tertiaryContainer = Color(tertiaryContainer.toArgbInt()), onTertiaryContainer = Color(onTertiaryContainer.toArgbInt()),
            background = bg, onBackground = onBg,
            surface = bg, onSurface = onBg,
            surfaceVariant = Color(surfaceVariant.toArgbInt()), onSurfaceVariant = Color(onSurfaceVariant.toArgbInt()),
            surfaceTint = pri,
            inverseSurface = inverseSurface, inverseOnSurface = bg,
            outline = Color(outline.toArgbInt()), outlineVariant = Color(outlineVariant.toArgbInt()),
            surfaceBright = bright, surfaceDim = dim,
            surfaceContainer = container, surfaceContainerHigh = high, surfaceContainerHighest = highest,
            surfaceContainerLow = low, surfaceContainerLowest = lowest,
        ) else darkColorScheme(
            primary = pri, onPrimary = Color(onPrimary.toArgbInt()),
            primaryContainer = Color(primaryContainer.toArgbInt()), onPrimaryContainer = Color(onPrimaryContainer.toArgbInt()),
            inversePrimary = inversePrimary,
            secondary = Color(secondary.toArgbInt()), onSecondary = Color(onSecondary.toArgbInt()),
            secondaryContainer = Color(secondaryContainer.toArgbInt()), onSecondaryContainer = Color(onSecondaryContainer.toArgbInt()),
            tertiary = Color(tertiary.toArgbInt()), onTertiary = Color(onTertiary.toArgbInt()),
            tertiaryContainer = Color(tertiaryContainer.toArgbInt()), onTertiaryContainer = Color(onTertiaryContainer.toArgbInt()),
            background = bg, onBackground = onBg,
            surface = bg, onSurface = onBg,
            surfaceVariant = Color(surfaceVariant.toArgbInt()), onSurfaceVariant = Color(onSurfaceVariant.toArgbInt()),
            surfaceTint = pri,
            inverseSurface = inverseSurface, inverseOnSurface = bg,
            outline = Color(outline.toArgbInt()), outlineVariant = Color(outlineVariant.toArgbInt()),
            surfaceBright = bright, surfaceDim = dim,
            surfaceContainer = container, surfaceContainerHigh = high, surfaceContainerHighest = highest,
            surfaceContainerLow = low, surfaceContainerLowest = lowest,
        )
    }
}
