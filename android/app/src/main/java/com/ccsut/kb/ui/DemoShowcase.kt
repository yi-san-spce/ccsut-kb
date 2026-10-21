package com.ccsut.kb.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * 功能演示: 每个编辑技巧拆成一步 (数据驱动, 可单独弹出也可整组连续播放)。
 * 首次删除/修改/添加卡片时弹出对应步骤, 进表后整组放一次; 已看过的不再打扰。
 */
data class DemoStep(
    val id: String,
    val title: String,
    val body: String,
    val art: @Composable () -> Unit,
)

object DemoSteps {

    val OVERVIEW = DemoStep(
        id = "edit_overview",
        title = "课表卡片可以随手改",
        body = "点一下课程卡片看详情、改信息或删除；点空白格加课。改动只保存在你的手机上，随时能撤回。",
        art = { ArtOverview() },
    )

    val UNDO = DemoStep(
        id = "edit_undo",
        title = "改错了？右上角能回退",
        body = "「更多选项」左边会出现一个回退按钮：点一下回退一步，按住不动、震动一下就还原全部。它只停留一会儿，有新改动才会再出现。",
        art = { ArtUndo() },
    )

    val GHOST = DemoStep(
        id = "edit_ghost",
        title = "删掉的课会留下虚影",
        body = "删除的卡片会在原位置变成灰色虚影，点一下虚影就恢复这门课。两分钟后虚影自己消失，之后可以在「更多 → 重置修改」里找回。",
        art = { ArtGhost() },
    )

    val DOT = DemoStep(
        id = "edit_dot",
        title = "改过的卡片带小圆点",
        body = "改过信息的卡片右上角有个小圆点；如果改的是时间或周次，原位置还会留一张「已移动」的虚影，新位置出现带点的卡片。",
        art = { ArtDot() },
    )

    val all = listOf(OVERVIEW, UNDO, GHOST, DOT)

    fun byId(id: String): DemoStep? = all.firstOrNull { it.id == id }

    /** 触发场景 → 应展示的步骤 (过滤已看过的); 返回空列表表示无需打扰 */
    fun forTrigger(trigger: String, seen: Set<String>): List<DemoStep> = when (trigger) {
        "delete" -> listOf(GHOST, UNDO)
        "modify" -> listOf(UNDO, DOT)
        "add" -> listOf(UNDO)
        else -> emptyList()
    }.filter { it.id !in seen }
}

/** 演示弹层: 底部弹层里横向翻页, 每页一步 (迷你示意图 + 一句人话) */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DemoShowcaseSheet(steps: List<DemoStep>, onDismiss: () -> Unit) {
    if (steps.isEmpty()) return
    val pager = rememberPagerState { steps.size }
    val scope = rememberCoroutineScope()
    val page = pager.currentPage
    val last = page == steps.lastIndex

    KbSheet(onDismiss = onDismiss, title = if (steps.size == 1) "小提示" else "功能演示") {
        // 标题与演示画布之间留出呼吸高度
        Spacer(Modifier.height(10.dp))
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxWidth(),
        ) { i ->
            val s = steps[i]
            Column(
                // 固定页高: 各步正文行数不同也不让弹层高度跳变
                Modifier.fillMaxWidth().padding(horizontal = 6.dp).heightIn(min = 264.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                s.art()
                Spacer(Modifier.height(18.dp))
                Text(
                    s.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    s.body,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    minLines = 3,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
                Spacer(Modifier.height(4.dp))
            }
        }
        // 圆点指示器 (多步时)
        if (steps.size > 1) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                steps.indices.forEach { i ->
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (i == page) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == page) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                            ),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) { Text("跳过") }
            Button(onClick = {
                if (last) onDismiss() else scope.launch { pager.animateScrollToPage(page + 1) }
            }) {
                Text(if (last) "知道了" else "下一步")
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

// ---- 迷你示意图 (纯 Compose 绘制, 与 App 内真实样式同源) ----

/** 演示画布: 圆角浅底, 统一高度 (内容高 = 132 - 上下内边距 28 = 104dp, 容得下卡片+说明文字) */
@Composable
private fun DemoCanvas(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(cs.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content,
    )
}

/** 虚影统一灰 (与课表内虚影同源) */
private fun ghostFill(cs: androidx.compose.material3.ColorScheme) = cs.onSurfaceVariant.copy(alpha = 0.14f)
private fun ghostLine(cs: androidx.compose.material3.ColorScheme) = cs.onSurfaceVariant.copy(alpha = 0.85f)

/** 迷你课程卡 (真实卡片缩小版) */
@Composable
private fun MiniCard(name: String, room: String, container: Color, on: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .width(86.dp)
            .height(72.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(container)
            .padding(7.dp),
    ) {
        Text(name, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = on, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(room, fontSize = 9.sp, color = on.copy(alpha = 0.85f), maxLines = 1)
    }
}

/** 步骤 1: 一格课 + 空白加课格 */
@Composable
private fun ArtOverview() {
    val cs = MaterialTheme.colorScheme
    DemoCanvas {
        MiniCard("高等数学", "7-南206", cs.primaryContainer, cs.onPrimaryContainer)
        Spacer(Modifier.width(10.dp))
        Column(
            Modifier
                .width(86.dp)
                .height(72.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(cs.background.copy(alpha = 0.6f))
                .border(1.dp, cs.outline.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Add, null, tint = cs.outline, modifier = Modifier.size(18.dp))
            Text("空白格加课", fontSize = 9.sp, color = cs.outline)
        }
        Spacer(Modifier.width(10.dp))
        MiniCard("大学英语", "9-102", cs.secondaryContainer, cs.onSecondaryContainer)
    }
}

/** 步骤 2: 顶栏右上角的回退按钮 + 更多按钮 (不加指向箭头, 避免误读为「更多」变成「回退」) */
@Composable
private fun ArtUndo() {
    val cs = MaterialTheme.colorScheme
    DemoCanvas {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(cs.onSurfaceVariant.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Undo, null,
                        tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(cs.onSurfaceVariant.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Menu, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("左边新增的是回退按钮 · 点按回退一步 / 按住还原全部", fontSize = 11.sp, color = cs.onSurfaceVariant)
        }
    }
}

/** 步骤 3: 灰色虚线虚影 (课名+教室齐全) + 右下角「已删除」角标 */
@Composable
private fun ArtGhost() {
    val cs = MaterialTheme.colorScheme
    DemoCanvas {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .width(120.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ghostFill(cs))
                    .border(
                        1.dp,
                        ghostLine(cs),
                        RoundedCornerShape(10.dp),
                    )
                    .padding(7.dp),
            ) {
                Column(Modifier.align(Alignment.CenterStart)) {
                    Text("高等数学", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = cs.onSurfaceVariant, maxLines = 1)
                    Spacer(Modifier.height(2.dp))
                    Text("7-南206", fontSize = 9.sp, color = cs.onSurfaceVariant.copy(alpha = 0.85f), maxLines = 1)
                }
                Text(
                    "已删除",
                    fontSize = 8.sp,
                    color = cs.background,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(cs.onSurfaceVariant.copy(alpha = 0.9f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 3.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("点虚影恢复 · 2 分钟后消失", fontSize = 11.sp, color = cs.onSurfaceVariant)
        }
    }
}

/** 步骤 4: 原位置「已移动」虚影 → 带小圆点的新位置卡片 */
@Composable
private fun ArtDot() {
    val cs = MaterialTheme.colorScheme
    DemoCanvas {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 原位置: 灰色虚影
                Box(
                    Modifier
                        .width(86.dp)
                        .height(72.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ghostFill(cs))
                        .border(1.dp, ghostLine(cs), RoundedCornerShape(10.dp))
                        .padding(7.dp),
                ) {
                    Column(Modifier.align(Alignment.CenterStart)) {
                        Text("复变函数", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = cs.onSurfaceVariant, maxLines = 1)
                        Spacer(Modifier.height(2.dp))
                        Text("7-南308", fontSize = 9.sp, color = cs.onSurfaceVariant.copy(alpha = 0.85f), maxLines = 1)
                    }
                    Text(
                        "已移动",
                        fontSize = 8.sp,
                        color = cs.background,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(cs.onSurfaceVariant.copy(alpha = 0.9f), RoundedCornerShape(5.dp))
                            .padding(horizontal = 3.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Text("→", fontSize = 12.sp, color = cs.outline)
                Spacer(Modifier.width(14.dp))
                // 新位置: 带小圆点的正常卡片
                Box {
                    MiniCard("复变函数", "7-南308", cs.tertiaryContainer, cs.onTertiaryContainer)
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(5.dp)
                            .background(cs.onTertiaryContainer, CircleShape),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("原位置留虚影 · 新卡片带小圆点", fontSize = 11.sp, color = cs.onSurfaceVariant)
        }
    }
}
