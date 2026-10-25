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

/** 顶部光泽渐变: 白 → 微透白, 玻璃字的"高光面" */
private val GlossBrush = Brush.verticalGradient(
    listOf(Color.White, Color.White.copy(alpha = 0.86f)),
)

/**
 * 玻璃字: 100% 纯文字档 (无底色可依托) 专用的伪玻璃文字效果。
 * 思路同 Apple vibrant text —— 中文不走字体文件路线 (显示字体动辄 5-10MB),
 * 艺术感全在渲染层, 三层同约束 Text 叠放:
 *   1. 底层柔影: 杂乱背景图上的可读性保险
 *   2. 中层细描边: 浅色背景下的轮廓定义 (深色底靠柔影, 浅色底靠描边)
 *   3. 顶层白色光泽渐变填充: 顶部高光的玻璃质感
 * [emphasis] 缩放描边/阴影强度, 副行等小字给 0.6-0.8 弱化。
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
    val density = LocalDensity.current
    val shadowBlur = with(density) { (5.5f * emphasis).dp.toPx() }
    val shadowDy = with(density) { (1.5f * emphasis).dp.toPx() }
    val strokeW = with(density) { (1.2f * emphasis).dp.toPx() }

    fun baseStyle(color: Color) = TextStyle(
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        lineHeight = lineHeight,
    )

    Box(modifier) {
        // 底层: 柔影 (Shadow 的 offset/blurRadius 按 px 计)
        Text(
            text,
            maxLines = maxLines, overflow = TextOverflow.Ellipsis,
            style = baseStyle(Color.Transparent).copy(
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.45f * emphasis),
                    offset = Offset(0f, shadowDy),
                    blurRadius = shadowBlur,
                ),
            ),
        )
        // 中层: 细描边轮廓
        Text(
            text,
            maxLines = maxLines, overflow = TextOverflow.Ellipsis,
            style = baseStyle(Color.Black.copy(alpha = 0.26f * emphasis)).copy(
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
