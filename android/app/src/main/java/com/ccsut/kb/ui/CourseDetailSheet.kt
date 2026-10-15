package com.ccsut.kb.ui
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ripple
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ccsut.kb.data.UserEdits
import com.ccsut.kb.util.Block
import com.ccsut.kb.util.Weeks
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailSheet(
    block: Block,
    className: String,
    customIdx: Int?,
    onPickColor: (Int?) -> Unit,
    onRevert: (() -> Unit)? = null, // 非空且是「已修改」课时, 按钮行多一个还原
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val course = block.course
    KbSheet(onDismiss = onDismiss, title = course.kc) {
        // verticalScroll 让嵌套滚动管线成立: 详情页任意位置下拉都能关弹层
        Column(
            Modifier
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            if (course.fx.isNotEmpty()) {
                Text(
                    "分项: ${course.fx}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            // 来源标注: 学校课被改过 (可还原) / 自建课程
            val editKind = UserEdits.editOf(course.editId)?.kind
            if (editKind == 1 || editKind == 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    if (editKind == 1) "已修改 · 点还原可恢复" else "自建课程",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            DetailRow(
                "时间",
                Weeks.cnDay(course.day) + " 第${block.startJc}-${block.startJc + block.span - 1}节",
            )
            DetailRow("教师", course.teacher.ifBlank { "待定" })
            DetailRow("教室", course.room.ifBlank { "—" })
            // 周次以 ranges 渲染为准: 拆分后的单周块/修改块 zc 文本可能停留在原始整学期文本
            DetailRow("周次", if (course.ranges.isNotEmpty()) UserEdits.zcText(course.ranges) else course.zc.ifBlank { "—" })
            if (course.jxb.isNotEmpty()) DetailRow("教学班", course.jxb)
            if (course.jxbzc.isNotEmpty()) DetailRow("合班", course.jxbzc)
            if (className.isNotEmpty()) DetailRow("查询班级", className)

            // ---- 自定义颜色 ----
            Spacer(Modifier.height(14.dp))
            Text("颜色", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            val dark = CourseColors.isDark(MaterialTheme.colorScheme)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                item {
                    // 外圈 42dp 触控区包住 30dp 视觉圆
                    val selected = customIdx == null
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable(onClickLabel = "恢复自动配色") { onPickColor(null) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                .border(
                                    if (selected) 2.dp else 0.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("自动", fontSize = 9.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
                items(count = CourseColors.CUSTOM_N) { i ->
                    val selected = customIdx == i
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable(onClickLabel = "使用颜色${i + 1}") { onPickColor(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .background(CourseColors.customContainer(i, dark), CircleShape)
                                .border(
                                    if (selected) 2.dp else 0.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    CircleShape,
                                ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))

            // ---- 还原 / 编辑 / 删除 ----
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (onRevert != null && editKind == 1) {
                    OutlinedButton(onClick = onRevert, modifier = Modifier.weight(1f)) { Text("还原") }
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("删除") }
                Button(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("编辑") }
            }
        }
    }
}

// ==================== 课程编辑表单 (加课/改课共用) ====================

@Composable
internal fun FormLabel(text: String) {
    Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun FormChip(
    text: String,
    on: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    // 外圈触控垫: 视觉不变, 可点区域扩到 ~40dp; 全 App 的 chip 统一用这一个形态
    Box(
        modifier
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .padding(4.dp)
                .clip(CircleShape)
                .background(
                    when {
                        on && enabled -> MaterialTheme.colorScheme.primary
                        enabled -> MaterialTheme.colorScheme.surfaceContainerHigh
                        else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                    },
                )
                .border(
                    1.dp,
                    when {
                        on && enabled -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (enabled) 1f else 0.5f)
                    },
                    CircleShape,
                )
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Text(
                text,
                fontSize = 12.sp,
                fontWeight = if (on && enabled) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    on && enabled -> MaterialTheme.colorScheme.onPrimary
                    enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
            )
        }
    }
}
