package com.ccsut.kb.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 顶部光泽渐变: 白 → 明显透白 (0.68), 玻璃字的"高光面"对比更强 */
private val GlossBrush = Brush.verticalGradient(
    listOf(Color.White, Color.White.copy(alpha = 0.68f)),
)

/**
 * 玻璃字: 100% 纯文字档 (无底色可依托) 专用的伪玻璃文字效果。
 * 思路同 Apple vibrant text —— 中文不走字体文件路线 (显示字体动辄 5-10MB),
 * 艺术感全在渲染层, 四层同约束 Text 叠放:
 *   1. 紧贴暗影: 字形边缘的清晰定义 (blur 1.5dp, 杂底上先勾出轮廓)
 *   2. 大柔影: 杂乱背景图上的整体可读性保险
 *   3. 中层细描边: 浅色背景下的轮廓加强 (深色底靠柔影, 浅色底靠描边)
 *   4. 顶层白色光泽渐变填充: 顶部高光的玻璃质感
 * [emphasis] 缩放描边/阴影强度, 内部钳制下限 0.75 —— 副行等小字也不至于失效。
 * 真玻璃字 (背景按字形蒙版透出) 留作后续: 需 Offscreen+BlendMode+RenderEffect, 可读性随背景走。
 */
@Composable
fun GlassText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 13.sp,
    fontWeight: FontWeight? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    emphasis: Float = 1f,
    maxLines: Int = 1,
) {
    val e = emphasis.coerceIn(0.75f, 1f)
    val density = LocalDensity.current
    val tightBlur = with(density) { 1.5.dp.toPx() }
    val softBlur = with(density) { (8f * e).dp.toPx() }
    val softDy = with(density) { (2.5f * e).dp.toPx() }
    val strokeW = with(density) { (1.5f * e).dp.toPx() }

    fun baseStyle(color: Color) = TextStyle(
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        lineHeight = lineHeight,
    )

    Box(modifier) {
        // 底层 1: 紧贴暗影 —— 字形边缘清晰定义
        Text(
            text,
            maxLines = maxLines, overflow = TextOverflow.Ellipsis,
            style = baseStyle(Color.Transparent).copy(
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.30f * e),
                    offset = Offset(0f, with(density) { 0.5.dp.toPx() }),
                    blurRadius = tightBlur,
                ),
            ),
        )
        // 底层 2: 大柔影 —— 整体可读性保险
        Text(
            text,
            maxLines = maxLines, overflow = TextOverflow.Ellipsis,
            style = baseStyle(Color.Transparent).copy(
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.58f * e),
                    offset = Offset(0f, softDy),
                    blurRadius = softBlur,
                ),
            ),
        )
        // 中层: 细描边轮廓
        Text(
            text,
            maxLines = maxLines, overflow = TextOverflow.Ellipsis,
            style = baseStyle(Color.Black.copy(alpha = 0.42f * e)).copy(
                drawStyle = Stroke(width = strokeW, join = StrokeJoin.Round),
            ),
        )
        // 顶层: 白色光泽渐变填充
        Text(
            text,
            maxLines = maxLines, overflow = TextOverflow.Ellipsis,
            style = baseStyle(Color.Unspecified).copy(brush = GlossBrush),
        )
    }
}
