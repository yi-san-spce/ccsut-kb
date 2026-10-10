package com.ccsut.kb.ui
import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ripple
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ccsut.kb.R
import com.ccsut.kb.data.BgStore
import com.ccsut.kb.data.Cls
import com.ccsut.kb.data.Dataset
import com.ccsut.kb.data.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
    styleTheme: Int,
    reminderOn: Boolean,
    reminderLead: Int,
    nextReminderAt: Long,
    afterClassOn: Boolean,
    earlyOn: Boolean,
    alarmMode: Boolean,
    devMode: Boolean,
    personalActive: Boolean,
    personalName: String,
    personalSyncedAt: Long,
    onThemeMode: (Int) -> Unit,
    onBgSaved: () -> Unit,
    onRemoveBg: () -> Unit,
    onBgAlpha: (Int) -> Unit,
    onColorSource: (Int) -> Unit,
    onStyleTheme: (Int) -> Unit,
    onReminderToggle: (Boolean) -> Unit,
    onLeadChange: (Int) -> Unit,
    onAfterClassChange: (Boolean) -> Unit,
    onEarlyChange: (Boolean) -> Unit,
    onAlarmModeChange: (Boolean) -> Unit,
    onSelectClass: () -> Unit,
    onOpenPersonalLogin: () -> Unit,
    onSetTimetable: (Boolean) -> Unit,
    onRefreshPersonal: () -> Unit,
    onLogoutPersonal: () -> Unit,
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
    // 双平台选择: (标题, giteeUrl, githubUrl) —— 国内用户默认 Gitee 直连, GitHub 功能更全
    var platformPick by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var checking by remember { mutableStateOf(false) }
    var checkNote by remember { mutableStateOf<String?>(null) }
    val cs = MaterialTheme.colorScheme

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

    // 提醒权限状态 (每次 RESUME 重查: 从系统设置授权/撤销返回后副标题才不会停在旧状态)
    var notifGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    // 「禁止后不再询问」: 再 launch 也静默无弹窗, 红行要改为跳系统应用通知设置
    var permDeniedForever by remember { mutableStateOf(false) }
    var exactOk by remember { mutableStateOf(false) }
    DisposableEffect(ctx) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val refresh = {
            notifGranted = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            exactOk = am?.canScheduleExactAlarms() ?: false
        }
        refresh()
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh() }
        val lifecycle = (ctx as? ComponentActivity)?.lifecycle
        lifecycle?.addObserver(obs)
        onDispose { lifecycle?.removeObserver(obs) }
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifGranted = it
        // 拒绝且系统不再弹窗 = 永久拒绝, 之后只引导去系统设置
        permDeniedForever = !it && Build.VERSION.SDK_INT >= 33 &&
            !ActivityCompat.shouldShowRequestPermissionRationale(ctx as Activity, Manifest.permission.POST_NOTIFICATIONS)
    }
    fun requestNotifPerm() {
        if (Build.VERSION.SDK_INT < 33) return
        permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

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
    platformPick?.let { (pickTitle, giteeUrl, githubUrl) ->
        AlertDialog(
            onDismissRequest = { platformPick = null },
            title = { Text(pickTitle) },
            text = { Text("Gitee 国内直连更快；GitHub 功能更全（分类反馈模板 / Discussions）。两边内容一致，任选即可。") },
            confirmButton = {
                TextButton(onClick = { platformPick = null; openUrl(ctx, giteeUrl) }) { Text("Gitee · 国内直连") }
            },
            dismissButton = {
                TextButton(onClick = { platformPick = null; openUrl(ctx, githubUrl) }) { Text("GitHub") }
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

            // 登录态 = 教务姓名非空; 当前课表卡与账号卡两处共用同一判定
            val personalLoggedIn = personalName.isNotBlank()

            // ---------------- 当前课表 ----------------
            SectionHeader("当前课表")
            SettingCard {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                ) {
                    FilterChip(
                        selected = !personalActive,
                        // 幂等守卫: 只在「当前不是班级课表」时才触发切换; 点已选中的 Chip 无操作
                        onClick = { if (personalActive) onSetTimetable(false) },
                        label = { Text("班级课表") },
                    )
                    FilterChip(
                        selected = personalActive,
                        enabled = personalLoggedIn,
                        onClick = { if (!personalActive) onSetTimetable(true) },
                        label = { Text("个人课表") },
                    )
                }
                // 当前生效对象 + 主操作一行说清, 不再重复显示「当前显示:…」
                if (!personalActive) {
                    SettingRow(
                        icon = Icons.Rounded.Person,
                        title = cls?.bjmc ?: "还没选班级",
                        subtitle = cls?.let { "${it.yxmc} · ${it.zymc}" } ?: "选好班级就能看课表了",
                        trailing = { RowTrailing("换班") },
                        onClick = onSelectClass,
                    )
                } else {
                    SettingRow(
                        icon = Icons.Rounded.Person,
                        title = "个人课表",
                        subtitle = "上次同步 " + formatSynced(personalSyncedAt),
                        trailing = { RowTrailing("同步") },
                        onClick = onRefreshPersonal,
                    )
                }
            }

            // ---------------- 个人课表: 账号生命周期 ----------------
            SectionHeader("个人课表")
            SettingCard {
                if (!personalLoggedIn) {
                    SettingRow(
                        icon = Icons.Rounded.ManageAccounts,
                        title = "登录教务账号",
                        subtitle = "短信验证码直连教务，只拉你自己的课表",
                        trailing = { RowTrailing("登录") },
                        onClick = onOpenPersonalLogin,
                    )
                } else {
                    SettingRow(
                        icon = Icons.Rounded.ManageAccounts,
                        title = personalName,
                        subtitle = "已登录 · 上次同步 " + formatSynced(personalSyncedAt),
                        trailing = { RowTrailing("同步") },
                        onClick = onRefreshPersonal,
                    )
                    SettingRow(
                        icon = Icons.AutoMirrored.Rounded.Logout,
                        title = "退出登录",
                        subtitle = "清除本机个人课表与登录状态",
                        onClick = onLogoutPersonal,
                    )
                }
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
                    subtitle = if (styleTheme != 0) "品牌 · ${ThemePacks.byId(styleTheme)?.title}" else when (colorSource) {
                        1 -> if (bgOn) "跟随背景图取色" else "跟随背景图（未设背景）"
                        2 -> "经典品牌蓝"
                        else -> "跟随系统壁纸"
                    },
                    trailing = {
                        SingleChoiceSegmentedButtonRow {
                            val srcs = listOf("壁纸" to 0, "背景图" to 1, "品牌" to 2)
                            srcs.forEachIndexed { i, (label, v) ->
                                SegmentedButton(
                                    // 主题风格归入品牌档: 固定配色, 永不随壁纸/背景变化
                                    selected = if (styleTheme != 0) v == 2 else colorSource == v,
                                    enabled = v != 1 || bgOn,
                                    onClick = {
                                        when (v) {
                                            // 品牌 = 固定配色档: 已选主题风格则保持, 否则回经典品牌蓝
                                            2 -> onColorSource(2)
                                            else -> {
                                                if (styleTheme != 0) onStyleTheme(0)
                                                onColorSource(v)
                                            }
                                        }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(i, srcs.size),
                                ) { Text(label, fontSize = 12.sp) }
                            }
                        }
                    },
                )
                // ---------------- 主题风格直达: 色卡平铺, 点选即换 ----------------
                Text(
                    "主题风格",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(start = 14.dp, top = 8.dp),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 10.dp),
                ) {
                    ThemeSwatchCard(
                        title = "经典品牌蓝",
                        selected = styleTheme == 0 && colorSource == 2,
                        preview = listOf(
                            LightColors.background, LightColors.primary, LightColors.primaryContainer,
                            LightColors.secondaryContainer, LightColors.tertiaryContainer,
                        ),
                        onClick = { onStyleTheme(0); onColorSource(2) },
                    )
                    ThemePacks.ALL.forEach { p ->
                        ThemeSwatchCard(
                            title = p.title,
                            selected = styleTheme == p.id,
                            preview = ThemePacks.previewOf(p),
                            onClick = { onStyleTheme(p.id); onColorSource(2) },
                        )
                    }
                }
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
                    subtitle = "在课程详情里为单门课自定义颜色",
                )
            }

            // ---------------- 提醒 ----------------
            SectionHeader("提醒")
            SettingCard {
                data class RemState(val sub: String, val err: Boolean, val click: (() -> Unit)?)
                val rem = when {
                    !reminderOn -> RemState("每节课开始前按时提醒", false, null)
                    !notifGranted -> if (permDeniedForever) RemState(
                        // 永久拒绝后系统弹窗再也不出现, 只能引导去系统应用通知设置
                        "通知权限被关了，点这里去系统设置", true,
                    ) {
                        runCatching {
                            ctx.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName),
                            )
                        }
                    } else RemState(
                        "先去开通知权限", true,
                    ) { requestNotifPerm() }
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
                            if (it) requestNotifPerm()
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
                        icon = Icons.Rounded.Alarm,
                        title = "闹钟模式",
                        subtitle = "到点全屏响铃，早八不怕睡过头（不依赖通知权限）",
                        trailing = { Switch(checked = alarmMode, onCheckedChange = onAlarmModeChange) },
                    )
                }
                // 与课前提醒各自独立: 关掉课前提醒不影响这两项
                SettingRow(
                    icon = Icons.Rounded.Celebration,
                    title = "下课后小结",
                    subtitle = "最后一节下课后推送今日课程小结",
                    trailing = {
                        Switch(checked = afterClassOn, onCheckedChange = {
                            if (it) requestNotifPerm()
                            onAfterClassChange(it)
                        })
                    },
                )
                SettingRow(
                    icon = Icons.Rounded.Bedtime,
                    title = "早八前夜",
                    subtitle = "前一晚 21:30，明天真有早八才提醒",
                    trailing = {
                        Switch(checked = earlyOn, onCheckedChange = {
                            if (it) requestNotifPerm()
                            onEarlyChange(it)
                        })
                    },
                )
                SettingRow(
                    icon = Icons.Rounded.Widgets,
                    title = "桌面小组件",
                    subtitle = "长按桌面空白处添加",
                )
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
                // 数据信息行: 点一下 = 手动检查更新 (结果用 toast 反馈), 不再单占一个「数据」区块
                // runCatching: 远端数据 generatedAt 字段畸形(非数字段)时不让更多面板组合期崩溃
                val genDate = runCatching {
                    dataset.generatedAt.substringBefore('T').split('-').let { d ->
                        if (d.size == 3) "${d[1].trim().toInt()}月${d[2].trim().toInt()}日" else ""
                    }
                }.getOrDefault("")
                Text(
                    if (checking) "正在检查更新…"
                    else "课表数据 · ${genDate}更新 · ${dataset.classes.size} 个班 · 点按检查",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp)).clickable(enabled = !checking) {
                            checking = true
                            onCheckUpdate { r ->
                                checking = false
                                Toast.makeText(ctx, r ?: "课表数据已是最新", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                Text(
                    "更新源 ${Uri.parse(url).host ?: "默认地址"}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp)).clickable {
                            urlDraft = url
                            showUrlDialog = true
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                Row {
                    Text(
                        "源代码已开源 · GitHub",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp)).clickable { openUrl(ctx, OPEN_SOURCE_URL) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                    Text(
                        "中国区镜像 · Gitee",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp)).clickable { openUrl(ctx, OPEN_SOURCE_MIRROR_URL) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                Row {
                    Text(
                        "隐私政策",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp)).clickable { openUrl(ctx, PRIVACY_URL) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                    Text(
                        "更新日志",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp)).clickable { openUrl(ctx, CHANGELOG_URL) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            // ---------------- 加入社区 ----------------
            // QQ 群/频道入口由 ui/Links.kt 的常量驱动, 留空即自动隐藏 (群号变动无需发版之外的操作)
            SectionHeader("加入社区")
            SettingCard {
                if (QQ_GROUP_UIN.isNotBlank()) {
                    SettingRow(
                        icon = Icons.Rounded.Groups,
                        title = "QQ 交流群",
                        subtitle = "$QQ_GROUP_NAME · 群号 $QQ_GROUP_UIN",
                        trailing = { RowTrailing() },
                        onClick = {
                            openQQGroup(ctx, QQ_GROUP_UIN) { uin ->
                                copyText(ctx, uin)
                                Toast.makeText(ctx, "群号 $uin 已复制，请在 QQ 中搜索加群", Toast.LENGTH_LONG).show()
                            }
                        },
                    )
                }
                if (QQ_CHANNEL_URL.isNotBlank()) {
                    SettingRow(
                        icon = Icons.Rounded.Forum,
                        title = "QQ 频道",
                        subtitle = "$QQ_CHANNEL_NAME · 公告与讨论",
                        trailing = { RowTrailing() },
                        onClick = { openUrl(ctx, QQ_CHANNEL_URL) },
                    )
                }
                SettingRow(
                    icon = Icons.Rounded.Star,
                    title = "给项目点个 Star",
                    subtitle = "Gitee / GitHub，你的 Star 是更新的动力",
                    trailing = { RowTrailing() },
                    onClick = {
                        platformPick = Triple(
                            "去哪个平台点亮 Star？",
                            OPEN_SOURCE_MIRROR_URL,
                            OPEN_SOURCE_URL,
                        )
                    },
                )
                SettingRow(
                    icon = Icons.Rounded.Groups,
                    title = "想法与问答 · Discussions",
                    subtitle = "GitHub 上沉淀想法与问答，也欢迎先来 QQ 群聊",
                    trailing = { RowTrailing() },
                    onClick = { openUrl(ctx, DISCUSSIONS_URL) },
                )
                SettingRow(
                    icon = Icons.Rounded.BugReport,
                    title = "反馈问题",
                    subtitle = "Bug / 功能建议 / 课表数据",
                    trailing = { RowTrailing() },
                    onClick = {
                        platformPick = Triple(
                            "在哪个平台反馈？",
                            ISSUES_GITEE_URL,
                            ISSUES_URL,
                        )
                    },
                )
            }

            // 数据热更后偶发的回退口: 只在发生过在线数据更新时出现
            if (fromUpdate) {
                Spacer(Modifier.height(14.dp))
                SettingCard {
                    SettingRow(
                        title = "恢复内置数据",
                        titleColor = MaterialTheme.colorScheme.error,
                        subtitle = "回退本次在线课表数据更新",
                        trailing = { RowTrailing() },
                        onClick = { showResetConfirm = true },
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

// ==================== 开发者模式面板 ====================

@Composable
internal fun DevActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    SettingRow(title = title, subtitle = subtitle, trailing = { RowTrailing() }, onClick = onClick)
}

// ==================== 主题风格选择 ====================

/** 主题风格色卡: 迷你课表预览(底色+主色条+容器块) + 名称, 选中描边高亮 */
@Composable
private fun ThemeSwatchCard(
    title: String,
    selected: Boolean,
    preview: List<Color>,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
    ) {
        Box(
            Modifier
                .padding(horizontal = 6.dp)
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(preview[0])
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) cs.primary else cs.outlineVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(7.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                // 主色条 = 顶栏, 三个容器块 = 课程卡
                Box(
                    Modifier
                        .fillMaxWidth(0.62f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(preview[1]),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(preview[2], preview[3], preview[4]).forEach { c ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(c),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            title,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            maxLines = 1,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) cs.primary else cs.onSurfaceVariant,
        )
    }
}
