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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ccsut.kb.data.BgStore
import com.ccsut.kb.data.Cls
import com.ccsut.kb.data.ClassCache
import com.ccsut.kb.data.Dataset
import com.ccsut.kb.data.Repo
import com.ccsut.kb.data.Updater
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailSheet(
    block: Block,
    className: String,
    customIdx: Int?,
    onPickColor: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val course = block.course
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 28.dp).navigationBarsPadding()) {
            Text(course.kc, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (course.fx.isNotEmpty()) {
                Text(
                    "分项: ${course.fx}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
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

@OptIn(ExperimentalMaterial3Api::class)
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

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
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
                    icon = Icons.Rounded.Refresh,
                    title = "检查更新",
                    subtitle = when {
                        checking -> "看看有没有新的…"
                        checkNote != null -> checkNote
                        else -> "v${dataset.version} · ${dataset.classes.size} 个班 · $genDate 生成"
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
                SettingRow(
                    icon = Icons.Rounded.Link,
                    title = "更新地址",
                    subtitle = Uri.parse(url).host ?: "默认地址",
                    trailing = { RowTrailing() },
                    onClick = {
                        urlDraft = url
                        showUrlDialog = true
                    },
                )
                if (fromUpdate) {
                    SettingRow(
                        icon = Icons.Rounded.RestartAlt,
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
            SettingCard {
                var devClicks by remember { mutableIntStateOf(0) }
                SettingRow(
                    icon = Icons.Rounded.Info,
                    title = "长工课表通",
                    subtitle = if (devMode) "v$appVersion · 开发者模式已开启" else "v$appVersion",
                    onClick = {
                        if (devMode) return@SettingRow
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
                )
                SettingRow(
                    icon = Icons.Rounded.Lock,
                    title = "数据说明",
                    subtitle = "来自教务系统公开课表，只存你手机上",
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

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
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
