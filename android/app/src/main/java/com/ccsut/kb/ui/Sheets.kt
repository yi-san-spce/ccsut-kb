package com.ccsut.kb.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.ccsut.kb.R
import com.ccsut.kb.data.BgStore
import com.ccsut.kb.data.BuildVersion
import com.ccsut.kb.data.Cls
import com.ccsut.kb.data.ClassCache
import com.ccsut.kb.data.Dataset
import com.ccsut.kb.data.Repo
import com.ccsut.kb.data.Updater
import com.ccsut.kb.data.UserEdits
import com.ccsut.kb.util.Block
import com.ccsut.kb.util.DebugLog
import com.ccsut.kb.util.Diag
import com.ccsut.kb.util.KbClock
import com.ccsut.kb.util.Weeks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ==================== 底部弹层统一封装 ====================

/**
 * 统一底部弹层 —— 自绘实现, 不用 Material3 的 ModalBottomSheet。
 * 原因有二:
 * 1) M3 sheet 自带的拖拽与内容 verticalScroll 抢事件, 造成滑动抽搐/不跟手
 *    (Google issuetracker 486562294, confirmValueChange 门控两轮实测仍会抽);
 * 2) 自绘必须自己给内容套 LocalContentColor —— M3 默认是纯黑, 不套的话
 *    深色模式下未显式指定颜色的文字全是黑底黑字。
 * 手势归我管, 全部走单一管线, 不存在打架:
 * - 内容没滚到顶: 滚动 100% 归内容;
 * - 滚到顶后继续下拉: 面板跟手滑出, 松手按「拉过 28% 或向下甩」判定关/弹回;
 * - 顶部把手整条可拖(详情页等无滚动内容的弹层靠它);
 * - 点遮罩 / 系统返回: 一律动画滑出。
 * 内容列请自带 verticalScroll + navigationBarsPadding (现有调用点均已如此)。
 */
@Composable
fun KbSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val panelColor = cs.surfaceContainerLow
    val scope = rememberCoroutineScope()

    var shown by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var panelH by remember { mutableFloatStateOf(0f) }   // 面板高 px, 布局后才有
    var dragY by remember { mutableFloatStateOf(0f) }    // 手势下拉量 px

    LaunchedEffect(Unit) { shown = true }
    // 0 = 展开, 1 = 完全滑出屏幕; 点遮罩/返回键触发的整板退场动画
    val p by animateFloatAsState(
        targetValue = if (shown) 0f else 1f,
        animationSpec = tween(220),
        label = "sheet",
        finishedListener = { if (it >= 1f) onDismiss() },
    )

    // 松手判定: 拉过面板 28% 或向下甩 → 关; 向上甩 → 回; 其余按距离
    fun settle(vy: Float) {
        if (closing || panelH <= 0f) return
        val close = when {
            vy > 900f -> true
            vy < -900f -> false
            else -> dragY > panelH * 0.28f
        }
        if (close) {
            closing = true
            scope.launch {
                animate(dragY, panelH, initialVelocity = vy, animationSpec = tween(200)) { v, _ -> dragY = v }
                onDismiss()
            }
        } else {
            scope.launch {
                animate(dragY, 0f, initialVelocity = vy, animationSpec = spring(stiffness = 1400f)) { v, _ ->
                    dragY = v.coerceAtLeast(0f)
                }
            }
        }
    }

    fun close() {
        if (closing) return
        if (dragY > 0f) settle(10_000f)   // 已被拖离: 从当前位置直接滑出
        else {
            closing = true
            shown = false
        }
    }

    // 内容滚动 leftover 的唯一接管者: 到顶后下拉驱动面板, 被拉下后上推先归位面板
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || dragY <= 0f || available.y >= 0f) return Offset.Zero
                val prev = dragY
                dragY = (dragY + available.y).coerceAtLeast(0f)
                return Offset(0f, dragY - prev)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0f || closing) return Offset.Zero
                val prev = dragY
                dragY = (dragY + available.y).coerceAtMost(if (panelH > 0f) panelH else Float.MAX_VALUE)
                return Offset(0f, dragY - prev)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dragY <= 0f || closing) return Velocity.Zero
                settle(available.y)
                return available
            }
        }
    }
    val grabDrag = rememberDraggableState { delta ->
        if (!closing) dragY = (dragY + delta).coerceAtLeast(0f).coerceAtMost(if (panelH > 0f) panelH else Float.MAX_VALUE)
    }
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

    Dialog(
        onDismissRequest = { close() },   // 系统返回走这里
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize()) {
            // 遮罩: 覆满全屏拦截对底层的点击, 只响应「点一下关闭」; 随面板滑出同步变淡
            val frac = if (panelH > 0f) ((p * panelH + dragY) / panelH).coerceIn(0f, 1f) else p
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.45f * (1f - frac) }
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { close() },
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.92f)
                    .imePadding()
                    .onSizeChanged { panelH = it.height.toFloat() }
                    .nestedScroll(connection)
                    .graphicsLayer { translationY = p * size.height + dragY }
                    .shadow(8.dp, shape)
                    .background(panelColor, shape),
            ) {
                // 顶部把手: 整条可拖, 无滚动内容的弹层(如课程详情)靠它下拉关闭
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .draggable(
                            state = grabDrag,
                            orientation = Orientation.Vertical,
                            onDragStopped = { vy -> settle(vy) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(40.dp, 4.dp)
                            .background(cs.outlineVariant.copy(alpha = 0.8f), RoundedCornerShape(2.dp)),
                    )
                }
                CompositionLocalProvider(LocalContentColor provides contentColorFor(panelColor)) {
                    content()
                }
            }
        }
    }
}

/** 更新弹窗: 居中卡片, 新版本号 + 更新内容列表 + 下载进度, 启动自动检查/手动检查共用 */
@Composable
fun UpdateDialog(
    newVersion: String,
    sizeBytes: Long?,
    notes: List<String>,
    downloading: Boolean,
    progress: Int?,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val cardColor = cs.surfaceContainerHigh
    Dialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 36.dp),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColorFor(cardColor)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 340.dp)
                        .background(cardColor, RoundedCornerShape(26.dp))
                        .padding(26.dp),
                ) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .background(cs.primary.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.SystemUpdateAlt,
                            contentDescription = null,
                            tint = cs.primary,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("发现新版本", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        buildString {
                            append("v$newVersion · 当前 v${BuildVersion.NAME}")
                            sizeBytes?.let { append(" · ${"%.1f".format(it / 1048576.0)} MB") }
                        },
                        fontSize = 13.sp,
                        color = cs.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(18.dp))
                    Text("更新内容", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = cs.primary)
                    Spacer(Modifier.height(6.dp))
                    Column(
                        Modifier
                            .heightIn(max = 230.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        if (notes.isEmpty()) {
                            Text(
                                "性能优化与问题修复。",
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        } else {
                            notes.forEach { line ->
                                Row(Modifier.padding(vertical = 4.dp)) {
                                    Box(
                                        Modifier
                                            .padding(top = 7.dp)
                                            .size(5.dp)
                                            .background(cs.primary, CircleShape),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(line, fontSize = 14.sp, lineHeight = 20.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = onUpdate,
                        enabled = !downloading,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        if (downloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(if (progress == null) "下载中…" else "下载中 $progress%")
                        } else {
                            Text("立即更新", fontSize = 15.sp)
                        }
                    }
                    TextButton(
                        onClick = onDismiss,
                        enabled = !downloading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("以后再说", fontSize = 13.sp, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

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
    KbSheet(onDismiss = onDismiss) {
        // verticalScroll 让嵌套滚动管线成立: 详情页任意位置下拉都能关弹层
        Column(
            Modifier
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            Text(course.kc, fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
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
            DetailRow("周次", course.zc.ifBlank { "—" })
            if (course.jxb.isNotEmpty()) DetailRow("教学班", course.jxb)
            if (course.jxbzc.isNotEmpty()) DetailRow("合班", course.jxbzc)
            if (className.isNotEmpty()) DetailRow("查询班级", className)

            // ---------------- 自定义颜色 ----------------
            Spacer(Modifier.height(14.dp))
            Text("颜色", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            val dark = CourseColors.isDark(MaterialTheme.colorScheme)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    val selected = customIdx == null
                    Box(
                        Modifier
                            .size(30.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .border(
                                if (selected) 2.dp else 0.dp,
                                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                CircleShape,
                            )
                            .clickable { onPickColor(null) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("自动", fontSize = 8.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                items(count = CourseColors.CUSTOM_N) { i ->
                    val selected = customIdx == i
                    Box(
                        Modifier
                            .size(30.dp)
                            .background(CourseColors.customContainer(i, dark), CircleShape)
                            .border(
                                if (selected) 2.dp else 0.dp,
                                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                CircleShape,
                            )
                            .clickable { onPickColor(i) },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))

            // ---------------- 还原 / 编辑 / 删除 ----------------
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
private fun FormLabel(text: String) {
    Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun FormChip(
    text: String,
    on: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(CircleShape)
            .background(
                if (on) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            .border(
                1.dp,
                if (on) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
            color = if (on) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CourseFormSheet(
    title: String,
    weeks: Int,
    maxJc: Int,
    initial: UserEdits.Form,
    showDelete: Boolean,
    onSave: (UserEdits.Form) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var kc by remember { mutableStateOf(initial.kc) }
    var teacher by remember { mutableStateOf(initial.teacher) }
    var room by remember { mutableStateOf(initial.room) }
    var day by remember { mutableIntStateOf(initial.day) }
    var startJc by remember { mutableIntStateOf(initial.startJc) }
    var span by remember { mutableIntStateOf(initial.span) }
    var sel by remember { mutableStateOf(initial.ranges.flatMap { it.toList() }.toSet()) }
    val sheetScroll = rememberScrollState()

    KbSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp)
                .verticalScroll(sheetScroll)
                .navigationBarsPadding(),
        ) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = kc,
                onValueChange = { kc = it },
                label = { Text("课程名（必填）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("教师") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("教室") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(16.dp))
            FormLabel("星期")
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..7).forEach { d ->
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (d == day) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                            )
                            .clickable { day = d }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            Weeks.cnDay(d),
                            fontSize = 11.sp,
                            fontWeight = if (d == day) FontWeight.Bold else FontWeight.Medium,
                            color = if (d == day) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            FormLabel("起始节次")
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                (1..maxJc).forEach { j ->
                    FormChip("$j", on = j == startJc, onClick = {
                        startJc = j
                        span = span.coerceAtMost(maxJc - j + 1)
                    })
                }
            }

            Spacer(Modifier.height(12.dp))
            FormLabel("连堂节数")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val maxSpan = (maxJc - startJc + 1).coerceAtMost(5)
                (1..maxSpan).forEach { s ->
                    FormChip("${s}节", on = s == span, onClick = { span = s })
                }
            }

            Spacer(Modifier.height(16.dp))
            FormLabel("上课周次（点击勾选）")
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (1..weeks).forEach { w ->
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(
                                if (w in sel) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                            )
                            .clickable {
                                sel = if (w in sel) sel - w else sel + w
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$w",
                            fontSize = 11.sp,
                            fontWeight = if (w in sel) FontWeight.Bold else FontWeight.Normal,
                            color = if (w in sel) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            TextButton(onClick = {
                sel = if (sel.size == weeks) emptySet() else (1..weeks).toSet()
            }) { Text(if (sel.size == weeks) "清空周次" else "全选周次") }

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (showDelete && onDelete != null) {
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text("删除") }
                }
                Button(
                    onClick = {
                        val (zc, ranges) = UserEdits.zcOf(sel)
                        onSave(
                            UserEdits.Form(
                                kc = kc.trim(), teacher = teacher.trim(), room = room.trim(),
                                day = day, startJc = startJc, span = span,
                                zc = zc, ranges = ranges,
                            ),
                        )
                    },
                    enabled = kc.isNotBlank() && sel.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { Text("保存") }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(
            label,
            Modifier.padding(end = 16.dp),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

// ==================== 更多面板：分组卡片式设置 ====================

@Composable
private fun SettingCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(vertical = 6.dp), content = content)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingRow(
    icon: ImageVector? = null,
    title: String,
    subtitle: String? = null,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    titleColor: Color = Color.Unspecified,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 14.dp),
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = titleColor,
                lineHeight = 19.sp,
            )
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = subtitleColor, lineHeight = 16.sp)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** 行尾「文字 + 右箭头」；text 为空时只画箭头 */
@Composable
private fun RowTrailing(text: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (text.isNotEmpty()) {
            Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Spacer(Modifier.height(20.dp))
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MoreSheet(
    dataset: Dataset,
    cls: Cls?,
    fromUpdate: Boolean,
    appVersion: String,
    themeMode: Int,
    bgOn: Boolean,
    bgAlpha: Int,
    colorSource: Int,
    bgSeed: Int,
    reminderOn: Boolean,
    reminderLead: Int,
    nextReminderAt: Long,
    afterClassOn: Boolean,
    earlyOn: Boolean,
    devMode: Boolean,
    onThemeMode: (Int) -> Unit,
    onBgSaved: () -> Unit,
    onRemoveBg: () -> Unit,
    onBgAlpha: (Int) -> Unit,
    onColorSource: (Int) -> Unit,
    onReminderToggle: (Boolean) -> Unit,
    onLeadChange: (Int) -> Unit,
    onAfterClassChange: (Boolean) -> Unit,
    onEarlyChange: (Boolean) -> Unit,
    onSelectClass: () -> Unit,
    onOpenDev: () -> Unit,
    onDevModeChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onCheckUpdate: ((String?) -> Unit) -> Unit,
    onClearUpdate: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(Updater.manifestUrl(ctx)) }
    var urlDraft by remember { mutableStateOf("") }
    var showBgDialog by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var checkNote by remember { mutableStateOf<String?>(null) }

    // 相册选图 (Photo Picker, 免存储权限)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching { BgStore.saveFromUri(ctx, uri) }
                        .onFailure { android.util.Log.e("kb-bg", "saveFromUri 异常", it) }
                        .getOrDefault(false)
                }
                if (ok) onBgSaved()
                else Toast.makeText(ctx, "背景图处理失败，请换一张试试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 提醒权限状态
    var notifGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifGranted = it
    }
    val am = ctx.getSystemService(AlarmManager::class.java)
    val exactOk = am?.canScheduleExactAlarms() ?: false

    if (showBgDialog) {
        AlertDialog(
            onDismissRequest = { showBgDialog = false },
            title = { Text("课表背景") },
            text = { Text("换一张，还是不要背景了？") },
            confirmButton = {
                TextButton(onClick = {
                    showBgDialog = false
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("换一张") }
            },
            dismissButton = {
                TextButton(onClick = { showBgDialog = false; onRemoveBg() }) { Text("移除") }
            },
        )
    }
    if (showUrlDialog) {
        val ok = urlDraft.trim().startsWith("https://")
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("更新地址") },
            text = {
                OutlinedTextField(
                    value = urlDraft,
                    onValueChange = { urlDraft = it },
                    singleLine = true,
                    placeholder = { Text("https://…/latest.json") },
                    supportingText = { Text(if (ok) "指向 latest.json 的地址" else "要以 https:// 开头") },
                    isError = !ok,
                    shape = RoundedCornerShape(12.dp),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = ok,
                    onClick = {
                        val v = urlDraft.trim()
                        Updater.setManifestUrl(ctx, v)
                        url = v
                        showUrlDialog = false
                        Toast.makeText(ctx, "已保存", Toast.LENGTH_SHORT).show()
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) { Text("取消") }
            },
        )
    }
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("恢复内置数据") },
            text = { Text("会丢掉在线更新的那份课表，回到 app 自带的那份。") },
            confirmButton = {
                TextButton(
                    onClick = { showResetConfirm = false; onClearUpdate() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("恢复") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("先不用") }
            },
        )
    }

    val sheetScroll = rememberScrollState()
    KbSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(sheetScroll)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                "更多",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
            Spacer(Modifier.height(14.dp))

            // ---------------- 班级 ----------------
            SettingCard {
                SettingRow(
                    icon = Icons.Rounded.Person,
                    title = cls?.bjmc ?: "还没选班级",
                    subtitle = cls?.let { "${it.yxmc} · ${it.zymc}" } ?: "选好班级就能看课表了",
                    trailing = { RowTrailing("切换") },
                    onClick = onSelectClass,
                )
            }

            // ---------------- 外观 ----------------
            SectionHeader("外观")
            SettingCard {
                SettingRow(
                    icon = Icons.Rounded.DarkMode,
                    title = "深色模式",
                    trailing = {
                        SingleChoiceSegmentedButtonRow {
                            val modes = listOf("系统" to 0, "浅色" to 1, "深色" to 2)
                            modes.forEachIndexed { i, (label, v) ->
                                SegmentedButton(
                                    selected = themeMode == v,
                                    onClick = { onThemeMode(v) },
                                    shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                                ) { Text(label, fontSize = 12.sp) }
                            }
                        }
                    },
                )
                SettingRow(
                    icon = Icons.Rounded.Palette,
                    title = "主题配色",
                    subtitle = when (colorSource) {
                        1 -> if (bgOn) "跟随背景图取色" else "跟随背景图（未设背景）"
                        2 -> "固定品牌色"
                        else -> "跟随系统壁纸"
                    },
                    trailing = {
                        SingleChoiceSegmentedButtonRow {
                            val srcs = listOf("壁纸" to 0, "背景图" to 1, "品牌" to 2)
                            srcs.forEachIndexed { i, (label, v) ->
                                SegmentedButton(
                                    selected = colorSource == v,
                                    enabled = v != 1 || bgOn,
                                    onClick = { onColorSource(v) },
                                    shape = SegmentedButtonDefaults.itemShape(i, srcs.size),
                                ) { Text(label, fontSize = 12.sp) }
                            }
                        }
                    },
                )
                SettingRow(
                    icon = Icons.Rounded.Wallpaper,
                    title = "课表背景",
                    subtitle = if (bgOn) "已设置" else "选张图当背景",
                    trailing = { RowTrailing(if (bgOn) "更换" else "选择") },
                    onClick = {
                        if (bgOn) showBgDialog = true
                        else picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                )
                if (bgOn) {
                    Column(Modifier.padding(start = 62.dp, end = 14.dp, bottom = 10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "透明度",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "$bgAlpha%",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Slider(
                            value = bgAlpha / 100f,
                            onValueChange = { onBgAlpha((it * 100).toInt().coerceIn(10, 100)) },
                        )
                    }
                }
                SettingRow(
                    icon = Icons.Rounded.Palette,
                    title = "课程颜色",
                    subtitle = "点课程块就能换色",
                )
            }

            // ---------------- 提醒 ----------------
            SectionHeader("提醒")
            SettingCard {
                data class RemState(val sub: String, val err: Boolean, val click: (() -> Unit)?)
                val rem = when {
                    !reminderOn -> RemState("上课前喊你一声", false, null)
                    !notifGranted -> RemState(
                        "先去开通知权限", true,
                    ) { permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    !exactOk -> RemState(
                        "可能不准时，点这里去设置", true,
                    ) {
                        runCatching {
                            ctx.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                    .setData(Uri.parse("package:${ctx.packageName}")),
                            )
                        }
                    }
                    nextReminderAt > 0 -> RemState(
                        "下次 " + DateTimeFormatter.ofPattern("M月d日 HH:mm")
                            .format(Instant.ofEpochMilli(nextReminderAt).atZone(ZoneId.systemDefault())),
                        false, null,
                    )
                    else -> RemState("已开启", false, null)
                }
                SettingRow(
                    icon = Icons.Rounded.Notifications,
                    title = "课前提醒",
                    subtitle = rem.sub,
                    subtitleColor = if (rem.err) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    trailing = {
                        Switch(checked = reminderOn, onCheckedChange = {
                            if (it && Build.VERSION.SDK_INT >= 33 && !notifGranted) {
                                permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            onReminderToggle(it)
                        })
                    },
                    onClick = rem.click,
                )
                if (reminderOn) {
                    SettingRow(title = "提前量", subtitle = "提醒早于上课的分钟数")
                    val leads = listOf(5, 10, 15, 20, 30)
                    SingleChoiceSegmentedButtonRow(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                    ) {
                        leads.forEachIndexed { i, v ->
                            SegmentedButton(
                                selected = reminderLead == v,
                                onClick = { onLeadChange(v) },
                                shape = SegmentedButtonDefaults.itemShape(i, leads.size),
                            ) { Text("$v", fontSize = 12.sp) }
                        }
                    }
                    SettingRow(
                        icon = Icons.Rounded.Celebration,
                        title = "下课后小结",
                        subtitle = "最后一节下课时夸夸你",
                        trailing = { Switch(checked = afterClassOn, onCheckedChange = onAfterClassChange) },
                    )
                    SettingRow(
                        icon = Icons.Rounded.Bedtime,
                        title = "早八前夜",
                        subtitle = "前一晚 21:30，明天真有早八才提醒",
                        trailing = { Switch(checked = earlyOn, onCheckedChange = onEarlyChange) },
                    )
                }
                SettingRow(
                    icon = Icons.Rounded.Widgets,
                    title = "桌面小组件",
                    subtitle = "长按桌面空白处添加",
                )
            }

            // ---------------- 数据 ----------------
            SectionHeader("数据")
            SettingCard {
                val genDate = dataset.generatedAt.substringBefore('T').split('-').let { d ->
                    if (d.size == 3) "${d[1].trim().toInt()}月${d[2].trim().toInt()}日" else ""
                }
                SettingRow(
                    title = "检查更新",
                    subtitle = when {
                        checking -> "看看有没有新的…"
                        checkNote != null -> checkNote
                        else -> "$genDate 更新 · ${dataset.classes.size} 个班"
                    },
                    trailing = {
                        if (checking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            RowTrailing()
                        }
                    },
                    onClick = if (checking) {
                        null
                    } else {
                        {
                            checking = true
                            checkNote = null
                            onCheckUpdate { r ->
                                checking = false
                                checkNote = r
                            }
                        }
                    },
                )
                if (fromUpdate) {
                    SettingRow(
                        title = "恢复内置数据",
                        titleColor = MaterialTheme.colorScheme.error,
                        trailing = { RowTrailing() },
                        onClick = { showResetConfirm = true },
                    )
                }
            }

            // ---------------- 开发者模式入口 ----------------
            if (devMode) {
                SectionHeader("开发者")
                SettingCard {
                    SettingRow(
                        icon = Icons.Rounded.BugReport,
                        title = "开发者模式",
                        subtitle = "时间旅行 · 测试通知 · 日志与诊断",
                        trailing = { RowTrailing() },
                        onClick = onOpenDev,
                    )
                }
            }

            // ---------------- 关于 ----------------
            SectionHeader("关于")
            var devClicks by remember { mutableIntStateOf(0) }
            Column(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 品牌块: 与启动器图标同款 (品牌蓝底 + 白色日历前景), 连点 7 次进开发者模式
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF3B64D8))
                        .combinedClickable(
                            onClick = {
                                if (devMode) return@combinedClickable
                                devClicks++
                                when {
                                    devClicks >= 7 -> {
                                        devClicks = 0
                                        onDevModeChange(true)
                                        Toast.makeText(ctx, "开发者模式已开启", Toast.LENGTH_SHORT).show()
                                    }
                                    devClicks >= 3 ->
                                        Toast.makeText(ctx, "再点 ${7 - devClicks} 次进入开发者模式", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onLongClick = if (devMode) {
                                {
                                    onDevModeChange(false)
                                    Toast.makeText(ctx, "开发者模式已关闭", Toast.LENGTH_SHORT).show()
                                }
                            } else null,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_fg),
                        contentDescription = "长工课表通",
                        modifier = Modifier.size(40.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "长工课表通",
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (devMode) "v$appVersion · 开发者模式" else "v$appVersion",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "让查看课表这件事，不再那么狼狈。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "课表来自教务系统公开课表，只保存在你手机上",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
                Text(
                    "更新源 ${Uri.parse(url).host ?: "默认地址"}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    modifier = Modifier
                        .clickable {
                            urlDraft = url
                            showUrlDialog = true
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

// ==================== 开发者模式面板 ====================

@Composable
private fun DevActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    SettingRow(title = title, subtitle = subtitle, trailing = { RowTrailing() }, onClick = onClick)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevSheet(
    dataset: Dataset,
    offset: Int,
    nextReminderAt: Long,
    onOffsetChange: (Int) -> Unit,
    onRebuildCache: () -> Unit,
    onRefreshWidget: () -> Unit,
    onReschedule: () -> Unit,
    onTestNotification: () -> Unit,
    onTestAlarm: () -> Unit,
    onReloadData: () -> Unit,
    onMockApkUpdate: () -> Unit,
    onResetPersonal: () -> Unit,
    onFullReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) } // 任何操作后 +1, 刷新速览/日志
    var rawManifest by remember { mutableStateOf<String?>(null) }
    var fetchingManifest by remember { mutableStateOf(false) }
    var confirmPersonal by remember { mutableStateOf(false) }
    var confirmFull by remember { mutableStateOf(false) }

    val snap = remember(tick) { runCatching { ClassCache.load(ctx) }.getOrNull() }
    val snapFile = remember(tick) { ctx.getFileStreamPath("class_cache.json") }
    val widgetCount = remember(tick) {
        runCatching {
            val mgr = android.appwidget.AppWidgetManager.getInstance(ctx)
            mgr?.getAppWidgetIds(android.content.ComponentName(ctx, com.ccsut.kb.widget.WidgetProvider::class.java))
                ?.size ?: 0
        }.getOrDefault(0)
    }
    val notifOk = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    val exactOk = ctx.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    val df = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss") }

    if (confirmPersonal) {
        AlertDialog(
            onDismissRequest = { confirmPersonal = false },
            title = { Text("重置个性化") },
            text = { Text("清空自定义课程颜色、课表背景和主题设置。班级与课表数据保留。") },
            confirmButton = {
                TextButton(
                    onClick = { confirmPersonal = false; onResetPersonal(); tick++ },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("重置") }
            },
            dismissButton = { TextButton(onClick = { confirmPersonal = false }) { Text("取消") } },
        )
    }
    if (confirmFull) {
        AlertDialog(
            onDismissRequest = { confirmFull = false },
            title = { Text("完全重置") },
            text = { Text("清空全部设置、已下载数据、班级快照和背景，回到选择班级页。开发者模式保持开启。") },
            confirmButton = {
                TextButton(
                    onClick = { confirmFull = false; onFullReset(); tick++ },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("全部重置") }
            },
            dismissButton = { TextButton(onClick = { confirmFull = false }) { Text("取消") } },
        )
    }

    val sheetScroll = rememberScrollState()
    KbSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(sheetScroll)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                "开发者模式",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
            Spacer(Modifier.height(14.dp))

            // ---------------- 状态速览 ----------------
            SettingCard {
                DetailRow(
                    "数据",
                    "v${dataset.version} · ${dataset.xnxq.ifBlank { "—" }} · ${dataset.classes.size} 个班 · " +
                        if (Repo.fromUpdate) "在线更新" else "内置",
                )
                DetailRow("学期", "${dataset.startDate} 起 · ${dataset.weeks} 周")
                DetailRow(
                    "快照",
                    snap?.let {
                        "${it.bjmc} · ${it.courses.size} 条课" + if (snapFile.exists()) " · ${snapFile.length()}B" else ""
                    } ?: "未选班级",
                )
                DetailRow(
                    "语义今天",
                    "${KbClock.fmt(KbClock.today())} · 第${Weeks.currentWeek(dataset, KbClock.today())}周" +
                        if (offset != 0) "（偏移 $offset 天）" else "",
                )
                DetailRow(
                    "提醒",
                    "下次 " + (if (nextReminderAt > 0) df.format(Instant.ofEpochMilli(nextReminderAt).atZone(ZoneId.systemDefault())) else "未安排") +
                        " · 精确闹钟${if (exactOk) "✓" else "✗"} · 通知${if (notifOk) "✓" else "✗"}",
                )
                DetailRow("小组件", "$widgetCount 个已添加")
            }

            // ---------------- 时间旅行 ----------------
            SectionHeader("时间旅行")
            SettingCard {
                SettingRow(
                    title = "日期偏移",
                    subtitle = if (offset == 0) "0 天，使用真实日期" else "偏移 $offset 天（只平移日期，时刻不变）",
                    subtitleColor = if (offset == 0) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error,
                )
                SingleChoiceSegmentedButtonRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                ) {
                    val nextMon = run {
                        val real = java.time.LocalDate.now()
                        ((8 - real.dayOfWeek.value) % 7).let { if (it == 0) 7 else it }
                    }
                    val chips = listOf("真实" to 0, "明天" to 1, "下周一" to nextMon, "+7 天" to 7)
                    chips.forEachIndexed { i, (label, v) ->
                        SegmentedButton(
                            selected = offset == v,
                            onClick = { onOffsetChange(v) },
                            shape = SegmentedButtonDefaults.itemShape(i, chips.size),
                        ) { Text(label, fontSize = 12.sp) }
                    }
                }
            }

            // ---------------- 即时操作 ----------------
            SectionHeader("即时操作")
            SettingCard {
                DevActionRow("重建班级快照", "重写 widget / 提醒共用的快照") { onRebuildCache(); tick++ }
                DevActionRow("强制刷新小组件", "马上重画桌面组件") { onRefreshWidget(); tick++ }
                DevActionRow("重排提醒闹钟", "重算下一次事件，见下方结果") { onReschedule(); tick++ }
                DevActionRow("发送测试通知", "验证渠道 / 图标 / 权限") { onTestNotification(); tick++ }
                DevActionRow("10 秒后测试闹钟", "验证 闹钟→接收器→通知 全链路") { onTestAlarm(); tick++ }
                DevActionRow("重载数据", "重新解析内置 / 已下载的数据集") { onReloadData(); tick++ }
                DevActionRow("模拟 APK 更新弹窗", "走一遍自更新确认 UI（下载会失败）") { onMockApkUpdate(); tick++ }
                DetailRow(
                    "下次闹钟",
                    if (nextReminderAt > 0) df.format(Instant.ofEpochMilli(nextReminderAt).atZone(ZoneId.systemDefault()))
                    else "未安排",
                )
            }

            // ---------------- 更新通道 ----------------
            SectionHeader("更新通道")
            SettingCard {
                SettingRow(
                    title = "拉取 latest.json",
                    subtitle = if (fetchingManifest) "拉取中…" else "查看更新服务器原始清单",
                    trailing = {
                        if (fetchingManifest) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            RowTrailing()
                        }
                    },
                    onClick = if (fetchingManifest) {
                        null
                    } else {
                        {
                            fetchingManifest = true
                            scope.launch {
                                rawManifest = withContext(Dispatchers.IO) {
                                    runCatching { Updater.fetchRawManifest(ctx) }
                                        .getOrElse { "拉取失败: ${it.message}" }
                                }
                                DebugLog.log("dev", "拉取 manifest ${rawManifest?.length ?: 0} 字符")
                                fetchingManifest = false
                                tick++
                            }
                        }
                    },
                )
                rawManifest?.let { raw ->
                    Text(
                        raw,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }

            // ---------------- 日志 ----------------
            SectionHeader("日志")
            SettingCard {
                Text(
                    DebugLog.dump().ifBlank { "(还没有日志)" },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState()),
                )
                SettingRow(title = "清空日志", onClick = { DebugLog.clear(); tick++ })
            }

            // ---------------- 诊断 ----------------
            SectionHeader("诊断")
            SettingCard {
                SettingRow(
                    title = "导出诊断报告",
                    subtitle = "设备 / 版本 / 数据 / 设置 / 日志，一键分享",
                    trailing = { RowTrailing() },
                    onClick = {
                        scope.launch {
                            val text = withContext(Dispatchers.IO) { Diag.build(ctx) }
                            val send = Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_SUBJECT, "长工课表通诊断报告")
                                .putExtra(Intent.EXTRA_TEXT, text)
                            runCatching { ctx.startActivity(Intent.createChooser(send, "分享诊断报告")) }
                                .onFailure {
                                    Toast.makeText(ctx, "无法分享: ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                    },
                )
            }

            // ---------------- 危险区 ----------------
            SectionHeader("危险区")
            SettingCard {
                SettingRow(
                    title = "重置个性化",
                    subtitle = "清课程颜色 / 背景 / 主题，保留班级与数据",
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = { confirmPersonal = true },
                )
                SettingRow(
                    title = "完全重置",
                    subtitle = "清空全部设置与数据，回到选班级",
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = { confirmFull = true },
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}
