package com.ccsut.kb.ui
import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ccsut.kb.data.ClassCache
import com.ccsut.kb.data.Dataset
import com.ccsut.kb.data.Repo
import com.ccsut.kb.data.Updater
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * 开发者模式面板。结构固定八段: 状态速览 → 时间旅行 → 即时操作 → 调试入口 → 更新通道 → 日志 → 诊断 → 危险区。
 * 速览一律 [DetailRow] (label 固定 + value 折行省略), 操作一律 [DevActionRow], 新增调试项先找对应分组再加。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevSheet(
    dataset: Dataset,
    offset: Int,
    nextReminderAt: Long,
    personalActive: Boolean,
    personalName: String,
    onOffsetChange: (Int) -> Unit,
    onRebuildCache: () -> Unit,
    onRefreshWidget: () -> Unit,
    onReschedule: () -> Unit,
    onTestNotification: () -> Unit,
    onTestAlarm: () -> Unit,
    onTestRing: () -> Unit,
    onReloadData: () -> Unit,
    onMockApkUpdate: () -> Unit,
    onToggleTimetable: () -> Unit,
    onReplayOnboarding: () -> Unit,
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
    val personalFile = remember(tick) { ctx.getFileStreamPath("personal_cache.json") }
    val notifOk = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    val exactOk = ctx.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    val df = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss") }
    val fmtTime: (Long) -> String = {
        df.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
    }

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

            // ---------------- 1. 状态速览 (只读, 全 DetailRow) ----------------
            SectionHeader("状态速览")
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
                DetailRow("课表模式", if (personalActive) "个人课表" else "班级课表")
                DetailRow(
                    "个人课表",
                    if (personalName.isNotBlank()) {
                        personalName + (personalFile?.takeIf { it.exists() }?.let { " · 缓存 ${it.length()}B" } ?: "")
                    } else "未登录",
                )
                DetailRow(
                    "语义今天",
                    "${KbClock.fmt(KbClock.today())} · 第${Weeks.currentWeek(dataset, KbClock.today())}周" +
                        if (offset != 0) "（偏移 $offset 天）" else "",
                )
                DetailRow(
                    "提醒",
                    "下次 " + (if (nextReminderAt > 0) fmtTime(nextReminderAt) else "未安排") +
                        " · 精确闹钟${if (exactOk) "✓" else "✗"} · 通知${if (notifOk) "✓" else "✗"}",
                )
                DetailRow("小组件", "$widgetCount 个已添加")
            }

            // ---------------- 2. 时间旅行 ----------------
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

            // ---------------- 3. 即时操作 ----------------
            SectionHeader("即时操作")
            SettingCard {
                DevActionRow("重建班级快照", "重写 widget / 提醒共用的快照") { onRebuildCache(); tick++ }
                DevActionRow("强制刷新小组件", "马上重画桌面组件") { onRefreshWidget(); tick++ }
                DevActionRow("重排提醒闹钟", "重算下一次事件，结果见上方速览") { onReschedule(); tick++ }
                DevActionRow("发送测试通知", "验证渠道 / 图标 / 权限") { onTestNotification(); tick++ }
                DevActionRow("10 秒后测试闹钟", "验证 闹钟→接收器→通知 全链路") { onTestAlarm(); tick++ }
                DevActionRow("10 秒测试响铃", "验证 闹钟→全屏响铃页 链路（闹钟模式）") { onTestRing(); tick++ }
                DevActionRow("重载数据", "重新解析内置 / 已下载的数据集") { onReloadData(); tick++ }
                DevActionRow("模拟 APK 更新弹窗", "走一遍自更新确认 UI（下载会失败）") { onMockApkUpdate(); tick++ }
            }

            // ---------------- 4. 调试入口 ----------------
            SectionHeader("调试入口")
            SettingCard {
                DevActionRow("对调当前课表", if (personalActive) "当前个人课表 → 班级课表" else "当前班级课表 → 个人课表") {
                    onToggleTimetable(); tick++
                }
                DevActionRow("重看引导页", "回到欢迎向导第一步（设置与数据保留）") {
                    onReplayOnboarding(); tick++
                }
            }

            // ---------------- 5. 更新通道 ----------------
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

            // ---------------- 6. 日志 ----------------
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

            // ---------------- 7. 诊断 ----------------
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

            // ---------------- 8. 危险区 ----------------
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
